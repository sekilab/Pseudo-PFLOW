package traj;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Strategy interface for writing vehicle-type-specific trajectory output columns.
 * <p>
 * Each vehicle type has different metadata columns in its trajectory CSV.
 * The routing engine calls {@link #writeWaypoint} for each route node,
 * passing the vehicle-specific record so the writer can format the output.
 *
 * @param <R> concrete trip record type
 */
public interface TrajectoryOutputWriter<R extends VehicleTripRecord> {

    /**
     * Return the CSV header line (without newline).
     */
    String getCsvHeader();

    /**
     * Write one waypoint row to the output CSV.
     *
     * @param bw     buffered writer
     * @param record the trip record (contains vehicle-specific metadata)
     * @param unixMs unix timestamp in milliseconds for this waypoint
     * @param lon    longitude of this waypoint
     * @param lat    latitude of this waypoint
     * @param linkId road network link ID (or "DIRECT" for fallback)
     */
    void writeWaypoint(BufferedWriter bw, R record, long unixMs,
                       double lon, double lat, String linkId) throws IOException;
}
