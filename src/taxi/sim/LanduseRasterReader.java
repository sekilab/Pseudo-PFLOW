package taxi.sim;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import util.PathResolver;

/**
 * Reader for the JAXA ALOS land-use raster used by the taxi demand model
 * (W19, 2026-05). Lifts the raster I/O pattern from the truck simulation's
 * {@code truck.sim.spatial.GeoValidator} (which uses the same file) and
 * exposes a minimal API: one method, {@link #classAt(double, double)}, that
 * returns the land-use class code at a (lon, lat) point.
 *
 * <p><b>File format.</b> The raster is a flat binary {@code byte[]} of
 * {@code width × height} pixels, with metadata (bounds, resolution) provided
 * by an accompanying header. The taxi sim reads the same {@code
 * config/truck/landuse_raster.bin} (308 MB, 50 m primary; Kanto/Chubu/Tohoku)
 * — no duplication; the file is reused.
 *
 * <p><b>Class codes (JAXA ALOS-2 land-use product).</b> Mapped to four
 * demand-relevant categories:
 * <ul>
 *   <li>{@link #LU_COMMERCIAL} (codes 5, 6) — drives high shop/jobs weight</li>
 *   <li>{@link #LU_RESIDENTIAL} (code 1) — drives residential weight</li>
 *   <li>{@link #LU_INDUSTRIAL} (codes 2, 3) — drives low demand</li>
 *   <li>{@link #LU_MIXED_OR_GREEN} (codes 4, 7-10) — neutral demand</li>
 * </ul>
 *
 * <p><b>Degradation.</b> If the raster is missing or unreadable, the reader
 * is constructed in a "no-op" state in which all {@code classAt} calls return
 * {@link #LU_UNKNOWN} (and the {@link DestinationZone} treats this as a 1.0×
 * multiplier — neutral). This matches the truck sim's graceful-degradation
 * convention: simulation runs without the raster, just without the land-use
 * weighting.
 *
 * @author Taxi ABM Framework
 * @since W19 (2026-05)
 */
public final class LanduseRasterReader {

    public static final int LU_UNKNOWN     = 0;
    public static final int LU_RESIDENTIAL = 1;
    public static final int LU_INDUSTRIAL  = 2;
    public static final int LU_COMMERCIAL  = 3;
    public static final int LU_MIXED_OR_GREEN = 4;

    /** Path resolved via {@link util.PathResolver}; "no-op" if null/missing. */
    private final String rasterPath;
    private final boolean loaded;

    // Raster geometry (populated if loaded)
    private MappedByteBuffer raster;
    private int width;
    private int height;
    private double minLon, maxLon, minLat, maxLat;
    private double pixelLon, pixelLat;

    /**
     * Construct a reader pointing to the truck/taxi shared raster.
     *
     * @param configRasterPath value of {@code taxi.landuse.raster.path};
     *                         typically {@code "${PFLOW_HOME}/Pseudo-PFLOW/config/truck/landuse_raster.bin"}
     * @param minLon raster west bound (deg)
     * @param maxLon raster east bound (deg)
     * @param minLat raster south bound (deg)
     * @param maxLat raster north bound (deg)
     * @param width raster pixel width
     * @param height raster pixel height
     */
    public LanduseRasterReader(String configRasterPath,
                               double minLon, double maxLon,
                               double minLat, double maxLat,
                               int width, int height) {
        this.rasterPath = configRasterPath != null
            ? PathResolver.resolve(configRasterPath)
            : null;
        this.loaded = tryLoad(minLon, maxLon, minLat, maxLat, width, height);
    }

    /**
     * Convenience constructor with Kanto-Chubu-Tohoku default bounds
     * matching the truck sim's primary raster.
     */
    public static LanduseRasterReader defaultKantoChubuTohoku(String configRasterPath) {
        return new LanduseRasterReader(configRasterPath,
            135.0, 143.0, 33.0, 41.0, 17600, 17600);
    }

