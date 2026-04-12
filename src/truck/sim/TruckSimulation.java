package truck.sim;

import truck.sim.spatial.GeoValidator;
import truck.sim.spatial.PointGenerator;
import util.MetricsDashboard;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrator for the Tokyo Truck Agent-Based Model.
 *
 * <p>Coordinates initialization, trip generation, validation, and export.
 * Domain logic is delegated to focused subsystems:
 * <ul>
 *   <li>{@link ZoneLoader} — zone CSV parsing, subsystem wiring, data loading</li>
 *   <li>{@link FleetFactory} — heterogeneous truck fleet generation</li>
 *   <li>{@link DestinationSelector} — destination selection strategies (O-D, POI, facility, zone)</li>
 *   <li>{@link ZoneManager} — zone lookup and Haversine distance calculation</li>
 *   <li>{@link truck.sim.spatial.GeoValidator} — multi-layer spatial validation (land, water, raster)</li>
 *   <li>{@link truck.sim.spatial.PointGenerator} — coordinate generation with land-use filtering</li>
 * </ul>
 *
 * <p>Based on Tokyo Metropolitan Freight Survey (H25/2013):
 * 327,108 vehicles/day, 1,726,420 tons/day across 72 zones.
 *
 * @author Truck ABM Framework
 * @version 2.1
 */
public class TruckSimulation {

    // Configuration
    private TruckConfig config;
    private MetropolitanConfig metroConfig;
    private MetricsTracker metricsTracker;

    // Default config file path
    private static final String DEFAULT_CONFIG_FILE = "config/truck/truck_config.properties";

    // Core data structures
    private List<TruckAgent> truckFleet;
    private List<TruckTrip> allTrips;
    private List<DeliveryZone> deliveryZones;
    private Random random;  // Seeded Random for deterministic initialization only (fleet/zones)
    private AtomicLong tripIdCounter;

    // Simulation components
    private OriginDestinationMatrix odMatrix;
    private GATargetsLoader gaTargets;
    private CommodityRouter commodityRouter;
    private TripGenerator tripGenerator;
    private POIManager poiManager;
    private GenerationAttractionBalancer gaBalancer;

    // Extracted subsystems (v2.0 refactoring)
    private GeoValidator geoValidator;
    private PointGenerator pointGenerator;
    private ZoneManager zoneManager;
    private DestinationSelector destinationSelector;

    // Network-aware point generation
    private truck.sim.spatial.TransportNetworkIndex networkIndex;

    /**
     * Constructor
     */
    public TruckSimulation() {
        this.config = TruckConfig.getInstance();
        this.truckFleet = new ArrayList<>();
        this.allTrips = new ArrayList<>();
        this.deliveryZones = new ArrayList<>();
        this.tripIdCounter = new AtomicLong(0);
        this.geoValidator = new GeoValidator();
        this.zoneManager = new ZoneManager(config);
    }

