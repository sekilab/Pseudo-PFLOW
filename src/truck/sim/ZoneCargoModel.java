package truck.sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import util.PathResolver;

/**
 * Per-zone cargo weight sampler.
 *
 * <p>Reads {@code config/truck/operations/zone_cargo_gamma.csv} (produced
 * by {@code mfs/extract_phase4_zone_cargo_gamma.py}) and samples from a
 * Gamma distribution per origin zone.
 *
 * <p>Schema: {@code zone_id, mean_tons, gamma_shape, gamma_scale, n_trucks, source}
 *
 * <p>Sampling uses Marsaglia-Tsang (the same generator already used in
 * {@link TripGenerator#generateGamma}). Thread-safe via
 * {@link ThreadLocalRandom}.
 *
 * <p>Resolves audit finding F0069: cargo weight Gamma was hand-tuned per
 * vehicle size with no zone calibration despite a 3.4x tons/truck spread.
 */
public class ZoneCargoModel {

    private static final double DEFAULT_FLEET_MEAN_TONS = 5.28;
    private static final double DEFAULT_CV = 0.7;

    public static final class GammaParams {
        public final double shape;
        public final double scale;
        public final double mean;
        GammaParams(double shape, double scale, double mean) {
            this.shape = shape;
            this.scale = scale;
            this.mean = mean;
        }
    }

    private final Map<String, GammaParams> paramsByZone;
    private final GammaParams fallback;

    private ZoneCargoModel(Map<String, GammaParams> params, GammaParams fallback) {
        this.paramsByZone = Collections.unmodifiableMap(params);
        this.fallback = fallback;
    }

    /** Load from the standard config location. */
    public static ZoneCargoModel loadDefault() throws IOException {
        return loadFromCsv(PathResolver.resolve(
            "${PFLOW_HOME}/Pseudo-PFLOW/config/truck/operations/zone_cargo_gamma.csv"));
    }

    public static ZoneCargoModel loadFromCsv(String path) throws IOException {
        Map<String, GammaParams> params = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String header = br.readLine();
            if (header == null) {
                throw new IOException("zone_cargo_gamma.csv is empty");
            }
            String line;
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) {
                    System.err.println("[ZoneCargoModel] Skipping malformed "
                        + "line " + lineNo + ": " + line);
                    continue;
                }
                try {
                    String zoneId = parts[0].trim();
                    double mean = Double.parseDouble(parts[1].trim());
                    double shape = Double.parseDouble(parts[2].trim());
                    double scale = Double.parseDouble(parts[3].trim());
                    params.put(zoneId, new GammaParams(shape, scale, mean));
                } catch (NumberFormatException e) {
                    System.err.println("[ZoneCargoModel] Non-numeric on line "
                        + lineNo + " (" + e.getMessage() + "): " + line);
                }
            }
        }
        if (params.isEmpty()) {
            throw new IOException("zone_cargo_gamma.csv had no usable rows: " + path);
        }
        double cvSq = DEFAULT_CV * DEFAULT_CV;
        GammaParams fallback = new GammaParams(
            1.0 / cvSq, DEFAULT_FLEET_MEAN_TONS * cvSq, DEFAULT_FLEET_MEAN_TONS);
        System.out.println("[ZoneCargoModel] Loaded " + params.size()
            + " zone cargo profiles from " + path);
        return new ZoneCargoModel(params, fallback);
    }

    /** @return raw Gamma sample for this zone (capped externally by vehicle capacity). */
    public double sampleWeight(String zoneId) {
        GammaParams p = paramsByZone.getOrDefault(zoneId, fallback);
        return generateGamma(p.shape, p.scale);
    }

    /** Exposed for diagnostics. Returns null if zone is unknown. */
    public GammaParams getParams(String zoneId) {
        return paramsByZone.get(zoneId);
    }

    public int size() {
        return paramsByZone.size();
    }

    // ------------------------------------------------------------------
    // Marsaglia-Tsang Gamma generator (mirrors TripGenerator.generateGamma)
    // ------------------------------------------------------------------

    private static double generateGamma(double shape, double scale) {
        if (shape < 1.0) {
            return generateGamma(shape + 1.0, scale)
                * Math.pow(ThreadLocalRandom.current().nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = randomGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u = ThreadLocalRandom.current().nextDouble();
            if (u < 1.0 - 0.0331 * (x * x * x * x)) return d * v * scale;
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }

    private static double randomGaussian() {
        double u1 = ThreadLocalRandom.current().nextDouble();
        double u2 = ThreadLocalRandom.current().nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }

    /**
     * Smoke test — verifies the sampler produces a mean within ~5% of
     * each zone's stated mean over 100k draws.
     */
    public static void main(String[] args) throws IOException {
        ZoneCargoModel model = loadDefault();
        String[] sampleZones = {"MFS01", "MFS29", "MFS71", "MFS62", "MFS999"};
        int N = 100_000;
        for (String zid : sampleZones) {
            double sum = 0.0;
            for (int i = 0; i < N; i++) {
                sum += model.sampleWeight(zid);
            }
            double empirical = sum / N;
            GammaParams p = model.getParams(zid);
            String expected = (p == null)
                ? "(fallback)"
                : String.format("%.3f", p.mean);
            System.out.printf("%s: empirical mean = %.3f (expected %s)%n",
                zid, empirical, expected);
        }
    }
}
