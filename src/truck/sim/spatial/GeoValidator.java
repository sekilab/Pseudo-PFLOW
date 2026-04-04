package truck.sim.spatial;

import dcity.aggr.Boundaries;
import dcity.aggr.ShpLoader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.opengis.feature.simple.SimpleFeature;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geographic validation engine for the truck simulation.
 *
 * <p>Provides multi-layer spatial validation to ensure that trip origins,
 * destinations, and POI locations fall on valid driveable land. Validation
 * layers include:
 * <ul>
 *   <li>Administrative land boundary polygons (polbnda_jpn.shp)</li>
 *   <li>Inland water body exclusion (inwatera_jpn.shp)</li>
 *   <li>Settlement proximity filtering (builtupp_jpn.shp)</li>
 *   <li>Built-up area polygon proximity (builtupa_jpn.shp)</li>
 *   <li>River centerline buffer exclusion (riverl_jpn.shp)</li>
 *   <li>JAXA land use raster pixel classification (50m primary + 100m Hokkaido extension)</li>
 * </ul>
 *
 * <p>All layers degrade gracefully — if a shapefile is missing, the
 * corresponding validation is skipped rather than failing the simulation.
 *
 * @author Truck ABM Framework
 * @version 2.0
 */
public class GeoValidator {

    // ── Shapefile-based spatial layers ──────────────────────────────────────
    private Boundaries japanBoundaries;
    private Boundaries waterBodies;
    private Boundaries settlements;
    private Boundaries builtUpAreas;
    private Boundaries riverLines;
    private final GeometryFactory geometryFactory;

    // ── Mainland island filter (4 main islands: Honshu, Hokkaido, Kyushu, Shikoku) ──
    private PreparedGeometry[] mainlandPolygons;
    private final Map<Long, Boolean> mainlandCache = new ConcurrentHashMap<>();

    // ── JAXA land use rasters ───────────────────────────────────────────────
    // Primary: 50m resolution (Kanto/Chubu/Tohoku)
    private byte[] landUseRaster;
    private double luMinLon, luMaxLon, luMinLat, luMaxLat;
    private int luWidth, luHeight;
    // Extension: 100m resolution (Hokkaido) — fallback for points outside primary bounds
    private byte[] landUseRasterExt;
    private double luMinLon2, luMaxLon2, luMinLat2, luMaxLat2;
    private int luWidth2, luHeight2;

    // ── Cache for land validation (round to ~50m resolution) ───────────────
    // Long-keyed cache avoids String allocation overhead in hot loop.
    // Key encoding: upper 32 bits = (lon * 1000) as int, lower 32 bits = (lat * 1000) as int
    private final Map<Long, Boolean> landValidationCache = new ConcurrentHashMap<>();

    public GeoValidator() {
        this.geometryFactory = new GeometryFactory();
    }

    // ════════════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Loads Japan land boundary polygons (polbnda_jpn.shp).
     * This is the primary spatial layer — required for simulation.
     *
     * @throws RuntimeException if shapefile cannot be loaded
     */
    public void loadJapanBoundaries() {
        String shapefilePath = "src/shared/gm-jp/polbnda_jpn_new.shp";
        System.out.println("[BOUNDARY] Loading Japan land boundaries from: " + shapefilePath);

        try {
            japanBoundaries = Boundaries.create(shapefilePath);
            System.out.println("[BOUNDARY] Successfully loaded " + japanBoundaries.size() +
                " land boundary polygons");
            System.out.println("[BOUNDARY] Spatial index ready for land validation");
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load Japan boundaries: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Cannot run simulation without land boundaries", e);
        }
    }

