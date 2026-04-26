package truck.sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import truck.sim.TruckTrip.LoadingConstraint;

/**
 * Facility-based trip generation using MFS data.
 * Updated Phase 3:
 * - Load establishment rates by subregion
 * - Load global inout ratios
 * - Calculate Gen/Attr based on establishment counts
 *
 * @version 3.0
 */
public class TripGenerator {

    private final TruckConfig config;
    
    // SubRegion -> Industry -> Facility -> Rate
    private final Map<String, Map<String, Map<String, Double>>> subRegionRates;
    // Facility -> Ratio
    private final Map<String, Double> inoutRatios;
    // FacilityType -> Mean Weight (Tons)
    private final Map<FacilityType, Double> facilityMeanWeights;
    private final ZoneCargoModel zoneCargoModel;

    private static final double DEFAULT_MEAN_WEIGHT = 4.8;

    public TripGenerator(TruckConfig config) {
        this(config, loadDefaultZoneCargoModel());
    }

    /** Test/DI constructor allowing a pre-built zone cargo model. */
    public TripGenerator(TruckConfig config, ZoneCargoModel zoneCargoModel) {
        this.config = config;
        this.zoneCargoModel = zoneCargoModel;
        this.subRegionRates = new HashMap<>();
        this.inoutRatios = new HashMap<>();
        this.facilityMeanWeights = new HashMap<>();

        loadSubRegionRates("config/truck/facilities/est_subregion.csv");
        loadInoutRatios("config/truck/operations/inout_ratios.csv");
        loadFacilityCargoWeights("config/truck/operations/cargo_weights.csv");
    }

    private static ZoneCargoModel loadDefaultZoneCargoModel() {
        try {
            return ZoneCargoModel.loadDefault();
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to load zone_cargo_gamma.csv — run "
                + "mfs/extract_phase4_zone_cargo_gamma.py first", e);
        }
    }
    
    private void loadSubRegionRates(String filePath) {
        System.out.println("[Config] Loading subregion establishment rates from: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String sub = parts[0].trim();
                    String ind = parts[1].trim();
                    // rate_office, rate_factory, rate_logistics, rate_other
                    double rOff = Double.parseDouble(parts[2]);
                    double rFac = Double.parseDouble(parts[3]);
                    double rLog = Double.parseDouble(parts[4]);
                    double rOth = Double.parseDouble(parts[5]);
                    
                    Map<String, Map<String, Double>> indMap = subRegionRates.computeIfAbsent(sub, k -> new HashMap<>());
                    Map<String, Double> facMap = indMap.computeIfAbsent(ind, k -> new HashMap<>());
                    facMap.put("office", rOff);
                    facMap.put("factory", rFac);
                    facMap.put("logistics", rLog);
                    facMap.put("other", rOth);
                    facMap.put("store", rOff * 0.5); // proxy
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading rates: " + e.getMessage());
        }
    }

    private void loadInoutRatios(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    inoutRatios.put(parts[0].trim(), Double.parseDouble(parts[1]));
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to load inout ratios from " + filePath + ": " + e.getMessage());
        }
    }

    private void loadFacilityCargoWeights(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    try {
                        FacilityType type = FacilityType.valueOf(parts[0].trim());
                        facilityMeanWeights.put(type, Double.parseDouble(parts[1]));
                    } catch (Exception e) {
                        System.err.println("[WARN] Failed to parse facility type in cargo weights: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to load facility cargo weights from " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Generate cargo weight from the per-zone Gamma profile derived from
     * MFS File 08 (see {@link ZoneCargoModel} and
     * {@code mfs/extract_phase4_zone_cargo_gamma.py}).
     *
     * <p>The raw draw is then capped by:
     * <ul>
     *   <li>The vehicle's physical capacity (always)</li>
     *   <li>The commodity's loading rate * capacity (only when the trip is
     *       capacity-constrained, not weight-constrained)</li>
     * </ul>
     *
     * @param facilityType    origin facility type — kept for the unknown-zone
     *                        fallback path that uses {@code facilityMeanWeights}
     * @param commodity       commodity key (used by loading-rate cap)
     * @param vehicleSize     "light" / "small" / "medium" / "heavy"
     * @param vehicleCapacity tons
     * @param router          source of commodity-specific loading rates
     * @param constraint      WEIGHT or CAPACITY
     * @param originZoneId    MFS zone id of the trip origin (e.g. "MFS01")
     */
    public double generateCargoWeight(FacilityType facilityType, String commodity,
                                      String vehicleSize, double vehicleCapacity,
                                      CommodityRouter router,
                                      LoadingConstraint constraint,
                                      String originZoneId) {
        double cargoWeight = zoneCargoModel.sampleWeight(originZoneId, vehicleSize);

        // TR3 verified (2026-04-22): LoadingConstraint IS enforced here, every trip,
        // via two caps below. Invariant at return: 0.1 ≤ cargoWeight ≤ vehicleCapacity
        // and, for CAPACITY-constrained trips, cargoWeight ≤ vehicleCapacity × loadingRate.

        // Always respect vehicle capacity (physical limit)
        cargoWeight = Math.min(cargoWeight, vehicleCapacity);

        // For capacity-constrained shipments, further limit by loading rate
        if (constraint == LoadingConstraint.CAPACITY) {
            double loadingRate = router.getLoadingRate(commodity, vehicleSize);
            cargoWeight = Math.min(cargoWeight, vehicleCapacity * loadingRate);
        }

        return Math.max(0.1, cargoWeight);
    }
    
    private double generateGamma(double shape, double scale) {
        if (shape < 1.0) return generateGamma(shape + 1.0, scale) * Math.pow(ThreadLocalRandom.current().nextDouble(), 1.0 / shape);
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do { x = randomGaussian(); v = 1.0 + c * x; } while (v <= 0.0);
            v = v * v * v;
            double u = ThreadLocalRandom.current().nextDouble();
            if (u < 1.0 - 0.0331 * (x * x * x * x)) return d * v * scale;
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v * scale;
        }
    }

    private int samplePoisson(double lambda) {
        if (lambda <= 0) return 0;
        if (lambda < 30.0) {
            double L = Math.exp(-lambda); int k = 0; double p = 1.0;
            do { k++; p *= ThreadLocalRandom.current().nextDouble(); } while (p > L);
            return k - 1;
        } else {
            double sample = lambda + Math.sqrt(lambda) * randomGaussian();
            return Math.max(0, (int) Math.round(sample));
        }
    }

    private double randomGaussian() {
        double u1 = ThreadLocalRandom.current().nextDouble(); double u2 = ThreadLocalRandom.current().nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
