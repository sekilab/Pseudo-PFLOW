package taxi.sim;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import util.PathResolver;

/**
 * Central configuration class for Tokyo Taxi ABM Simulation
 *
 * This class loads and holds ALL configurable parameters from taxi_config.properties
 * All other classes (TaxiSimulation, TaxiTrip, TaxiAgent, TaxiDataExporter) access
 * configuration through this singleton class.
 *
 * Benefits:
 * - Single source of truth for all configuration
 * - No hardcoded values anywhere in the codebase
 * - Easy to extend with new parameters
 * - Thread-safe singleton pattern
 *
 * Usage:
 *   TaxiConfig config = TaxiConfig.getInstance();
 *   config.loadFromFile("config/taxi_config.properties");
 *   double baseFare = config.getTaxiFareBase();
 */
public class TaxiConfig {

    private static TaxiConfig instance;

    // ═══ Directories ═══
    /** Input data directory path. Resolved via PathResolver. */
    private String inputDirectory = "./data/input/";
    /** Output directory for simulation results. Resolved via PathResolver. */
    private String outputDirectory = "./data/output/";

    // ═══ Fleet Configuration ═══
    /** Total registered taxis in the city. Source: city taxi association statistics. */
    private int taxiTotalRegistered = 50000;
    /** Fraction of registered taxis operating at any given time. */
    private double taxiOperatingRate = 0.80;
    /**
     * Active fleet size for simulation. If explicitly set in config, uses that value;
     * otherwise calculated as taxiTotalRegistered * taxiOperatingRate.
     */
    private int taxiFleetSize = 1000;

    // ═══ Trip Configuration ═══
    /** Average number of trips per taxi per shift. City-specific calibration. */
    private double taxiTripsAverage = 26.8;
    /** Minimum trips per taxi per shift. */
    private int taxiTripsMin = 20;
    /** Maximum trips per taxi per shift (hard cap). */
    private int taxiTripsMax = 35;
    /** Standard deviation for Gaussian trip count variation. */
    private double taxiTripsStddev = 3.0;

    // ═══ Distance Parameters ═══
    /** Average trip distance in km. */
    private double tripDistanceAverage = 5.0;
    /** Standard deviation of trip distance in km. */
    private double tripDistanceStddev = 2.0;
    /** Minimum trip distance in km (shorter trips rejected). */
    private double tripDistanceMin = 0.5;
    /** Maximum trip distance in km (longer trips capped). */
    private double tripDistanceMax = 15.0;

    /**
     * Spatial-validation retry budget per trip-pair generation attempt.
     * Default 200 (raised from previously hardcoded 50 in TaxiSimulation v6.0).
     * Higher values reduce premature loop exit in dense zones with strong
     * water/river constraints (Sumida River, Tokyo Bay coast, Tama River) at
     * small runtime cost. Configured via {@code taxi.trip.generation.max.attempts}.
     */
    private int tripGenerationMaxAttempts = 200;

    // ═══ Phase 3 (B1): v7.0 log-normal distance distribution ═══
    // DESIGN.md §2.6 — used by shift_time engine only. Legacy engine ignores these.
    //
    // Loaded leg: lognormal(mu = ln(4.6) - sigma^2/2, sigma = 0.6), bounded [0.5, 15] km
    //   The -sigma^2/2 correction makes the arithmetic mean = trip.distance.average.
    // Empty leg:  lognormal(mu = ln(5.1) - sigma^2/2, sigma = 0.7), bounded [0.3, 12] km
    //   Empty mean is loaded by Phase 3 but only used in Phase 4+ (DUAL_MODE).
    /** Sigma of log-normal loaded-leg distance (Phase 3 / shift_time engine). */
    private double tripDistanceLoadedSigma = 0.6;
    /** Mean of log-normal empty-leg distance (Phase 4+; loaded but unused in Phase 3). */
    private double tripDistanceEmptyMeanKm = 5.1;
    /** Sigma of log-normal empty-leg distance (Phase 4+; loaded but unused in Phase 3). */
    private double tripDistanceEmptySigma = 0.7;
    /**
     * Nearest-valid-projection radius for the spiral-grid scan when a sampled
     * dropoff position fails spatial validation. Phase 3 / shift_time engine.
     * Default 0.5 km (500 m) per DESIGN.md §2.6.
     */
    private double projectionRadiusKm = 0.5;

    // ═══ Nearby Trip Logic ═══
    /** Whether nearby-trip destination bias is enabled. */
    private boolean useNearbyTrips = true;
    /** Probability [0.0, 1.0] of selecting a nearby destination over a random one. */
    private double nearbyTripProbability = 0.70;
    /** Radius in km defining "nearby" for trip destination selection. */
    private double nearbyTripRadiusKm = 3.0;
    /**
     * Minimum reposition distance (km) before an empty trip is emitted between
     * two passenger trips. Default 0.05 km (50 m) matches pre-2026 behavior but
     * is implausibly small compared with Tokyo field data (~500 m typical
     * cruising). Consider raising if empty-trip fractions look inflated.
     */
    private double emptyTripThresholdKm = 0.05;

    // ═══ Simulation Engine (v6.1, Phase 0 of shift-time rewrite) ═══
    /**
     * Trip-generation engine selector.
     * "legacy"     = trip-count-driven loop with rejection-resampling (v6.0)
     * "shift_time" = shift-time-driven loop with DUAL_MODE (v7.0, Phase 1-7 build)
     * During Phase 1-7 of DESIGN.md, both engines coexist via this flag.
     */
    private String simulationEngine = "legacy";

    // ═══ Shift Configuration ═══
    /** Minimum shift duration in hours. */
    private int shiftDurationMin = 12;
    /** Maximum shift duration in hours. */
    private int shiftDurationMax = 16;
    /** Average shift duration in hours. Gaussian center. */
    private double shiftDurationAverage = 14;
    /** Standard deviation of shift duration in hours. */
    private double shiftDurationStddev = 1.0;

    /** First shift cohort start time (seconds since midnight). */
    private long shiftStart1Time = 6 * 3600;
    /** Probability of a taxi belonging to the first shift cohort. */
    private double shiftStart1Prob = 0.40;
    /** Second shift cohort start time (seconds since midnight). */
    private long shiftStart2Time = 8 * 3600;
    /** Probability of a taxi belonging to the second shift cohort. */
    private double shiftStart2Prob = 0.30;
    /** Third shift cohort start time (seconds since midnight). */
    private long shiftStart3Time = 14 * 3600;
    /** Probability of a taxi belonging to the third shift cohort. */
    private double shiftStart3Prob = 0.30;

    // ═══ Routing Parameters ═══
    /** Average taxi travel speed in km/h. Used for travel time estimation. */
    private double taxiAverageSpeed = 20.0;
    /** Passenger pickup time in seconds. Added to each trip duration. */
    private int taxiPickupTime = 300;
    /** Break time between trips in seconds. */
    private int taxiBreakTime = 600;
    /** Manhattan factor: road distance = straight-line distance * this factor. */
    private double manhattanFactor = 1.4;

