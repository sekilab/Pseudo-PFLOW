package traj;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Trajectory output writer for taxi trips.
 * <p>
 * Output format:
 * taxi_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,passenger_in,fare_yen,is_night_trip,link_id,is_fallback
 */
public class TaxiTrajectoryWriter implements TrajectoryOutputWriter<TaxiTripRecord> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getCsvHeader() {
        return "taxi_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,passenger_in,fare_yen,is_night_trip,link_id,is_fallback";
    }

    @Override
    public void writeWaypoint(BufferedWriter bw, TaxiTripRecord rec, long unixMs,
                              double lon, double lat, String linkId,
                              boolean isFallback) throws IOException {
        String datetime;
        synchronized (DATE_FORMAT) {
            datetime = DATE_FORMAT.format(new Date(unixMs));
        }
        bw.write(String.format("%d,%d,%d,%s,%.6f,%.6f,%d,%d,%s,%.2f,%s,%s,%d",
                rec.taxiId, rec.tripId, unixMs, datetime, lon, lat,
                rec.getTransportMode(), rec.getPurposeCode(),
                rec.passengerIn, rec.fareYen, rec.isNightTrip, linkId,
                isFallback ? 1 : 0));
        bw.newLine();
    }
}
