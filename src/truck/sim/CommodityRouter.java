package truck.sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Commodity-based routing and vehicle selection.
 * Based on Tokyo MFS data (Files 03, 04, 06).
 *
 * Updated Phase 1: Loads configuration from CSVs.
 * - loading_rates.csv
 * - loading_constraints.csv
 *
 * @version 2.0
 */
public class CommodityRouter {

    // Maps
    // Commodity -> Vehicle Size -> Loading Rate
    private final Map<String, Map<String, Double>> loadingRates;
    // Commodity -> Weight Constraint Probability
    private final Map<String, Double> weightConstraintProbs;
    // Origin Facility Type -> Destination Facility Type -> Probability
    private final Map<String, Map<String, Double>> facilityFlows;
    // Commodity -> TimeWindow [earliest_hour, latest_hour]
    private final Map<String, int[]> timeWindows;
    // Commodity -> Time Window Required Percentage (0.0-1.0)
    private final Map<String, Double> timeWindowRequiredPct;
    // Origin Industry -> Destination Industry -> Flow Probability
    private final Map<String, Map<String, Double>> industryFlows;
    
    // Default fallback values
    private static final double DEFAULT_LOADING_RATE = 0.70;
    private static final double DEFAULT_WEIGHT_CONSTRAINT_PROB = 0.50;

    /**
     * Constructor - initialize with MFS commodity data.
     */
    public CommodityRouter() {
        this.loadingRates = new HashMap<>();
        this.weightConstraintProbs = new HashMap<>();
        this.facilityFlows = new HashMap<>();
        this.timeWindows = new HashMap<>();
        this.timeWindowRequiredPct = new HashMap<>();
        this.industryFlows = new HashMap<>();

        // Load configurations
        loadLoadingRates("config/truck/operations/loading_rates.csv");
        loadLoadingConstraints("config/truck/operations/loading_constraints.csv");
        loadFacilityFlows("config/truck/flows/facility_flows.csv");
        loadTimeWindows("config/truck/operations/time_windows.csv");
        loadIndustryFlows("config/truck/flows/industry_flows.csv");
    }

    private void loadFacilityFlows(String filePath) {
        System.out.println("[Config] Loading facility flows from: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // Header
            if (line == null) return;
            
            String[] headers = line.split(",");
            // headers: origin_facility, office, factory, store, logistics, residential, construction, other
            
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= headers.length) {
                    String origin = parts[0].trim();
                    Map<String, Double> destProbs = new HashMap<>();
                    
                    for (int i = 1; i < parts.length; i++) {
                        String destType = headers[i].trim();
                        try {
                            double prob = Double.parseDouble(parts[i].trim());
                            destProbs.put(destType, prob);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    facilityFlows.put(origin, destProbs);
                }
            }
        } catch (IOException e) {
            System.err.println("[Error] Failed to load facility flows: " + e.getMessage());
        }
    }

