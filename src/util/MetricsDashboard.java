package util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Unified metrics dashboard writer for truck and taxi ABM simulations.
 * <p>
 * Produces 2 standardized CSV files per run:
 * <ul>
 *   <li><b>dashboard.csv</b> — All metrics in one file (category, metric, value, target, status)</li>
 *   <li><b>validation.csv</b> — Pass/fail validation results with error% and grade</li>
 * </ul>
 * <p>
 * Both files use the same format regardless of vehicle type, making it easy
 * to compare truck vs taxi runs, track metrics across versions, and monitor
 * simulation quality at a glance.
 *
 * @version 1.0
 */
public class MetricsDashboard {

    /**
     * Write a unified dashboard.csv from a metrics map.
     * Format: category,metric,value,unit
     *
     * @param outputDir run directory (e.g., output/trips/truck/run_20260404_165638/)
     * @param vehicleType "truck" or "taxi"
     * @param cityName city name (e.g., "tokyo") or null for truck
     * @param metrics ordered map of category.metric → value
     */
    public static void writeDashboard(String outputDir, String vehicleType, String cityName,
                                       Map<String, Double> metrics) {
        String path = outputDir + "/dashboard.csv";
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            // Header
            pw.println("# PFLOW Simulation Dashboard");
            pw.printf("# Vehicle: %s%s%n", vehicleType, cityName != null ? " (" + cityName + ")" : "");
            pw.printf("# Generated: %s%n", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            pw.println("category,metric,value,unit");

            for (Map.Entry<String, Double> e : metrics.entrySet()) {
                String key = e.getKey();
                double val = e.getValue();
                // Split "CATEGORY.metric_name" → category + metric
                int dot = key.indexOf('.');
                String cat = dot > 0 ? key.substring(0, dot) : "GENERAL";
                String metric = dot > 0 ? key.substring(dot + 1) : key;
                String unit = inferUnit(metric);

                if (val == Math.floor(val) && val < 1e9) {
                    pw.printf("%s,%s,%.0f,%s%n", cat, metric, val, unit);
                } else {
                    pw.printf("%s,%s,%.2f,%s%n", cat, metric, val, unit);
                }
            }
            System.out.println("  dashboard.csv: " + metrics.size() + " metrics");
        } catch (IOException e) {
            System.err.println("[DASHBOARD] Error writing dashboard.csv: " + e.getMessage());
        }
    }

