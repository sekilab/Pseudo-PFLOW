package truck.sim;

import truck.sim.spatial.PointGenerator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fleet generation for the Tokyo Truck ABM.
 *
 * <p>Creates heterogeneous truck agents in parallel, using ThreadLocalRandom
 * for lock-free random generation across threads.
 *
 * @version 3.0
 */
public class FleetFactory {

    private final TruckConfig config;
    private final Random random;  // Used only for zone weight array construction (deterministic)
    private final List<DeliveryZone> deliveryZones;
    private final OriginDestinationMatrix odMatrix;
    private final POIManager poiManager;
    private final PointGenerator pointGenerator;
    private final ZoneManager zoneManager;

    public FleetFactory(TruckConfig config, Random random,
                        List<DeliveryZone> deliveryZones,
                        OriginDestinationMatrix odMatrix,
                        POIManager poiManager, PointGenerator pointGenerator,
                        ZoneManager zoneManager) {
        this.config = config;
        this.random = random;
        this.deliveryZones = deliveryZones;
        this.odMatrix = odMatrix;
        this.poiManager = poiManager;
        this.pointGenerator = pointGenerator;
        this.zoneManager = zoneManager;
    }

    /**
     * Create the full truck fleet using parallel generation.
     */
    public List<TruckAgent> createFleet() {
        int numTrucks = config.getTruckFleetSize();
        System.out.println("[CHECKPOINT] Initializing " + numTrucks + " trucks...");

        // Build cumulative zone weight arrays (deterministic, single-threaded)
        double[][] weightArrays = buildZoneWeightArrays();
        double[] cumWeightsLocal = weightArrays[0];
        double[] cumWeightsNational = weightArrays[1];
        double totalWeightLocal = weightArrays[2][0];
        double totalWeightNational = weightArrays[3][0];

        // Pre-compute truck type and zone assignments (deterministic)
        TruckType[] truckTypes = new TruckType[numTrucks];
        int[] homeZoneIndices = new int[numTrucks];

        for (int i = 0; i < numTrucks; i++) {
            truckTypes[i] = selectTruckType();
            boolean isLongHaul = (truckTypes[i] == TruckType.LONG_HAUL);
            double[] cumWeights = isLongHaul ? cumWeightsNational : cumWeightsLocal;
            double totalWeight = isLongHaul ? totalWeightNational : totalWeightLocal;

            double roll = random.nextDouble() * totalWeight;
            int zoneIdx = deliveryZones.size() - 1;
            for (int z = 0; z < deliveryZones.size(); z++) {
                if (roll < cumWeights[z]) {
                    zoneIdx = z;
                    break;
                }
            }
            homeZoneIndices[i] = zoneIdx;
        }

        // Parallel fleet generation — home point generation is the expensive part
        int nThreads = Runtime.getRuntime().availableProcessors();
        TruckAgent[] fleet = new TruckAgent[numTrucks];
        AtomicInteger deliveryCount = new AtomicInteger();
        AtomicInteger longHaulCount = new AtomicInteger();
        AtomicInteger urbanCount = new AtomicInteger();
        AtomicInteger heavyCount = new AtomicInteger();
        AtomicInteger mediumCount = new AtomicInteger();
        AtomicInteger smallCount = new AtomicInteger();
        AtomicInteger lightCount = new AtomicInteger();

        int chunkSize = (numTrucks + nThreads - 1) / nThreads;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < nThreads; t++) {
            int start = t * chunkSize;
            int end = Math.min(start + chunkSize, numTrucks);
            if (start >= end) break;

            futures.add(executor.submit(() -> {
                for (int i = start; i < end; i++) {
                    TruckType truckType = truckTypes[i];
                    DeliveryZone homeZone = deliveryZones.get(homeZoneIndices[i]);

                    // Generate home point (EXPENSIVE — uses PointGenerator cascadee)
                    HomeLocation home = generateHomePoint(homeZone);

                    // Assign vehicle size
                    VehicleSpec spec = assignVehicleSpec(truckType);

                    // Goods type and shift timing
                    String primaryGoodsType = selectGoodsTypeThreadSafe();
                    long shiftStart = selectShiftStartThreadSafe();
                    int shiftDuration = generateShiftDurationThreadSafe();

                    double familiarRadius = config.getTruckTypeDeliveryFamiliarRadiusKm();
                    TruckAgent truck = new TruckAgent(i, truckType, home.lon, home.lat,
                        familiarRadius, spec.size, spec.capacityTons, primaryGoodsType,
                        shiftStart, shiftDuration);

                    if (home.poiId != null) {
                        truck.setHomePOIId(home.poiId);
                    }

                    if (truckType == TruckType.DELIVERY) {
                        String zoneId = zoneManager.findNearestZone(home.lon, home.lat);
                        truck.setFamiliarAreaZoneId(zoneId);
                    }

                    fleet[i] = truck;

                    // Update counters
                    switch (truckType) {
                        case DELIVERY:        deliveryCount.incrementAndGet(); break;
                        case LONG_HAUL:       longHaulCount.incrementAndGet(); break;
                        case MIXED_OPERATION: urbanCount.incrementAndGet(); break;
                    }
                    switch (spec.size) {
                        case "heavy":  heavyCount.incrementAndGet(); break;
                        case "medium": mediumCount.incrementAndGet(); break;
                        case "small":  smallCount.incrementAndGet(); break;
                        case "light":  lightCount.incrementAndGet(); break;
                    }
                }
            }));
        }

