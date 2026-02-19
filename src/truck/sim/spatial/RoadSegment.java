package truck.sim.spatial;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.linearref.*;

/**
 * Value object representing a road segment from the road network shapefile.
 *
 * Stores road attributes and provides geometry operations:
 * - Point projection onto road segment (snapping)
 * - Distance calculations
 * - Accessibility scoring
 *
 * Attributes from roadl_jpn.shp:
 * - acc (accessibility): 0=unknown, 1=ferry, 2=primary, 3=secondary, 4=local
 * - rst (surface type): 0=unknown, 1=paved, 2=unpaved, 3=loose, 4=unimproved
 * - rtt (traffic type): road classification
 * - RSU (speed class): speed indicator
 */
public class RoadSegment {
    private final LineString geometry;
    private final int accessibility;      // acc: 0-4
    private final int surfaceType;        // rst: 0-4
    private final int trafficType;        // rtt
    private final int speedClass;         // RSU
    private final double accessibilityScore;  // Pre-computed for performance

    private final GeometryFactory gf = new GeometryFactory();

    /**
     * Create a road segment with attributes.
     *
     * @param acc Accessibility (0-4, higher = better road class)
     * @param rst Surface type (0-4, lower = better surface quality)
     * @param rtt Traffic type classification
     * @param rsu Speed class
     * @param geom LineString geometry of the road segment
     */
    public RoadSegment(int acc, int rst, int rtt, int rsu, LineString geom) {
        this.accessibility = acc;
        this.surfaceType = rst;
        this.trafficType = rtt;
        this.speedClass = rsu;
        this.geometry = geom;

        // Pre-compute accessibility score for performance
        // Formula: 2.0 * acc + (4 - rst)
        // Higher score = better road (primary roads + paved surfaces)
        this.accessibilityScore = 2.0 * acc + (4 - rst);
    }

    /**
     * Project point onto this road segment (find nearest point on line).
     * Uses JTS LocationIndexedLine for efficient projection.
     *
     * @param point Point to project
     * @return [lon, lat] of projected point on road segment
     */
    public double[] projectPoint(Point point) {
        LocationIndexedLine indexedLine = new LocationIndexedLine(geometry);
        LinearLocation loc = indexedLine.project(point.getCoordinate());
        Coordinate projected = indexedLine.extractPoint(loc);
        return new double[]{projected.x, projected.y};
    }

    /**
     * Calculate distance from point to this road segment in kilometers.
     *
     * @param lon Longitude in degrees
     * @param lat Latitude in degrees
     * @return Distance to road segment in km
     */
    public double distanceToPoint(double lon, double lat) {
        Point p = gf.createPoint(new Coordinate(lon, lat));
        return geometry.distance(p) * 111.0;  // Convert degrees to km
    }

    // Getters

    public LineString getGeometry() {
        return geometry;
    }

    public int getAccessibility() {
        return accessibility;
    }

    public int getSurfaceType() {
        return surfaceType;
    }

    public int getTrafficType() {
        return trafficType;
    }

    public int getSpeedClass() {
        return speedClass;
    }

    public double getAccessibilityScore() {
        return accessibilityScore;
    }

    @Override
    public String toString() {
        return String.format("RoadSegment[acc=%d, rst=%d, score=%.1f]",
            accessibility, surfaceType, accessibilityScore);
    }
}
