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
    
    private static final double DEFAULT_MEAN_WEIGHT = 4.8;

    public TripGenerator(TruckConfig config) {
        this.config = config;
        this.subRegionRates = new HashMap<>();
        this.inoutRatios = new HashMap<>();
        this.facilityMeanWeights = new HashMap<>();
        
        loadSubRegionRates("config/truck/facilities/est_subregion.csv");
        loadInoutRatios("config/truck/operations/inout_ratios.csv");
        loadFacilityCargoWeights("config/truck/operations/cargo_weights.csv");
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
     * Calculate total trips to generate for a zone based on establishments.
     */
    public int generateTripsForZone(DeliveryZone zone, double scaleFactor) {
        String sub = zone.getSubRegion();
        if (sub == null) sub = "metropolitan_area_total";
        
        double totalLambda = 0.0;
        String[] facilities = {"factory", "logistics", "office", "store", "other"};
        
        for (String fac : facilities) {
            Map<String, Integer> industries = zone.getIndustryCounts(fac);
            for (Map.Entry<String, Integer> entry : industries.entrySet()) {
                String ind = entry.getKey();
                int count = entry.getValue();
                
                double rate = getRate(sub, ind, fac);
                totalLambda += count * rate;
            }
        }
        
        // Scale factor for simulation speed/fleet size
        totalLambda *= scaleFactor;
        
        int trips = samplePoisson(totalLambda);
        return Math.max(config.getTruckTripsMin(), Math.min(config.getTruckTripsMax(), trips));
    }
    
    private double getRate(String sub, String ind, String fac) {
        Map<String, Map<String, Double>> indMap = subRegionRates.get(sub);
        if (indMap == null) indMap = subRegionRates.get("metropolitan_area_total");
        if (indMap == null) return 5.0; // fallback
        
        Map<String, Double> facMap = indMap.get(ind);
        if (facMap == null) return 5.0;
        
        return facMap.getOrDefault(fac, 5.0);
    }

    /**
     * Generate cargo weight based on MFS File 18 utilization rates.
     * Calibrated for trip counts: DELIVERY=3, MIXED=2, LONG_HAUL=1.
     *
     * MFS targets (tons/truck/day):
     * - Light (<2t): 0.78 (capacity 2.0t, ~39% utilization)
     * - Small (2-4t): 2.72 (capacity 5.0t, ~54% utilization)
     * - Medium (4-10t): 7.64 (capacity 8.0t, ~76% utilization)
     * - Heavy (10t+): 11.46 (capacity 20.0t, ~57% utilization)
     */
    public double generateCargoWeight(FacilityType facilityType, String commodity, String vehicleSize,
                                      double vehicleCapacity, CommodityRouter router, LoadingConstraint constraint) {
        double cargoWeight;

        switch (vehicleSize.toLowerCase()) {
            case "light":
                // Target: 0.78t/truck/day ÷ ~2.8 loaded trips = ~0.28t/trip
                cargoWeight = generateGamma(1.5, 0.19);
                break;
            case "small":
                // Target: 2.72t/truck/day ÷ ~1.9 effective loaded trips = ~1.43t/trip
                cargoWeight = generateGamma(1.8, 0.80);
                break;
            case "medium":
                // Target: 7.64t/truck/day ÷ ~1.15 effective loaded trips = ~6.6t/trip
                cargoWeight = generateGamma(2.0, 3.50);
                break;
            case "heavy":
                // Target: 11.46t/truck/day ÷ ~1.0 loaded trip = ~11.5t/trip
                cargoWeight = generateGamma(2.5, 4.6);
                break;
            default:
                double mean = facilityMeanWeights.getOrDefault(facilityType, DEFAULT_MEAN_WEIGHT);
                cargoWeight = generateGamma(2.0, mean / 2.0);
                break;
        }

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
