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
        DUAL
    }

    private static TruckConfig instance;
    private Properties properties;
    
    // Fleet configuration (MLIT alignment)
    private int truckFleetSize;           // Daily active trucks (after operating rate)
    private double truckOperatingRate;    // MLIT 実働率 (reference only, not applied)
    private int registeredFleetSize;      // Total registered fleet (MLIT)
    
    // Truck type distribution
    private double truckTypeDeliveryProb;
    private double truckTypeLongHaulProb;
    private double truckTypeUrbanLogisticsProb;
    private double truckTypeDeliveryFamiliarRadiusKm;

    // Vehicle type preferences by truck type [light, small, medium, heavy]
    private double[] deliveryVehicleProbs;
    private double[] longHaulVehicleProbs;
    private double[] urbanVehicleProbs;
    
    // Trip configuration
    private int truckTripsAverage;
    private int truckTripsMin;
    private int truckTripsMax;
    private double truckTripsStddev;
    private int truckTripsDelivery;
    private int truckTripsMixed;
    private int truckTripsLongHaul;

    // V5.2: Delivery tour parameters — multi-stop tour model
    private double deliveryFirstStopMaxKm = 15.0;      // Depot → first POI max distance
    private double deliveryStopToStopMaxKm = 5.0;       // Between consecutive POIs
    private int deliveryTourMinStops = 8;                // Min stops per tour
    private int deliveryTourMaxStops = 15;               // Max stops per tour
    private double deliveryDecayFirst = 3.0;             // Distance decay for first trip
    private double deliveryDecaySubsequent = 1.0;        // Distance decay for stop-to-stop
    private double deliveryTourIntrazoneBonus = 3.0;     // Intra-zone bonus for tour stops (replaces damping)
    
    // Vehicle size distribution
    private double vehicleSizeLargeProb;
    private double vehicleSizeMediumProb;
    private double vehicleSizeSmallProb;
    private double capacityLargeTons;
    private double capacityMediumTons;
    private double capacitySmallTons;
    private double capacityLightTons;
    
    // Goods type distribution
    private String[] goodsTypes;
    private double[] goodsTypeProbabilities;
    
    // Zone configuration
    private String zonesFile;
    
    // Time periods
    private long timePeriod1Start;
    private long timePeriod1End;
    private long timePeriod2Start;
    private long timePeriod2End;
    
    // Attractiveness coefficients
    private double attractivenessBeta1;
    private double attractivenessBeta2;
    private double attractivenessBeta3;
    private double attractivenessBeta4;
    
    // Loading/unloading times
    private double loadingTimeAverage;
    private double loadingTimeStddev;
    private double unloadingTimeAverage;
    private double unloadingTimeStddev;
    
    // Shift configuration
    private int shiftDurationMin;
    private int shiftDurationMax;
    private int shiftDurationAverage;
    private double shiftDurationStddev;
    private long shiftStart1Time;
    private double shiftStart1Probability;
    private long shiftStart2Time;
    private double shiftStart2Probability;
    private long shiftStart3Time;
    private double shiftStart3Probability;
    
    // Routing configuration
    private double routingAverageSpeedKmh;
    private double routingBreakTimeSeconds;
    
    // Geography
    private double geographyMinLon;
    private double geographyMaxLon;
    private double geographyMinLat;
    private double geographyMaxLat;
    private double geographyEarthRadiusKm;
    
    // Distance calculation
    private double distanceManhattanFactor;
    
    // Empty trip configuration
    private boolean useEmptyTrips;
    private double emptyTripProbability;
    private double emptyTripThresholdKm;
    
    // Cargo weight generation
    private double cargoWeightMean;
    private double cargoWeightStddev;
    private double cargoWeightGammaShape;
    private double cargoWeightGammaScale;
    
    // Random seed
    private long randomSeed;
    
    // Export configuration
    private String outputDirectory;
    private String dateTimeFormat;
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

    public int getTruckFleetSize() { return truckFleetSize; }
    public double getTruckOperatingRate() { return truckOperatingRate; }

    /**
     * Get total registered fleet size (MLIT).
     * @return total registered trucks
     */
    public int getRegisteredFleetSize() { return registeredFleetSize; }
    
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
