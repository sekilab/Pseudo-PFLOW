package traj;

/**
 * Trip record for a single taxi trip parsed from trips_pseudo_pflow.csv.
 * <p>
 * Taxi-specific metadata includes fare, passenger occupancy, and night trip flag.
 * <p>
 * CSV column layout (16 columns from TaxiDataExporter):
 *   0:id, 1:starttime, 2:start_lon, 3:start_lat, 4:end_lon, 5:end_lat,
 *   6:transport_mode(8), 7:purpose(0/3), 8:occupation,
 *   9:passenger_in, 10:taxi_id, 11:distance_km, 12:fare_yen,
 *   13:is_night_trip, 14:starttime_h, 15:simulation_day
 */
public class TaxiTripRecord implements VehicleTripRecord {

    public final long tripId;
    public final int taxiId;
    public final long depTime;       // simulationDay * 86400 + starttime (seconds)
    public final double originLon;
    public final double originLat;
    public final double destLon;
    public final double destLat;
    public final double distanceKm;
    // Taxi-specific metadata
    public final boolean passengerIn;
    public final double fareYen;
    public final boolean isNightTrip;
    public final int simulationDay;

    public TaxiTripRecord(long tripId, int taxiId, long depTime,
                          double originLon, double originLat,
                          double destLon, double destLat,
                          double distanceKm,
                          boolean passengerIn, double fareYen,
                          boolean isNightTrip, int simulationDay) {
        this.tripId = tripId;
        this.taxiId = taxiId;
        this.depTime = depTime;
        this.originLon = originLon;
        this.originLat = originLat;
        this.destLon = destLon;
        this.destLat = destLat;
        this.distanceKm = distanceKm;
        this.passengerIn = passengerIn;
        this.fareYen = fareYen;
        this.isNightTrip = isNightTrip;
        this.simulationDay = simulationDay;
    }

    // ── VehicleTripRecord interface ──────────────────────────────────────

    @Override public long   getTripId()        { return tripId; }
    @Override public int    getVehicleId()      { return taxiId; }
    @Override public long   getDepTime()        { return depTime; }
    @Override public double getOriginLon()      { return originLon; }
    @Override public double getOriginLat()      { return originLat; }
    @Override public double getDestLon()        { return destLon; }
    @Override public double getDestLat()        { return destLat; }
    @Override public double getDistanceKm()     { return distanceKm; }
    @Override public int    getTransportMode()  { return 8; }  // taxi
    @Override public int    getPurposeCode()    { return passengerIn ? 3 : 0; }
}
