package truck.sim;

import java.util.*;
import java.io.*;

/**
 * Comprehensive metrics tracking for truck simulation.
 * 
 * Tracks metrics requested:
 * 1. Volume of trips (by type, zone, time period, commodity)
 * 2. Truck distances (total, per truck, by type)
 * 3. Number of trucks (total, active, by type)
 * 4. Total travel distances (loaded vs empty)
 * 5. Destination selection patterns (from O-D flows)
 * 
 * Integrates with 32 Tokyo MFS datasets for calibration and validation.
 * 
 * @version 1.0
 */
public class MetricsTracker {
    
    // Core metrics
    private int totalTrips;
    private int deliveryTrips;
    private int emptyTrips;
    private int intrametropolitanTrips;
    private int intermetropolitanTrips;
    
    private double totalDistanceKm;
    private double loadedDistanceKm;
    private double emptyDistanceKm;
    private double intrametroDistanceKm;
    private double intermetroDistanceKm;
    
    private int totalTrucks;
    private int activeTrucks;
    
    // Trip volume by attributes
    private Map<String, Integer> tripsByZone;              // Zone ID -> count
    private Map<String, Integer> tripsByCommodity;         // Commodity type -> count
    private Map<Integer, Integer> tripsByTimePeriod;       // Time period -> count
    private Map<TruckType, Integer> tripsByTruckType;      // Truck type -> count
    private Map<String, Integer> tripsByVehicleSize;       // Vehicle size -> count
    
    // Distance by attributes
    private Map<String, Double> distanceByZone;            // Zone ID -> distance km
    private Map<TruckType, Double> distanceByTruckType;    // Truck type -> distance km
    private Map<String, Double> distanceByVehicleSize;     // Vehicle size -> distance km

    // NEW: Phase 5 - Average distance per trip by truck type
    private Map<TruckType, Double> avgDistancePerTripByType;
    
    // O-D flow patterns (for destination selection analysis)
    private Map<String, Integer> odFlows;                  // "origin-destination" -> count
    private Map<String, Double> odDistances;               // "origin-destination" -> avg distance
    
    // Temporal patterns
    private Map<Integer, Integer> tripsByHour;             // Hour of day -> count
    
    // Cargo metrics
    private double totalCargoTons;
    private double avgCargoPerDelivery;
    private Map<String, Double> cargoByComommodity;         // Commodity -> total tons
    
    // Per-truck statistics
    private Map<Integer, TruckMetrics> truckMetrics;       // Truck ID -> metrics

    // MFS validation baseline (loaded from CSV)
    private Map<String, MFSBaselineMetric> mfsBaseline;
    private boolean validationEnabled;
    private Map<String, ValidationResult> validationResults;

    /**
     * MFS baseline metric from CSV.
     */
    public static class MFSBaselineMetric {
        public String metricName;
        public String category;
        public double surveyValue;
        public String unit;
        public String sourceFile;
        public String notes;

        public MFSBaselineMetric(String name, String category, double value,
                                 String unit, String source, String notes) {
            this.metricName = name;
            this.category = category;
            this.surveyValue = value;
            this.unit = unit;
            this.sourceFile = source;
            this.notes = notes;
        }
    }

    /**
     * Validation result container.
     */
    public static class ValidationResult {
        public String metricName;
        public double simulatedValue;
        public double surveyValue;
        public double percentDifference;
        public boolean withinTolerance;

        public ValidationResult(String name, double simulated, double survey, double tolerance) {
            this.metricName = name;
            this.simulatedValue = simulated;
            this.surveyValue = survey;
            this.percentDifference = survey != 0 ? Math.abs((simulated - survey) / survey * 100) : 0;
            this.withinTolerance = this.percentDifference <= tolerance;
        }
    }

    /**
     * Per-truck metrics container.
     */
    public static class TruckMetrics {
        public int truckId;
        public TruckType truckType;
        public String vehicleSize;
        public int totalTrips;
        public int deliveryTrips;
        public int emptyTrips;
        public double totalDistanceKm;
        public double totalCargoTons;
        public double utilizationRate;
        public List<String> destinationsVisited;
        
