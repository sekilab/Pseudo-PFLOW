package truck.sim;

import java.io.*;
import java.util.*;

/**
 * Standalone validation tool that grades existing simulation outputs
 * without re-running the simulation.
 *
 * Reads dashboard.csv from a completed run directory and validates
 * against MFS baselines in config/truck/validation/mfs_baseline.csv.
 *
 * Usage:
 *   java truck.sim.StandaloneValidator <run_directory> [baseline_csv]
 *
 * Examples:
 *   java truck.sim.StandaloneValidator output/trips/truck/run_20260415_120000/
 *   java truck.sim.StandaloneValidator output/trips/truck/run_20260415_120000/ config/truck/validation/mfs_baseline.csv
 *
 * @version 1.0
 */
public class StandaloneValidator {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java truck.sim.StandaloneValidator <run_directory> [baseline_csv]");
            System.err.println("  run_directory: path to a completed truck simulation run (must contain dashboard.csv)");
            System.err.println("  baseline_csv:  path to validation baselines (default: config/truck/validation/mfs_baseline.csv)");
            System.exit(1);
        }

        String runDir = args[0];
        String baselinePath = args.length > 1 ? args[1] : "config/truck/validation/mfs_baseline.csv";

        // Verify run directory exists
        File dashboardFile = new File(runDir, "dashboard.csv");
        if (!dashboardFile.exists()) {
            System.err.println("[ERROR] dashboard.csv not found in: " + runDir);
            System.err.println("  Expected file: " + dashboardFile.getAbsolutePath());
            System.err.println("  Is this a valid truck simulation run directory?");
            System.exit(1);
        }

        // Verify baseline file exists
        File baselineFile = new File(baselinePath);
        if (!baselineFile.exists()) {
            System.err.println("[ERROR] Baseline file not found: " + baselinePath);
            System.exit(1);
        }

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║          STANDALONE TRUCK VALIDATION TOOL v1.0           ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("  Run directory: " + runDir);
        System.out.println("  Baseline:      " + baselinePath);
        System.out.println();

        try {
            // Step 1: Read dashboard.csv
            Map<String, Double> dashboardMetrics = readDashboard(dashboardFile);
            System.out.println("[OK] Loaded " + dashboardMetrics.size() + " metrics from dashboard.csv");

            // Step 2: Read baseline CSV
            Map<String, BaselineEntry> baselines = readBaseline(baselineFile);
            System.out.println("[OK] Loaded " + baselines.size() + " baseline targets from " + baselinePath);

            // Step 3: Detect fleet scaling
            double baselineFleet = baselines.containsKey("Total Trucks") ? baselines.get("Total Trucks").surveyValue : 0;
            double simFleet = dashboardMetrics.getOrDefault("total_fleet", dashboardMetrics.getOrDefault("Total Trucks", 0.0));
            double scaleFactor = (baselineFleet > 0 && simFleet > 0) ? simFleet / baselineFleet : 1.0;
            boolean isScaled = Math.abs(scaleFactor - 1.0) > 0.05;
            if (isScaled) {
                System.out.printf("[INFO] Fleet scale factor: %.4f (sim=%,.0f, baseline=%,.0f)%n",
                    scaleFactor, simFleet, baselineFleet);
            }

            // Step 4: Validate
            List<ValidationEngine.ValidationResult> results = new ArrayList<>();
            List<String> unmatched = new ArrayList<>();

            for (Map.Entry<String, BaselineEntry> entry : baselines.entrySet()) {
                String metricName = entry.getKey();
                BaselineEntry baseline = entry.getValue();

                if (dashboardMetrics.containsKey(metricName)) {
                    double actual = dashboardMetrics.get(metricName);
                    double target = baseline.surveyValue;

                    // Scale absolute targets for reduced fleet runs
                    if (isScaled && isAbsoluteUnit(baseline.unit)) {
                        target *= scaleFactor;
                    }

                    results.add(new ValidationEngine.ValidationResult(
                        metricName, baseline.category, actual, target, baseline.tolerancePct / 100.0
                    ));
                } else {
                    unmatched.add(metricName);
                }
            }

            // Step 5: Print results
            System.out.println();
            String sep = "=".repeat(100);
            System.out.println(sep);
            System.out.println("VALIDATION RESULTS");
            System.out.println(sep);

            int passed = 0;
            int failed = 0;
            for (ValidationEngine.ValidationResult r : results) {
                System.out.println(r.toString());
                if (r.passed) passed++; else failed++;
            }

            System.out.println(sep);
            int total = passed + failed;
            double passRate = total > 0 ? (double) passed / total : 0;
            String grade;
            if (passRate >= 0.95) grade = "A+";
            else if (passRate >= 0.90) grade = "A";
            else if (passRate >= 0.85) grade = "A-";
            else if (passRate >= 0.80) grade = "B+";
            else if (passRate >= 0.75) grade = "B";
            else if (passRate >= 0.70) grade = "B-";
            else if (passRate >= 0.65) grade = "C+";
            else if (passRate >= 0.60) grade = "C";
            else grade = "F";

            System.out.printf("%nGRADE: %s  (%d/%d passed, %.1f%%)%n", grade, passed, total, passRate * 100);

            if (!unmatched.isEmpty()) {
                System.out.println("\n[WARNING] " + unmatched.size() + " baseline metrics not found in dashboard:");
                for (String m : unmatched) {
                    System.out.println("  - " + m);
                }
            }

            // Also save validation.csv
            File validationOut = new File(runDir, "validation_standalone.csv");
            try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(validationOut)))) {
                pw.println("metric,category,actual,target,tolerance_pct,error_pct,status");
                for (ValidationEngine.ValidationResult r : results) {
                    pw.printf("%s,%s,%.4f,%.4f,%.1f,%.1f,%s%n",
                        r.metric, r.category, r.actual, r.target,
                        r.tolerance * 100, r.getErrorPercent(), r.status);
                }
                pw.printf("%n# Grade: %s (%d/%d)%n", grade, passed, total);
            }
            System.out.println("\n[OK] Results saved to: " + validationOut.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("[ERROR] Validation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** Read dashboard.csv into a metric_name -> value map. */
    private static Map<String, Double> readDashboard(File file) throws IOException {
        Map<String, Double> metrics = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!headerSkipped) { headerSkipped = true; continue; }
                String[] parts = line.split(",", -1);
                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    try {
                        double value = Double.parseDouble(parts[1].trim().replace(",", ""));
                        metrics.put(name, value);
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values (e.g., string columns)
                    }
                }
            }
        }
        return metrics;
    }

    /** Read mfs_baseline.csv into a metric_name -> BaselineEntry map. */
    private static Map<String, BaselineEntry> readBaseline(File file) throws IOException {
        Map<String, BaselineEntry> baselines = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!headerSkipped) { headerSkipped = true; continue; }
                String[] parts = line.split(",", -1);
                // Expected: metric_name, category, survey_value, unit, tolerance_pct, source_file, notes, priority
                if (parts.length >= 5) {
                    String name = parts[0].trim();
                    String category = parts[1].trim();
                    double surveyValue;
                    try {
                        surveyValue = Double.parseDouble(parts[2].trim().replace(",", ""));
                    } catch (NumberFormatException e) {
                        continue; // Skip rows with non-numeric survey values
                    }
                    String unit = parts[3].trim();
                    double tolerancePct;
                    try {
                        tolerancePct = Double.parseDouble(parts[4].trim());
                    } catch (NumberFormatException e) {
                        tolerancePct = 15.0; // Default tolerance
                    }
                    baselines.put(name, new BaselineEntry(name, category, surveyValue, unit, tolerancePct));
                }
            }
        }
        return baselines;
    }

    private static boolean isAbsoluteUnit(String unit) {
        if (unit == null) return false;
        return unit.toLowerCase().trim().endsWith("/day");
    }

    /** Lightweight baseline entry (no dependency on ValidationEngine internals). */
    private static class BaselineEntry {
        final String name;
        final String category;
        final double surveyValue;
        final String unit;
        final double tolerancePct;

        BaselineEntry(String name, String category, double surveyValue, String unit, double tolerancePct) {
            this.name = name;
            this.category = category;
            this.surveyValue = surveyValue;
            this.unit = unit;
            this.tolerancePct = tolerancePct;
        }
    }
}
