package traj;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Trajectory output writer for truck trips.
 * <p>
 * Output format matches the existing TruckTrajectoryGenerator output:
 * truck_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,goods_type,vehicle_size,link_id
 */
public class TruckTrajectoryWriter implements TrajectoryOutputWriter<TruckTripRecord> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getCsvHeader() {
        return "truck_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,goods_type,vehicle_size,link_id";
    }

    @Override
    public void writeWaypoint(BufferedWriter bw, TruckTripRecord rec, long unixMs,
                              double lon, double lat, String linkId) throws IOException {
        String datetime;
        synchronized (DATE_FORMAT) {
            datetime = DATE_FORMAT.format(new Date(unixMs));
        }
        bw.write(String.format("%d,%d,%d,%s,%.6f,%.6f,%d,%d,%s,%s,%s",
                rec.truckId, rec.tripId, unixMs, datetime, lon, lat,
                rec.getTransportMode(), rec.getPurposeCode(),
                rec.goodsType, rec.vehicleSize, linkId));
        bw.newLine();
    }
}
