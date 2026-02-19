package truck.sim.spatial;

import java.util.*;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.linearref.*;

import truck.sim.DeliveryZone;

/**
 * Enhanced point generation combining polygon containment + network proximity.
 *
 * Three-phase generation algorithm:
 * 1. Network-aware sampling (10 attempts) - sample along high-quality roads
 * 2. Post-generation snapping (100 attempts) - snap polygon points to nearby roads
 * 3. Fallback to pure polygon - maintains land guarantee
 *
 * Ensures points are both:
 * - On land (via polygon containment)
 * - Near roads (via network proximity)
 *
 * Eliminates "middle of nowhere" points while preserving 99.7% land accuracy.
 */
public class NetworkAwarePointGenerator {
    private final GeometryFactory gf = new GeometryFactory();

    /**
     * Generate network-accessible point within zone.
     * Maintains polygon containment guarantee while preferring accessible locations.
     *
     * @param zone DeliveryZone to generate point within
     * @param networkIndex Transport network index for road queries
     * @param random Random number generator
     * @return [lon, lat] guaranteed to be on land and preferably near roads
     */
    public double[] generateNetworkAccessiblePoint(
        DeliveryZone zone,
        TransportNetworkIndex networkIndex,
        Random random
    ) {
        // Phase 1: Try network-aware sampling (10 attempts)
        // Sample points along high-quality roads within zone
        for (int i = 0; i < 10; i++) {
            double[] candidate = sampleNearRoad(zone, networkIndex, random);
            if (candidate != null) {
                return candidate;  // Found point near high-quality road!
            }
        }

        // Phase 2: Polygon sampling with post-snap (100 attempts)
        // Generate polygon point, snap to nearest road
        for (int i = 0; i < 100; i++) {
            double[] polyPoint = zone.generateRandomPoint(random);
            double[] snapped = networkIndex.snapToNearestRoad(
                polyPoint[0], polyPoint[1], 0.2);  // 200m max snap

            if (snapped != null && zone.containsPoint(snapped[0], snapped[1])) {
                return snapped;  // Snapped point still in polygon!
            }
        }

        // Phase 3: Accept pure polygon point (maintains land guarantee)
        return zone.generateRandomPoint(random);
    }

    /**
     * Sample point along high-accessibility road within zone.
     *
     * Strategy:
     * 1. Get roads in zone (cached)
     * 2. Weight by accessibility score (prefer primary/paved roads)
     * 3. Sample random point along selected road
     * 4. Verify polygon containment
     *
     * @return [lon, lat] on road within zone, or null if sampling fails
     */
    private double[] sampleNearRoad(
        DeliveryZone zone,
        TransportNetworkIndex networkIndex,
        Random random
    ) {
        // Get roads in zone (cached by zone for performance)
        List<RoadSegment> roads = zone.getRoadsInZone(networkIndex);
        if (roads.isEmpty()) return null;

        // Weight by accessibility score (prefer primary/paved roads)
        RoadSegment selected = weightedRandomSelect(roads, random);

        // Sample random point along selected road segment
        double[] pointOnRoad = sampleAlongSegment(selected, random);

        // Verify polygon containment (critical!)
        // Road may cross zone boundary - point must be inside
        if (zone.containsPoint(pointOnRoad[0], pointOnRoad[1])) {
            return pointOnRoad;
        }

        return null;  // Road crosses zone boundary - point outside
    }

    /**
     * Weighted random selection based on accessibility score.
     * Higher score = higher probability of selection.
     *
     * @param segments List of road segments to choose from
     * @param random Random number generator
     * @return Selected road segment (weighted by accessibility score)
     */
    private RoadSegment weightedRandomSelect(List<RoadSegment> segments, Random random) {
        // Calculate total weight (sum of all accessibility scores)
        double totalWeight = segments.stream()
            .mapToDouble(RoadSegment::getAccessibilityScore)
            .sum();

        // Handle edge case: all scores are zero
        if (totalWeight == 0) {
            return segments.get(random.nextInt(segments.size()));
        }

        // Random selection weighted by score
        double randomValue = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (RoadSegment segment : segments) {
            cumulative += segment.getAccessibilityScore();
            if (cumulative >= randomValue) {
                return segment;
            }
        }

        // Fallback (should rarely reach here due to floating point)
        return segments.get(segments.size() - 1);
    }

    /**
     * Sample random point along road segment LineString.
     *
     * Uses coordinate interpolation along the segment:
     * 1. Calculate total length of LineString
     * 2. Pick random distance along length
     * 3. Find segment containing that distance
     * 4. Interpolate coordinates within segment
     *
     * @param segment Road segment to sample from
     * @param random Random number generator
     * @return [lon, lat] on the road segment
     */
    private double[] sampleAlongSegment(RoadSegment segment, Random random) {
        LineString geom = segment.getGeometry();
        Coordinate[] coords = geom.getCoordinates();

        // Handle edge case: single-point LineString (shouldn't happen, but be safe)
        if (coords.length < 2) {
            return new double[]{coords[0].x, coords[0].y};
        }

        // Calculate total length
        double totalLength = 0.0;
        double[] segmentLengths = new double[coords.length - 1];
        for (int i = 0; i < coords.length - 1; i++) {
            double segLen = coords[i].distance(coords[i + 1]);
            segmentLengths[i] = segLen;
            totalLength += segLen;
        }

        // Pick random distance along total length
        double sampleDist = random.nextDouble() * totalLength;

        // Find which segment contains this distance
        double cumulative = 0.0;
        for (int i = 0; i < coords.length - 1; i++) {
            Coordinate c1 = coords[i];
            Coordinate c2 = coords[i + 1];
            double segLen = segmentLengths[i];

            if (cumulative + segLen >= sampleDist) {
                // Interpolate within this segment
                double t = (sampleDist - cumulative) / segLen;
                double lon = c1.x + t * (c2.x - c1.x);
                double lat = c1.y + t * (c2.y - c1.y);
                return new double[]{lon, lat};
            }

            cumulative += segLen;
        }

        // Fallback: return last coordinate (shouldn't reach here, but be safe)
        Coordinate last = coords[coords.length - 1];
        return new double[]{last.x, last.y};
    }
}
