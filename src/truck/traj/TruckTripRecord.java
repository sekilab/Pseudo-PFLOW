package truck.traj;

/**
 * Lightweight data class for one parsed truck trip from trips_pseudo_pflow.csv.
 * Fields map directly to CSV columns — no PFLOW enum dependency.
 */
public class TruckTripRecord {
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
}
