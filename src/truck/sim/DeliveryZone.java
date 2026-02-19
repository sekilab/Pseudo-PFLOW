package truck.sim;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// Polygon-based zone support (Phase 2)
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/**
 * Represents a delivery zone with time-dependent attractiveness for truck agents.
 * 
 * Updated Phase 3: Added establishment tracking by facility and industry.
 *
 * @version 3.0
 */
public class DeliveryZone {

    // Zone identification
    private final String zoneId;
    private final String name;

    // Geographic properties
    private final double centerLongitude;
    private final double centerLatitude;
    private final double radiusKm;

    // Zone characteristics (weights 0.0-1.0)
    private final double warehousesWeight;
    private final double retailWeight;
    private final double constructionWeight;
    private final double residentialWeight;

    // Phase 3 Attributes
    private FacilityType facilityType;
    private String subRegion; // e.g. "tokyo_ward_coastal"
    private Map<String, Integer> establishments; // facilityType -> count
    private Map<String, Map<String, Integer>> industryEstablishments; // facilityType -> industry -> count

    // POIs in this zone
    private List<PointOfInterest> pois;

    // Phase 2: Polygon-based zone boundaries (optional - fallback to radius if not set)
    private List<String> administrativeCodes;     // Administrative codes from shapefile
    private List<Geometry> boundaryPolygons;      // Boundary geometries
    private Geometry unionPolygon;                 // Union of all boundaries for this zone
    private PreparedGeometry preparedPolygon;      // Prepared for fast point-in-polygon tests
    private GeometryFactory geometryFactory;

    // Phase 3: Network-aware generation (cached roads for performance)
    private List<truck.sim.spatial.RoadSegment> cachedRoads = null;

    /**
     * Constructor for DeliveryZone.
     */
    public DeliveryZone(String zoneId, String name,
                       double centerLongitude, double centerLatitude,
                       double radiusKm,
                       double warehousesWeight, double retailWeight,
                       double constructionWeight, double residentialWeight) {
        this.zoneId = zoneId;
        this.name = name;
        this.centerLongitude = centerLongitude;
        this.centerLatitude = centerLatitude;
        this.radiusKm = radiusKm;
        this.warehousesWeight = warehousesWeight;
        this.retailWeight = retailWeight;
        this.constructionWeight = constructionWeight;
        this.residentialWeight = residentialWeight;

        // Infer facility type from zone weights
        this.facilityType = FacilityType.inferFromWeights(
            warehousesWeight, retailWeight, constructionWeight, residentialWeight
        );

        this.establishments = new HashMap<>();
        this.industryEstablishments = new HashMap<>();
        this.pois = new ArrayList<>();

        // Initialize geometry factory for polygon support
        this.geometryFactory = new GeometryFactory();
        this.administrativeCodes = new ArrayList<>();
        this.boundaryPolygons = new ArrayList<>();
    }
    
    /**
     * Calculate time-dependent attractiveness score.
     */
    public double calculateAttractiveness(int timePeriod,
                                         double beta1, double beta2,
                                         double beta3, double beta4) {
        double baseScore = beta1 * warehousesWeight +
                          beta2 * retailWeight +
                          beta3 * constructionWeight +
                          beta4 * residentialWeight;
        
        double timeMultiplier = 1.0;
        switch (timePeriod) {
            case 0: if (warehousesWeight > 0.5 || constructionWeight > 0.5) timeMultiplier = 1.5; break;
            case 1: if (retailWeight > 0.5 || residentialWeight > 0.5) timeMultiplier = 1.5; break;
            case 2: timeMultiplier = 0.3; if (warehousesWeight > 0.8) timeMultiplier = 0.6; break;
        }
        return baseScore * timeMultiplier;
    }
    
    /**
     * Set establishment count for a facility type.
     */
    public void setEstablishmentCount(String facility, int count) {
        this.establishments.put(facility, count);
    }
    
    /**
     * Get establishment count for a facility type.
     */
    public int getEstablishmentCount(String facility) {
        return establishments.getOrDefault(facility, 0);
    }
    
