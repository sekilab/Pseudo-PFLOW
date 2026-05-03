package traj;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Trajectory output writer for truck trips.
 * <p>
 * Output format matches the existing TruckTrajectoryGenerator output plus the
 * trailing {@code is_fallback} flag introduced with the typed fallback counters:
 * truck_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,goods_type,vehicle_size,link_id,is_fallback
 */
public class TruckTrajectoryWriter implements TrajectoryOutputWriter<TruckTripRecord> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getCsvHeader() {
        return "truck_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,goods_type,vehicle_size,link_id,is_fallback";
    }

    @Override
    public void writeWaypoint(BufferedWriter bw, TruckTripRecord rec, long unixMs,
                              double lon, double lat, String linkId,
                              boolean isFallback) throws IOException {
        String datetime;
        synchronized (DATE_FORMAT) {
            datetime = DATE_FORMAT.format(new Date(unixMs));
        }
        bw.write(String.format("%d,%d,%d,%s,%.6f,%.6f,%d,%d,%s,%s,%s,%d",
                rec.truckId, rec.tripId, unixMs, datetime, lon, lat,
                rec.getTransportMode(), rec.getPurposeCode(),
                rec.goodsType, rec.vehicleSize, linkId, isFallback ? 1 : 0));
        bw.newLine();
    }
}
