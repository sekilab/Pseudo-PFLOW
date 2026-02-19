package truck.sim;

/**
 * Facility types for delivery zones.
 * Based on Tokyo MFS trip generation analysis (Files 22-25).
 *
 * Each facility type has different trip generation characteristics:
 * - LOGISTICS_HUB: Major distribution centers, ports (58 trips/day)
 * - LARGE_DISTRIBUTION: Regional distribution centers (29 trips/day)
 * - MEDIUM_WAREHOUSE: Standard warehouses (9.7 trips/day)
 * - SMALL_RETAIL: Retail shops, small businesses (4.3 trips/day)
 * - INDUSTRIAL: Factories, manufacturing sites (35 trips/day)
 * - CONSTRUCTION_SITE: Active building zones (15 trips/day)
 * - RESIDENTIAL: Primarily receiving deliveries (low generation)
 * - MIXED: Combination of facility types (average rate)
 *
 * @version 2.0
 */
public enum FacilityType {

    LOGISTICS_HUB("Logistics Hub", 58.0, 1.0, 0.3),
    LARGE_DISTRIBUTION("Large Distribution", 29.0, 0.8, 0.4),
    MEDIUM_WAREHOUSE("Medium Warehouse", 9.7, 0.7, 0.3),
    SMALL_RETAIL("Small Retail", 4.3, 0.2, 0.8),
    INDUSTRIAL("Industrial Site", 35.0, 0.6, 0.2),
    CONSTRUCTION_SITE("Construction Site", 15.0, 0.4, 0.1),
    RESIDENTIAL("Residential", 2.0, 0.1, 0.2),
    MIXED("Mixed Use", 8.0, 0.5, 0.5);

    private final String description;
    private final double baseTripsPerDay;      // Base trip generation rate
    private final double warehouseBias;         // 0.0-1.0, preference for warehouse deliveries
    private final double retailBias;            // 0.0-1.0, preference for retail deliveries

    FacilityType(String description, double baseTripsPerDay,
                 double warehouseBias, double retailBias) {
        this.description = description;
        this.baseTripsPerDay = baseTripsPerDay;
        this.warehouseBias = warehouseBias;
        this.retailBias = retailBias;
    }

    public String getDescription() {
        return description;
    }

    public double getBaseTripsPerDay() {
        return baseTripsPerDay;
    }

    public double getWarehouseBias() {
        return warehouseBias;
    }

    public double getRetailBias() {
        return retailBias;
    }

    /**
     * Get trip generation multiplier relative to baseline (9.7 trips/day).
     */
    public double getTripMultiplier() {
        return baseTripsPerDay / 9.7;
    }

    /**
     * Infer facility type from zone characteristics.
     */
    public static FacilityType inferFromWeights(double warehousesWeight,
                                                 double retailWeight,
                                                 double constructionWeight,
                                                 double residentialWeight) {
        // Logistics hub: Very high warehouses
        if (warehousesWeight >= 0.9) return LOGISTICS_HUB;

        // Construction: High construction
        if (constructionWeight >= 0.6) return CONSTRUCTION_SITE;

        // Industrial: High warehouse + some construction
        if (warehousesWeight >= 0.6 && constructionWeight >= 0.3) return INDUSTRIAL;

        // Large distribution: High warehouses
        if (warehousesWeight >= 0.7) return LARGE_DISTRIBUTION;

        // Medium warehouse: Moderate warehouses
        if (warehousesWeight >= 0.5) return MEDIUM_WAREHOUSE;

        // Small retail: High retail
        if (retailWeight >= 0.7) return SMALL_RETAIL;

        // Residential: High residential
        if (residentialWeight >= 0.6) return RESIDENTIAL;

        // Default: Mixed use
        return MIXED;
    }

    @Override
    public String toString() {
        return description;
    }
}