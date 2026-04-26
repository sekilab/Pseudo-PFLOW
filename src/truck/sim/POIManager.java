package truck.sim;

import truck.sim.util.DistanceCalculator;

import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * POI Manager - Loads and manages Points of Interest for INTRA-metropolitan trips.
 *
 * Responsibilities:
 * - Load POIs from CSV files (logistic centers, retail shops, shopping malls)
 * - Provide smart POI selection for truck agents
 * - Filter POIs by type, zone, and distance
 *
 * @author Truck ABM Framework
 * @version 2.0
 */
public class POIManager {

    private List<PointOfInterest> allPOIs;
    private List<PointOfInterest> logisticCenters;
    private List<PointOfInterest> retailShops;
    private List<PointOfInterest> shoppingMalls;
    private List<PointOfInterest> industrialSites;
    private List<PointOfInterest> portsTerminals;
    private List<PointOfInterest> wholesaleFacilities;

    private Map<String, List<PointOfInterest>> poisByZone;  // zone_id -> POIs in zone

    // Spatial grid for fast proximity search (~0.1° cells ≈ 11km)
    private static final double GRID_CELL_SIZE = 0.1;  // degrees
    private Map<Long, List<PointOfInterest>> spatialGrid;

    // Water body validation checker (passed from TruckSimulation)
    // INVERTED LOGIC: Changed from waterChecker to landChecker
    // OLD: waterChecker returned TRUE if point was IN water (to reject)
    // NEW: landChecker returns TRUE if point is ON land (to accept)
    private BiPredicate<Double, Double> landChecker;

    // JAXA land use raster checker — returns land use category (0-15) at (lon, lat)
    private java.util.function.BiFunction<Double, Double, Integer> landUseChecker;

    // Network-aware POI filtering (Phase 3 - optional enhancement)
    private truck.sim.spatial.TransportNetworkIndex networkIndex;

    /**
     * Constructor.
     */
    public POIManager() {
        this.allPOIs = new ArrayList<>();
        this.logisticCenters = new ArrayList<>();
        this.retailShops = new ArrayList<>();
        this.shoppingMalls = new ArrayList<>();
        this.industrialSites = new ArrayList<>();
        this.portsTerminals = new ArrayList<>();
        this.wholesaleFacilities = new ArrayList<>();
        this.poisByZone = new HashMap<>();
    }

    /**
     * Set land boundary checker for coordinate validation.
     * Validates that POIs are on land using shapefile boundaries.
     *
     * @param checker BiPredicate that returns true if coordinates are on land
     */
    public void setLandChecker(BiPredicate<Double, Double> checker) {
        this.landChecker = checker;
    }

    /**
     * Set JAXA land use raster checker for POI validation at load time.
     * Rejects POIs on Water(1) or Wetland(13) pixels.
     *
     * @param checker BiFunction returning JAXA category (0-15) for (lon, lat)
     */
    public void setLandUseChecker(java.util.function.BiFunction<Double, Double, Integer> checker) {
        this.landUseChecker = checker;
    }

    /**
     * Set network index for network-aware POI filtering (Phase 3 - optional).
     *
     * @param index Transport network index
     */
    public void setNetworkIndex(truck.sim.spatial.TransportNetworkIndex index) {
        this.networkIndex = index;
    }

    /**
     * Check if coordinates are on land (driveable).
     * INVERTED from old isInWater() logic.
     *
     * @param lon Longitude
     * @param lat Latitude
     * @return true if coordinates are on land
     */
    private boolean isOnLand(double lon, double lat) {
        return landChecker != null && landChecker.test(lon, lat);
    }