    // ═══ City & Geographical Parameters ═══
    /** City name for display and output labeling. */
    private String cityName = "Tokyo";
    /** Directory containing per-city config files (zones.csv, etc.). */
    private String configDir = "config/taxi/";
    /** Minimum longitude of city bounding box. */
    private double boundsMinLon = 139.6;
    /** Maximum longitude of city bounding box. */
    private double boundsMaxLon = 139.9;
    /** Minimum latitude of city bounding box. */
    private double boundsMinLat = 35.5;
    /** Maximum latitude of city bounding box. */
    private double boundsMaxLat = 35.8;
    /** Earth radius in km for Haversine distance calculations. */
    private double earthRadiusKm = 6371.0;

    // ═══ Hotspot Configuration ═══
    /** Whether demand hotspot zones (stations, airports) are enabled. */
    private boolean useHotspots = true;
    /** Multiplier for airport zone demand during night hours. */
    private double hotspotAirportNightMultiplier = 2.0;

    // ═══ Fare Parameters ═══
    /** Base fare in yen (flag drop). Source: THTA / city taxi association. */
    private double taxiFareBase = 730.0;
    /** Distance in km covered by base fare. */
    private double taxiFareBaseDistance = 2.0;
    /** Per-km fare in yen beyond base distance. */
    private double taxiFarePerKm = 320.0;
    /** Waiting charge per hour in yen. */
    private double taxiFareWaitingPerHour = 3085.0;
    /** Night surcharge multiplier (e.g. 1.20 = +20%). Applied during nightHours. */
    private double taxiFareNightSurcharge = 1.20;

    // ═══ Night Hours ═══
    /** Night period start hour (24h format). Used for fare surcharge and hotspot weighting. */
    private int nightHoursStart = 22;
    /** Night period end hour (24h format). */
    private int nightHoursEnd = 5;
    /** Night period start in seconds since midnight. */
    private long nightHoursStartSeconds = 22 * 3600;
    /** Night period end in seconds since midnight. */
    private long nightHoursEndSeconds = 5 * 3600;

    // ═══ Time/Date Formats ═══
    /** Date-time format for CSV export timestamps. */
    private String exportDatetimeFormat = "MM/dd/yyyy HH:mm";
    /** Timestamp format for run directory naming. */
    private String exportTimestampFormat = "yyyyMMdd_HHmmss";

    // ═══ System ═══
    /** Random seed for deterministic reproducibility. Negative = non-reproducible. */
    private int randomSeed = 12345;

    // ═══ Taxi Type Distribution ═══
    /** Probability of generating a LOCAL taxi (operates within familiar zone cluster). */
    private double taxiTypeLocalProb = 0.70;
    /** Probability of generating a CITYWIDE taxi (operates across entire city). */
    private double taxiTypeCitywideProb = 0.15;
    /** Probability of generating a HUB taxi (anchored to stations/airports). */
    private double taxiTypeHubProb = 0.15;
    /** Familiar area radius in km for LOCAL taxis (legacy, pre-V4.0 zone clustering). */
    private double localFamiliarRadiusKm = 5.0;

    // ═══ v7.0 Five-type Fleet (Phase 1, used by shift_time engine) ═══
    // DESIGN.md §2.1 — defaults match Tokyo 特別区・武三 FY2024 calibration.
    // Sums to 1.000: 0.500 + 0.150 + 0.200 + 0.095 + 0.005 = 1.000
    /** v7.0: LOCAL share of fleet. Read by initializeTaxis() when isShiftTimeEngine(). */
    private double taxiTypeLocalShare = 0.500;
    /** v7.0: CITYWIDE share of fleet. */
    private double taxiTypeCitywideShare = 0.150;
    /** v7.0: APP_PREFERRED share. Sensitivity range [0.20, 0.60]; default 0.20 per DESIGN.md NEW-1. */
    private double taxiTypeAppPreferredShare = 0.200;
    /** v7.0: HUB share (held fixed in NEW-1 sensitivity reallocation). */
    private double taxiTypeHubShare = 0.095;
    /** v7.0: RIDE_HAIL_PRHS share (held fixed; matches MLIT 1,365 trips/day vs ~625K total ≈0.22%). */
    private double taxiTypeRideHailPrhsShare = 0.005;

    /** v7.0: APP_PREFERRED has no familiar zone (citywide demand-following). 0.0 = unrestricted. */
    private double taxiTypeAppPreferredFamiliarRadiusKm = 0.0;
    /** v7.0: RIDE_HAIL_PRHS has no familiar zone (regulatorily citywide). 0.0 = unrestricted. */
    private double taxiTypeRideHailPrhsFamiliarRadiusKm = 0.0;

    // ═══ v7.0 Per-Type Mode Mix (street / app / stand) ═══
    // Each row sums to 1.0. RIDE_HAIL_PRHS street is regulatorily 0% (Road Transport Act 78-3).
    /** LOCAL street-hail probability. */    private double taxiTypeLocalModeStreet = 0.70;
    /** LOCAL app-dispatch probability. */   private double taxiTypeLocalModeApp = 0.25;
    /** LOCAL stand-queue probability. */    private double taxiTypeLocalModeStand = 0.05;

    /** CITYWIDE street-hail probability. */ private double taxiTypeCitywideModeStreet = 0.60;
    /** CITYWIDE app-dispatch probability. */private double taxiTypeCitywideModeApp = 0.30;
    /** CITYWIDE stand-queue probability. */ private double taxiTypeCitywideModeStand = 0.10;

    /** APP_PREFERRED street-hail probability. */ private double taxiTypeAppPreferredModeStreet = 0.20;
    /** APP_PREFERRED app-dispatch probability. */private double taxiTypeAppPreferredModeApp = 0.75;
    /** APP_PREFERRED stand-queue probability. */ private double taxiTypeAppPreferredModeStand = 0.05;

    /** HUB street-hail probability. */    private double taxiTypeHubModeStreet = 0.10;
    /** HUB app-dispatch probability. */   private double taxiTypeHubModeApp = 0.30;
    /** HUB stand-queue probability. */    private double taxiTypeHubModeStand = 0.60;

    /** RIDE_HAIL_PRHS street-hail probability (regulatorily 0%). */
    private double taxiTypeRideHailPrhsModeStreet = 0.00;
    /** RIDE_HAIL_PRHS app-dispatch probability (100% by regulation). */
    private double taxiTypeRideHailPrhsModeApp = 1.00;
    /** RIDE_HAIL_PRHS stand-queue probability (regulatorily 0%). */
    private double taxiTypeRideHailPrhsModeStand = 0.00;

    // ═══ v7.0 AT_STAND Exponential Wait (DESIGN.md §2.8) ═══
    /** Mean exponential wait at airport stands (Haneda, Narita) in minutes. */
    private double taxiStandWaitAirportMeanMinutes = 8.0;
    /** Mean exponential wait at major-station stands (Tokyo, Shinjuku, etc.) in minutes. */
    private double taxiStandWaitStationMeanMinutes = 12.0;
    /** Mean exponential wait at entertainment stands (Roppongi) in minutes. */
    private double taxiStandWaitEntertainmentMeanMinutes = 6.0;