        // Wait for completion
        try {
            for (Future<?> f : futures) {
                f.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("[FATAL] Parallel fleet generation failed", e);
        }
        executor.shutdown();

        // Print summary
        System.out.println("[CHECKPOINT] Initialized " + numTrucks + " trucks (" + nThreads + " threads):");
        System.out.println("  DELIVERY: " + deliveryCount.get() + " (" +
            String.format("%.1f%%", 100.0 * deliveryCount.get() / numTrucks) + ")");
        System.out.println("  LONG_HAUL: " + longHaulCount.get() + " (" +
            String.format("%.1f%%", 100.0 * longHaulCount.get() / numTrucks) + ")");
        System.out.println("  MIXED_OPERATION: " + urbanCount.get() + " (" +
            String.format("%.1f%%", 100.0 * urbanCount.get() / numTrucks) + ")");
        System.out.println("  Fleet mix: Heavy=" + heavyCount.get() + ", Medium=" + mediumCount.get() +
            ", Small=" + smallCount.get() + ", Light=" + lightCount.get());

        return new ArrayList<>(Arrays.asList(fleet));
    }

    // ========================================================================
    // ZONE WEIGHT ARRAYS
    // ========================================================================

    private double[][] buildZoneWeightArrays() {
        int n = deliveryZones.size();
        double[] cumLocal = new double[n];
        double[] cumNational = new double[n];
        double totalLocal = 0.0;
        double totalNational = 0.0;

        for (int z = 0; z < n; z++) {
            double rawFlow = odMatrix.getRawOutflowTotal(z);
            double localWeight = (rawFlow > 0.0) ? rawFlow : 1.0;
            totalLocal += localWeight;
            totalNational += 1.0;
        }

        double cumSumLocal = 0.0, cumSumNational = 0.0;
        for (int z = 0; z < n; z++) {
            double rawFlow = odMatrix.getRawOutflowTotal(z);
            double localWeight = (rawFlow > 0.0) ? rawFlow : 1.0;
            cumSumLocal += localWeight;
            cumSumNational += 1.0;
            cumLocal[z] = cumSumLocal;
            cumNational[z] = cumSumNational;
        }

        double minW = Double.MAX_VALUE, maxW = 0;
        int minZ = 0, maxZ = 0;
        for (int z = 0; z < n; z++) {
            double rawFlow = odMatrix.getRawOutflowTotal(z);
            double w = (rawFlow > 0.0) ? rawFlow : 1.0;
            if (w < minW) { minW = w; minZ = z; }
            if (w > maxW) { maxW = w; maxZ = z; }
        }
        System.out.println("[Fleet] Zone weight range: min=" + String.format("%.0f", minW) +
            " (zone" + minZ + ") max=" + String.format("%.0f", maxW) + " (zone" + maxZ + ")");
        System.out.println("[Fleet] Weight ratio max/min: " + String.format("%.1f", maxW / Math.max(minW, 1)));

        return new double[][]{cumLocal, cumNational, {totalLocal}, {totalNational}};
    }

    // ========================================================================
    // TRUCK TYPE SELECTION (deterministic with shared Random)
    // ========================================================================

    private TruckType selectTruckType() {
        double typeRoll = random.nextDouble();
        if (typeRoll < config.getTruckTypeDeliveryProb()) {
            return TruckType.DELIVERY;
        } else if (typeRoll < config.getTruckTypeDeliveryProb() + config.getTruckTypeLongHaulProb()) {
            return TruckType.LONG_HAUL;
        } else {
            return TruckType.MIXED_OPERATION;
        }
    }

    // ========================================================================
    // HOME LOCATION GENERATION
    // ========================================================================

    private static class HomeLocation {
        final double lon;
        final double lat;
        final String poiId;

        HomeLocation(double lon, double lat, String poiId) {
            this.lon = lon;
            this.lat = lat;
            this.poiId = poiId;
        }
    }

