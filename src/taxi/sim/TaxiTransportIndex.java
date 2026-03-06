package taxi.sim;

import dcity.aggr.ShpLoader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spatial index for transport infrastructure and settlements from Japan-wide shapefiles.
 *
 * <p>Provides O(log n) spatial queries against:
 * <ul>
 *   <li>{@code rstatp_jpn.shp} — 108 major railway station points</li>
 *   <li>{@code airp_jpn.shp} — 92 airport points</li>
 *   <li>{@code builtupp_jpn.shp} — 1,640 settlement/city center points</li>
 *   <li>{@code portp_jpn.shp} — 128 port points</li>
 * </ul>
 *
 * <p>Used to enrich {@link DestinationZone} with transport proximity metadata
 * and to discover additional zone candidates from shapefiles at runtime.
 * Supports both single-nearest queries and radius-based bulk queries.
 *
 * <p>Reuses {@link dcity.aggr.ShpLoader} for CRS-aware shapefile loading.
 * Builds its own STRtree indexes (rather than using {@link dcity.aggr.Boundaries})
 * because we need geometry→name mapping that Boundaries doesn't support.
 *
 * @version 1.1
 */
public class TaxiTransportIndex {

    // ── Spatial indexes ────────────────────────────────────────────────────
    private STRtree stationIndex;
    private STRtree airportIndex;
    private STRtree settlementIndex;
    private STRtree portIndex;
    private final GeometryFactory geometryFactory;

    // ── Name lookup maps ───────────────────────────────────────────────────
    private Map<Geometry, String> stationNames;
    private Map<Geometry, String> airportNames;
    private Map<Geometry, String> settlementNames;
    private Map<Geometry, String> portNames;

    // ── Counts ─────────────────────────────────────────────────────────────
    private int stationCount;
    private int airportCount;
    private int settlementCount;
    private int portCount;

    /**
     * Creates a new empty transport index.
     * Call {@link #loadStations} and {@link #loadAirports} to populate.
     */
    public TaxiTransportIndex() {
        this.geometryFactory = new GeometryFactory();
        this.stationNames = new HashMap<>();
        this.airportNames = new HashMap<>();
        this.settlementNames = new HashMap<>();
        this.portNames = new HashMap<>();
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Loads railway station points from rstatp_jpn.shp.
     * Attributes used: {@code nam} (station name), {@code f_code} (feature code).
     * Non-fatal: logs warning and continues if file is missing.
     *
     * @param shapefilePath Path to rstatp_jpn.shp
     */
    public void loadStations(String shapefilePath) {
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[TRANSPORT] Station shapefile not found: " + shapefilePath +
                    " — station proximity features disabled");
                return;
            }

            List<SimpleFeature> features = ShpLoader.load(shapefilePath);
            stationIndex = new STRtree();

