package traj;

/**
 * Trip record for a single truck trip parsed from trips_pseudo_pflow.csv.
 * <p>
 * Retains truck-specific metadata (cargo, goods type, vehicle size) alongside
 * the common routing fields defined by {@link VehicleTripRecord}.
 * <p>
 * Adapted from {@code truck.traj.TruckTripRecord} with the addition of the
 * {@link VehicleTripRecord} interface.
 */
public class TruckTripRecord implements VehicleTripRecord {

    public final long tripId;
    public final int truckId;
    public final long depTime;       // sim_day * 86400 + starttime (seconds from epoch day 0)
    public final double originLon;
    public final double originLat;
    public final double destLon;
    public final double destLat;
    public final boolean cargoLoaded;
    public final String goodsType;
    public final String vehicleSize;
    public final double distanceKm;

    public TruckTripRecord(long tripId, int truckId, long depTime,
                           double originLon, double originLat,
                           double destLon, double destLat,
                           boolean cargoLoaded, String goodsType,
                           String vehicleSize, double distanceKm) {
        this.tripId = tripId;
        this.truckId = truckId;
        this.depTime = depTime;
        this.originLon = originLon;
        this.originLat = originLat;
        this.destLon = destLon;
        this.destLat = destLat;
        this.cargoLoaded = cargoLoaded;
        this.goodsType = goodsType;
        this.vehicleSize = vehicleSize;
        this.distanceKm = distanceKm;
    }

    // ── VehicleTripRecord interface ──────────────────────────────────────

    @Override public long   getTripId()        { return tripId; }
    @Override public int    getVehicleId()      { return truckId; }
    @Override public long   getDepTime()        { return depTime; }
    @Override public double getOriginLon()      { return originLon; }
    @Override public double getOriginLat()      { return originLat; }
    @Override public double getDestLon()        { return destLon; }
    @Override public double getDestLat()        { return destLat; }
    @Override public double getDistanceKm()     { return distanceKm; }
    @Override public int    getTransportMode()  { return 9; }  // truck
    @Override public int    getPurposeCode()    { return cargoLoaded ? 1 : 0; }
}