    // ═══ v7.0 RIDE_HAIL_PRHS Regulatory Windows (DESIGN.md §2.9) ═══
    /** Number of MLIT-permitted PRHS operating windows in current scenario. */
    private int prhsWindowCount = 0;
    /**
     * PRHS windows as raw spec strings, e.g. {@code "07:00-10:00,Mon-Fri"}.
     * Parsed by ShiftSimulator at runtime via {@code PrhsWindow.parse(spec)}.
     * FY2024 default: 4 windows; R8 contraction: 1 window.
     */
    private final java.util.List<String> prhsWindowSpecs = new java.util.ArrayList<>();

    // ═══ Zone Clustering (V4.0) ═══
    /** Minimum number of familiar zones assigned to LOCAL taxis. */
    private int zoneClusterMinZones = 3;
    /** Maximum number of familiar zones assigned to LOCAL taxis. */
    private int zoneClusterMaxZones = 5;
    /** Minimum distance (km) between zones in a cluster. */
    private double zoneClusterMinDistanceKm = 5.0;
    /** Maximum distance (km) between zones in a cluster. */
    private double zoneClusterMaxDistanceKm = 10.0;

    // ═══ Time Periods ═══
    /** Morning period start (seconds since midnight). For attractiveness calculation. */
    private long timePeriod1Start = 5 * 3600;
    /** Morning period end (seconds since midnight). */
    private long timePeriod1End = 10 * 3600;
    /** Daytime period start (seconds since midnight). */
    private long timePeriod2Start = 10 * 3600;
    /** Daytime period end (seconds since midnight). Night = everything else. */
    private long timePeriod2End = 18 * 3600;

    // ═══ Attractiveness Coefficients ═══
    /** Attractiveness weight for employment/job density. */
    private double attractivenessBeta1 = 1.0;
    /** Attractiveness weight for commercial/shop density. */
    private double attractivenessBeta2 = 0.8;
    /** Attractiveness weight for nightlife/entertainment density. */
    private double attractivenessBeta3 = 0.6;
    /** Attractiveness weight for residential density. */
    private double attractivenessBeta4 = 0.5;

    // ═══ Spatial Validation ═══
    /** Whether 3-layer spatial validation (land/water/river) is enabled. */
    private boolean spatialValidationEnabled = true;
    /** Directory containing shared Japan admin shapefiles. */
    private String shapefileDir = "src/shared/gm-jp/";
    /** Buffer distance (km) around rivers for point rejection. */
    private double riverBufferKm = 0.15;
    /** Comma-separated prefecture adm_code prefixes for shapefile filtering. */
    private String prefectureCodes = "";

    // ═══ Transport Network Index ═══
    /** Whether station/airport proximity enrichment is enabled. */
    private boolean transportIndexEnabled = true;
    /** Maximum distance (km) for station proximity boost. */
    private double stationProximityMaxKm = 2.0;
    /** Probability [0.0, 1.0] of generating station-biased origin/destination points. */
    private double stationBiasProb = 0.4;

    // ═══ Spatial Distribution (V5.2) ═══
    /** Point generation mode: "gaussian" (center-weighted) or "uniform" (radial). */
    private String spatialDistributionMode = "gaussian";
    /** Gaussian sigma = zone radius / this factor. Smaller = tighter clustering. */
    private double spatialGaussianSigmaFactor = 2.5;
    /** Maximum sampling distance = zone radius * this factor. Hard clip boundary. */
    private double spatialGaussianClipFactor = 1.2;
    /** East-West stretch factor for zone shapes (>1 = wider ellipse). */
    private double spatialAspectX = 1.0;
    /** North-South stretch factor for zone shapes (>1 = taller ellipse). */
    private double spatialAspectY = 1.0;
    /** Final random perturbation in meters applied after all other sampling. */
    private double spatialJitterMeters = 50.0;

    // ═══ Zone Enrichment from Shapefiles ═══
    /** Whether automatic zone creation from station/settlement shapefiles is enabled. */
    private boolean zoneEnrichmentEnabled = true;
    /** Distance threshold (km): stations within this of an existing zone are skipped. */
    private double stationCoverageKm = 1.5;
    /** Distance threshold (km): settlements within this of an existing zone are skipped. */
    private double settlementCoverageKm = 2.0;

    // ═══ Zone Configuration ═══
    /** CSV filename for zone definitions (loaded relative to configDir). */
    private String zonesFile = "zones.csv";

    /**
     * Private constructor for singleton pattern
     */
    private TaxiConfig() {
        // Default values are already set above
    }

    /**
     * Get singleton instance
     */
    public static synchronized TaxiConfig getInstance() {
        if (instance == null) {
            instance = new TaxiConfig();
        }
        return instance;
    }

