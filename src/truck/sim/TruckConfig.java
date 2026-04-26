package truck.sim;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import util.PathResolver;

/**
 * Configuration management singleton for Truck ABM.
 * 
 * Loads and provides access to all simulation parameters from truck_config.properties.
 * Thread-safe singleton pattern.
 * 
 * @author Truck ABM Framework
 * @version 1.0
 */
public class TruckConfig {

    /**
     * Simulation mode for truck logistics.
     */
    public enum SimulationMode {
        /** Within Tokyo metro area only */
        INTRA_METROPOLITAN,
        /** Long-haul between regions (MFS regions 61-71) */
        INTER_METROPOLITAN,
        /** Run BOTH for complete validation */
        DUAL,
        /** Nationwide: 106 zones (66 Kanto + 40 prefecture sub-zones from MFS67-71 disaggregation) */
        EXPANDED,
        /** Unified: 134 zones (Kanto detail + Keihanshin detail + national coverage) */
        UNIFIED
    }

    private static TruckConfig instance;
    private Properties properties;

    // ═══ Fleet Parameters ═══
    /** Total active trucks after operating rate applied. Source: MLIT national statistics. */
    private int truckFleetSize;
    /** MLIT operating rate (実働率). Reference only — fleet size is set explicitly. */
    private double truckOperatingRate;
    /** Total registered trucks nationwide. Source: MLIT. */
    private int registeredFleetSize;

    // ═══ MFS Survey-Day Baselines ═══
    /** MFS File 18 observed truck count on survey day (un-scaled). Validation target. */
    private int baselineSurveyTrucks;
    /** MFS File 18 observed total tons on survey day (un-scaled). Validation target. */
    private long baselineSurveyTons;

    // ═══ Truck Type Distribution ═══
    /** Probability of generating a DELIVERY truck. MFS-calibrated. */
    private double truckTypeDeliveryProb;
    /** Probability of generating a LONG_HAUL truck. MFS-calibrated. */
    private double truckTypeLongHaulProb;
    /** Probability of generating a MIXED_OPERATION truck. MFS-calibrated. */
    private double truckTypeUrbanLogisticsProb;
    /** Maximum operating radius (km) for DELIVERY trucks. Constrains destination selection. */
    private double truckTypeDeliveryFamiliarRadiusKm;

    // ═══ Vehicle Type Preferences [light, small, medium, heavy] ═══
    /** Vehicle size probabilities for DELIVERY trucks. */
    private double[] deliveryVehicleProbs;
    /** Vehicle size probabilities for LONG_HAUL trucks. */
    private double[] longHaulVehicleProbs;
    /** Vehicle size probabilities for MIXED_OPERATION trucks. */
    private double[] urbanVehicleProbs;

    // ═══ Trip Configuration ═══
    /** Default average trips per truck per day (fallback). */
    private int truckTripsAverage;
    /** Minimum trips per truck per day. */
    private int truckTripsMin;
    /** Maximum trips per truck per day (hard cap). */
    private int truckTripsMax;
    /** Standard deviation for Gaussian trip count variation. */
    private double truckTripsStddev;
    /** Trips per day for DELIVERY trucks. MFS-calibrated. */
    private int truckTripsDelivery;
    /** Trips per day for MIXED_OPERATION trucks. MFS-calibrated. */
    private int truckTripsMixed;
    /** Trips per day for LONG_HAUL trucks. MFS-calibrated. */
    private int truckTripsLongHaul;

    // ═══ V5.2: Delivery Tour Parameters (multi-stop tour model) ═══
    /** Maximum distance (km) from depot to first POI stop. */
    private double deliveryFirstStopMaxKm = 15.0;
    /** Maximum distance (km) between consecutive POI stops. */
    private double deliveryStopToStopMaxKm = 5.0;
    /** Minimum number of stops per delivery tour. */
    private int deliveryTourMinStops = 8;
    /** Maximum number of stops per delivery tour. */
    private int deliveryTourMaxStops = 15;
    /** Distance decay exponent for depot-to-first-stop selection. */
    private double deliveryDecayFirst = 3.0;
    /** Distance decay exponent for stop-to-stop selection. */
    private double deliveryDecaySubsequent = 1.0;
    /** Weight bonus for selecting POIs in the same zone as the current stop. */
    private double deliveryTourIntrazoneBonus = 3.0;

