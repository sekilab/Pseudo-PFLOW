package truck.sim;

/**
 * Enumeration of truck agent types with vehicle size constraints.
 *
 * Version 2.0 - Redefined types with strict vehicle size enforcement
 *
 * @author Truck ABM Framework
 * @version 2.0
 */
public enum TruckType {
    /**
     * DELIVERY type (light vehicles only: <2t, 2-4t)
     * - Local delivery operations
     * - Urban areas, retail/residential destinations
     * - Short-distance trips within familiar area
     * - Vehicle constraints: light (<2t) OR small (2-4t) ONLY
     */
    DELIVERY,

    /**
     * LONG_HAUL type (heavy and medium trucks: 4-10t, 10t+)
     * - Inter-regional freight transportation
     * - Long-distance highway routes
     * - Factory/port to distribution center flows
     * - Vehicle constraints: heavy (10t+) OR medium (4-10t)
     */
    LONG_HAUL,

    /**
     * MIXED_OPERATION type (medium and small trucks: 2-4t, 4-10t)
     * - Flexible urban and regional operations
     * - Both intra and inter-metropolitan capable
     * - Distribution center to retail flows
     * - Vehicle constraints: medium (4-10t) OR small (2-4t)
     */
    MIXED_OPERATION;

    /**
     * Get allowed vehicle sizes for this truck type.
     * @return Array of allowed vehicle size strings
     */
    public String[] getAllowedVehicleSizes() {
        switch (this) {
            case DELIVERY:
                return new String[]{"light", "small"};
            case LONG_HAUL:
                return new String[]{"heavy", "medium"};
            case MIXED_OPERATION:
                return new String[]{"medium", "small"};
            default:
                return new String[]{"medium"};
        }
    }

    /**
     * Check if vehicle size is allowed for this truck type.
     * @param vehicleSize Size to check ("light", "small", "medium", "heavy")
     * @return true if size is allowed for this type
     */
    public boolean isVehicleSizeAllowed(String vehicleSize) {
        for (String allowed : getAllowedVehicleSizes()) {
            if (allowed.equals(vehicleSize)) {
                return true;
            }
        }
        return false;
    }
}
