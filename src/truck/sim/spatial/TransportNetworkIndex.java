package truck.sim.spatial;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.feature.simple.SimpleFeature;

// Use SafeShpLoader instead of dcity.aggr.ShpLoader for null-safety
// import dcity.aggr.ShpLoader;

/**
 * Unified spatial index for road and rail networks.
 * Uses proven STRtree pattern from dcity.aggr.Boundaries for efficient spatial queries.
 *
 * Loads transportation network shapefiles and provides:
 * - Nearest road/rail segment queries
 * - Envelope-based range queries for zone caching
 * - Point-to-network snapping
 */
public class TransportNetworkIndex {
    private STRtree networkIndex;  // Unified spatial index for roads and rails
    private Map<Geometry, RoadSegment> roadSegments;
    private Map<Geometry, RailSegment> railSegments;
    private GeometryFactory gf = new GeometryFactory();

    public TransportNetworkIndex() {
        this.networkIndex = new STRtree();
        this.roadSegments = new HashMap<>();
        this.railSegments = new HashMap<>();
    }

    /**
     * Load road network from shapefile using existing ShpLoader infrastructure.
     * Pattern reused from dcity.aggr.Boundaries for proven shapefile loading.
     *
     * Expected attributes:
     * - acc (accessibility): 0=unknown, 1=ferry, 2=primary, 3=secondary, 4=local
     * - rst (surface type): 0=unknown, 1=paved, 2=unpaved, 3=loose, 4=unimproved
     * - rtt (traffic type): road classification
     * - RSU (speed class): speed indicator
     */
    public void loadRoadNetwork(String shapefilePath) throws Exception {
        System.out.println("[NETWORK] Loading road network from: " + shapefilePath);

        List<SimpleFeature> features = SafeShpLoader.load(shapefilePath);
        System.out.println("[NETWORK] SafeShpLoader returned " + features.size() + " features");

        int loadedCount = 0;
        int nullGeomCount = 0;
        int nonLineStringCount = 0;

        for (SimpleFeature f : features) {
            Geometry geom = (Geometry) f.getDefaultGeometry();

            // Skip null geometries
            if (geom == null) {
                nullGeomCount++;
                continue;
            }

            // Parse attributes once for this feature (handle nulls gracefully - default to 0)
            Object accObj = f.getAttribute("acc");
            Object rstObj = f.getAttribute("rst");
            Object rttObj = f.getAttribute("rtt");
            Object rsuObj = f.getAttribute("RSU");

            int acc = (accObj != null) ? ((Number)accObj).intValue() : 0;
            int rst = (rstObj != null) ? ((Number)rstObj).intValue() : 0;
            int rtt = (rttObj != null) ? ((Number)rttObj).intValue() : 0;
            int rsu = (rsuObj != null) ? ((Number)rsuObj).intValue() : 0;

            // Handle both LineString and MultiLineString geometries
            List<LineString> lineStrings = new ArrayList<>();
            if (geom instanceof LineString) {
                lineStrings.add((LineString) geom);
            } else if (geom instanceof MultiLineString) {
                MultiLineString multiLine = (MultiLineString) geom;
                for (int i = 0; i < multiLine.getNumGeometries(); i++) {
                    lineStrings.add((LineString) multiLine.getGeometryN(i));
                }
            } else {
                nonLineStringCount++;
                if (nonLineStringCount == 1) {
                    System.out.println("[NETWORK] DEBUG: Unsupported geometry type: " + geom.getGeometryType());
                }
                continue;
            }

            // Load each LineString component
            for (LineString lineGeom : lineStrings) {
                RoadSegment segment = new RoadSegment(acc, rst, rtt, rsu, lineGeom);
                roadSegments.put(lineGeom, segment);

                // Insert into spatial index using envelope for fast queries
                networkIndex.insert(lineGeom.getEnvelopeInternal(), lineGeom);
                loadedCount++;
            }
        }

        System.out.println("[NETWORK] Loaded " + loadedCount + " road segments");
        if (nullGeomCount > 0) {
            System.out.println("[NETWORK] Warning: Skipped " + nullGeomCount + " features with null geometries");
        }
        if (nonLineStringCount > 0) {
            System.out.println("[NETWORK] Info: Skipped " + nonLineStringCount + " non-LineString geometries");
        }
    }

