package taxi.sim;

/**
 * Enumeration of taxi agent states.
 *
 * <p>The legacy v6.0 engine uses only {@link #IDLE} (all states previously
 * reserved but unused, deprecated 2026-04-22).
 *
 * <p>The v7.0 shift-time engine (Phase 1+ of the rewrite) introduces five
 * active states matching the architecture in DESIGN.md §2.3:
 *
 * <pre>
 *   OFF_DUTY → DUAL_MODE ↔ AT_STAND → OCCUPIED → DUAL_MODE | AT_STAND
 *                                  → RETURNING_HOME → OFF_DUTY
 * </pre>
 *
 * <p>DUAL_MODE represents the realistic situation where a moving taxi is
 * simultaneously available to street-hail customers and listening for app
 * dispatches. The pickup mode (street vs app) is sampled from the taxi's
 * type-specific mode-mix at the end of each empty leg.
 */
public enum TaxiStatus {
    /** Taxi is idle, waiting for trip assignment. Only state used by the legacy v6.0 engine. */
    IDLE,

    /** v7.0: Taxi has not yet started its shift, or has finished and returned home. */
    OFF_DUTY,

    /**
     * v7.0: Taxi is moving (cruising) AND available for both street-hail and app-dispatch
     * pickups. The next pickup mode is sampled from the taxi's type mode-mix when an
     * empty leg ends. Aligns with industry observation that contemporary Tokyo taxis
     * are simultaneously addressable via multiple channels.
     */
    DUAL_MODE,

    /**
     * v7.0: Taxi is parked at a stand (airport / major station / entertainment district),
     * waiting in FIFO queue for the next passenger. Wait time sampled from
     * Exp(λ) with stand-type-dependent mean (8 min airport, 12 min station, 6 min
     * entertainment). RIDE_HAIL_PRHS taxis never enter this state.
     */
    AT_STAND,

    /** v7.0: Taxi is carrying a passenger from pickup to dropoff. */
    OCCUPIED,

    /**
     * v7.0: Taxi has finished its shift and is returning to home location.
     * Records a single empty leg from current position to home; no further
     * trips are generated.
     */
    RETURNING_HOME
}
