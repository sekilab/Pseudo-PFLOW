package truck.sim;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.*;

/**
 * Manages delivery zone lookup and distance calculations.
 *
 * <p>Provides efficient zone lookups via:
 * <ul>
 *   <li>ID-based lookup using a {@code Map} index — O(1)</li>
 *   <li>STRtree spatial index for point-in-zone — O(log n) vs O(n)</li>
 *   <li>Pre-computed zone-to-zone distance matrix — O(1) vs O(trig)</li>
 *   <li>Pre-computed nearby zone lists per distance threshold</li>
 * </ul>
 *
 * @author Truck ABM Framework
 * @version 3.0
 */
public class ZoneManager {

    private List<DeliveryZone> deliveryZones;
    private final Map<String, DeliveryZone> zoneIndex = new HashMap<>();
    private final Map<String, Integer> zoneIdToListIndex = new HashMap<>();
    private final TruckConfig config;

    // ── Spatial index for O(log n) point-in-zone lookups ─────────────────
    private STRtree zoneSpatialIndex;

    // ── Pre-computed zone-to-zone distance matrix ────────────────────────
    // distMatrix[i][j] = network distance from zone i center to zone j center
    private double[][] distMatrix;

    // ── Pre-computed "nearby zones" lists for common distance thresholds ──
    // nearbyZones35[i] = list of (zone, distance) pairs within 35km of zone i
    private List<NearbyZoneEntry>[] nearbyZones35;
    private List<NearbyZoneEntry>[] nearbyZones80;
    private List<NearbyZoneEntry>[] nearbyZones100;

    // ── Equirectangular distance pre-computation ─────────────────────────
    private double manhattanFactor;
    private double earthRadiusKm;

    /** Pre-computed nearby zone entry (zone + distance from center). */
    public static class NearbyZoneEntry {
        public final DeliveryZone zone;
        public final double distKm;
        public NearbyZoneEntry(DeliveryZone zone, double distKm) {
            this.zone = zone;
            this.distKm = distKm;
        }
    }

    public ZoneManager(TruckConfig config) {
        this.config = config;
    }

    /**
     * Sets the zone list and builds all lookup structures.
     */
    public void setZones(List<DeliveryZone> zones) {
        this.deliveryZones = zones;
        this.manhattanFactor = config.getDistanceManhattanFactor();
        this.earthRadiusKm = config.getGeographyEarthRadiusKm();
        zoneIndex.clear();
        zoneIdToListIndex.clear();

        for (int i = 0; i < zones.size(); i++) {
            DeliveryZone zone = zones.get(i);
            zoneIndex.put(zone.getZoneId(), zone);
            zoneIdToListIndex.put(zone.getZoneId(), i);
        }

        buildSpatialIndex();
        buildDistanceMatrix();
        buildNearbyZoneLists();
    }

    /** Returns the current zone list. */
    public List<DeliveryZone> getZones() {
        return deliveryZones;
    }

    // ════════════════════════════════════════════════════════════════════════
    // INDEX BUILDING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Builds STRtree spatial index on zone polygons/envelopes.
     * Turns findZone() from O(n) to O(log n) + 1-3 containment tests.
     */
    private void buildSpatialIndex() {
        zoneSpatialIndex = new STRtree();
        for (DeliveryZone zone : deliveryZones) {
            Envelope env = zone.getEnvelope();
            if (env != null && !env.isNull()) {
                zoneSpatialIndex.insert(env, zone);
            } else {
                // Fallback: build envelope from center + radius
                double degRadius = zone.getRadiusKm() / 111.0;
                Envelope fallbackEnv = new Envelope(
                    zone.getCenterLongitude() - degRadius,
                    zone.getCenterLongitude() + degRadius,
                    zone.getCenterLatitude() - degRadius,
                    zone.getCenterLatitude() + degRadius
                );
                zoneSpatialIndex.insert(fallbackEnv, zone);
            }
        }
        zoneSpatialIndex.build();
    }

