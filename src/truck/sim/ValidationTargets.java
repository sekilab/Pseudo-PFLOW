package truck.sim;

/**
 * MFS Survey Baseline Targets for validation.
 *
 * These values are derived from Tokyo Metropolitan Freight Survey (MFS)
 * File 18 (H25/2013 survey data) and serve as validation targets.
 *
 * Source: Tokyo MFS データ集 平成25年度 (2013)
 */
public final class ValidationTargets {

    // Prevent instantiation
    private ValidationTargets() {}

    // ========== Truck Type Distribution (%) ==========

    /** Target percentage of DELIVERY trucks in fleet */
    public static final double DELIVERY_PERCENT = 37.0;

    /** Target percentage of LONG_HAUL trucks in fleet */
    public static final double LONGHAUL_PERCENT = 34.5;

    /** Target percentage of MIXED_OPERATION trucks in fleet */
    public static final double MIXED_PERCENT = 28.5;

    // ========== Vehicle Size Distribution (%) ==========

    /** Target percentage of heavy vehicles (10+ tons) */
    public static final double HEAVY_PERCENT = 26.07;

    /** Target percentage of medium vehicles (4-10 tons) */
    public static final double MEDIUM_PERCENT = 16.86;

    /** Target percentage of small vehicles (2-4 tons) */
    public static final double SMALL_PERCENT = 28.65;

    /** Target percentage of light vehicles (<2 tons) */
    public static final double LIGHT_PERCENT = 28.41;

    // ========== Fleet Size ==========

    /** Total number of trucks in Tokyo metropolitan area */
    public static final int TOTAL_TRUCKS = 327_108;

    /** Target number of heavy trucks */
    public static final int HEAVY_TRUCKS = 85_278;

    /** Target number of medium trucks */
    public static final int MEDIUM_TRUCKS = 55_165;

    /** Target number of small trucks */
    public static final int SMALL_TRUCKS = 93_720;

    /** Target number of light trucks */
    public static final int LIGHT_TRUCKS = 92_945;

    // ========== Trip Volume Targets ==========

    /** Target total vehicle movements per day */
    public static final int TOTAL_MOVEMENTS = 607_657;

    /** Target total logistics volume (tons per day) */
    public static final double TOTAL_LOGISTICS_VOLUME = 1_726_420.0;

    // ========== Average Cargo Weight (tons/truck/day) ==========

    /** Target cargo weight per heavy truck per day */
    public static final double HEAVY_TONS_PER_TRUCK = 11.46;

    /** Target cargo weight per medium truck per day */
    public static final double MEDIUM_TONS_PER_TRUCK = 7.64;

    /** Target cargo weight per small truck per day */
    public static final double SMALL_TONS_PER_TRUCK = 2.72;

    /** Target cargo weight per light truck per day */
    public static final double LIGHT_TONS_PER_TRUCK = 0.78;

    /** Target average cargo per delivery trip */
    public static final double AVG_CARGO_PER_DELIVERY = 5.28;

    // ========== Distance Targets (km) ==========

    /** Target average distance per movement */
    public static final double AVG_DISTANCE_PER_MOVEMENT = 91.18;

    /** Target total distance covered per day */
    public static final double TOTAL_DISTANCE = 55_403_820.0;

    /** Target average distance for DELIVERY trucks */
    public static final double DELIVERY_AVG_DISTANCE = 11.12;

    /** Target average distance for LONG_HAUL trucks */
    public static final double LONGHAUL_AVG_DISTANCE = 260.29;

    /** Target average distance for MIXED_OPERATION trucks */
    public static final double MIXED_AVG_DISTANCE = 55.04;

    // ========== Operational Targets ==========

    /** Target empty trip ratio (percentage) */
    public static final double EMPTY_TRIP_RATIO = 7.5;

    /** Target average movements per truck per day */
    public static final double AVG_MOVEMENTS_PER_TRUCK = 1.86;

    /** Target intra-zone trip ratio (percentage) */
    public static final double INTRAZONE_TRIP_RATIO = 20.67;

    /** Target weight-limited shipment percentage */
    public static final double WEIGHT_LIMITED_SHIPMENTS = 35.9;

    // ========== Commodity Mix Targets (%) ==========

    /** Target percentage for daily necessities commodity */
    public static final double DAILY_NECESSITIES_PERCENT = 27.0;

    /** Target percentage for agricultural/food commodity */
    public static final double AGRICULTURAL_FOOD_PERCENT = 22.0;

    /** Target percentage for publications commodity */
    public static final double PUBLICATIONS_PERCENT = 11.0;

    /** Target percentage for light industrial commodity */
    public static final double LIGHT_INDUSTRIAL_PERCENT = 16.0;

    /** Target percentage for machinery commodity */
    public static final double MACHINERY_PERCENT = 7.0;

    /** Target percentage for forestry/mineral commodity */
    public static final double FORESTRY_MINERAL_PERCENT = 7.0;

    /** Target percentage for metal products commodity */
    public static final double METAL_PRODUCTS_PERCENT = 5.0;

    /** Target percentage for ceramic/chemical commodity */
    public static final double CERAMIC_CHEMICAL_PERCENT = 3.0;

    /** Target percentage for special products commodity */
    public static final double SPECIAL_PRODUCTS_PERCENT = 2.0;

    // ========== Validation Tolerances (%) ==========

    /** Standard tolerance for most metrics */
    public static final double STANDARD_TOLERANCE = 15.0;

    /** Strict tolerance for critical metrics */
    public static final double STRICT_TOLERANCE = 10.0;

    /** Relaxed tolerance for distance metrics */
    public static final double DISTANCE_TOLERANCE = 25.0;

    /** Generous tolerance for commodity mix */
    public static final double COMMODITY_TOLERANCE = 35.0;

    /** Very strict tolerance for balance metrics */
    public static final double BALANCE_TOLERANCE = 10.0;

    // ========== Network Targets ==========

    /** Number of MFS survey zones */
    public static final int MFS_SURVEY_ZONES = 73;

    /** Target G-A balance ratio (should be 1.0) */
    public static final double GA_BALANCE_RATIO = 1.0;

    /** Target G-A generation balance (percentage of target) */
    public static final double GA_GENERATION_BALANCE = 100.0;

    /** Target G-A attraction balance (percentage of target) */
    public static final double GA_ATTRACTION_BALANCE = 100.0;
}
