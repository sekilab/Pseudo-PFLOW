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
 * - trips.csv: Comprehensive trip records (all columns)
 * - trips_pseudo_pflow.csv: Pseudo PFLOW format (transport_mode=9)
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
        exportTripsComplete(trucks, trips);
        exportTripsPseudoPFlow(trips);
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
     * Export comprehensive trip records with all columns including
     * truck type, facility IDs, and loading/unloading times.
     */
    private void exportTripsComplete(List<TruckAgent> trucks, List<TruckTrip> trips) {
        String filename = runDirectory + "/trips.csv";
        int tripCount = 0;

        Map<Integer, TruckAgent> truckMap = new HashMap<>();
        for (TruckAgent truck : trucks) {
            truckMap.put(truck.getTruckId(), truck);
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            writer.println("trip_id,truck_id,truck_type,vehicle_size,capacity_tons," +
                "origin_lon,origin_lat,dest_lon,dest_lat," +
                "origin_zone,dest_zone,origin_facility_id,dest_facility_id," +
                "request_time,loading_start,departure_time,arrival_time,unloading_end," +
                "distance_km,cargo_loaded,goods_type,cargo_weight_tons," +
                "loading_time_min,unloading_time_min,status");

            for (TruckTrip trip : trips) {
                TruckAgent truck = truckMap.get(trip.getTruckId());
                String truckType = truck != null ? truck.getTruckType().toString() : "UNKNOWN";

                writer.printf("%d,%d,%s,%s,%.2f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%s,%s,%.2f,%.2f,%.2f,%s%n",
                    trip.getTripId(),
                    trip.getTruckId(),
                    truckType,
                    trip.getVehicleSize(),
                    trip.getCapacityTons(),
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
                    trip.getCargoWeightTons(),
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
