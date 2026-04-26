package traj;

/**
 * Categorizes why a trip emits a 2-point direct trajectory (writeDirect)
 * instead of a full A*-routed sequence.
 * <p>
 * Each value corresponds to one decision branch in VehicleTrajectoryGenerator.
 * Tallied separately so dashboards can distinguish intentional bypass from
 * genuine routing failure — the output CSV also carries an is_fallback flag
 * on every waypoint row.
 */
public enum FallbackReason {
    /** Origin or destination could not be snapped to a network node. */
    NO_SNAP,
    /** Trip distance below bypass threshold — A* skipped intentionally. */
    SHORT_TRIP_BYPASS,
    /** A* returned no route (disconnected network or no valid path). */
    NO_ROUTE
}
