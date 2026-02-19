package truck.sim;

import java.io.*;
import java.util.*;

/**
 * Validation engine for Tokyo truck simulation results.
 *
 * DATA SOURCES:
 * - Tokyo Metropolitan Freight Survey (MFS H25/2013) - Original 327k baseline
 * - MLIT National Statistics - 2.53M registered, 56.7% operating rate
 * - Scaled validation: 4.387x multiplier for absolute metrics
 *
 * Validation modes:
 * - Primary: CSV-driven (config/truck/validation/mfs_baseline.csv)
 * - Fallback: Hardcoded targets (legacy, if CSV unavailable)
 *
 * @version 2.1 - MLIT Scaling Support
 */
public class ValidationEngine {

    // ========================================================================
    // HARDCODED TARGETS (legacy fallback)
    // ========================================================================

    private static class ValidationTargets {
        static final double DELIVERY_PERCENT = 37.0;
        static final double LONGHAUL_PERCENT = 34.5;
        static final double URBAN_PERCENT = 28.5;

        static final double HEAVY_PERCENT = 26.07;
        static final double MEDIUM_PERCENT = 16.86;
        static final double SMALL_PERCENT = 28.65;
        static final double LIGHT_PERCENT = 28.41;

        static final double EMPTY_RATIO_MIN = 0.05;
        static final double EMPTY_RATIO_MAX = 0.10;
        static final double INTRA_ZONE_MIN = 0.30;
        static final double INTRA_ZONE_MAX = 0.45;
        static final double AVG_CARGO_TONS = 3.50;

        static final Map<String, Double> COMMODITY_TARGETS = new HashMap<String, Double>() {{
            put("daily_necessities", 27.0);
            put("agricultural_food", 22.0);
            put("publications", 11.0);
            put("light_industrial", 16.0);
            put("machinery", 7.0);
            put("forestry_mineral", 7.0);
            put("metal_products", 5.0);
            put("ceramic_chemical", 3.0);
            put("special_products", 2.0);
        }};

        static final double TIGHT_TOLERANCE = 0.10;
        static final double NORMAL_TOLERANCE = 0.15;
        static final double LOOSE_TOLERANCE = 0.35;
    }

    // ========================================================================
    // BASELINE METRIC (CSV row)
    // ========================================================================

    /**
     * Represents a single metric row from the CSV baseline file.
     */
    private static class BaselineMetric {
        final String metricName;
        final String category;
        final double surveyValue;
        final String unit;
        final double tolerancePct;  // e.g., 10 means ±10%
        final String sourceFile;
        final String notes;
        final String priority;      // CRITICAL, HIGH, MEDIUM

        BaselineMetric(String metricName, String category, double surveyValue,
                       String unit, double tolerancePct, String sourceFile,
                       String notes, String priority) {
            this.metricName = metricName;
            this.category = category;
            this.surveyValue = surveyValue;
            this.unit = unit;
            this.tolerancePct = tolerancePct;
            this.sourceFile = sourceFile;
            this.notes = notes;
            this.priority = priority;
        }

        /** Tolerance as a fraction (e.g., 0.10 for 10%). */
        double toleranceFraction() {
            return tolerancePct / 100.0;
        }
    }

    // ========================================================================
    // VALIDATION RESULT
    // ========================================================================

    /**
     * Validation result for a single metric.
     */
    public static class ValidationResult {
        public final String metric;
        public final String category;
        public final double actual;
        public final double target;
        public final double tolerance;
        public final boolean passed;
        public final String status;

        public ValidationResult(String metric, String category, double actual,
                               double target, double tolerance) {
            this.metric = metric;
            this.category = category;
            this.actual = actual;
            this.target = target;
            this.tolerance = tolerance;

            double error = Math.abs(actual - target);
            this.passed = target == 0 ? (actual == 0) : (error <= tolerance * Math.abs(target));
            this.status = passed ? "PASS" : "FAIL";
        }

        /** Constructor without category (for hardcoded fallback). */
        public ValidationResult(String metric, double actual, double target,
                               double tolerance) {
            this(metric, "", actual, target, tolerance);
        }