    /**
     * Pre-computes zone-to-zone distance matrix (72×72 = 5,184 entries).
     * Replaces per-trip Haversine calls with O(1) array lookup.
     */
    private void buildDistanceMatrix() {
        int n = deliveryZones.size();
        distMatrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            DeliveryZone zi = deliveryZones.get(i);
            for (int j = i + 1; j < n; j++) {
                DeliveryZone zj = deliveryZones.get(j);
                double dist = calculateDistance(
                    zi.getCenterLongitude(), zi.getCenterLatitude(),
                    zj.getCenterLongitude(), zj.getCenterLatitude());
                distMatrix[i][j] = dist;
                distMatrix[j][i] = dist;
            }
        }
    }

    /**
     * Pre-computes nearby zone lists for common distance thresholds.
     * Eliminates per-trip linear scans in selectNearbyDestination().
     */
    @SuppressWarnings("unchecked")
    private void buildNearbyZoneLists() {
        int n = deliveryZones.size();
        nearbyZones35 = new List[n];
        nearbyZones80 = new List[n];
        nearbyZones100 = new List[n];

        for (int i = 0; i < n; i++) {
            nearbyZones35[i] = new ArrayList<>();
            nearbyZones80[i] = new ArrayList<>();
            nearbyZones100[i] = new ArrayList<>();

            for (int j = 0; j < n; j++) {
                double dist = distMatrix[i][j];
                DeliveryZone zone = deliveryZones.get(j);
                if (dist <= 35.0) nearbyZones35[i].add(new NearbyZoneEntry(zone, dist));
                if (dist <= 80.0) nearbyZones80[i].add(new NearbyZoneEntry(zone, dist));
                if (dist <= 100.0) nearbyZones100[i].add(new NearbyZoneEntry(zone, dist));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ZONE LOOKUP
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Finds a zone by its ID — O(1) via map index.
     */
    public DeliveryZone findZoneByZoneId(String zoneId) {
        return zoneIndex.get(zoneId);
    }

    /**
     * Returns the list index for a zone ID — O(1).
     * Used for O-D matrix lookups and distance matrix lookups.
     */
    public int getZoneListIndex(String zoneId) {
        Integer idx = zoneIdToListIndex.get(zoneId);
        return (idx != null) ? idx : -1;
    }

    /**
     * Finds which zone contains a given point — O(log n) via STRtree.
     */
    @SuppressWarnings("unchecked")
    public DeliveryZone findZone(double lon, double lat) {
        if (zoneSpatialIndex != null) {
            Envelope searchEnv = new Envelope(lon, lon, lat, lat);
            List<DeliveryZone> candidates = zoneSpatialIndex.query(searchEnv);
            for (DeliveryZone zone : candidates) {
                if (zone.containsPoint(lon, lat)) {
                    return zone;
                }
            }
            return null;
        }

        // Fallback: linear scan (should not happen after setZones)
        for (DeliveryZone zone : deliveryZones) {
            if (zone.containsPoint(lon, lat)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Finds the zone ID for a given location.
     * Uses STRtree spatial index, falls back to nearest zone.
     */
    public String findZoneForLocation(double lon, double lat) {
        DeliveryZone zone = findZone(lon, lat);
        if (zone != null) {
            return zone.getZoneId();
        }

        // Fallback: nearest zone (uses fast equirectangular distance)
        int nearestIndex = findNearestZoneIndex(lon, lat);
        if (nearestIndex >= 0 && nearestIndex < deliveryZones.size()) {
            return deliveryZones.get(nearestIndex).getZoneId();
        }

        return "OUTSIDE";
    }

    /**
     * Finds the index of the nearest zone to a location.
     * Uses equirectangular approximation (no trig functions).
     */
    public int findNearestZoneIndex(double lon, double lat) {
        if (deliveryZones.isEmpty()) {
            return -1;
        }

        int nearestIndex = 0;
        double minDistSq = Double.MAX_VALUE;
        double cosLat = Math.cos(Math.toRadians(lat));

        for (int i = 0; i < deliveryZones.size(); i++) {
            DeliveryZone zone = deliveryZones.get(i);
            double dLon = (zone.getCenterLongitude() - lon) * cosLat;
            double dLat = zone.getCenterLatitude() - lat;
            double distSq = dLon * dLon + dLat * dLat;
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }

    /**
     * Finds the nearest zone ID to a given location.
     */
    public String findNearestZone(double lon, double lat) {
        int idx = findNearestZoneIndex(lon, lat);
        if (idx >= 0 && idx < deliveryZones.size()) {
            return deliveryZones.get(idx).getZoneId();
        }
        return "DZ01";
    }

    /**
     * Finds a zone by its human-readable name or zone ID.
     */
    public DeliveryZone findZoneByRegionName(String name) {
        for (DeliveryZone zone : deliveryZones) {
            if (zone.getName().equalsIgnoreCase(name) || zone.getZoneId().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Gets the time period (hour of day) from simulation time.
     */
    public int getTimePeriod(long currentTime) {
        return config.getTimePeriod(currentTime);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DISTANCE CALCULATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns pre-computed zone-to-zone distance — O(1).
     *
     * @param zoneIndex1 List index of first zone
     * @param zoneIndex2 List index of second zone
     * @return Network distance in km
     */
    public double getZoneDistance(int zoneIndex1, int zoneIndex2) {
        return distMatrix[zoneIndex1][zoneIndex2];
    }

    /**
     * Returns pre-computed nearby zones within a distance threshold — O(1).
     *
     * @param zoneListIndex List index of the reference zone
     * @param maxDistKm     Distance threshold (35, 80, or 100 km)
     * @return List of nearby zone entries
     */
    public List<NearbyZoneEntry> getNearbyZones(int zoneListIndex, double maxDistKm) {
        if (zoneListIndex < 0 || zoneListIndex >= deliveryZones.size()) {
            return Collections.emptyList();
        }
        if (maxDistKm <= 35.0) return nearbyZones35[zoneListIndex];
        if (maxDistKm <= 80.0) return nearbyZones80[zoneListIndex];
        return nearbyZones100[zoneListIndex];
    }

    /**
     * Calculates the network distance between two points using
     * Haversine formula with Manhattan correction factor.
     */
    public double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadiusKm * c * manhattanFactor;
    }

    /**
     * Fast equirectangular distance approximation — no trig functions.
     * Accurate to &lt;0.5% for Tokyo-scale distances (&lt;500km).
     * Uses pre-computed cosine at midpoint latitude.
     */
    public double calculateDistanceFast(double lon1, double lat1, double lon2, double lat2) {
        double midLat = (lat1 + lat2) * 0.5;
        double cosLat = Math.cos(Math.toRadians(midLat));
        double dLon = (lon2 - lon1) * cosLat;
        double dLat = lat2 - lat1;
        return Math.sqrt(dLon * dLon + dLat * dLat) * 111.0 * manhattanFactor;
    }
}