    /**
     * Load rail network from shapefile using existing ShpLoader infrastructure.
     *
     * Expected attributes:
     * - f_code (rail classification): type of rail line
     * - exs (existence): operational status
     * - fco (function code): functional classification
     * - loc (location): spatial location type
     */
    public void loadRailNetwork(String shapefilePath) throws Exception {
        System.out.println("[NETWORK] Loading rail network from: " + shapefilePath);

        List<SimpleFeature> features = SafeShpLoader.load(shapefilePath);
        int loadedCount = 0;
        int nullGeomCount = 0;
        int nonLineStringCount = 0;

        for (SimpleFeature f : features) {
            Geometry geom = (Geometry) f.getDefaultGeometry();

            // Skip null geometries
            if (geom == null) {
                nullGeomCount++;
                continue;
            }

            // Parse attributes once for this feature (handle nulls gracefully)
            Object fCodeObj = f.getAttribute("f_code");
            Object exsObj = f.getAttribute("exs");
            Object fcoObj = f.getAttribute("fco");
            Object locObj = f.getAttribute("loc");

            String fCode = (fCodeObj != null) ? fCodeObj.toString() : "unknown";
            int exs = (exsObj != null) ? ((Number)exsObj).intValue() : 0;
            int fco = (fcoObj != null) ? ((Number)fcoObj).intValue() : 0;
            int loc = (locObj != null) ? ((Number)locObj).intValue() : 0;

            // Handle both LineString and MultiLineString geometries
            List<LineString> lineStrings = new ArrayList<>();
            if (geom instanceof LineString) {
                lineStrings.add((LineString) geom);
            } else if (geom instanceof MultiLineString) {
                MultiLineString multiLine = (MultiLineString) geom;
                for (int i = 0; i < multiLine.getNumGeometries(); i++) {
                    lineStrings.add((LineString) multiLine.getGeometryN(i));
                }
            } else {
                nonLineStringCount++;
                if (nonLineStringCount == 1) {
                    System.out.println("[NETWORK] DEBUG: Unsupported geometry type: " + geom.getGeometryType());
                }
                continue;
            }

            // Load each LineString component
            for (LineString lineGeom : lineStrings) {
                RailSegment segment = new RailSegment(fCode, exs, fco, loc, lineGeom);
                railSegments.put(lineGeom, segment);

                // Insert into same spatial index
                networkIndex.insert(lineGeom.getEnvelopeInternal(), lineGeom);
                loadedCount++;
            }
        }

        System.out.println("[NETWORK] Loaded " + loadedCount + " rail segments");
    }

    /**
     * Find nearest road segment within distance threshold.
     * Uses STRtree spatial index for O(log n) envelope query.
     *
     * @param lon Longitude in degrees
     * @param lat Latitude in degrees
     * @param maxDistKm Maximum search distance in kilometers
     * @return NearestSegmentResult with segment and distance, or null if none found
     */
    public NearestSegmentResult findNearestRoad(double lon, double lat, double maxDistKm) {
        // Convert km to degrees (approximate: 1 degree ≈ 111 km)
        double radiusDeg = maxDistKm / 111.0;
        Envelope searchEnv = new Envelope(
            lon - radiusDeg, lon + radiusDeg,
            lat - radiusDeg, lat + radiusDeg
        );

        // Query spatial index for candidate segments
        @SuppressWarnings("unchecked")
        List<Geometry> candidates = networkIndex.query(searchEnv);

        Point point = gf.createPoint(new Coordinate(lon, lat));
        RoadSegment nearest = null;
        double minDist = Double.MAX_VALUE;

        // Test only road segments (not rails)
        for (Geometry geom : candidates) {
            RoadSegment segment = roadSegments.get(geom);
            if (segment == null) continue;  // Skip rail segments

            double dist = geom.distance(point);  // In degrees
            if (dist < minDist) {
                minDist = dist;
                nearest = segment;
            }
        }

        // Convert distance back to km
        double distKm = minDist * 111.0;

        return (distKm <= maxDistKm && nearest != null) ?
            new NearestSegmentResult(nearest, distKm) : null;
    }

    /**
     * Query all roads in rectangular envelope (for zone caching).
     * Returns road segments whose envelopes intersect the query envelope.
     *
     * @param minLon Minimum longitude of query envelope
     * @param minLat Minimum latitude of query envelope
     * @param maxLon Maximum longitude of query envelope
     * @param maxLat Maximum latitude of query envelope
     * @return List of RoadSegments in the envelope
     */
    public List<RoadSegment> queryRoadsInEnvelope(
        double minLon, double minLat, double maxLon, double maxLat) {

        Envelope env = new Envelope(minLon, maxLon, minLat, maxLat);

        @SuppressWarnings("unchecked")
        List<Geometry> results = networkIndex.query(env);

        return results.stream()
            .map(roadSegments::get)
            .filter(Objects::nonNull)  // Filter out rail segments
            .collect(Collectors.toList());
    }

    /**
     * Snap point to nearest road segment (project onto LineString).
     *
     * @param lon Longitude in degrees
     * @param lat Latitude in degrees
     * @param maxDistKm Maximum snap distance in kilometers
     * @return [lon, lat] of snapped point, or null if no road within distance
     */
    public double[] snapToNearestRoad(double lon, double lat, double maxDistKm) {
        NearestSegmentResult nearest = findNearestRoad(lon, lat, maxDistKm);
        if (nearest == null) return null;

        Point p = gf.createPoint(new Coordinate(lon, lat));
        return nearest.getSegment().projectPoint(p);
    }

    /**
     * Calculate road quality score (higher = better).
     * Formula: 2.0 * accessibility + (4 - surfaceType)
     *
     * Examples:
     * - Primary paved (acc=2, rst=1): 2.0*2 + (4-1) = 7.0 (excellent)
     * - Secondary unpaved (acc=3, rst=2): 2.0*3 + (4-2) = 8.0 (good)
     * - Local unimproved (acc=4, rst=4): 2.0*4 + (4-4) = 8.0 (poor)
     */
    public double calculateAccessibilityScore(RoadSegment segment) {
        return 2.0 * segment.getAccessibility() + (4 - segment.getSurfaceType());
    }

    /**
     * Get total number of road segments loaded.
     */
    public int getRoadCount() {
        return roadSegments.size();
    }

    /**
     * Get total number of rail segments loaded.
     */
    public int getRailCount() {
        return railSegments.size();
    }
}
