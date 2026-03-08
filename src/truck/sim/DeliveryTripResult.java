package truck.sim;

/**
 * Result of generating a single delivery trip.
 *
 * <p>Replaces the previous {@code Object[]} return from {@code generateDeliveryTrip()},
 * providing type-safe access to all trip generation outputs.
 *
 * @author Truck ABM Framework
 * @see TruckSimulation#generateDeliveryTrip
 */
public class DeliveryTripResult {

    /** The generated trip, or null if zone is at capacity. */
    public final TruckTrip trip;

    /** Updated simulation time after trip timing adjustments. */
    public final long updatedTime;

    /** Whether this trip crosses metropolitan boundaries. */
    public final boolean isInterMetro;

    /** POI ID at the destination, for facility chain tracking. May be null. */
    public final String destPOIId;

    public DeliveryTripResult(TruckTrip trip, long updatedTime,
                              boolean isInterMetro, String destPOIId) {
        this.trip = trip;
        this.updatedTime = updatedTime;
        this.isInterMetro = isInterMetro;
        this.destPOIId = destPOIId;
    }

    /** Creates an empty result when zone is at capacity. */
    public static DeliveryTripResult atCapacity(long currentTime) {
        return new DeliveryTripResult(null, currentTime, false, null);
    }
}
