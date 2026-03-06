package traj;

/**
 * Common interface for all vehicle trip records used in trajectory generation.
 * <p>
 * Provides the minimal set of fields needed by the A* routing engine:
 * trip identity, vehicle identity, departure time, origin/destination coordinates,
 * distance, and vehicle-type-specific transport mode and purpose codes.
 * <p>
 * Vehicle-specific metadata (e.g., goods_type for trucks, fare_yen for taxis)
 * lives in the concrete implementations and is accessed by the corresponding
 * {@link TrajectoryOutputWriter} via downcasting.
 *
 * @see TruckTripRecord
 * @see TaxiTripRecord
 */
public interface VehicleTripRecord {

    /** Unique trip identifier. */
    long getTripId();

    /** Vehicle/agent identifier (truck_id, taxi_id, etc.). */
    int getVehicleId();

    /** Departure time in seconds from epoch day 0 (sim_day * 86400 + starttime). */
    long getDepTime();

    /** Origin longitude (WGS84). */
    double getOriginLon();

    /** Origin latitude (WGS84). */
    double getOriginLat();

    /** Destination longitude (WGS84). */
    double getDestLon();

    /** Destination latitude (WGS84). */
    double getDestLat();

    /** Trip distance in kilometers (used for fallback speed estimation). */
    double getDistanceKm();

    /** Transport mode code for output CSV (e.g., 9=truck, 8=taxi). */
    int getTransportMode();

    /** Purpose code for output CSV (vehicle-type-specific). */
    int getPurposeCode();
}
