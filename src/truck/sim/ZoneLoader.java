package truck.sim;

import truck.sim.spatial.BuiltUpIndex;
import truck.sim.spatial.GeoValidator;
import truck.sim.spatial.PointGenerator;
import truck.sim.spatial.TransportNetworkIndex;
import truck.sim.spatial.NetworkAwarePointGenerator;
import java.io.*;
import java.util.*;

/**
 * Zone initialization and data loading for the Tokyo Truck ABM.
 *
 * <p>Handles all zone-related setup: CSV parsing, O-D matrix loading,
 * establishment data, transport networks, polygon boundaries, and
 * wiring of downstream subsystems (DestinationSelector, PointGenerator, etc.)
 *
 * <p>Supports two modes:
 * <ul>
 *   <li>SINGLE: One zones file ({@link #loadSingle()})</li>
 *   <li>DUAL: Separate intra + inter files ({@link #loadDual(String, String)})</li>
 * </ul>
 *
 * <p>Extracted from TruckSimulation.java v2.0 to reduce orchestrator size.
 *
 * @version 2.1
 */
public class ZoneLoader {

    private final TruckConfig config;
    private final Random random;
    private final GeoValidator geoValidator;
    private final ZoneManager zoneManager;
    private final MetropolitanConfig metroConfig;

    // Populated during loading
    private List<DeliveryZone> deliveryZones;
    private OriginDestinationMatrix odMatrix;
    private CommodityRouter commodityRouter;
    private TripGenerator tripGenerator;
    private POIManager poiManager;
    private GenerationAttractionBalancer gaBalancer;
    private MetricsTracker metricsTracker;
    private DestinationSelector destinationSelector;
    private PointGenerator pointGenerator;
    private TransportNetworkIndex networkIndex;
    private NetworkAwarePointGenerator networkAwareGenerator;

    /**
     * @param config      Simulation configuration
     * @param random      Shared random generator (seeded)
     * @param geoValidator Multi-layer spatial validator
     * @param zoneManager  Zone lookup and distance calculator
     * @param metroConfig  Metropolitan area configuration
     */
    public ZoneLoader(TruckConfig config, Random random,
                      GeoValidator geoValidator, ZoneManager zoneManager,
                      MetropolitanConfig metroConfig) {
        this.config = config;
        this.random = random;
        this.geoValidator = geoValidator;
        this.zoneManager = zoneManager;
        this.metroConfig = metroConfig;
        this.deliveryZones = new ArrayList<>();
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Load zones from a single CSV file (SINGLE mode).
     *
     * @return ZoneLoadResult with all initialized subsystems
     */
    public ZoneLoadResult loadSingle() {
        System.out.println("[CHECKPOINT] Loading delivery zones from CSV...");

        String zonesFile = config.getZonesFile();
        String zonesPath = "config/" + zonesFile;

        try (BufferedReader reader = new BufferedReader(new FileReader(zonesPath))) {
            parseZonesCSV(reader, null);
            System.out.println("[CHECKPOINT] Loaded " + deliveryZones.size() +
                " delivery zones from " + zonesFile);
        } catch (IOException e) {
            System.err.println("Error loading zones file: " + e.getMessage());
            System.err.println("  Using empty zone list");
        }

        wireSubsystems();
        return buildResult();
    }

    /**
     * Load zones from both intra and inter CSV files (DUAL mode).
     *
     * @param intraZonesFile Intra-metropolitan zones CSV filename
     * @param interZonesFile Inter-metropolitan zones CSV filename
     * @return ZoneLoadResult with all initialized subsystems
     */
    public ZoneLoadResult loadDual(String intraZonesFile, String interZonesFile) {
        System.out.println("[DUAL] Loading combined delivery zones from both files...");

        String configDir = "config/truck/";
        int totalLoaded = 0;

        // Load INTRA zones
        totalLoaded += loadZonesFromFile(configDir + intraZonesFile, "INTRA");

        // Load INTER zones
        totalLoaded += loadZonesFromFile(configDir + interZonesFile, "INTER");

        System.out.println("[DUAL] Total zones loaded: " + totalLoaded +
            " (INTRA + INTER combined)");

        wireSubsystems();

        // Compute geography bounds from all loaded zones so inter-regional zones
        // (MFS67-MFS71) generate home points inside their actual geography
        computeGeographyBounds();

        return buildResult();
    }

    /**
     * Load nationwide expanded zones from a single CSV file (EXPANDED mode).
     * Contains 106 zones: 66 existing Kanto (MFS01-66) + 40 prefecture sub-zones (PRF01-PRF47).
     * Uses expanded O-D matrix (od_volume_expanded.csv) and expanded zone mapping.
     *
     * @return ZoneLoadResult with all initialized subsystems
     */
    public ZoneLoadResult loadExpanded() {
        System.out.println("[EXPANDED] Loading nationwide zones (106 zones)...");

        String configDir = "config/truck/";
        String expandedZonesFile = config.getProperty("zones.file.expanded", "zones/expanded.csv");
        int loaded = loadZonesFromFile(configDir + expandedZonesFile, "EXPANDED");

        System.out.println("[EXPANDED] Total zones loaded: " + loaded + " (Kanto + nationwide prefectures)");

        wireSubsystems();
        computeGeographyBounds();

        return buildResult();
    }

    // ========================================================================
    // ZONE CSV PARSING
    // ========================================================================

    /**
     * Parse zones from a CSV reader. Used by SINGLE mode.
     *
     * @param reader CSV reader (header already expected as first line)
     * @param tag    Sub-region tag (null for SINGLE mode)
     */
    private void parseZonesCSV(BufferedReader reader, String tag) throws IOException {
        String line = reader.readLine(); // Skip header
        int lineNum = 1;

        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split(",");
            if (parts.length < 9) {
                System.err.println("  Warning: Skipping malformed line " + lineNum +
                    " (expected 9 columns, got " + parts.length + ")");
                continue;
            }

            try {
                DeliveryZone zone = parseZoneLine(parts);
                if (tag != null) {
                    zone.setSubRegion(tag);
                }
                deliveryZones.add(zone);
            } catch (NumberFormatException e) {
                System.err.println("  Warning: Skipping line " + lineNum +
                    " due to number format error: " + e.getMessage());
            }
        }
    }

