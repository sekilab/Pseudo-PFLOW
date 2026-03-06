package truck.sim;

/**
 * Constants for Point of Interest (POI) generation and management.
 *
 * These values control the density and distribution of POIs (Points of Interest)
 * such as logistic centers, retail shops, and shopping malls across zones.
 */
public final class POIManagerConstants {

    // Prevent instantiation
    private POIManagerConstants() {}

    // ========== POI Generation Ratios ==========

    /** Number of logistic establishments per generated logistic center POI */
    public static final int LOGISTIC_POI_RATIO = 10;

    /** Number of retail establishments per generated retail shop POI */
    public static final int RETAIL_POI_RATIO = 20;

    /** Retail establishment threshold for generating shopping mall POI */
    public static final int HIGH_RETAIL_THRESHOLD = 100;

    /** Number of industrial establishments per generated industrial site POI */
    public static final int INDUSTRIAL_POI_RATIO = 15;

    // ========== Geographic Constants ==========

    /** Meters per degree of latitude (for point distribution within zones) */
    public static final double METERS_PER_DEGREE_LAT = 111320.0;

    /** Standard deviation multiplier for random point generation (in degrees) */
    public static final double POINT_DISTRIBUTION_STDDEV = 0.01;

    // ========== POI Selection Weights ==========

    /** Default weight for POI selection when no specific weight is defined */
    public static final double DEFAULT_POI_WEIGHT = 1.0;

    /** Weight multiplier for POIs in high-density zones */
    public static final double HIGH_DENSITY_WEIGHT_MULTIPLIER = 1.5;
}
