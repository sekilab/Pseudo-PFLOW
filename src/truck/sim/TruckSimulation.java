package truck.sim;

import truck.sim.util.DistanceCalculator;
import static truck.sim.TruckSimulationConstants.*;

import java.io.*;
import java.util.*;

// Shapefile-based land validation using existing dcity infrastructure
import dcity.aggr.Boundaries;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * Main simulation controller for Tokyo Truck Agent-Based Model
 * Version 1.0 - PSEUDO PFLOW COMPATIBILITY WITH EMPTY TRIPS
 *
 * SURVEY DATA IMPLEMENTATION:
 * - Based on Tokyo Metropolitan Freight Survey (H25/2013)
 * - 327,108 vehicles/day, 1,726,420 tons/day across 72 zones
 * - Fleet mix: 26.1% heavy, 16.9% medium, 28.6% small, 28.4% light
 * - Cargo weight: gamma distribution (shape=2.0, scale=2.40)
 * - Empty trip probability: 35%
 *
 * TRIP GENERATION LOGIC:
 * - Smart destination selection based on zone attractiveness
 * - Time-dependent multipliers (morning/afternoon 1.5x, night 0.3x)
 * - Average 8 trips per truck (Gaussian distribution)
 * - Loading rates by commodity and vehicle size
 *
 * @author Truck ABM Framework
 * @version 1.0
 */
public class TruckSimulation {

    // Configuration singleton
    private TruckConfig config;
    private MetropolitanConfig metroConfig;
    private MetricsTracker metricsTracker;

    // Default config file path
    private static final String DEFAULT_CONFIG_FILE = "config/truck/truck_config.properties";

    // Data structures
    private List<TruckAgent> truckFleet;
    private List<TruckTrip> allTrips;
    private List<DeliveryZone> deliveryZones;
    private OriginDestinationMatrix odMatrix;
    private CommodityRouter commodityRouter;
    private TripGenerator tripGenerator;
    private POIManager poiManager;  // NEW: POI system for INTRA trips
    private GenerationAttractionBalancer gaBalancer;  // NEW: G-A Balance enforcer
    private Random random;

    // Shapefile-based land boundary validation (replaces bounding box approach)
    private Boundaries japanBoundaries;
    private GeometryFactory geometryFactory;

    // Cache for land validation - avoids repeated expensive queries
    private Map<String, Boolean> landValidationCache = new HashMap<>();

    // Track last selected destination zone and POI from selectBalancedDestination
    private String lastSelectedDestZoneId = null;
    private String lastSelectedDestPOIId = null;

    // Trip ID counter
    private long tripIdCounter;

    // Network-aware point generation (Phase 3)
    private truck.sim.spatial.TransportNetworkIndex networkIndex;
    private truck.sim.spatial.NetworkAwarePointGenerator networkAwareGenerator;

