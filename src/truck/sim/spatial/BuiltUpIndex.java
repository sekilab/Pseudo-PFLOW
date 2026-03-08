package truck.sim.spatial;

import truck.sim.DeliveryZone;
import truck.sim.PointOfInterest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Density-weighted Built-up pixel sampling index for a single delivery zone.
 *
 * <p>Pre-computes a weighted sampling table of JAXA Built-up(2) pixels, where
 * weights are derived from <b>local raster density</b> and <b>POI proximity</b>
 * (commercial bias). This concentrates trip destinations on dense urban cores
 * and logistics corridors instead of distributing them uniformly.
 *
 * <h3>Architecture</h3>
 * <ol>
 *   <li>Scan all raster pixels within zone boundary envelope</li>
 *   <li>For each Built-up(2) pixel inside the zone polygon, compute:
 *       {@code weight = localDensity^α × (1.0 + poiBonus)}</li>
 *   <li>Build cumulative distribution function (CDF) for O(log n) weighted sampling</li>
 * </ol>
 *
 * <h3>Weight Components</h3>
 * <ul>
 *   <li><b>Local density</b>: Count of Built-up pixels in a 13×13 kernel (±300m at 50m resolution).
 *       Ranges from 1 (isolated pixel) to 169 (fully urban). Raised to power α.</li>
 *   <li><b>POI bonus</b>: Sum of {@code typeWeight / (1 + dist_km)} for all POIs
 *       within 2km. Type weights: logistics/industrial=3×, retail=2×, other=1×.
 *       This provides commercial bias since JAXA doesn't distinguish land use subtypes.</li>
 * </ul>
 *
 * <p>Memory: ~24 bytes per pixel × typical 10K-50K pixels per zone ≈ 0.2-1.2 MB/zone.
 *
 * @author Truck ABM Framework
 * @version 1.0
 * @see PointGenerator Tier 0 integration
 */
public class BuiltUpIndex {

    private static final int BUILT_UP_CODE = 2;
    private static final int KERNEL_RADIUS = 6;      // ±6 pixels = ±300m at 50m resolution
    private static final double DENSITY_ALPHA = 0.4;  // Density exponent (sub-linear = flatter spread)
    private static final double POI_RADIUS_KM = 2.0;  // POI influence radius

    // POI type weight multipliers (commercial bias)
    private static final double POI_WEIGHT_LOGISTICS = 3.0;
    private static final double POI_WEIGHT_INDUSTRIAL = 3.0;
    private static final double POI_WEIGHT_RETAIL = 2.0;
    private static final double POI_WEIGHT_DEFAULT = 1.0;

    // Maximum pixel envelope size to scan (prevents OOM on huge inter-regional zones)
    // At 50m resolution: 8M pixels ≈ 140km × 140km coverage
    private static final long MAX_ENVELOPE_PIXELS = 8_000_000;

    private final double[] lons;              // Pixel center longitudes
    private final double[] lats;              // Pixel center latitudes
    private final double[] cumulativeWeights; // CDF for weighted sampling
    private final int pixelCount;

    /**
     * Build density-weighted index for a zone.
     */
    public BuiltUpIndex(DeliveryZone zone, byte[] raster,
                        double rasterMinLon, double rasterMaxLon,
                        double rasterMinLat, double rasterMaxLat,
                        int rasterWidth, int rasterHeight,
                        List<PointOfInterest> zonePOIs) {
        this(zone, raster, rasterMinLon, rasterMaxLon, rasterMinLat, rasterMaxLat,
             rasterWidth, rasterHeight, zonePOIs, null);
    }