    /**
     * Load configuration and initialize random generator
     */
    public void loadConfiguration(String configPath) {
        try {
            config.loadFromFile(configPath);
            System.out.println("[CHECKPOINT] Configuration loaded from: " + configPath);

            // Initialize random generator with seed from config
            long seed = config.getRandomSeed();
            this.random = new Random(seed);

            // Initialize metropolitan configuration
            String metroMode = config.getMetroMode();
            String primaryMetro = config.getMetroPrimary();

            if (metroMode.equalsIgnoreCase("INTER")) {
                String secondaryMetro = config.getMetroSecondary();
                this.metroConfig = new MetropolitanConfig(primaryMetro, secondaryMetro);
                System.out.println("[CHECKPOINT] Metropolitan mode: INTER");
                System.out.println("  Primary: " + primaryMetro);
                System.out.println("  Secondary: " + secondaryMetro);
            } else {
                this.metroConfig = new MetropolitanConfig(primaryMetro);
                System.out.println("[CHECKPOINT] Metropolitan mode: INTRA");
                System.out.println("  Metro area: " + primaryMetro);
            }

            System.out.println(metroConfig.getSummary());

            // NOTE: Geography bounds are NOT set here from metro config.
            // They are computed from the actual loaded zone envelopes after
            // initializeDeliveryZonesDual() completes, so all 72 MFS zones
            // (including MFS67-MFS71 inter-regional) generate home points in
            // their correct geographic locations rather than being clamped to Tokyo.

        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ========================================================================
    // ZONE INITIALIZATION (delegated to ZoneLoader)
    // ========================================================================

    /**
     * Initialize delivery zones and all downstream subsystems.
     * Delegates to {@link ZoneLoader} and unpacks the result.
     */
    private void initializeZones(boolean isDualMode, String intraZonesFile, String interZonesFile) {
        initializeZones(isDualMode, false, intraZonesFile, interZonesFile);
    }

    private void initializeZones(boolean isDualMode, boolean isExpandedMode,
                                  String intraZonesFile, String interZonesFile) {
        ZoneLoader loader = new ZoneLoader(config, random, geoValidator, zoneManager, metroConfig);

        ZoneLoadResult result;
        if (isExpandedMode) {
            result = loader.loadExpanded();
        } else if (isDualMode) {
            result = loader.loadDual(intraZonesFile, interZonesFile);
        } else {
            result = loader.loadSingle();
        }

        // Unpack results into simulation fields
        this.deliveryZones = result.deliveryZones;
        this.odMatrix = result.odMatrix;
        this.gaTargets = result.gaTargets;
        this.commodityRouter = result.commodityRouter;
        this.tripGenerator = result.tripGenerator;
        this.poiManager = result.poiManager;
        this.gaBalancer = result.gaBalancer;
        this.metricsTracker = result.metricsTracker;
        this.destinationSelector = result.destinationSelector;
        this.pointGenerator = result.pointGenerator;
        this.networkIndex = result.networkIndex;
    }

    // ========================================================================
    // FLEET INITIALIZATION (delegated to FleetFactory)
    // ========================================================================

    /**
     * Initialize truck fleet. Delegates to {@link FleetFactory}.
     */
    private void initializeTrucks() {
        FleetFactory factory = new FleetFactory(config, random,
            deliveryZones, odMatrix, gaTargets,
            poiManager, pointGenerator, zoneManager);
        this.truckFleet = factory.createFleet();
    }

    // ========================================================================
    // TRIP GENERATION
    // ========================================================================

    /**
     * Generate trips for all trucks with empty trip support.
     *
     * <p>Survey data implementation:
     * <ul>
     *   <li>Cargo weight: gamma distribution (shape=2.0, scale=2.40) for mean~4.8 tons</li>
     *   <li>Empty trip probability: 35%</li>
     *   <li>Average 8 trips per truck (Gaussian distribution)</li>
     *   <li>Loading rates by commodity and vehicle size</li>
     * </ul>
     */
    private void generateTrips() {
        int nThreads = Runtime.getRuntime().availableProcessors();
        int totalTrucks = truckFleet.size();
        long tripGenStart = System.currentTimeMillis();
        System.out.println("[CHECKPOINT] Generating trips (delivery + empty) using " +
            nThreads + " threads for " + totalTrucks + " trucks...");

        // Parallel execution with thread-local trip lists
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        AtomicInteger processedCount = new AtomicInteger(0);
        int progressInterval = Math.max(1, totalTrucks / 10);

        // Partition fleet into chunks
        int chunkSize = (totalTrucks + nThreads - 1) / nThreads;
        List<Future<List<TruckTrip>>> futures = new ArrayList<>();

        for (int t = 0; t < nThreads; t++) {
            int start = t * chunkSize;
            int end = Math.min(start + chunkSize, totalTrucks);
            if (start >= end) break;
            List<TruckAgent> chunk = truckFleet.subList(start, end);

            futures.add(executor.submit(() -> {
                List<TruckTrip> localTrips = new ArrayList<>();
                for (TruckAgent truck : chunk) {
                    List<TruckTrip> truckTrips = generateTripsForTruck(truck);
                    localTrips.addAll(truckTrips);

                    int done = processedCount.incrementAndGet();
                    if (done % progressInterval == 0) {
                        int pct = (done * 100) / totalTrucks;
                        System.out.println("[PROGRESS] " + done + "/" + totalTrucks +
                            " trucks (" + pct + "%) — ~" + (localTrips.size() * nThreads) + " trips est.");
                    }
                }
                return localTrips;
            }));
        }

        // Merge thread-local trip lists
        try {
            for (Future<List<TruckTrip>> f : futures) {
                allTrips.addAll(f.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("[FATAL] Parallel trip generation failed", e);
        }
        executor.shutdown();

        // Deferred metrics recording (single-threaded, fast)
        // G-A recording already happened during parallel phase (ConcurrentHashMap)
        Map<Integer, TruckAgent> truckMap = new HashMap<>(truckFleet.size());
        for (TruckAgent t : truckFleet) {
            truckMap.put(t.getTruckId(), t);
        }
        for (TruckTrip trip : allTrips) {
            TruckAgent truck = truckMap.get(trip.getTruckId());
            if (truck != null) {
                metricsTracker.recordTrip(trip, truck, trip.isInterMetro());
            }
        }

        // Summary statistics
        long deliveryCount = allTrips.stream().filter(TruckTrip::isCargoLoaded).count();
        long emptyCount = allTrips.size() - deliveryCount;
        double totalCargo = allTrips.stream()
            .filter(TruckTrip::isCargoLoaded)
            .mapToDouble(TruckTrip::getCargoWeightTons).sum();
        double avgCargoWeight = deliveryCount > 0 ? totalCargo / deliveryCount : 0.0;
        double emptyRatio = allTrips.isEmpty() ? 0.0 : 100.0 * emptyCount / allTrips.size();

        long tripGenElapsed = System.currentTimeMillis() - tripGenStart;
        System.out.println("[CHECKPOINT] Generated " + allTrips.size() + " trips (" + nThreads + " threads) in " +
            String.format("%.1f", tripGenElapsed / 1000.0) + "s:");
        System.out.println("  Delivery trips: " + deliveryCount +
            " (" + String.format("%.0f", totalCargo) + " tons total)");
        System.out.println("  Empty trips: " + emptyCount +
            " (" + String.format("%.1f%%", emptyRatio) + " empty ratio)");
        System.out.println("  Average cargo: " + String.format("%.2f", avgCargoWeight) + " tons/trip");
    }

    /**
     * Generates all trips for a single truck. Thread-safe — uses ThreadLocalRandom
     * and only mutates thread-local data (except gaBalancer which is ConcurrentHashMap-backed).
     *
     * @param truck The truck to generate trips for
     * @return List of all trips (delivery + empty) generated for this truck
     */
    private List<TruckTrip> generateTripsForTruck(TruckAgent truck) {
        List<TruckTrip> localTrips = new ArrayList<>();
        long currentTime = truck.getShiftStartTime();
        truck.setCurrentTime(currentTime);

        double lastDropoffLon = truck.getHomeLongitude();
        double lastDropoffLat = truck.getHomeLatitude();
        boolean hasLastDropoff = false;
        String lastDropoffPOIId = truck.getHomePOIId();

        // Compute zone ID ONCE per position, carry forward
        String currentZoneId = zoneManager.findZoneForLocation(lastDropoffLon, lastDropoffLat);

        int numTrips = calculateNumTripsForTruck(truck);

        for (int tripNum = 0; tripNum < numTrips; tripNum++) {
            // Generate empty trip if needed (repositioning)
            TruckTrip emptyTrip = generateEmptyTripIfNeeded(truck, hasLastDropoff,
                                                           lastDropoffLon, lastDropoffLat,
                                                           currentTime, currentZoneId);
            if (emptyTrip != null) {
                emptyTrip.setInterMetro(false);
                truck.addTrip(emptyTrip);
                localTrips.add(emptyTrip);

                long travelTime = emptyTrip.calculateTravelTime(config.getRoutingAverageSpeedKmh());
                currentTime += travelTime;
                lastDropoffLon = emptyTrip.getDestLongitude();
                lastDropoffLat = emptyTrip.getDestLatitude();
                currentZoneId = emptyTrip.getDestZoneId();
                updateTruckState(truck, currentTime, lastDropoffLon, lastDropoffLat);
            }

            if (!truck.hasTimeInShift(3600)) {
                break;
            }

            // Generate delivery trip — pass current zone ID to avoid re-computation
            DeliveryTripResult result = generateDeliveryTrip(truck, lastDropoffLon, lastDropoffLat,
                                                  hasLastDropoff, currentTime,
                                                  lastDropoffPOIId, currentZoneId, tripNum);
            TruckTrip trip = result.trip;
            if (trip == null) {
                break;
            }

            currentTime = result.updatedTime;
            trip.setInterMetro(result.isInterMetro);
            lastDropoffPOIId = result.destPOIId;

            truck.addTrip(trip);
            localTrips.add(trip);

            currentTime = trip.getUnloadingEndTime() + (long)config.getRoutingBreakTimeSeconds();
            lastDropoffLon = trip.getDestLongitude();
            lastDropoffLat = trip.getDestLatitude();
            hasLastDropoff = true;
            // Update zone ID from trip destination (already computed in configureTripTiming)
            currentZoneId = trip.getDestZoneId();
            updateTruckState(truck, currentTime, lastDropoffLon, lastDropoffLat);
        }

        return localTrips;
    }

    // ========================================================================
    // TRIP GENERATION HELPERS
    // ========================================================================

    /**
     * Calculates the number of trips for a truck based on its type.
     * Uses truck-type-specific trip counts with Gaussian variation.
     */
    private int calculateNumTripsForTruck(TruckAgent truck) {
        int typeSpecificTrips = config.getTruckTripsForType(truck.getTruckType());
        int numTrips = (int)(typeSpecificTrips + ThreadLocalRandom.current().nextGaussian() * 1.0);
        return Math.max(1, Math.min(config.getTruckTripsMax(), numTrips));
    }

    /**
     * Generates loading time with Gaussian distribution (minimum 10 seconds).
     */
    private double generateLoadingTime() {
        return Math.max(
            config.getLoadingTimeAverage() + ThreadLocalRandom.current().nextGaussian() * config.getLoadingTimeStddev(),
            10.0
        );
    }

    /**
     * Generates unloading time with Gaussian distribution (minimum 5 seconds).
     */
    private double generateUnloadingTime() {
        return Math.max(
            config.getUnloadingTimeAverage() + ThreadLocalRandom.current().nextGaussian() * config.getUnloadingTimeStddev(),
            5.0
        );
    }

    /**
     * Updates truck state after completing a trip.
     */
    private void updateTruckState(TruckAgent truck, long currentTime, double longitude, double latitude) {
        truck.setCurrentTime(currentTime);
        truck.setCurrentLongitude(longitude);
        truck.setCurrentLatitude(latitude);
    }

    /**
     * Creates and configures an empty repositioning trip.
     * Accepts pre-computed origin zone ID to avoid redundant spatial lookups.
     */
    private TruckTrip createEmptyTrip(TruckAgent truck, double originLon, double originLat,
                                     double destLon, double destLat, long currentTime,
                                     double distance, String originZoneId) {
        TruckTrip emptyTrip = new TruckTrip(
            tripIdCounter.getAndIncrement(),
            truck.getTruckId(),
            originLon, originLat,
            destLon, destLat,
            currentTime,
            distance,
            truck.getVehicleSize()
        );

        long travelTime = emptyTrip.calculateTravelTime(config.getRoutingAverageSpeedKmh());
        emptyTrip.setLoadingStartTime(currentTime);
        emptyTrip.setDepartureTime(currentTime);
        emptyTrip.setArrivalTime(currentTime + travelTime);
        emptyTrip.setUnloadingEndTime(currentTime + travelTime);
        emptyTrip.setStatus(TruckStatus.EMPTY_RUNNING);

        emptyTrip.setOriginZoneId(originZoneId);
        emptyTrip.setDestZoneId(zoneManager.findZoneForLocation(destLon, destLat));

        return emptyTrip;
    }

    /**
     * Generates an empty repositioning trip if needed.
     * Accepts pre-computed current zone ID to avoid redundant spatial lookups.
     */
    private TruckTrip generateEmptyTripIfNeeded(TruckAgent truck, boolean hasLastDropoff,
                                               double lastDropoffLon, double lastDropoffLat,
                                               long currentTime, String currentZoneId) {
        if (!config.getUseEmptyTrips()) {
            return null;
        }

        if (ThreadLocalRandom.current().nextDouble() >= config.getEmptyTripProbability()) {
            return null;
        }

        DestinationResult emptyDest = destinationSelector.selectEmptyTripDestination(
            truck, lastDropoffLon, lastDropoffLat, currentTime);
        double[] nextPickup = emptyDest.coords;

        double emptyDistance = zoneManager.calculateDistanceFast(
            lastDropoffLon, lastDropoffLat,
            nextPickup[0], nextPickup[1]
        );

        if (emptyDistance <= config.getEmptyTripThresholdKm()) {
            return null;
        }

        return createEmptyTrip(truck, lastDropoffLon, lastDropoffLat,
                             nextPickup[0], nextPickup[1], currentTime, emptyDistance, currentZoneId);
    }

    /**
     * Configures all timing and properties for a delivery trip.
     * Accepts pre-computed zone IDs to avoid redundant spatial lookups.
     */
    private long configureTripTiming(TruckTrip trip, double loadingTime, double unloadingTime,
                                    long currentTime, String originZoneId, String destZoneId,
                                    String commodityType) {
        long loadingTimeSec = (long)(loadingTime * 60);
        long travelTime = trip.calculateTravelTime(config.getRoutingAverageSpeedKmh());
        long unloadingTimeSec = (long)(unloadingTime * 60);

        trip.setLoadingStartTime(currentTime);
        trip.setDepartureTime(currentTime + loadingTimeSec);
        trip.setArrivalTime(currentTime + loadingTimeSec + travelTime);
        trip.setUnloadingEndTime(currentTime + loadingTimeSec + travelTime + unloadingTimeSec);
        trip.setStatus(TruckStatus.IN_TRANSIT);

        trip.setOriginZoneId(originZoneId);
        trip.setDestZoneId(destZoneId);

        if (commodityRouter.requiresTimeWindow(commodityType)) {
            int[] window = commodityRouter.getTimeWindow(commodityType);
            if (window != null) {
                trip.setTimeWindow(window[0], window[1]);

                if (!trip.isWithinTimeWindow()) {
                    long earliestFeasible = trip.getEarliestFeasibleDeparture(config.getRoutingAverageSpeedKmh());
                    if (earliestFeasible > currentTime) {
                        currentTime = earliestFeasible;
                        trip.setLoadingStartTime(currentTime);
                        trip.setDepartureTime(currentTime + loadingTimeSec);
                        trip.setArrivalTime(currentTime + loadingTimeSec + travelTime);
                        trip.setUnloadingEndTime(currentTime + loadingTimeSec + travelTime + unloadingTimeSec);
                    }
                }
            }
        }

        return currentTime;
    }

    /**
     * Generates a complete delivery trip for a truck.
     * Computes origin zone ID ONCE and passes it through all sub-calls.
     */
    private DeliveryTripResult generateDeliveryTrip(TruckAgent truck, double lastDropoffLon, double lastDropoffLat,
                                         boolean hasLastDropoff, long currentTime,
                                         String originPOIId, String currentZoneId, int tourStopNumber) {
        boolean isInterMetro = metroConfig.shouldMakeInterMetroTrip(ThreadLocalRandom.current(), truck.getTruckType(), config);

        double[] origin = hasLastDropoff ?
            new double[]{lastDropoffLon, lastDropoffLat} :
            new double[]{truck.getHomeLongitude(), truck.getHomeLatitude()};

        // Use pre-computed zone ID (already resolved once per truck loop iteration)
        String originZoneId = currentZoneId;

        String commodityType = commodityRouter.selectCommodityForTruckType(truck.getTruckType());

        DestinationResult destResult;
        double distance;

        if (isInterMetro && metroConfig.isInterMetropolitan()) {
            destResult = destinationSelector.selectInterMetroDestination(origin);
            distance = destinationSelector.calculateInterMetroDistance();
        } else {
            isInterMetro = false;

            // V5.2: DELIVERY trucks use tour-based destination selection
            if (truck.getTruckType() == TruckType.DELIVERY) {
                double maxDistKm;
                double decayFactor;
                if (tourStopNumber == 0) {
                    // First stop: depot → first POI (longer trip allowed)
                    maxDistKm = config.getDeliveryFirstStopMaxKm();
                    decayFactor = config.getDeliveryDecayFirst();
                } else {
                    // Subsequent stops: POI → POI (short legs)
                    maxDistKm = config.getDeliveryStopToStopMaxKm();
                    decayFactor = config.getDeliveryDecaySubsequent();
                }

                destResult = destinationSelector.selectDeliveryTourStop(
                    truck, origin, maxDistKm, decayFactor,
                    originZoneId, currentTime, commodityType);
            } else {
                destResult = destinationSelector.selectIntraMetroDestination(
                    truck, origin, currentTime, commodityType, originZoneId);
            }

            if (destResult == null) {
                return DeliveryTripResult.atCapacity(currentTime);
            }

            distance = zoneManager.calculateDistanceFast(origin[0], origin[1], destResult.coords[0], destResult.coords[1]);

            // LONG_HAUL: enforce minimum distance floor (50 km) — if O-D balanced
            // result is too short, retry with inter-zone selection as fallback
            if (truck.getTruckType() == TruckType.LONG_HAUL && distance < 50.0) {
                // O-D matrix gave a nearby zone — acceptable for some trips, but
                // retry once for a longer destination to match long-haul character
                DestinationResult retry = destinationSelector.selectBalancedDestination(
                    truck, origin, originZoneId, currentTime, commodityType);
                if (retry != null) {
                    double retryDist = zoneManager.calculateDistanceFast(
                        origin[0], origin[1], retry.coords[0], retry.coords[1]);
                    if (retryDist >= 50.0) {
                        destResult = retry;
                        distance = retryDist;
                    }
                }
                // If still short, keep the O-D result — some long-haul trips are
                // legitimately intra-zone (loading at depot, delivering nearby)
            }

            // MIXED trucks: enforce maximum distance constraint
            if (truck.getTruckType() == TruckType.MIXED_OPERATION && distance > 80.0) {
                destResult = destinationSelector.selectNearbyDestination(truck, origin, 80.0, originZoneId);
                distance = zoneManager.calculateDistanceFast(origin[0], origin[1], destResult.coords[0], destResult.coords[1]);
            }
        }

        double[] destination = destResult.coords;

        TruckTrip.LoadingConstraint constraint =
            commodityRouter.isWeightLimited(commodityType) ?
            TruckTrip.LoadingConstraint.WEIGHT :
            TruckTrip.LoadingConstraint.CAPACITY;

        // Use pre-computed origin zone instead of another findZone() call
        DeliveryZone originZone = zoneManager.findZoneByZoneId(originZoneId);
        FacilityType originFacility = (originZone != null) ? originZone.getFacilityType() : FacilityType.MIXED;

        double cargoWeight = tripGenerator.generateCargoWeight(
            originFacility, commodityType, truck.getVehicleSize(),
            truck.getCapacityTons(), commodityRouter, constraint,
            originZoneId
        );

        double loadingTime = generateLoadingTime();
        double unloadingTime = generateUnloadingTime();

        TruckTrip trip = new TruckTrip(
            tripIdCounter.getAndIncrement(), truck.getTruckId(),
            origin[0], origin[1], destination[0], destination[1],
            currentTime, distance, commodityType,
            truck.getVehicleSize(), cargoWeight, truck.getCapacityTons(),
            loadingTime, unloadingTime
        );
        trip.setLoadingConstraint(constraint);

        if (originPOIId != null) {
            trip.setOriginFacilityId(originPOIId);
        }
        if (destResult.poiId != null) {
            trip.setDestFacilityId(destResult.poiId);
        }

        // Compute dest zone ID once (origin zone already known)
        String destZoneId = (destResult.zoneId != null) ? destResult.zoneId
            : zoneManager.findZoneForLocation(destination[0], destination[1]);

        currentTime = configureTripTiming(trip, loadingTime, unloadingTime,
                                         currentTime, originZoneId, destZoneId, commodityType);

        if (gaBalancer != null && !isInterMetro) {
            gaBalancer.recordTrip(originZoneId, destZoneId);
        }

        return new DeliveryTripResult(trip, currentTime, isInterMetro, destResult.poiId);
    }

    // ========================================================================
    // STATISTICS & REPORTING
    // ========================================================================

    /**
     * Print essential statistics summary.
     */
    private void printStatistics() {
        long deliveryTrips = allTrips.stream().filter(TruckTrip::isCargoLoaded).count();
        long emptyTrips = allTrips.stream().filter(t -> !t.isCargoLoaded()).count();

        double deliveryDistance = allTrips.stream()
            .filter(TruckTrip::isCargoLoaded)
            .mapToDouble(TruckTrip::getDistanceKm).sum();
        double emptyDistance = allTrips.stream()
            .filter(t -> !t.isCargoLoaded())
            .mapToDouble(TruckTrip::getDistanceKm).sum();

        double totalDistance = deliveryDistance + emptyDistance;
        double totalCargo = allTrips.stream()
            .filter(TruckTrip::isCargoLoaded)
            .mapToDouble(TruckTrip::getCargoWeightTons).sum();

        double emptyRunningRatio = 100.0 * emptyDistance / totalDistance;
        double avgDeliveryTripsPerTruck = (double) deliveryTrips / config.getTruckFleetSize();
        double avgCargoWeight = deliveryTrips > 0 ? totalCargo / deliveryTrips : 0.0;

        long heavyCount = truckFleet.stream().filter(t -> t.getVehicleSize().equals("heavy")).count();
        long mediumCount = truckFleet.stream().filter(t -> t.getVehicleSize().equals("medium")).count();
        long smallCount = truckFleet.stream().filter(t -> t.getVehicleSize().equals("small")).count();
        long lightCount = truckFleet.stream().filter(t -> t.getVehicleSize().equals("light")).count();

        System.out.println("\n[SUMMARY]");
        System.out.println("  Active fleet: " + config.getTruckFleetSize() + " trucks/day");
        System.out.println("  Registered fleet: " + config.getRegisteredFleetSize() + " trucks (MLIT)");
        System.out.println("  Operating rate: " +
            String.format("%.1f%%", 100.0 * config.getTruckOperatingRate()));
        System.out.println("  Fleet mix: Heavy=" + heavyCount + " (" + String.format("%.1f%%", 100.0*heavyCount/config.getTruckFleetSize()) + "), " +
            "Medium=" + mediumCount + " (" + String.format("%.1f%%", 100.0*mediumCount/config.getTruckFleetSize()) + "), " +
            "Small=" + smallCount + " (" + String.format("%.1f%%", 100.0*smallCount/config.getTruckFleetSize()) + "), " +
            "Light=" + lightCount + " (" + String.format("%.1f%%", 100.0*lightCount/config.getTruckFleetSize()) + ")");
        System.out.println("  Delivery trips: " + deliveryTrips +
            " (avg " + String.format("%.1f", avgDeliveryTripsPerTruck) + " per truck)");
        System.out.println("  Empty trips: " + emptyTrips);
        System.out.println("  Total distance: " + String.format("%.0f", totalDistance) + " km");
        System.out.println("  Empty running ratio: " + String.format("%.1f%%", emptyRunningRatio));
        System.out.println("  Total cargo: " + String.format("%.0f", totalCargo) + " tons");
        System.out.println("  Average cargo: " + String.format("%.2f", avgCargoWeight) + " tons/delivery");
    }

    // ========================================================================
    // SIMULATION EXECUTION
    // ========================================================================

    /**
     * Main simulation execution.
     */
    public void run(String[] args) {
        run(args, true, null, null);
    }

    /**
     * Main simulation execution with optional config loading.
     * Handles both SINGLE and DUAL zone modes based on parameters.
     *
     * @param args           CLI arguments (args[0] = config file path)
     * @param loadConfig     Whether to load configuration from file
     * @param intraZonesFile Intra-metro zones file for DUAL mode (null for SINGLE mode)
     * @param interZonesFile Inter-metro zones file for DUAL mode (null for SINGLE mode)
     */
    /**
     * Run in EXPANDED mode (nationwide 106 zones).
     */
    public void runExpanded(String[] args) {
        long simStart = System.currentTimeMillis();

        System.out.println("=================================================================");
        System.out.println("  TRUCK ABM V3.0 - EXPANDED NATIONWIDE (106 ZONES)");
        System.out.println("  MFS 2013 + prefecture-level disaggregation");
        System.out.println("=================================================================");

        // Initialize delivery zones in expanded mode
        long t0 = System.currentTimeMillis();
        initializeZones(false, true, null, null);
        long t1 = System.currentTimeMillis();
        System.out.println("[TIMING] Zone init: " + String.format("%.1f", (t1 - t0) / 1000.0) + "s");

        // Run same simulation phases as DUAL
        initializeTrucks();
        long t2 = System.currentTimeMillis();
        System.out.println("[TIMING] Fleet init: " + String.format("%.1f", (t2 - t1) / 1000.0) + "s");

        generateTrips();
        long t3 = System.currentTimeMillis();
        System.out.println("[TIMING] Trip gen: " + String.format("%.1f", (t3 - t2) / 1000.0) + "s");

        // Validation
        System.out.println("\n[CHECKPOINT] Running MFS validation...");
        ValidationEngine.ValidationReport validationReport = runMFSValidation();

        // Export
        System.out.println("\n[CHECKPOINT] Exporting results...");
        TruckDataExporter exporter = new TruckDataExporter(config.getOutputDirectory());
        exporter.setDeliveryZones(deliveryZones);
        exporter.exportAll(truckFleet, allTrips);

        try {
            metricsTracker.exportToCSV(exporter.getRunDirectory());
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to export metrics: " + e.getMessage());
        }

        // Unified dashboard + validation files
        writeTruckDashboard(exporter.getRunDirectory());
        if (validationReport != null) {
            writeTruckValidation(exporter.getRunDirectory(), validationReport);
        }

        long simEnd = System.currentTimeMillis();
        System.out.printf("\n[COMPLETE] Output: %s%n", exporter.getRunDirectory());
        System.out.printf("[TIMING] Total simulation: %.1fs%n", (simEnd - simStart) / 1000.0);
        if (validationReport != null) {
            System.out.printf("[VALIDATION] Grade: %s (%d/%d tests passed)%n",
                    validationReport.grade, validationReport.passedTests, validationReport.totalTests);
        }
        System.out.println("  Total trucks: " + truckFleet.size());
        System.out.println("  Total zones: " + deliveryZones.size() + " (expanded nationwide)");
    }

    public void run(String[] args, boolean loadConfig,
                    String intraZonesFile, String interZonesFile) {
        boolean isDualMode = (intraZonesFile != null && interZonesFile != null);
        long simStart = System.currentTimeMillis();

        System.out.println("=================================================================");
        if (isDualMode) {
            System.out.println("  TOKYO TRUCK ABM V2.1 - DUAL MODE (INTRA + INTER ZONES)");
        } else {
            System.out.println("  TOKYO TRUCK ABM V2.1 - PSEUDO PFLOW COMPATIBLE");
        }
        System.out.println("  Based on Tokyo Metropolitan Freight Survey (H25/2013)");
        System.out.println("=================================================================");

        // Load configuration (unless already loaded)
        if (loadConfig) {
            String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;
            loadConfiguration(configFile);
        }

        // Initialize delivery zones (must be after config and before trucks)
        long t0 = System.currentTimeMillis();
        initializeZones(isDualMode, intraZonesFile, interZonesFile);
        long t1 = System.currentTimeMillis();
        System.out.println("[TIMING] Zone init: " + String.format("%.1f", (t1 - t0) / 1000.0) + "s");

        // Run simulation phases
        initializeTrucks();
        long t2 = System.currentTimeMillis();
        System.out.println("[TIMING] Fleet init: " + String.format("%.1f", (t2 - t1) / 1000.0) + "s");

        generateTrips();
        long t3 = System.currentTimeMillis();
        printStatistics();

        // Initialize metrics tracker with truck fleet
        metricsTracker.initializeTrucks(truckFleet);

        // Finalize and print comprehensive metrics
        System.out.println("\n[CHECKPOINT] Finalizing metrics...");
        metricsTracker.calculateFinalMetrics();
        metricsTracker.printReport();

        if (gaBalancer != null) {
            gaBalancer.printSummary();
        }

        // Load MFS validation baseline and validate (if enabled)
        if (config.isMFSValidationEnabled()) {
            String baselinePath = "config/truck/validation/mfs_baseline.csv";
            metricsTracker.loadMFSBaseline(baselinePath);
            double tolerance = config.getMFSTolerancePct();
            metricsTracker.validateAgainstMFS(tolerance);
            System.out.println("[MFS] Validation complete");
        }

        // Comprehensive MFS Validation with Metrics
        System.out.println("\n[CHECKPOINT] Running comprehensive MFS validation...");
        ValidationEngine.ValidationReport validationReport = runMFSValidation();

        // Export results
        long t4 = System.currentTimeMillis();
        System.out.println("\n[CHECKPOINT] Exporting results...");
        System.out.println("[TIMING] Validation: " + String.format("%.1f", (t4 - t3) / 1000.0) + "s");
        TruckDataExporter exporter = new TruckDataExporter(config.getOutputDirectory());
        exporter.setDeliveryZones(deliveryZones);
        exporter.exportAll(truckFleet, allTrips);

        try {
            metricsTracker.exportToCSV(exporter.getRunDirectory());
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to export metrics: " + e.getMessage());
        }

        // Write unified dashboard.csv and validation.csv
        writeTruckDashboard(exporter.getRunDirectory());
        if (validationReport != null) {
            writeTruckValidation(exporter.getRunDirectory(), validationReport);
        }

        long t5 = System.currentTimeMillis();
        System.out.println("[TIMING] Export: " + String.format("%.1f", (t5 - t4) / 1000.0) + "s");

        long simElapsed = System.currentTimeMillis() - simStart;
        System.out.println("\n[COMPLETE] Output: " + exporter.getRunDirectory());
        System.out.println("[TIMING] Total simulation: " + String.format("%.1f", simElapsed / 1000.0) + "s" +
            " (" + String.format("%d:%02d", simElapsed / 60000, (simElapsed / 1000) % 60) + ")");
        System.out.println("[VALIDATION] Overall grade: " + validationReport.grade +
            " (" + validationReport.passedTests + "/" + validationReport.totalTests + " tests passed)");
        if (isDualMode) {
            System.out.println("  Total trucks: " + truckFleet.size() + " (target: 327,108)");
            System.out.println("  Total zones: " + deliveryZones.size() + " (intra + inter combined)");
        }
        destinationSelector.printDeliveryTourDiagnostics();
    }

    /**
     * Run comprehensive MFS validation with metrics calculation.
     */
    private ValidationEngine.ValidationReport runMFSValidation() {
        System.out.println("\n" + repeatString("=", 80));
        System.out.println("RUNNING MFS BASELINE VALIDATION");
        System.out.println(repeatString("=", 80));

        SimulationMetrics metrics = new SimulationMetrics(truckFleet, allTrips);
        metrics.calculateAllMetrics();
        metrics.printSummary();

        String baselinePath = "config/truck/validation/mfs_baseline.csv";
        ValidationEngine csvValidator = new ValidationEngine(baselinePath);

        ValidationEngine.ValidationReport report;
        if (csvValidator.hasBaseline()) {
            report = csvValidator.validateFromBaseline(truckFleet, allTrips, gaBalancer);
        } else {
            System.out.println("[Validation] CSV baseline not found, using hardcoded targets.");
            report = ValidationEngine.validateHardcoded(truckFleet, allTrips, gaBalancer);
        }
        report.printReport();

        System.out.println(repeatString("=", 80));
        return report;
    }

    /**
     * Helper method to repeat a string (Java 8 compatible).
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /** Write unified dashboard.csv for truck ABM. */
    private void writeTruckDashboard(String runDir) {
        int heavy = 0, medium = 0, small = 0, light = 0;
        int delivery = 0, longHaul = 0, mixed = 0;
        for (TruckAgent t : truckFleet) {
            switch (t.getVehicleSize()) {
                case "heavy": heavy++; break;
                case "medium": medium++; break;
                case "small": small++; break;
                case "light": light++; break;
            }
            TruckType tt = t.getTruckType();
            if (tt == TruckType.DELIVERY) delivery++;
            else if (tt == TruckType.LONG_HAUL) longHaul++;
            else if (tt == TruckType.MIXED_OPERATION) mixed++;
        }
        int totalTrips = allTrips.size();
        int deliveryTrips = 0, emptyTrips = 0;
        double totalDist = 0, deliveryDist = 0, emptyDist = 0, totalCargo = 0;
        for (TruckTrip trip : allTrips) {
            totalDist += trip.getDistanceKm();
            if (trip.getStatus() == TruckStatus.EMPTY_RUNNING) {
                emptyTrips++;
                emptyDist += trip.getDistanceKm();
            } else {
                deliveryTrips++;
                deliveryDist += trip.getDistanceKm();
                totalCargo += trip.getCargoWeightTons();
            }
        }
        double avgCargo = deliveryTrips > 0 ? totalCargo / deliveryTrips : 0;
        double avgDist = totalTrips > 0 ? totalDist / totalTrips : 0;
        double avgTripsPerTruck = truckFleet.size() > 0 ? (double) totalTrips / truckFleet.size() : 0;

        Map<String, Double> metrics = MetricsDashboard.buildTruckMetrics(
                truckFleet.size(), delivery, longHaul, mixed,
                heavy, medium, small, light,
                totalTrips, deliveryTrips, emptyTrips,
                totalDist, deliveryDist, emptyDist,
                totalCargo, avgCargo, avgTripsPerTruck, avgDist, null);

        MetricsDashboard.writeDashboard(runDir, "truck", null, metrics);
    }

    /** Write unified validation.csv for truck ABM. */
    private void writeTruckValidation(String runDir, ValidationEngine.ValidationReport report) {
        List<String[]> rows = new ArrayList<>();
        for (ValidationEngine.ValidationResult r : report.results) {
            rows.add(new String[]{
                    r.category, r.metric,
                    String.format("%.2f", r.actual),
                    String.format("%.2f", r.target),
                    String.format("%.0f", r.tolerance * 100),
                    String.format("%.1f", r.getErrorPercent()),
                    r.passed ? "PASS" : "FAIL"
            });
        }
        MetricsDashboard.writeValidation(runDir, rows, report.grade, report.passRate);
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        TruckConfig config = TruckConfig.getInstance();

        String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;
        try {
            config.loadFromFile(configFile);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to load config: " + e.getMessage());
            System.exit(1);
        }

        TruckConfig.SimulationMode mode = config.getSimulationMode();
        TruckSimulation sim = new TruckSimulation();
        sim.loadConfiguration(configFile);

        switch (mode) {
            case DUAL:
                System.out.println("[Config] Simulation mode: DUAL (intra + inter metropolitan)");
                String dualIntra = config.getProperty("zones.file.intra", "zones_intra_metro_combined.csv");
                String dualInter = config.getProperty("zones.file.inter", "zones_inter_metro.csv");
                sim.run(args, false, dualIntra, dualInter);
                break;

            case INTRA_METROPOLITAN:
                System.out.println("[Config] Simulation mode: INTRA_METROPOLITAN only");
                sim.config.setZonesFile(config.getProperty("zones.file.intra", "zones_tokyo_metro.csv"));
                sim.run(args, false, null, null);
                break;

            case INTER_METROPOLITAN:
                System.out.println("[Config] Simulation mode: INTER_METROPOLITAN only");
                sim.config.setZonesFile(config.getProperty("zones.file.inter", "zones_inter_metro.csv"));
                sim.run(args, false, null, null);
                break;

            case EXPANDED:
                System.out.println("[Config] Simulation mode: EXPANDED (nationwide 106 zones)");
                sim.runExpanded(args);
                break;

            default:
                System.err.println("[Error] Unknown simulation mode: " + mode);
                System.exit(1);
        }
    }
}
