package taxi.sim;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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

    // Singleton instance
    private static TaxiConfig instance;

    // ===== DIRECTORIES =====
    private String inputDirectory = "./data/input/";
    private String outputDirectory = "./data/output/";

    // ===== FLEET CONFIGURATION =====
    // Fleet size calculation:
    // - If taxi.fleet.size is explicitly set in config, use that value
    // - Otherwise, calculate: taxiFleetSize = taxiTotalRegistered × taxiOperatingRate
    // This allows simulation of different cities with different taxi populations
    // by setting total.registered and operating.rate specific to each city
    private int taxiTotalRegistered = 50000;     // Total registered taxis in the city
    private double taxiOperatingRate = 0.80;      // Fraction of taxis operating at any time
    private int taxiFleetSize = 1000;             // Actual fleet size (calculated or explicit)

    // ===== TRIP CONFIGURATION =====
    private double taxiTripsAverage = 26.8;
    private int taxiTripsMin = 20;
    private int taxiTripsMax = 35;
    private double taxiTripsStddev = 3.0;

    // ===== DISTANCE PARAMETERS =====
    private double tripDistanceAverage = 5.0;
    private double tripDistanceStddev = 2.0;
    private double tripDistanceMin = 0.5;
    private double tripDistanceMax = 15.0;

    // ===== NEARBY TRIP LOGIC =====
    private boolean useNearbyTrips = true;
    private double nearbyTripProbability = 0.70;
    private double nearbyTripRadiusKm = 3.0;

    // ===== SHIFT CONFIGURATION =====
    private int shiftDurationMin = 12;
    private int shiftDurationMax = 16;
    private double shiftDurationAverage = 14;
    private double shiftDurationStddev = 1.0;

    // Shift start times (in seconds since midnight)
    private long shiftStart1Time = 6 * 3600;
    private double shiftStart1Prob = 0.40;
    private long shiftStart2Time = 8 * 3600;
    private double shiftStart2Prob = 0.30;
    private long shiftStart3Time = 14 * 3600;
    private double shiftStart3Prob = 0.30;

    // ===== ROUTING PARAMETERS =====
    private double taxiAverageSpeed = 20.0;
    private int taxiPickupTime = 300;     // seconds
    private int taxiBreakTime = 600;      // seconds
    private double manhattanFactor = 1.4;  // road distance = straight-line × factor

    // ===== CITY & GEOGRAPHICAL PARAMETERS =====
    private String cityName = "Tokyo";            // City name for display
    private String configDir = "config/taxi/";    // Directory containing config files
    private double boundsMinLon = 139.6;
    private double boundsMaxLon = 139.9;
    private double boundsMinLat = 35.5;
    private double boundsMaxLat = 35.8;
    private double earthRadiusKm = 6371.0;  // For Haversine formula

    // ===== HOTSPOT CONFIGURATION =====
    private boolean useHotspots = true;
    private double hotspotAirportNightMultiplier = 2.0;

    // ===== FARE PARAMETERS =====
    private double taxiFareBase = 730.0;
    private double taxiFareBaseDistance = 2.0;
    private double taxiFarePerKm = 320.0;
    private double taxiFareWaitingPerHour = 3085.0;
    private double taxiFareNightSurcharge = 1.20;

    // ===== NIGHT HOURS =====
    // Night hours for fare surcharge and hotspot weighting
    private int nightHoursStart = 22;  // 22:00
    private int nightHoursEnd = 5;     // 05:00
    private long nightHoursStartSeconds = 22 * 3600;  // 79200
    private long nightHoursEndSeconds = 5 * 3600;     // 18000

    // ===== TIME/DATE FORMATS =====
    private String exportDatetimeFormat = "MM/dd/yyyy HH:mm";
    private String exportTimestampFormat = "yyyyMMdd_HHmmss";

    // ===== SYSTEM =====
    private int randomSeed = 12345;

    // ===== TAXI TYPE DISTRIBUTION =====
    private double taxiTypeLocalProb = 0.70;      // 70% LOCAL type
    private double taxiTypeCitywideProb = 0.15;   // 15% CITYWIDE type
    private double taxiTypeHubProb = 0.15;        // 15% HUB type
    private double localFamiliarRadiusKm = 5.0;   // Familiar area radius for LOCAL taxis (legacy)

    // ===== ZONE CLUSTERING (V4.0) =====
    private int zoneClusterMinZones = 3;                  // Min familiar zones for LOCAL taxis
    private int zoneClusterMaxZones = 5;                  // Max familiar zones for LOCAL taxis
    private double zoneClusterMinDistanceKm = 5.0;        // Min distance between cluster zones
    private double zoneClusterMaxDistanceKm = 10.0;       // Max distance between cluster zones

    // ===== TIME PERIODS =====
    // Three time periods for attractiveness calculation
    private long timePeriod1Start = 5 * 3600;    // Morning: 05:00
    private long timePeriod1End = 10 * 3600;     // to 10:00
    private long timePeriod2Start = 10 * 3600;   // Daytime: 10:00
    private long timePeriod2End = 18 * 3600;     // to 18:00
    // Period 3 (Night): 18:00-05:00 (everything else)

    // ===== ATTRACTIVENESS COEFFICIENTS =====
    private double attractivenessBeta1 = 1.0;  // Jobs coefficient
    private double attractivenessBeta2 = 0.8;  // Shops coefficient
    private double attractivenessBeta3 = 0.6;  // Nightlife coefficient
    private double attractivenessBeta4 = 0.5;  // Residential coefficient

    // ===== SPATIAL VALIDATION =====
    private boolean spatialValidationEnabled = true;
    private String shapefileDir = "src/taxi/gm-jp/";
    private double riverBufferKm = 0.15;
    private String prefectureCodes = "";  // e.g. "13,14" — filter polbnda_jpn.shp by adm_code prefix

    // ===== TRANSPORT NETWORK INDEX =====
    private boolean transportIndexEnabled = true;
    private double stationProximityMaxKm = 2.0;     // Max distance for station boost
    private double stationBiasProb = 0.4;           // Probability of station-biased point generation

    // ===== ZONE ENRICHMENT FROM SHAPEFILES =====
    private boolean zoneEnrichmentEnabled = true;
    private double stationCoverageKm = 1.5;       // station is "covered" if within this of existing zone
    private double settlementCoverageKm = 2.0;     // settlement is "covered" if within this of existing zone

    // ===== ZONE CONFIGURATION =====
    private String zonesFile = "zones.csv";  // Zone CSV file path

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
        inputDirectory = props.getProperty("input.directory", inputDirectory);
        outputDirectory = props.getProperty("output.directory", outputDirectory);
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
        System.out.println("  Trip distance: " + tripDistanceAverage + " km average");
    }

    private void loadNearbyTripLogic(Properties props) {
        useNearbyTrips = getBoolean(props, "use.nearby.trips", useNearbyTrips);
        nearbyTripProbability = getDouble(props, "nearby.trip.probability", nearbyTripProbability);
        nearbyTripRadiusKm = getDouble(props, "nearby.trip.radius.km", nearbyTripRadiusKm);
        System.out.println("  Nearby trips: " + (useNearbyTrips ? "ENABLED" : "DISABLED") +
            " (" + (nearbyTripProbability * 100) + "% within " + nearbyTripRadiusKm + " km)");
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
        taxiTypeLocalProb = getDouble(props, "taxi.type.local.prob", taxiTypeLocalProb);
        taxiTypeCitywideProb = getDouble(props, "taxi.type.citywide.prob", taxiTypeCitywideProb);
        taxiTypeHubProb = getDouble(props, "taxi.type.hub.prob", taxiTypeHubProb);
        localFamiliarRadiusKm = getDouble(props, "taxi.type.local.familiar.radius.km", localFamiliarRadiusKm);

        // V4.0: Zone clustering parameters
        zoneClusterMinZones = getInt(props, "zone.cluster.min.zones", zoneClusterMinZones);
        zoneClusterMaxZones = getInt(props, "zone.cluster.max.zones", zoneClusterMaxZones);
        zoneClusterMinDistanceKm = getDouble(props, "zone.cluster.min.distance.km", zoneClusterMinDistanceKm);
        zoneClusterMaxDistanceKm = getDouble(props, "zone.cluster.max.distance.km", zoneClusterMaxDistanceKm);

        System.out.println("  Taxi types: LOCAL=" + (taxiTypeLocalProb * 100) + "%, " +
            "CITYWIDE=" + (taxiTypeCitywideProb * 100) + "%, " +
            "HUB=" + (taxiTypeHubProb * 100) + "%");
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

    public boolean isUseNearbyTrips() { return useNearbyTrips; }
    public double getNearbyTripProbability() { return nearbyTripProbability; }
    public double getNearbyTripRadiusKm() { return nearbyTripRadiusKm; }

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
}
