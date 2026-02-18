package taxi.sim;

/**
 * Enumeration of taxi agent states for behavior modeling
 *
 * CURRENT IMPLEMENTATION (V3.1):
 * - Only IDLE state is actively used in the simulation
 * - Other states are reserved for future state machine implementation
 *
 * This represents the complete state machine for a taxi agent:
 *
 * IDLE           - Taxi is waiting at depot or taxi stand [ACTIVE]
 * SEARCHING      - Actively looking for passengers (cruising) [RESERVED]
 * EN_ROUTE_TO_PICKUP - Heading to pick up assigned passenger [RESERVED]
 * OCCUPIED       - Passenger onboard, traveling to destination [RESERVED]
 * RETURNING_HOME - Shift ending, returning to depot/garage [RESERVED]
 * OFF_DUTY       - Not operating (between shifts, maintenance, etc.) [RESERVED]
 */
public enum TaxiStatus {
    /**
     * Taxi is idle, waiting for trip assignment
     * Typically at: depot, taxi stand, waiting area
     * STATUS: ACTIVE - currently used in simulation
     */
    IDLE,

    /**
     * Taxi is actively searching for passengers
     * Cruising behavior, looking for flag-downs
     * STATUS: RESERVED - not yet implemented
     */
    SEARCHING,

    /**
     * Taxi has been assigned a trip and is traveling to pickup location
     * STATUS: RESERVED - not yet implemented
     */
    EN_ROUTE_TO_PICKUP,

    /**
     * Passenger is onboard, taxi is traveling to destination
     * STATUS: RESERVED - not yet implemented
     */
    OCCUPIED,

    /**
     * Shift is ending, taxi is returning to home depot/garage
     * STATUS: RESERVED - not yet implemented
     */
    RETURNING_HOME,

    /**
     * Taxi is not in operation (maintenance, between shifts, etc.)
     * STATUS: RESERVED - not yet implemented
     */
    OFF_DUTY
}
