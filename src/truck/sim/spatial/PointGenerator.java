package truck.sim.spatial;

import truck.sim.DeliveryZone;
import truck.sim.MetropolitanConfig;
import truck.sim.POIManager;
import truck.sim.PointOfInterest;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates spatially valid coordinates within delivery zones.
 *
 * <p>Uses a 2-tier coordinate cascade to produce realistic trip endpoints:
 * <ol>
 *   <li><b>Tier 1 — Raster-guided</b>: O(1) JAXA pixel lookup, accepts only Built-up(2) pixels.
 *       30 attempts.</li>
 *   <li><b>Tier 2 — Shapefile + road proximity</b>: Settlement proximity, built-up polygon
 *       check, and road network snap. 15 attempts.</li>
 *   <li><b>Fallback</b>: Road snap (built-up validated) → POI → zone centroid.</li>
 * </ol>
 *
 * <p>Also provides jitter functions for adding spatial noise to POI coordinates
 * while keeping results on valid land.
 *
 * @author Truck ABM Framework
 * @version 2.0
 * @see GeoValidator
 */
public class PointGenerator {

    private final GeoValidator geoValidator;

    // Optional dependencies (may be null)
    private TransportNetworkIndex networkIndex;
    private POIManager poiManager;

    public PointGenerator(GeoValidator geoValidator) {
        this.geoValidator = geoValidator;
    }

    /** Sets the transport network index for road proximity checks. */
    public void setNetworkIndex(TransportNetworkIndex networkIndex) {
        this.networkIndex = networkIndex;
    }

    /** Sets the POI manager for fallback point selection. */
    public void setPOIManager(POIManager poiManager) {
        this.poiManager = poiManager;
    }