    /**
     * Loads simplified mainland Japan polygons (4 main islands only).
     * Used to filter out trip endpoints on small road-unreachable islands
     * (Izu, Ogasawara, Okinawa, Rishiri, Northern Territories, etc.).
     * Non-fatal: if file missing, mainland filtering is disabled.
     */
    public void loadMainlandBoundaries() {
        String shapefilePath = "src/shared/gm-jp/mainland_jpn.shp";
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[MAINLAND] Mainland shapefile not found: " + shapefilePath
                    + " — island filtering disabled");
                return;
            }
            List<SimpleFeature> features = ShpLoader.load(shapefilePath);
            mainlandPolygons = new PreparedGeometry[features.size()];
            for (int i = 0; i < features.size(); i++) {
                Geometry geom = (Geometry) features.get(i).getDefaultGeometry();
                mainlandPolygons[i] = PreparedGeometryFactory.prepare(geom);
            }
            System.out.println("[MAINLAND] Loaded " + mainlandPolygons.length
                + " mainland island polygons for road-reachability filtering");
        } catch (Exception e) {
            System.err.println("[MAINLAND] Warning: Failed to load mainland boundaries: "
                + e.getMessage() + " — island filtering disabled");
            mainlandPolygons = null;
        }
    }

    /**
     * Loads inland water body polygons for exclusion from land validation.
     * polbnda_jpn.shp treats Lake Biwa as inside Shiga prefecture.
     * inwatera_jpn.shp provides explicit water polygon exclusion.
     * Non-fatal: if file missing, isOnLand() falls back to admin-boundary-only validation.
     */
    public void loadWaterBodies() {
        String shapefilePath = "src/shared/gm-jp/inwatera_jpn.shp";
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[BOUNDARY] Water bodies shapefile not found: " + shapefilePath
                    + " — inland water exclusion disabled");
                return;
            }
            waterBodies = Boundaries.create(shapefilePath);
            System.out.println("[BOUNDARY] Loaded " + waterBodies.size()
                + " inland water body polygons (Lake Biwa, rivers, etc.)");
        } catch (Exception e) {
            System.err.println("[BOUNDARY] Warning: Failed to load water bodies: " + e.getMessage()
                + " — inland water exclusion disabled");
            waterBodies = null;
        }
    }

    /**
     * Loads settlement center points for city-aware point generation.
     * builtupp_jpn.shp contains 1,640 settlement points (towns, villages, cities).
     * Non-fatal: if file missing, settlement proximity check is skipped.
     */
    public void loadSettlements() {
        String shapefilePath = "src/shared/gm-jp/builtupp_jpn.shp";
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[SETTLEMENT] Settlement points file not found: " + shapefilePath
                    + " — city-aware filtering disabled");
                return;
            }
            settlements = Boundaries.create(shapefilePath);
            System.out.println("[SETTLEMENT] Loaded " + settlements.size()
                + " settlement center points for city-aware filtering");
        } catch (Exception e) {
            System.err.println("[SETTLEMENT] Warning: Failed to load settlements: " + e.getMessage()
                + " — city-aware filtering disabled");
            settlements = null;
        }
    }

    /**
     * Loads built-up area polygons for developed-land proximity filtering.
     * builtupa_jpn.shp contains 221 urban/built-up area polygons.
     * Non-fatal: if file missing, built-up area proximity check is skipped.
     */
    public void loadBuiltUpAreas() {
        String shapefilePath = "src/shared/gm-jp/builtupa_jpn.shp";
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[BUILTUP] Built-up area shapefile not found: " + shapefilePath
                    + " — built-up area filtering disabled");
                return;
            }
            builtUpAreas = Boundaries.create(shapefilePath);
            System.out.println("[BUILTUP] Loaded " + builtUpAreas.size()
                + " built-up area polygons for developed-land filtering");
        } catch (Exception e) {
            System.err.println("[BUILTUP] Warning: Failed to load built-up areas: " + e.getMessage()
                + " — built-up area filtering disabled");
            builtUpAreas = null;
        }
    }

    /**
     * Loads river line geometries for river channel exclusion buffering.
     * riverl_jpn.shp contains 4,795 river/stream LINE features.
     * Non-fatal: if file missing, river buffer exclusion is disabled.
     */
    public void loadRiverLines() {
        String shapefilePath = "src/shared/gm-jp/riverl_jpn.shp";
        try {
            java.io.File shapefile = new java.io.File(shapefilePath);
            if (!shapefile.exists()) {
                System.err.println("[RIVER] River lines shapefile not found: " + shapefilePath
                    + " — river buffer exclusion disabled");
                return;
            }
            riverLines = Boundaries.create(shapefilePath);
            System.out.println("[RIVER] Loaded " + riverLines.size()
                + " river line segments for exclusion buffering");
        } catch (Exception e) {
            System.err.println("[RIVER] Warning: Failed to load river lines: " + e.getMessage()
                + " — river buffer exclusion disabled");
            riverLines = null;
        }
    }

    /**
     * Loads preprocessed JAXA 50m land use raster for pixel-level land classification.
     * Binary format: 40-byte header (4 doubles + 2 ints, big-endian) + raw uint8 pixels.
     * Categories: 0=NoData, 1=Water, 2=Built-up, 3=Paddy, 4=Cropland, 5=Grassland,
     *             6-9,11=Forest, 10=Bare, 12=Solar, 13=Wetland, 14=Greenhouse, 15=Rock/Tidal.
     * Non-fatal: if file missing, pixel-level classification is disabled.
     */
    public void loadLandUseRaster() {
        String rasterPath = "config/truck/landuse_raster.bin";
        try {
            java.io.File rasterFile = new java.io.File(rasterPath);
            if (!rasterFile.exists()) {
                System.err.println("[LANDUSE] Land use raster not found: " + rasterPath
                    + " — pixel-level land classification disabled");
                System.err.println("[LANDUSE] Generate with: python tools/extract_landuse.py");
                return;
            }

            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.BufferedInputStream(new java.io.FileInputStream(rasterFile)));
            luMinLon = dis.readDouble();
            luMaxLon = dis.readDouble();
            luMinLat = dis.readDouble();
            luMaxLat = dis.readDouble();
            luWidth  = dis.readInt();
            luHeight = dis.readInt();

            landUseRaster = new byte[luWidth * luHeight];
            dis.readFully(landUseRaster);
            dis.close();

            // Count built-up pixels for reporting
            int builtUpCount = 0;
            for (byte b : landUseRaster) {
                if ((b & 0xFF) == 2) builtUpCount++;
            }

            System.out.println("[LANDUSE] Loaded " + luWidth + "×" + luHeight
                + " land use raster (" + (landUseRaster.length / 1024 / 1024) + " MB)"
                + " — bounds: " + String.format("%.1f–%.1f°E, %.1f–%.1f°N",
                    luMinLon, luMaxLon, luMinLat, luMaxLat)
                + " — " + builtUpCount + " built-up pixels ("
                + String.format("%.1f%%", 100.0 * builtUpCount / landUseRaster.length) + ")");
        } catch (Exception e) {
            System.err.println("[LANDUSE] Warning: Failed to load land use raster: " + e.getMessage()
                + " — pixel-level land classification disabled");
            landUseRaster = null;
        }
    }

    /**
     * Loads extended land use raster (100m Hokkaido) for fallback coverage.
     * Same binary format as primary raster. Non-fatal if missing.
     */
    public void loadExtendedLandUseRaster() {
        String rasterPath = "config/truck/landuse_raster_ext.bin";
        try {
            java.io.File rasterFile = new java.io.File(rasterPath);
            if (!rasterFile.exists()) {
                System.out.println("[LANDUSE] Extension raster not found: " + rasterPath
                    + " — Hokkaido pixel classification disabled");
                return;
            }

            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.BufferedInputStream(new java.io.FileInputStream(rasterFile)));
            luMinLon2 = dis.readDouble();
            luMaxLon2 = dis.readDouble();
            luMinLat2 = dis.readDouble();
            luMaxLat2 = dis.readDouble();
            luWidth2  = dis.readInt();
            luHeight2 = dis.readInt();

            landUseRasterExt = new byte[luWidth2 * luHeight2];
            dis.readFully(landUseRasterExt);
            dis.close();

            int builtUpCount = 0;
            for (byte b : landUseRasterExt) {
                if ((b & 0xFF) == 2) builtUpCount++;
            }

            System.out.println("[LANDUSE] Loaded extension " + luWidth2 + "×" + luHeight2
                + " raster (" + (landUseRasterExt.length / 1024 / 1024) + " MB)"
                + " — bounds: " + String.format("%.1f–%.1f°E, %.1f–%.1f°N",
                    luMinLon2, luMaxLon2, luMinLat2, luMaxLat2)
                + " — " + builtUpCount + " built-up pixels");
        } catch (Exception e) {
            System.err.println("[LANDUSE] Warning: Failed to load extension raster: " + e.getMessage());
            landUseRasterExt = null;
        }
    }

    /**
     * Loads all spatial layers in the correct order.
     * Convenience method that calls all individual load methods.
     */
    public void loadAll() {
        loadJapanBoundaries();
        loadMainlandBoundaries();
        loadWaterBodies();
        loadSettlements();
        loadBuiltUpAreas();
        loadRiverLines();
        loadLandUseRaster();
        loadExtendedLandUseRaster();
    }

    // ════════════════════════════════════════════════════════════════════════
    // VALIDATION METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Checks if coordinates fall on one of Japan's 4 main islands
     * (Honshu, Hokkaido, Kyushu, Shikoku). Returns true if mainland
     * data not loaded (graceful degradation).
     */
    public boolean isOnMainland(double lon, double lat) {
        if (mainlandPolygons == null) return true;

        long lonBits = (long) Math.floor(lon * 10000);
        long latBits = (long) Math.floor(lat * 10000);
        long cacheKey = (lonBits << 32) | (latBits & 0xFFFFFFFFL);
        Boolean cached = mainlandCache.get(cacheKey);
        if (cached != null) return cached;

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        boolean result = false;
        for (PreparedGeometry pg : mainlandPolygons) {
            if (pg.covers(point)) {
                result = true;
                break;
            }
        }

        mainlandCache.put(cacheKey, result);
        return result;
    }

    /**
     * Checks if coordinates are on land (driveable surface).
     * Uses shapefile polygon validation with land boundary inclusion
     * and inland water body exclusion.
     *
     * @param lon Longitude (EPSG:4326 WGS84)
     * @param lat Latitude (EPSG:4326 WGS84)
     * @return true if on land, false if in water/ocean
     */
    public boolean isOnLand(double lon, double lat) {
        // Fast raster pre-check: valid land-use codes (2-15) are definitively on land
        // This avoids expensive polygon containment queries for ~95% of calls
        int lu = getLandUseCategory(lon, lat);
        if (lu >= 2 && lu <= 15) return true;  // land pixel (Built-up, Paddy, Forest, etc.)
        if (lu == 1) return false;              // water pixel

        // lu == 0 (NoData) or -1 (outside raster): fall through to polygon check
        if (japanBoundaries == null) {
            return true;
        }

        // Cache key at ~10m resolution (long-based, no allocation)
        long lonBits = (long) Math.floor(lon * 10000);
        long latBits = (long) Math.floor(lat * 10000);
        long cacheKey = (lonBits << 32) | (latBits & 0xFFFFFFFFL);
        Boolean cachedResult = landValidationCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        // Query spatial index with ±0.01° (~1km) envelope
        double r = 0.01;
        List<Geometry> candidates = japanBoundaries.query(
            lon - r, lat - r, lon + r, lat + r
        );

        Coordinate coord = new Coordinate(lon, lat);
        Point point = geometryFactory.createPoint(coord);
        boolean result = false;
        for (Geometry geometry : candidates) {
            if (geometry.covers(point)) {
                result = true;
                break;
            }
        }

        // Inland water body exclusion (Lake Biwa, etc.)
        if (result && waterBodies != null) {
            List<Geometry> waterCandidates = waterBodies.query(
                lon - r, lat - r, lon + r, lat + r
            );
            for (Geometry wg : waterCandidates) {
                if (wg.covers(point)) {
                    result = false;
                    break;
                }
            }
        }

        landValidationCache.put(cacheKey, result);
        return result;
    }

    /**
     * City-aware check: is the point within maxDistKm of any human settlement?
     * Uses builtupp_jpn.shp (1,640 settlement center points) with STRtree spatial index.
     * Returns true if settlements data not loaded (graceful degradation).
     */
    public boolean isNearSettlement(double lon, double lat, double maxDistKm) {
        if (settlements == null) return true;

        double searchDeg = maxDistKm / 111.0;
        List<Geometry> candidates = settlements.query(
            lon - searchDeg, lat - searchDeg,
            lon + searchDeg, lat + searchDeg);

        if (candidates.isEmpty()) return false;

        double cosLat = Math.cos(Math.toRadians(lat));
        for (Geometry g : candidates) {
            Coordinate gc = g.getCoordinate();
            double dLon = (gc.x - lon) * cosLat;
            double dLat = gc.y - lat;
            double distKm = Math.sqrt(dLon * dLon + dLat * dLat) * 111.0;
            if (distKm <= maxDistKm) return true;
        }
        return false;
    }

    /**
     * Built-up area proximity check: is the point within maxDistKm of any built-up area polygon?
     * Uses builtupa_jpn.shp (221 urban/built-up area polygons) with STRtree spatial index.
     * Returns true if builtUpAreas data not loaded (graceful degradation).
     */
    public boolean isNearBuiltUpArea(double lon, double lat, double maxDistKm) {
        if (builtUpAreas == null) return true;

        double searchDeg = maxDistKm / 111.0;
        List<Geometry> candidates = builtUpAreas.query(
            lon - searchDeg, lat - searchDeg,
            lon + searchDeg, lat + searchDeg);

        if (candidates.isEmpty()) return false;

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        double maxDistDeg = maxDistKm / (111.0 * Math.cos(Math.toRadians(lat)));
        for (Geometry g : candidates) {
            if (point.distance(g) <= maxDistDeg) return true;
        }
        return false;
    }

    /**
     * River proximity check: is the point within bufferKm of any river centerline?
     * Uses riverl_jpn.shp (4,795 river/stream LINE features) with STRtree spatial index.
     * Returns false if river data not loaded (no exclusion).
     */
    public boolean isNearRiver(double lon, double lat, double bufferKm) {
        if (riverLines == null) return false;

        double searchDeg = bufferKm / 111.0;
        List<Geometry> candidates = riverLines.query(
            lon - searchDeg, lat - searchDeg,
            lon + searchDeg, lat + searchDeg);

        if (candidates.isEmpty()) return false;

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        double maxDistDeg = bufferKm / (111.0 * Math.cos(Math.toRadians(lat)));
        for (Geometry g : candidates) {
            if (point.distance(g) <= maxDistDeg) return true;
        }
        return false;
    }

    /**
     * JAXA land use pixel lookup — O(1) array index.
     * Tries primary 50m raster first, falls back to 100m extension for Hokkaido.
     *
     * @return Land use category (0-15), or -1 if outside all raster bounds
     */
    public int getLandUseCategory(double lon, double lat) {
        // Primary raster (50m, Kanto/Chubu/Tohoku)
        if (landUseRaster != null) {
            int col = (int) Math.floor((lon - luMinLon) / (luMaxLon - luMinLon) * luWidth);
            int row = (int) Math.floor((luMaxLat - lat) / (luMaxLat - luMinLat) * luHeight);
            if (col >= 0 && col < luWidth && row >= 0 && row < luHeight) {
                return landUseRaster[row * luWidth + col] & 0xFF;
            }
        }
        // Extension raster (100m, Hokkaido)
        if (landUseRasterExt != null) {
            int col = (int) Math.floor((lon - luMinLon2) / (luMaxLon2 - luMinLon2) * luWidth2);
            int row = (int) Math.floor((luMaxLat2 - lat) / (luMaxLat2 - luMinLat2) * luHeight2);
            if (col >= 0 && col < luWidth2 && row >= 0 && row < luHeight2) {
                return landUseRasterExt[row * luWidth2 + col] & 0xFF;
            }
        }
        return -1;
    }

    /**
     * Validates a coordinate against all spatial layers for trip endpoint use.
     * Checks: land polygon, JAXA raster (water/wetland/paddy/cropland), river buffer.
     *
     * @param lon Longitude
     * @param lat Latitude
     * @param zoneRadiusKm Zone radius (paddy/cropland rejected only for zones ≤20km)
     * @return true if coordinate is acceptable for a trip origin or destination
     */
    public boolean isValidTripEndpoint(double lon, double lat, double zoneRadiusKm) {
        if (!isOnLand(lon, lat)) return false;
        int landUse = getLandUseCategory(lon, lat);
        if (landUse == 1 || landUse == 13) return false;                          // water/wetland
        if (zoneRadiusKm <= 20.0 && (landUse == 3 || landUse == 4)) return false; // paddy/cropland
        // Urban zones (≤10km): require Built-up(2), Solar(12), Greenhouse(14), or outside raster(-1)
        if (zoneRadiusKm <= 10.0 && landUse != 2 && landUse != -1
                && landUse != 12 && landUse != 14) return false;
        if (isNearRiver(lon, lat, 0.3)) return false;                             // river buffer
        return true;
    }

    /**
     * Checks if a POI's land use is facility-compatible.
     * Stricter than isValidTripEndpoint: requires Built-up(2), Solar(12), Greenhouse(14),
     * or unknown(-1, outside raster bounds).
     *
     * @param lon Longitude
     * @param lat Latitude
     * @return true if the land use is suitable for a POI/facility
     */
    public boolean isValidPOILandUse(double lon, double lat) {
        int lu = getLandUseCategory(lon, lat);
        return lu == 2 || lu == -1 || lu == 12 || lu == 14;
    }

    // ════════════════════════════════════════════════════════════════════════
    // RASTER DATA ACCESS (for BuiltUpIndex construction)
    // ════════════════════════════════════════════════════════════════════════

    /** @return true if the JAXA land-use raster is loaded and available */
    public boolean hasRaster() { return landUseRaster != null; }

    /** @return Raw JAXA land-use raster (row-major uint8 pixels), or null if not loaded */
    public byte[] getLandUseRaster() { return landUseRaster; }

    /** @return Raster west bound (degrees longitude) */
    public double getRasterMinLon() { return luMinLon; }

    /** @return Raster east bound (degrees longitude) */
    public double getRasterMaxLon() { return luMaxLon; }

    /** @return Raster south bound (degrees latitude) */
    public double getRasterMinLat() { return luMinLat; }

    /** @return Raster north bound (degrees latitude) */
    public double getRasterMaxLat() { return luMaxLat; }

    /** @return Raster width in pixels (columns) */
    public int getRasterWidth() { return luWidth; }

    /** @return Raster height in pixels (rows) */
    public int getRasterHeight() { return luHeight; }

    // ── Extension raster (Hokkaido 100m) accessors ──────────────────────────

    /** @return true if extension raster is loaded */
    public boolean hasExtensionRaster() { return landUseRasterExt != null; }

    /** @return Extension raster byte array, or null */
    public byte[] getExtensionRaster() { return landUseRasterExt; }

    /** @return Extension raster west bound */
    public double getExtRasterMinLon() { return luMinLon2; }

    /** @return Extension raster east bound */
    public double getExtRasterMaxLon() { return luMaxLon2; }

    /** @return Extension raster south bound */
    public double getExtRasterMinLat() { return luMinLat2; }

    /** @return Extension raster north bound */
    public double getExtRasterMaxLat() { return luMaxLat2; }

    /** @return Extension raster width in pixels */
    public int getExtRasterWidth() { return luWidth2; }

    /** @return Extension raster height in pixels */
    public int getExtRasterHeight() { return luHeight2; }
}