    /**
     * Build density-weighted index for a zone with optional exclusion envelope.
     *
     * <p>Uses a Summed Area Table (integral image) for O(1) kernel density queries
     * and a pre-stamped POI bonus grid to avoid per-pixel Haversine loops.
     *
     * <p>When {@code excludeBounds} is provided, pixels falling within those bounds
     * are skipped. This is used when building from a lower-resolution extension
     * raster to avoid duplicating pixels already covered by a higher-resolution
     * primary raster.
     *
     * @param zone           Target delivery zone
     * @param raster         JAXA land-use raster byte array (row-major, unsigned)
     * @param rasterMinLon   Raster west bound (degrees)
     * @param rasterMaxLon   Raster east bound (degrees)
     * @param rasterMinLat   Raster south bound (degrees)
     * @param rasterMaxLat   Raster north bound (degrees)
     * @param rasterWidth    Raster columns
     * @param rasterHeight   Raster rows
     * @param zonePOIs       POIs in this zone (for commercial bias weighting)
     * @param excludeBounds  Envelope to exclude (e.g., primary raster bounds), or null
     */
    public BuiltUpIndex(DeliveryZone zone, byte[] raster,
                        double rasterMinLon, double rasterMaxLon,
                        double rasterMinLat, double rasterMaxLat,
                        int rasterWidth, int rasterHeight,
                        List<PointOfInterest> zonePOIs,
                        org.locationtech.jts.geom.Envelope excludeBounds) {

        // Convert zone envelope to pixel bounds
        org.locationtech.jts.geom.Envelope env = zone.getEnvelope();
        int colMin = Math.max(0, lonToCol(env.getMinX(), rasterMinLon, rasterMaxLon, rasterWidth));
        int colMax = Math.min(rasterWidth - 1, lonToCol(env.getMaxX(), rasterMinLon, rasterMaxLon, rasterWidth));
        int rowMin = Math.max(0, latToRow(env.getMaxY(), rasterMinLat, rasterMaxLat, rasterHeight));
        int rowMax = Math.min(rasterHeight - 1, latToRow(env.getMinY(), rasterMinLat, rasterMaxLat, rasterHeight));

        // Compute downsample factor for large envelopes (instead of skipping them)
        long envelopePixels = (long) (colMax - colMin + 1) * (rowMax - rowMin + 1);
        int dsf = 1; // downsample factor
        if (envelopePixels > MAX_ENVELOPE_PIXELS) {
            dsf = (int) Math.ceil(Math.sqrt((double) envelopePixels / MAX_ENVELOPE_PIXELS));
        }

        // Downsampled grid dimensions (step by dsf in each dimension)
        int envW = (colMax - colMin + dsf) / dsf;  // ceil division
        int envH = (rowMax - rowMin + dsf) / dsf;

        // ── Build Summed Area Table (integral image) for O(1) kernel density ──
        // SAT operates on the downsampled grid
        int[] sat = new int[(envH + 1) * (envW + 1)]; // 1-indexed, row-major
        int satStride = envW + 1;
        for (int r = 0; r < envH; r++) {
            int rasterRow = rowMin + r * dsf;
            if (rasterRow >= rasterHeight) rasterRow = rasterHeight - 1;
            for (int c = 0; c < envW; c++) {
                int rasterCol = colMin + c * dsf;
                if (rasterCol >= rasterWidth) rasterCol = rasterWidth - 1;
                int val = ((raster[rasterRow * rasterWidth + rasterCol] & 0xFF) == BUILT_UP_CODE) ? 1 : 0;
                sat[(r + 1) * satStride + (c + 1)] = val
                    + sat[r * satStride + (c + 1)]
                    + sat[(r + 1) * satStride + c]
                    - sat[r * satStride + c];
            }
        }

        // ── Pre-stamp POI bonus grid ──────────────────────────────────────────
        float[] poiGrid = null;
        if (zonePOIs != null && !zonePOIs.isEmpty()) {
            poiGrid = new float[envH * envW];
            double pixSizeLon = (rasterMaxLon - rasterMinLon) / rasterWidth * dsf;
            double pixSizeLat = (rasterMaxLat - rasterMinLat) / rasterHeight * dsf;
            double centerLat = (env.getMinY() + env.getMaxY()) * 0.5;
            double degPerKmLat = 1.0 / 111.0;
            double degPerKmLon = degPerKmLat / Math.cos(Math.toRadians(centerLat));
            int radiusPixR = (int) Math.ceil(POI_RADIUS_KM * degPerKmLat / pixSizeLat);
            int radiusPixC = (int) Math.ceil(POI_RADIUS_KM * degPerKmLon / pixSizeLon);

            for (PointOfInterest poi : zonePOIs) {
                double poiLon = poi.getLongitude();
                double poiLat = poi.getLatitude();
                // Map POI to downsampled grid coordinates
                int poiCol = (lonToCol(poiLon, rasterMinLon, rasterMaxLon, rasterWidth) - colMin) / dsf;
                int poiRow = (latToRow(poiLat, rasterMinLat, rasterMaxLat, rasterHeight) - rowMin) / dsf;
                double typeWeight = getPOITypeWeight(poi);

                int rStart = Math.max(0, poiRow - radiusPixR);
                int rEnd = Math.min(envH - 1, poiRow + radiusPixR);
                int cStart = Math.max(0, poiCol - radiusPixC);
                int cEnd = Math.min(envW - 1, poiCol + radiusPixC);

                for (int r = rStart; r <= rEnd; r++) {
                    int rasterRow = rowMin + r * dsf;
                    if (rasterRow >= rasterHeight) rasterRow = rasterHeight - 1;
                    double pLat = rowToLat(rasterRow, rasterMinLat, rasterMaxLat, rasterHeight);
                    double dLat = pLat - poiLat;
                    for (int c = cStart; c <= cEnd; c++) {
                        int rasterCol = colMin + c * dsf;
                        if (rasterCol >= rasterWidth) rasterCol = rasterWidth - 1;
                        double pLon = colToLon(rasterCol, rasterMinLon, rasterMaxLon, rasterWidth);
                        double dLon = pLon - poiLon;
                        double distKm = Math.sqrt((dLon / degPerKmLon) * (dLon / degPerKmLon)
                            + (dLat / degPerKmLat) * (dLat / degPerKmLat));
                        if (distKm <= POI_RADIUS_KM) {
                            poiGrid[r * envW + c] += (float) (typeWeight / (1.0 + distKm));
                        }
                    }
                }
            }
        }

        // ── Collect Built-up pixels inside zone and compute weights ───────────
        List<Double> lonList = new ArrayList<>();
        List<Double> latList = new ArrayList<>();
        List<Double> weightList = new ArrayList<>();

        for (int r = 0; r < envH; r++) {
            int rasterRow = rowMin + r * dsf;
            if (rasterRow >= rasterHeight) rasterRow = rasterHeight - 1;
            for (int c = 0; c < envW; c++) {
                int rasterCol = colMin + c * dsf;
                if (rasterCol >= rasterWidth) rasterCol = rasterWidth - 1;
                if ((raster[rasterRow * rasterWidth + rasterCol] & 0xFF) != BUILT_UP_CODE) continue;

                double lon = colToLon(rasterCol, rasterMinLon, rasterMaxLon, rasterWidth);
                double lat = rowToLat(rasterRow, rasterMinLat, rasterMaxLat, rasterHeight);
                // Skip pixels in exclusion zone (avoids overlap with higher-res primary raster)
                if (excludeBounds != null && excludeBounds.contains(lon, lat)) continue;
                // Skip expensive polygon check for downsampled zones (envelope is sufficient)
                if (dsf == 1 && !zone.containsPoint(lon, lat)) continue;

                // O(1) kernel density via Summed Area Table
                int density = querySAT(sat, satStride, envW, envH, r, c, KERNEL_RADIUS);

                // O(1) POI bonus from pre-stamped grid
                double poiBonus = (poiGrid != null) ? poiGrid[r * envW + c] : 0.0;

                double weight = Math.pow(density, DENSITY_ALPHA) * (1.0 + poiBonus);

                lonList.add(lon);
                latList.add(lat);
                weightList.add(weight);
            }
        }

        this.pixelCount = lonList.size();

        if (pixelCount == 0) {
            this.lons = new double[0];
            this.lats = new double[0];
            this.cumulativeWeights = new double[0];
            return;
        }

        // Convert to primitive arrays
        this.lons = new double[pixelCount];
        this.lats = new double[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            lons[i] = lonList.get(i);
            lats[i] = latList.get(i);
        }

        // Build CDF (cumulative distribution function) for weighted sampling
        this.cumulativeWeights = new double[pixelCount];
        double cumSum = 0.0;
        for (int i = 0; i < pixelCount; i++) {
            cumSum += weightList.get(i);
            cumulativeWeights[i] = cumSum;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SAMPLING
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sample a density-weighted Built-up pixel location.
     * Uses binary search on CDF for O(log n) lookup, plus micro-jitter
     * (±50m Gaussian) to avoid grid artifacts.
     *
     * @param random Random generator
     * @return Coordinates [lon, lat] with micro-jitter, or null if index is empty
     */
    public double[] sampleWeighted(Random random) {
        if (pixelCount == 0) return null;

        double totalWeight = cumulativeWeights[pixelCount - 1];
        double r = random.nextDouble() * totalWeight;

        // Binary search for the sampled pixel
        int idx = Arrays.binarySearch(cumulativeWeights, r);
        if (idx < 0) idx = -(idx + 1);  // insertion point
        idx = Math.min(idx, pixelCount - 1);

        // Micro-jitter: ±50m ≈ ±0.00045° to avoid grid artifacts
        double jitterLon = random.nextGaussian() * 0.00045;
        double jitterLat = random.nextGaussian() * 0.00045;

        return new double[]{lons[idx] + jitterLon, lats[idx] + jitterLat};
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ════════════════════════════════════════════════════════════════════════

    /** @return true if no Built-up pixels found in zone (or zone was too large) */
    public boolean isEmpty() {
        return pixelCount == 0;
    }

    /** @return Number of Built-up pixels in this zone's index */
    public int getPixelCount() {
        return pixelCount;
    }

    /**
     * Merge two BuiltUpIndex instances into one combined index.
     * Concatenates pixel arrays and rebuilds the CDF from combined weights.
     * Used when a zone spans both primary and extension rasters.
     *
     * @param a First index (may be empty)
     * @param b Second index (may be empty)
     * @return Merged index, or whichever is non-empty, or empty if both are
     */
    public static BuiltUpIndex merge(BuiltUpIndex a, BuiltUpIndex b) {
        return merge(a, b, 1.0);
    }

    /**
     * Merge two BuiltUpIndex instances with a weight scale factor for the second index.
     * This compensates for resolution differences between rasters: a 100m pixel
     * represents 4× the area of a 50m pixel, so bWeightScale = 4.0 normalizes them.
     *
     * @param a            First index (typically from higher-res primary raster)
     * @param b            Second index (typically from lower-res extension raster)
     * @param bWeightScale Multiplier for B's weights (e.g., 4.0 for 100m→50m area ratio)
     * @return Merged index with resolution-compensated weights
     */
    public static BuiltUpIndex merge(BuiltUpIndex a, BuiltUpIndex b, double bWeightScale) {
        if (a == null || a.isEmpty()) return (b != null) ? b : a;
        if (b == null || b.isEmpty()) return a;

        int totalCount = a.pixelCount + b.pixelCount;
        double[] mergedLons = new double[totalCount];
        double[] mergedLats = new double[totalCount];
        double[] mergedCDF = new double[totalCount];

        // Copy A's pixels
        System.arraycopy(a.lons, 0, mergedLons, 0, a.pixelCount);
        System.arraycopy(a.lats, 0, mergedLats, 0, a.pixelCount);

        // Copy B's pixels
        System.arraycopy(b.lons, 0, mergedLons, a.pixelCount, b.pixelCount);
        System.arraycopy(b.lats, 0, mergedLats, a.pixelCount, b.pixelCount);

        // Rebuild CDF: A's weights then B's weights (scaled and shifted by A's total)
        double aTotalWeight = a.cumulativeWeights[a.pixelCount - 1];
        System.arraycopy(a.cumulativeWeights, 0, mergedCDF, 0, a.pixelCount);
        double bCumulative = 0.0;
        for (int i = 0; i < b.pixelCount; i++) {
            double bWeight = (i == 0) ? b.cumulativeWeights[0]
                                      : b.cumulativeWeights[i] - b.cumulativeWeights[i - 1];
            bCumulative += bWeight * bWeightScale;
            mergedCDF[a.pixelCount + i] = aTotalWeight + bCumulative;
        }

        return new BuiltUpIndex(mergedLons, mergedLats, mergedCDF, totalCount);
    }

    /** Private constructor for merged index */
    private BuiltUpIndex(double[] lons, double[] lats, double[] cumulativeWeights, int pixelCount) {
        this.lons = lons;
        this.lats = lats;
        this.cumulativeWeights = cumulativeWeights;
        this.pixelCount = pixelCount;
    }

    // ════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * O(1) kernel density query using Summed Area Table.
     * Returns count of Built-up pixels in (2*radius+1)² kernel centered at (r, c)
     * within the local envelope coordinate system.
     */
    private static int querySAT(int[] sat, int satStride, int envW, int envH,
                                int r, int c, int radius) {
        // Clamp kernel to envelope bounds (1-indexed SAT)
        int r1 = Math.max(0, r - radius);
        int c1 = Math.max(0, c - radius);
        int r2 = Math.min(envH, r + radius + 1);
        int c2 = Math.min(envW, c + radius + 1);
        return sat[r2 * satStride + c2]
             - sat[r1 * satStride + c2]
             - sat[r2 * satStride + c1]
             + sat[r1 * satStride + c1];
    }

    /**
     * Map POI type to commercial bias weight.
     */
    private double getPOITypeWeight(PointOfInterest poi) {
        switch (poi.getPoiType()) {
            case LOGISTIC_CENTER: return POI_WEIGHT_LOGISTICS;
            case INDUSTRIAL_SITE: return POI_WEIGHT_INDUSTRIAL;
            case RETAIL_SHOP:
            case SHOPPING_MALL:   return POI_WEIGHT_RETAIL;
            case PORT_TERMINAL:   return POI_WEIGHT_LOGISTICS;
            default:              return POI_WEIGHT_DEFAULT;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // COORDINATE CONVERSION
    // ════════════════════════════════════════════════════════════════════════

    /** Longitude → raster column index */
    private static int lonToCol(double lon, double minLon, double maxLon, int width) {
        return (int) Math.floor((lon - minLon) / (maxLon - minLon) * width);
    }

    /** Latitude → raster row index (top-down, north = row 0) */
    private static int latToRow(double lat, double minLat, double maxLat, int height) {
        return (int) Math.floor((maxLat - lat) / (maxLat - minLat) * height);
    }

    /** Raster column → pixel center longitude */
    private static double colToLon(int col, double minLon, double maxLon, int width) {
        return minLon + (col + 0.5) / width * (maxLon - minLon);
    }

    /** Raster row → pixel center latitude */
    private static double rowToLat(int row, double minLat, double maxLat, int height) {
        return maxLat - (row + 0.5) / height * (maxLat - minLat);
    }
}