    // ═══ Vehicle Size Distribution ═══
    /** Probability of generating a heavy (10t) vehicle. */
    private double vehicleSizeLargeProb;
    /** Probability of generating a medium (4t) vehicle. */
    private double vehicleSizeMediumProb;
    /** Probability of generating a small (2t) vehicle. */
    private double vehicleSizeSmallProb;
    /** Cargo capacity in tons for heavy vehicles. */
    private double capacityLargeTons;
    /** Cargo capacity in tons for medium vehicles. */
    private double capacityMediumTons;
    /** Cargo capacity in tons for small vehicles. */
    private double capacitySmallTons;
    /** Cargo capacity in tons for light vehicles. */
    private double capacityLightTons;

    // ═══ Goods Type Distribution ═══
    /** Array of commodity type names (9 MFS categories). */
    private String[] goodsTypes;
    /** Probability distribution over commodity types. Must sum to 1.0. */
    private double[] goodsTypeProbabilities;

    // ═══ Zone Configuration ═══
    /** CSV filename for delivery zone definitions. */
    private String zonesFile;

    // ═══ Time Periods ═══
    /** Morning period start (seconds since midnight). */
    private long timePeriod1Start;
    /** Morning period end (seconds since midnight). */
    private long timePeriod1End;
    /** Afternoon period start (seconds since midnight). */
    private long timePeriod2Start;
    /** Afternoon period end (seconds since midnight). */
    private long timePeriod2End;

    // ═══ Attractiveness Coefficients ═══
    /** Attractiveness weight for warehouse/logistics facilities. */
    private double attractivenessBeta1;
    /** Attractiveness weight for retail establishments. */
    private double attractivenessBeta2;
    /** Attractiveness weight for construction sites. */
    private double attractivenessBeta3;
    /** Attractiveness weight for residential areas. */
    private double attractivenessBeta4;

    // ═══ Loading/Unloading Times ═══
    /** Average loading time in minutes. Gaussian distribution center. */
    private double loadingTimeAverage;
    /** Standard deviation of loading time in minutes. */
    private double loadingTimeStddev;
    /** Average unloading time in minutes. Gaussian distribution center. */
    private double unloadingTimeAverage;
    /** Standard deviation of unloading time in minutes. */
    private double unloadingTimeStddev;

    // ═══ Shift Configuration ═══
    /** Minimum shift duration in hours. */
    private int shiftDurationMin;
    /** Maximum shift duration in hours. */
    private int shiftDurationMax;
    /** Average shift duration in hours. Gaussian center. */
    private int shiftDurationAverage;
    /** Standard deviation of shift duration in hours. */
    private double shiftDurationStddev;
    /** First shift cohort start time (seconds since midnight). Typically 06:00. */
    private long shiftStart1Time;
    /** Probability of a truck belonging to the first shift cohort. */
    private double shiftStart1Probability;
    /** Second shift cohort start time (seconds since midnight). Typically 08:00. */
    private long shiftStart2Time;
    /** Probability of a truck belonging to the second shift cohort. */
    private double shiftStart2Probability;
    /** Third shift cohort start time (seconds since midnight). Typically 14:00. */
    private long shiftStart3Time;
    /** Probability of a truck belonging to the third shift cohort. */
    private double shiftStart3Probability;

    // ═══ Routing Configuration ═══
    /** Average truck travel speed in km/h. Used for travel time estimation. */
    private double routingAverageSpeedKmh;
    /** Break time between trips in seconds. */
    private double routingBreakTimeSeconds;

