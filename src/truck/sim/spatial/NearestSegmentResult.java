package truck.sim.spatial;

/**
 * Return type for nearest segment queries.
 *
 * Encapsulates the result of a nearest road segment query:
 * - The road segment found
 * - Distance from query point to segment in kilometers
 *
 * Returned by TransportNetworkIndex.findNearestRoad()
 */
public class NearestSegmentResult {
    private final RoadSegment segment;
    private final double distanceKm;

    /**
     * Create a nearest segment result.
     *
     * @param segment The nearest road segment found
     * @param distanceKm Distance from query point to segment in km
     */
    public NearestSegmentResult(RoadSegment segment, double distanceKm) {
        this.segment = segment;
        this.distanceKm = distanceKm;
    }

    /**
     * Get the nearest road segment.
     */
    public RoadSegment getSegment() {
        return segment;
    }

    /**
     * Get distance to the segment in kilometers.
     */
    public double getDistanceKm() {
        return distanceKm;
    }

    @Override
    public String toString() {
        return String.format("NearestSegment[dist=%.2f km, %s]",
            distanceKm, segment);
    }
}