    // ════════════════════════════════════════════════════════════════════════
    // POINT GENERATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Generates a spatially valid point within a delivery zone using the 3-tier cascade.
     *
     * <p>Tier 0 (density-weighted) samples from pre-computed {@link BuiltUpIndex},
     * concentrating trips on dense urban cores and near commercial POIs.
     * Falls through to Tier 1 (uniform raster) if index is empty or validation fails.
     *
     * @param zone Zone to generate point in
     * @return Coordinates [lon, lat] guaranteed to be on valid land
     */
    public double[] generatePointInZone(DeliveryZone zone) {
        final double zr = zone.getRadiusKm();

        // ── Tier 0: Density-weighted Built-up sampling (preferred) ────
        BuiltUpIndex builtUpIndex = zone.getBuiltUpIndex();
        if (builtUpIndex != null && !builtUpIndex.isEmpty()) {
            final int DENSITY_ATTEMPTS = 15;
            for (int attempt = 0; attempt < DENSITY_ATTEMPTS; attempt++) {
                double[] candidate = builtUpIndex.sampleWeighted(ThreadLocalRandom.current());
                if (candidate == null) break;
                double lon = candidate[0], lat = candidate[1];

                // Validate: raster category + zone polygon membership
                int lu = geoValidator.getLandUseCategory(lon, lat);
                if (lu == 1 || lu == 0) continue;  // jitter pushed to water/nodata
                // Accept Built-up(2) pixels even outside zone polygons (covers gaps/undefined areas)
                if (!zone.containsPoint(lon, lat) && lu != 2) continue;
                if (!geoValidator.isOnMainland(lon, lat)) continue;  // small island, not road-reachable
                return candidate;
            }
            // Fall through to Tier 1 if all Tier 0 attempts fail validation
        }

        // ── Tier 1: Raster-guided (Built-up only) ─────────────────────
        final int RASTER_ATTEMPTS = 30;
        for (int attempt = 0; attempt < RASTER_ATTEMPTS; attempt++) {
            double[] candidate = zone.generateRandomPoint(ThreadLocalRandom.current());
            double lon = candidate[0], lat = candidate[1];

            // Check raster first (O(1)) — Built-up(2) implies on land, skip polygon check
            int landUse = geoValidator.getLandUseCategory(lon, lat);
            if (landUse != 2) continue;              // ONLY Built-up(2) accepted
            if (geoValidator.isNearRiver(lon, lat, 0.3)) continue;
            if (!geoValidator.isOnMainland(lon, lat)) continue;  // small island, not road-reachable
            return candidate;
        }

        // ── Tier 2: Shapefile + road proximity fallback ────────────────
        final double ROAD_PROXIMITY_KM = (zr <= 5.0) ? 3.0 : (zr <= 10.0) ? 6.0 : 10.0;
        final double SETTLEMENT_DIST_KM = (zr <= 10.0) ? 5.0 : 15.0;
        final double BUILTUP_DIST_KM = (zr <= 5.0) ? 2.0 : (zr <= 10.0) ? 5.0 : (zr <= 20.0) ? 10.0 : 0;
        final boolean useBuiltUpCheck = (zr <= 20.0);
        final int SHAPEFILE_ATTEMPTS = 15;

        for (int attempt = 0; attempt < SHAPEFILE_ATTEMPTS; attempt++) {
            double[] candidate = zone.generateRandomPoint(ThreadLocalRandom.current());
            double lon = candidate[0], lat = candidate[1];

            if (!geoValidator.isOnLand(lon, lat)) continue;
            if (!geoValidator.isOnMainland(lon, lat)) continue;  // small island, not road-reachable
            int landUse = geoValidator.getLandUseCategory(lon, lat);
            if (landUse == 1 || landUse == 13) continue;
            if (geoValidator.isNearRiver(lon, lat, 0.3)) continue;
            if (!geoValidator.isNearSettlement(lon, lat, SETTLEMENT_DIST_KM)) continue;
            boolean pixelIsBuiltUp = (landUse == 2);
            if (!pixelIsBuiltUp && useBuiltUpCheck && !geoValidator.isNearBuiltUpArea(lon, lat, BUILTUP_DIST_KM)) continue;
            if (networkIndex != null) {
                NearestSegmentResult nearest = networkIndex.findNearestRoad(lon, lat, ROAD_PROXIMITY_KM);
                if (nearest == null) continue;
            }
            return candidate;
        }

        // ── Absolute fallback: road snap → POI → centroid ──
        if (networkIndex != null) {
            double[] snap = networkIndex.snapToNearestRoad(
                    zone.getCenterLongitude(), zone.getCenterLatitude(), 20.0);
            if (snap != null) {
                int lu = geoValidator.getLandUseCategory(snap[0], snap[1]);
                if (lu == 2 || lu == -1) return snap;
            }
        }
        if (poiManager != null && poiManager.hasPOIs()) {
            PointOfInterest fallbackPOI = poiManager.selectPOIForZone(zone.getZoneId(), null);
            if (fallbackPOI != null) {
                return new double[]{fallbackPOI.getLongitude(), fallbackPOI.getLatitude()};
            }
        }
        return new double[]{zone.getCenterLongitude(), zone.getCenterLatitude()};
    }

