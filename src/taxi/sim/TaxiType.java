package taxi.sim;

/**
 * Enumeration of taxi agent types for heterogeneous behavior modeling
 *
 * Three types of taxi agents with different operational strategies:
 * - LOCAL: Operate within a familiar area/zone (majority)
 * - CITYWIDE: Roam across the entire city with larger cruising range (minority)
 * - HUB: Concentrate around major hubs like airports and stations (minority)
 *
 * This heterogeneity creates more realistic taxi behavior patterns
 * compared to uniform random-walk models.
 */
public enum TaxiType {
    /**
     * Local type (majority): Mostly operate within a "familiar area"
     * - Defined by a fixed radius from home base
     * - Prefer pickups and dropoffs within their familiar zone
     * - Represent neighborhood/district-based taxi operations
     */
    LOCAL,

    /**
     * Citywide type (minority): Roam across the whole city
     * - No spatial restrictions
     * - Larger cruising range
     * - Willing to take trips anywhere
     */
    CITYWIDE,

    /**
     * Hub type (minority): Concentrate around airports and major stations
     * - Focus on high-traffic transportation hubs
     * - Airport shuttles, station taxi stands
     * - Follow hub-specific demand patterns
     */
    HUB
}
