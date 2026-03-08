package truck.sim;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports truck simulation data to CSV files in PFlow-compatible format
 *
 * OUTPUT STRUCTURE:
 * - trucks.csv: Truck agent information and statistics
 * - trips.csv: Individual trip records (original format)
 * - trips_pseudo_pflow.csv: Pseudo PFLOW format (transport_mode=9)
 * - trips_with_zones.csv: Trips with zone annotations
 * - summary.csv: Aggregate validation metrics
 *
 * PSEUDO PFLOW FORMAT:
 * - Transport mode: 9 (truck)
 * - Purpose: 0 (empty trip) or 1 (delivery)
 * - Includes cargo weight, goods type, vehicle size
 *
 * VALIDATION METRICS:
 * - Total vehicles by size class
 * - Total tonnage moved
 * - Fleet mix distribution
 * - Empty running ratio
 * - Average cargo weight
 *
 * @author Truck ABM Framework
 * @version 1.0
 */
public class TruckDataExporter {

    private String outputDirectory;
    private String runDirectory;
    private TruckConfig config;

    // Store zones for zone lookup during export
    private List<DeliveryZone> zones;

    /**
     * Constructor
     * @param outputDir Base output directory
     */
    public TruckDataExporter(String outputDir) {
        this.config = TruckConfig.getInstance();
        this.outputDirectory = outputDir;

        // Create timestamped run directory
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        this.runDirectory = outputDir + "/run_" + timestamp;

        // Create directories if they don't exist
        try {
            Files.createDirectories(Paths.get(runDirectory));
            System.out.println("Created output directory: " + runDirectory);
        } catch (IOException e) {
            System.err.println("Error creating output directory: " + e.getMessage());
        }
    }

    /**
     * Set delivery zones for zone lookup
     * @param deliveryZones List of delivery zones
     */
    public void setDeliveryZones(List<DeliveryZone> deliveryZones) {
        this.zones = deliveryZones;
    }

    /**
     * Export all simulation data
     * @param trucks List of truck agents
     * @param trips List of trips (including empty trips)
     */
    public void exportAll(List<TruckAgent> trucks, List<TruckTrip> trips) {
        exportTrucks(trucks);
        exportTrips(trips);
        exportTripsPseudoPFlow(trips);
        exportTripsWithZones(trucks, trips);
        exportSummary(trucks, trips);
    }