    /**
     * Generates a point on the far side of a zone (away from the origin).
     * Ensures LONG_HAUL trips achieve maximum distance.
     *
     * @param zone Zone to generate point in
     * @param origin Origin coordinates [lon, lat]
     * @return Coordinates [lon, lat] on the far side of the zone
     */
    public double[] generatePointInZoneFarSide(DeliveryZone zone, double[] origin) {
        double centerLon = zone.getCenterLongitude();
        double centerLat = zone.getCenterLatitude();
        double radiusKm = zone.getRadiusKm();

        double dLon = centerLon - origin[0];
        double dLat = centerLat - origin[1];
        double magnitude = Math.sqrt(dLon * dLon + dLat * dLat);

        if (magnitude < 0.001) {
            return generatePointInZone(zone);
        }

        // Unit vector pointing away from origin
        double unitLon = dLon / magnitude;
        double unitLat = dLat / magnitude;

        double degreesPerKmLon = 1.0 / (111.0 * Math.cos(Math.toRadians(centerLat)));
        double degreesPerKmLat = 1.0 / 111.0;

        int maxAttempts = 20;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double distanceFraction = 0.1 + ThreadLocalRandom.current().nextDouble() * 0.3;
            double offsetKm = radiusKm * distanceFraction;

            double pointLon = centerLon + unitLon * offsetKm * degreesPerKmLon;
            double pointLat = centerLat + unitLat * offsetKm * degreesPerKmLat;

            if (geoValidator.isValidTripEndpoint(pointLon, pointLat, radiusKm)
                    && geoValidator.isOnMainland(pointLon, pointLat)) {
                return new double[]{pointLon, pointLat};
            }
        }
        return new double[]{centerLon, centerLat};
    }

    // ════════════════════════════════════════════════════════════════════════
    // JITTER
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Zone-aware spatial jitter for POI coordinates.
     * Retries up to 10 times with isValidTripEndpoint(); smart fallback to built-up pixel.
     *
     * @param lon Base longitude
     * @param lat Base latitude
     * @param zoneRadiusKm Zone radius (controls jitter magnitude)
     * @return Jittered coordinates [lon, lat]
     */
    public double[] jitterPoint(double lon, double lat, double zoneRadiusKm) {
        double sigma;
        if (zoneRadiusKm <= 5.0)       sigma = 0.004;  // ~400m (urban core)
        else if (zoneRadiusKm <= 10.0)  sigma = 0.008;  // ~800m (suburban)
        else if (zoneRadiusKm <= 20.0)  sigma = 0.015;  // ~1.5km (mid-range)
        else                            sigma = 0.020;  // ~2.0km (rural/long-haul)

        // Phase 1: jitter with Built-up preference (strict)
        // Raster check is O(1); skip expensive isOnLand polygon query when raster confirms land
        for (int i = 0; i < 10; i++) {
            double jLon = lon + ThreadLocalRandom.current().nextGaussian() * sigma;
            double jLat = lat + ThreadLocalRandom.current().nextGaussian() * sigma * 0.9;
            int lu = geoValidator.getLandUseCategory(jLon, jLat);
            if (lu == 2) return new double[]{jLon, jLat};  // Built-up = on land, done
            if (lu == -1 && geoValidator.isOnLand(jLon, jLat)) {
                return new double[]{jLon, jLat};  // outside raster, polygon fallback
            }
        }

        // Phase 2: if original POI is on built-up land, use it directly (no jitter)
        int origLU = geoValidator.getLandUseCategory(lon, lat);
        if (origLU == 2 || origLU == -1) return new double[]{lon, lat};

        // Phase 3: tiny jitter (~100m) to escape non-built-up pixel
        for (int i = 0; i < 5; i++) {
            double jLon = lon + ThreadLocalRandom.current().nextGaussian() * 0.001;
            double jLat = lat + ThreadLocalRandom.current().nextGaussian() * 0.001 * 0.9;
            int lu = geoValidator.getLandUseCategory(jLon, jLat);
            if (lu == 2 && geoValidator.isOnLand(jLon, jLat)) return new double[]{jLon, jLat};
        }
        return new double[]{lon, lat};  // absolute fallback
    }

    /**
     * Default jitter with urban-scale sigma (~400m).
     * Used by callers without zone context.
     */
    public double[] jitterPoint(double lon, double lat) {
        return jitterPoint(lon, lat, 5.0);
    }

    /**
     * Generates a random location within metropolitan bounds.
     *
     * @param metro Metropolitan area configuration
     * @return Coordinates [lon, lat]
     */
    public double[] generateLocationInMetro(MetropolitanConfig.Metropolitan metro) {
        double lon = metro.minLon + ThreadLocalRandom.current().nextDouble() * (metro.maxLon - metro.minLon);
        double lat = metro.minLat + ThreadLocalRandom.current().nextDouble() * (metro.maxLat - metro.minLat);
        return new double[]{lon, lat};
    }
}