    // ═══ Geography ═══
    /** Minimum longitude of simulation bounding box. Recomputed from zone envelopes. */
    private double geographyMinLon;
    /** Maximum longitude of simulation bounding box. */
    private double geographyMaxLon;
    /** Minimum latitude of simulation bounding box. */
    private double geographyMinLat;
    /** Maximum latitude of simulation bounding box. */
    private double geographyMaxLat;
    /** Earth radius in km for Haversine distance calculations. */
    private double geographyEarthRadiusKm;

    // ═══ Distance Calculation ═══
    /** Manhattan factor: road distance = straight-line distance * this factor. */
    private double distanceManhattanFactor;

    // ═══ Empty Trip Configuration ═══
    /** Whether empty repositioning trips are enabled. */
    private boolean useEmptyTrips;
    /** Probability [0.0, 1.0] that a truck makes an empty trip between deliveries. */
    private double emptyTripProbability;
    /** Minimum distance (km) for an empty trip to be generated. Shorter trips skipped. */
    private double emptyTripThresholdKm;

    // ═══ Cargo Weight Generation ═══
    /** Mean cargo weight in tons (for Gaussian fallback). */
    private double cargoWeightMean;
    /** Standard deviation of cargo weight in tons (for Gaussian fallback). */
    private double cargoWeightStddev;
    /** Shape parameter for gamma-distributed cargo weight. */
    private double cargoWeightGammaShape;
    /** Scale parameter for gamma-distributed cargo weight. Mean = shape * scale. */
    private double cargoWeightGammaScale;

    // ═══ System ═══
    /** Random seed for deterministic reproducibility. */
    private long randomSeed;

    // ═══ Export Configuration ═══
    /** Output directory path for simulation results. Resolved via PathResolver. */
    private String outputDirectory;
    /** Date-time format string for CSV export timestamps. */
    private String dateTimeFormat;
    /** Time-only format string for CSV export. */
    private String timeFormat;
    
    /**
     * Private constructor (singleton pattern).
     */
    private TruckConfig() {
        properties = new Properties();
    }
    
    /**
     * Get singleton instance.
     */
    public static synchronized TruckConfig getInstance() {
        if (instance == null) {
            instance = new TruckConfig();
        }
        return instance;
    }
    
