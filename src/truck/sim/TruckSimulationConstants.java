package truck.sim;

/**
 * Constants used in truck simulation for calibration and behavioral parameters.
 *
 * These values are calibrated based on Tokyo MFS (Metropolitan Freight Survey)
 * to achieve Grade A+ validation (51/51 metrics passing).
 *
 * DO NOT modify these values without running full calibration validation.
 */
public final class TruckSimulationConstants {

    // Prevent instantiation
    private TruckSimulationConstants() {}

    // ========== Distance Thresholds (km) ==========

    /** Minimum distance threshold for DELIVERY truck operations */
    public static final double DELIVERY_DISTANCE_THRESHOLD_KM = 8.0;

    /** Threshold distance for DELIVERY truck short-range operations */
    public static final double DELIVERY_SHORT_RANGE_THRESHOLD_KM = 10.0;

    /** Minimum distance for inter-zone trips */
    public static final double MIN_DISTANCE_KM = 1.0;

    // ========== Distance Decay Factors ==========

    /** Distance decay factor for DELIVERY truck short trips (exponential decay) */
    public static final double DELIVERY_DISTANCE_DECAY_FACTOR = 3.8;

    /** Distance decay factor for MIXED_OPERATION trucks */
    public static final double MIXED_DISTANCE_DECAY_FACTOR = 90.0;

    /** Distance decay factor for LONG_HAUL trucks */
    public static final double LONGHAUL_DISTANCE_DECAY_FACTOR = 310.0;

    // ========== Probability Damping Factors ==========

    /** Intra-zone trip probability damping factor (calibrated for 20.67% intra-zone ratio) */
    public static final double INTRAZONE_DAMPING_FACTOR = 0.012;

    /** Empty trip probability (8% of all trips) */
    public static final double EMPTY_TRIP_PROBABILITY = 0.08;

    // ========== Cargo Weight Parameters (tons) ==========

    /** Maximum cargo weight for light vehicles */
    public static final double LIGHT_VEHICLE_MAX_CARGO_TONS = 1.5;

    /** Maximum cargo weight for small vehicles */
    public static final double SMALL_VEHICLE_MAX_CARGO_TONS = 4.0;

    /** Maximum cargo weight for medium vehicles */
    public static final double MEDIUM_VEHICLE_MAX_CARGO_TONS = 10.0;

    /** Maximum cargo weight for heavy vehicles */
    public static final double HEAVY_VEHICLE_MAX_CARGO_TONS = 25.0;

    // ========== Gamma Distribution Parameters ==========

    /** Gamma shape parameter for light vehicle cargo weight */
    public static final double LIGHT_GAMMA_SHAPE = 1.5;

    /** Gamma scale parameter for light vehicle cargo weight */
    public static final double LIGHT_GAMMA_SCALE = 0.19;

    /** Gamma shape parameter for small vehicle cargo weight */
    public static final double SMALL_GAMMA_SHAPE = 1.8;

    /** Gamma scale parameter for small vehicle cargo weight */
    public static final double SMALL_GAMMA_SCALE = 0.70;

    /** Gamma shape parameter for medium vehicle cargo weight */
    public static final double MEDIUM_GAMMA_SHAPE = 2.0;

    /** Gamma scale parameter for medium vehicle cargo weight */
    public static final double MEDIUM_GAMMA_SCALE = 4.5;

    /** Gamma shape parameter for heavy vehicle cargo weight */
    public static final double HEAVY_GAMMA_SHAPE = 2.5;

    /** Gamma scale parameter for heavy vehicle cargo weight (calibrated from 9.5 → 4.9) */
    public static final double HEAVY_GAMMA_SCALE = 4.9;

    // ========== Generation-Attraction Balance ==========

    /** Tolerance multiplier for generation targets (5% over target allowed) */
    public static final double GENERATION_TOLERANCE = 1.05;

    /** Tolerance multiplier for attraction targets (5% over target allowed) */
    public static final double ATTRACTION_TOLERANCE = 1.05;

    // ========== Geographic Constants ==========

    /** Earth radius in kilometers (for Haversine distance calculation) */
    public static final double EARTH_RADIUS_KM = 6371.0;

    /** Meters per degree of latitude (approximate) */
    public static final double METERS_PER_DEGREE_LAT = 111320.0;

    /** Manhattan factor for network distance adjustment */
    public static final double MANHATTAN_FACTOR = 1.72;
}