    /**
     * Load time windows from MFS File 05 data.
     * CSV format: commodity,window_required_pct,earliest_hour,latest_hour,notes
     */
    private void loadTimeWindows(String filePath) {
        System.out.println("[Config] Loading time windows from: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            // Skip header
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String commodity = parts[0].trim();
                    double requiredPct = Double.parseDouble(parts[1].trim());
                    int earliestHour = Integer.parseInt(parts[2].trim());
                    int latestHour = Integer.parseInt(parts[3].trim());

                    timeWindows.put(commodity, new int[]{earliestHour, latestHour});
                    timeWindowRequiredPct.put(commodity, requiredPct);
                }
            }
            System.out.println("[Config] Loaded " + timeWindows.size() + " commodity time windows");
        } catch (IOException | NumberFormatException e) {
            System.err.println("[Error] Failed to load time windows: " + e.getMessage());
        }
    }

    /**
     * Load industry flow matrix from MFS File 06 data.
     * CSV format: origin_industry,dest_industry1,dest_industry2,...
     */
    private void loadIndustryFlows(String filePath) {
        System.out.println("[Config] Loading industry flows from: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // Header row with industry codes
            if (line == null) return;

            String[] destIndustries = line.split(",");
            // First column is origin_industry label

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String originIndustry = parts[0].trim();
                    Map<String, Double> destProbs = new HashMap<>();

                    for (int i = 1; i < parts.length && i < destIndustries.length; i++) {
                        try {
                            double prob = Double.parseDouble(parts[i].trim());
                            if (prob > 0) {
                                destProbs.put(destIndustries[i].trim(), prob);
                            }
                        } catch (NumberFormatException e) {
                            // Skip non-numeric values
                        }
                    }
                    if (!destProbs.isEmpty()) {
                        industryFlows.put(originIndustry, destProbs);
                    }
                }
            }
            System.out.println("[Config] Loaded " + industryFlows.size() + " industry flow origins");
        } catch (IOException e) {
            System.err.println("[Error] Failed to load industry flows: " + e.getMessage());
        }
    }

    /**
     * Select destination facility type based on origin facility type.
     * Uses MFS File 07 probability matrix.
     *
     * @param originType The facility type of the origin
     * @return The selected destination facility type (as string key from CSV)
     */
    public String selectDestinationFacilityType(FacilityType originType) {
        // Map sim FacilityType to CSV key
        String originKey = mapFacilityTypeToKey(originType);
        
        if (!facilityFlows.containsKey(originKey)) {
            return "mixed"; // fallback
        }
        
        Map<String, Double> probs = facilityFlows.get(originKey);
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;
        
        for (Map.Entry<String, Double> entry : probs.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return "mixed";
    }
    
    private String mapFacilityTypeToKey(FacilityType type) {
        // Map Sim enum to MFS CSV keys matching facility_flows.csv headers:
        // office, factory, store, logistics, residential, construction, other
        // (Labels normalized in mfs/extract_phase2_3.py from File 07's raw long labels.)
        switch (type) {
            case INDUSTRIAL: return "factory";
            case LOGISTICS_HUB:
            case MEDIUM_WAREHOUSE:
            case LARGE_DISTRIBUTION: return "logistics";
            case SMALL_RETAIL: return "store";
            case RESIDENTIAL: return "residential";
            case CONSTRUCTION_SITE: return "construction";
            case MIXED: return "other";
            default: return "other";
        }
    }
    
    /**
     * Map CSV destination key back to FacilityType preference
     */
    public FacilityType mapKeyToFacilityType(String key) {
        switch (key) {
            case "factory": return FacilityType.INDUSTRIAL;
            case "logistics": return FacilityType.LOGISTICS_HUB; // Can also be MEDIUM_WAREHOUSE
            case "store": return FacilityType.SMALL_RETAIL;
            case "residential": return FacilityType.RESIDENTIAL;
            case "construction": return FacilityType.CONSTRUCTION_SITE;
            case "office": return FacilityType.MIXED; // Map office to mixed for now as we don't have OFFICE type
            default: return FacilityType.MIXED;
        }
    }

    private void loadLoadingRates(String filePath) {
        System.out.println("[Config] Loading loading rates from: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            // Skip header
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String commodity = parts[0].trim();
                    String vehicleSize = parts[1].trim();
                    double rate = Double.parseDouble(parts[2].trim());
                    
                    loadingRates.computeIfAbsent(commodity, k -> new HashMap<>())
                               .put(vehicleSize, rate);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[Error] Failed to load loading rates: " + e.getMessage());
        }
    }

    private void loadLoadingConstraints(String filePath) {
        System.out.println("[Config] Loading loading constraints from: " + filePath);
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            // Skip header
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String commodity = parts[0].trim();
                    double prob = Double.parseDouble(parts[1].trim());
                    weightConstraintProbs.put(commodity, prob);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[Error] Failed to load loading constraints: " + e.getMessage());
        }
    }

    /**
     * Get loading rate for a commodity-vehicle combination.
     *
     * @param commodityType The commodity type
     * @param vehicleSize The vehicle size
     * @return Loading rate (0.0-1.0)
     */
    public double getLoadingRate(String commodityType, String vehicleSize) {
        // Normalize keys
        String commKey = normalizeCommodity(commodityType);
        String sizeKey = normalizeVehicleSize(vehicleSize);
        
        if (loadingRates.containsKey(commKey)) {
            return loadingRates.get(commKey).getOrDefault(sizeKey, DEFAULT_LOADING_RATE);
        }
        return DEFAULT_LOADING_RATE;
    }

    /**
     * Check if commodity is weight-limited (vs volume-limited).
     *
     * @param commodityType The commodity type
     * @return true if weight-limited, false if volume-limited
     */
    public boolean isWeightLimited(String commodityType) {
        String commKey = normalizeCommodity(commodityType);
        double prob = weightConstraintProbs.getOrDefault(commKey, DEFAULT_WEIGHT_CONSTRAINT_PROB);
        return ThreadLocalRandom.current().nextDouble() < prob;
    }

    /**
     * Calculate actual cargo weight based on loading rate and vehicle capacity.
     */
    public double calculateActualCargo(String commodityType, String vehicleSize,
                                       double vehicleCapacity, double desiredCargoWeight) {
        double loadingRate = getLoadingRate(commodityType, vehicleSize);

        // Maximum cargo based on loading rate
        double maxCargo = vehicleCapacity * loadingRate;

        // Return minimum of desired and maximum
        return Math.min(desiredCargoWeight, maxCargo);
    }
    
    // Helper to normalize keys to match CSV
    private String normalizeCommodity(String input) {
        // Map simulation types to CSV keys if necessary
        // Currently they seem to match 1:1 based on my extraction script
        return input; 
    }
    
    private String normalizeVehicleSize(String input) {
        // Map simulation vehicle sizes to CSV keys (heavy, medium, small, light)
        // TruckTrip uses "large", "medium", "small" (and maybe "light"?)
        // TruckConfig has vehicle.capacity.large.tons
        // TruckSimulation fleet init uses: HEAVY, MEDIUM, SMALL, LIGHT
        // TruckAgent uses String vehicleSize.
        
        if (input == null) return "medium";
        String lower = input.toLowerCase();
        if (lower.contains("heavy") || lower.contains("large") || lower.contains("10t")) return "heavy";
        if (lower.contains("medium") || lower.contains("4t")) return "medium";
        if (lower.contains("small") || lower.contains("2t")) return "small";
        if (lower.contains("light")) return "light";
        return "medium";
    }

    /**
     * Select commodity type based on truck type and zone characteristics.
     *
     * Calibrated to match MFS commodity mix targets:
     *   daily_necessities: 27.0%
     *   agricultural_food: 22.0%
     *   light_industrial: 16.0%
     *   publications: 11.0%
     *   machinery: 7.0%
     *   forestry_mineral: 7.0%
     *   metal_products: 5.0%
     *   ceramic_chemical: 3.0%
     *   special_products: 2.0%
     *
     * @version 2.3 - Balanced commodity distribution
     */
    public String selectCommodityForTruckType(TruckType truckType) {
        // Commodity distribution varies by truck type
        double roll = ThreadLocalRandom.current().nextDouble();

        switch (truckType) {
            case DELIVERY:
                // DELIVERY (63% of trips): Consumer + some industrial commodities
                // Rebalanced for reduced empty trip model (7% empty → more DELIVERY trips)
                if (roll < 0.32) return "daily_necessities";      // 32%
                else if (roll < 0.60) return "agricultural_food"; // 28%
                else if (roll < 0.76) return "light_industrial";  // 16%
                else if (roll < 0.91) return "publications";      // 15%
                else if (roll < 0.94) return "machinery";         // 3%
                else if (roll < 0.97) return "forestry_mineral";  // 3%
                else if (roll < 0.99) return "metal_products";    // 2%
                else return "ceramic_chemical";                   // 1%

            case LONG_HAUL:
                // LONG_HAUL (17% of trips): Industrial + bulk commodities
                // Calibrated: ceramic_chemical 9%, special_products 5% to match MFS targets
                if (roll < 0.22) return "forestry_mineral";       // 22%
                else if (roll < 0.40) return "machinery";         // 18%
                else if (roll < 0.49) return "ceramic_chemical";  // 9%
                else if (roll < 0.61) return "metal_products";    // 12%
                else if (roll < 0.76) return "light_industrial";  // 15%
                else if (roll < 0.95) return "daily_necessities"; // 19%
                else return "special_products";                   // 5%

            case MIXED_OPERATION:
                // MIXED (23% of trips): Balanced across all commodity types
                if (roll < 0.18) return "daily_necessities";      // 18%
                else if (roll < 0.42) return "agricultural_food"; // 24%
                else if (roll < 0.55) return "light_industrial";  // 13%
                else if (roll < 0.67) return "machinery";         // 12%
                else if (roll < 0.77) return "forestry_mineral";  // 10%
                else if (roll < 0.85) return "publications";      // 8%
                else if (roll < 0.93) return "metal_products";    // 8%
                else if (roll < 0.97) return "special_products";  // 4%
                else return "ceramic_chemical";                   // 3%

            default:
                return "daily_necessities";
        }
    }
    
    // ============================================================================
    // TIME WINDOW METHODS (MFS File 05)
    // ============================================================================

    /**
     * Get time window for a commodity type.
     *
     * @param commodityType The commodity type
     * @return int array [earliest_hour, latest_hour] or null if no window defined
     */
    public int[] getTimeWindow(String commodityType) {
        String commKey = normalizeCommodity(commodityType);
        return timeWindows.getOrDefault(commKey, null);
    }

    /**
     * Check if a trip for this commodity requires a time window constraint.
     * Uses probability from MFS File 05.
     *
     * @param commodityType The commodity type
     * @return true if this trip should have time window constraint
     */
    public boolean requiresTimeWindow(String commodityType) {
        String commKey = normalizeCommodity(commodityType);
        double prob = timeWindowRequiredPct.getOrDefault(commKey, 0.0);
        return ThreadLocalRandom.current().nextDouble() < prob;
    }

    /**
     * Check if time windows are loaded.
     */
    public boolean hasTimeWindowData() {
        return !timeWindows.isEmpty();
    }

    // ============================================================================
    // INDUSTRY FLOW METHODS (MFS File 06)
    // ============================================================================

    /**
     * Select destination industry based on origin industry.
     * Uses MFS File 06 inter-industry flow probabilities.
     *
     * @param originIndustry The origin industry code
     * @return Selected destination industry code
     */
    public String selectDestinationIndustry(String originIndustry) {
        if (!industryFlows.containsKey(originIndustry)) {
            return originIndustry; // fallback: same industry
        }

        Map<String, Double> probs = industryFlows.get(originIndustry);
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : probs.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }
        return originIndustry; // fallback
    }

    /**
     * Check if industry flows are loaded.
     */
    public boolean hasIndustryFlowData() {
        return !industryFlows.isEmpty();
    }

    /**
     * Get all known industry codes.
     */
    public java.util.Set<String> getKnownIndustries() {
        return industryFlows.keySet();
    }
}