package taxi.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Sobol indices driver for Tokyo Taxi ABM (B5 / Phase 8.2 — DESIGN.md §3).
 *
 * <p>Computes first-order Sobol indices {@code S_i} and total-order
 * {@code S_T_i} for the top-K parameters identified by Morris screening
 * (B5.1) across 5 output metrics (trips/active-taxi, daily km/active-taxi,
 * 実車率, 実働率, daily revenue/active-taxi).
 *
 * <p>Saltelli sampling: matrices A (N×p) and B (N×p), plus AB^j for each
 * j∈[1,p] which is B with column j replaced by A's column j. Total sims:
 * N(p+2). With N=64, p=5 → 448 sims.
 *
 * <p>Estimators (Saltelli 2010 + Janon 2014):
 * <pre>
 *   E[V_i]   ≈ (1/N) Σ y_B^i (y_AB^i - y_A^i)         (first-order, Janon)
 *   E[V_T_i] ≈ (1/2N) Σ (y_A^i - y_AB^i)²              (total-order, Saltelli)
 *   Var(Y)   ≈ (1/N) Σ y_A^i² - (mean(y_A))²
 *   S_i   = E[V_i] / Var(Y)
 *   S_T_i = E[V_T_i] / Var(Y)
 * </pre>
 *
 * <p>Workflow mirrors {@link MorrisDriver}:
 * <pre>
 *   1. SobolDriver generate &lt;baseCfg&gt; &lt;morrisResults.csv&gt; &lt;paramsCsv&gt; &lt;outDir&gt; [N [topK]]
 *      → writes outDir/sample_NNNN.properties + outDir/manifest.csv
 *   2. (PowerShell launcher runs all sample_NNNN.properties in parallel)
 *   3. SobolDriver aggregate &lt;morrisResults.csv&gt; &lt;paramsCsv&gt; &lt;outDir&gt; &lt;sobolResults.csv&gt;
 *      → reads outDir/sample_NNNN_run/dashboard.csv files, computes indices
 * </pre>
 */
public class SobolDriver {

    static final int DEFAULT_N = 64;
    static final int DEFAULT_TOP_K = 5;

    /** Output metrics extracted from each run's dashboard.csv (5 totals). */
    private static final String[] METRICS = {
        "TRIPS.passenger_per_taxi",       // trips/active-taxi/day
        "DISTANCE.avg_per_taxi_km",       // total daily km/active-taxi
        "DISTANCE.empty_running_pct",     // (=100% - 実車率)
        "FLEET.operating_rate_pct",       // 実働率 (synthetic, see parseDashboard)
        "REVENUE.per_taxi_yen"            // daily revenue/active-taxi
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) { usage(); return; }
        switch (args[0]) {
            case "generate": cmdGenerate(args); break;
            case "aggregate": cmdAggregate(args); break;
            default: usage();
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  SobolDriver generate <baseCfg> <morrisResultsCsv> <paramsCsv> <outDir> [N [topK]]");
        System.out.println("  SobolDriver aggregate <morrisResultsCsv> <paramsCsv> <outDir> <sobolResultsCsv>");
    }

