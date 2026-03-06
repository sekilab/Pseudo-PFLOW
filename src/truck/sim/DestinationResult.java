package truck.sim;

/**
 * Result of a destination selection operation.
 *
 * <p>Bundles the selected coordinates with the zone and POI metadata that
 * downstream code needs. This replaces the mutable instance fields
 * ({@code lastSelectedDestZoneId}, {@code lastSelectedDestPOIId},
 * {@code lastInterZoneSelected}) that previously passed data between methods
 * through shared state.
 *
 * @author Truck ABM Framework
 * @see DestinationSelector
 */
public class DestinationResult {

    /** Destination coordinates [longitude, latitude]. */
    public final double[] coords;

    /** Zone ID of the selected destination. May be null for fallback selections. */
    public final String zoneId;

    /** POI ID at the destination. May be null if no POI was selected. */
    public final String poiId;

    public DestinationResult(double[] coords, String zoneId, String poiId) {
        this.coords = coords;
        this.zoneId = zoneId;
        this.poiId = poiId;
    }

    /** Creates a result with coordinates only (no zone/POI metadata). */
    public static DestinationResult coordsOnly(double[] coords) {
        return new DestinationResult(coords, null, null);
    }

    /** Creates a result with coordinates and zone ID (no POI). */
    public static DestinationResult withZone(double[] coords, String zoneId) {
        return new DestinationResult(coords, zoneId, null);
    }
}