        public double getErrorPercent() {
            if (target == 0) return actual == 0 ? 0.0 : 100.0;
            return Math.abs(actual - target) / Math.abs(target) * 100.0;
        }

        @Override
        public String toString() {
            String icon = passed ? "PASS" : "FAIL";
            if (target > 1000) {
                // Large numbers: format with commas
                return String.format("  %-40s %,14.0f  (target: %,14.0f +/-%3.0f%%)  [error: %5.1f%%]  %s",
                    metric, actual, target, tolerance * 100, getErrorPercent(), icon);
            } else {
                return String.format("  %-40s %14.2f  (target: %14.2f +/-%3.0f%%)  [error: %5.1f%%]  %s",
                    metric, actual, target, tolerance * 100, getErrorPercent(), icon);
            }
        }
    }

    // ========================================================================
    // VALIDATION REPORT
    // ========================================================================

    /**
     * Overall validation report.
     */
    public static class ValidationReport {
        public final List<ValidationResult> results;
        public final int totalTests;
        public final int passedTests;
        public final double passRate;
        public final String grade;

        public ValidationReport(List<ValidationResult> results) {
            this.results = results;
            this.totalTests = results.size();
            this.passedTests = (int) results.stream().filter(r -> r.passed).count();
            this.passRate = totalTests > 0 ? (double) passedTests / totalTests : 0.0;

            if (passRate >= 0.95) grade = "A+";
            else if (passRate >= 0.90) grade = "A";
            else if (passRate >= 0.85) grade = "A-";
            else if (passRate >= 0.80) grade = "B+";
            else if (passRate >= 0.75) grade = "B";
            else if (passRate >= 0.70) grade = "B-";
            else if (passRate >= 0.65) grade = "C+";
            else if (passRate >= 0.60) grade = "C";
            else grade = "F";
        }

        public void printReport() {
            String separator = repeatString("=", 100);
            System.out.println("\n" + separator);
            System.out.println("MFS VALIDATION REPORT (CSV-DRIVEN)");
            System.out.println(separator);

            // Collect unique categories in order
            LinkedHashSet<String> categories = new LinkedHashSet<>();
            for (ValidationResult r : results) {
                categories.add(r.category.isEmpty() ? "UNCATEGORIZED" : r.category);
            }

            for (String cat : categories) {
                System.out.println("\n[" + cat.toUpperCase().replace("_", " ") + "]");
                for (ValidationResult r : results) {
                    String rCat = r.category.isEmpty() ? "UNCATEGORIZED" : r.category;
                    if (rCat.equals(cat)) {
                        System.out.println(r);
                    }
                }
            }

            // Summary
            System.out.println("\n" + separator);
            System.out.println(String.format("SUMMARY: %d/%d tests passed (%.1f%%)",
                passedTests, totalTests, passRate * 100));
            System.out.println("GRADE: " + grade);

            // Show failing tests
            long failCount = results.stream().filter(r -> !r.passed).count();
            if (failCount > 0) {
                System.out.println("\nFAILING TESTS:");
                for (ValidationResult r : results) {
                    if (!r.passed) {
                        System.out.println("  - " + r.metric + " (error: " +
                            String.format("%.1f%%", r.getErrorPercent()) +
                            ", tolerance: " + String.format("%.0f%%", r.tolerance * 100) + ")");
                    }
                }
            }

            System.out.println(separator + "\n");
        }