    private boolean tryLoad(double minLon, double maxLon,
                            double minLat, double maxLat,
                            int width, int height) {
        if (rasterPath == null) {
            System.err.println("[LANDUSE] No raster path configured; reader runs in no-op mode");
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(rasterPath, "r")) {
            FileChannel ch = raf.getChannel();
            long size = (long) width * (long) height;
            if (ch.size() < size) {
                System.err.println("[LANDUSE] Raster smaller than expected ("
                    + ch.size() + " bytes < " + size + "); no-op mode");
                return false;
            }
            this.raster = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
            this.width = width;
            this.height = height;
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.pixelLon = (maxLon - minLon) / width;
            this.pixelLat = (maxLat - minLat) / height;
            System.out.println("[LANDUSE] Loaded " + width + "x" + height
                + " raster from " + rasterPath);
            return true;
        } catch (IOException e) {
            System.err.println("[LANDUSE] Failed to load raster: " + e.getMessage()
                + "; no-op mode");
            return false;
        }
    }

    /**
     * Return the land-use class code at a point.
     *
     * @param lon longitude (WGS84 deg)
     * @param lat latitude  (WGS84 deg)
     * @return one of {@link #LU_UNKNOWN}, {@link #LU_RESIDENTIAL},
     *         {@link #LU_INDUSTRIAL}, {@link #LU_COMMERCIAL},
     *         {@link #LU_MIXED_OR_GREEN}
     */
    public int classAt(double lon, double lat) {
        if (!loaded) return LU_UNKNOWN;
        if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) {
            return LU_UNKNOWN;
        }
        int col = Math.min(width  - 1, (int) Math.floor((lon - minLon) / pixelLon));
        int row = Math.min(height - 1, (int) Math.floor((maxLat - lat) / pixelLat));
        byte raw = raster.get(row * width + col);
        return mapJaxaCode(raw & 0xFF);
    }

    /**
     * Mean land-use class around a zone center (samples a small grid).
     * Used by {@link DestinationZone} at zone initialisation to pre-compute
     * a single land-use signature per zone (cached, no per-trip raster access
     * in the hot loop).
     *
     * @param lon zone center longitude
     * @param lat zone center latitude
     * @param radiusKm zone radius in km
     * @return the most common land-use class within the zone
     */
    public int dominantClassInZone(double lon, double lat, double radiusKm) {
        if (!loaded) return LU_UNKNOWN;
        // Sample a 5x5 grid within the zone bounding box
        int[] counts = new int[5];
        double dLon = (radiusKm / 111.0) / Math.cos(Math.toRadians(lat));
        double dLat = radiusKm / 111.0;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int c = classAt(lon + i * dLon / 2.0, lat + j * dLat / 2.0);
                if (c >= 0 && c < counts.length) counts[c]++;
            }
        }
        int best = LU_UNKNOWN;
        int bestCount = -1;
        for (int c = 0; c < counts.length; c++) {
            if (counts[c] > bestCount) {
                bestCount = counts[c];
                best = c;
            }
        }
        return best;
    }

    /**
     * Map JAXA ALOS-2 raw class code (0-255) to our demand-relevant categories.
     * The actual JAXA legend has ~12 classes; we collapse to the four that
     * matter for taxi demand (commercial, residential, industrial, neutral).
     */
    private static int mapJaxaCode(int raw) {
        // Heuristic mapping based on standard JAXA ALOS land-use legend.
        // Real mapping table is in {@code config/taxi/landuse_jaxa_legend.csv}
        // (TBD as part of the full Item 5 implementation).
        switch (raw) {
            case 1: return LU_RESIDENTIAL;
            case 2: case 3: return LU_INDUSTRIAL;
            case 5: case 6: return LU_COMMERCIAL;
            case 4: case 7: case 8: case 9: case 10: return LU_MIXED_OR_GREEN;
            default: return LU_UNKNOWN;
        }
    }

    public boolean isLoaded() { return loaded; }
}