    /**
     * Load POIs from CSV files in config directory.
     *
     * @param configDir Config directory path
     * @throws IOException if loading fails
     */
    public void loadPOIsFromCSV(String configDir) throws IOException {
        System.out.println("[POI] Loading POIs from CSV files...");

        // Load logistic centers
        String logisticCentersPath = configDir + "logistics.csv";
        List<PointOfInterest> logCenters = loadPOIFile(logisticCentersPath,
            PointOfInterest.POIType.LOGISTIC_CENTER);
        logisticCenters.addAll(logCenters);
        allPOIs.addAll(logCenters);

        // Load retail shops
        String retailShopsPath = configDir + "retail.csv";
        List<PointOfInterest> shops = loadPOIFile(retailShopsPath,
            PointOfInterest.POIType.RETAIL_SHOP);
        retailShops.addAll(shops);
        allPOIs.addAll(shops);

        // Load shopping malls
        String shoppingMallsPath = configDir + "malls.csv";
        List<PointOfInterest> malls = loadPOIFile(shoppingMallsPath,
            PointOfInterest.POIType.SHOPPING_MALL);
        shoppingMalls.addAll(malls);
        allPOIs.addAll(malls);

        // Load industrial sites
        String industrialSitesPath = configDir + "industrial.csv";
        List<PointOfInterest> industrial = loadPOIFile(industrialSitesPath,
            PointOfInterest.POIType.INDUSTRIAL_SITE);
        industrialSites.addAll(industrial);
        allPOIs.addAll(industrial);

        // Load ports/terminals
        String portsTerminalsPath = configDir + "ports.csv";
        List<PointOfInterest> ports = loadPOIFile(portsTerminalsPath,
            PointOfInterest.POIType.PORT_TERMINAL);
        portsTerminals.addAll(ports);
        allPOIs.addAll(ports);

        // Build zone index
        buildZoneIndex();

        System.out.println("[POI] Loaded " + logisticCenters.size() + " logistic centers");
        System.out.println("[POI] Loaded " + retailShops.size() + " retail shops");
        System.out.println("[POI] Loaded " + shoppingMalls.size() + " shopping malls");
        System.out.println("[POI] Loaded " + industrialSites.size() + " industrial sites");
        System.out.println("[POI] Loaded " + portsTerminals.size() + " ports/terminals");
        System.out.println("[POI] Total POIs: " + allPOIs.size());
        buildSpatialGrid();
    }

    /**
     * Load census-derived POIs from 4 CSV files generated by extract_census_pois.py.
     * Census POIs augment existing Telepoint POIs with nationwide coverage.
     * Must be called AFTER loadPOIsFromCSV() since it appends to existing lists.
     *
     * @param configDir Config directory path (e.g., "config/truck/facilities/")
     */
    public void loadCensusPOIs(String configDir) {
        System.out.println("[POI-Census] Loading census-derived POIs...");
        int beforeCount = allPOIs.size();

        // Census file -> POIType -> typed list mapping
        String[][] censusFiles = {
            {"census_logistics.csv",     "LOGISTIC_CENTER"},
            {"census_manufacturing.csv", "INDUSTRIAL_SITE"},
            {"census_wholesale.csv",     "WHOLESALE_FACILITY"},
            {"census_retail.csv",        "RETAIL_SHOP"},
        };

        for (String[] entry : censusFiles) {
            String fileName = entry[0];
            String poiTypeStr = entry[1];
            PointOfInterest.POIType poiType = PointOfInterest.POIType.valueOf(poiTypeStr);

            String filePath = configDir + fileName;
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("[POI-Census] Skipped (not found): " + fileName);
                continue;
            }

            int loaded = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine(); // skip header
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length < 7) continue;

                    String poiId = parts[0].trim();
                    String name = parts[1].trim();
                    double lon, lat;
                    try {
                        lon = Double.parseDouble(parts[2].trim());
                        lat = Double.parseDouble(parts[3].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String zoneId = parts[4].trim();
                    String facilityTypeStr = parts[5].trim();
                    String additionalInfo = parts[6].trim();

                    // Parse facility type with fallback
                    FacilityType facilityType;
                    try {
                        facilityType = FacilityType.valueOf(facilityTypeStr);
                    } catch (IllegalArgumentException e) {
                        facilityType = FacilityType.MIXED;
                    }

                    // Skip geo-validation for census POIs — they're already mesh centroids
                    // on land (from census). Validation is expensive for 160K+ POIs.

                    PointOfInterest poi = new PointOfInterest(
                        poiId, name, lon, lat, zoneId, facilityType, poiType, additionalInfo);

                    allPOIs.add(poi);
                    loaded++;

                    // Add to typed list
                    switch (poiType) {
                        case LOGISTIC_CENTER:   logisticCenters.add(poi); break;
                        case INDUSTRIAL_SITE:   industrialSites.add(poi); break;
                        case WHOLESALE_FACILITY: wholesaleFacilities.add(poi); break;
                        case RETAIL_SHOP:       retailShops.add(poi); break;
                        default: break;
                    }
                }
            } catch (IOException e) {
                System.err.println("[POI-Census] Error loading " + fileName + ": " + e.getMessage());
            }