    /**
     * Set industry-specific establishment counts.
     */
    public void setIndustryCounts(String facility, Map<String, Integer> counts) {
        this.industryEstablishments.put(facility, counts);
    }
    
    public Map<String, Integer> getIndustryCounts(String facility) {
        return industryEstablishments.getOrDefault(facility, new HashMap<>());
    }

    // ============================================================================
    // INDUSTRY PRESENCE METHODS (MFS File 06 Integration)
    // ============================================================================

    /**
     * Get the dominant industry code for this zone.
     * Aggregates industry counts across all facility types.
     *
     * @return The industry code with highest establishment count, or "0" if none
     */
    public String getDominantIndustry() {
        Map<String, Integer> aggregatedIndustries = new HashMap<>();

        // Aggregate across all facility types
        for (Map<String, Integer> facilityCounts : industryEstablishments.values()) {
            for (Map.Entry<String, Integer> entry : facilityCounts.entrySet()) {
                aggregatedIndustries.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        // Find the industry with highest count
        return aggregatedIndustries.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("0"); // Default industry code
    }

    /**
     * Check if this zone has establishments in a given industry.
     *
     * @param industryCode The industry code to check
     * @return true if zone has at least one establishment in that industry
     */
    public boolean hasIndustryPresence(String industryCode) {
        for (Map<String, Integer> facilityCounts : industryEstablishments.values()) {
            Integer count = facilityCounts.get(industryCode);
            if (count != null && count > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the industry presence weight (proportion of establishments in that industry).
     *
     * @param industryCode The industry code
     * @return Weight from 0.0 to 1.0 representing industry share
     */
    public double getIndustryPresenceWeight(String industryCode) {
        int industryCount = 0;
        int totalCount = 0;

        for (Map<String, Integer> facilityCounts : industryEstablishments.values()) {
            for (Map.Entry<String, Integer> entry : facilityCounts.entrySet()) {
                totalCount += entry.getValue();
                if (entry.getKey().equals(industryCode)) {
                    industryCount += entry.getValue();
                }
            }
        }

        return totalCount > 0 ? (double) industryCount / totalCount : 0.0;
    }

    /**
     * Get all industries present in this zone with their weights.
     *
     * @return Map of industry code to presence weight
     */
    public Map<String, Double> getIndustryPresenceMap() {
        Map<String, Integer> aggregated = new HashMap<>();
        int totalCount = 0;

        for (Map<String, Integer> facilityCounts : industryEstablishments.values()) {
            for (Map.Entry<String, Integer> entry : facilityCounts.entrySet()) {
                aggregated.merge(entry.getKey(), entry.getValue(), Integer::sum);
                totalCount += entry.getValue();
            }
        }

        Map<String, Double> weights = new HashMap<>();
        if (totalCount > 0) {
            for (Map.Entry<String, Integer> entry : aggregated.entrySet()) {
                weights.put(entry.getKey(), (double) entry.getValue() / totalCount);
            }
        }
        return weights;
    }

    // ============================================================================
    // POLYGON-BASED ZONE SUPPORT (Phase 2)
    // ============================================================================

    /**
     * Set polygon boundaries for this zone.
     * Creates union polygon and prepared geometry for fast point-in-polygon testing.
     * For zones with many polygons (>50), uses individual polygon testing instead of union.
     */
    public void setBoundaryPolygons(List<Geometry> polygons) {
        if (polygons == null || polygons.isEmpty()) {
            return;
        }

        this.boundaryPolygons = new ArrayList<>(polygons);

        // For large polygon sets (>50), don't create union - too expensive
        // Instead, we'll test against individual polygons
        if (polygons.size() > 50) {
            System.out.println("[ZONE] " + zoneId + " has " + polygons.size() +
                " polygons - using individual polygon testing (no union)");
            // Don't create union or prepared polygon - will use individual testing
            this.unionPolygon = null;
            this.preparedPolygon = null;
            return;
        }

        // Create union of all polygons for this zone (small zones only)
        if (polygons.size() == 1) {
            this.unionPolygon = polygons.get(0);
        } else {
            Geometry[] geomArray = polygons.toArray(new Geometry[0]);
            this.unionPolygon = geometryFactory.createGeometryCollection(geomArray).union();
        }

        // Create prepared geometry for fast point-in-polygon testing
        this.preparedPolygon = PreparedGeometryFactory.prepare(unionPolygon);
    }

    /**
     * Set administrative codes associated with this zone.
     */
    public void setAdministrativeCodes(List<String> codes) {
        this.administrativeCodes = codes != null ? new ArrayList<>(codes) : new ArrayList<>();
    }

    /**
     * Check if this zone has polygon boundary support.
     */
    public boolean hasPolygonBoundary() {
        return preparedPolygon != null;
    }

    /**
     * Get administrative codes for this zone.
     */
    public List<String> getAdministrativeCodes() {
        return administrativeCodes;
    }

    /**
     * Get boundary polygons for this zone.
     */
    public List<Geometry> getBoundaryPolygons() {
        return boundaryPolygons;
    }

    /**
     * Get union polygon for this zone.
     */
    public Geometry getUnionPolygon() {
        return unionPolygon;
    }

    /**
     * Check if a point is within this zone's boundaries.
     * Uses polygon boundaries if available, otherwise falls back to radius.
     */
    public boolean containsPoint(double longitude, double latitude) {
        // Use prepared polygon if available (small zones with union)
        if (preparedPolygon != null) {
            Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
            return preparedPolygon.contains(point);
        }

        // For large zones without union, test against individual polygons
        if (boundaryPolygons != null && !boundaryPolygons.isEmpty()) {
            Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
            for (Geometry polygon : boundaryPolygons) {
                if (polygon.contains(point)) {
                    return true;
                }
            }
            return false;
        }

        // Fallback to radius-based containment
        double distance = calculateDistance(centerLongitude, centerLatitude,
                                          longitude, latitude);
        return distance <= radiusKm;
    }

    /**
     * Generate random point within zone boundaries.
     * Uses polygon sampling if available, otherwise falls back to radius-based generation.
     */
    public double[] generateRandomPoint(Random random) {
        // Use union polygon-based generation if available (small zones)
        if (unionPolygon != null && preparedPolygon != null) {
            return generateRandomPointInPolygon(random);
        }

        // For large zones with many individual polygons, pick random polygon and sample from it
        if (boundaryPolygons != null && !boundaryPolygons.isEmpty()) {
            // Pick a random polygon from the zone
            Geometry randomPolygon = boundaryPolygons.get(random.nextInt(boundaryPolygons.size()));
            return generateRandomPointInSinglePolygon(randomPolygon, random);
        }

        // Fallback to radius-based generation
        return generateRandomPointInRadius(random);
    }

    /**
     * Generate random point within polygon boundaries using envelope sampling.
     */
    private double[] generateRandomPointInPolygon(Random random) {
        Envelope env = unionPolygon.getEnvelopeInternal();
        int maxAttempts = 1000;
        int attempts = 0;

        while (attempts < maxAttempts) {
            // Sample random point within bounding envelope
            double lon = env.getMinX() + random.nextDouble() * env.getWidth();
            double lat = env.getMinY() + random.nextDouble() * env.getHeight();

            // Test if point is inside polygon
            Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
            if (preparedPolygon.contains(p)) {
                return new double[]{lon, lat};
            }
            attempts++;
        }

        // Fallback: return centroid if sampling fails
        Coordinate centroid = unionPolygon.getCentroid().getCoordinate();
        return new double[]{centroid.x, centroid.y};
    }

    /**
     * Generate random point within a single polygon using envelope sampling.
     * Used for large zones with many individual polygons.
     */
    private double[] generateRandomPointInSinglePolygon(Geometry polygon, Random random) {
        Envelope env = polygon.getEnvelopeInternal();
        int maxAttempts = 1000;

        for (int attempts = 0; attempts < maxAttempts; attempts++) {
            // Sample random point within bounding envelope
            double lon = env.getMinX() + random.nextDouble() * env.getWidth();
            double lat = env.getMinY() + random.nextDouble() * env.getHeight();

            // Test if point is inside polygon
            Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
            if (polygon.contains(p)) {
                return new double[]{lon, lat};
            }
        }

        // Fallback: return polygon centroid if sampling fails
        Coordinate centroid = polygon.getCentroid().getCoordinate();
        return new double[]{centroid.x, centroid.y};
    }

    /**
     * Generate random point within radius (fallback method).
     */
    private double[] generateRandomPointInRadius(Random random) {
        // Uniform random sampling within circle
        double r = radiusKm * Math.sqrt(random.nextDouble());
        double theta = random.nextDouble() * 2 * Math.PI;

        // Convert to lat/lon offset (approximate)
        double deltaLat = (r * Math.cos(theta)) / 111.0; // ~111 km per degree latitude
        double deltaLon = (r * Math.sin(theta)) / (111.0 * Math.cos(Math.toRadians(centerLatitude)));

        return new double[]{centerLongitude + deltaLon, centerLatitude + deltaLat};
    }

    /**
     * Check if a point is within this zone's radius.
     */
    private boolean isWithinRadius(double longitude, double latitude) {
        double distance = calculateDistance(centerLongitude, centerLatitude,
                                          longitude, latitude);
        return distance <= radiusKm;
    }
    
    private double calculateDistance(double lon1, double lat1,
                                    double lon2, double lat2) {
        final double EARTH_RADIUS_KM = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Get roads in this zone (cached for performance).
     * Called by NetworkAwarePointGenerator for network-aware sampling.
     *
     * @param networkIndex Transport network index to query roads
     * @return List of RoadSegments in this zone
     */
    public List<truck.sim.spatial.RoadSegment> getRoadsInZone(truck.sim.spatial.TransportNetworkIndex networkIndex) {
        if (cachedRoads == null) {
            org.locationtech.jts.geom.Envelope env = getEnvelope();
            cachedRoads = networkIndex.queryRoadsInEnvelope(
                env.getMinX(), env.getMinY(),
                env.getMaxX(), env.getMaxY()
            );
            System.out.println("[ZONE] " + zoneId + " cached " +
                cachedRoads.size() + " road segments");
        }
        return cachedRoads;
    }

    /**
     * Get bounding envelope of this zone.
     * Used for network queries and road caching.
     *
     * @return Envelope encompassing the zone
     */
    public org.locationtech.jts.geom.Envelope getEnvelope() {
        if (unionPolygon != null) {
            return unionPolygon.getEnvelopeInternal();
        }

        // Construct envelope from radius for zones without polygons
        double degPerKm = 1.0 / 111.0;
        return new org.locationtech.jts.geom.Envelope(
            centerLongitude - radiusKm * degPerKm,
            centerLongitude + radiusKm * degPerKm,
            centerLatitude - radiusKm * degPerKm,
            centerLatitude + radiusKm * degPerKm
        );
    }

    // Getters and Setters
    public String getZoneId() { return zoneId; }
    public String getName() { return name; }
    public double getCenterLongitude() { return centerLongitude; }
    public double getCenterLatitude() { return centerLatitude; }
    public double getRadiusKm() { return radiusKm; }
    public double getWarehousesWeight() { return warehousesWeight; }
    public double getRetailWeight() { return retailWeight; }
    public double getConstructionWeight() { return constructionWeight; }
    public double getResidentialWeight() { return residentialWeight; }
    public FacilityType getFacilityType() { return facilityType; }
    public String getSubRegion() { return subRegion; }
    public void setSubRegion(String sub) { this.subRegion = sub; }
    public void setFacilityType(FacilityType facilityType) { this.facilityType = facilityType; }
    public void setPOIs(List<PointOfInterest> pois) { this.pois = pois != null ? pois : new ArrayList<>(); }
    public List<PointOfInterest> getPOIs() { return pois; }

    @Override
    public String toString() {
        return String.format("DeliveryZone[%s: %s, sub=%s, facility=%s]",
                           zoneId, name, subRegion, facilityType);
    }
}