        private static String repeatString(String str, int count) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append(str);
            }
            return sb.toString();
        }
    }

    // ========================================================================
    // CSV-BASED VALIDATION (PRIMARY)
    // ========================================================================

    private String baselineFilePath;
    private Map<String, BaselineMetric> baselineMetrics;

    /**
     * Constructor that loads baseline from CSV file.
     *
     * @param baselineFilePath Path to mfs_validation_baseline_updated.csv
     */
    public ValidationEngine(String baselineFilePath) {
        this.baselineFilePath = baselineFilePath;
        this.baselineMetrics = new LinkedHashMap<>();
        try {
            loadBaseline();
            System.out.println("[Validation] Loaded " + baselineMetrics.size() +
                             " baseline metrics from " + baselineFilePath);
        } catch (IOException e) {
            System.err.println("[Validation] Warning: Could not load baseline from " +
                             baselineFilePath + ": " + e.getMessage());
            System.err.println("[Validation] Will use hardcoded validation targets as fallback.");
        }
    }

    /**
     * Load baseline metrics from CSV file.
     *
     * CSV format (8 columns):
     * metric_name,category,survey_value,unit,tolerance_pct,source_file,notes,priority
     */
    private void loadBaseline() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(baselineFilePath))) {
            String line = br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // Parse CSV with quoted fields (notes column may contain commas)
                String[] parts = parseCSVLine(line);
                if (parts.length < 5) continue;

                try {
                    String metricName = parts[0].trim();
                    String category = parts.length > 1 ? parts[1].trim() : "";
                    double surveyValue = Double.parseDouble(parts[2].trim());
                    String unit = parts.length > 3 ? parts[3].trim() : "";
                    double tolerancePct = Double.parseDouble(parts[4].trim());
                    String sourceFile = parts.length > 5 ? parts[5].trim() : "";
                    String notes = parts.length > 6 ? parts[6].trim() : "";
                    String priority = parts.length > 7 ? parts[7].trim() : "MEDIUM";

                    baselineMetrics.put(metricName, new BaselineMetric(
                        metricName, category, surveyValue, unit,
                        tolerancePct, sourceFile, notes, priority));
                } catch (NumberFormatException e) {
                    System.err.println("[Validation] Skipping invalid row: " + line);
                }
            }
        }
    }

    /**
     * Parse a CSV line handling quoted fields (for notes with commas).
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Check if baseline data is loaded.
     */
    public boolean hasBaseline() {
        return !baselineMetrics.isEmpty();
    }

    /**
     * Get baseline value for a specific metric.
     */
    public double getBaselineValue(String metricName) {
        BaselineMetric m = baselineMetrics.get(metricName);
        return m != null ? m.surveyValue : 0.0;
    }

    /**
     * CSV-driven validation: validate simulation against all baseline metrics.
     * This is the primary validation method that produces the graded report.
     *
     * @param trucks List of truck agents
     * @param trips List of all trips
     * @param gaBalancer Generation-Attraction balancer (may be null)
     * @return ValidationReport with pass/fail for each matched metric
     */
    public ValidationReport validateFromBaseline(List<TruckAgent> trucks, List<TruckTrip> trips,
                                                  GenerationAttractionBalancer gaBalancer) {
        // Calculate all simulation metrics
        SimulationMetrics simMetrics = new SimulationMetrics(trucks, trips);
        simMetrics.calculateAllMetrics();
        Map<String, Double> metricValues = simMetrics.getAllMetrics();

        List<ValidationResult> results = new ArrayList<>();
        List<String> missingMetrics = new ArrayList<>();

        // Validate each CSV baseline metric against simulation output
        for (Map.Entry<String, BaselineMetric> entry : baselineMetrics.entrySet()) {
            String metricName = entry.getKey();
            BaselineMetric baseline = entry.getValue();

            if (metricValues.containsKey(metricName)) {
                double actual = metricValues.get(metricName);
                results.add(new ValidationResult(
                    metricName,
                    baseline.category,
                    actual,
                    baseline.surveyValue,
                    baseline.toleranceFraction()
                ));
            } else {
                missingMetrics.add(metricName);
            }
        }

        // Add G-A balance tests from balancer (if available)
        if (gaBalancer != null) {
            Map<String, Double> gaStats = gaBalancer.getBalanceStats();
            double genError = gaStats.get("gen_error_pct");
            double attError = gaStats.get("att_error_pct");
            double balanceError = gaStats.get("balance_error_pct");

            results.add(new ValidationResult(
                "G-A Generation Balance", "ga_balance_dynamic",
                100.0 - genError, 100.0, ValidationTargets.LOOSE_TOLERANCE));

            results.add(new ValidationResult(
                "G-A Attraction Balance", "ga_balance_dynamic",
                100.0 - attError, 100.0, ValidationTargets.LOOSE_TOLERANCE));

            results.add(new ValidationResult(
                "G-A Mass Balance", "ga_balance_dynamic",
                100.0 - balanceError, 100.0, ValidationTargets.TIGHT_TOLERANCE));
        }

        // Log missing metrics
        if (!missingMetrics.isEmpty()) {
            System.out.println("\n[Validation] Metrics in CSV but not computed by simulation:");
            for (String m : missingMetrics) {
                System.out.println("  - " + m);
            }
        }

        return new ValidationReport(results);
    }

    // ========================================================================
    // HARDCODED VALIDATION (FALLBACK)
    // ========================================================================

    /**
     * Hardcoded fallback validation (legacy).
     * Used when CSV baseline is not available.
     */
    public static ValidationReport validateHardcoded(List<TruckAgent> trucks,
                                                      List<TruckTrip> trips,
                                                      GenerationAttractionBalancer gaBalancer) {
        List<ValidationResult> results = new ArrayList<>();
        SimulationStats stats = calculateStats(trucks, trips);

        // Agent type distribution
        results.add(new ValidationResult("DELIVERY %",
            stats.deliveryPercent, ValidationTargets.DELIVERY_PERCENT,
            ValidationTargets.TIGHT_TOLERANCE));
        results.add(new ValidationResult("LONG_HAUL %",
            stats.longHaulPercent, ValidationTargets.LONGHAUL_PERCENT,
            ValidationTargets.TIGHT_TOLERANCE));
        results.add(new ValidationResult("MIXED_OPERATION %",
            stats.urbanPercent, ValidationTargets.URBAN_PERCENT,
            ValidationTargets.TIGHT_TOLERANCE));

        // Fleet composition
        results.add(new ValidationResult("Heavy vehicles %",
            stats.heavyPercent, ValidationTargets.HEAVY_PERCENT,
            ValidationTargets.LOOSE_TOLERANCE));
        results.add(new ValidationResult("Medium vehicles %",
            stats.mediumPercent, ValidationTargets.MEDIUM_PERCENT,
            ValidationTargets.LOOSE_TOLERANCE));
        results.add(new ValidationResult("Small vehicles %",
            stats.smallPercent, ValidationTargets.SMALL_PERCENT,
            ValidationTargets.NORMAL_TOLERANCE));
        results.add(new ValidationResult("Light vehicles %",
            stats.lightPercent, ValidationTargets.LIGHT_PERCENT,
            ValidationTargets.NORMAL_TOLERANCE));

        // Trip characteristics
        double emptyRatioTarget = (ValidationTargets.EMPTY_RATIO_MIN + ValidationTargets.EMPTY_RATIO_MAX) / 2;
        results.add(new ValidationResult("Empty trip ratio",
            stats.emptyRatio * 100, emptyRatioTarget * 100,
            ValidationTargets.NORMAL_TOLERANCE));

        double intraZoneTarget = (ValidationTargets.INTRA_ZONE_MIN + ValidationTargets.INTRA_ZONE_MAX) / 2;
        results.add(new ValidationResult("Intra-zone trips %",
            stats.intraZonePercent, intraZoneTarget * 100,
            ValidationTargets.NORMAL_TOLERANCE));

        results.add(new ValidationResult("Avg cargo weight (tons)",
            stats.avgCargoTons, ValidationTargets.AVG_CARGO_TONS,
            ValidationTargets.LOOSE_TOLERANCE));

        // Commodity mix
        for (Map.Entry<String, Double> entry : stats.commodityPercents.entrySet()) {
            String commodity = entry.getKey();
            Double target = ValidationTargets.COMMODITY_TARGETS.get(commodity);
            if (target != null) {
                results.add(new ValidationResult(commodity + " commodity %",
                    entry.getValue(), target, ValidationTargets.LOOSE_TOLERANCE));
            }
        }

        // G-A balance
        if (gaBalancer != null) {
            Map<String, Double> gaStats = gaBalancer.getBalanceStats();
            double genError = gaStats.get("gen_error_pct");
            double attError = gaStats.get("att_error_pct");
            double balanceError = gaStats.get("balance_error_pct");

            results.add(new ValidationResult("G-A Generation Balance",
                100.0 - genError, 100.0, ValidationTargets.LOOSE_TOLERANCE));
            results.add(new ValidationResult("G-A Attraction Balance",
                100.0 - attError, 100.0, ValidationTargets.LOOSE_TOLERANCE));
            results.add(new ValidationResult("G-A Mass Balance",
                100.0 - balanceError, 100.0, ValidationTargets.TIGHT_TOLERANCE));
        }

        return new ValidationReport(results);
    }

    /**
     * Calculate simulation statistics for hardcoded validation.
     */
    private static class SimulationStats {
        double deliveryPercent, longHaulPercent, urbanPercent;
        double heavyPercent, mediumPercent, smallPercent, lightPercent;
        double emptyRatio, intraZonePercent, avgCargoTons;
        Map<String, Double> commodityPercents;
    }

    private static SimulationStats calculateStats(List<TruckAgent> trucks, List<TruckTrip> trips) {
        SimulationStats stats = new SimulationStats();

        int deliveryCount = 0, longHaulCount = 0, urbanCount = 0;
        for (TruckAgent truck : trucks) {
            switch (truck.getTruckType()) {
                case DELIVERY: deliveryCount++; break;
                case LONG_HAUL: longHaulCount++; break;
                case MIXED_OPERATION: urbanCount++; break;
            }
        }

        int totalTrucks = trucks.size();
        stats.deliveryPercent = (double) deliveryCount / totalTrucks * 100;
        stats.longHaulPercent = (double) longHaulCount / totalTrucks * 100;
        stats.urbanPercent = (double) urbanCount / totalTrucks * 100;

        int heavyCount = 0, mediumCount = 0, smallCount = 0, lightCount = 0;
        for (TruckAgent truck : trucks) {
            switch (truck.getVehicleSize().toLowerCase()) {
                case "heavy": heavyCount++; break;
                case "medium": mediumCount++; break;
                case "small": smallCount++; break;
                case "light": lightCount++; break;
            }
        }

        stats.heavyPercent = (double) heavyCount / totalTrucks * 100;
        stats.mediumPercent = (double) mediumCount / totalTrucks * 100;
        stats.smallPercent = (double) smallCount / totalTrucks * 100;
        stats.lightPercent = (double) lightCount / totalTrucks * 100;

        int emptyTrips = 0, deliveryTrips = 0, intraZoneTrips = 0;
        double totalCargo = 0.0;

        for (TruckTrip trip : trips) {
            if (trip.getStatus() == TruckStatus.EMPTY_RUNNING) {
                emptyTrips++;
            } else {
                deliveryTrips++;
                totalCargo += trip.getCargoWeightTons();
                if (trip.getOriginZoneId() != null &&
                    trip.getOriginZoneId().equals(trip.getDestZoneId())) {
                    intraZoneTrips++;
                }
            }
        }

        int totalTrips = trips.size();
        stats.emptyRatio = totalTrips > 0 ? (double) emptyTrips / totalTrips : 0.0;
        stats.intraZonePercent = deliveryTrips > 0 ? (double) intraZoneTrips / deliveryTrips * 100 : 0.0;
        stats.avgCargoTons = deliveryTrips > 0 ? totalCargo / deliveryTrips : 0.0;

        Map<String, Integer> commodityCounts = new HashMap<>();
        for (TruckTrip trip : trips) {
            if (trip.getStatus() != TruckStatus.EMPTY_RUNNING && trip.getGoodsType() != null) {
                commodityCounts.put(trip.getGoodsType(),
                    commodityCounts.getOrDefault(trip.getGoodsType(), 0) + 1);
            }
        }

        stats.commodityPercents = new HashMap<>();
        for (Map.Entry<String, Integer> entry : commodityCounts.entrySet()) {
            stats.commodityPercents.put(entry.getKey(),
                (double) entry.getValue() / deliveryTrips * 100);
        }

        return stats;
    }

    /**
     * Helper method to repeat a string (Java 8 compatible).
     */
    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