    /**
     * Generate home coordinates within a zone.
     * Thread-safe: uses ThreadLocalRandom via PointGenerator.
     */
    private HomeLocation generateHomePoint(DeliveryZone homeZone) {
        if (homeZone.hasPolygonBoundary()) {
            double homeBypassRatio = (homeZone.getRadiusKm() > config.getRuralZoneRadiusThresholdKm())
                ? 0.90 : 0.35;
            boolean homeBypass = ThreadLocalRandom.current().nextDouble() < homeBypassRatio;

            if (!homeBypass && poiManager != null && poiManager.hasPOIs()) {
                PointOfInterest homePOI = poiManager.selectPOIForZone(homeZone.getZoneId(), null);
                if (homePOI != null) {
                    double[] jHome = pointGenerator.jitterPoint(
                        homePOI.getLongitude(), homePOI.getLatitude(), homeZone.getRadiusKm());
                    return new HomeLocation(jHome[0], jHome[1], homePOI.getPoiId());
                }
            }
            double[] homePoint = pointGenerator.generatePointInZone(homeZone);
            return new HomeLocation(homePoint[0], homePoint[1], null);
        } else {
            double[] homePoint = pointGenerator.generatePointInZone(homeZone);
            return new HomeLocation(homePoint[0], homePoint[1], null);
        }
    }

    // ========================================================================
    // VEHICLE SIZE ASSIGNMENT (thread-safe)
    // ========================================================================

    private static class VehicleSpec {
        final String size;
        final double capacityTons;

        VehicleSpec(String size, double capacityTons) {
            this.size = size;
            this.capacityTons = capacityTons;
        }
    }

    private VehicleSpec assignVehicleSpec(TruckType truckType) {
        double[] vehicleProbs = getVehicleProbs(truckType);
        double sizeRoll = ThreadLocalRandom.current().nextDouble();

        String vehicleSize;
        double capacityTons;

        if (sizeRoll < vehicleProbs[0]) {
            vehicleSize = "light";
            capacityTons = config.getCapacityLightTons();
        } else if (sizeRoll < vehicleProbs[0] + vehicleProbs[1]) {
            vehicleSize = "small";
            capacityTons = config.getCapacitySmallTons();
        } else if (sizeRoll < vehicleProbs[0] + vehicleProbs[1] + vehicleProbs[2]) {
            vehicleSize = "medium";
            capacityTons = config.getCapacityMediumTons();
        } else {
            vehicleSize = "heavy";
            capacityTons = config.getCapacityLargeTons();
        }

        if (!truckType.isVehicleSizeAllowed(vehicleSize)) {
            throw new IllegalStateException("Vehicle size " + vehicleSize +
                " not allowed for truck type " + truckType);
        }

        return new VehicleSpec(vehicleSize, capacityTons);
    }

    private double[] getVehicleProbs(TruckType truckType) {
        switch (truckType) {
            case DELIVERY: {
                double[] raw = config.getDeliveryVehicleProbs();
                double sum = raw[0] + raw[1];
                return new double[]{raw[0] / sum, raw[1] / sum, 0.0, 0.0};
            }
            case LONG_HAUL: {
                double[] raw = config.getLongHaulVehicleProbs();
                double sum = raw[2] + raw[3];
                if (sum > 0) {
                    return new double[]{0.0, 0.0, raw[2] / sum, raw[3] / sum};
                }
                return new double[]{0.0, 0.0, 0.0, 1.0};
            }
            case MIXED_OPERATION: {
                double[] raw = config.getUrbanVehicleProbs();
                double sum = raw[1] + raw[2];
                if (sum > 0) {
                    return new double[]{0.0, raw[1] / sum, raw[2] / sum, 0.0};
                }
                return new double[]{0.0, 0.0, 1.0, 0.0};
            }
            default:
                return new double[]{0.25, 0.25, 0.25, 0.25};
        }
    }

    // ========================================================================
    // GOODS TYPE & SHIFT TIMING (thread-safe versions)
    // ========================================================================

    private String selectGoodsTypeThreadSafe() {
        String[] types = config.getGoodsTypes();
        double[] probs = config.getGoodsTypeProbabilities();

        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < types.length; i++) {
            cumulative += probs[i];
            if (roll <= cumulative) {
                return types[i];
            }
        }

        return types[0];
    }

    private long selectShiftStartThreadSafe() {
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < config.getShiftStart1Probability()) {
            return config.getShiftStart1Time();
        } else if (roll < config.getShiftStart1Probability() + config.getShiftStart2Probability()) {
            return config.getShiftStart2Time();
        } else {
            return config.getShiftStart3Time();
        }
    }

    private int generateShiftDurationThreadSafe() {
        double hours = config.getShiftDurationAverage() +
            ThreadLocalRandom.current().nextGaussian() * config.getShiftDurationStddev();
        hours = Math.max(config.getShiftDurationMin(),
            Math.min(config.getShiftDurationMax(), hours));
        return (int) hours;
    }
}