        public TruckMetrics(int truckId, TruckType truckType, String vehicleSize) {
            this.truckId = truckId;
            this.truckType = truckType;
            this.vehicleSize = vehicleSize;
            this.totalTrips = 0;
            this.deliveryTrips = 0;
            this.emptyTrips = 0;
            this.totalDistanceKm = 0.0;
            this.totalCargoTons = 0.0;
            this.utilizationRate = 0.0;
            this.destinationsVisited = new ArrayList<>();
        }
    }
    
    /**
     * Destination selection pattern.
     */
    public static class DestinationPattern {
        public String originZone;
        public Map<String, Integer> destinationCounts;     // Dest zone -> frequency
        public Map<String, Double> destinationDistances;   // Dest zone -> avg distance
        
        public DestinationPattern(String originZone) {
            this.originZone = originZone;
            this.destinationCounts = new HashMap<>();
            this.destinationDistances = new HashMap<>();
        }
        
        public void addDestination(String destZone, double distance) {
            destinationCounts.put(destZone, 
                destinationCounts.getOrDefault(destZone, 0) + 1);
            
            // Running average of distances
            double currentAvg = destinationDistances.getOrDefault(destZone, 0.0);
            int count = destinationCounts.get(destZone);
            double newAvg = (currentAvg * (count - 1) + distance) / count;
            destinationDistances.put(destZone, newAvg);
        }
        
        public String getMostFrequentDestination() {
            return destinationCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
        }
    }
    
    /**
     * Constructor.
     */
    public MetricsTracker() {
        this.totalTrips = 0;
        this.deliveryTrips = 0;
        this.emptyTrips = 0;
        this.intrametropolitanTrips = 0;
        this.intermetropolitanTrips = 0;
        
        this.totalDistanceKm = 0.0;
        this.loadedDistanceKm = 0.0;
        this.emptyDistanceKm = 0.0;
        this.intrametroDistanceKm = 0.0;
        this.intermetroDistanceKm = 0.0;
        
        this.totalTrucks = 0;
        this.activeTrucks = 0;
        
        this.tripsByZone = new HashMap<>();
        this.tripsByCommodity = new HashMap<>();
        this.tripsByTimePeriod = new HashMap<>();
        this.tripsByTruckType = new HashMap<>();
        this.tripsByVehicleSize = new HashMap<>();
        
        this.distanceByZone = new HashMap<>();
        this.distanceByTruckType = new HashMap<>();
        this.distanceByVehicleSize = new HashMap<>();
        this.avgDistancePerTripByType = new HashMap<>();  // NEW: Phase 5

        this.odFlows = new HashMap<>();
        this.odDistances = new HashMap<>();
        
        this.tripsByHour = new HashMap<>();
        
        this.totalCargoTons = 0.0;
        this.avgCargoPerDelivery = 0.0;
        this.cargoByComommodity = new HashMap<>();

        this.truckMetrics = new HashMap<>();

        this.mfsBaseline = null;
        this.validationEnabled = false;
        this.validationResults = new HashMap<>();
    }

    /**
     * Initialize MFS validation with loaded datasets.
     */
    /**
     * Load MFS validation baseline from CSV file.
     */
    public void loadMFSBaseline(String csvPath) {
        this.mfsBaseline = new LinkedHashMap<>();

        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            System.err.println("[MFS] Baseline CSV not found: " + csvPath);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip header
                }

                String[] parts = line.split(",", 6); // 6 columns
                if (parts.length < 4) continue;

                String metricName = parts[0].trim();
                String category = parts[1].trim();
                double surveyValue = Double.parseDouble(parts[2].trim());
                String unit = parts[3].trim();
                String sourceFile = parts.length > 4 ? parts[4].trim() : "";
                String notes = parts.length > 5 ? parts[5].trim() : "";

