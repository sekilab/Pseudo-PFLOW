package truck.sim;

/**
 * Enumeration of truck operational status states.
 * 
 * @author Truck ABM Framework
 * @version 1.0
 */
public enum TruckStatus {
    /**
     * IDLE - Truck is available, waiting for next delivery
     */
    IDLE,
    
    /**
     * LOADING - Truck is loading cargo at origin
     */
    LOADING,
    
    /**
     * IN_TRANSIT - Truck is en route with cargo
     */
    IN_TRANSIT,
    
    /**
     * UNLOADING - Truck is unloading cargo at destination
     */
    UNLOADING,
    
    /**
     * EMPTY_RUNNING - Truck is repositioning without cargo
     */
    EMPTY_RUNNING,
    
    /**
     * OFF_SHIFT - Truck has completed shift
     */
    OFF_SHIFT,
    
    /**
     * MAINTENANCE - Truck is under maintenance (optional)
     */
    MAINTENANCE
}