    /**
     * Parse a single zone from CSV columns.
     */
    private DeliveryZone parseZoneLine(String[] parts) {
        String zoneId = parts[0].trim();
        String name = parts[1].trim();
        double centerLon = Double.parseDouble(parts[2].trim());
        double centerLat = Double.parseDouble(parts[3].trim());
        double radius = Double.parseDouble(parts[4].trim());
        double warehousesWeight = Double.parseDouble(parts[5].trim());
        double retailWeight = Double.parseDouble(parts[6].trim());
        double constructionWeight = Double.parseDouble(parts[7].trim());
        double residentialWeight = Double.parseDouble(parts[8].trim());

        return new DeliveryZone(
            zoneId, name, centerLon, centerLat, radius,
            warehousesWeight, retailWeight, constructionWeight, residentialWeight
        );
    }

    /**
     * Load zones from a single CSV file into the deliveryZones list.
     * Used by DUAL mode for each file (intra, inter).
     *
     * @param zonesPath Full path to zones CSV file
     * @param tag       Tag for logging and sub-region assignment ("INTRA" or "INTER")
     * @return Number of zones loaded
     */
    private int loadZonesFromFile(String zonesPath, String tag) {
        int loadedCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(zonesPath))) {
            String line = reader.readLine(); // Skip header
            int lineNum = 1;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length < 9) {
                    System.err.println("  Warning: Skipping malformed line " + lineNum +
                        " in " + tag + " zones (expected 9 columns, got " + parts.length + ")");
                    continue;
                }

                try {
                    DeliveryZone zone = parseZoneLine(parts);
                    zone.setSubRegion(tag);
                    deliveryZones.add(zone);
                    loadedCount++;
                } catch (NumberFormatException e) {
                    System.err.println("  Warning: Skipping line " + lineNum +
                        " in " + tag + " zones due to number format error");
                }
            }

            System.out.println("[DUAL] Loaded " + loadedCount + " " + tag + " zones from " + zonesPath);

        } catch (IOException e) {
            System.err.println("[DUAL] Error loading " + tag + " zones from " + zonesPath + ": " + e.getMessage());
        }

        return loadedCount;
    }

    // ========================================================================
    // SUBSYSTEM WIRING (shared by both modes)
    // ========================================================================

    /**
     * Wire up all downstream subsystems after zones are loaded.
     * This is the shared initialization path for both SINGLE and DUAL modes.
     */
    private void wireSubsystems() {
        // O-D matrix
        odMatrix = new OriginDestinationMatrix(deliveryZones.size());
        System.out.println("[CHECKPOINT] Initialized O-D matrix (" + deliveryZones.size() +
            "x" + deliveryZones.size() + " zones)");

        if (config.getUseMFSODMatrix()) {
            try {
                String mappingFile = config.getProperty("datasets.zone.mapping.file", "zones/mapping.csv");
                String odFile = config.getProperty("datasets.od.matrix.file", "flows/od_volume.csv");
                Map<String, Integer> zoneMapping = loadZoneMapping("config/truck/" + mappingFile);
                odMatrix.loadFromMFSCSV("config/truck/" + odFile, zoneMapping);
                System.out.println("[CHECKPOINT] Loaded O-D probability matrix from " + odFile);
            } catch (IOException e) {
                System.err.println("[O-D] Warning: Could not load O-D matrix: " + e.getMessage());
            }
        }

        // Commodity router
        commodityRouter = new CommodityRouter();
        System.out.println("[CHECKPOINT] Initialized CommodityRouter with 9 commodity types");

        // Trip generator
        tripGenerator = new TripGenerator(config);
        System.out.println("[CHECKPOINT] Initialized TripGenerator with facility-based rates");

        // POI Manager
        poiManager = new POIManager();

        // Spatial validation
        geoValidator.loadAll();

        // Point generator
        pointGenerator = new PointGenerator(geoValidator);

        // Transport networks
        loadTransportNetworks();
        if (networkIndex != null) {
            pointGenerator.setNetworkIndex(networkIndex);
        }
        pointGenerator.setPOIManager(poiManager);

        // Polygon zone boundaries
        loadPolygonZones();

        // POI initialization
        poiManager.setLandChecker(geoValidator::isOnLand);
        poiManager.setLandUseChecker((lon, lat) ->
            geoValidator.getLandUseCategory(lon.doubleValue(), lat.doubleValue()));

        // Establishment data (Phase 3)
        zoneManager.setZones(deliveryZones);
        loadEstablishmentData();

        // Load POIs
        loadPOIs();

        // Built-up density indices (requires raster + POIs)
        buildBuiltUpIndices();

        // G-A Balancer
        initializeGABalancer();

        // Print zone facility types
        System.out.println("[CHECKPOINT] Zone facility types:");
        for (DeliveryZone zone : deliveryZones) {
            System.out.println("  " + zone.getZoneId() + ": " + zone.getFacilityType() +
                " (" + zone.getFacilityType().getBaseTripsPerDay() + " trips/day base rate)");
        }

        // Metrics tracker
        metricsTracker = new MetricsTracker();
        System.out.println("[CHECKPOINT] Initialized MetricsTracker for comprehensive metrics");

        // Destination selector (depends on all above)
        destinationSelector = new DestinationSelector(config,
            zoneManager, geoValidator, pointGenerator, poiManager,
            odMatrix, gaBalancer, commodityRouter, metroConfig);
        System.out.println("[CHECKPOINT] Initialized extracted subsystems (v2.0)");
    }

    // ========================================================================
    // DATA LOADING HELPERS
    // ========================================================================

    /**
     * Load POIs from CSV or auto-generate from establishment data.
     */
    private void loadPOIs() {
        try {
            String poiMode = config.getPOIMode();
            if (poiMode.equals("auto_generate")) {
                String establishmentFile = config.getEstablishmentFile();
                // In DUAL mode, config dir prefix differs
                String prefix = deliveryZones.stream()
                    .anyMatch(z -> "INTER".equals(z.getSubRegion())) ? "config/truck/" : "config/";
                poiManager.generatePOIsFromEstablishments(
                    prefix + establishmentFile, deliveryZones);
                System.out.println("[CHECKPOINT] Auto-generated POIs from establishment data");
            } else {
                poiManager.loadPOIsFromCSV("config/truck/facilities/");
                System.out.println("[CHECKPOINT] Loaded POIs from CSV files");
            }

            // Link POIs to zones
            for (DeliveryZone zone : deliveryZones) {
                List<PointOfInterest> zonePOIs = poiManager.getPOIsInZone(zone.getZoneId());
                zone.setPOIs(zonePOIs);
            }
            System.out.println("[POI] Total: " +
                poiManager.getLogisticCenters().size() + " logistic centers, " +
                poiManager.getRetailShops().size() + " retail shops, " +
                poiManager.getShoppingMalls().size() + " shopping malls");
        } catch (IOException e) {
            System.err.println("[POI] Warning: Could not initialize POIs: " + e.getMessage());
            System.err.println("[POI] Will use zone-based destination selection");
        }
    }

    /**
     * Initialize Generation-Attraction Balancer if enabled.
     */
    private void initializeGABalancer() {
        if (config.getUseGABalance()) {
            try {
                gaBalancer = new GenerationAttractionBalancer();
                gaBalancer.loadTargets("config/truck/flows/ga_targets.csv");

                // Scale G-A targets to match expected trip volume for current fleet size.
                double fleetSize = config.getTruckFleetSize();
                double avgTripsPerTruck =
                    config.getTruckTypeDeliveryProb() * config.getTruckTripsDelivery() +
                    config.getTruckTypeUrbanLogisticsProb() * config.getTruckTripsMixed() +
                    config.getTruckTypeLongHaulProb() * config.getTruckTripsLongHaul();
                double expectedLoadedMovements = fleetSize * avgTripsPerTruck * 0.92;
                gaBalancer.scaleTargets(expectedLoadedMovements);

                System.out.println("[CHECKPOINT] Generation-Attraction Balancer initialized");
            } catch (IOException e) {
                System.err.println("[G-A] Warning: Could not load G-A targets: " + e.getMessage());
                System.err.println("[G-A] Balancing disabled, using unconstrained generation");
                gaBalancer = null;
            }
        } else {
            gaBalancer = null;
            System.out.println("[CHECKPOINT] G-A Balancer disabled (using unconstrained generation)");
        }
    }

    /**
     * Load establishment counts and industry proportions (Phase 3).
     */
    private void loadEstablishmentData() {
        System.out.println("[Phase 3] Loading establishment data...");

        // 0. Load Region Name to Sim Zone ID mapping
        Map<String, String> regionToSimId = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("config/truck/zones/mapping.csv"))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    regionToSimId.put(parts[1].trim(), parts[2].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: zone_mapping.csv not found");
        }

        // 1. Load Industry Mix Shares
        Map<String, Map<String, Double>> industryMix = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("config/truck/operations/industry_mix.csv"))) {
            String header = br.readLine();
            String[] cols = header.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String industry = parts[0].trim();
                for (int i = 1; i < parts.length; i++) {
                    if (cols[i].endsWith("_share")) {
                        String facility = cols[i].replace("_share", "");
                        double share = Double.parseDouble(parts[i]);
                        industryMix.computeIfAbsent(facility, k -> new HashMap<>()).put(industry, share);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: industry_mix_per_facility.csv not found");
        }

        // 2. Load Establishment Counts per Zone
        try (BufferedReader br = new BufferedReader(new FileReader("config/truck/facilities/est_derived.csv"))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String regionName = parts[0].trim();
                String subRegion = parts[1].trim();

                String simId = regionToSimId.get(regionName);
                DeliveryZone zone = (simId != null) ? zoneManager.findZoneByZoneId(simId) : null;

                if (zone != null) {
                    zone.setSubRegion(subRegion);
                    String[] facilities = {"factory", "logistics", "office", "store", "other"};
                    for (int i = 0; i < facilities.length; i++) {
                        int count = Integer.parseInt(parts[i + 2]);
                        zone.setEstablishmentCount(facilities[i], count);

                        Map<String, Double> shares = industryMix.get(facilities[i]);
                        if (shares != null) {
                            Map<String, Integer> industryCounts = new HashMap<>();
                            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                                industryCounts.put(entry.getKey(), (int) Math.round(count * entry.getValue()));
                            }
                            zone.setIndustryCounts(facilities[i], industryCounts);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: establishment_counts_derived.csv not found");
        }
    }

    /**
     * Load road and rail networks for network-aware point generation.
     * Gracefully degrades to pure polygon sampling if loading fails.
     */
    private void loadTransportNetworks() {
        String roadPath = "src/shared/gm-jp/roadl_jpn.shp";
        String railPath = "src/shared/gm-jp/raill_jpn.shp";

        System.out.println("[NETWORK] Loading transport networks...");

        try {
            networkIndex = new TransportNetworkIndex();

            networkIndex.loadRoadNetwork(roadPath);
            System.out.println("[NETWORK] Loaded " + networkIndex.getRoadCount() +
                " road segments with accessibility attributes");

            try {
                networkIndex.loadRailNetwork(railPath);
                System.out.println("[NETWORK] Loaded " + networkIndex.getRailCount() +
                    " rail segments");
            } catch (Exception e) {
                System.out.println("[NETWORK] Rail network not loaded (optional): " +
                    e.getMessage());
            }

            System.out.println("[NETWORK] Spatial index built (STRtree)");

            networkAwareGenerator = new NetworkAwarePointGenerator();
            System.out.println("[NETWORK] Network-aware point generation enabled");

            if (poiManager != null) {
                poiManager.setNetworkIndex(networkIndex);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load transport networks: " + e.getMessage());
            e.printStackTrace();
            System.err.println("[WARNING] Proceeding without network-aware generation");
            networkIndex = null;
            networkAwareGenerator = null;
        }
    }

    /**
     * Load polygon-based zone boundaries.
     * Associates MFS zones with shapefile administrative boundaries.
     */
    private void loadPolygonZones() {
        String mappingPath = "config/truck/zones/zone_boundary_mapping.csv";
        String shapefilePath = "src/shared/gm-jp/polbnda_jpn_new.shp";
        System.out.println("[ZONE] Loading polygon-based zone boundaries...");

        try {
            truck.sim.util.ZoneBoundaryMapper zoneBoundaryMapper =
                new truck.sim.util.ZoneBoundaryMapper();

            zoneBoundaryMapper.loadMapping(mappingPath);
            zoneBoundaryMapper.loadBoundaryGeometries(shapefilePath);
            zoneBoundaryMapper.associateZoneBoundaries(deliveryZones);

            int polygonZones = (int) deliveryZones.stream()
                .filter(DeliveryZone::hasPolygonBoundary)
                .count();
            int radiusZones = deliveryZones.size() - polygonZones;

            System.out.println("[ZONE] Zone initialization complete: " + polygonZones +
                " polygon-based, " + radiusZones + " radius-based");

        } catch (IOException e) {
            System.err.println("[ZONE] Warning: Failed to load polygon zone mapping: " +
                e.getMessage());
            System.err.println("[ZONE] All zones will use radius-based fallback");
        } catch (Exception e) {
            System.err.println("[ZONE] Warning: Error during polygon zone setup: " +
                e.getMessage());
            System.err.println("[ZONE] All zones will use radius-based fallback");
        }
    }

    /**
     * Build density-weighted Built-up pixel indices for all zones.
     * Uses JAXA land-use raster (Built-up=2) combined with POI proximity
     * to create per-zone weighted sampling tables for realistic trip distribution.
     *
     * <p>Zones with very large envelopes (>140km) are automatically skipped
     * by BuiltUpIndex to prevent excessive memory/computation on inter-regional zones.
     */
    private void buildBuiltUpIndices() {
        if (!geoValidator.hasRaster()) {
            System.out.println("[BUILTUP-IDX] Raster not loaded — skipping density index construction");
            return;
        }

        long startTime = System.currentTimeMillis();

        // Primary raster (50m, Kanto/Chubu/Tohoku: 135-143°E × 33.5-40.5°N)
        final byte[] r1 = geoValidator.getLandUseRaster();
        final double r1MinLon = geoValidator.getRasterMinLon(), r1MaxLon = geoValidator.getRasterMaxLon();
        final double r1MinLat = geoValidator.getRasterMinLat(), r1MaxLat = geoValidator.getRasterMaxLat();
        final int r1Width = geoValidator.getRasterWidth(), r1Height = geoValidator.getRasterHeight();

        // Extension raster (100m, full Japan: 129-146°E × 30-46°N — fills gaps outside primary)
        final boolean hasExt = geoValidator.hasExtensionRaster();
        final byte[] r2 = hasExt ? geoValidator.getExtensionRaster() : null;
        final double r2MinLon = hasExt ? geoValidator.getExtRasterMinLon() : 0;
        final double r2MaxLon = hasExt ? geoValidator.getExtRasterMaxLon() : 0;
        final double r2MinLat = hasExt ? geoValidator.getExtRasterMinLat() : 0;
        final double r2MaxLat = hasExt ? geoValidator.getExtRasterMaxLat() : 0;
        final int r2Width = hasExt ? geoValidator.getExtRasterWidth() : 0;
        final int r2Height = hasExt ? geoValidator.getExtRasterHeight() : 0;

        // Compute resolution ratio for merge weight scaling (extension pixels → primary equivalents)
        // e.g., 100m extension pixel represents 4× the area of a 50m primary pixel
        final double extPixelArea;
        final double primaryPixelArea;
        if (hasExt) {
            double r1PixW = (r1MaxLon - r1MinLon) / r1Width;
            double r2PixW = (r2MaxLon - r2MinLon) / r2Width;
            primaryPixelArea = r1PixW * r1PixW;   // approximate square pixels
            extPixelArea = r2PixW * r2PixW;
        } else {
            primaryPixelArea = 1.0;
            extPixelArea = 1.0;
        }
        final double extWeightScale = extPixelArea / primaryPixelArea;

        // Parallel BuiltUpIndex construction — each zone is independent
        java.util.concurrent.atomic.AtomicInteger builtCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger skippedCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger totalPixels = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger mergedCount = new java.util.concurrent.atomic.AtomicInteger();

        deliveryZones.parallelStream().forEach(zone -> {
            List<PointOfInterest> zonePOIs = poiManager.getPOIsInZone(zone.getZoneId());

            // Build from primary raster
            BuiltUpIndex primaryIndex = new BuiltUpIndex(zone, r1,
                r1MinLon, r1MaxLon, r1MinLat, r1MaxLat, r1Width, r1Height, zonePOIs);

            // Build from extension raster if zone extends beyond primary bounds
            BuiltUpIndex extIndex = null;
            if (hasExt) {
                org.locationtech.jts.geom.Envelope env = zone.getEnvelope();
                // Skip extension for zones fully covered by higher-res primary raster
                boolean fullyInPrimary = env != null
                    && env.getMinX() >= r1MinLon && env.getMaxX() <= r1MaxLon
                    && env.getMinY() >= r1MinLat && env.getMaxY() <= r1MaxLat;
                if (!fullyInPrimary && env != null
                        && env.getMaxY() > r2MinLat && env.getMinY() < r2MaxLat
                        && env.getMaxX() > r2MinLon && env.getMinX() < r2MaxLon) {
                    // Exclude primary raster bounds to avoid duplicate pixels
                    org.locationtech.jts.geom.Envelope primaryBounds =
                        new org.locationtech.jts.geom.Envelope(r1MinLon, r1MaxLon, r1MinLat, r1MaxLat);
                    extIndex = new BuiltUpIndex(zone, r2,
                        r2MinLon, r2MaxLon, r2MinLat, r2MaxLat, r2Width, r2Height, zonePOIs,
                        primaryBounds);
                }
            }

            // Merge if both are non-empty, scaling extension weights by pixel area ratio
            BuiltUpIndex finalIndex;
            if (extIndex != null && !extIndex.isEmpty()) {
                if (!primaryIndex.isEmpty()) {
                    finalIndex = BuiltUpIndex.merge(primaryIndex, extIndex, extWeightScale);
                    mergedCount.incrementAndGet();
                } else {
                    finalIndex = extIndex;
                }
            } else {
                finalIndex = primaryIndex;
            }

            if (!finalIndex.isEmpty()) {
                zone.setBuiltUpIndex(finalIndex);
                totalPixels.addAndGet(finalIndex.getPixelCount());
                builtCount.incrementAndGet();
            } else {
                skippedCount.incrementAndGet();
            }
        });

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[BUILTUP-IDX] Built density indices for " + builtCount.get() + "/" +
            deliveryZones.size() + " zones (" + totalPixels.get() + " total pixels, " +
            String.format("%.1f", totalPixels.get() * 24.0 / 1024 / 1024) + " MB) in " + elapsed + "ms");
        if (mergedCount.get() > 0) {
            System.out.println("[BUILTUP-IDX] Merged primary+extension raster for " +
                mergedCount.get() + " zones (multi-raster coverage)");
        }
        if (skippedCount.get() > 0) {
            System.out.println("[BUILTUP-IDX] Skipped " + skippedCount.get() +
                " zones (no Built-up pixels in zone or envelope too large)");
        }
    }

    /**
     * Load zone mapping from MFS zone names to simulation zone indices.
     *
     * @param mappingPath Path to zone_mapping.csv
     * @return Map from MFS zone name to simulation zone index
     */
    private Map<String, Integer> loadZoneMapping(String mappingPath) throws IOException {
        Map<String, Integer> mapping = new HashMap<>();

        File mappingFile = new File(mappingPath);
        if (!mappingFile.exists()) {
            System.err.println("[O-D] Zone mapping file not found: " + mappingPath);
            return mapping;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            String line = reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                String mfsZoneName = parts[1].trim();
                String simZoneId = parts[2].trim();

                // Skip external zones and inter-metro zones for INTRA routing
                if (simZoneId.equals("EXTERNAL") ||
                    simZoneId.equals("INTER_METRO") ||
                    simZoneId.equals("AGGREGATE")) {
                    continue;
                }

                // Find simulation zone index
                for (int i = 0; i < deliveryZones.size(); i++) {
                    if (deliveryZones.get(i).getZoneId().equals(simZoneId)) {
                        mapping.put(mfsZoneName, i);
                        break;
                    }
                }
            }
        }

        System.out.println("[O-D] Loaded zone mapping: " + mapping.size() + " MFS zones -> " +
            deliveryZones.size() + " simulation zones");

        return mapping;
    }

    // ========================================================================
    // DUAL MODE HELPERS
    // ========================================================================

    /**
     * Compute geography bounds from all loaded zone envelopes.
     * Ensures inter-regional zones (MFS67-MFS71) generate home points
     * inside their actual geography rather than being clamped to Tokyo.
     */
    private void computeGeographyBounds() {
        if (!deliveryZones.isEmpty()) {
            double minLon = deliveryZones.stream()
                .mapToDouble(z -> z.getCenterLongitude() - z.getRadiusKm() / 111.0)
                .min().orElse(130.0);
            double maxLon = deliveryZones.stream()
                .mapToDouble(z -> z.getCenterLongitude() + z.getRadiusKm() / 111.0)
                .max().orElse(145.0);
            double minLat = deliveryZones.stream()
                .mapToDouble(z -> z.getCenterLatitude() - z.getRadiusKm() / 111.0)
                .min().orElse(30.0);
            double maxLat = deliveryZones.stream()
                .mapToDouble(z -> z.getCenterLatitude() + z.getRadiusKm() / 111.0)
                .max().orElse(45.0);
            config.setGeographyBounds(minLon, maxLon, minLat, maxLat);
            System.out.println(String.format("[DUAL] Geography bounds set from zone envelopes: " +
                "lon [%.2f, %.2f], lat [%.2f, %.2f]", minLon, maxLon, minLat, maxLat));
        }
    }

    // ========================================================================
    // RESULT BUILDER
    // ========================================================================

    private ZoneLoadResult buildResult() {
        return new ZoneLoadResult(
            deliveryZones, odMatrix, commodityRouter, tripGenerator,
            poiManager, gaBalancer, metricsTracker, destinationSelector,
            pointGenerator, networkIndex
        );
    }
}