            System.out.println("[POI-Census] Loaded " + loaded + " " + poiTypeStr + " from " + fileName);
        }

        int added = allPOIs.size() - beforeCount;
        System.out.println("[POI-Census] Total census POIs added: " + added);

        // Rebuild zone index and spatial grid to include census POIs
        buildZoneIndex();
        buildSpatialGrid();
    }

    /** Build spatial grid index for fast proximity lookups. */
    private void buildSpatialGrid() {
        spatialGrid = new HashMap<>();
        for (PointOfInterest poi : allPOIs) {
            long key = gridKey(poi.getLongitude(), poi.getLatitude());
            spatialGrid.computeIfAbsent(key, k -> new ArrayList<>()).add(poi);
        }
        System.out.println("[POI] Spatial grid: " + spatialGrid.size() + " cells");
    }

    private long gridKey(double lon, double lat) {
        int ix = (int) Math.floor(lon / GRID_CELL_SIZE);
        int iy = (int) Math.floor(lat / GRID_CELL_SIZE);
        return ((long) ix << 32) | (iy & 0xFFFFFFFFL);
    }

    /**
     * Load POI file.
     *
     * @param filePath CSV file path
     * @param poiType POI type
     * @return List of POIs
     * @throws IOException if file not found or parse error
     */
    private List<PointOfInterest> loadPOIFile(String filePath, PointOfInterest.POIType poiType)
            throws IOException {
        List<PointOfInterest> pois = new ArrayList<>();
        int skippedCount = 0;

        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("[POI] Warning: File not found: " + filePath);
            return pois;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();  // Skip header

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 7) {
                    System.err.println("[POI] Warning: Invalid line in " + filePath + ": " + line);
                    continue;
                }

                String poiId = parts[0].trim();
                String name = parts[1].trim();
                double lon = Double.parseDouble(parts[2].trim());
                double lat = Double.parseDouble(parts[3].trim());
                String zoneId = parts[4].trim();
                String facilityTypeStr = parts[5].trim();
                String additionalInfo = parts[6].trim();

                // Validate coordinates are on land (INVERTED from old water exclusion logic)
                // Note: Removed PORT_TERMINAL exception - ports must also be on land
                if (!isOnLand(lon, lat)) {
                    System.out.println("[POI] Skipped " + poiId + " (" + name +
                        ") - coordinates not on land (ocean/water): " + lon + ", " + lat);
                    skippedCount++;
                    continue;
                }

                // JAXA raster check — reject POIs on water/wetland pixels
                if (landUseChecker != null) {
                    int lu = landUseChecker.apply(lon, lat);
                    if (lu == 1 || lu == 13) {
                        System.out.println("[POI] Skipped " + poiId + " (" + name +
                            ") - JAXA raster: water/wetland pixel at " + lon + ", " + lat);
                        skippedCount++;
                        continue;
                    }
                }

                // Parse facility type
                FacilityType facilityType;
                try {
                    facilityType = FacilityType.valueOf(facilityTypeStr);
                } catch (IllegalArgumentException e) {
                    System.err.println("[POI] Warning: Invalid facility type: " + facilityTypeStr);
                    facilityType = FacilityType.MIXED;
                }

                PointOfInterest poi = new PointOfInterest(poiId, name, lon, lat,
                    zoneId, facilityType, poiType, additionalInfo);
                pois.add(poi);
            }
        }

        if (skippedCount > 0) {
            System.out.println("[POI] Skipped " + skippedCount + " POIs in water bodies from " + filePath);
        }

        return pois;
    }

    /**
     * Build zone index for fast lookup.
     */
    private void buildZoneIndex() {
        poisByZone.clear();
        for (PointOfInterest poi : allPOIs) {
            poisByZone.computeIfAbsent(poi.getZoneId(), k -> new ArrayList<>()).add(poi);
        }
        System.out.println("[POI] Indexed POIs across " + poisByZone.size() + " zones");
    }

    /**
     * Select POI for INTRA-metropolitan trip.
     *
     * Selection strategy:
     * - DELIVERY: Prefer retail shops and shopping malls within familiar area
     * - LONG_HAUL: Prefer logistic centers (rare for INTRA)
     * - MIXED_OPERATION: Balanced mix of all types
     *
     * @param truck Truck agent
     * @param originZoneId Origin zone ID
     * @param timePeriod Time period (for time-dependent weighting)
     * @return Selected POI, or null if no suitable POI found
     */
    public PointOfInterest selectPOIForINTRATrip(TruckAgent truck, String originZoneId,
                                                  int timePeriod) {
        TruckType truckType = truck.getTruckType();

        // Filter POIs based on truck type preferences
        List<PointOfInterest> candidates = new ArrayList<>();

        switch (truckType) {
            case DELIVERY:
                // Prefer retail shops and shopping malls
                candidates.addAll(retailShops);
                candidates.addAll(shoppingMalls);

                // Apply familiar area constraint
                double homeLon = truck.getHomeLongitude();
                double homeLat = truck.getHomeLatitude();
                double familiarRadius = truck.getFamiliarAreaRadiusKm();

                candidates.removeIf(poi -> poi.distanceTo(homeLon, homeLat) > familiarRadius);
                break;

            case LONG_HAUL:
                // Prefer logistic centers and ports (should be rare for INTRA trips)
                candidates.addAll(logisticCenters);
                candidates.addAll(portsTerminals);
                candidates.addAll(industrialSites);
                break;

            case MIXED_OPERATION:
                // Balanced mix of all POI types
                candidates.addAll(allPOIs);
                break;
        }

        if (candidates.isEmpty()) {
            // Fallback: use all POIs if no type-specific POIs available
            candidates.addAll(allPOIs);
        }

        // Filter to keep only POIs on land (INVERTED logic from old water exclusion)
        List<PointOfInterest> landPOIs = candidates.stream()
            .filter(poi -> isOnLand(poi.getLongitude(), poi.getLatitude()))
            .collect(Collectors.toList());

        if (landPOIs.isEmpty()) {
            System.err.println("[POI] Warning: No land-based POIs available for selection (all in water/ocean)");
            return null;
        }

        // NOTE: POI selection within a zone is currently uniform random.
        // Future enhancement: weight by O-D flows and time-dependent attractiveness.
        // This is tracked as part of Shi's KR2 evaluation (demand-supply alignment).
        // See: docs/code_audit_plan.md Phase 4.1
        return landPOIs.get(ThreadLocalRandom.current().nextInt(landPOIs.size()));
    }

    /**
     * Get POIs in a specific zone.
     *
     * @param zoneId Zone ID
     * @return List of POIs in zone (empty if none)
     */
    public List<PointOfInterest> getPOIsInZone(String zoneId) {
        return poisByZone.getOrDefault(zoneId, new ArrayList<>());
    }

    /**
     * Get POIs by type.
     *
     * @param type POI type
     * @return List of POIs of that type
     */
    public List<PointOfInterest> getPOIsByType(PointOfInterest.POIType type) {
        switch (type) {
            case LOGISTIC_CENTER:
                return logisticCenters;
            case RETAIL_SHOP:
                return retailShops;
            case SHOPPING_MALL:
                return shoppingMalls;
            case INDUSTRIAL_SITE:
                return industrialSites;
            case PORT_TERMINAL:
                return portsTerminals;
            default:
                return new ArrayList<>();
        }
    }

    /**
     * Select POI for a truck's home zone (first-trip origin anchoring).
     * Prefers facility types matched to the truck's primary commodity type.
     * Falls back to any POI in the zone, then returns null if zone has no POIs.
     *
     * @param zoneId Home zone ID
     * @param commodityType Truck's primary commodity type
     * @return Selected POI anchored in the zone, or null if zone has no POIs
     */
    public PointOfInterest selectPOIForZone(String zoneId, String commodityType) {
        List<PointOfInterest> zonePOIs = poisByZone.getOrDefault(zoneId, Collections.emptyList());
        if (zonePOIs.isEmpty()) return null;

        List<FacilityType> preferred = getPreferredFacilities(commodityType);

        List<PointOfInterest> matched = new ArrayList<>();
        for (PointOfInterest poi : zonePOIs) {
            if (preferred.contains(poi.getFacilityType())) {
                matched.add(poi);
            }
        }
        if (matched.isEmpty()) matched = new ArrayList<>(zonePOIs);

        return matched.get(ThreadLocalRandom.current().nextInt(matched.size()));
    }

    /**
     * Select POI for any trip type (INTRA or INTER), routed by commodity type.
     * Maps commodity → preferred facility types → POIs in target zone.
     *
     * @param truck Truck agent
     * @param targetZoneId Target zone (already selected by OD matrix)
     * @param commodityType The commodity being carried
     * @param timePeriod Time period
     * @return Selected POI, or null if no suitable POI found
     */
    public PointOfInterest selectPOIForTrip(TruckAgent truck, String targetZoneId,
                                             String commodityType, int timePeriod) {
        List<PointOfInterest> zonePOIs = poisByZone.getOrDefault(targetZoneId, new ArrayList<>());
        if (zonePOIs.isEmpty()) return null;

        // Map commodity to preferred facility types
        List<FacilityType> preferredTypes = getPreferredFacilities(commodityType);

        // Filter POIs by preferred facility type
        List<PointOfInterest> matched = new ArrayList<>();
        for (PointOfInterest poi : zonePOIs) {
            if (preferredTypes.contains(poi.getFacilityType())) {
                matched.add(poi);
            }
        }

        // If no match by facility type, use all zone POIs
        if (matched.isEmpty()) matched = zonePOIs;

        // Filter to keep only POIs on land (INVERTED logic from old water exclusion)
        List<PointOfInterest> landPOIs = matched.stream()
            .filter(poi -> isOnLand(poi.getLongitude(), poi.getLatitude()))
            .collect(Collectors.toList());

        if (landPOIs.isEmpty()) {
            // Final fallback: no land-based POIs (if all zone POIs are in water, this shouldn't happen)
            return null;
        }

        // Weighted random selection
        return landPOIs.get(ThreadLocalRandom.current().nextInt(landPOIs.size()));
    }

    /**
     * Select POI based on truck type and destination facility type from MFS File 07.
     *
     * Routing logic:
     * - LONG_HAUL: facility -> facility (B2B heavy freight)
     *   Prefers: LOGISTIC_CENTER, INDUSTRIAL_SITE, PORT_TERMINAL, WHOLESALE_FACILITY
     * - DELIVERY: depot -> retail/homes (last-mile)
     *   Prefers: RETAIL_SHOP, SHOPPING_MALL, WHOLESALE_FACILITY
     *   Returns null for "residential" destType (caller uses BuiltUpIndex)
     * - MIXED_OPERATION: hybrid (both hauling and delivery)
     *   Uses all POI types, weighted by destFacilityTypeKey match
     *
     * Soft preference: falls back to selectPOIForTrip() if filtered pool is empty.
     *
     * @param truckType Truck type (DELIVERY, MIXED_OPERATION, LONG_HAUL)
     * @param targetZoneId Target zone
     * @param destFacilityTypeKey Destination facility type key from CommodityRouter
     *        (e.g., "factory", "logistics", "store", "residential", "office")
     * @param commodityType Commodity for fallback selection
     * @return Selected POI, or null if residential destination or no POIs
     */
    public PointOfInterest selectPOIByTruckType(TruckType truckType, String targetZoneId,
                                                 String destFacilityTypeKey, String commodityType) {
        List<PointOfInterest> zonePOIs = poisByZone.getOrDefault(targetZoneId, Collections.emptyList());
        if (zonePOIs.isEmpty()) return null;

        // DELIVERY to residential -> return null, caller uses BuiltUpIndex density sampling
        if (truckType == TruckType.DELIVERY && "residential".equals(destFacilityTypeKey)) {
            return null;
        }

        // Build preferred POI type filter based on truck type
        Set<PointOfInterest.POIType> preferredTypes = new java.util.HashSet<>();

        switch (truckType) {
            case LONG_HAUL:
                // B2B: facility-to-facility routing
                preferredTypes.add(PointOfInterest.POIType.LOGISTIC_CENTER);
                preferredTypes.add(PointOfInterest.POIType.INDUSTRIAL_SITE);
                preferredTypes.add(PointOfInterest.POIType.PORT_TERMINAL);
                preferredTypes.add(PointOfInterest.POIType.WHOLESALE_FACILITY);
                break;
            case DELIVERY:
                // Last-mile: depot-to-consumer
                preferredTypes.add(PointOfInterest.POIType.RETAIL_SHOP);
                preferredTypes.add(PointOfInterest.POIType.SHOPPING_MALL);
                preferredTypes.add(PointOfInterest.POIType.WHOLESALE_FACILITY);
                break;
            case MIXED_OPERATION:
                // Hybrid: all types, but boost destFacilityTypeKey matches
                // Use all types — no pre-filtering
                break;
        }

        // Filter zone POIs by preferred types
        List<PointOfInterest> filtered;
        if (preferredTypes.isEmpty()) {
            // MIXED: no pre-filter, use all zone POIs
            filtered = new ArrayList<>(zonePOIs);
        } else {
            filtered = new ArrayList<>();
            for (PointOfInterest poi : zonePOIs) {
                if (preferredTypes.contains(poi.getPoiType())) {
                    filtered.add(poi);
                }
            }
        }

        // Further boost by destFacilityTypeKey match (for MIXED, this IS the preference)
        if (destFacilityTypeKey != null && !filtered.isEmpty()) {
            FacilityType destFT = mapDestKeyToFacilityType(destFacilityTypeKey);
            List<PointOfInterest> matched = new ArrayList<>();
            for (PointOfInterest poi : filtered) {
                if (poi.getFacilityType() == destFT) {
                    matched.add(poi);
                }
            }
            if (!matched.isEmpty()) {
                return matched.get(ThreadLocalRandom.current().nextInt(matched.size()));
            }
        }

        // Return from filtered pool
        if (!filtered.isEmpty()) {
            return filtered.get(ThreadLocalRandom.current().nextInt(filtered.size()));
        }

        // Soft fallback: use existing commodity-based selection
        return selectPOIForTrip(null, targetZoneId, commodityType, 0);
    }

    /** Map MFS facility flow key to FacilityType for POI matching. */
    private FacilityType mapDestKeyToFacilityType(String key) {
        switch (key) {
            case "factory": return FacilityType.INDUSTRIAL;
            case "logistics": return FacilityType.LOGISTICS_HUB;
            case "store": return FacilityType.SMALL_RETAIL;
            case "office": return FacilityType.MIXED;
            case "construction": return FacilityType.CONSTRUCTION_SITE;
            default: return FacilityType.MIXED;
        }
    }

    /**
     * Map commodity type to preferred facility types for destination routing.
     */
    private List<FacilityType> getPreferredFacilities(String commodityType) {
        List<FacilityType> types = new ArrayList<>();
        if (commodityType == null) {
            types.add(FacilityType.MIXED);
            return types;
        }
        switch (commodityType) {
            case "daily_necessities":
            case "publications":
                types.add(FacilityType.SMALL_RETAIL);
                types.add(FacilityType.LARGE_DISTRIBUTION);
                break;
            case "agricultural_food":
                types.add(FacilityType.LOGISTICS_HUB);
                types.add(FacilityType.SMALL_RETAIL);
                break;
            case "machinery":
            case "metal_products":
            case "light_industrial":
                types.add(FacilityType.INDUSTRIAL);
                types.add(FacilityType.MEDIUM_WAREHOUSE);
                break;
            case "forestry_mineral":
            case "ceramic_chemical":
                types.add(FacilityType.INDUSTRIAL);
                types.add(FacilityType.CONSTRUCTION_SITE);
                break;
            case "special_products":
                types.add(FacilityType.LOGISTICS_HUB);
                types.add(FacilityType.LARGE_DISTRIBUTION);
                break;
            default:
                types.add(FacilityType.MIXED);
                break;
        }
        return types;
    }

    /**
     * Check if POIs are loaded.
     *
     * @return true if at least one POI is loaded
     */
    public boolean hasPOIs() {
        return !allPOIs.isEmpty();
    }

    /**
     * Auto-generate POIs from establishment count data.
     * Creates POIs based on establishment density in each zone.
     *
     * @param establishmentCsvPath Path to establishment_counts.csv
     * @param deliveryZones List of delivery zones to place POIs in
     * @throws IOException if file reading fails
     */
    public void generatePOIsFromEstablishments(String establishmentCsvPath,
                                              List<DeliveryZone> deliveryZones)
            throws IOException {
        System.out.println("[POI] Generating POIs from establishment data: " + establishmentCsvPath);

        Map<String, EstablishmentCounts> counts = loadEstablishmentCounts(establishmentCsvPath);

        int logisticPOIs = 0;
        int retailPOIs = 0;
        int mallPOIs = 0;

        for (DeliveryZone zone : deliveryZones) {
            String zoneId = zone.getZoneId();
            EstablishmentCounts zoneCount = counts.get(zoneId);

            if (zoneCount == null) continue;

            // Generate logistic centers: 1 POI per N establishments
            int numLogistic = Math.max(1, zoneCount.logisticsEstablishments / POIManagerConstants.LOGISTIC_POI_RATIO);
            for (int i = 0; i < numLogistic; i++) {
                double[] point = generateRandomPointInZone(zone);
                String poiId = "LC_GEN_" + zoneId + "_" + (i + 1);
                PointOfInterest poi = new PointOfInterest(
                    poiId, "Auto-generated Logistic Center",
                    point[0], point[1], zoneId,
                    FacilityType.LOGISTICS_HUB, PointOfInterest.POIType.LOGISTIC_CENTER,
                    "auto_generated");

                logisticCenters.add(poi);
                allPOIs.add(poi);
                logisticPOIs++;
            }

            // Generate retail shops: 1 POI per N establishments
            int numRetail = Math.max(1, zoneCount.retailEstablishments / POIManagerConstants.RETAIL_POI_RATIO);
            for (int i = 0; i < numRetail; i++) {
                double[] point = generateRandomPointInZone(zone);
                String poiId = "RS_GEN_" + zoneId + "_" + (i + 1);
                PointOfInterest poi = new PointOfInterest(
                    poiId, "Auto-generated Retail Shop",
                    point[0], point[1], zoneId,
                    FacilityType.SMALL_RETAIL, PointOfInterest.POIType.RETAIL_SHOP,
                    "auto_generated");

                retailShops.add(poi);
                allPOIs.add(poi);
                retailPOIs++;
            }

            // Generate shopping malls: 1 POI per zone with high retail density
            if (zoneCount.retailEstablishments > POIManagerConstants.HIGH_RETAIL_THRESHOLD) {
                double[] point = generateRandomPointInZone(zone);
                String poiId = "SM_GEN_" + zoneId;
                PointOfInterest poi = new PointOfInterest(
                    poiId, "Auto-generated Shopping Mall",
                    point[0], point[1], zoneId,
                    FacilityType.SMALL_RETAIL, PointOfInterest.POIType.SHOPPING_MALL,
                    "auto_generated");

                shoppingMalls.add(poi);
                allPOIs.add(poi);
                mallPOIs++;
            }
        }

        // Build zone index
        buildZoneIndex();

        System.out.println("[POI] Generated " + (logisticPOIs + retailPOIs + mallPOIs) + " POIs total:");
        System.out.println("[POI]   - Logistic Centers: " + logisticPOIs);
        System.out.println("[POI]   - Retail Shops: " + retailPOIs);
        System.out.println("[POI]   - Shopping Malls: " + mallPOIs);
    }

    /**
     * Load establishment counts from CSV.
     *
     * CSV format:
     * zone_id,logistics_establishments,retail_establishments,total_establishments
     * DZ01,150,800,950
     *
     * @param csvPath Path to CSV file
     * @return Map of zone ID to establishment counts
     * @throws IOException if file reading fails
     */
    private Map<String, EstablishmentCounts> loadEstablishmentCounts(String csvPath)
            throws IOException {
        Map<String, EstablishmentCounts> counts = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                String zoneId = parts[0].trim();
                int logistics = Integer.parseInt(parts[1].trim());
                int retail = Integer.parseInt(parts[2].trim());
                int total = Integer.parseInt(parts[3].trim());

                counts.put(zoneId, new EstablishmentCounts(logistics, retail, total));
            }
        }

        return counts;
    }

    /**
     * Generate random point within a delivery zone.
     *
     * @param zone Delivery zone
     * @return Random point [lon, lat]
     */
    private double[] generateRandomPointInZone(DeliveryZone zone) {
        // Generate random point within zone radius
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = ThreadLocalRandom.current().nextDouble() * zone.getRadiusKm();

        // Convert to lat/lon offset
        double lonOffset = distance / 111.32 * Math.cos(Math.toRadians(zone.getCenterLatitude()));
        double latOffset = distance / 111.32;

        double lon = zone.getCenterLongitude() + lonOffset * Math.cos(angle);
        double lat = zone.getCenterLatitude() + latOffset * Math.sin(angle);

        return new double[]{lon, lat};
    }

    /**
     * Helper class for establishment counts.
     */
    private static class EstablishmentCounts {
        int logisticsEstablishments;
        int retailEstablishments;
        int totalEstablishments;

        EstablishmentCounts(int logistics, int retail, int total) {
            this.logisticsEstablishments = logistics;
            this.retailEstablishments = retail;
            this.totalEstablishments = total;
        }
    }

    // ── Spatial proximity search (V5.2 delivery tour) ──────────────────

    /**
     * Find all POIs within a given radius of a point using spatial grid + Haversine.
     * Grid reduces search from O(N) to O(cells×POIs_per_cell).
     */
    public List<PointOfInterest> findPOIsNearPoint(double lon, double lat, double maxDistKm) {
        List<PointOfInterest> nearby = new ArrayList<>();
        // Convert km to approximate degrees for grid cell search radius
        int cellRadius = (int) Math.ceil(maxDistKm / (GRID_CELL_SIZE * 111.0)) + 1;
        int cx = (int) Math.floor(lon / GRID_CELL_SIZE);
        int cy = (int) Math.floor(lat / GRID_CELL_SIZE);

        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dy = -cellRadius; dy <= cellRadius; dy++) {
                long key = ((long) (cx + dx) << 32) | ((cy + dy) & 0xFFFFFFFFL);
                List<PointOfInterest> cell = spatialGrid.get(key);
                if (cell != null) {
                    for (PointOfInterest poi : cell) {
                        if (poi.distanceTo(lon, lat) <= maxDistKm) {
                            nearby.add(poi);
                        }
                    }
                }
            }
        }
        return nearby;
    }

    /**
     * Find the single nearest POI to a point using spatial grid.
     * Searches expanding rings of grid cells until a candidate is found,
     * then verifies one extra ring to handle cell-boundary edge cases.
     * Falls back to full scan only if the grid returns nothing.
     */
    public PointOfInterest findNearestPOI(double lon, double lat) {
        int cx = (int) Math.floor(lon / GRID_CELL_SIZE);
        int cy = (int) Math.floor(lat / GRID_CELL_SIZE);

        PointOfInterest nearest = null;
        double minDist = Double.MAX_VALUE;

        // Expanding ring search: start with 3x3, expand if needed
        for (int ring = 1; ring <= 20; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    // Only check the outer ring (skip interior already searched)
                    if (ring > 1 && Math.abs(dx) < ring && Math.abs(dy) < ring) continue;
                    long key = ((long) (cx + dx) << 32) | ((cy + dy) & 0xFFFFFFFFL);
                    List<PointOfInterest> cell = spatialGrid.get(key);
                    if (cell != null) {
                        for (PointOfInterest poi : cell) {
                            double dist = poi.distanceTo(lon, lat);
                            if (dist < minDist) {
                                minDist = dist;
                                nearest = poi;
                            }
                        }
                    }
                }
            }
            // Once we found a candidate, check one more ring to handle boundary cases
            if (nearest != null && ring > 1) break;
            if (nearest != null) continue;  // found in ring 1, check ring 2 then break
        }

        // Fallback: full scan (should rarely trigger)
        if (nearest == null) {
            for (PointOfInterest poi : allPOIs) {
                double dist = poi.distanceTo(lon, lat);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = poi;
                }
            }
        }
        return nearest;
    }

    // Getters
    public List<PointOfInterest> getAllPOIs() { return new ArrayList<>(allPOIs); }
    public List<PointOfInterest> getLogisticCenters() { return new ArrayList<>(logisticCenters); }
    public List<PointOfInterest> getRetailShops() { return new ArrayList<>(retailShops); }
    public List<PointOfInterest> getShoppingMalls() { return new ArrayList<>(shoppingMalls); }
    public List<PointOfInterest> getIndustrialSites() { return new ArrayList<>(industrialSites); }
    public List<PointOfInterest> getPortsTerminals() { return new ArrayList<>(portsTerminals); }
    public List<PointOfInterest> getWholesaleFacilities() { return new ArrayList<>(wholesaleFacilities); }
}