    /**
     * Write a unified validation.csv from validation results.
     * Format: category,metric,actual,target,tolerance_pct,error_pct,status,grade
     *
     * @param outputDir run directory
     * @param results list of [category, metric, actual, target, tolerancePct, errorPct, status]
     * @param grade overall grade string (A+, A, B, etc.)
     * @param passRate pass rate (0.0-1.0)
     */
    public static void writeValidation(String outputDir, List<String[]> results,
                                        String grade, double passRate) {
        String path = outputDir + "/validation.csv";
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            pw.println("category,metric,actual,target,tolerance_pct,error_pct,status");
            for (String[] row : results) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        row[0], row[1], row[2], row[3], row[4], row[5], row[6]);
            }
            pw.println("# SUMMARY");
            pw.printf("# Grade: %s (%d/%d passed, %.1f%%)%n",
                    grade, (int)(passRate * results.size()), results.size(), passRate * 100);
            System.out.printf("  validation.csv: %s (%d/%d tests)%n",
                    grade, (int)(passRate * results.size()), results.size());
        } catch (IOException e) {
            System.err.println("[DASHBOARD] Error writing validation.csv: " + e.getMessage());
        }
    }

    /**
     * Build a standardized metrics map for truck ABM.
     */
    public static Map<String, Double> buildTruckMetrics(
            int totalTrucks, int deliveryCount, int longHaulCount, int mixedCount,
            int heavyCount, int mediumCount, int smallCount, int lightCount,
            int totalTrips, int deliveryTrips, int emptyTrips,
            double totalDistanceKm, double deliveryDistanceKm, double emptyDistanceKm,
            double totalCargoTons, double avgCargoTons,
            double avgTripsPerTruck, double avgDistancePerTrip,
            Map<String, Double> commodityPcts) {

        Map<String, Double> m = new LinkedHashMap<>();

        // Fleet
        m.put("FLEET.total_trucks", (double) totalTrucks);
        m.put("FLEET.heavy_count", (double) heavyCount);
        m.put("FLEET.medium_count", (double) mediumCount);
        m.put("FLEET.small_count", (double) smallCount);
        m.put("FLEET.light_count", (double) lightCount);
        m.put("FLEET.heavy_pct", 100.0 * heavyCount / totalTrucks);
        m.put("FLEET.medium_pct", 100.0 * mediumCount / totalTrucks);
        m.put("FLEET.small_pct", 100.0 * smallCount / totalTrucks);
        m.put("FLEET.light_pct", 100.0 * lightCount / totalTrucks);
        m.put("FLEET.delivery_pct", 100.0 * deliveryCount / totalTrucks);
        m.put("FLEET.long_haul_pct", 100.0 * longHaulCount / totalTrucks);
        m.put("FLEET.mixed_pct", 100.0 * mixedCount / totalTrucks);

        // Trips
        m.put("TRIPS.total", (double) totalTrips);
        m.put("TRIPS.delivery", (double) deliveryTrips);
        m.put("TRIPS.empty", (double) emptyTrips);
        m.put("TRIPS.empty_ratio_pct", 100.0 * emptyTrips / totalTrips);
        m.put("TRIPS.avg_per_truck", avgTripsPerTruck);

        // Distance
        m.put("DISTANCE.total_km", totalDistanceKm);
        m.put("DISTANCE.delivery_km", deliveryDistanceKm);
        m.put("DISTANCE.empty_km", emptyDistanceKm);
        m.put("DISTANCE.empty_running_pct", 100.0 * emptyDistanceKm / totalDistanceKm);
        m.put("DISTANCE.avg_per_trip_km", avgDistancePerTrip);
        m.put("DISTANCE.avg_per_truck_km", totalDistanceKm / totalTrucks);

        // Cargo
        m.put("CARGO.total_tons", totalCargoTons);
        m.put("CARGO.avg_per_truck_tons", totalCargoTons / totalTrucks);
        m.put("CARGO.avg_per_trip_tons", avgCargoTons);

        // Commodity
        if (commodityPcts != null) {
            for (Map.Entry<String, Double> e : commodityPcts.entrySet()) {
                m.put("COMMODITY." + e.getKey() + "_pct", e.getValue());
            }
        }

        return m;
    }

    /**
     * Build a standardized metrics map for taxi ABM.
     */
    public static Map<String, Double> buildTaxiMetrics(
            int fleetSize, int localCount, int citywideCount, int hubCount,
            long passengerTrips, long emptyTrips,
            double passengerDistanceKm, double emptyDistanceKm,
            double totalRevenueYen, long nightTrips,
            double avgFare, double avgTripDistance) {

        Map<String, Double> m = new LinkedHashMap<>();
        long totalTrips = passengerTrips + emptyTrips;
        double totalDistance = passengerDistanceKm + emptyDistanceKm;

        // Fleet
        m.put("FLEET.total_taxis", (double) fleetSize);
        m.put("FLEET.local_count", (double) localCount);
        m.put("FLEET.citywide_count", (double) citywideCount);
        m.put("FLEET.hub_count", (double) hubCount);
        m.put("FLEET.local_pct", 100.0 * localCount / fleetSize);
        m.put("FLEET.citywide_pct", 100.0 * citywideCount / fleetSize);
        m.put("FLEET.hub_pct", 100.0 * hubCount / fleetSize);

        // Trips
        m.put("TRIPS.total", (double) totalTrips);
        m.put("TRIPS.passenger", (double) passengerTrips);
        m.put("TRIPS.empty", (double) emptyTrips);
        m.put("TRIPS.empty_ratio_pct", 100.0 * emptyTrips / totalTrips);
        m.put("TRIPS.passenger_per_taxi", (double) passengerTrips / fleetSize);

        // Distance
        m.put("DISTANCE.total_km", totalDistance);
        m.put("DISTANCE.passenger_km", passengerDistanceKm);
        m.put("DISTANCE.empty_km", emptyDistanceKm);
        m.put("DISTANCE.empty_running_pct", 100.0 * emptyDistanceKm / totalDistance);
        m.put("DISTANCE.avg_per_trip_km", avgTripDistance);
        m.put("DISTANCE.avg_per_taxi_km", totalDistance / fleetSize);

        // Revenue
        m.put("REVENUE.total_yen", totalRevenueYen);
        m.put("REVENUE.avg_fare_yen", avgFare);
        m.put("REVENUE.per_taxi_yen", totalRevenueYen / fleetSize);
        m.put("REVENUE.per_km_yen", totalDistance > 0 ? totalRevenueYen / totalDistance : 0.0);

        // Temporal
        m.put("TEMPORAL.night_trips", (double) nightTrips);
        m.put("TEMPORAL.night_pct", passengerTrips > 0 ? 100.0 * nightTrips / passengerTrips : 0.0);

        return m;
    }

    private static String inferUnit(String metric) {
        String m = metric.toLowerCase();
        if (m.contains("_pct") || m.contains("ratio") || m.contains("percentage")) return "%";
        if (m.contains("_yen") || m.contains("revenue") || m.contains("fare")) return "yen";
        if (m.contains("_tons") || m.contains("cargo")) return "tons";
        if (m.contains("_km") || m.contains("distance")) return "km";
        if (m.contains("count") || m.contains("total") || m.contains("trips")) return "count";
        return "";
    }
}