                mfsBaseline.put(metricName,
                    new MFSBaselineMetric(metricName, category, surveyValue,
                        unit, sourceFile, notes));
            }

            this.validationEnabled = true;
            this.validationResults = new HashMap<>();
            System.out.println("[MFS] Loaded " + mfsBaseline.size() +
                " baseline metrics from: " + csvPath);

        } catch (IOException | NumberFormatException e) {
            System.err.println("[MFS] Error loading baseline CSV: " + e.getMessage());
        }
    }

    /**
     * Initialize truck tracking.
     */
    public void initializeTrucks(List<TruckAgent> trucks) {
        this.totalTrucks = trucks.size();
        
        for (TruckAgent truck : trucks) {
            truckMetrics.put(truck.getTruckId(), 
                new TruckMetrics(truck.getTruckId(), truck.getTruckType(), 
                                truck.getVehicleSize()));
        }
    }
    
    /**
     * Record a trip.
     */
    public void recordTrip(TruckTrip trip, TruckAgent truck, 
                          boolean isInterMetropolitan) {
        // Core counters
        totalTrips++;
        
        if (trip.isCargoLoaded()) {
            deliveryTrips++;
            totalCargoTons += trip.getCargoWeightTons();
        } else {
            emptyTrips++;
        }
        
        if (isInterMetropolitan) {
            intermetropolitanTrips++;
            intermetroDistanceKm += trip.getDistanceKm();
        } else {
            intrametropolitanTrips++;
            intrametroDistanceKm += trip.getDistanceKm();
        }
        
        // Distance tracking
        double distance = trip.getDistanceKm();
        totalDistanceKm += distance;
        
        if (trip.isCargoLoaded()) {
            loadedDistanceKm += distance;
        } else {
            emptyDistanceKm += distance;
        }
        
        // Zone tracking
        String originZone = trip.getOriginZoneId() != null ? trip.getOriginZoneId() : "UNKNOWN";
        String destZone = trip.getDestZoneId() != null ? trip.getDestZoneId() : "UNKNOWN";
        
        tripsByZone.put(destZone, tripsByZone.getOrDefault(destZone, 0) + 1);
        distanceByZone.put(destZone, distanceByZone.getOrDefault(destZone, 0.0) + distance);
        
        // Commodity tracking
        if (trip.isCargoLoaded()) {
            String commodity = trip.getGoodsType();
            tripsByCommodity.put(commodity, tripsByCommodity.getOrDefault(commodity, 0) + 1);
            cargoByComommodity.put(commodity,
                cargoByComommodity.getOrDefault(commodity, 0.0) + trip.getCargoWeightTons());
        }
        
        // Time period tracking
        int timePeriod = getTimePeriod(trip.getDepartureTime());
        tripsByTimePeriod.put(timePeriod, tripsByTimePeriod.getOrDefault(timePeriod, 0) + 1);
        
        // Hour tracking
        int hour = (int) (trip.getDepartureTime() / 3600) % 24;
        tripsByHour.put(hour, tripsByHour.getOrDefault(hour, 0) + 1);
        
        // Truck type tracking
        tripsByTruckType.put(truck.getTruckType(), 
            tripsByTruckType.getOrDefault(truck.getTruckType(), 0) + 1);
        distanceByTruckType.put(truck.getTruckType(),
            distanceByTruckType.getOrDefault(truck.getTruckType(), 0.0) + distance);
        
        // Vehicle size tracking
        tripsByVehicleSize.put(trip.getVehicleSize(),
            tripsByVehicleSize.getOrDefault(trip.getVehicleSize(), 0) + 1);
        distanceByVehicleSize.put(trip.getVehicleSize(),
            distanceByVehicleSize.getOrDefault(trip.getVehicleSize(), 0.0) + distance);
        
        // O-D flow tracking
        String odKey = originZone + "-" + destZone;
        odFlows.put(odKey, odFlows.getOrDefault(odKey, 0) + 1);
        
        // O-D distance (running average)
        double currentAvg = odDistances.getOrDefault(odKey, 0.0);
        int count = odFlows.get(odKey);
        double newAvg = (currentAvg * (count - 1) + distance) / count;
        odDistances.put(odKey, newAvg);
        
        // Per-truck metrics
        TruckMetrics tm = truckMetrics.get(truck.getTruckId());
        if (tm != null) {
            tm.totalTrips++;
            tm.totalDistanceKm += distance;
            
            if (trip.isCargoLoaded()) {
                tm.deliveryTrips++;
                tm.totalCargoTons += trip.getCargoWeightTons();
            } else {
                tm.emptyTrips++;
            }
            
            if (!tm.destinationsVisited.contains(destZone)) {
                tm.destinationsVisited.add(destZone);
            }
        }
    }
    
    /**
     * Calculate final metrics after all trips have been recorded.
     */
    public void calculateFinalMetrics() {
        // Calculate averages
        if (deliveryTrips > 0) {
            avgCargoPerDelivery = totalCargoTons / deliveryTrips;
        }
        
        // Count active trucks (trucks with at least 1 trip)
        activeTrucks = (int) truckMetrics.values().stream()
            .filter(tm -> tm.totalTrips > 0)
            .count();
        
        // Calculate utilization rates for each truck
        for (TruckMetrics tm : truckMetrics.values()) {
            if (tm.deliveryTrips > 0) {
                // Utilization = (cargo tons) / (capacity * deliveries)
                double capacity = getCapacityForSize(tm.vehicleSize);
                tm.utilizationRate = tm.totalCargoTons / (capacity * tm.deliveryTrips);
            }
        }

        // NEW: Phase 5 - Calculate average distance per trip by truck type
        for (TruckType type : TruckType.values()) {
            int trips = tripsByTruckType.getOrDefault(type, 0);
            double distance = distanceByTruckType.getOrDefault(type, 0.0);
            double avgDistance = trips > 0 ? distance / trips : 0.0;
            avgDistancePerTripByType.put(type, avgDistance);
        }
    }
    
    /**
     * Get destination selection patterns by origin zone.
     */
    public Map<String, DestinationPattern> getDestinationPatterns() {
        Map<String, DestinationPattern> patterns = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : odFlows.entrySet()) {
            String[] parts = entry.getKey().split("-");
            if (parts.length != 2) continue;
            
            String origin = parts[0];
            String dest = parts[1];
            
            DestinationPattern pattern = patterns.computeIfAbsent(
                origin, k -> new DestinationPattern(origin));
            
            double distance = odDistances.getOrDefault(entry.getKey(), 0.0);
            pattern.addDestination(dest, distance);
        }
        
        return patterns;
    }
    
    /**
     * Get time period from seconds (0=Morning, 1=Afternoon, 2=Night).
     */
    private int getTimePeriod(long seconds) {
        long hour = (seconds / 3600) % 24;
        
        if (hour >= 6 && hour < 12) {
            return 0;  // Morning
        } else if (hour >= 12 && hour < 18) {
            return 1;  // Afternoon
        } else {
            return 2;  // Night
        }
    }
    
    /**
     * Get vehicle capacity by size.
     */
    private double getCapacityForSize(String size) {
        switch (size.toLowerCase()) {
            case "heavy": return 10.0;
            case "medium": return 4.0;
            case "small": return 2.0;
            case "light": return 1.0;
            default: return 4.0;
        }
    }
    
    /**
     * Print comprehensive metrics report.
     */
    public void printReport() {
        String separator = repeatString("=", 80);
        
        System.out.println("\n" + separator);
        System.out.println("COMPREHENSIVE METRICS REPORT");
        System.out.println(separator);
        
        // 1. VOLUME OF TRIPS
        System.out.println("\n[1] TRIP VOLUME METRICS");
        System.out.println("  Total trips: " + totalTrips);
        System.out.println("  - Delivery trips: " + deliveryTrips + 
            " (" + String.format("%.1f%%", 100.0 * deliveryTrips / totalTrips) + ")");
        System.out.println("  - Empty trips: " + emptyTrips + 
            " (" + String.format("%.1f%%", 100.0 * emptyTrips / totalTrips) + ")");
        System.out.println("  - Intra-metropolitan: " + intrametropolitanTrips);
        System.out.println("  - Inter-metropolitan: " + intermetropolitanTrips);
        
        System.out.println("\n  Trips by truck type:");
        for (Map.Entry<TruckType, Integer> entry : tripsByTruckType.entrySet()) {
            System.out.println("    " + entry.getKey() + ": " + entry.getValue() +
                " (" + String.format("%.1f%%", 100.0 * entry.getValue() / totalTrips) + ")");
        }
        
        System.out.println("\n  Trips by vehicle size:");
        for (Map.Entry<String, Integer> entry : tripsByVehicleSize.entrySet()) {
            System.out.println("    " + entry.getKey() + ": " + entry.getValue() +
                " (" + String.format("%.1f%%", 100.0 * entry.getValue() / totalTrips) + ")");
        }
        
        System.out.println("\n  Trips by time period:");
        System.out.println("    Morning (06:00-12:00): " + 
            tripsByTimePeriod.getOrDefault(0, 0));
        System.out.println("    Afternoon (12:00-18:00): " + 
            tripsByTimePeriod.getOrDefault(1, 0));
        System.out.println("    Night (18:00-06:00): " + 
            tripsByTimePeriod.getOrDefault(2, 0));
        
        // 2. TRUCK DISTANCES
        System.out.println("\n[2] DISTANCE METRICS");
        System.out.println("  Total distance: " + String.format("%.2f", totalDistanceKm) + " km");
        System.out.println("  - Loaded distance: " + String.format("%.2f", loadedDistanceKm) + 
            " km (" + String.format("%.1f%%", 100.0 * loadedDistanceKm / totalDistanceKm) + ")");
        System.out.println("  - Empty distance: " + String.format("%.2f", emptyDistanceKm) + 
            " km (" + String.format("%.1f%%", 100.0 * emptyDistanceKm / totalDistanceKm) + ")");
        System.out.println("  - Intra-metro distance: " + String.format("%.2f", intrametroDistanceKm) + " km");
        System.out.println("  - Inter-metro distance: " + String.format("%.2f", intermetroDistanceKm) + " km");
        
        System.out.println("\n  Average distance per trip: " + 
            String.format("%.2f", totalDistanceKm / totalTrips) + " km");
        System.out.println("  Average distance per delivery: " + 
            String.format("%.2f", loadedDistanceKm / deliveryTrips) + " km");
        
        System.out.println("\n  Distance by truck type:");
        for (TruckType type : TruckType.values()) {
            double totalDist = distanceByTruckType.getOrDefault(type, 0.0);
            int trips = tripsByTruckType.getOrDefault(type, 0);
            double avgDist = avgDistancePerTripByType.getOrDefault(type, 0.0);

            System.out.println("    " + type + ": " +
                String.format("%.2f", totalDist) + " km total, " +
                trips + " trips, avg " +
                String.format("%.2f", avgDist) + " km/trip");
        }
        
        // 3. NUMBER OF TRUCKS
        System.out.println("\n[3] TRUCK METRICS");
        System.out.println("  Total trucks: " + totalTrucks);
        System.out.println("  Active trucks (≥1 trip): " + activeTrucks +
            " (" + String.format("%.1f%%", 100.0 * activeTrucks / totalTrucks) + ")");
        
        // 4. CARGO METRICS
        System.out.println("\n[4] CARGO METRICS");
        System.out.println("  Total cargo: " + String.format("%.2f", totalCargoTons) + " tons");
        System.out.println("  Average cargo per delivery: " + 
            String.format("%.2f", avgCargoPerDelivery) + " tons");
        
        System.out.println("\n  Cargo by commodity (top 5):");
        cargoByComommodity.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .limit(5)
            .forEach(entry -> {
                System.out.println("    " + entry.getKey() + ": " + 
                    String.format("%.2f", entry.getValue()) + " tons (" +
                    String.format("%.1f%%", 100.0 * entry.getValue() / totalCargoTons) + ")");
            });
        
        // 5. DESTINATION SELECTION PATTERNS
        System.out.println("\n[5] DESTINATION SELECTION PATTERNS");
        System.out.println("  Top 10 O-D pairs:");
        odFlows.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(10)
            .forEach(entry -> {
                double avgDist = odDistances.getOrDefault(entry.getKey(), 0.0);
                System.out.println("    " + entry.getKey() + ": " + entry.getValue() + 
                    " trips (avg: " + String.format("%.1f", avgDist) + " km)");
            });
        
        // Zone popularity
        System.out.println("\n  Most popular destination zones:");
        tripsByZone.entrySet().stream()
            .filter(e -> !e.getKey().equals("UNKNOWN"))
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(5)
            .forEach(entry -> {
                double avgDist = distanceByZone.getOrDefault(entry.getKey(), 0.0) / entry.getValue();
                System.out.println("    " + entry.getKey() + ": " + entry.getValue() + 
                    " trips (avg distance: " + String.format("%.1f", avgDist) + " km)");
            });
        
        System.out.println("\n" + separator);
    }
    
    /**
     * Export metrics to CSV file.
     */
    public void exportToCSV(String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        
        // Export summary metrics
        try (PrintWriter pw = new PrintWriter(new File(outputDir, "metrics_summary.csv"))) {
            pw.println("metric,value");
            pw.println("total_trips," + totalTrips);
            pw.println("delivery_trips," + deliveryTrips);
            pw.println("empty_trips," + emptyTrips);
            pw.println("intrametropolitan_trips," + intrametropolitanTrips);
            pw.println("intermetropolitan_trips," + intermetropolitanTrips);
            pw.println("total_distance_km," + String.format("%.2f", totalDistanceKm));
            pw.println("loaded_distance_km," + String.format("%.2f", loadedDistanceKm));
            pw.println("empty_distance_km," + String.format("%.2f", emptyDistanceKm));
            pw.println("total_trucks," + totalTrucks);
            pw.println("active_trucks," + activeTrucks);
            pw.println("total_cargo_tons," + String.format("%.2f", totalCargoTons));
            pw.println("avg_cargo_per_delivery," + String.format("%.2f", avgCargoPerDelivery));
        }
        
        // Export O-D flows
        try (PrintWriter pw = new PrintWriter(new File(outputDir, "metrics_od_flows.csv"))) {
            pw.println("origin,destination,trip_count,avg_distance_km");
            
            for (Map.Entry<String, Integer> entry : odFlows.entrySet()) {
                String[] parts = entry.getKey().split("-");
                if (parts.length == 2) {
                    double avgDist = odDistances.getOrDefault(entry.getKey(), 0.0);
                    pw.println(parts[0] + "," + parts[1] + "," + 
                        entry.getValue() + "," + String.format("%.2f", avgDist));
                }
            }
        }
        
        // Export per-truck metrics
        try (PrintWriter pw = new PrintWriter(new File(outputDir, "metrics_per_truck.csv"))) {
            pw.println("truck_id,truck_type,vehicle_size,total_trips,delivery_trips,empty_trips," +
                      "total_distance_km,total_cargo_tons,utilization_rate,unique_destinations");
            
            for (TruckMetrics tm : truckMetrics.values()) {
                if (tm.totalTrips > 0) {
                    pw.println(tm.truckId + "," + tm.truckType + "," + tm.vehicleSize + "," +
                              tm.totalTrips + "," + tm.deliveryTrips + "," + tm.emptyTrips + "," +
                              String.format("%.2f", tm.totalDistanceKm) + "," +
                              String.format("%.2f", tm.totalCargoTons) + "," +
                              String.format("%.3f", tm.utilizationRate) + "," +
                              tm.destinationsVisited.size());
                }
            }
        }

        // NEW: Phase 5 - Export per-truck-type metrics
        try (PrintWriter pw = new PrintWriter(new File(outputDir, "metrics_by_truck_type.csv"))) {
            pw.println("truck_type,total_trips,total_distance_km,avg_distance_per_trip_km," +
                      "total_cargo_tons,avg_cargo_per_trip_tons");

            for (TruckType type : TruckType.values()) {
                int trips = tripsByTruckType.getOrDefault(type, 0);
                double distance = distanceByTruckType.getOrDefault(type, 0.0);
                double avgDist = avgDistancePerTripByType.getOrDefault(type, 0.0);

                // Calculate cargo for this truck type
                double totalCargo = 0.0;
                for (TruckMetrics tm : truckMetrics.values()) {
                    if (tm.truckType == type) {
                        totalCargo += tm.totalCargoTons;
                    }
                }
                double avgCargo = trips > 0 ? totalCargo / trips : 0.0;

                pw.println(type + "," + trips + "," +
                          String.format("%.2f", distance) + "," +
                          String.format("%.2f", avgDist) + "," +
                          String.format("%.2f", totalCargo) + "," +
                          String.format("%.2f", avgCargo));
            }
        }

        // Export MFS validation results (same structure as baseline CSV + simulated_value)
        if (validationEnabled && mfsBaseline != null && !mfsBaseline.isEmpty()) {
            try (PrintWriter pw = new PrintWriter(
                    new File(outputDir, "metrics_mfs_validation.csv"))) {
                // Header: baseline columns + simulated_value + percent_diff + status
                pw.println("metric_name,category,simulated_value,survey_value,percent_diff,status,unit,source_file,notes");

                // Iterate through baseline metrics in same order
                for (Map.Entry<String, MFSBaselineMetric> entry : mfsBaseline.entrySet()) {
                    MFSBaselineMetric baseline = entry.getValue();

                    // Get simulated value from validation results
                    ValidationResult result = validationResults.get(baseline.metricName);
                    double simValue = result != null ? result.simulatedValue : 0.0;
                    double percentDiff = result != null ? result.percentDifference : 0.0;
                    String status = result != null && result.withinTolerance ? "PASS" : "FAIL";

                    pw.println(baseline.metricName + "," +
                              baseline.category + "," +
                              String.format("%.2f", simValue) + "," +
                              String.format("%.2f", baseline.surveyValue) + "," +
                              String.format("%.2f", percentDiff) + "," +
                              status + "," +
                              baseline.unit + "," +
                              baseline.sourceFile + "," +
                              baseline.notes);
                }
            }
        }

        System.out.println("[✓] Metrics exported to: " + outputDir);
    }

    /**
     * Validate simulation results against MFS baseline CSV.
     */
    public void validateAgainstMFS(double tolerancePct) {
        if (mfsBaseline == null || mfsBaseline.isEmpty()) {
            System.out.println("[MFS] No baseline metrics loaded");
            return;
        }

        System.out.println("\n[MFS VALIDATION] Comparing simulation vs survey baseline");
        System.out.println("Tolerance: " + String.format("%.1f%%", tolerancePct));

        // Validate truck counts
        validateMetric("Total Trucks", totalTrucks, tolerancePct);
        validateMetric("Trucks Heavy (10t+)", getTruckCountBySize("heavy"), tolerancePct);
        validateMetric("Trucks Medium (4-10t)", getTruckCountBySize("medium"), tolerancePct);
        validateMetric("Trucks Small (2-4t)", getTruckCountBySize("small"), tolerancePct);
        validateMetric("Trucks Light (<2t)", getTruckCountBySize("light"), tolerancePct);

        // Validate percentages
        double totalTrucksD = (double) totalTrucks;
        if (totalTrucksD > 0) {
            validateMetric("Heavy Truck Percentage",
                100.0 * getTruckCountBySize("heavy") / totalTrucksD, tolerancePct);
            validateMetric("Medium Truck Percentage",
                100.0 * getTruckCountBySize("medium") / totalTrucksD, tolerancePct);
            validateMetric("Small Truck Percentage",
                100.0 * getTruckCountBySize("small") / totalTrucksD, tolerancePct);
            validateMetric("Light Truck Percentage",
                100.0 * getTruckCountBySize("light") / totalTrucksD, tolerancePct);
        }

        // Validate trip metrics
        validateMetric("Total Logistics Volume", totalCargoTons, tolerancePct);

        // Validate vehicle volume (cargo by vehicle size)
        validateMetric("Heavy Vehicle Volume", getCargoBySize("heavy"), tolerancePct);
        validateMetric("Medium Vehicle Volume", getCargoBySize("medium"), tolerancePct);
        validateMetric("Small Vehicle Volume", getCargoBySize("small"), tolerancePct);
        validateMetric("Light Vehicle Volume", getCargoBySize("light"), tolerancePct);

        // Validate distances
        if (totalTrips > 0) {
            double avgDistance = totalDistanceKm / totalTrips;
            validateMetric("Avg Distance per Trip", avgDistance, tolerancePct);
        }

        // NEW: Phase 5 - Validate per-truck-type distances
        for (TruckType type : TruckType.values()) {
            double avgDist = avgDistancePerTripByType.getOrDefault(type, 0.0);
            String metricName = "Avg Distance per Trip (" + type + ")";
            validateMetric(metricName, avgDist, tolerancePct);
        }

        // Validate efficiency
        if (totalTrips > 0) {
            double emptyRatio = 100.0 * emptyTrips / totalTrips;
            validateMetric("Empty Trip Ratio", emptyRatio, tolerancePct);
        }

        // Validate destination patterns
        validateIntraZoneRatio(tolerancePct);

        // REMOVED: Avg trips per truck (Phase 5 - per user requirements)
        // This metric was removed from validation as requested

        // Validate cargo per delivery
        validateMetric("Avg Cargo per Delivery", avgCargoPerDelivery, tolerancePct);

        // Print summary
        printValidationSummary();
    }

    private void validateMetric(String metricName, double simulatedValue, double tolerance) {
        MFSBaselineMetric baseline = mfsBaseline.get(metricName);
        if (baseline == null) return;

        validationResults.put(metricName,
            new ValidationResult(metricName, simulatedValue, baseline.surveyValue, tolerance));
    }

    private int getTruckCountBySize(String size) {
        Integer count = tripsByVehicleSize.get(size);
        return count != null ? count : 0;
    }

    private double getCargoBySize(String size) {
        // Calculate cargo for this vehicle size from truck metrics
        double totalCargo = 0.0;
        for (TruckMetrics tm : truckMetrics.values()) {
            if (tm.vehicleSize.equals(size)) {
                totalCargo += tm.totalCargoTons;
            }
        }
        return totalCargo;
    }

    private void validateIntraZoneRatio(double tolerance) {
        int intraZoneTrips = 0;
        for (String odKey : odFlows.keySet()) {
            String[] parts = odKey.split("-");
            if (parts.length == 2 && parts[0].equals(parts[1])) {
                intraZoneTrips += odFlows.get(odKey);
            }
        }

        if (totalTrips > 0) {
            double intraZonePct = 100.0 * intraZoneTrips / totalTrips;
            validateMetric("Intra-zone Trip Ratio", intraZonePct, tolerance);
        }
    }

    /**
     * Get category for metric name (from baseline if available).
     */
    private String getCategoryForMetric(String metricName) {
        if (mfsBaseline != null && mfsBaseline.containsKey(metricName)) {
            return mfsBaseline.get(metricName).category;
        }

        // Fallback categories
        if (metricName.contains("Truck")) return "truck_count";
        if (metricName.contains("Distance")) return "distance";
        if (metricName.contains("Volume")) return "trip_volume";
        if (metricName.contains("Cargo")) return "cargo";
        if (metricName.contains("Empty")) return "efficiency";
        if (metricName.contains("Percentage")) return "fleet_distribution";
        if (metricName.contains("Ratio")) return "destination";

        return "other";
    }

    /**
     * Get unit for metric name (from baseline if available).
     */
    private String getUnitForMetric(String metricName) {
        if (mfsBaseline != null && mfsBaseline.containsKey(metricName)) {
            return mfsBaseline.get(metricName).unit;
        }

        // Fallback units
        if (metricName.contains("Percentage") || metricName.contains("Ratio")) return "%";
        if (metricName.contains("Distance")) return "km";
        if (metricName.contains("Volume") || metricName.contains("Cargo")) return "tons/day";
        if (metricName.contains("Trucks")) return "vehicles/day";

        return "count";
    }

    private void printValidationSummary() {
        if (validationResults.isEmpty()) {
            System.out.println("[MFS] No validation results available");
            return;
        }

        int passed = 0;
        int total = validationResults.size();

        System.out.println("\n[VALIDATION RESULTS]");
        for (ValidationResult result : validationResults.values()) {
            if (result.withinTolerance) {
                passed++;
                System.out.println("  ✓ " + result.metricName + ": " +
                    String.format("%.1f%%", result.percentDifference) + " diff");
            } else {
                System.out.println("  ✗ " + result.metricName + ": " +
                    String.format("%.1f%%", result.percentDifference) + " diff " +
                    "(simulated: " + String.format("%.0f", result.simulatedValue) +
                    ", survey: " + String.format("%.0f", result.surveyValue) + ")");
            }
        }

        double passRate = total > 0 ? 100.0 * passed / total : 0;
        System.out.println("\nPass rate: " + String.format("%.1f%%", passRate) +
            " (" + passed + "/" + total + " metrics within tolerance)");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Helper to repeat a string (Java 8 compatible).
     */
    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // Getters for all metrics
    public int getTotalTrips() { return totalTrips; }
    public int getDeliveryTrips() { return deliveryTrips; }
    public int getEmptyTrips() { return emptyTrips; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public double getLoadedDistanceKm() { return loadedDistanceKm; }
    public double getEmptyDistanceKm() { return emptyDistanceKm; }
    public int getTotalTrucks() { return totalTrucks; }
    public int getActiveTrucks() { return activeTrucks; }
    public double getTotalCargoTons() { return totalCargoTons; }
    public Map<String, Integer> getOdFlows() { return Collections.unmodifiableMap(odFlows); }
    public Map<Integer, TruckMetrics> getTruckMetrics() { return Collections.unmodifiableMap(truckMetrics); }
}