    /**
     * Load configuration from properties file.
     */
    public void loadFromFile(String configPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(configPath)) {
            properties.load(fis);
            parseProperties();
            System.out.println("[✓] Configuration loaded from: " + configPath);
        }
    }
    
    /**
     * Parse all properties from loaded file.
     */
    private void parseProperties() {
        // Fleet configuration (MLIT scaling support)
        registeredFleetSize = getIntProperty("truck.registered.fleet.size", 2530000);
        truckOperatingRate = getDoubleProperty("truck.operating.rate", 0.567);
        truckFleetSize = getIntProperty("truck.fleet.size",
            (int)(registeredFleetSize * truckOperatingRate));  // Default: calculate

        // MFS survey-day raw baselines (File 18 un-scaled observed totals)
        baselineSurveyTrucks = getIntProperty("truck.baseline.survey.trucks", 327108);
        baselineSurveyTons = getLongProperty("truck.baseline.survey.tons", 1726420L);
        
        // Truck types
        truckTypeDeliveryProb = getDoubleProperty("truck.type.delivery.prob", 0.50);
        truckTypeLongHaulProb = getDoubleProperty("truck.type.longhaul.prob", 0.30);
        truckTypeUrbanLogisticsProb = getDoubleProperty("truck.type.mixed.prob", 0.17);
        truckTypeDeliveryFamiliarRadiusKm = getDoubleProperty(
            "truck.type.delivery.familiar.radius.km", 8.0);

        // Vehicle type preferences (light, small, medium, heavy)
        deliveryVehicleProbs = parseVehicleProbs("truck.type.delivery.vehicle.probs",
            new double[]{0.25, 0.25, 0.25, 0.25});
        longHaulVehicleProbs = parseVehicleProbs("truck.type.longhaul.vehicle.probs",
            new double[]{0.25, 0.25, 0.25, 0.25});
        urbanVehicleProbs = parseVehicleProbs("truck.type.mixed.vehicle.probs",
            new double[]{0.0, 0.0, 1.0, 0.0});
        
        // Trips
        truckTripsAverage = getIntProperty("truck.trips.average", 8);
        truckTripsMin = getIntProperty("truck.trips.min", 5);
        truckTripsMax = getIntProperty("truck.trips.max", 15);
        truckTripsStddev = getDoubleProperty("truck.trips.stddev", 2.0);

        // Truck-type-specific trips (MFS-calibrated)
        truckTripsDelivery = getIntProperty("truck.trips.DELIVERY", 6);
        truckTripsMixed = getIntProperty("truck.trips.MIXED_OPERATION", 4);
        truckTripsLongHaul = getIntProperty("truck.trips.LONG_HAUL", 2);

        // V5.2: Delivery tour parameters
        deliveryFirstStopMaxKm = getDoubleProperty("delivery.first.stop.max.km", 15.0);
        deliveryStopToStopMaxKm = getDoubleProperty("delivery.stop.to.stop.max.km", 5.0);
        deliveryTourMinStops = getIntProperty("delivery.tour.min.stops", 8);
        deliveryTourMaxStops = getIntProperty("delivery.tour.max.stops", 15);
        deliveryDecayFirst = getDoubleProperty("delivery.distance.decay.first", 3.0);
        deliveryDecaySubsequent = getDoubleProperty("delivery.distance.decay.subsequent", 1.0);
        deliveryTourIntrazoneBonus = getDoubleProperty("delivery.tour.intrazone.bonus", 3.0);

        // Vehicle sizes
        vehicleSizeLargeProb = getDoubleProperty("vehicle.size.large.prob", 0.30);
        vehicleSizeMediumProb = getDoubleProperty("vehicle.size.medium.prob", 0.50);
        vehicleSizeSmallProb = getDoubleProperty("vehicle.size.small.prob", 0.20);
        capacityLargeTons = getDoubleProperty("vehicle.capacity.large.tons", 10.0);
        capacityMediumTons = getDoubleProperty("vehicle.capacity.medium.tons", 4.0);
        capacitySmallTons = getDoubleProperty("vehicle.capacity.small.tons", 2.0);
        capacityLightTons = getDoubleProperty("vehicle.capacity.light.tons", 1.5);
        
        // Goods types (simplified)
        goodsTypes = new String[] {
            "general_goods", "food", "chemicals", "machinery", "materials"
        };
        goodsTypeProbabilities = new double[] { 0.3, 0.2, 0.15, 0.2, 0.15 };
        
        // Zones
        zonesFile = getProperty("zones.file", "delivery_zones.csv");
        
        // Time periods
        timePeriod1Start = parseTimeToSeconds(getProperty("time.period.1.start", "06:00"));
        timePeriod1End = parseTimeToSeconds(getProperty("time.period.1.end", "12:00"));
        timePeriod2Start = parseTimeToSeconds(getProperty("time.period.2.start", "12:00"));
        timePeriod2End = parseTimeToSeconds(getProperty("time.period.2.end", "18:00"));
        
        // Attractiveness
        attractivenessBeta1 = getDoubleProperty("attractiveness.beta1.warehouses", 1.0);
        attractivenessBeta2 = getDoubleProperty("attractiveness.beta2.retail", 0.8);
        attractivenessBeta3 = getDoubleProperty("attractiveness.beta3.construction", 0.7);
        attractivenessBeta4 = getDoubleProperty("attractiveness.beta4.residential", 0.6);
        
        // Loading/unloading
        loadingTimeAverage = getDoubleProperty("loading.time.average.minutes", 30.0);
        loadingTimeStddev = getDoubleProperty("loading.time.stddev.minutes", 5.0);
        unloadingTimeAverage = getDoubleProperty("unloading.time.average.minutes", 20.0);
        unloadingTimeStddev = getDoubleProperty("unloading.time.stddev.minutes", 5.0);
        
        // Shifts
        shiftDurationMin = getIntProperty("shift.duration.min", 8);
        shiftDurationMax = getIntProperty("shift.duration.max", 12);
        shiftDurationAverage = getIntProperty("shift.duration.average", 10);
        shiftDurationStddev = getDoubleProperty("shift.duration.stddev", 1.0);
        
        shiftStart1Time = parseTimeToSeconds(getProperty("shift.start.1.time", "06:00"));
        shiftStart1Probability = getDoubleProperty("shift.start.1.probability", 0.50);
        shiftStart2Time = parseTimeToSeconds(getProperty("shift.start.2.time", "08:00"));
        shiftStart2Probability = getDoubleProperty("shift.start.2.probability", 0.30);
        shiftStart3Time = parseTimeToSeconds(getProperty("shift.start.3.time", "14:00"));
        shiftStart3Probability = getDoubleProperty("shift.start.3.probability", 0.20);
        
        // Routing
        routingAverageSpeedKmh = getDoubleProperty("routing.average.speed.kmh", 35.0);
        routingBreakTimeSeconds = getDoubleProperty("routing.break.time.seconds", 180.0);
        
        // Geography
        geographyMinLon = getDoubleProperty("geography.tokyo.min.lon", 139.3);
        geographyMaxLon = getDoubleProperty("geography.tokyo.max.lon", 140.0);
        geographyMinLat = getDoubleProperty("geography.tokyo.min.lat", 35.5);
        geographyMaxLat = getDoubleProperty("geography.tokyo.max.lat", 35.9);
        geographyEarthRadiusKm = getDoubleProperty("geography.earth.radius.km", 6371.0);
        
        // Distance
        distanceManhattanFactor = getDoubleProperty("distance.manhattan.factor", 1.4);
        
        // Empty trips
        useEmptyTrips = getBooleanProperty("use.empty.trips", true);
        emptyTripProbability = getDoubleProperty("empty.trip.probability", 0.35);
        emptyTripThresholdKm = getDoubleProperty("empty.trip.threshold.km", 0.5);
        
        // Cargo weight
        cargoWeightMean = getDoubleProperty("cargo.weight.mean.tons", 3.0);
        cargoWeightStddev = getDoubleProperty("cargo.weight.stddev.tons", 1.0);
        cargoWeightGammaShape = getDoubleProperty("cargo.weight.gamma.shape", 2.0);
        cargoWeightGammaScale = getDoubleProperty("cargo.weight.gamma.scale", 2.40);
        
        // Random
        randomSeed = getLongProperty("random.seed", 42L);
        
        // Export
        outputDirectory = getProperty("export.output.dir", "data/output/truck");
        dateTimeFormat = getProperty("export.datetime.format", "yyyy-MM-dd HH:mm:ss");
        timeFormat = getProperty("export.time.format", "HH:mm:ss");
    }
    
    /**
     * Get time period (0=Morning, 1=Afternoon, 2=Night).
     */
    public int getTimePeriod(long timeSeconds) {
        if (timeSeconds >= timePeriod1Start && timeSeconds < timePeriod1End) {
            return 0;  // Morning
        } else if (timeSeconds >= timePeriod2Start && timeSeconds < timePeriod2End) {
            return 1;  // Afternoon
        } else {
            return 2;  // Night
        }
    }
    
    // Property utility methods
    public String getProperty(String key) {
        return PathResolver.resolve(properties.getProperty(key));
    }

    public String getProperty(String key, String defaultValue) {
        return PathResolver.resolve(properties.getProperty(key, defaultValue));
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
    
    private double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }
    
    private long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }
    
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    /**
     * Parse time string (HH:MM) to seconds from midnight.
     */
    private long parseTimeToSeconds(String timeStr) {
        String[] parts = timeStr.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        return (hours * 3600L) + (minutes * 60L);
    }

    /**
     * Parse vehicle probability string "light,small,medium,heavy" into array.
     */
    private double[] parseVehicleProbs(String propertyKey, double[] defaultValues) {
        String probStr = getProperty(propertyKey, null);
        if (probStr == null) {
            return defaultValues;
        }

        String[] parts = probStr.split(",");
        if (parts.length != 4) {
            System.err.println("Warning: " + propertyKey + " must have 4 values (light,small,medium,heavy). Using defaults.");
            return defaultValues;
        }

        double[] probs = new double[4];
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            probs[i] = Double.parseDouble(parts[i].trim());
            sum += probs[i];
        }

        // Validate sum is close to 1.0
        if (Math.abs(sum - 1.0) > 0.01) {
            System.err.println("Warning: " + propertyKey + " probabilities sum to " + sum + ", not 1.0. Normalizing.");
            for (int i = 0; i < 4; i++) {
                probs[i] /= sum;
            }
        }

        return probs;
    }
    
    // Getters for all configuration parameters

    public SimulationMode getSimulationMode() {
        String mode = getProperty("simulation.mode", "DUAL");
        return SimulationMode.valueOf(mode.toUpperCase());
    }

    /**
     * Get the zones file for UNIFIED mode (134 zones: Kanto + Keihanshin detail).
     */
    public String getUnifiedZonesFile() {
        return getProperty("zones.file.unified", "zones/unified.csv");
    }

    /**
     * Get the GA targets file path (relative to config/truck/).
     * Defaults to flows/ga_targets.csv; overridden in unified/expanded configs.
     */
    public String getGaTargetsFile() {
        return getProperty("datasets.ga.targets.file", "flows/ga_targets.csv");
    }

    public int getTruckFleetSize() { return truckFleetSize; }
    public double getTruckOperatingRate() { return truckOperatingRate; }

    /**
     * Get total registered fleet size (MLIT).
     * @return total registered trucks
     */
    public int getRegisteredFleetSize() { return registeredFleetSize; }

    /**
     * MFS File 18 raw survey-day truck count (un-scaled). Used by
     * TruckDataExporter as the "validation target" baseline.
     * @return survey-day observed truck count
     */
    public int getBaselineSurveyTrucks() { return baselineSurveyTrucks; }

    /**
     * MFS File 18 raw survey-day total tons (un-scaled). Used by
     * TruckDataExporter as the "validation target" baseline.
     * @return survey-day observed total tons
     */
    public long getBaselineSurveyTons() { return baselineSurveyTons; }
    
    public double getTruckTypeDeliveryProb() { return truckTypeDeliveryProb; }
    public double getTruckTypeLongHaulProb() { return truckTypeLongHaulProb; }
    public double getTruckTypeUrbanLogisticsProb() { return truckTypeUrbanLogisticsProb; }
    public double getTruckTypeDeliveryFamiliarRadiusKm() { return truckTypeDeliveryFamiliarRadiusKm; }

    public double[] getDeliveryVehicleProbs() { return deliveryVehicleProbs; }
    public double[] getLongHaulVehicleProbs() { return longHaulVehicleProbs; }
    public double[] getUrbanVehicleProbs() { return urbanVehicleProbs; }
    
    public int getTruckTripsAverage() { return truckTripsAverage; }
    public int getTruckTripsMin() { return truckTripsMin; }
    public int getTruckTripsMax() { return truckTripsMax; }
    public double getTruckTripsStddev() { return truckTripsStddev; }
    public int getTruckTripsDelivery() { return truckTripsDelivery; }
    public int getTruckTripsMixed() { return truckTripsMixed; }
    public int getTruckTripsLongHaul() { return truckTripsLongHaul; }

    // V5.2: Delivery tour getters
    public double getDeliveryFirstStopMaxKm() { return deliveryFirstStopMaxKm; }
    public double getDeliveryStopToStopMaxKm() { return deliveryStopToStopMaxKm; }
    public int getDeliveryTourMinStops() { return deliveryTourMinStops; }
    public int getDeliveryTourMaxStops() { return deliveryTourMaxStops; }
    public double getDeliveryDecayFirst() { return deliveryDecayFirst; }
    public double getDeliveryDecaySubsequent() { return deliveryDecaySubsequent; }
    public double getDeliveryTourIntrazoneBonus() { return deliveryTourIntrazoneBonus; }

    /**
     * Get trips per day for a specific truck type.
     * Returns type-specific value if configured, otherwise falls back to average.
     */
    public int getTruckTripsForType(TruckType truckType) {
        switch (truckType) {
            case DELIVERY: return truckTripsDelivery;
            case MIXED_OPERATION: return truckTripsMixed;
            case LONG_HAUL: return truckTripsLongHaul;
            default: return truckTripsAverage;
        }
    }
    
    public double getVehicleSizeLargeProb() { return vehicleSizeLargeProb; }
    public double getVehicleSizeMediumProb() { return vehicleSizeMediumProb; }
    public double getVehicleSizeSmallProb() { return vehicleSizeSmallProb; }
    public double getCapacityLargeTons() { return capacityLargeTons; }
    public double getCapacityMediumTons() { return capacityMediumTons; }
    public double getCapacitySmallTons() { return capacitySmallTons; }
    public double getCapacityLightTons() { return capacityLightTons; }
    
    public String[] getGoodsTypes() { return goodsTypes; }
    public double[] getGoodsTypeProbabilities() { return goodsTypeProbabilities; }
    
    public String getZonesFile() { return zonesFile; }
    public void setZonesFile(String zonesFile) { this.zonesFile = zonesFile; }
    
    public long getTimePeriod1Start() { return timePeriod1Start; }
    public long getTimePeriod1End() { return timePeriod1End; }
    public long getTimePeriod2Start() { return timePeriod2Start; }
    public long getTimePeriod2End() { return timePeriod2End; }
    
    public double getAttractivenessBeta1() { return attractivenessBeta1; }
    public double getAttractivenessBeta2() { return attractivenessBeta2; }
    public double getAttractivenessBeta3() { return attractivenessBeta3; }
    public double getAttractivenessBeta4() { return attractivenessBeta4; }
    
    public double getLoadingTimeAverage() { return loadingTimeAverage; }
    public double getLoadingTimeStddev() { return loadingTimeStddev; }
    public double getUnloadingTimeAverage() { return unloadingTimeAverage; }
    public double getUnloadingTimeStddev() { return unloadingTimeStddev; }
    
    public int getShiftDurationMin() { return shiftDurationMin; }
    public int getShiftDurationMax() { return shiftDurationMax; }
    public int getShiftDurationAverage() { return shiftDurationAverage; }
    public double getShiftDurationStddev() { return shiftDurationStddev; }
    public long getShiftStart1Time() { return shiftStart1Time; }
    public double getShiftStart1Probability() { return shiftStart1Probability; }
    public long getShiftStart2Time() { return shiftStart2Time; }
    public double getShiftStart2Probability() { return shiftStart2Probability; }
    public long getShiftStart3Time() { return shiftStart3Time; }
    public double getShiftStart3Probability() { return shiftStart3Probability; }
    
    public double getRoutingAverageSpeedKmh() { return routingAverageSpeedKmh; }
    public double getRoutingBreakTimeSeconds() { return routingBreakTimeSeconds; }
    
    public double getGeographyMinLon() { return geographyMinLon; }
    public double getGeographyMaxLon() { return geographyMaxLon; }
    public double getGeographyMinLat() { return geographyMinLat; }
    public double getGeographyMaxLat() { return geographyMaxLat; }
    public double getGeographyEarthRadiusKm() { return geographyEarthRadiusKm; }
    
    public double getDistanceManhattanFactor() { return distanceManhattanFactor; }
    
    public boolean getUseEmptyTrips() { return useEmptyTrips; }
    public double getEmptyTripProbability() { return emptyTripProbability; }
    public double getEmptyTripThresholdKm() { return emptyTripThresholdKm; }
    public double getDeliveryRandomDestRatio() {
        return getDoubleProperty("delivery.random.dest.ratio", 0.50);
    }
    public double getLongHaulRandomDestRatio() {
        return getDoubleProperty("longhaul.random.dest.ratio", 0.35);
    }
    public double getMixedRandomDestRatio() {
        return getDoubleProperty("mixed.random.dest.ratio", 0.50);
    }
    /** Radius threshold (km) above which a zone is treated as rural/mountain for bypass purposes. */
    public double getRuralZoneRadiusThresholdKm() {
        return getDoubleProperty("zone.rural.radius.threshold.km", 20.0);
    }
    /** Bypass ratio for large rural/mountain zones — directs most trips to actual POIs. */
    public double getRuralZoneBypassRatio() {
        return getDoubleProperty("zone.rural.bypass.ratio", 0.10);
    }

    public double getCargoWeightMean() { return cargoWeightMean; }
    public double getCargoWeightStddev() { return cargoWeightStddev; }
    public double getCargoWeightGammaShape() { return cargoWeightGammaShape; }
    public double getCargoWeightGammaScale() { return cargoWeightGammaScale; }
    
    public long getRandomSeed() { return randomSeed; }
    
    public String getOutputDirectory() { return outputDirectory; }
    public String getDateTimeFormat() { return dateTimeFormat; }
    public String getTimeFormat() { return timeFormat; }

    // Metropolitan configuration getters
    public String getMetroMode() {
        return getProperty("metro.mode", "INTRA");
    }

    public String getMetroPrimary() {
        return getProperty("metro.primary", "TOKYO");
    }

    public String getMetroSecondary() {
        return getProperty("metro.secondary", "NAGOYA");
    }

    public double getInterMetroLongHaulProb() {
        return getDoubleProperty("metro.inter.longhaul.prob", 0.60);
    }

    public double getInterMetroUrbanProb() {
        return getDoubleProperty("metro.inter.urban.prob", 0.25);
    }

    public double getInterMetroDeliveryProb() {
        return getDoubleProperty("metro.inter.delivery.prob", 0.10);
    }

    public void setGeographyBounds(double minLon, double maxLon,
                                   double minLat, double maxLat) {
        geographyMinLon = minLon;
        geographyMaxLon = maxLon;
        geographyMinLat = minLat;
        geographyMaxLat = maxLat;
        properties.setProperty("geography.min.lon", String.valueOf(minLon));
        properties.setProperty("geography.max.lon", String.valueOf(maxLon));
        properties.setProperty("geography.min.lat", String.valueOf(minLat));
        properties.setProperty("geography.max.lat", String.valueOf(maxLat));
    }

    // MFS dataset configuration
    public String getDatasetsDirectory() {
        return getProperty("datasets.directory", "N:/PFLOW-test/mfs/en");
    }

    public boolean isMFSValidationEnabled() {
        return getBooleanProperty("validation.mfs.enabled", true);
    }

    public double getMFSTolerancePct() {
        return getDoubleProperty("validation.mfs.tolerance.pct", 10.0);
    }

    /**
     * Check if POI destinations should be used for INTRA trips.
     * @return true if POI destinations enabled
     */
    public boolean getUsePOIDestinations() {
        return getBooleanProperty("datasets.use.poi.destinations", true);
    }

    /**
     * Check if MFS O-D matrix should be loaded from CSV.
     * @return true if MFS O-D matrix enabled
     */
    public boolean getUseMFSODMatrix() {
        return getBooleanProperty("datasets.use.mfs.od.matrix", true);
    }

    /**
     * Check if Generation-Attraction Balance should be enforced.
     * @return true if G-A balance enabled
     */
    public boolean getUseGABalance() {
        return getBooleanProperty("datasets.use.ga.balance", true);
    }

    /**
     * Get POI mode (from_csv or auto_generate).
     * @return POI mode string
     */
    public String getPOIMode() {
        return properties.getProperty("datasets.poi.mode", "from_csv");
    }

    /**
     * Get establishment counts file path.
     * @return Establishment file name
     */
    public String getEstablishmentFile() {
        return properties.getProperty("datasets.poi.establishment.file", "establishment_counts.csv");
    }
}
