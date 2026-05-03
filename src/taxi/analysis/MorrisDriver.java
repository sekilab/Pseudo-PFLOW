package taxi.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Morris elementary-effects (EE) screening driver for Tokyo Taxi ABM
 * (B5 / Phase 8.1 — DESIGN.md §3 + Kang 2021 GSA-CAL framework).
 *
 * <p>Generates Morris trajectories that perturb one parameter at a time, ranks
 * the 12 GSA parameters by mean absolute elementary effect μ* (Saltelli 2010
 * "improved Morris"), and writes both:
 * <ul>
 *   <li>A directory of derived {@code .properties} files (one per sim run)
 *       plus a {@code manifest.csv} listing them.
 *   <li>{@code morris_results.csv} after sims complete (run separately via
 *       PowerShell parallel launcher), with columns
 *       {@code parameter_name, mu_star, sigma, rank_by_mu_star}.
 * </ul>
 *
 * <p>Two-phase workflow:
 * <pre>
 *   1. java MorrisDriver generate &lt;basecfg&gt; &lt;params.csv&gt; &lt;outdir&gt; [trajectories]
 *      → writes outdir/sample_NNNN.properties + outdir/manifest.csv
 *   2. (PowerShell launcher runs all sample_NNNN.properties in parallel)
 *   3. java MorrisDriver aggregate &lt;params.csv&gt; &lt;outdir&gt; &lt;morris_results.csv&gt;
 *      → reads outdir/sample_NNNN_run/validation.csv files, computes μ*, σ
 * </pre>
 *
 * <p>Trajectory construction follows the standard Morris "OAT" (one-at-a-time)
 * design: each trajectory has p+1 points (p=12 params), with consecutive points
 * differing in exactly one parameter by ±Δ where Δ = (max-min) × delta-fraction.
 *
 * <p>Output metric for ranking (a single scalar per sim run): aggregate
 * validation grade as percent of tests passed (0–100). Computed by
 * {@link #parseValidationGrade(Path)}.
 */
public class MorrisDriver {

    static final int DEFAULT_TRAJECTORIES = 6;  // → 6 × (12+1) = 78 sim runs
    static final double DEFAULT_DELTA_FRACTION = 0.5;  // perturb by 50% of range

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            return;
        }
        switch (args[0]) {
            case "generate": cmdGenerate(args); break;
            case "aggregate": cmdAggregate(args); break;
            default: usage();
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  MorrisDriver generate <baseCfg> <gsaParamsCsv> <outDir> [trajectories]");
        System.out.println("  MorrisDriver aggregate <gsaParamsCsv> <outDir> <morrisResultsCsv>");
    }

    /** Generate per-sample derived .properties files + manifest.csv. */
    static void cmdGenerate(String[] args) throws Exception {
        if (args.length < 4) { usage(); return; }
        Path baseCfg = Paths.get(args[1]);
        Path paramsCsv = Paths.get(args[2]);
        Path outDir = Paths.get(args[3]);
        int trajectories = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_TRAJECTORIES;

        List<GsaParam> params = loadParams(paramsCsv);
        Files.createDirectories(outDir);

        Properties baseProps = new Properties();
        try (var in = Files.newBufferedReader(baseCfg)) {
            baseProps.load(in);
        }

        Random rand = new Random(20260426L);  // deterministic Morris design
        int p = params.size();
        double delta = DEFAULT_DELTA_FRACTION;

        List<String> manifestLines = new ArrayList<>();
        manifestLines.add("sample_id,trajectory,step,perturbed_param,perturbed_value,config_path,output_dir");

        int sampleId = 0;
        for (int t = 0; t < trajectories; t++) {
            // Random base point in unit hypercube (snapped to grid {0, delta, 2*delta, ...})
            double[] base = new double[p];
            for (int i = 0; i < p; i++) {
                base[i] = rand.nextDouble() < 0.5 ? 0.0 : 1.0 - delta;
            }
            // Random permutation of parameter order
            int[] order = new int[p];
            for (int i = 0; i < p; i++) order[i] = i;
            shuffle(order, rand);

            // Step 0: emit base point
            sampleId = emitSample(sampleId, t, 0, -1, params, base, baseProps,
                outDir, manifestLines);

            // Steps 1..p: perturb one parameter at a time
            double[] cur = base.clone();
            for (int step = 0; step < p; step++) {
                int idx = order[step];
                cur[idx] = (cur[idx] < 0.5) ? cur[idx] + delta : cur[idx] - delta;
                sampleId = emitSample(sampleId, t, step + 1, idx, params, cur, baseProps,
                    outDir, manifestLines);
            }
        }

        Files.write(outDir.resolve("manifest.csv"), manifestLines);
        System.out.println("Generated " + sampleId + " sample configs in " + outDir);
        System.out.println("Trajectories: " + trajectories + ", parameters: " + p
            + ", delta-fraction: " + delta);
    }

    /** Emit one sample: write derived properties file, append manifest row. */
    private static int emitSample(int sampleId, int trajectory, int step, int perturbedIdx,
                                  List<GsaParam> params, double[] u, Properties baseProps,
                                  Path outDir, List<String> manifest) throws IOException {
        Properties derived = new Properties();
        derived.putAll(baseProps);
        for (int i = 0; i < params.size(); i++) {
            GsaParam pm = params.get(i);
            double v = pm.scale(u[i]);
            derived.setProperty(pm.configKey, formatDouble(v));
        }
        // Each sample writes to its own output dir so parallel sims don't collide.
        String runDir = "${PFLOW_HOME}/output/trips/taxi/tokyo/gsa_sample_"
            + String.format("%04d", sampleId);
        derived.setProperty("output.directory", runDir);

        String fileName = String.format("sample_%04d.properties", sampleId);
        Path samplePath = outDir.resolve(fileName);
        try (BufferedWriter w = Files.newBufferedWriter(samplePath)) {
            w.write("# GSA-Morris auto-generated sample\n");
            w.write("# Trajectory " + trajectory + ", step " + step
                + (perturbedIdx >= 0 ? ", perturbed=" + params.get(perturbedIdx).name : ", base") + "\n");
            for (String key : new TreeSet<>(derived.stringPropertyNames())) {
                w.write(key + "=" + derived.getProperty(key) + "\n");
            }
        }
        String perturbedName = perturbedIdx >= 0 ? params.get(perturbedIdx).name : "BASE";
        double perturbedValue = perturbedIdx >= 0
            ? params.get(perturbedIdx).scale(u[perturbedIdx])
            : Double.NaN;
        manifest.add(sampleId + "," + trajectory + "," + step + "," + perturbedName
            + "," + (Double.isNaN(perturbedValue) ? "" : formatDouble(perturbedValue))
            + "," + samplePath + "," + "sample_" + String.format("%04d", sampleId));
        return sampleId + 1;
    }

    /** Read all sample validation.csv outputs; compute Morris mu-star and sigma per parameter. */
    static void cmdAggregate(String[] args) throws Exception {
        if (args.length < 4) { usage(); return; }
        Path paramsCsv = Paths.get(args[1]);
        Path outDir = Paths.get(args[2]);
        Path resultsOut = Paths.get(args[3]);

        List<GsaParam> params = loadParams(paramsCsv);
        Path manifestPath = outDir.resolve("manifest.csv");
        if (!Files.exists(manifestPath)) {
            throw new IOException("Manifest not found: " + manifestPath);
        }
        List<String> manifest = Files.readAllLines(manifestPath);

        // Map sampleId → grade (% of validation tests passed). Each sim writes
        // its output to ${PFLOW_HOME}/output/trips/taxi/tokyo/gsa_sample_NNNN/run_TIMESTAMP/.
        // Glob for the most recent run_* subdir per sample.
        String pflowHome = System.getenv("PFLOW_HOME");
        if (pflowHome == null) pflowHome = "H:/Dropbox/PFLOW";
        Path tokyoOutBase = Paths.get(pflowHome, "output/trips/taxi/tokyo");

        Map<Integer, Double> grades = new LinkedHashMap<>();
        for (int i = 1; i < manifest.size(); i++) {
            String[] cols = manifest.get(i).split(",", -1);
            int sampleId = Integer.parseInt(cols[0]);
            String sampleSubdir = cols[6];  // e.g. sample_0000
            Path sampleOutDir = tokyoOutBase.resolve("gsa_" + sampleSubdir);
            if (!Files.isDirectory(sampleOutDir)) continue;
            // Find most recent run_* subdir
            try (var dirs = Files.list(sampleOutDir)) {
                Optional<Path> latest = dirs
                    .filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("run_"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()));
                if (latest.isPresent()) {
                    Path validation = latest.get().resolve("validation.csv");
                    if (Files.exists(validation)) {
                        grades.put(sampleId, parseValidationGrade(validation));
                    }
                }
            }
        }

        // Re-walk trajectories to compute elementary effects.
        // For each trajectory, step k perturbs params[order[k-1]] by delta;
        // EE = (Y[step k] - Y[step k-1]) / delta.
        List<List<Double>> effects = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) effects.add(new ArrayList<>());

        // Re-parse manifest for trajectory structure
        Map<Integer, String[]> rowById = new LinkedHashMap<>();
        for (int i = 1; i < manifest.size(); i++) {
            String[] cols = manifest.get(i).split(",", -1);
            rowById.put(Integer.parseInt(cols[0]), cols);
        }
        Integer[] sampleIds = rowById.keySet().toArray(new Integer[0]);
        for (int i = 1; i < sampleIds.length; i++) {
            int prev = sampleIds[i - 1];
            int cur = sampleIds[i];
            String[] curRow = rowById.get(cur);
            int curStep = Integer.parseInt(curRow[2]);
            if (curStep == 0) continue;  // start of a new trajectory — no EE
            String perturbedName = curRow[3];
            int idx = -1;
            for (int j = 0; j < params.size(); j++) {
                if (params.get(j).name.equals(perturbedName)) { idx = j; break; }
            }
            Double yPrev = grades.get(prev);
            Double yCur = grades.get(cur);
            if (idx >= 0 && yPrev != null && yCur != null) {
                double ee = (yCur - yPrev) / DEFAULT_DELTA_FRACTION;
                effects.get(idx).add(ee);
            }
        }

        // Write morris_results.csv
        try (BufferedWriter w = Files.newBufferedWriter(resultsOut)) {
            w.write("parameter_name,mu_star,sigma,rank_by_mu_star,n_effects\n");
            // Compute μ*, σ
            double[] muStar = new double[params.size()];
            double[] sigma = new double[params.size()];
            int[] nEffects = new int[params.size()];
            for (int i = 0; i < params.size(); i++) {
                List<Double> es = effects.get(i);
                nEffects[i] = es.size();
                if (es.isEmpty()) continue;
                double sumAbs = 0, sum = 0;
                for (double e : es) { sumAbs += Math.abs(e); sum += e; }
                muStar[i] = sumAbs / es.size();
                double mean = sum / es.size();
                double sumSq = 0;
                for (double e : es) sumSq += (e - mean) * (e - mean);
                sigma[i] = es.size() > 1 ? Math.sqrt(sumSq / (es.size() - 1)) : 0.0;
            }
            // Rank by μ*
            Integer[] rankIdx = new Integer[params.size()];
            for (int i = 0; i < params.size(); i++) rankIdx[i] = i;
            Arrays.sort(rankIdx, (a, b) -> Double.compare(muStar[b], muStar[a]));
            int[] rank = new int[params.size()];
            for (int r = 0; r < rankIdx.length; r++) rank[rankIdx[r]] = r + 1;

            for (int i = 0; i < params.size(); i++) {
                w.write(String.format("%s,%g,%g,%d,%d%n",
                    params.get(i).name, muStar[i], sigma[i], rank[i], nEffects[i]));
            }
        }
        System.out.println("Wrote " + resultsOut);
        System.out.println("Successful sims: " + grades.size() + " / "
            + (manifest.size() - 1));
    }

    /**
     * Continuous output metric for Morris EE: mean absolute error percent across
     * all validation rows. Format: category,metric,actual,target,tolerance,error_pct,status.
     *
     * <p>The error_pct column is already |actual-target|/target × 100 for each
     * metric; we average across all rows. This gives a continuous, sensitive
     * output that reflects how far the run drifted from THTA targets — better
     * than the binary PASS/FAIL count which quantizes too coarsely.
     */
    static double parseValidationGrade(Path validationCsv) throws IOException {
        List<String> lines = Files.readAllLines(validationCsv);
        double sumErr = 0;
        int n = 0;
        for (String line : lines) {
            if (line.startsWith("category") || line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length < 7) continue;
            try {
                double err = Double.parseDouble(cols[5].trim());
                sumErr += err;
                n++;
            } catch (NumberFormatException e) { /* skip header rows */ }
        }
        return n > 0 ? sumErr / n : 0.0;
    }

    static List<GsaParam> loadParams(Path csv) throws IOException {
        List<GsaParam> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(csv);
        for (int i = 1; i < lines.size(); i++) {  // skip header
            String[] c = lines.get(i).split(",", -1);
            if (c.length < 5) continue;
            out.add(new GsaParam(c[0].trim(),
                Double.parseDouble(c[1].trim()),
                Double.parseDouble(c[2].trim()),
                Double.parseDouble(c[3].trim()),
                c[4].trim()));
        }
        return out;
    }

    private static String formatDouble(double v) {
        // Use %g for compact output; avoid scientific notation for small values.
        if (Math.abs(v) >= 0.001 && Math.abs(v) < 1e6) {
            return String.format("%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", ".0");
        }
        return Double.toString(v);
    }

    private static void shuffle(int[] a, Random r) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
        }
    }
}