    /**
     * Constructor
     */
    public TruckSimulation() {
        this.config = TruckConfig.getInstance();
        this.truckFleet = new ArrayList<>();
        this.allTrips = new ArrayList<>();
        this.deliveryZones = new ArrayList<>();
        this.tripIdCounter = 0;
        this.geometryFactory = new GeometryFactory();
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

    /**
     * Initialize delivery zones from CSV file
     */
    private void initializeDeliveryZones() {
        System.out.println("[CHECKPOINT] Loading delivery zones from CSV...");

        String zonesFile = config.getZonesFile();
        String configDir = "config/";
        String zonesPath = configDir + zonesFile;

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
                if (parts.length != 9) {
                    System.err.println("  Warning: Skipping malformed line " + lineNum +
                        " (expected 9 columns, got " + parts.length + ")");
                    continue;
                }

                try {
                    String zoneId = parts[0].trim();
                    String name = parts[1].trim();
                    double centerLon = Double.parseDouble(parts[2].trim());
                    double centerLat = Double.parseDouble(parts[3].trim());
                    double radius = Double.parseDouble(parts[4].trim());
                    double warehousesWeight = Double.parseDouble(parts[5].trim());
                    double retailWeight = Double.parseDouble(parts[6].trim());
                    double constructionWeight = Double.parseDouble(parts[7].trim());
                    double residentialWeight = Double.parseDouble(parts[8].trim());

                    DeliveryZone zone = new DeliveryZone(
                        zoneId, name, centerLon, centerLat, radius,
                        warehousesWeight, retailWeight, constructionWeight, residentialWeight
                    );
                    deliveryZones.add(zone);

                } catch (NumberFormatException e) {
                    System.err.println("  Warning: Skipping line " + lineNum +
                        " due to number format error: " + e.getMessage());
                }
            }

            System.out.println("[CHECKPOINT] Loaded " + deliveryZones.size() +
                " delivery zones from " + zonesFile);

            // Initialize O-D matrix with survey-based flow patterns
            odMatrix = new OriginDestinationMatrix(deliveryZones.size(), random);
            System.out.println("[CHECKPOINT] Initialized O-D matrix (" + deliveryZones.size() +
                "x" + deliveryZones.size() + " zones) with default flow patterns");

            // Load MFS O-D matrix from CSV (NEW: Phase 3 - Model Refinement)
            if (config.getUseMFSODMatrix()) {
                try {
                    Map<String, Integer> zoneMapping = loadZoneMapping("config/truck/zones/mapping.csv");
                    odMatrix.loadFromMFSCSV("config/truck/flows/od_volume.csv", zoneMapping);
                    System.out.println("[CHECKPOINT] Loaded MFS O-D probability matrix from CSV");
                } catch (IOException e) {
                    System.err.println("[O-D] Warning: Could not load MFS O-D matrix: " + e.getMessage());
                    System.err.println("[O-D] Using default flow patterns:");
                }
            } else {
                System.out.println("[CHECKPOINT] Using default O-D flow patterns (MFS matrix disabled)");
            }

            // Initialize commodity router with MFS commodity profiles
            commodityRouter = new CommodityRouter(random);
            System.out.println("[CHECKPOINT] Initialized CommodityRouter with 9 commodity types");

            // Initialize trip generator with facility-based rates (Phase 4)
            tripGenerator = new TripGenerator(random, config);
            System.out.println("[CHECKPOINT] Initialized TripGenerator with facility-based rates");

            // Load POI Manager
            poiManager = new POIManager(random);

            // Load Japan land boundaries for validation
            loadJapanBoundaries();

            // Load transport networks for network-aware point generation (Phase 3)
            loadTransportNetworks();

            // Load polygon-based zone boundaries (Phase 2)
            loadPolygonZones();

            // Set land checker for POI validation (INVERTED from old water exclusion logic)
            poiManager.setLandChecker(this::isOnLand);
            String poiMode = config.getPOIMode();

            // Phase 3: Load Establishments and Industry Mix
            loadEstablishmentData();

            try {
                if (poiMode.equals("auto_generate")) {
                    // Auto-generate POIs from establishment data
                    String establishmentFile = config.getEstablishmentFile();
                    poiManager.generatePOIsFromEstablishments(
                        "config/" + establishmentFile, deliveryZones);
                    System.out.println("[CHECKPOINT] Auto-generated POIs from establishment data");
                } else {
                    // Load POIs from CSV files (default)
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

            // Initialize Generation-Attraction Balancer (NEW: G-A Balance)
            if (config.getUseGABalance()) {
                try {
                    gaBalancer = new GenerationAttractionBalancer(random);
                    gaBalancer.loadTargets("config/truck/flows/ga_targets.csv");
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

            // Print zone facility types
            System.out.println("[CHECKPOINT] Zone facility types:");
            for (DeliveryZone zone : deliveryZones) {
                System.out.println("  " + zone.getZoneId() + ": " + zone.getFacilityType() +
                    " (" + zone.getFacilityType().getBaseTripsPerDay() + " trips/day base rate)");
            }

            // Initialize comprehensive metrics tracker
            this.metricsTracker = new MetricsTracker();
            System.out.println("[CHECKPOINT] Initialized MetricsTracker for comprehensive metrics");

        } catch (IOException e) {
            System.err.println("Error loading zones file: " + e.getMessage());
            System.err.println("  Using empty zone list");
            // Initialize empty O-D matrix as fallback
            odMatrix = new OriginDestinationMatrix(0, random);
            commodityRouter = new CommodityRouter(random);
            tripGenerator = new TripGenerator(random, config);
            this.metricsTracker = new MetricsTracker();
        }
    }

    /**
     * Initialize delivery zones from BOTH intra and inter zone files (DUAL mode).
     * This ensures a single simulation with combined zones, avoiding fleet doubling.
     *
     * @param intraZonesFile Path to intra-metropolitan zones CSV
     * @param interZonesFile Path to inter-metropolitan zones CSV
     */
    private void initializeDeliveryZonesDual(String intraZonesFile, String interZonesFile) {
        System.out.println("[DUAL] Loading combined delivery zones from both files...");

        String configDir = "config/truck/";
        int totalLoaded = 0;

        // Load INTRA zones
        totalLoaded += loadZonesFromFile(configDir + intraZonesFile, "INTRA");

        // Load INTER zones
        totalLoaded += loadZonesFromFile(configDir + interZonesFile, "INTER");

        System.out.println("[DUAL] Total zones loaded: " + totalLoaded +
            " (INTRA + INTER combined)");

        // Initialize O-D matrix with combined zones
        odMatrix = new OriginDestinationMatrix(deliveryZones.size(), random);
        System.out.println("[DUAL] Initialized O-D matrix (" + deliveryZones.size() +
            "x" + deliveryZones.size() + " zones)");

        // Load MFS O-D matrix if enabled
        if (config.getUseMFSODMatrix()) {
            try {
                Map<String, Integer> zoneMapping = loadZoneMapping("config/truck/zones/mapping.csv");
                odMatrix.loadFromMFSCSV("config/truck/flows/od_volume.csv", zoneMapping);
                System.out.println("[DUAL] Loaded MFS O-D probability matrix");
            } catch (IOException e) {
                System.err.println("[DUAL] Warning: Could not load MFS O-D matrix: " + e.getMessage());
            }
        }

        // Initialize commodity router
        commodityRouter = new CommodityRouter(random);
        System.out.println("[DUAL] Initialized CommodityRouter");

        // Initialize trip generator
        tripGenerator = new TripGenerator(random, config);
        System.out.println("[DUAL] Initialized TripGenerator");

        // Load POI Manager
        poiManager = new POIManager(random);

        // Load Japan land boundaries for validation
        loadJapanBoundaries();

        // Load transport networks for network-aware point generation (Phase 3)
        loadTransportNetworks();

        // Load polygon-based zone boundaries (Phase 2)
        loadPolygonZones();

        // Set land checker for POI validation (INVERTED from old water exclusion logic)
        poiManager.setLandChecker(this::isOnLand);
        try {
            String poiMode = config.getPOIMode();
            if (poiMode.equals("auto_generate")) {
                String establishmentFile = config.getEstablishmentFile();
                poiManager.generatePOIsFromEstablishments(
                    "config/truck/" + establishmentFile, deliveryZones);
            } else {
                poiManager.loadPOIsFromCSV("config/truck/facilities/");
            }

            // Link POIs to zones
            for (DeliveryZone zone : deliveryZones) {
                List<PointOfInterest> zonePOIs = poiManager.getPOIsInZone(zone.getZoneId());
                zone.setPOIs(zonePOIs);
            }
        } catch (IOException e) {
            System.err.println("[DUAL] Warning: Could not initialize POIs: " + e.getMessage());
        }

        // Load establishment data
        loadEstablishmentData();

        // Initialize G-A Balancer
        if (config.getUseGABalance()) {
            try {
                gaBalancer = new GenerationAttractionBalancer(random);
                gaBalancer.loadTargets("config/truck/flows/ga_targets.csv");
                System.out.println("[DUAL] G-A Balancer initialized");
            } catch (IOException e) {
                System.err.println("[DUAL] Warning: Could not load G-A targets: " + e.getMessage());
                gaBalancer = null;
            }
        }

        // Initialize metrics tracker
        this.metricsTracker = new MetricsTracker();
        System.out.println("[DUAL] Initialized MetricsTracker");

        // Compute geography bounds from all loaded zones so inter-regional zones
        // (MFS67-MFS71) generate home points inside their actual geography, not
        // clamped to the Tokyo bounding box.
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

    /**
     * Load zones from a single CSV file into the deliveryZones list.
     *
     * @param zonesPath Full path to zones CSV file
     * @param tag Tag for logging (e.g., "INTRA" or "INTER")
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
                    String zoneId = parts[0].trim();
                    String name = parts[1].trim();
                    double centerLon = Double.parseDouble(parts[2].trim());
                    double centerLat = Double.parseDouble(parts[3].trim());
                    double radius = Double.parseDouble(parts[4].trim());
                    double warehousesWeight = Double.parseDouble(parts[5].trim());
                    double retailWeight = Double.parseDouble(parts[6].trim());
                    double constructionWeight = Double.parseDouble(parts[7].trim());
                    double residentialWeight = Double.parseDouble(parts[8].trim());

                    DeliveryZone zone = new DeliveryZone(
                        zoneId, name, centerLon, centerLat, radius,
                        warehousesWeight, retailWeight, constructionWeight, residentialWeight
                    );

                    // Tag zone as INTRA or INTER for routing decisions
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
                DeliveryZone zone = (simId != null) ? findZoneByZoneId(simId) : null;
                
                if (zone != null) {
                    zone.setSubRegion(subRegion);
                    // factory, logistics, office, store, other
                    String[] facilities = {"factory", "logistics", "office", "store", "other"};
                    for (int i = 0; i < facilities.length; i++) {
                        int count = Integer.parseInt(parts[i+2]);
                        zone.setEstablishmentCount(facilities[i], count);
                        
                        // Distribute into industries
                        Map<String, Double> shares = industryMix.get(facilities[i]);
                        if (shares != null) {
                            Map<String, Integer> industryCounts = new HashMap<>();
                            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                                industryCounts.put(entry.getKey(), (int)Math.round(count * entry.getValue()));
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

    private DeliveryZone findZoneByRegionName(String name) {
        // Look through mapping to find zoneId
        // This is a bit inefficient but only done at init
        for (DeliveryZone zone : deliveryZones) {
            // Check if name matches. Zone names in CSV might be human readable, 
            // but File 21 names match zone_mapping.csv 'mfs_zone_name'
            // We need to verify what 'zone.getName()' returns.
            // If it doesn't match, we might need a reverse mapping.
            if (zone.getName().equalsIgnoreCase(name) || zone.getZoneId().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Load Japan land boundaries from shapefile for validation.
     * Uses existing dcity.aggr.Boundaries infrastructure (proven in dcity module).
     * Replaces old bounding box water exclusion with precise polygon validation.
     */
    private void loadJapanBoundaries() {
        String shapefilePath = "src/truck/gm-jp/polbnda_jpn.shp";
        System.out.println("[BOUNDARY] Loading Japan land boundaries from: " + shapefilePath);

        try {
            japanBoundaries = Boundaries.create(shapefilePath);
            System.out.println("[BOUNDARY] Successfully loaded " + japanBoundaries.size() +
                " land boundary polygons");
            System.out.println("[BOUNDARY] Spatial index ready for land validation");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load Japan boundaries: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Cannot run simulation without land boundaries", e);
        }
    }

    /**
     * Load road and rail networks for network-aware point generation (Phase 3).
     * Reuses existing ShpLoader infrastructure and STRtree spatial indexing.
     * Gracefully degrades to pure polygon sampling if loading fails.
     */
    private void loadTransportNetworks() {
        String roadPath = "src/truck/gm-jp/roadl_jpn.shp";
        String railPath = "src/truck/gm-jp/raill_jpn.shp";

        System.out.println("[NETWORK] Loading transport networks...");

        try {
            networkIndex = new truck.sim.spatial.TransportNetworkIndex();

            // Load road network
            networkIndex.loadRoadNetwork(roadPath);
            System.out.println("[NETWORK] Loaded " + networkIndex.getRoadCount() +
                " road segments with accessibility attributes");

            // Load rail network (optional for future multi-modal features)
            try {
                networkIndex.loadRailNetwork(railPath);
                System.out.println("[NETWORK] Loaded " + networkIndex.getRailCount() +
                    " rail segments");
            } catch (Exception e) {
                System.out.println("[NETWORK] Rail network not loaded (optional): " +
                    e.getMessage());
            }

            System.out.println("[NETWORK] Spatial index built (STRtree)");

            // Initialize network-aware point generator
            networkAwareGenerator = new truck.sim.spatial.NetworkAwarePointGenerator();
            System.out.println("[NETWORK] Network-aware point generation enabled");

            // Link to POI manager for proximity filtering (Phase 3 optional)
            if (poiManager != null) {
                poiManager.setNetworkIndex(networkIndex);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load transport networks: " + e.getMessage());
            e.printStackTrace();
            System.err.println("[WARNING] Proceeding without network-aware generation");
            System.err.println("[WARNING] Points will use pure polygon sampling (existing behavior)");
            networkIndex = null;  // Graceful degradation
            networkAwareGenerator = null;
        }
    }

    /**
     * Load polygon-based zone boundaries (Phase 2).
     * Associates MFS zones with shapefile administrative boundaries.
     * Falls back to radius-based zones if mapping unavailable.
     */
    private void loadPolygonZones() {
        String mappingPath = "config/truck/zones/zone_boundary_mapping.csv";
        String shapefilePath = "src/truck/gm-jp/polbnda_jpn.shp";
        System.out.println("[ZONE] Loading polygon-based zone boundaries...");

        try {
            truck.sim.util.ZoneBoundaryMapper zoneBoundaryMapper =
                new truck.sim.util.ZoneBoundaryMapper();

            // Load zone-to-adm_code mapping
            zoneBoundaryMapper.loadMapping(mappingPath);

            // Index boundary geometries by adm_code (load shapefile again for attribute access)
            zoneBoundaryMapper.loadBoundaryGeometries(shapefilePath);

            // Associate zones with polygons
            zoneBoundaryMapper.associateZoneBoundaries(deliveryZones);

            // Count polygon vs radius zones
            int polygonZones = (int) deliveryZones.stream()
                .filter(z -> z.hasPolygonBoundary())
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
     * Initialize truck fleet with heterogeneous agent types.
     *
     * FLEET SCALING (MLIT Alignment v2.0):
     * - Total registered: 2,530,000 trucks (MLIT national statistics)
     * - Operating rate: 56.7% (MLIT 実働率)
     * - Daily active fleet: 1,434,510 trucks (what we simulate)
     * - Scale factor from MFS: 4.387x (327,108 → 1,434,510)
     *
     * ORIGINAL MFS BASELINE (H25/2013):
     * - Fleet size: 327,108 vehicles/day
     * - Operating rate: 98% (survey-based)
     * - Vehicle mix: 26% heavy, 17% medium, 29% small, 28% light
     * - Truck types: 37% DELIVERY, 28.5% MIXED, 34.5% LONG_HAUL
     */
    private void initializeTrucks() {
        System.out.println("[CHECKPOINT] Initializing " + config.getTruckFleetSize() + " trucks...");

        int numTrucks = config.getTruckFleetSize();
        int deliveryCount = 0, longHaulCount = 0, urbanCount = 0;
        int heavyCount = 0, mediumCount = 0, smallCount = 0, lightCount = 0;

        // Build O-D-weighted zone assignment: weight each zone by its raw MFS outbound
        // truck volume so the simulated origin distribution matches the survey.
        // Zones not present in the O-D matrix get a small minimum weight (1.0) so they
        // still receive at least some trucks.
        double[] zoneWeights = new double[deliveryZones.size()];
        double totalWeight = 0.0;
        for (int z = 0; z < deliveryZones.size(); z++) {
            String zid = deliveryZones.get(z).getZoneId();
            // Find O-D matrix index for this zone (matches by position in deliveryZones list)
            double rawFlow = odMatrix.getRawOutflowTotal(z);
            zoneWeights[z] = (rawFlow > 0.0) ? rawFlow : 1.0;
            totalWeight += zoneWeights[z];
        }
        // Build cumulative weight array for fast O(1) zone selection
        double[] cumWeights = new double[deliveryZones.size()];
        double cumSum = 0.0;
        for (int z = 0; z < deliveryZones.size(); z++) {
            cumSum += zoneWeights[z];
            cumWeights[z] = cumSum;
        }

        for (int i = 0; i < numTrucks; i++) {
            // Distribute trucks across zones weighted by MFS O-D outbound volumes
            double homeLon, homeLat;
            String homePOIId = null;

            if (!deliveryZones.isEmpty()) {
                // O-D-weighted random zone selection
                double roll = random.nextDouble() * totalWeight;
                int zoneIdx = deliveryZones.size() - 1;
                for (int z = 0; z < deliveryZones.size(); z++) {
                    if (roll < cumWeights[z]) {
                        zoneIdx = z;
                        break;
                    }
                }
                DeliveryZone homeZone = deliveryZones.get(zoneIdx);

                // POI-first home location: try to anchor truck to a facility in its home zone.
                // This ensures trucks departing the same facility share identical origin coordinates.
                // Falls back to road-snapped random point when no POI exists in the zone.
                if (poiManager != null && poiManager.hasPOIs()) {
                    // primaryGoodsType not yet determined — use null to get any facility type
                    PointOfInterest homePOI = poiManager.selectPOIForZone(homeZone.getZoneId(), null);
                    if (homePOI != null) {
                        homeLon = homePOI.getLongitude();
                        homeLat = homePOI.getLatitude();
                        homePOIId = homePOI.getPoiId();
                    } else {
                        double[] homePoint = generatePointInZone(homeZone);
                        homeLon = homePoint[0];
                        homeLat = homePoint[1];
                    }
                } else {
                    double[] homePoint = generatePointInZone(homeZone);
                    homeLon = homePoint[0];
                    homeLat = homePoint[1];
                }
            } else {
                // Fallback: Random home location within geography bounds
                homeLon = config.getGeographyMinLon() +
                    random.nextDouble() * (config.getGeographyMaxLon() - config.getGeographyMinLon());
                homeLat = config.getGeographyMinLat() +
                    random.nextDouble() * (config.getGeographyMaxLat() - config.getGeographyMinLat());
            }

            // Determine truck type based on configured probabilities
            TruckType truckType;
            double typeRoll = random.nextDouble();
            if (typeRoll < config.getTruckTypeDeliveryProb()) {
                truckType = TruckType.DELIVERY;
                deliveryCount++;
            } else if (typeRoll < config.getTruckTypeDeliveryProb() + config.getTruckTypeLongHaulProb()) {
                truckType = TruckType.LONG_HAUL;
                longHaulCount++;
            } else {
                truckType = TruckType.MIXED_OPERATION;
                urbanCount++;
            }

            // Determine vehicle size based on truck type CONSTRAINTS
            String vehicleSize;
            double capacityTons;

            // Get vehicle probabilities for this truck type [light, small, medium, heavy]
            double[] vehicleProbs;
            switch (truckType) {
                case DELIVERY:
                    // DELIVERY: Only light (<2t) or small (2-4t)
                    // Normalize probabilities to sum to 1.0 for allowed sizes only
                    double[] rawDeliveryProbs = config.getDeliveryVehicleProbs();
                    double lightProb = rawDeliveryProbs[0];
                    double smallProb = rawDeliveryProbs[1];
                    double sum = lightProb + smallProb;
                    vehicleProbs = new double[]{lightProb/sum, smallProb/sum, 0.0, 0.0};
                    break;
                case LONG_HAUL:
                    // LONG_HAUL: Heavy (10t+) or Medium (4-10t) from config
                    double[] rawLongHaulProbs = config.getLongHaulVehicleProbs();
                    double longHaulMediumProb = rawLongHaulProbs[2];
                    double longHaulHeavyProb = rawLongHaulProbs[3];
                    double longHaulSum = longHaulMediumProb + longHaulHeavyProb;
                    if (longHaulSum > 0) {
                        vehicleProbs = new double[]{0.0, 0.0, longHaulMediumProb/longHaulSum, longHaulHeavyProb/longHaulSum};
                    } else {
                        vehicleProbs = new double[]{0.0, 0.0, 0.0, 1.0}; // fallback to heavy only
                    }
                    break;
                case MIXED_OPERATION:
                    // MIXED_OPERATION: medium (4-10t) and small (2-4t) from config
                    double[] rawMixedProbs = config.getUrbanVehicleProbs();
                    double mixedSmallProb = rawMixedProbs[1];
                    double mixedMediumProb = rawMixedProbs[2];
                    double mixedSum = mixedSmallProb + mixedMediumProb;
                    if (mixedSum > 0) {
                        vehicleProbs = new double[]{0.0, mixedSmallProb/mixedSum, mixedMediumProb/mixedSum, 0.0};
                    } else {
                        vehicleProbs = new double[]{0.0, 0.0, 1.0, 0.0}; // fallback to medium only
                    }
                    break;
                default:
                    vehicleProbs = new double[]{0.25, 0.25, 0.25, 0.25};
            }

            double sizeRoll = random.nextDouble();

            // Order: light, small, medium, heavy
            if (sizeRoll < vehicleProbs[0]) {  // Light
                vehicleSize = "light";
                capacityTons = config.getCapacityLargeTons() * 0.8 / 11.5;  // ~0.7 tons
                lightCount++;
            } else if (sizeRoll < vehicleProbs[0] + vehicleProbs[1]) {  // Small
                vehicleSize = "small";
                capacityTons = config.getCapacitySmallTons();
                smallCount++;
            } else if (sizeRoll < vehicleProbs[0] + vehicleProbs[1] + vehicleProbs[2]) {  // Medium
                vehicleSize = "medium";
                capacityTons = config.getCapacityMediumTons();
                mediumCount++;
            } else {  // Heavy
                vehicleSize = "heavy";
                capacityTons = config.getCapacityLargeTons();
                heavyCount++;
            }

            // VALIDATION: Enforce truck type constraints
            if (!truckType.isVehicleSizeAllowed(vehicleSize)) {
                throw new IllegalStateException("Vehicle size " + vehicleSize +
                    " not allowed for truck type " + truckType);
            }

            // Determine primary goods type
            String primaryGoodsType = selectGoodsType();

            // Determine shift start time (staggered)
            long shiftStart;
            double shiftStartRoll = random.nextDouble();

            if (shiftStartRoll < config.getShiftStart1Probability()) {
                shiftStart = config.getShiftStart1Time();
            } else if (shiftStartRoll < config.getShiftStart1Probability() + config.getShiftStart2Probability()) {
                shiftStart = config.getShiftStart2Time();
            } else {
                shiftStart = config.getShiftStart3Time();
            }

            // Variable shift duration: Gaussian distribution
            double shiftDurationHours = config.getShiftDurationAverage() +
                random.nextGaussian() * config.getShiftDurationStddev();

            // Clamp to min/max bounds
            shiftDurationHours = Math.max(config.getShiftDurationMin(),
                Math.min(config.getShiftDurationMax(), shiftDurationHours));
            
            int shiftDuration = (int) shiftDurationHours;

            // Create truck agent with familiar area radius
            double familiarRadius = config.getTruckTypeDeliveryFamiliarRadiusKm();
            TruckAgent truck = new TruckAgent(i, truckType, homeLon, homeLat,
                familiarRadius, vehicleSize, capacityTons, primaryGoodsType,
                shiftStart, shiftDuration);

            // Store home POI ID for facility-anchored first-trip origin
            if (homePOIId != null) {
                truck.setHomePOIId(homePOIId);
            }

            // Assign familiar area zone for DELIVERY type
            if (truckType == TruckType.DELIVERY) {
                String zoneId = findNearestZone(homeLon, homeLat);
                truck.setFamiliarAreaZoneId(zoneId);
            }

            truckFleet.add(truck);
        }

        System.out.println("[CHECKPOINT] Initialized " + numTrucks + " trucks:");
        System.out.println("  DELIVERY: " + deliveryCount + " (" +
            String.format("%.1f%%", 100.0 * deliveryCount / numTrucks) + ")");
        System.out.println("  LONG_HAUL: " + longHaulCount + " (" +
            String.format("%.1f%%", 100.0 * longHaulCount / numTrucks) + ")");
        System.out.println("  MIXED_OPERATION: " + urbanCount + " (" +
            String.format("%.1f%%", 100.0 * urbanCount / numTrucks) + ")");
        System.out.println("  Fleet mix: Heavy=" + heavyCount + ", Medium=" + mediumCount +
            ", Small=" + smallCount + ", Light=" + lightCount);
    }

    /**
     * Select goods type based on survey probabilities
     */
    private String selectGoodsType() {
        String[] types = config.getGoodsTypes();
        double[] probs = config.getGoodsTypeProbabilities();

        double roll = random.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < types.length; i++) {
            cumulative += probs[i];
            if (roll <= cumulative) {
                return types[i];
            }
        }

        return types[0];  // Fallback
    }

    /**
     * Find the nearest delivery zone to a given location
     */
    /**
     * Load zone mapping from MFS zone names to simulation zone indices.
     *
     * Phase 3: Model Refinement - O-D Matrix Implementation
     *
     * Maps 74 MFS regional zones to 20 simulation zones using aggregation rules.
     *
     * @param mappingPath Path to zone_mapping.csv
     * @return Map from MFS zone name to simulation zone index
     * @throws IOException if file read fails
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

                String mfsZoneName = parts[1].trim();  // mfs_zone_name
                String simZoneId = parts[2].trim();    // sim_zone_id
                String aggregationRule = parts[3].trim();

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

    private String findNearestZone(double lon, double lat) {
        double minDistance = Double.MAX_VALUE;
        String nearestZoneId = "DZ01";  // Default

        for (DeliveryZone zone : deliveryZones) {
            double distance = calculateDistance(
                lon, lat,
                zone.getCenterLongitude(), zone.getCenterLatitude()
            );
            if (distance < minDistance) {
                minDistance = distance;
                nearestZoneId = zone.getZoneId();
            }
        }

        return nearestZoneId;
    }

    /**
     * Generate trips for all trucks with empty trip support
     *
     * SURVEY DATA IMPLEMENTATION:
     * - Cargo weight: gamma distribution (shape=2.0, scale=2.40) for mean~4.8 tons
     * - Empty trip probability: 35%
     * - Average 8 trips per truck (Gaussian distribution)
     * - Loading rates by commodity and vehicle size
     */
    private void generateTrips() {
        System.out.println("[CHECKPOINT] Generating trips (delivery + empty)...");

        int totalDeliveryTrips = 0;
        int totalEmptyTrips = 0;
        double totalCargo = 0.0;

        // Progress tracking
        int totalTrucks = truckFleet.size();
        int progressInterval = totalTrucks / 10; // Report every 10%
        int processedTrucks = 0;

        for (TruckAgent truck : truckFleet) {
            long currentTime = truck.getShiftStartTime();
            truck.setCurrentTime(currentTime);

            // Progress logging
            processedTrucks++;
            if (processedTrucks % progressInterval == 0) {
                int percentage = (processedTrucks * 100) / totalTrucks;
                System.out.println("[PROGRESS] Processed " + processedTrucks + " / " +
                    totalTrucks + " trucks (" + percentage + "%) - " +
                    "Generated " + (totalDeliveryTrips + totalEmptyTrips) + " trips so far");
            }

            // Tracking variables for nearby trip logic
            double lastDropoffLon = truck.getHomeLongitude();
            double lastDropoffLat = truck.getHomeLatitude();
            boolean hasLastDropoff = false;
            String lastDropoffPOIId = truck.getHomePOIId();  // facility anchor chain

            // Determine number of trips for this truck
            int numTrips = calculateNumTripsForTruck(truck);

            // Generate trips for this truck
            for (int tripNum = 0; tripNum < numTrips; tripNum++) {
                // Generate empty trip if needed (repositioning)
                TruckTrip emptyTrip = generateEmptyTripIfNeeded(truck, hasLastDropoff,
                                                               lastDropoffLon, lastDropoffLat, currentTime);

                if (emptyTrip != null) {
                    truck.addTrip(emptyTrip);
                    allTrips.add(emptyTrip);
                    totalEmptyTrips++;

                    // Record in metrics tracker (always intra-metro for empty trips)
                    metricsTracker.recordTrip(emptyTrip, truck, false);

                    // Update position and time
                    long travelTime = emptyTrip.calculateTravelTime(config.getRoutingAverageSpeedKmh());
                    currentTime += travelTime;
                    lastDropoffLon = emptyTrip.getDestLongitude();
                    lastDropoffLat = emptyTrip.getDestLatitude();
                    updateTruckState(truck, currentTime, lastDropoffLon, lastDropoffLat);
                }

                // Check if shift time remaining
                if (!truck.hasTimeInShift(3600)) {  // Need at least 1 hour
                    break;
                }

                // Generate delivery trip
                Object[] result = generateDeliveryTrip(truck, lastDropoffLon, lastDropoffLat,
                                                      hasLastDropoff, currentTime, lastDropoffPOIId);

                TruckTrip trip = (TruckTrip) result[0];
                if (trip == null) {
                    // Zone at capacity, stop generating for this truck
                    break;
                }

                currentTime = (Long) result[1];
                boolean isInterMetro = (Boolean) result[2];
                lastDropoffPOIId = (result.length > 3) ? (String) result[3] : null;

                // Add to collections and update statistics
                truck.addTrip(trip);
                allTrips.add(trip);
                totalDeliveryTrips++;
                totalCargo += trip.getCargoWeightTons();

                // Record in metrics tracker
                metricsTracker.recordTrip(trip, truck, isInterMetro);

                // Update tracking variables
                currentTime = trip.getUnloadingEndTime() + (long)config.getRoutingBreakTimeSeconds();
                lastDropoffLon = trip.getDestLongitude();
                lastDropoffLat = trip.getDestLatitude();
                hasLastDropoff = true;
                updateTruckState(truck, currentTime, lastDropoffLon, lastDropoffLat);
            }
        }

        double avgCargoWeight = totalDeliveryTrips > 0 ? totalCargo / totalDeliveryTrips : 0.0;
        double emptyRatio = 100.0 * totalEmptyTrips / allTrips.size();

        System.out.println("[CHECKPOINT] Generated " + allTrips.size() + " trips:");
        System.out.println("  Delivery trips: " + totalDeliveryTrips +
            " (" + String.format("%.0f", totalCargo) + " tons total)");
        System.out.println("  Empty trips: " + totalEmptyTrips +
            " (" + String.format("%.1f%%", emptyRatio) + " empty ratio)");
        System.out.println("  Average cargo: " + String.format("%.2f", avgCargoWeight) + " tons/trip");
    }

    /**
     * Calculates the number of trips for a truck based on its type.
     *
     * <p>Uses truck-type-specific trip counts (MFS-calibrated):
     * <ul>
     *   <li>DELIVERY: 6 trips</li>
     *   <li>MIXED: 4 trips</li>
     *   <li>LONG_HAUL: 2 trips</li>
     * </ul>
     *
     * <p>Adds Gaussian variation (stddev=1.0) and clamps to valid range.
     *
     * @param truck Truck to calculate trips for
     * @return Number of trips to generate (between 1 and config max)
     */
    private int calculateNumTripsForTruck(TruckAgent truck) {
        int typeSpecificTrips = config.getTruckTripsForType(truck.getTruckType());
        int numTrips = (int)(typeSpecificTrips + random.nextGaussian() * 1.0);
        return Math.max(1, Math.min(config.getTruckTripsMax(), numTrips));
    }

    /**
     * Generates loading time with Gaussian distribution.
     *
     * <p>Uses config average and standard deviation, with minimum of 10 seconds.
     *
     * @return Loading time in seconds
     */
    private double generateLoadingTime() {
        return Math.max(
            config.getLoadingTimeAverage() + random.nextGaussian() * config.getLoadingTimeStddev(),
            10.0
        );
    }

    /**
     * Generates unloading time with Gaussian distribution.
     *
     * <p>Uses config average and standard deviation, with minimum of 5 seconds.
     *
     * @return Unloading time in seconds
     */
    private double generateUnloadingTime() {
        return Math.max(
            config.getUnloadingTimeAverage() + random.nextGaussian() * config.getUnloadingTimeStddev(),
            5.0
        );
    }

    /**
     * Updates truck state after completing a trip.
     *
     * <p>Updates:
     * <ul>
     *   <li>Current simulation time</li>
     *   <li>Current longitude</li>
     *   <li>Current latitude</li>
     * </ul>
     *
     * @param truck Truck to update
     * @param currentTime New simulation time
     * @param longitude New longitude position
     * @param latitude New latitude position
     */
    private void updateTruckState(TruckAgent truck, long currentTime, double longitude, double latitude) {
        truck.setCurrentTime(currentTime);
        truck.setCurrentLongitude(longitude);
        truck.setCurrentLatitude(latitude);
    }

    /**
     * Selects destination for empty repositioning trip based on truck type.
     *
     * <p>Truck-type-specific routing strategy:
     * <ul>
     *   <li>DELIVERY: Stay within 8km for urban delivery</li>
     *   <li>LONG_HAUL: Go to INTER zones for long-distance routing</li>
     *   <li>MIXED_OPERATION: Use facility-based selection</li>
     * </ul>
     *
     * @param truck Truck needing repositioning
     * @param lastDropoffLon Last dropoff longitude
     * @param lastDropoffLat Last dropoff latitude
     * @param currentTime Current simulation time
     * @return Destination coordinates [lon, lat]
     */
    private double[] selectEmptyTripDestination(TruckAgent truck, double lastDropoffLon,
                                               double lastDropoffLat, long currentTime) {
        double[] lastDropoff = new double[]{lastDropoffLon, lastDropoffLat};

        if (truck.getTruckType() == TruckType.DELIVERY) {
            // DELIVERY: Stay within 8km (target avg: 9.68km)
            return selectNearbyDestination(truck, lastDropoff, 8.0);
        } else if (truck.getTruckType() == TruckType.LONG_HAUL) {
            // LONG_HAUL: Go to INTER zones
            return selectInterZoneDestination(truck, lastDropoff);
        } else {
            // MIXED_OPERATION: Use standard selection
            String originZoneId = findZoneForLocation(lastDropoffLon, lastDropoffLat);
            return selectFacilityBasedDestination(truck, originZoneId, currentTime);
        }
    }

    /**
     * Creates and configures an empty repositioning trip.
     *
     * <p>Empty trips represent trucks moving without cargo between delivery locations.
     *
     * @param truck Truck making the repositioning trip
     * @param originLon Origin longitude
     * @param originLat Origin latitude
     * @param destLon Destination longitude
     * @param destLat Destination latitude
     * @param currentTime Current simulation time
     * @param distance Trip distance in km
     * @return Configured empty trip
     */
    private TruckTrip createEmptyTrip(TruckAgent truck, double originLon, double originLat,
                                     double destLon, double destLat, long currentTime, double distance) {
        TruckTrip emptyTrip = new TruckTrip(
            tripIdCounter++,
            truck.getTruckId(),
            originLon, originLat,
            destLon, destLat,
            currentTime,
            distance,
            truck.getVehicleSize()
        );

        // Set times for empty trip
        long travelTime = emptyTrip.calculateTravelTime(config.getRoutingAverageSpeedKmh());
        emptyTrip.setLoadingStartTime(currentTime);
        emptyTrip.setDepartureTime(currentTime);
        emptyTrip.setArrivalTime(currentTime + travelTime);
        emptyTrip.setUnloadingEndTime(currentTime + travelTime);
        emptyTrip.setStatus(TruckStatus.EMPTY_RUNNING);

        // Set zones
        emptyTrip.setOriginZoneId(findZoneForLocation(originLon, originLat));
        emptyTrip.setDestZoneId(findZoneForLocation(destLon, destLat));

        return emptyTrip;
    }

    /**
     * Generates an empty repositioning trip if needed.
     *
     * <p>Empty trips are generated with probability from config, and only if:
     * <ul>
     *   <li>Truck has completed a previous delivery</li>
     *   <li>Empty trips are enabled in config</li>
     *   <li>Random probability check passes</li>
     *   <li>Distance to next pickup exceeds threshold</li>
     * </ul>
     *
     * @param truck Truck that may need repositioning
     * @param hasLastDropoff Whether truck has completed a previous trip
     * @param lastDropoffLon Last dropoff longitude
     * @param lastDropoffLat Last dropoff latitude
     * @param currentTime Current simulation time
     * @return Empty trip if generated, null otherwise; also returns updated time/location via array
     */
    private TruckTrip generateEmptyTripIfNeeded(TruckAgent truck, boolean hasLastDropoff,
                                               double lastDropoffLon, double lastDropoffLat,
                                               long currentTime) {
        // Check if empty trip should be generated
        if (!hasLastDropoff || !config.getUseEmptyTrips()) {
            return null;
        }

        if (random.nextDouble() >= config.getEmptyTripProbability()) {
            return null;
        }

        // Select destination for empty trip
        double[] nextPickup = selectEmptyTripDestination(truck, lastDropoffLon, lastDropoffLat, currentTime);

        double emptyDistance = calculateDistance(
            lastDropoffLon, lastDropoffLat,
            nextPickup[0], nextPickup[1]
        );

        // Only create empty trip if distance exceeds threshold
        if (emptyDistance <= config.getEmptyTripThresholdKm()) {
            return null;
        }

        // Create empty trip
        return createEmptyTrip(truck, lastDropoffLon, lastDropoffLat,
                             nextPickup[0], nextPickup[1], currentTime, emptyDistance);
    }

    /**
     * Selects destination for intra-metropolitan delivery trip.
     *
     * <p>Selection strategy priority:
     * <ol>
     *   <li>G-A balanced destination (if balancer enabled)</li>
     *   <li>POI-based destination (if POIs available)</li>
     *   <li>Facility-based destination (fallback)</li>
     * </ol>
     *
     * @param truck Truck making the trip
     * @param origin Origin coordinates [lon, lat]
     * @param currentTime Current simulation time
     * @param commodityType Commodity being transported
     * @return Destination coordinates [lon, lat], or null if zone at capacity
     */
    private double[] selectIntraMetroDestination(TruckAgent truck, double[] origin,
                                                long currentTime, String commodityType) {
        String originZoneId = findZoneForLocation(origin[0], origin[1]);

        // Check G-A balance capacity
        if (gaBalancer != null && !gaBalancer.canGenerate(originZoneId)) {
            return null;  // Zone at generation capacity
        }

        // Strategy 1: G-A balanced selection
        if (gaBalancer != null) {
            return selectBalancedDestination(truck, origin, originZoneId, currentTime, commodityType);
        }

        // Strategy 2: POI-based selection
        if (config.getUsePOIDestinations() && poiManager != null && poiManager.hasPOIs()) {
            int timePeriod = getTimePeriod(currentTime);
            PointOfInterest targetPOI = poiManager.selectPOIForINTRATrip(truck, originZoneId, timePeriod);

            if (targetPOI != null) {
                double lon = targetPOI.getLongitude();
                double lat = targetPOI.getLatitude();

                // Double-check: verify POI is on land (safety check)
                if (isOnLand(lon, lat)) {
                    return new double[]{lon, lat};
                }
                // If not on land, fall through to facility-based fallback
            }
        }

        // Strategy 3: Facility-based fallback
        return selectFacilityBasedDestination(truck, originZoneId, currentTime);
    }

    /**
     * Selects destination for inter-metropolitan delivery trip.
     *
     * @param origin Origin coordinates [lon, lat]
     * @return Destination coordinates [lon, lat] in secondary metro
     */
    private double[] selectInterMetroDestination(double[] origin) {
        MetropolitanConfig.Metropolitan secondaryMetro = metroConfig.getSecondaryMetro();
        return new double[]{
            secondaryMetro.centerLon + (random.nextDouble() - 0.5) * 0.5,
            secondaryMetro.centerLat + (random.nextDouble() - 0.5) * 0.5
        };
    }

    /**
     * Calculates distance for inter-metropolitan trip using route data.
     *
     * @return Distance in km (uses route distance or 350km fallback)
     */
    private double calculateInterMetroDistance() {
        MetropolitanConfig.InterMetroRoute route = metroConfig.getRoute(
            metroConfig.getPrimaryMetro(),
            metroConfig.getSecondaryMetro()
        );
        return route != null ? route.distanceKm : 350.0;
    }

    /**
     * Applies truck-type-specific distance constraints for LONG_HAUL trucks.
     *
     * <p>LONG_HAUL trucks always select INTER zone destinations with average distance ~260km.
     * May snap to POI in selected zone if available.
     *
     * @param truck Truck making the trip
     * @param origin Origin coordinates
     * @param commodityType Commodity being transported
     * @return Adjusted destination coordinates [lon, lat]
     */
    private double[] applyLongHaulConstraints(TruckAgent truck, double[] origin, String commodityType) {
        double[] destination = selectInterZoneDestination(truck, origin);

        // Try to snap to a POI in the selected zone
        String destZoneId = findZoneForLocation(destination[0], destination[1]);
        if (poiManager != null && poiManager.hasPOIs()) {
            PointOfInterest poi = poiManager.selectPOIForTrip(truck, destZoneId, commodityType, 0);
            if (poi != null) {
                double lon = poi.getLongitude();
                double lat = poi.getLatitude();
                // Verify POI is on land
                if (isOnLand(lon, lat)) {
                    destination = new double[]{lon, lat};
                }
            }
        }

        lastSelectedDestZoneId = null; // override O-D selection
        return destination;
    }

    /**
     * Configures all timing and properties for a delivery trip.
     *
     * @param trip Trip to configure
     * @param loadingTime Loading time in minutes
     * @param unloadingTime Unloading time in minutes
     * @param currentTime Current simulation time
     * @param origin Origin coordinates
     * @param destination Destination coordinates
     * @param commodityType Commodity type
     * @return Updated current time after potential time window adjustments
     */
    private long configureTripTiming(TruckTrip trip, double loadingTime, double unloadingTime,
                                    long currentTime, double[] origin, double[] destination,
                                    String commodityType) {
        // Calculate timing
        long loadingTimeSec = (long)(loadingTime * 60);
        long travelTime = trip.calculateTravelTime(config.getRoutingAverageSpeedKmh());
        long unloadingTimeSec = (long)(unloadingTime * 60);

        trip.setLoadingStartTime(currentTime);
        trip.setDepartureTime(currentTime + loadingTimeSec);
        trip.setArrivalTime(currentTime + loadingTimeSec + travelTime);
        trip.setUnloadingEndTime(currentTime + loadingTimeSec + travelTime + unloadingTimeSec);
        trip.setStatus(TruckStatus.IN_TRANSIT);

        // Set zones
        trip.setOriginZoneId(findZoneForLocation(origin[0], origin[1]));
        if (lastSelectedDestZoneId != null) {
            trip.setDestZoneId(lastSelectedDestZoneId);
            lastSelectedDestZoneId = null;
        } else {
            trip.setDestZoneId(findZoneForLocation(destination[0], destination[1]));
        }

        // Apply time window constraints
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
     *
     * <p>This orchestrates the full delivery trip generation process:
     * <ol>
     *   <li>Determine trip type (inter vs intra metro)</li>
     *   <li>Select origin and destination</li>
     *   <li>Apply truck-type-specific constraints</li>
     *   <li>Generate cargo weight and commodity</li>
     *   <li>Create and configure trip object</li>
     *   <li>Apply timing and time window constraints</li>
     * </ol>
     *
     * @param truck Truck to generate trip for
     * @param lastDropoffLon Last dropoff longitude (or home if first trip)
     * @param lastDropoffLat Last dropoff latitude (or home if first trip)
     * @param hasLastDropoff Whether truck has completed a previous trip
     * @param currentTime Current simulation time (may be updated by time windows)
     * @return Array containing [trip, updatedCurrentTime] - trip may be null if zone at capacity
     */
    private Object[] generateDeliveryTrip(TruckAgent truck, double lastDropoffLon, double lastDropoffLat,
                                         boolean hasLastDropoff, long currentTime, String originPOIId) {
        // Determine trip type
        boolean isInterMetro = metroConfig.shouldMakeInterMetroTrip(random, truck.getTruckType(), config);

        // Select origin
        double[] origin = hasLastDropoff ?
            new double[]{lastDropoffLon, lastDropoffLat} :
            new double[]{truck.getHomeLongitude(), truck.getHomeLatitude()};

        // Pre-select commodity
        String commodityType = commodityRouter.selectCommodityForTruckType(truck.getTruckType());

        // Select destination based on trip type
        double[] destination;
        double distance;

        if (isInterMetro && metroConfig.isInterMetropolitan()) {
            // INTER-METROPOLITAN TRIP
            destination = selectInterMetroDestination(origin);
            distance = calculateInterMetroDistance();
        } else {
            // INTRA-METROPOLITAN TRIP
            isInterMetro = false;
            destination = selectIntraMetroDestination(truck, origin, currentTime, commodityType);

            if (destination == null) {
                // Zone at capacity
                return new Object[]{null, currentTime};
            }

            distance = calculateDistance(origin[0], origin[1], destination[0], destination[1]);

            // Apply LONG_HAUL constraints
            if (truck.getTruckType() == TruckType.LONG_HAUL) {
                destination = applyLongHaulConstraints(truck, origin, commodityType);
                distance = calculateDistance(origin[0], origin[1], destination[0], destination[1]);
            }
        }

        // Generate cargo weight
        TruckTrip.LoadingConstraint constraint =
            commodityRouter.isWeightLimited(commodityType) ?
            TruckTrip.LoadingConstraint.WEIGHT :
            TruckTrip.LoadingConstraint.CAPACITY;

        DeliveryZone originZone = findZone(origin[0], origin[1]);
        FacilityType originFacility = (originZone != null) ? originZone.getFacilityType() : FacilityType.MIXED;

        double cargoWeight = tripGenerator.generateCargoWeight(
            originFacility, commodityType, truck.getVehicleSize(),
            truck.getCapacityTons(), commodityRouter, constraint
        );

        // Generate timing
        double loadingTime = generateLoadingTime();
        double unloadingTime = generateUnloadingTime();

        // Create trip
        TruckTrip trip = new TruckTrip(
            tripIdCounter++, truck.getTruckId(),
            origin[0], origin[1], destination[0], destination[1],
            currentTime, distance, commodityType,
            truck.getVehicleSize(), cargoWeight, truck.getCapacityTons(),
            loadingTime, unloadingTime
        );
        trip.setLoadingConstraint(constraint);

        // Thread facility IDs: origin from previous trip's dest POI (or truck home POI),
        // destination from selectBalancedDestination via lastSelectedDestPOIId.
        if (originPOIId != null) {
            trip.setOriginFacilityId(originPOIId);
        }
        if (lastSelectedDestPOIId != null) {
            trip.setDestFacilityId(lastSelectedDestPOIId);
        }
        String tripDestPOIId = lastSelectedDestPOIId;  // capture before clearing
        lastSelectedDestPOIId = null;

        // Configure timing (may update currentTime for time windows)
        currentTime = configureTripTiming(trip, loadingTime, unloadingTime,
                                         currentTime, origin, destination, commodityType);

        // Record in G-A balancer
        if (gaBalancer != null && !isInterMetro) {
            gaBalancer.recordTrip(trip.getOriginZoneId(), trip.getDestZoneId());
        }

        return new Object[]{trip, currentTime, isInterMetro, tripDestPOIId};
    }

    /**
     * Generate cargo weight using gamma distribution
     * Survey data: mean=4.80 tons, median=3.50 tons
     * Gamma distribution: shape and scale from config
     */
    private double generateCargoWeight(double capacityTons) {
        // Gamma distribution parameters from config
        double shape = config.getCargoWeightGammaShape();
        double scale = config.getCargoWeightGammaScale();

        // Generate gamma-distributed random variable
        double cargoWeight = generateGamma(shape, scale);

        // Clamp to vehicle capacity
        cargoWeight = Math.min(cargoWeight, capacityTons);

        return cargoWeight;
    }

    /**
     * Generate gamma-distributed random variable
     * Using shape-scale parameterization
     */
    private double generateGamma(double shape, double scale) {
        // Use Marsaglia and Tsang method for gamma generation
        if (shape < 1.0) {
            // Use rejection method for shape < 1
            return generateGamma(shape + 1.0, scale) * Math.pow(random.nextDouble(), 1.0 / shape);
        }

        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);

        while (true) {
            double x, v;
            do {
                x = random.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0);

            v = v * v * v;
            double u = random.nextDouble();

            if (u < 1.0 - 0.0331 * x * x * x * x) {
                return d * v * scale;
            }

            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }

    /**
     * Select destination using time-dependent attractiveness scoring
     * Implements smart destination selection based on:
     * - Zone attractiveness (warehouses, retail, construction, residential)
     * - Time-dependent multipliers (morning/afternoon 1.5x, night 0.3x)
     * - Truck type preferences (DELIVERY stays in familiar area)
     */
    private double[] selectSmartDestination(TruckAgent truck, long currentTime) {
        TruckType truckType = truck.getTruckType();

        // Get time period for attractiveness calculation
        int timePeriod = config.getTimePeriod(currentTime);

        // Find origin zone (truck's current location)
        int originZoneIndex = findNearestZoneIndex(
            truck.getCurrentLongitude(), truck.getCurrentLatitude());

        // Calculate combined scores for all zones (O-D flows × attractiveness × truck-specific)
        double totalScore = 0.0;
        double[] zoneScores = new double[deliveryZones.size()];

        for (int i = 0; i < deliveryZones.size(); i++) {
            DeliveryZone zone = deliveryZones.get(i);

            // Skip Tokyo Islands — no road access
            if (zone.getZoneId().equals("MFS62")) continue;

            // Component 1: O-D flow from origin to this destination (from MFS survey)
            double odFlow = (originZoneIndex >= 0) ?
                odMatrix.getFlow(originZoneIndex, i) : 1.0;

            // Component 2: Time-dependent attractiveness (facility types × time period)
            double attractiveness = zone.calculateAttractiveness(timePeriod,
                config.getAttractivenessBeta1(),
                config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(),
                config.getAttractivenessBeta4());

            // Component 3: Truck-type specific preferences
            double truckTypeMultiplier = 1.0;
            if (truckType == TruckType.DELIVERY) {
                // DELIVERY trucks: boost zones within familiar area
                double distance = calculateDistance(
                    truck.getHomeLongitude(), truck.getHomeLatitude(),
                    zone.getCenterLongitude(), zone.getCenterLatitude()
                );
                if (distance <= truck.getFamiliarAreaRadiusKm()) {
                    truckTypeMultiplier = 3.0;  // Strong boost for familiar area
                } else {
                    truckTypeMultiplier = 0.1;  // Heavily penalize outside familiar area
                }
            }

            // Combined score: O-D flow × attractiveness × truck-type preference
            double combinedScore = odFlow * attractiveness * truckTypeMultiplier;
            zoneScores[i] = combinedScore;
            totalScore += combinedScore;
        }

        // Select zone based on weighted probability
        double rand = random.nextDouble() * totalScore;
        double cumulative = 0.0;
        DeliveryZone selectedZone = deliveryZones.get(0);

        for (int i = 0; i < deliveryZones.size(); i++) {
            cumulative += zoneScores[i];
            if (rand <= cumulative) {
                selectedZone = deliveryZones.get(i);
                break;
            }
        }

        // Generate random point within selected zone (with water exclusion)
        return generatePointInZone(selectedZone);
    }

    /**
     * Select destination using facility-based flow logic (MFS File 07).
     * Enhanced with industry-based filtering (MFS File 06).
     *
     * @param truck The truck agent
     * @param originZoneId The ID of the origin zone
     * @param currentTime Current simulation time
     * @return Destination coordinates [lon, lat]
     */
    private double[] selectFacilityBasedDestination(TruckAgent truck, String originZoneId, long currentTime) {
        DeliveryZone originZone = findZoneByZoneId(originZoneId);
        FacilityType originType = (originZone != null) ? originZone.getFacilityType() : FacilityType.MIXED;

        // 1. Determine target facility type (e.g. Factory -> Logistics)
        String targetTypeKey = commodityRouter.selectDestinationFacilityType(originType);
        FacilityType targetType = commodityRouter.mapKeyToFacilityType(targetTypeKey);

        // NEW: Determine target industry (MFS File 06)
        String originIndustry = (originZone != null) ? originZone.getDominantIndustry() : "0";
        String targetIndustry = commodityRouter.selectDestinationIndustry(originIndustry);
        boolean useIndustryFilter = commodityRouter.hasIndustryFlowData() && !"0".equals(targetIndustry);

        // 2. Filter zones by facility type and optionally by industry
        List<DeliveryZone> candidates = new ArrayList<>();
        double totalScore = 0.0;

        for (DeliveryZone zone : deliveryZones) {
            // Skip Tokyo Islands — no road access
            if (zone.getZoneId().equals("MFS62")) continue;

            // Check if zone matches target type (using weights as proxy)
            boolean facilityMatches = false;
            switch (targetType) {
                case INDUSTRIAL: facilityMatches = zone.getFacilityType() == FacilityType.INDUSTRIAL; break;
                case LOGISTICS_HUB: facilityMatches = zone.getWarehousesWeight() > 0.3; break;
                case SMALL_RETAIL: facilityMatches = zone.getRetailWeight() > 0.3; break;
                case CONSTRUCTION_SITE: facilityMatches = zone.getConstructionWeight() > 0.3; break;
                case RESIDENTIAL: facilityMatches = zone.getResidentialWeight() > 0.3; break;
                default: facilityMatches = true;
            }

            // Check industry match (if using industry filter)
            boolean industryMatches = !useIndustryFilter || zone.hasIndustryPresence(targetIndustry);

            if (facilityMatches && industryMatches) {
                candidates.add(zone);
                // Weight candidates by attractiveness AND industry presence
                double baseScore = zone.calculateAttractiveness(config.getTimePeriod(currentTime),
                    config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                    config.getAttractivenessBeta3(), config.getAttractivenessBeta4());

                // Intra-zone boost removed - O-D matrix already encodes 20.7% intra-zone
                // Previously 5.3x (over-boosted to 45%), then 2.0x (still 43%)
                // The O-D matrix diagonal handles intra-zone naturally

                // Boost score by industry presence weight
                if (useIndustryFilter) {
                    double industryWeight = zone.getIndustryPresenceWeight(targetIndustry);
                    baseScore *= (1.0 + industryWeight); // Boost by industry presence
                }
                totalScore += baseScore;
            }
        }

        // If no candidates found with industry filter, try without it
        if (candidates.isEmpty() && useIndustryFilter) {
            useIndustryFilter = false;
            for (DeliveryZone zone : deliveryZones) {
                boolean facilityMatches = false;
                switch (targetType) {
                    case INDUSTRIAL: facilityMatches = zone.getFacilityType() == FacilityType.INDUSTRIAL; break;
                    case LOGISTICS_HUB: facilityMatches = zone.getWarehousesWeight() > 0.3; break;
                    case SMALL_RETAIL: facilityMatches = zone.getRetailWeight() > 0.3; break;
                    case CONSTRUCTION_SITE: facilityMatches = zone.getConstructionWeight() > 0.3; break;
                    case RESIDENTIAL: facilityMatches = zone.getResidentialWeight() > 0.3; break;
                    default: facilityMatches = true;
                }
                if (facilityMatches) {
                    candidates.add(zone);
                    totalScore += zone.calculateAttractiveness(config.getTimePeriod(currentTime),
                        config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                        config.getAttractivenessBeta3(), config.getAttractivenessBeta4());
                }
            }
        }

        // If still no candidates found (rare), fall back to all zones
        if (candidates.isEmpty()) {
            return selectSmartDestination(truck, currentTime);
        }

        // 3. Select zone
        double rand = random.nextDouble() * totalScore;
        double cumulative = 0.0;
        DeliveryZone selectedZone = candidates.get(0);

        for (DeliveryZone zone : candidates) {
            double score = zone.calculateAttractiveness(config.getTimePeriod(currentTime),
                    config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                    config.getAttractivenessBeta3(), config.getAttractivenessBeta4());
            if (useIndustryFilter) {
                double industryWeight = zone.getIndustryPresenceWeight(targetIndustry);
                score *= (1.0 + industryWeight);
            }
            cumulative += score;
            if (rand <= cumulative) {
                selectedZone = zone;
                break;
            }
        }

        return generatePointInZone(selectedZone);
    }

    /**
     * Check if coordinates are on land (driveable).
     * Uses shapefile polygon validation with INVERTED logic from old water exclusion.
     *
     * CRITICAL LOGIC INVERSION:
     * - OLD: isOnLand() returned TRUE if point was INSIDE water exclusion zones (to reject)
     * - NEW: isOnLand() returns TRUE if point is INSIDE land boundary polygons (to accept)
     *
     * Pattern reused from dcity.aggr.BoundaryVolume.searchBoundary():
     * - Query spatial index with small envelope (±0.01° ~ 1km)
     * - Test point against candidate polygons using geometry.covers()
     *
     * @param lon Longitude (EPSG:4326 WGS84)
     * @param lat Latitude (EPSG:4326 WGS84)
     * @return true if on land (valid), false if in water/ocean (invalid)
     */
    private boolean isOnLand(double lon, double lat) {
        if (japanBoundaries == null) {
            System.err.println("[BOUNDARY] Warning: Boundaries not loaded - assuming on land");
            return true;  // Fail-safe if boundaries failed to load
        }

        // Create cache key - round to 3 decimal places (~100m resolution)
        String cacheKey = String.format("%.3f,%.3f", lon, lat);

        // Check cache first
        Boolean cachedResult = landValidationCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        // Query spatial index with small envelope (±0.01 degrees ~ 1km search radius)
        double radius = 0.01;
        List<Geometry> candidates = japanBoundaries.query(
            lon - radius, lat - radius,
            lon + radius, lat + radius
        );

        // Test point against candidate polygons
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        boolean result = false;
        for (Geometry geometry : candidates) {
            if (geometry.covers(point)) {
                // Point is inside this land boundary polygon
                result = true;  // ON LAND - valid!
                break;
            }
        }

        // Cache the result
        landValidationCache.put(cacheKey, result);

        return result;
    }

    private double[] generatePointInZone(DeliveryZone zone) {
        // Skip Tokyo Islands entirely — trucks cannot drive there
        if (zone.getZoneId().equals("MFS62")) {
            return new double[]{zone.getCenterLongitude(), zone.getCenterLatitude()};
        }

        // NETWORK-AWARE POLYGON-BASED GENERATION (Phase 3)
        // If zone has polygon boundaries AND network index is available,
        // use network-aware generation to prefer points near roads
        if (zone.hasPolygonBoundary()) {
            if (networkIndex != null && networkAwareGenerator != null) {
                return networkAwareGenerator.generateNetworkAccessiblePoint(
                    zone, networkIndex, random
                );
            }

            // Fallback: pure polygon generation (existing behavior - Phase 2)
            return zone.generateRandomPoint(random);  // Guaranteed on land!
        }

        // Fallback for zones without polygon boundaries (should be rare/none)
        // Use zone center which is now verified to be on land
        return new double[]{zone.getCenterLongitude(), zone.getCenterLatitude()};
    }

    /**
     * Select nearby destination for DELIVERY trucks (max distance constraint).
     * Used when the initially selected destination is too far (>25km).
     *
     * @param truck The truck agent
     * @param origin Origin coordinates [lon, lat]
     * @param maxDistanceKm Maximum allowed distance
     * @return Destination coordinates within the distance constraint
     */
    private double[] selectNearbyDestination(TruckAgent truck, double[] origin, double maxDistanceKm) {
        // Find zones within max distance
        List<DeliveryZone> nearbyZones = new ArrayList<>();
        for (DeliveryZone zone : deliveryZones) {
            double dist = calculateDistance(origin[0], origin[1],
                zone.getCenterLongitude(), zone.getCenterLatitude());
            if (dist <= maxDistanceKm) {
                nearbyZones.add(zone);
            }
        }

        // If no zones within range, use nearest zone
        if (nearbyZones.isEmpty()) {
            double minDist = Double.MAX_VALUE;
            DeliveryZone nearestZone = deliveryZones.get(0);
            for (DeliveryZone zone : deliveryZones) {
                double dist = calculateDistance(origin[0], origin[1],
                    zone.getCenterLongitude(), zone.getCenterLatitude());
                if (dist < minDist) {
                    minDist = dist;
                    nearestZone = zone;
                }
            }
            return generatePointInZone(nearestZone);
        }

        // Select randomly from nearby zones with attractiveness weighting
        double totalScore = 0.0;
        for (DeliveryZone zone : nearbyZones) {
            totalScore += zone.calculateAttractiveness(0,
                config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(), config.getAttractivenessBeta4());
        }

        double rand = random.nextDouble() * totalScore;
        double cumulative = 0.0;
        DeliveryZone selectedZone = nearbyZones.get(0);

        for (DeliveryZone zone : nearbyZones) {
            cumulative += zone.calculateAttractiveness(0,
                config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(), config.getAttractivenessBeta4());
            if (rand <= cumulative) {
                selectedZone = zone;
                break;
            }
        }

        return generatePointInZone(selectedZone);
    }

    /**
     * Select INTER zone destination for LONG_HAUL trucks.
     * Directly targets the farthest regions: MFS67-71 (Hokkaido, Tohoku, Chubu, etc.)
     * Target average distance: 285 km
     *
     * @param truck The truck agent
     * @param origin Origin coordinates [lon, lat]
     * @return Destination coordinates in far regions
     */
    /**
     * Select INTER-metro destination for LONG_HAUL trucks.
     * Enforces minimum 100km distance (LONG_HAUL definition).
     */
    private double[] selectInterZoneDestination(TruckAgent truck, double[] origin) {
        // Tokyo center for calculating "far from Tokyo" zones
        final double TOKYO_CENTER_LON = 139.6917;
        final double TOKYO_CENTER_LAT = 35.6895;
        final double MIN_DISTANCE_KM = 100.0; // Enforce 100km min for LONG_HAUL category

        // Find the FARTHEST zones from Tokyo (MFS67-71 type)
        // Sort all zones by distance from Tokyo and select top N
        List<DeliveryZone> allZonesByDist = new ArrayList<>(deliveryZones);
        allZonesByDist.sort((a, b) -> {
            double distA = calculateDistance(TOKYO_CENTER_LON, TOKYO_CENTER_LAT,
                a.getCenterLongitude(), a.getCenterLatitude());
            double distB = calculateDistance(TOKYO_CENTER_LON, TOKYO_CENTER_LAT,
                b.getCenterLongitude(), b.getCenterLatitude());
            return Double.compare(distB, distA);  // Descending order
        });

        // Take the farthest 10 zones (or all if fewer than 10)
        int numFarZones = Math.min(10, allZonesByDist.size());
        List<DeliveryZone> farZones = allZonesByDist.subList(0, numFarZones);

        if (farZones.isEmpty()) {
            // Fallback: random zone
            DeliveryZone fallback = deliveryZones.get(random.nextInt(deliveryZones.size()));
            return generatePointInZone(fallback);
        }

        // Filter to only zones beyond minimum distance from origin
        // Use distance-weighted selection: peak at ~250km, gentle decay beyond
        List<DeliveryZone> validFarZones = new ArrayList<>();
        List<Double> zoneWeights = new ArrayList<>();
        for (DeliveryZone zone : farZones) {
            double distFromOrigin = calculateDistance(origin[0], origin[1],
                zone.getCenterLongitude(), zone.getCenterLatitude());
            if (distFromOrigin >= MIN_DISTANCE_KM) {
                validFarZones.add(zone);
                // Distance decay: exp(-dist/220) favors moderate long-haul zones
                // At 200km: weight=0.40, 300km: 0.25, 500km: 0.10, 800km: 0.03
                double weight = Math.exp(-distFromOrigin / LONGHAUL_DISTANCE_DECAY_FACTOR);
                zoneWeights.add(weight);
            }
        }

        // If no zones beyond min distance, force selection of truly distant zones (MFS67-71)
        if (validFarZones.isEmpty()) {
            for (DeliveryZone zone : farZones) {
                String zoneId = zone.getZoneId();
                // MFS67-71 are the long-haul inter-regional zones (200-800km)
                if (zoneId.startsWith("MFS67") || zoneId.startsWith("MFS68") ||
                    zoneId.startsWith("MFS69") || zoneId.startsWith("MFS70") ||
                    zoneId.startsWith("MFS71")) {
                    validFarZones.add(zone);
                    zoneWeights.add(1.0);
                }
            }
        }

        // Still empty? Use original far zones
        if (validFarZones.isEmpty()) {
            validFarZones = new ArrayList<>(farZones);
            for (int i = 0; i < validFarZones.size(); i++) {
                zoneWeights.add(1.0);
            }
        }

        // Select from valid far zones with distance-based weighting
        double totalWeight = 0.0;
        for (double w : zoneWeights) totalWeight += w;

        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        DeliveryZone selectedZone = validFarZones.get(0);

        for (int i = 0; i < validFarZones.size(); i++) {
            cumulative += zoneWeights.get(i);
            if (rand <= cumulative) {
                selectedZone = validFarZones.get(i);
                break;
            }
        }

        // Generate point on the FAR SIDE of the zone (away from origin) for longer distance
        return generatePointInZoneFarSide(selectedZone, origin);
    }

    /**
     * Generate a point in the far side of a zone (away from the origin).
     * This ensures LONG_HAUL trips achieve maximum distance.
     * For zones with large radii (e.g., MFS67 with 150km radius), this is crucial.
     */
    private double[] generatePointInZoneFarSide(DeliveryZone zone, double[] origin) {
        double centerLon = zone.getCenterLongitude();
        double centerLat = zone.getCenterLatitude();
        double radiusKm = zone.getRadiusKm();

        // Calculate direction from origin to zone center
        double dLon = centerLon - origin[0];
        double dLat = centerLat - origin[1];
        double magnitude = Math.sqrt(dLon * dLon + dLat * dLat);

        if (magnitude < 0.001) {
            // Origin is at zone center, pick random direction
            return generatePointInZone(zone);
        }

        // Unit vector pointing away from origin
        double unitLon = dLon / magnitude;
        double unitLat = dLat / magnitude;

        // Convert km to degrees (approximate)
        double degreesPerKmLon = 1.0 / (111.0 * Math.cos(Math.toRadians(centerLat)));
        double degreesPerKmLat = 1.0 / 111.0;

        // Retry with water exclusion
        int maxAttempts = 20;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generate point 10-40% of radius away from center, in the direction away from origin
            double distanceFraction = 0.1 + random.nextDouble() * 0.3;
            double offsetKm = radiusKm * distanceFraction;

            double pointLon = centerLon + unitLon * offsetKm * degreesPerKmLon;
            double pointLat = centerLat + unitLat * offsetKm * degreesPerKmLat;

            if (isOnLand(pointLon, pointLat)) {
                return new double[]{pointLon, pointLat};
            }
        }
        // Fallback: zone center
        return new double[]{centerLon, centerLat};
    }

    /**
     * Select destination with G-A balance constraints.
     * Weights candidates by O-D probability AND remaining attraction capacity.
     *
     * @param truck The truck agent making the trip
     * @param origin Origin coordinates [lon, lat]
     * @param originZoneId Origin zone ID
     * @param currentTime Current simulation time
     * @param commodityType Pre-selected commodity type for POI-aware routing (may be null)
     * @return Destination coordinates [lon, lat]
     */
    private double[] selectBalancedDestination(TruckAgent truck, double[] origin,
                                              String originZoneId, long currentTime,
                                              String commodityType) {
        // Get O-D probabilities from matrix
        DeliveryZone originZone = findZoneByZoneId(originZoneId);
        if (originZone == null) {
            return selectSmartDestination(truck, currentTime); // fallback
        }

        int originIndex = deliveryZones.indexOf(originZone);

        // Build candidate map: zoneId -> O-D probability
        // Apply distance decay for DELIVERY and intra-zone damping for all types
        Map<String, Double> candidates = new HashMap<>();
        TruckType truckType = truck.getTruckType();

        for (int destIndex = 0; destIndex < deliveryZones.size(); destIndex++) {
            DeliveryZone destZone = deliveryZones.get(destIndex);

            // Skip Tokyo Islands — no road access
            if (destZone.getZoneId().equals("MFS62")) continue;

            double odProb = odMatrix.getFlow(originIndex, destIndex);

            if (odProb > 0.0) {
                boolean isIntraZone = destZone.getZoneId().equals(originZoneId);

                double dist = calculateDistance(origin[0], origin[1],
                    destZone.getCenterLongitude(), destZone.getCenterLatitude());

                if (truckType == TruckType.DELIVERY) {
                    // DELIVERY: Strong distance decay — in-town only (target: 11.12km)
                    // Threshold and decay scaled for manhattan factor 2.25
                    if (dist > 34.0 && !isIntraZone) continue;
                    double distanceWeight = Math.exp(-dist / DELIVERY_DISTANCE_DECAY_FACTOR);
                    odProb *= distanceWeight;
                } else if (truckType == TruckType.MIXED_OPERATION) {
                    // MIXED_OPERATION: Gentle distance decay (target: 55.04km)
                    // Decay scaled for manhattan factor 2.25
                    double distanceWeight = Math.exp(-dist / MIXED_DISTANCE_DECAY_FACTOR);
                    odProb *= distanceWeight;
                }

                // Intra-zone damping for all types to match File 08's 20.67% ratio
                if (isIntraZone) {
                    odProb *= INTRAZONE_DAMPING_FACTOR;
                }

                if (odProb > 1e-10) {
                    candidates.put(destZone.getZoneId(), odProb);
                }
            }
        }

        // Select balanced destination
        String selectedZoneId = gaBalancer.selectBalancedDestination(originZoneId, candidates);

        if (selectedZoneId == null) {
            lastSelectedDestZoneId = null;
            return selectSmartDestination(truck, currentTime); // fallback
        }

        // Store selected zone ID for use in trip zone assignment
        lastSelectedDestZoneId = selectedZoneId;

        // Generate point in selected zone
        DeliveryZone selectedZone = findZoneByZoneId(selectedZoneId);
        if (selectedZone == null) {
            return selectSmartDestination(truck, currentTime); // fallback
        }

        // POI-based destination selection (commodity-aware routing)
        if (poiManager != null && poiManager.hasPOIs()) {
            int timePeriod = getTimePeriod(currentTime);
            PointOfInterest targetPOI = poiManager.selectPOIForTrip(
                truck, selectedZoneId, commodityType, timePeriod);

            if (targetPOI != null && isOnLand(targetPOI.getLongitude(), targetPOI.getLatitude())) {
                lastSelectedDestPOIId = targetPOI.getPoiId();
                return new double[]{targetPOI.getLongitude(), targetPOI.getLatitude()};
            }
        }

        // Fallback: random point in zone (with water exclusion)
        lastSelectedDestPOIId = null;
        return generatePointInZone(selectedZone);
    }

    /**
     * Find zone by zone ID.
     *
     * @param zoneId Zone identifier
     * @return DeliveryZone object, or null if not found
     */
    private DeliveryZone findZoneByZoneId(String zoneId) {
        for (DeliveryZone zone : deliveryZones) {
            if (zone.getZoneId().equals(zoneId)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Find which zone a location belongs to (returns zone object).
     */
    private DeliveryZone findZone(double lon, double lat) {
        for (DeliveryZone zone : deliveryZones) {
            if (zone.containsPoint(lon, lat)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Find which zone a location belongs to (returns zone ID string).
     * Uses nearest neighbor if point is not within any zone radius.
     */
    private String findZoneForLocation(double lon, double lat) {
        DeliveryZone zone = findZone(lon, lat);
        if (zone != null) {
            return zone.getZoneId();
        }
        
        // Fallback: Find nearest zone to ensure we never return "OUTSIDE"
        int nearestIndex = findNearestZoneIndex(lon, lat);
        if (nearestIndex >= 0 && nearestIndex < deliveryZones.size()) {
            return deliveryZones.get(nearestIndex).getZoneId();
        }
        
        return "OUTSIDE"; // Only if no zones exist
    }

    /**
     * Get time period for time-dependent POI selection.
     * Phase 4: Model Refinement
     *
     * @param currentTime Current simulation time (milliseconds)
     * @return Time period (0-23 for hours)
     */
    private int getTimePeriod(long currentTime) {
        return config.getTimePeriod(currentTime);
    }

    /**
     * Find the index of the nearest zone to a location (for O-D matrix lookup).
     */
    private int findNearestZoneIndex(double lon, double lat) {
        if (deliveryZones.isEmpty()) {
            return -1;
        }

        int nearestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < deliveryZones.size(); i++) {
            DeliveryZone zone = deliveryZones.get(i);
            double distance = calculateDistance(
                lon, lat,
                zone.getCenterLongitude(), zone.getCenterLatitude()
            );

            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        final double EARTH_RADIUS_KM = config.getGeographyEarthRadiusKm();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c * config.getDistanceManhattanFactor();
    }

    /**
     * Print essential statistics summary
     */
    private void printStatistics() {
        // Separate delivery and empty trips
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

        // Fleet mix statistics
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

    /**
     * Main simulation execution
     */
    public void run(String[] args) {
        run(args, true);
    }

    /**
     * Main simulation execution with optional config loading
     */
    public void run(String[] args, boolean loadConfig) {
        System.out.println("=================================================================");
        System.out.println("  TOKYO TRUCK ABM V1.0 - PSEUDO PFLOW COMPATIBLE");
        System.out.println("  Based on Tokyo Metropolitan Freight Survey (H25/2013)");
        System.out.println("=================================================================");

        // Load configuration (unless already loaded)
        if (loadConfig) {
            String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;
            loadConfiguration(configFile);
        }

        // Initialize delivery zones (must be after config and before trucks)
        initializeDeliveryZones();

        // Run simulation phases
        initializeTrucks();
        generateTrips();
        printStatistics();

        // Initialize metrics tracker with truck fleet
        metricsTracker.initializeTrucks(truckFleet);

        // Finalize and print comprehensive metrics
        System.out.println("\n[CHECKPOINT] Finalizing metrics...");
        metricsTracker.calculateFinalMetrics();
        metricsTracker.printReport();

        // NEW: G-A Balance - Print balance summary
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

        // Phase 5: Comprehensive MFS Validation with Metrics
        System.out.println("\n[CHECKPOINT] Running comprehensive MFS validation...");
        ValidationEngine.ValidationReport validationReport = runMFSValidation();

        // Export results
        System.out.println("\n[CHECKPOINT] Exporting results...");
        TruckDataExporter exporter = new TruckDataExporter("data/output/truck");
        exporter.setDeliveryZones(deliveryZones);
        exporter.exportAll(truckFleet, allTrips);

        // Export comprehensive metrics
        try {
            metricsTracker.exportToCSV(exporter.getRunDirectory());
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to export metrics: " + e.getMessage());
        }

        System.out.println("\n[COMPLETE] Output: " + exporter.getRunDirectory());
        System.out.println("[VALIDATION] Overall grade: " + validationReport.grade +
            " (" + validationReport.passedTests + "/" + validationReport.totalTests + " tests passed)");
    }

    /**
     * Run comprehensive MFS validation with metrics calculation.
     */
    private ValidationEngine.ValidationReport runMFSValidation() {
        System.out.println("\n" + repeatString("=", 80));
        System.out.println("RUNNING MFS BASELINE VALIDATION");
        System.out.println(repeatString("=", 80));

        // Calculate simulation metrics
        SimulationMetrics metrics = new SimulationMetrics(truckFleet, allTrips);
        metrics.calculateAllMetrics();
        metrics.printSummary();

        // Validate against MFS baseline (CSV-driven)
        String baselinePath = "config/truck/validation/mfs_baseline.csv";
        ValidationEngine csvValidator = new ValidationEngine(baselinePath);

        ValidationEngine.ValidationReport report;
        if (csvValidator.hasBaseline()) {
            // CSV-driven validation (primary)
            report = csvValidator.validateFromBaseline(truckFleet, allTrips, gaBalancer);
        } else {
            // Fallback to hardcoded validation
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

    /**
     * Select inter-metropolitan route weighted by flow multiplier.
     */
    private MetropolitanConfig.InterMetroRoute selectInterMetroRoute() {
        List<MetropolitanConfig.InterMetroRoute> routes = metroConfig.getActiveRoutes();
        if (routes == null || routes.isEmpty()) return null;

        // Weight by flow multiplier
        double totalWeight = 0.0;
        for (MetropolitanConfig.InterMetroRoute route : routes) {
            totalWeight += route.flowVolumeMultiplier;
        }

        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (MetropolitanConfig.InterMetroRoute route : routes) {
            cumulative += route.flowVolumeMultiplier;
            if (rand <= cumulative) return route;
        }

        return routes.get(0);
    }

    /**
     * Generate random location within metropolitan bounds.
     */
    private double[] generateLocationInMetro(MetropolitanConfig.Metropolitan metro) {
        double lon = metro.minLon + random.nextDouble() * (metro.maxLon - metro.minLon);
        double lat = metro.minLat + random.nextDouble() * (metro.maxLat - metro.minLat);
        return new double[]{lon, lat};
    }

    /**
     * Run dual simulation mode (intra + inter metropolitan).
     *
     * FIXED: Now runs a SINGLE simulation with combined zones from both
     * intra and inter metropolitan zone files. This prevents fleet doubling.
     *
     * Before: Two separate simulations, each with full fleet → 654,216 trucks
     * After: One simulation with combined zones → 327,108 trucks
     */
    private static void runDualSimulation(String[] args) {
        System.out.println("\n" + repeatString("=", 80));
        System.out.println("DUAL SIMULATION MODE: COMBINED INTRA + INTER ZONES");
        System.out.println(repeatString("=", 80));

        TruckConfig config = TruckConfig.getInstance();
        String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;

        // Get zone file paths
        String intraZonesFile = config.getProperty("zones.file.intra", "zones_intra_metro_combined.csv");
        String interZonesFile = config.getProperty("zones.file.inter", "zones_inter_metro.csv");

        System.out.println("[DUAL] Loading zones from both files:");
        System.out.println("  INTRA: " + intraZonesFile);
        System.out.println("  INTER: " + interZonesFile);

        // Create combined zones file path for the simulation
        // We'll load both zone files in initializeDeliveryZones by using a special marker
        config.setProperty("zones.file.dual.intra", intraZonesFile);
        config.setProperty("zones.file.dual.inter", interZonesFile);
        config.setProperty("zones.file", "DUAL_MODE");  // Special marker

        // Run SINGLE simulation with combined zones
        System.out.println("\n[DUAL] Running unified simulation with combined zones...");
        TruckSimulation sim = new TruckSimulation();
        sim.loadConfiguration(configFile);

        // Override zones loading to use both files
        sim.initializeDeliveryZonesDual(intraZonesFile, interZonesFile);

        // Run simulation phases (skip zone loading since we did it above)
        sim.initializeTrucks();
        sim.generateTrips();
        sim.printStatistics();

        // Initialize metrics tracker with truck fleet
        sim.metricsTracker.initializeTrucks(sim.truckFleet);
        sim.metricsTracker.calculateFinalMetrics();
        sim.metricsTracker.printReport();

        // G-A Balance summary
        if (sim.gaBalancer != null) {
            sim.gaBalancer.printSummary();
        }

        // Run MFS validation
        System.out.println("\n" + repeatString("=", 80));
        System.out.println("DUAL MODE VALIDATION - COMBINED ZONES");
        System.out.println(repeatString("=", 80));

        SimulationMetrics metrics = new SimulationMetrics(sim.truckFleet, sim.allTrips);
        metrics.calculateAllMetrics();
        metrics.printSummary();

        // Validate against MFS baseline (CSV-driven)
        String baselinePath = "config/truck/validation/mfs_baseline.csv";
        ValidationEngine csvValidator = new ValidationEngine(baselinePath);

        ValidationEngine.ValidationReport report;
        if (csvValidator.hasBaseline()) {
            // CSV-driven validation (primary)
            report = csvValidator.validateFromBaseline(sim.truckFleet, sim.allTrips, sim.gaBalancer);
        } else {
            // Fallback to hardcoded validation
            System.out.println("[Validation] CSV baseline not found, using hardcoded targets.");
            report = ValidationEngine.validateHardcoded(sim.truckFleet, sim.allTrips, sim.gaBalancer);
        }
        report.printReport();

        // Export results
        System.out.println("\n[CHECKPOINT] Exporting results...");
        TruckDataExporter exporter = new TruckDataExporter("data/output/truck");
        exporter.setDeliveryZones(sim.deliveryZones);
        exporter.exportAll(sim.truckFleet, sim.allTrips);

        try {
            sim.metricsTracker.exportToCSV(exporter.getRunDirectory());
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to export metrics: " + e.getMessage());
        }

        System.out.println("\n" + repeatString("=", 80));
        System.out.println("DUAL SIMULATION COMPLETE");
        System.out.println("[VALIDATION] Overall grade: " + report.grade +
            " (" + report.passedTests + "/" + report.totalTests + " tests passed)");
        System.out.println("  Total trucks: " + sim.truckFleet.size() + " (target: 327,108)");
        System.out.println("  Total zones: " + sim.deliveryZones.size() + " (intra + inter combined)");
        System.out.println(repeatString("=", 80));
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        TruckConfig config = TruckConfig.getInstance();

        // Load config to get simulation mode
        String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;
        try {
            config.loadFromFile(configFile);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to load config: " + e.getMessage());
            System.exit(1);
        }

        // Check simulation mode
        TruckConfig.SimulationMode mode = config.getSimulationMode();

        switch (mode) {
            case DUAL:
                System.out.println("[Config] Simulation mode: DUAL (intra + inter metropolitan)");
                runDualSimulation(args);
                break;

            case INTRA_METROPOLITAN:
                System.out.println("[Config] Simulation mode: INTRA_METROPOLITAN only");
                String intraZonesFile = config.getProperty("zones.file.intra", "zones_tokyo_metro.csv");
                TruckSimulation intraSim = new TruckSimulation();
                intraSim.loadConfiguration(configFile);
                intraSim.config.setZonesFile(intraZonesFile);
                intraSim.run(args, false);
                break;

            case INTER_METROPOLITAN:
                System.out.println("[Config] Simulation mode: INTER_METROPOLITAN only");
                String interZonesFile = config.getProperty("zones.file.inter", "zones_inter_metro.csv");
                TruckSimulation interSim = new TruckSimulation();
                interSim.loadConfiguration(configFile);
                interSim.config.setZonesFile(interZonesFile);
                interSim.run(args, false);
                break;

            default:
                System.err.println("[Error] Unknown simulation mode: " + mode);
                System.exit(1);
        }
    }
}