    /**
     * Load configuration from properties file
     * @param configPath Path to taxi_config.properties
     * @return true if loaded successfully, false otherwise
     */
    public boolean loadFromFile(String configPath) {
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(configPath)) {
            props.load(fis);

            System.out.println("=== Loading Configuration ===");

            // Load all parameters with defaults
            simulationEngine = props.getProperty("taxi.simulation.engine", simulationEngine).trim();
            System.out.println("  Simulation engine: " + simulationEngine);
            loadDirectories(props);
            loadFleetConfig(props);
            loadTripConfig(props);
            loadDistanceParams(props);
            loadNearbyTripLogic(props);
            loadShiftConfig(props);
            loadRoutingParams(props);
            loadGeographicalParams(props);
            loadHotspotConfig(props);
            loadFareParams(props);
            loadNightHours(props);
            loadFormatParams(props);
            loadSystemParams(props);
            loadTaxiTypeDistribution(props);
            loadTimePeriods(props);
            loadAttractivenessCoefficients(props);
            loadZoneConfig(props);
            loadSpatialConfig(props);
            loadTransportIndexConfig(props);
            loadSpatialDistributionConfig(props);
            loadZoneEnrichmentConfig(props);

            System.out.println("✓ Configuration loaded successfully from: " + configPath);
            return true;

        } catch (IOException e) {
            System.out.println("⚠ Could not load config file: " + e.getMessage());
            System.out.println("  Using default values");
            return false;
        } catch (NumberFormatException e) {
            System.out.println("⚠ Error parsing config value: " + e.getMessage());
            System.out.println("  Using default for invalid parameter");
            return false;
        }
    }

    private void loadDirectories(Properties props) {
        inputDirectory = PathResolver.resolve(props.getProperty("input.directory", inputDirectory));
        outputDirectory = PathResolver.resolve(props.getProperty("output.directory", outputDirectory));
        System.out.println("  Input: " + inputDirectory);
        System.out.println("  Output: " + outputDirectory);
    }

    private void loadFleetConfig(Properties props) {
        taxiTotalRegistered = getInt(props, "taxi.total.registered", taxiTotalRegistered);
        taxiOperatingRate = getDouble(props, "taxi.operating.rate", taxiOperatingRate);

        int explicitFleetSize = getInt(props, "taxi.fleet.size", -1);
        if (explicitFleetSize > 0) {
            taxiFleetSize = explicitFleetSize;
        } else {
            taxiFleetSize = (int)(taxiTotalRegistered * taxiOperatingRate);
        }
        System.out.println("  Fleet size: " + taxiFleetSize + " taxis");
    }

    private void loadTripConfig(Properties props) {
        taxiTripsAverage = getDouble(props, "taxi.trips.average", taxiTripsAverage);
        taxiTripsMin = getInt(props, "taxi.trips.min", taxiTripsMin);
        taxiTripsMax = getInt(props, "taxi.trips.max", taxiTripsMax);
        taxiTripsStddev = getDouble(props, "taxi.trips.stddev", taxiTripsStddev);
        System.out.println("  Trips per taxi: " + taxiTripsAverage +
            " (min=" + taxiTripsMin + ", max=" + taxiTripsMax + ")");
    }

    private void loadDistanceParams(Properties props) {
        tripDistanceAverage = getDouble(props, "trip.distance.average", tripDistanceAverage);
        tripDistanceStddev = getDouble(props, "trip.distance.stddev", tripDistanceStddev);
        tripDistanceMin = getDouble(props, "trip.distance.min", tripDistanceMin);
        tripDistanceMax = getDouble(props, "trip.distance.max", tripDistanceMax);
        tripGenerationMaxAttempts = getInt(props, "taxi.trip.generation.max.attempts", tripGenerationMaxAttempts);
        // Phase 3 (B1): log-normal distance params for shift_time engine
        tripDistanceLoadedSigma = getDouble(props, "trip.distance.loaded.sigma", tripDistanceLoadedSigma);
        tripDistanceEmptyMeanKm = getDouble(props, "trip.distance.empty.mean.km", tripDistanceEmptyMeanKm);
        tripDistanceEmptySigma = getDouble(props, "trip.distance.empty.sigma", tripDistanceEmptySigma);
        projectionRadiusKm = getDouble(props, "spatial.projection.radius.km", projectionRadiusKm);
        System.out.println("  Trip distance: " + tripDistanceAverage + " km average");
        System.out.println("  Trip generation MAX_ATTEMPTS: " + tripGenerationMaxAttempts);
        if ("shift_time".equalsIgnoreCase(simulationEngine)) {
            System.out.println("  [v7.0] Loaded leg log-normal: mean=" + tripDistanceAverage
                + " km, sigma=" + tripDistanceLoadedSigma + ", bounds=[" + tripDistanceMin + ", " + tripDistanceMax + "] km");
            System.out.println("  [v7.0] Empty leg log-normal:  mean=" + tripDistanceEmptyMeanKm
                + " km, sigma=" + tripDistanceEmptySigma + " (Phase 4+)");
            System.out.println("  [v7.0] Projection radius: " + projectionRadiusKm + " km (spiral-grid scan)");
        }
    }

    private void loadNearbyTripLogic(Properties props) {
        useNearbyTrips = getBoolean(props, "use.nearby.trips", useNearbyTrips);
        nearbyTripProbability = getDouble(props, "nearby.trip.probability", nearbyTripProbability);
        nearbyTripRadiusKm = getDouble(props, "nearby.trip.radius.km", nearbyTripRadiusKm);
        emptyTripThresholdKm = getDouble(props, "empty.trip.threshold.km", emptyTripThresholdKm);
        System.out.println("  Nearby trips: " + (useNearbyTrips ? "ENABLED" : "DISABLED") +
            " (" + (nearbyTripProbability * 100) + "% within " + nearbyTripRadiusKm + " km)");
        System.out.println("  Empty trip threshold: " + emptyTripThresholdKm + " km");
    }

    private void loadShiftConfig(Properties props) {
        shiftDurationMin = getInt(props, "shift.duration.min", shiftDurationMin);
        shiftDurationMax = getInt(props, "shift.duration.max", shiftDurationMax);
        shiftDurationAverage = getDouble(props, "shift.duration.average", shiftDurationAverage);
        shiftDurationStddev = getDouble(props, "shift.duration.stddev", shiftDurationStddev);

        String shift1Time = props.getProperty("shift.start.1.time", "06:00");
        String shift2Time = props.getProperty("shift.start.2.time", "08:00");
        String shift3Time = props.getProperty("shift.start.3.time", "14:00");

        shiftStart1Time = parseTimeToSeconds(shift1Time);
        shiftStart1Prob = getDouble(props, "shift.start.1.probability", shiftStart1Prob);
        shiftStart2Time = parseTimeToSeconds(shift2Time);
        shiftStart2Prob = getDouble(props, "shift.start.2.probability", shiftStart2Prob);
        shiftStart3Time = parseTimeToSeconds(shift3Time);
        shiftStart3Prob = getDouble(props, "shift.start.3.probability", shiftStart3Prob);

        System.out.println("  Shift duration: " + shiftDurationMin + "-" + shiftDurationMax + " hours");
        System.out.println("  Shift starts:");
        System.out.println("    " + shift1Time + " (" + (shiftStart1Prob * 100) + "%)");
        System.out.println("    " + shift2Time + " (" + (shiftStart2Prob * 100) + "%)");
        System.out.println("    " + shift3Time + " (" + (shiftStart3Prob * 100) + "%)");
    }

    private void loadRoutingParams(Properties props) {
        taxiAverageSpeed = getDouble(props, "taxi.average.speed", taxiAverageSpeed);
        taxiPickupTime = getInt(props, "taxi.pickup.time", taxiPickupTime);
        taxiBreakTime = getInt(props, "taxi.break.time", taxiBreakTime);
        manhattanFactor = getDouble(props, "distance.manhattan.factor", manhattanFactor);
        System.out.println("  Taxi speed: " + taxiAverageSpeed + " km/h");
        System.out.println("  Manhattan factor: " + manhattanFactor);
    }

    private void loadGeographicalParams(Properties props) {
        cityName = props.getProperty("city.name", cityName);

        // Support both new generic keys and legacy Tokyo keys for backward compatibility
        boundsMinLon = getDouble(props, "city.bounds.min.lon",
            getDouble(props, "tokyo.bounds.min.lon", boundsMinLon));
        boundsMaxLon = getDouble(props, "city.bounds.max.lon",
            getDouble(props, "tokyo.bounds.max.lon", boundsMaxLon));
        boundsMinLat = getDouble(props, "city.bounds.min.lat",
            getDouble(props, "tokyo.bounds.min.lat", boundsMinLat));
        boundsMaxLat = getDouble(props, "city.bounds.max.lat",
            getDouble(props, "tokyo.bounds.max.lat", boundsMaxLat));
        earthRadiusKm = getDouble(props, "earth.radius.km", earthRadiusKm);

        System.out.println("  City: " + cityName);
        System.out.println("  Bounds: [" + boundsMinLon + "," + boundsMinLat +
            "] to [" + boundsMaxLon + "," + boundsMaxLat + "]");
    }

    private void loadHotspotConfig(Properties props) {
        useHotspots = getBoolean(props, "use.hotspots", useHotspots);
        hotspotAirportNightMultiplier = getDouble(props, "hotspot.airport.night.multiplier",
            hotspotAirportNightMultiplier);
        System.out.println("  Hotspots: " + (useHotspots ? "ENABLED" : "DISABLED"));
    }

    private void loadFareParams(Properties props) {
        taxiFareBase = getDouble(props, "taxi.fare.base", taxiFareBase);
        taxiFareBaseDistance = getDouble(props, "taxi.fare.base.distance", taxiFareBaseDistance);
        taxiFarePerKm = getDouble(props, "taxi.fare.per.km", taxiFarePerKm);
        taxiFareWaitingPerHour = getDouble(props, "taxi.fare.waiting.per.hour", taxiFareWaitingPerHour);
        taxiFareNightSurcharge = getDouble(props, "taxi.fare.night.surcharge", taxiFareNightSurcharge);
        System.out.println("  Fare: ¥" + taxiFareBase + " base + ¥" + taxiFarePerKm + "/km");
    }

    private void loadNightHours(Properties props) {
        String nightStart = props.getProperty("taxi.fare.night.start", "22:00");
        String nightEnd = props.getProperty("taxi.fare.night.end", "05:00");

        nightHoursStartSeconds = parseTimeToSeconds(nightStart);
        nightHoursEndSeconds = parseTimeToSeconds(nightEnd);
        nightHoursStart = (int)(nightHoursStartSeconds / 3600);
        nightHoursEnd = (int)(nightHoursEndSeconds / 3600);

        System.out.println("  Night hours: " + nightStart + "-" + nightEnd +
            " (surcharge: " + ((taxiFareNightSurcharge - 1.0) * 100) + "%)");
    }

    private void loadFormatParams(Properties props) {
        exportDatetimeFormat = props.getProperty("export.datetime.format", exportDatetimeFormat);
        exportTimestampFormat = props.getProperty("export.timestamp.format", exportTimestampFormat);
    }

    private void loadSystemParams(Properties props) {
        randomSeed = getInt(props, "random.seed", randomSeed);
        if (randomSeed >= 0) {
            System.out.println("  Random seed: " + randomSeed + " (reproducible)");
        } else {
            System.out.println("  Random seed: random (non-reproducible)");
        }
    }

    private void loadTaxiTypeDistribution(Properties props) {
        // Legacy 3-type prob keys (used by initializeTaxis when engine=legacy).
        taxiTypeLocalProb = getDouble(props, "taxi.type.local.prob", taxiTypeLocalProb);
        taxiTypeCitywideProb = getDouble(props, "taxi.type.citywide.prob", taxiTypeCitywideProb);
        taxiTypeHubProb = getDouble(props, "taxi.type.hub.prob", taxiTypeHubProb);
        localFamiliarRadiusKm = getDouble(props, "taxi.type.local.familiar.radius.km", localFamiliarRadiusKm);

        // v7.0 5-type share keys (used by initializeTaxis when engine=shift_time, B2/Phase 4).
        taxiTypeLocalShare = getDouble(props, "taxi.type.local.share", taxiTypeLocalShare);
        taxiTypeCitywideShare = getDouble(props, "taxi.type.citywide.share", taxiTypeCitywideShare);
        taxiTypeAppPreferredShare = getDouble(props, "taxi.type.app_preferred.share", taxiTypeAppPreferredShare);
        taxiTypeHubShare = getDouble(props, "taxi.type.hub.share", taxiTypeHubShare);
        taxiTypeRideHailPrhsShare = getDouble(props, "taxi.type.ride_hail_prhs.share", taxiTypeRideHailPrhsShare);

        taxiTypeAppPreferredFamiliarRadiusKm = getDouble(props,
            "taxi.type.app_preferred.familiar.radius.km", taxiTypeAppPreferredFamiliarRadiusKm);
        taxiTypeRideHailPrhsFamiliarRadiusKm = getDouble(props,
            "taxi.type.ride_hail_prhs.familiar.radius.km", taxiTypeRideHailPrhsFamiliarRadiusKm);

        // v7.0 per-type mode mix (street / app / stand) — used by ModeMixResolver in B2/Phase 4.
        taxiTypeLocalModeStreet = getDouble(props, "taxi.type.local.mode.street", taxiTypeLocalModeStreet);
        taxiTypeLocalModeApp    = getDouble(props, "taxi.type.local.mode.app",    taxiTypeLocalModeApp);
        taxiTypeLocalModeStand  = getDouble(props, "taxi.type.local.mode.stand",  taxiTypeLocalModeStand);
        taxiTypeCitywideModeStreet = getDouble(props, "taxi.type.citywide.mode.street", taxiTypeCitywideModeStreet);
        taxiTypeCitywideModeApp    = getDouble(props, "taxi.type.citywide.mode.app",    taxiTypeCitywideModeApp);
        taxiTypeCitywideModeStand  = getDouble(props, "taxi.type.citywide.mode.stand",  taxiTypeCitywideModeStand);
        taxiTypeAppPreferredModeStreet = getDouble(props, "taxi.type.app_preferred.mode.street", taxiTypeAppPreferredModeStreet);
        taxiTypeAppPreferredModeApp    = getDouble(props, "taxi.type.app_preferred.mode.app",    taxiTypeAppPreferredModeApp);
        taxiTypeAppPreferredModeStand  = getDouble(props, "taxi.type.app_preferred.mode.stand",  taxiTypeAppPreferredModeStand);
        taxiTypeHubModeStreet = getDouble(props, "taxi.type.hub.mode.street", taxiTypeHubModeStreet);
        taxiTypeHubModeApp    = getDouble(props, "taxi.type.hub.mode.app",    taxiTypeHubModeApp);
        taxiTypeHubModeStand  = getDouble(props, "taxi.type.hub.mode.stand",  taxiTypeHubModeStand);
        taxiTypeRideHailPrhsModeStreet = getDouble(props, "taxi.type.ride_hail_prhs.mode.street", taxiTypeRideHailPrhsModeStreet);
        taxiTypeRideHailPrhsModeApp    = getDouble(props, "taxi.type.ride_hail_prhs.mode.app",    taxiTypeRideHailPrhsModeApp);
        taxiTypeRideHailPrhsModeStand  = getDouble(props, "taxi.type.ride_hail_prhs.mode.stand",  taxiTypeRideHailPrhsModeStand);

        // v7.0 AT_STAND exponential wait (DESIGN.md §2.8).
        taxiStandWaitAirportMeanMinutes = getDouble(props, "taxi.stand.wait.airport.mean.minutes", taxiStandWaitAirportMeanMinutes);
        taxiStandWaitStationMeanMinutes = getDouble(props, "taxi.stand.wait.station.mean.minutes", taxiStandWaitStationMeanMinutes);
        taxiStandWaitEntertainmentMeanMinutes = getDouble(props, "taxi.stand.wait.entertainment.mean.minutes", taxiStandWaitEntertainmentMeanMinutes);

        // v7.0 PRHS regulatory windows (DESIGN.md §2.9).
        prhsWindowCount = getInt(props, "prhs.window.count", 0);
        prhsWindowSpecs.clear();
        for (int i = 1; i <= prhsWindowCount; i++) {
            String spec = props.getProperty("prhs.window." + i);
            if (spec != null && !spec.isEmpty()) {
                prhsWindowSpecs.add(spec);
            }
        }

        // V4.0: Zone clustering parameters
        zoneClusterMinZones = getInt(props, "zone.cluster.min.zones", zoneClusterMinZones);
        zoneClusterMaxZones = getInt(props, "zone.cluster.max.zones", zoneClusterMaxZones);
        zoneClusterMinDistanceKm = getDouble(props, "zone.cluster.min.distance.km", zoneClusterMinDistanceKm);
        zoneClusterMaxDistanceKm = getDouble(props, "zone.cluster.max.distance.km", zoneClusterMaxDistanceKm);

        if ("shift_time".equalsIgnoreCase(simulationEngine)) {
            // v7.0 5-type display
            System.out.println("  [v7.0] Taxi types: LOCAL=" + (taxiTypeLocalShare * 100)
                + "%, CITYWIDE=" + (taxiTypeCitywideShare * 100)
                + "%, APP_PREFERRED=" + (taxiTypeAppPreferredShare * 100)
                + "%, HUB=" + (taxiTypeHubShare * 100)
                + "%, RIDE_HAIL_PRHS=" + (taxiTypeRideHailPrhsShare * 100) + "%");
            System.out.println("  [v7.0] Stand wait (Exp mean min): airport=" + taxiStandWaitAirportMeanMinutes
                + ", station=" + taxiStandWaitStationMeanMinutes
                + ", entertainment=" + taxiStandWaitEntertainmentMeanMinutes);
            System.out.println("  [v7.0] PRHS windows: " + prhsWindowCount + " configured" + (prhsWindowCount > 0 ? " " + prhsWindowSpecs : ""));
        } else {
            // legacy 3-type display
            System.out.println("  Taxi types: LOCAL=" + (taxiTypeLocalProb * 100) + "%, " +
                "CITYWIDE=" + (taxiTypeCitywideProb * 100) + "%, " +
                "HUB=" + (taxiTypeHubProb * 100) + "%");
        }
        System.out.println("  Local familiar radius (legacy): " + localFamiliarRadiusKm + " km");
        System.out.println("  Zone clustering: " + zoneClusterMinZones + "-" + zoneClusterMaxZones +
            " zones within " + zoneClusterMinDistanceKm + "-" + zoneClusterMaxDistanceKm + " km");
    }

    private void loadTimePeriods(Properties props) {
        String period1Start = props.getProperty("time.period.1.start", "05:00");
        String period1End = props.getProperty("time.period.1.end", "10:00");
        String period2Start = props.getProperty("time.period.2.start", "10:00");
        String period2End = props.getProperty("time.period.2.end", "18:00");

        timePeriod1Start = parseTimeToSeconds(period1Start);
        timePeriod1End = parseTimeToSeconds(period1End);
        timePeriod2Start = parseTimeToSeconds(period2Start);
        timePeriod2End = parseTimeToSeconds(period2End);

        System.out.println("  Time periods:");
        System.out.println("    Morning: " + period1Start + "-" + period1End);
        System.out.println("    Daytime: " + period2Start + "-" + period2End);
        System.out.println("    Night: " + period2End + "-" + period1Start);
    }

    private void loadAttractivenessCoefficients(Properties props) {
        attractivenessBeta1 = getDouble(props, "attractiveness.beta1.jobs", attractivenessBeta1);
        attractivenessBeta2 = getDouble(props, "attractiveness.beta2.shops", attractivenessBeta2);
        attractivenessBeta3 = getDouble(props, "attractiveness.beta3.nightlife", attractivenessBeta3);
        attractivenessBeta4 = getDouble(props, "attractiveness.beta4.residential", attractivenessBeta4);
        System.out.println("  Attractiveness coefficients: β1=" + attractivenessBeta1 +
            ", β2=" + attractivenessBeta2 + ", β3=" + attractivenessBeta3 +
            ", β4=" + attractivenessBeta4);
    }

    private void loadZoneConfig(Properties props) {
        zonesFile = props.getProperty("zones.file", zonesFile);
        System.out.println("  Zones file: " + zonesFile);
    }

    private void loadSpatialConfig(Properties props) {
        spatialValidationEnabled = getBoolean(props, "spatial.validation.enabled", spatialValidationEnabled);
        shapefileDir = props.getProperty("spatial.shapefile.dir", shapefileDir);
        riverBufferKm = getDouble(props, "spatial.river.buffer.km", riverBufferKm);
        prefectureCodes = props.getProperty("city.prefecture.codes", "").trim();
        System.out.println("  Spatial validation: " + (spatialValidationEnabled ? "ENABLED" : "DISABLED"));
        if (spatialValidationEnabled) {
            System.out.println("  Shapefile dir: " + shapefileDir);
            System.out.println("  River buffer: " + riverBufferKm + " km");
        }
        if (!prefectureCodes.isEmpty()) {
            System.out.println("  Prefecture codes: " + prefectureCodes);
        }
    }

    private void loadTransportIndexConfig(Properties props) {
        transportIndexEnabled = getBoolean(props, "transport.index.enabled", transportIndexEnabled);
        stationProximityMaxKm = getDouble(props, "transport.station.proximity.max.km", stationProximityMaxKm);
        stationBiasProb = getDouble(props, "transport.station.bias.probability", stationBiasProb);
        System.out.println("  Transport index: " + (transportIndexEnabled ? "ENABLED" : "DISABLED"));
        if (transportIndexEnabled) {
            System.out.println("  Station proximity max: " + stationProximityMaxKm + " km");
            System.out.println("  Station bias probability: " + (stationBiasProb * 100) + "%");
        }
    }

    private void loadSpatialDistributionConfig(Properties props) {
        spatialDistributionMode = props.getProperty("spatial.distribution.mode", spatialDistributionMode).trim();
        spatialGaussianSigmaFactor = getDouble(props, "spatial.gaussian.sigma.factor", spatialGaussianSigmaFactor);
        spatialGaussianClipFactor = getDouble(props, "spatial.gaussian.clip.factor", spatialGaussianClipFactor);
        spatialAspectX = getDouble(props, "spatial.aspect.x", spatialAspectX);
        spatialAspectY = getDouble(props, "spatial.aspect.y", spatialAspectY);
        spatialJitterMeters = getDouble(props, "spatial.jitter.meters", spatialJitterMeters);
        System.out.println("  Spatial distribution: " + spatialDistributionMode +
            " (sigma=" + spatialGaussianSigmaFactor + ", clip=" + spatialGaussianClipFactor +
            ", aspect=" + spatialAspectX + "x" + spatialAspectY +
            ", jitter=" + spatialJitterMeters + "m)");
    }

    private void loadZoneEnrichmentConfig(Properties props) {
        zoneEnrichmentEnabled = getBoolean(props, "zone.enrichment.enabled", zoneEnrichmentEnabled);
        stationCoverageKm = getDouble(props, "zone.enrichment.station.coverage.km", stationCoverageKm);
        settlementCoverageKm = getDouble(props, "zone.enrichment.settlement.coverage.km", settlementCoverageKm);
        System.out.println("  Zone enrichment: " + (zoneEnrichmentEnabled ? "ENABLED" : "DISABLED"));
        if (zoneEnrichmentEnabled) {
            System.out.println("  Station coverage threshold: " + stationCoverageKm + " km");
            System.out.println("  Settlement coverage threshold: " + settlementCoverageKm + " km");
        }
    }

    // ===== HELPER METHODS =====

    private int getInt(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDouble(Properties props, String key, double defaultValue) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Properties props, String key, boolean defaultValue) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(defaultValue)));
    }

    private long parseTimeToSeconds(String timeStr) {
        try {
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 3600L + minutes * 60L;
            } else {
                return Integer.parseInt(timeStr) * 3600L;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check if given time is during night hours
     * @param timeSeconds Time in seconds since midnight (can exceed 86400 for multi-day)
     * @return true if night hours
     */
    public boolean isNightHours(long timeSeconds) {
        // Normalize time to 0-86400 range for multi-day trips
        long normalizedTime = timeSeconds % 86400;
        return normalizedTime >= nightHoursStartSeconds || normalizedTime < nightHoursEndSeconds;
    }

    /**
     * Get time period for attractiveness calculation
     * @param timeSeconds Time in seconds since midnight (can exceed 86400 for multi-day)
     * @return 0=morning, 1=daytime, 2=night
     */
    public int getTimePeriod(long timeSeconds) {
        // Normalize time to 0-86400 range for multi-day trips
        long normalizedTime = timeSeconds % 86400;
        if (normalizedTime >= timePeriod1Start && normalizedTime < timePeriod1End) {
            return 0;  // Morning
        } else if (normalizedTime >= timePeriod2Start && normalizedTime < timePeriod2End) {
            return 1;  // Daytime
        } else {
            return 2;  // Night
        }
    }

    // ===== GETTERS =====

    /**
     * Returns the trip-generation engine selector.
     * "legacy" or "shift_time". Phase 1-7 of DESIGN.md transitions
     * the default from "legacy" to "shift_time" while keeping both code paths.
     */
    public String getSimulationEngine() { return simulationEngine; }

    /** True iff the v7.0 shift-time engine is selected. */
    public boolean isShiftTimeEngine() { return "shift_time".equalsIgnoreCase(simulationEngine); }

    public String getInputDirectory() { return inputDirectory; }
    public String getOutputDirectory() { return outputDirectory; }

    public int getTaxiTotalRegistered() { return taxiTotalRegistered; }
    public double getTaxiOperatingRate() { return taxiOperatingRate; }
    public int getTaxiFleetSize() { return taxiFleetSize; }

    public double getTaxiTripsAverage() { return taxiTripsAverage; }
    public int getTaxiTripsMin() { return taxiTripsMin; }
    public int getTaxiTripsMax() { return taxiTripsMax; }
    public double getTaxiTripsStddev() { return taxiTripsStddev; }

    public double getTripDistanceAverage() { return tripDistanceAverage; }
    public double getTripDistanceStddev() { return tripDistanceStddev; }
    public double getTripDistanceMin() { return tripDistanceMin; }
    public double getTripDistanceMax() { return tripDistanceMax; }

    /**
     * Spatial-validation retry budget per trip-pair generation attempt.
     * Configured via {@code taxi.trip.generation.max.attempts} (default 200).
     * Higher values reduce premature loop exit in dense zones with strong
     * water/river constraints; lower values run faster.
     */
    public int getTripGenerationMaxAttempts() { return tripGenerationMaxAttempts; }

    // Phase 3 (B1) — v7.0 log-normal distance distribution getters
    /** Sigma of log-normal loaded-leg distance. Used by shift_time engine. DESIGN.md §2.6. */
    public double getTripDistanceLoadedSigma() { return tripDistanceLoadedSigma; }
    /** Mean of log-normal empty-leg distance in km. Loaded by Phase 3, used by Phase 4+. */
    public double getTripDistanceEmptyMeanKm() { return tripDistanceEmptyMeanKm; }
    /** Sigma of log-normal empty-leg distance. Loaded by Phase 3, used by Phase 4+. */
    public double getTripDistanceEmptySigma() { return tripDistanceEmptySigma; }
    /**
     * Spiral-grid projection radius (km) for nearest-valid-cell snap when a
     * sampled dropoff position fails spatial validation. Used by shift_time
     * engine in Phase 3+. DESIGN.md §2.6.
     */
    public double getProjectionRadiusKm() { return projectionRadiusKm; }

    public boolean isUseNearbyTrips() { return useNearbyTrips; }
    public double getNearbyTripProbability() { return nearbyTripProbability; }
    public double getNearbyTripRadiusKm() { return nearbyTripRadiusKm; }
    public double getEmptyTripThresholdKm() { return emptyTripThresholdKm; }

    public int getShiftDurationMin() { return shiftDurationMin; }
    public int getShiftDurationMax() { return shiftDurationMax; }
    public double getShiftDurationAverage() { return shiftDurationAverage; }
    public double getShiftDurationStddev() { return shiftDurationStddev; }

    public long getShiftStart1Time() { return shiftStart1Time; }
    public double getShiftStart1Prob() { return shiftStart1Prob; }
    public long getShiftStart2Time() { return shiftStart2Time; }
    public double getShiftStart2Prob() { return shiftStart2Prob; }
    public long getShiftStart3Time() { return shiftStart3Time; }
    public double getShiftStart3Prob() { return shiftStart3Prob; }

    public double getTaxiAverageSpeed() { return taxiAverageSpeed; }
    public int getTaxiPickupTime() { return taxiPickupTime; }
    public int getTaxiBreakTime() { return taxiBreakTime; }
    public double getManhattanFactor() { return manhattanFactor; }

    public String getCityName() { return cityName; }
    public String getConfigDir() { return configDir; }
    public void setConfigDir(String dir) { this.configDir = dir; }
    public double getBoundsMinLon() { return boundsMinLon; }
    public double getBoundsMaxLon() { return boundsMaxLon; }
    public double getBoundsMinLat() { return boundsMinLat; }
    public double getBoundsMaxLat() { return boundsMaxLat; }
    public double getEarthRadiusKm() { return earthRadiusKm; }

    // Backward compatibility — deprecated
    @Deprecated public double getTokyoBoundsMinLon() { return boundsMinLon; }
    @Deprecated public double getTokyoBoundsMaxLon() { return boundsMaxLon; }
    @Deprecated public double getTokyoBoundsMinLat() { return boundsMinLat; }
    @Deprecated public double getTokyoBoundsMaxLat() { return boundsMaxLat; }

    public boolean isUseHotspots() { return useHotspots; }
    public double getHotspotAirportNightMultiplier() { return hotspotAirportNightMultiplier; }

    public double getTaxiFareBase() { return taxiFareBase; }
    public double getTaxiFareBaseDistance() { return taxiFareBaseDistance; }
    public double getTaxiFarePerKm() { return taxiFarePerKm; }
    public double getTaxiFareWaitingPerHour() { return taxiFareWaitingPerHour; }
    public double getTaxiFareNightSurcharge() { return taxiFareNightSurcharge; }

    public int getNightHoursStart() { return nightHoursStart; }
    public int getNightHoursEnd() { return nightHoursEnd; }
    public long getNightHoursStartSeconds() { return nightHoursStartSeconds; }
    public long getNightHoursEndSeconds() { return nightHoursEndSeconds; }

    public String getExportDatetimeFormat() { return exportDatetimeFormat; }
    public String getExportTimestampFormat() { return exportTimestampFormat; }

    public int getRandomSeed() { return randomSeed; }

    public double getTaxiTypeLocalProb() { return taxiTypeLocalProb; }
    public double getTaxiTypeCitywideProb() { return taxiTypeCitywideProb; }
    public double getTaxiTypeHubProb() { return taxiTypeHubProb; }
    public double getLocalFamiliarRadiusKm() { return localFamiliarRadiusKm; }

    // ═══ v7.0 5-type fleet share getters (B2 / Phase 4) ═══
    public double getTaxiTypeLocalShare()         { return taxiTypeLocalShare; }
    public double getTaxiTypeCitywideShare()      { return taxiTypeCitywideShare; }
    public double getTaxiTypeAppPreferredShare()  { return taxiTypeAppPreferredShare; }
    public double getTaxiTypeHubShare()           { return taxiTypeHubShare; }
    public double getTaxiTypeRideHailPrhsShare()  { return taxiTypeRideHailPrhsShare; }
    public double getTaxiTypeAppPreferredFamiliarRadiusKm()  { return taxiTypeAppPreferredFamiliarRadiusKm; }
    public double getTaxiTypeRideHailPrhsFamiliarRadiusKm()  { return taxiTypeRideHailPrhsFamiliarRadiusKm; }

    // ═══ v7.0 Per-type mode-mix getters (street/app/stand) ═══
    public double getTaxiTypeLocalModeStreet()  { return taxiTypeLocalModeStreet; }
    public double getTaxiTypeLocalModeApp()     { return taxiTypeLocalModeApp; }
    public double getTaxiTypeLocalModeStand()   { return taxiTypeLocalModeStand; }
    public double getTaxiTypeCitywideModeStreet() { return taxiTypeCitywideModeStreet; }
    public double getTaxiTypeCitywideModeApp()    { return taxiTypeCitywideModeApp; }
    public double getTaxiTypeCitywideModeStand()  { return taxiTypeCitywideModeStand; }
    public double getTaxiTypeAppPreferredModeStreet() { return taxiTypeAppPreferredModeStreet; }
    public double getTaxiTypeAppPreferredModeApp()    { return taxiTypeAppPreferredModeApp; }
    public double getTaxiTypeAppPreferredModeStand()  { return taxiTypeAppPreferredModeStand; }
    public double getTaxiTypeHubModeStreet()    { return taxiTypeHubModeStreet; }
    public double getTaxiTypeHubModeApp()       { return taxiTypeHubModeApp; }
    public double getTaxiTypeHubModeStand()     { return taxiTypeHubModeStand; }
    public double getTaxiTypeRideHailPrhsModeStreet() { return taxiTypeRideHailPrhsModeStreet; }
    public double getTaxiTypeRideHailPrhsModeApp()    { return taxiTypeRideHailPrhsModeApp; }
    public double getTaxiTypeRideHailPrhsModeStand()  { return taxiTypeRideHailPrhsModeStand; }

    // ═══ v7.0 AT_STAND wait time getters (DESIGN.md §2.8) ═══
    /** Mean exponential wait at airport stands in MINUTES. */
    public double getTaxiStandWaitAirportMeanMinutes() { return taxiStandWaitAirportMeanMinutes; }
    /** Mean exponential wait at major-station stands in MINUTES. */
    public double getTaxiStandWaitStationMeanMinutes() { return taxiStandWaitStationMeanMinutes; }
    /** Mean exponential wait at entertainment-district stands in MINUTES. */
    public double getTaxiStandWaitEntertainmentMeanMinutes() { return taxiStandWaitEntertainmentMeanMinutes; }

    // ═══ v7.0 PRHS regulatory window getters ═══
    /** Number of MLIT-permitted PRHS operating windows in current scenario. */
    public int getPrhsWindowCount() { return prhsWindowCount; }
    /** Raw PRHS window spec strings (e.g. "07:00-10:00,Mon-Fri"). Parsed at runtime. */
    public java.util.List<String> getPrhsWindowSpecs() { return java.util.Collections.unmodifiableList(prhsWindowSpecs); }

    // V4.0: Zone clustering getters
    public int getZoneClusterMinZones() { return zoneClusterMinZones; }
    public int getZoneClusterMaxZones() { return zoneClusterMaxZones; }
    public double getZoneClusterMinDistanceKm() { return zoneClusterMinDistanceKm; }
    public double getZoneClusterMaxDistanceKm() { return zoneClusterMaxDistanceKm; }

    public long getTimePeriod1Start() { return timePeriod1Start; }
    public long getTimePeriod1End() { return timePeriod1End; }
    public long getTimePeriod2Start() { return timePeriod2Start; }
    public long getTimePeriod2End() { return timePeriod2End; }

    public double getAttractivenessBeta1() { return attractivenessBeta1; }
    public double getAttractivenessBeta2() { return attractivenessBeta2; }
    public double getAttractivenessBeta3() { return attractivenessBeta3; }
    public double getAttractivenessBeta4() { return attractivenessBeta4; }

    public String getZonesFile() { return zonesFile; }

    // Spatial validation getters
    public boolean isSpatialValidationEnabled() { return spatialValidationEnabled; }
    public String getShapefileDir() { return shapefileDir; }
    public double getRiverBufferKm() { return riverBufferKm; }
    public String getPrefectureCodes() { return prefectureCodes; }

    // Transport index getters
    public boolean isTransportIndexEnabled() { return transportIndexEnabled; }
    public double getStationProximityMaxKm() { return stationProximityMaxKm; }
    public double getStationBiasProb() { return stationBiasProb; }

    // Zone enrichment getters
    public boolean isZoneEnrichmentEnabled() { return zoneEnrichmentEnabled; }
    public double getStationCoverageKm() { return stationCoverageKm; }
    public double getSettlementCoverageKm() { return settlementCoverageKm; }

    // V5.2: Spatial distribution getters
    public String getSpatialDistributionMode() { return spatialDistributionMode; }
    public double getSpatialGaussianSigmaFactor() { return spatialGaussianSigmaFactor; }
    public double getSpatialGaussianClipFactor() { return spatialGaussianClipFactor; }
    public double getSpatialAspectX() { return spatialAspectX; }
    public double getSpatialAspectY() { return spatialAspectY; }
    public double getSpatialJitterMeters() { return spatialJitterMeters; }
}