    /**
     * Export truck agent information and statistics
     */
    private void exportTrucks(List<TruckAgent> trucks) {
        String filename = runDirectory + "/trucks.csv";
        int truckCount = 0;

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            // Header
            writer.println("truck_id,truck_type,vehicle_size,capacity_tons,primary_goods_type," +
                "home_lon,home_lat,home_poi_id,familiar_radius_km,familiar_area_zone," +
                "shift_start,shift_end,shift_duration_hours," +
                "total_trips,delivery_trips,empty_trips," +
                "total_distance_km,total_cargo_tons,avg_cargo_tons," +
                "empty_running_ratio,status");

            // Data rows
            for (TruckAgent truck : trucks) {
                writer.printf("%d,%s,%s,%.2f,%s,%.6f,%.6f,%s,%.2f,%s,%s,%s,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.3f,%s%n",
                    truck.getTruckId(),
                    truck.getTruckType().toString(),
                    truck.getVehicleSize(),
                    truck.getCapacityTons(),
                    truck.getPrimaryGoodsType(),
                    truck.getHomeLongitude(),
                    truck.getHomeLatitude(),
                    truck.getHomePOIId() != null ? truck.getHomePOIId() : "",
                    truck.getFamiliarAreaRadiusKm(),
                    truck.getFamiliarAreaZoneId() != null ? truck.getFamiliarAreaZoneId() : "N/A",
                    formatTime(truck.getShiftStartTime()),
                    formatTime(truck.getShiftEndTime()),
                    truck.getShiftDurationHours(),
                    truck.getTotalTripCount(),
                    truck.getDeliveryCount(),
                    truck.getEmptyTripCount(),
                    truck.getTotalDistanceKm(),
                    truck.getTotalCargoWeightTons(),
                    truck.getAvgCargoWeight(),
                    truck.getEmptyRunningRatio(),
                    truck.getCurrentStatus().toString()
                );
                truckCount++;
            }

            System.out.println("Exported trucks.csv: " + truckCount + " trucks");

        } catch (IOException e) {
            System.err.println("Error exporting trucks: " + e.getMessage());
        }
    }

    /**
     * Export trip information (original format)
     */
    private void exportTrips(List<TruckTrip> trips) {
        String filename = runDirectory + "/trips.csv";
        int tripCount = 0;

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            // Header
            writer.println("trip_id,truck_id,origin_lon,origin_lat,dest_lon,dest_lat," +
                "origin_zone,dest_zone,origin_facility_id,dest_facility_id," +
                "request_time,loading_start,departure_time,arrival_time,unloading_end," +
                "distance_km,cargo_loaded,goods_type,vehicle_size," +
                "cargo_weight_tons,capacity_tons," +
                "loading_time_min,unloading_time_min,status");

            // Data rows
            for (TruckTrip trip : trips) {
                writer.printf("%d,%d,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s%n",
                    trip.getTripId(),
                    trip.getTruckId(),
                    trip.getOriginLongitude(),
                    trip.getOriginLatitude(),
                    trip.getDestLongitude(),
                    trip.getDestLatitude(),
                    trip.getOriginZoneId() != null ? trip.getOriginZoneId() : "UNKNOWN",
                    trip.getDestZoneId() != null ? trip.getDestZoneId() : "UNKNOWN",
                    trip.getOriginFacilityId() != null ? trip.getOriginFacilityId() : "",
                    trip.getDestFacilityId() != null ? trip.getDestFacilityId() : "",
                    formatTime(trip.getRequestTime()),
                    formatTime(trip.getLoadingStartTime()),
                    formatTime(trip.getDepartureTime()),
                    formatTime(trip.getArrivalTime()),
                    formatTime(trip.getUnloadingEndTime()),
                    trip.getDistanceKm(),
                    trip.isCargoLoaded() ? "true" : "false",
                    trip.getGoodsType(),
                    trip.getVehicleSize(),
                    trip.getCargoWeightTons(),
                    trip.getCapacityTons(),
                    trip.getLoadingTimeMinutes(),
                    trip.getUnloadingTimeMinutes(),
                    trip.getStatus().toString()
                );
                tripCount++;
            }

            System.out.println("Exported trips.csv: " + tripCount + " trips");

        } catch (IOException e) {
            System.err.println("Error exporting trips: " + e.getMessage());
        }
    }

    /**
     * Export trip information in Pseudo PFLOW format
     *
     * PSEUDO PFLOW STANDARD FORMAT:
     * Columns: ["id", "starttime", "start_lon", "start_lat", "end_lon", "end_lat",
     *           "transport_mode", "purpose", "occupation",
     *           "cargo_loaded", "truck_id", "distance_km", "cargo_weight_tons",
     *           "goods_type", "vehicle_size", "capacity_tons"]
     *
     * TRANSPORT MODE CODES:
     * - 9 = Truck (freight transport)
     *
     * PURPOSE CODES:
     * - 0 = Empty trip (repositioning)
     * - 1 = Delivery trip (with cargo)
     *
     * OCCUPATION CODES:
     * - 0 = Not applicable (trucks don't have fixed occupation)
     */
    private void exportTripsPseudoPFlow(List<TruckTrip> trips) {
        String filename = runDirectory + "/trips_pseudo_pflow.csv";
        int tripCount = 0;
        int deliveryTrips = 0;
        int emptyTrips = 0;

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            // Header - Pseudo PFLOW standard columns + truck-specific extensions
            writer.println("id,sim_day,starttime,start_lon,start_lat,end_lon,end_lat," +
                "transport_mode,purpose,occupation," +
                "cargo_loaded,truck_id,distance_km,cargo_weight_tons," +
                "goods_type,vehicle_size,capacity_tons,status,starttime_h");

            // Data rows
            for (TruckTrip trip : trips) {
                // Standard Pseudo PFLOW fields
                int transportMode = 9;  // 9 = Truck
                int purpose = trip.isCargoLoaded() ? 1 : 0;  // 1=delivery, 0=empty
                int occupation = 0;  // Not applicable for trucks

                // Calculate sim_day and normalized starttime (0-86400)
                long rawStartTime = trip.getDepartureTime();
                int simDay = (int)(rawStartTime / 86400);  // Integer division gives day number
                int normalizedStartTime = (int)(rawStartTime % 86400);  // Modulo gives time within day

                writer.printf("%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%s,%d,%.2f,%.2f,%s,%s,%.2f,%s,%s%n",
                    // Standard Pseudo PFLOW columns
                    trip.getTripId(),
                    simDay,  // Simulation day (0, 1, 2, ...)
                    normalizedStartTime,  // Seconds from midnight (0-86400)
                    trip.getOriginLongitude(),
                    trip.getOriginLatitude(),
                    trip.getDestLongitude(),
                    trip.getDestLatitude(),
                    transportMode,
                    purpose,
                    occupation,
                    // Truck-specific extensions
                    trip.isCargoLoaded() ? "true" : "false",
                    trip.getTruckId(),
                    trip.getDistanceKm(),
                    trip.getCargoWeightTons(),
                    trip.getGoodsType(),
                    trip.getVehicleSize(),
                    trip.getCapacityTons(),
                    trip.getStatus().toString(),
                    formatTime(normalizedStartTime)  // Human-readable time (normalized)
                );
                tripCount++;
                if (trip.isCargoLoaded()) {
                    deliveryTrips++;
                } else {
                    emptyTrips++;
                }
            }

            System.out.println("Exported trips_pseudo_pflow.csv: " + tripCount + " trips (" +
                deliveryTrips + " delivery, " + emptyTrips + " empty)");

        } catch (IOException e) {
            System.err.println("Error exporting Pseudo PFLOW trips: " + e.getMessage());
        }
    }

    /**
     * Export trips with agent type and zone information
     */
    private void exportTripsWithZones(List<TruckAgent> trucks, List<TruckTrip> trips) {
        String filename = runDirectory + "/trips_with_zones.csv";
        int tripCount = 0;

        // Build a map of truck ID to truck agent for quick lookup
        Map<Integer, TruckAgent> truckMap = new HashMap<>();
        for (TruckAgent truck : trucks) {
            truckMap.put(truck.getTruckId(), truck);
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            // Header
            writer.println("trip_id,truck_id,truck_type,vehicle_size,capacity_tons," +
                "origin_lon,origin_lat,dest_lon,dest_lat," +
                "origin_zone,dest_zone," +
                "request_time,departure_time,arrival_time," +
                "distance_km,cargo_loaded,cargo_weight_tons,goods_type,status");

            // Data rows
            for (TruckTrip trip : trips) {
                TruckAgent truck = truckMap.get(trip.getTruckId());
                String truckType = truck != null ? truck.getTruckType().toString() : "UNKNOWN";
                String vehicleSize = truck != null ? truck.getVehicleSize() : "UNKNOWN";
                double capacityTons = truck != null ? truck.getCapacityTons() : 0.0;

                writer.printf("%d,%d,%s,%s,%.2f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%s,%.2f,%s,%.2f,%s,%s%n",
                    trip.getTripId(),
                    trip.getTruckId(),
                    truckType,
                    vehicleSize,
                    capacityTons,
                    trip.getOriginLongitude(),
                    trip.getOriginLatitude(),
                    trip.getDestLongitude(),
                    trip.getDestLatitude(),
                    trip.getOriginZoneId() != null ? trip.getOriginZoneId() : "UNKNOWN",
                    trip.getDestZoneId() != null ? trip.getDestZoneId() : "UNKNOWN",
                    formatTime(trip.getRequestTime()),
                    formatTime(trip.getDepartureTime()),
                    formatTime(trip.getArrivalTime()),
                    trip.getDistanceKm(),
                    trip.isCargoLoaded() ? "true" : "false",
                    trip.getCargoWeightTons(),
                    trip.getGoodsType(),
                    trip.getStatus().toString()
                );
                tripCount++;
            }

            System.out.println("Exported trips_with_zones.csv: " + tripCount + " trips");

        } catch (IOException e) {
            System.err.println("Error exporting trips with zones: " + e.getMessage());
        }
    }

    /**
     * Export summary statistics with validation metrics
     */
    private void exportSummary(List<TruckAgent> trucks, List<TruckTrip> trips) {
        String filename = runDirectory + "/summary.csv";

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            // Calculate statistics
            int totalTrucks = trucks.size();
            int totalTrips = trips.size();

            // Fleet mix by vehicle size
            long heavyCount = trucks.stream().filter(t -> t.getVehicleSize().equals("heavy")).count();
            long mediumCount = trucks.stream().filter(t -> t.getVehicleSize().equals("medium")).count();
            long smallCount = trucks.stream().filter(t -> t.getVehicleSize().equals("small")).count();
            long lightCount = trucks.stream().filter(t -> t.getVehicleSize().equals("light")).count();

            // Fleet mix by truck type
            long deliveryTypeCount = trucks.stream().filter(t -> t.getTruckType() == TruckType.DELIVERY).count();
            long longHaulTypeCount = trucks.stream().filter(t -> t.getTruckType() == TruckType.LONG_HAUL).count();
            long urbanTypeCount = trucks.stream().filter(t -> t.getTruckType() == TruckType.MIXED_OPERATION).count();

            // Trip statistics
            long deliveryTrips = trips.stream().filter(TruckTrip::isCargoLoaded).count();
            long emptyTrips = trips.stream().filter(t -> !t.isCargoLoaded()).count();

            double totalDistance = trips.stream().mapToDouble(TruckTrip::getDistanceKm).sum();
            double deliveryDistance = trips.stream()
                .filter(TruckTrip::isCargoLoaded)
                .mapToDouble(TruckTrip::getDistanceKm).sum();
            double emptyDistance = trips.stream()
                .filter(t -> !t.isCargoLoaded())
                .mapToDouble(TruckTrip::getDistanceKm).sum();

            double totalCargo = trips.stream()
                .filter(TruckTrip::isCargoLoaded)
                .mapToDouble(TruckTrip::getCargoWeightTons).sum();

            // Calculated metrics
            double avgTripsPerTruck = (double) totalTrips / totalTrucks;
            double avgDeliveryTripsPerTruck = (double) deliveryTrips / totalTrucks;
            double avgDistancePerTruck = totalDistance / totalTrucks;
            double avgTripDistance = totalDistance / totalTrips;
            double avgCargoWeight = deliveryTrips > 0 ? totalCargo / deliveryTrips : 0;
            double emptyRunningRatio = totalDistance > 0 ? emptyDistance / totalDistance : 0;
            double deliveryTripPercentage = 100.0 * deliveryTrips / totalTrips;
            double emptyTripPercentage = 100.0 * emptyTrips / totalTrips;

            // Write summary
            writer.println("metric,value");
            writer.println("# FLEET COMPOSITION");
            writer.println("total_trucks," + totalTrucks);
            writer.println("fleet_heavy_count," + heavyCount);
            writer.println("fleet_medium_count," + mediumCount);
            writer.println("fleet_small_count," + smallCount);
            writer.println("fleet_light_count," + lightCount);
            writer.println("fleet_heavy_percentage," + String.format("%.2f", 100.0 * heavyCount / totalTrucks));
            writer.println("fleet_medium_percentage," + String.format("%.2f", 100.0 * mediumCount / totalTrucks));
            writer.println("fleet_small_percentage," + String.format("%.2f", 100.0 * smallCount / totalTrucks));
            writer.println("fleet_light_percentage," + String.format("%.2f", 100.0 * lightCount / totalTrucks));
            writer.println("# TRUCK TYPE DISTRIBUTION");
            writer.println("type_delivery_count," + deliveryTypeCount);
            writer.println("type_long_haul_count," + longHaulTypeCount);
            writer.println("type_urban_logistics_count," + urbanTypeCount);
            writer.println("type_delivery_percentage," + String.format("%.2f", 100.0 * deliveryTypeCount / totalTrucks));
            writer.println("type_long_haul_percentage," + String.format("%.2f", 100.0 * longHaulTypeCount / totalTrucks));
            writer.println("type_urban_logistics_percentage," + String.format("%.2f", 100.0 * urbanTypeCount / totalTrucks));
            writer.println("# TRIP STATISTICS");
            writer.println("total_trips," + totalTrips);
            writer.println("delivery_trips," + deliveryTrips);
            writer.println("empty_trips," + emptyTrips);
            writer.println("delivery_trip_percentage," + String.format("%.2f", deliveryTripPercentage));
            writer.println("empty_trip_percentage," + String.format("%.2f", emptyTripPercentage));
            writer.println("avg_trips_per_truck," + String.format("%.2f", avgTripsPerTruck));
            writer.println("avg_delivery_trips_per_truck," + String.format("%.2f", avgDeliveryTripsPerTruck));
            writer.println("# DISTANCE METRICS");
            writer.println("total_distance_km," + String.format("%.2f", totalDistance));
            writer.println("delivery_distance_km," + String.format("%.2f", deliveryDistance));
            writer.println("empty_distance_km," + String.format("%.2f", emptyDistance));
            writer.println("empty_running_ratio," + String.format("%.3f", emptyRunningRatio));
            writer.println("empty_running_percentage," + String.format("%.2f", 100.0 * emptyRunningRatio));
            writer.println("avg_distance_per_truck_km," + String.format("%.2f", avgDistancePerTruck));
            writer.println("avg_trip_distance_km," + String.format("%.2f", avgTripDistance));
            writer.println("# CARGO METRICS");
            writer.println("total_cargo_tons," + String.format("%.2f", totalCargo));
            writer.println("avg_cargo_weight_tons," + String.format("%.2f", avgCargoWeight));
            writer.println("avg_cargo_per_truck_tons," + String.format("%.2f", totalCargo / totalTrucks));
            writer.println("# VALIDATION TARGETS (from survey)");
            writer.println("validation_target_vehicles_per_day,327108");
            writer.println("validation_target_tons_per_day,1726420");
            writer.println("validation_target_avg_load_tons,5.28");
            writer.println("validation_target_empty_ratio,0.37");

            System.out.println("Exported summary.csv");

        } catch (IOException e) {
            System.err.println("Error exporting summary: " + e.getMessage());
        }
    }

    /**
     * Format time as HH:MM:SS
     * @param seconds Seconds since midnight
     * @return Time string in HH:MM:SS format
     */
    private String formatTime(long seconds) {
        int hours = (int)(seconds / 3600);
        int minutes = (int)((seconds % 3600) / 60);
        int secs = (int)(seconds % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Get the run directory path
     * @return Full path to the current run directory
     */
    public String getRunDirectory() {
        return runDirectory;
    }
}