    static void cmdGenerate(String[] args) throws Exception {
        if (args.length < 5) { usage(); return; }
        Path baseCfg = Paths.get(args[1]);
        Path morrisCsv = Paths.get(args[2]);
        Path paramsCsv = Paths.get(args[3]);
        Path outDir = Paths.get(args[4]);
        int N = args.length > 5 ? Integer.parseInt(args[5]) : DEFAULT_N;
        int topK = args.length > 6 ? Integer.parseInt(args[6]) : DEFAULT_TOP_K;

        List<GsaParam> allParams = MorrisDriver.loadParams(paramsCsv);
        List<GsaParam> selected = topKByMorris(morrisCsv, allParams, topK);
        System.out.println("Sobol on top-" + topK + " parameters by Morris μ*:");
        for (GsaParam p : selected) System.out.println("  " + p);
        Files.createDirectories(outDir);

        Properties baseProps = new Properties();
        try (var in = Files.newBufferedReader(baseCfg)) { baseProps.load(in); }

        Random rand = new Random(20260427L);  // deterministic
        int p = selected.size();
        // Saltelli sample matrices
        double[][] A = sampleUnit(rand, N, p);
        double[][] B = sampleUnit(rand, N, p);

        List<String> manifest = new ArrayList<>();
        manifest.add("sample_id,matrix,row,replaced_col,output_dir");

        int sampleId = 0;
        // Matrix A samples
        for (int i = 0; i < N; i++) {
            sampleId = emit(sampleId, "A", i, -1, A[i], selected, baseProps, outDir, manifest);
        }
        // Matrix B samples
        for (int i = 0; i < N; i++) {
            sampleId = emit(sampleId, "B", i, -1, B[i], selected, baseProps, outDir, manifest);
        }
        // AB^j matrices: B with column j replaced by A's column j
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < N; i++) {
                double[] row = B[i].clone();
                row[j] = A[i][j];
                sampleId = emit(sampleId, "AB", i, j, row, selected, baseProps, outDir, manifest);
            }
        }

        Files.write(outDir.resolve("manifest.csv"), manifest);
        System.out.println("Generated " + sampleId + " Saltelli samples (N=" + N
            + ", p=" + p + ") in " + outDir);
    }

    private static int emit(int sampleId, String matrix, int row, int replacedCol,
                            double[] u, List<GsaParam> selected,
                            Properties baseProps, Path outDir, List<String> manifest) throws IOException {
        Properties derived = new Properties();
        derived.putAll(baseProps);
        for (int i = 0; i < selected.size(); i++) {
            GsaParam pm = selected.get(i);
            derived.setProperty(pm.configKey, String.format("%.6f", pm.scale(u[i])));
        }
        String runDirName = "sobol_sample_" + String.format("%05d", sampleId);
        derived.setProperty("output.directory",
            "${PFLOW_HOME}/output/trips/taxi/tokyo/" + runDirName);
        Path samplePath = outDir.resolve(String.format("sample_%05d.properties", sampleId));
        try (BufferedWriter w = Files.newBufferedWriter(samplePath)) {
            w.write("# Sobol-Saltelli auto-generated sample — matrix=" + matrix
                + ", row=" + row + (replacedCol >= 0 ? ", replaced_col=" + replacedCol : "") + "\n");
            for (String key : new TreeSet<>(derived.stringPropertyNames())) {
                w.write(key + "=" + derived.getProperty(key) + "\n");
            }
        }
        manifest.add(sampleId + "," + matrix + "," + row + "," + replacedCol + "," + runDirName);
        return sampleId + 1;
    }

    /** Read top-K parameter names from morris_results.csv (column rank_by_mu_star). */
    static List<GsaParam> topKByMorris(Path morrisCsv, List<GsaParam> allParams, int K) throws IOException {
        Map<String, Integer> rankByName = new HashMap<>();
        List<String> lines = Files.readAllLines(morrisCsv);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            if (c.length < 4) continue;
            rankByName.put(c[0].trim(), Integer.parseInt(c[3].trim()));
        }
        return allParams.stream()
            .sorted((a, b) -> {
                int ra = rankByName.getOrDefault(a.name, Integer.MAX_VALUE);
                int rb = rankByName.getOrDefault(b.name, Integer.MAX_VALUE);
                return Integer.compare(ra, rb);
            })
            .limit(K)
            .collect(java.util.stream.Collectors.toList());
    }

    static void cmdAggregate(String[] args) throws Exception {
        if (args.length < 5) { usage(); return; }
        Path morrisCsv = Paths.get(args[1]);
        Path paramsCsv = Paths.get(args[2]);
        Path outDir = Paths.get(args[3]);
        Path resultsOut = Paths.get(args[4]);

        List<GsaParam> allParams = MorrisDriver.loadParams(paramsCsv);
        List<GsaParam> selected = topKByMorris(morrisCsv, allParams, DEFAULT_TOP_K);
        int p = selected.size();

        Path manifestPath = outDir.resolve("manifest.csv");
        List<String> manifest = Files.readAllLines(manifestPath);

        // Re-derive N from manifest: rows in matrix A.
        int N = 0;
        for (int i = 1; i < manifest.size(); i++) {
            if (manifest.get(i).split(",", -1)[1].equals("A")) N++;
        }
        if (N == 0) throw new IOException("Manifest has no A-matrix rows");

        // Read all dashboard outputs into per-metric Y arrays.
        // y[m][k] = output metric m for sample k.
        // Sample order: 0..N-1 = A; N..2N-1 = B; 2N + j*N + i = AB^j row i.
        int total = N * (p + 2);
        double[][] y = new double[METRICS.length][total];
        boolean[] valid = new boolean[total];

        String pflowHome = System.getenv("PFLOW_HOME");
        if (pflowHome == null) pflowHome = "H:/Dropbox/PFLOW";
        Path tokyoOutBase = Paths.get(pflowHome, "output/trips/taxi/tokyo");

        for (int i = 1; i < manifest.size(); i++) {
            String[] cols = manifest.get(i).split(",", -1);
            int sampleId = Integer.parseInt(cols[0]);
            String sampleSubdir = cols[4];  // e.g. sobol_sample_00000
            Path sampleOutDir = tokyoOutBase.resolve(sampleSubdir);
            if (!Files.isDirectory(sampleOutDir)) continue;
            Path dashboard = null;
            try (var dirs = Files.list(sampleOutDir)) {
                Optional<Path> latest = dirs
                    .filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("run_"))
                    .max(Comparator.comparing(pp -> pp.getFileName().toString()));
                if (latest.isPresent()) {
                    dashboard = latest.get().resolve("dashboard.csv");
                }
            }
            if (dashboard == null || !Files.exists(dashboard)) continue;
            Map<String, Double> metrics = parseDashboard(dashboard);
            valid[sampleId] = true;
            for (int m = 0; m < METRICS.length; m++) {
                y[m][sampleId] = metrics.getOrDefault(METRICS[m], 0.0);
            }
        }

        try (BufferedWriter w = Files.newBufferedWriter(resultsOut)) {
            w.write("output_metric,parameter_name,S_first,S_total,n_valid_pairs\n");
            for (int m = 0; m < METRICS.length; m++) {
                double[] yA = sliceN(y[m], 0, N);
                double[] yB = sliceN(y[m], N, N);
                double meanY = mean(yA);
                double varY = variance(yA, meanY);
                if (varY <= 0) {
                    System.out.println("[Sobol] WARN metric " + METRICS[m] + " has zero variance");
                    continue;
                }
                for (int j = 0; j < p; j++) {
                    int abStart = 2 * N + j * N;
                    double[] yABj = sliceN(y[m], abStart, N);
                    int nValid = 0;
                    double s1 = 0, st = 0;
                    for (int i = 0; i < N; i++) {
                        if (!valid[i] || !valid[N + i] || !valid[abStart + i]) continue;
                        // Janon first-order: y_B (y_AB - y_A) / Var
                        s1 += yB[i] * (yABj[i] - yA[i]);
                        // Saltelli total-order: 0.5 * (y_A - y_AB)²
                        double d = yA[i] - yABj[i];
                        st += 0.5 * d * d;
                        nValid++;
                    }
                    if (nValid > 0) {
                        s1 /= nValid; st /= nValid;
                    }
                    double S_first = s1 / varY;
                    double S_total = st / varY;
                    w.write(String.format("%s,%s,%.6f,%.6f,%d%n",
                        METRICS[m], selected.get(j).name, S_first, S_total, nValid));
                }
            }
        }
        System.out.println("Wrote " + resultsOut);
    }

    /** Parse dashboard.csv into a map of "CATEGORY.metric" -> value. */
    static Map<String, Double> parseDashboard(Path dashboardCsv) throws IOException {
        Map<String, Double> out = new HashMap<>();
        List<String> lines = Files.readAllLines(dashboardCsv);
        for (String line : lines) {
            if (line.startsWith("#") || line.startsWith("category") || line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length < 3) continue;
            String key = cols[0].trim() + "." + cols[1].trim();
            try {
                out.put(key, Double.parseDouble(cols[2].trim()));
            } catch (NumberFormatException e) { /* skip */ }
        }
        // Synthetic: 実働率 = fleet.size / fleet.total_registered ×100. We don't
        // expose total_registered directly, but fleet operating rate is constant
        // per config (validation engine prints it as "Fleet Operating Rate").
        // For Sobol we treat it as an output anyway — read from validation.csv
        // sibling if available.
        Path sibling = dashboardCsv.resolveSibling("validation.csv");
        if (Files.exists(sibling)) {
            for (String line : Files.readAllLines(sibling)) {
                if (line.contains("Fleet Operating Rate") && !line.startsWith("metric_name")) {
                    String[] vc = line.split(",", -1);
                    if (vc.length > 2) {
                        try { out.put("FLEET.operating_rate_pct", Double.parseDouble(vc[2].trim())); }
                        catch (NumberFormatException e) { }
                    }
                }
            }
        }
        return out;
    }

    private static double[][] sampleUnit(Random r, int N, int p) {
        double[][] m = new double[N][p];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < p; j++)
                m[i][j] = r.nextDouble();
        return m;
    }

    private static double[] sliceN(double[] a, int start, int n) {
        double[] s = new double[n];
        System.arraycopy(a, start, s, 0, n);
        return s;
    }

    private static double mean(double[] a) {
        double s = 0; for (double v : a) s += v;
        return s / a.length;
    }

    private static double variance(double[] a, double m) {
        double s = 0; for (double v : a) s += (v - m) * (v - m);
        return s / a.length;
    }
}