            for (SimpleFeature feature : features) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom == null) continue;

                String name = (String) feature.getAttribute("nam");
                if (name == null) name = "Unknown Station";

                stationNames.put(geom, toTitleCase(name.trim()));
                stationIndex.insert(geom.getEnvelopeInternal(), geom);
                stationCount++;
            }

            // Build the index for query efficiency
            stationIndex.build();

            System.out.println("[TRANSPORT] Loaded " + stationCount +
                " railway station points for proximity analysis");

        } catch (Exception e) {
            System.err.println("[TRANSPORT] Warning: Failed to load stations: " + e.getMessage() +
                " — station proximity features disabled");
            stationIndex = null;
        }
    }

    /**
     * Loads airport points from airp_jpn.shp.
     * Attributes used: {@code nam} (airport name), {@code iko} (ICAO-style code).
     * Non-fatal: logs warning and continues if file is missing.
     *
     * @param shapefilePath Path to airp_jpn.shp
     */
    public void loadAirports(String shapefilePath) {
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[TRANSPORT] Airport shapefile not found: " + shapefilePath +
                    " — airport proximity features disabled");
                return;
            }

            List<SimpleFeature> features = ShpLoader.load(shapefilePath);
            airportIndex = new STRtree();

            for (SimpleFeature feature : features) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom == null) continue;

                String name = (String) feature.getAttribute("nam");
                if (name == null) name = "Unknown Airport";

                airportNames.put(geom, toTitleCase(name.trim()));
                airportIndex.insert(geom.getEnvelopeInternal(), geom);
                airportCount++;
            }

            // Build the index for query efficiency
            airportIndex.build();

            System.out.println("[TRANSPORT] Loaded " + airportCount +
                " airport points for proximity analysis");

        } catch (Exception e) {
            System.err.println("[TRANSPORT] Warning: Failed to load airports: " + e.getMessage() +
                " — airport proximity features disabled");
            airportIndex = null;
        }
    }

    /**
     * Loads settlement/city center points from builtupp_jpn.shp.
     * Attributes used: {@code nam} (settlement name).
     * Non-fatal: logs warning and continues if file is missing.
     *
     * @param shapefilePath Path to builtupp_jpn.shp
     */
    public void loadSettlements(String shapefilePath) {
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[TRANSPORT] Settlement shapefile not found: " + shapefilePath +
                    " — settlement enrichment disabled");
                return;
            }

            List<SimpleFeature> features = ShpLoader.load(shapefilePath);
            settlementIndex = new STRtree();

            for (SimpleFeature feature : features) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom == null) continue;

                String name = (String) feature.getAttribute("nam");
                if (name == null) name = "Unknown Settlement";

                settlementNames.put(geom, toTitleCase(name.trim()));
                settlementIndex.insert(geom.getEnvelopeInternal(), geom);
                settlementCount++;
            }

            settlementIndex.build();

            System.out.println("[TRANSPORT] Loaded " + settlementCount +
                " settlement points for zone enrichment");

        } catch (Exception e) {
            System.err.println("[TRANSPORT] Warning: Failed to load settlements: " + e.getMessage() +
                " — settlement enrichment disabled");
            settlementIndex = null;
        }
    }

    /**
     * Loads port points from portp_jpn.shp.
     * Attributes used: {@code nam} (port name).
     * Non-fatal: logs warning and continues if file is missing.
     *
     * @param shapefilePath Path to portp_jpn.shp
     */
    public void loadPorts(String shapefilePath) {
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[TRANSPORT] Port shapefile not found: " + shapefilePath +
                    " — port enrichment disabled");
                return;
            }

            List<SimpleFeature> features = ShpLoader.load(shapefilePath);
            portIndex = new STRtree();

            for (SimpleFeature feature : features) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom == null) continue;

                String name = (String) feature.getAttribute("nam");
                if (name == null) name = "Unknown Port";

                portNames.put(geom, toTitleCase(name.trim()));
                portIndex.insert(geom.getEnvelopeInternal(), geom);
                portCount++;
            }

            portIndex.build();

            System.out.println("[TRANSPORT] Loaded " + portCount +
                " port points for zone enrichment");

        } catch (Exception e) {
            System.err.println("[TRANSPORT] Warning: Failed to load ports: " + e.getMessage() +
                " — port enrichment disabled");
            portIndex = null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // QUERY METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Finds the nearest railway station to the given coordinates.
     *
     * @param lon Longitude (EPSG:4326)
     * @param lat Latitude (EPSG:4326)
     * @param maxDistKm Maximum search distance in kilometers
     * @return NearestResult with distance and station info, or null if none found
     */
    public NearestResult findNearestStation(double lon, double lat, double maxDistKm) {
        return findNearest(stationIndex, stationNames, lon, lat, maxDistKm);
    }

    /**
     * Finds the nearest airport to the given coordinates.
     *
     * @param lon Longitude (EPSG:4326)
     * @param lat Latitude (EPSG:4326)
     * @param maxDistKm Maximum search distance in kilometers
     * @return NearestResult with distance and airport info, or null if none found
     */
    public NearestResult findNearestAirport(double lon, double lat, double maxDistKm) {
        return findNearest(airportIndex, airportNames, lon, lat, maxDistKm);
    }

    /**
     * Checks if a point is within the specified distance of any airport.
     *
     * @param lon Longitude
     * @param lat Latitude
     * @param distKm Distance threshold in kilometers
     * @return true if within distance of an airport
     */
    public boolean isNearAirport(double lon, double lat, double distKm) {
        NearestResult result = findNearestAirport(lon, lat, distKm);
        return result != null;
    }

    /**
     * Counts railway stations within a radius of the given point.
     * Useful for identifying interchange hubs (multiple stations nearby).
     *
     * @param lon Longitude
     * @param lat Latitude
     * @param radiusKm Search radius in kilometers
     * @return Number of stations within radius
     */
    public int countStationsInRadius(double lon, double lat, double radiusKm) {
        if (stationIndex == null) return 0;

        double searchDeg = radiusKm / 111.0;
        Envelope env = new Envelope(
            lon - searchDeg, lon + searchDeg,
            lat - searchDeg, lat + searchDeg
        );

        @SuppressWarnings("unchecked")
        List<Geometry> candidates = stationIndex.query(env);

        int count = 0;
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        for (Geometry g : candidates) {
            double distDeg = point.distance(g);
            double distKm = distDeg * 111.0 * Math.cos(Math.toRadians(lat));
            if (distKm <= radiusKm) {
                count++;
            }
        }
        return count;
    }

    // ════════════════════════════════════════════════════════════════════════
    // RADIUS QUERIES (return all features within radius)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Finds ALL railway stations within a radius of the given point.
     * Unlike {@link #findNearestStation}, returns every match — used for
     * discovering uncovered zone candidates during shapefile enrichment.
     *
     * @param lon Longitude (EPSG:4326)
     * @param lat Latitude (EPSG:4326)
     * @param radiusKm Search radius in kilometers
     * @return List of all stations within radius (empty list if none or index not loaded)
     */
    public List<NearestResult> findStationsInRadius(double lon, double lat, double radiusKm) {
        return findAllInRadius(stationIndex, stationNames, lon, lat, radiusKm);
    }

    /**
     * Finds ALL airports within a radius of the given point.
     *
     * @param lon Longitude (EPSG:4326)
     * @param lat Latitude (EPSG:4326)
     * @param radiusKm Search radius in kilometers
     * @return List of all airports within radius
     */
    public List<NearestResult> findAirportsInRadius(double lon, double lat, double radiusKm) {
        return findAllInRadius(airportIndex, airportNames, lon, lat, radiusKm);
    }

    /**
     * Finds ALL settlements within a radius of the given point.
     *
     * @param lon Longitude (EPSG:4326)
     * @param lat Latitude (EPSG:4326)
     * @param radiusKm Search radius in kilometers
     * @return List of all settlements within radius
     */
    public List<NearestResult> findSettlementsInRadius(double lon, double lat, double radiusKm) {
        return findAllInRadius(settlementIndex, settlementNames, lon, lat, radiusKm);
    }

    /**
     * Finds ALL ports within a radius of the given point.
     *
     * @param lon Longitude (EPSG:4326)
     * @param lat Latitude (EPSG:4326)
     * @param radiusKm Search radius in kilometers
     * @return List of all ports within radius
     */
    public List<NearestResult> findPortsInRadius(double lon, double lat, double radiusKm) {
        return findAllInRadius(portIndex, portNames, lon, lat, radiusKm);
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Generic nearest-feature query using STRtree envelope search + Haversine refinement.
     */
    @SuppressWarnings("unchecked")
    private NearestResult findNearest(STRtree index, Map<Geometry, String> names,
                                      double lon, double lat, double maxDistKm) {
        if (index == null) return null;

        double searchDeg = maxDistKm / 111.0;
        Envelope env = new Envelope(
            lon - searchDeg, lon + searchDeg,
            lat - searchDeg, lat + searchDeg
        );

        List<Geometry> candidates = index.query(env);
        if (candidates.isEmpty()) return null;

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        double minDistKm = Double.MAX_VALUE;
        Geometry nearest = null;

        for (Geometry g : candidates) {
            double distDeg = point.distance(g);
            double distKm = distDeg * 111.0 * Math.cos(Math.toRadians(lat));
            if (distKm < minDistKm && distKm <= maxDistKm) {
                minDistKm = distKm;
                nearest = g;
            }
        }

        if (nearest == null) return null;

        String name = names.getOrDefault(nearest, "Unknown");
        Coordinate coord = nearest.getCoordinate();
        return new NearestResult(minDistKm, name, coord.x, coord.y);
    }

    /**
     * Generic radius query — returns ALL features within the specified radius.
     * Uses the same envelope+distance pattern as {@link #findNearest} but
     * collects all qualifying features instead of tracking only the closest.
     */
    @SuppressWarnings("unchecked")
    private List<NearestResult> findAllInRadius(STRtree index, Map<Geometry, String> names,
                                                 double lon, double lat, double radiusKm) {
        List<NearestResult> results = new ArrayList<>();
        if (index == null) return results;

        double searchDeg = radiusKm / 111.0;
        Envelope env = new Envelope(
            lon - searchDeg, lon + searchDeg,
            lat - searchDeg, lat + searchDeg
        );

        List<Geometry> candidates = index.query(env);
        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));

        for (Geometry g : candidates) {
            double distDeg = point.distance(g);
            double distKm = distDeg * 111.0 * Math.cos(Math.toRadians(lat));
            if (distKm <= radiusKm) {
                String name = names.getOrDefault(g, "Unknown");
                Coordinate coord = g.getCoordinate();
                results.add(new NearestResult(distKm, name, coord.x, coord.y));
            }
        }
        return results;
    }

    /**
     * Converts ALL-CAPS shapefile names to Title Case for display.
     * E.g., "YOKOHAMA" → "Yokohama", "SHIN-YOKOHAMA" → "Shin-Yokohama".
     */
    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == ' ' || c == '-' || c == '\'') {
                sb.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    // RESULT CLASS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Result of a nearest-feature query.
     * Contains the distance, name, and coordinates of the nearest feature.
     */
    public static class NearestResult {
        /** Distance from query point to feature in kilometers */
        public final double distanceKm;
        /** Feature name (from "nam" shapefile attribute) */
        public final String name;
        /** Feature longitude (EPSG:4326) */
        public final double lon;
        /** Feature latitude (EPSG:4326) */
        public final double lat;

        public NearestResult(double distanceKm, String name, double lon, double lat) {
            this.distanceKm = distanceKm;
            this.name = name;
            this.lon = lon;
            this.lat = lat;
        }

        @Override
        public String toString() {
            return String.format("%s (%.1fkm at %.4f,%.4f)", name, distanceKm, lon, lat);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ════════════════════════════════════════════════════════════════════════

    /** @return Number of loaded station points */
    public int getStationCount() { return stationCount; }

    /** @return Number of loaded airport points */
    public int getAirportCount() { return airportCount; }

    /** @return Number of loaded settlement points */
    public int getSettlementCount() { return settlementCount; }

    /** @return Number of loaded port points */
    public int getPortCount() { return portCount; }

    /** @return true if station data is loaded and queryable */
    public boolean hasStations() { return stationIndex != null && stationCount > 0; }

    /** @return true if airport data is loaded and queryable */
    public boolean hasAirports() { return airportIndex != null && airportCount > 0; }

    /** @return true if settlement data is loaded and queryable */
    public boolean hasSettlements() { return settlementIndex != null && settlementCount > 0; }

    /** @return true if port data is loaded and queryable */
    public boolean hasPorts() { return portIndex != null && portCount > 0; }
}
