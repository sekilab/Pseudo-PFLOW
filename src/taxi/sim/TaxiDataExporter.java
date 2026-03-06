package taxi.sim;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports taxi simulation data to CSV files in PFlow-compatible format
 *
 * V3.1 ENHANCEMENT - PSEUDO PFLOW COMPATIBILITY:
 * - Added exportTripsPseudoPFlow() method for Pseudo PFLOW format
 * - Follows standard column order: ["id", "starttime", "start lon", "start lat",
 *   "end lon", "end lat", "transport mode", "purpose", "occupation"]
 * - Added passenger_in flag to distinguish occupied vs empty trips
 * - Empty trips (repositioning/deadhead) included in output
 * - Taxi-specific columns appended after standard fields
 *
 * Output structure:
 * output/
 *   run_YYYYMMDD_HHMMSS/
 *     taxis.csv                 - Taxi agent information
 *     trips.csv                 - Individual trip records (original format)
 *     trips_pseudo_pflow.csv    - Pseudo PFLOW format with empty trips
 *     trajectories.csv          - Spatiotemporal trajectories
 *     summary.csv               - Aggregate statistics
 *
 * Each run creates a timestamped folder to avoid overwriting previous results
 *
 * V3.0 ENHANCEMENT:
 * - All date/time formats now configurable through TaxiConfig
 * - No hardcoded format strings remain
 */
public class TaxiDataExporter {

    private String outputDirectory;
    private String runDirectory;
    private TaxiConfig config;

    /**
     * Constructor
     * @param outputDir Base output directory
     */
    public TaxiDataExporter(String outputDir) {
        this.config = TaxiConfig.getInstance();
        this.outputDirectory = outputDir;

        // Create timestamped run directory using configurable format
        SimpleDateFormat sdf = new SimpleDateFormat(config.getExportTimestampFormat());
        String timestamp = sdf.format(new Date());
        this.runDirectory = outputDir + "/run_" + timestamp;

        // Create directories if they don't exist
        try {
            Files.createDirectories(Paths.get(runDirectory));
            System.out.println("✓ Created output directory: " + runDirectory);
        } catch (IOException e) {
            System.err.println("✗ Error creating output directory: " + e.getMessage());
        }
    }

    /**
     * Format time as datetime string using configurable format from TaxiConfig
     * Default format: MM/DD/YYYY HH:MM
     *
     * @param seconds Seconds since midnight
     * @return Formatted datetime string
     */
    private String formatDateTime(long seconds) {
        // Use a base date (today's date)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Add the seconds
        cal.add(Calendar.SECOND, (int)seconds);

        // Format using configurable format from TaxiConfig
        SimpleDateFormat sdf = new SimpleDateFormat(config.getExportDatetimeFormat());
        return sdf.format(cal.getTime());
    }

    /**
     * Format time as human-readable HH:MM:SS
     *
     * @param seconds Seconds since midnight
     * @return Time string in HH:MM:SS format
     */
    private String formatTimeHMS(long seconds) {
        int hours = (int)(seconds / 3600);
        int minutes = (int)((seconds % 3600) / 60);
        int secs = (int)(seconds % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Get the day offset from simulation start (0 = same day, 1 = next day, etc.)
     * This handles times that exceed 86400 seconds (24 hours)
     *
     * @param timeSeconds Time in seconds since midnight of simulation start
     * @return Day offset (0, 1, 2, etc.)
     */
    private int getDayOffset(long timeSeconds) {
        return (int)(timeSeconds / 86400);
    }

    /**
     * Normalize time to 0-86400 range (within a single day)
     * This converts times like 108449 (30:07:29) to 22049 (06:07:29)
     *
     * @param timeSeconds Time in seconds since midnight of simulation start
     * @return Normalized time in seconds (0-86399)
     */
    private long getNormalizedTime(long timeSeconds) {
        return timeSeconds % 86400;
    }

    /**
     * Format normalized time with day offset applied
     * This properly handles times that span multiple days
     *
     * @param timeSeconds Time in seconds since midnight of simulation start
     * @return Formatted datetime string with correct date
     */
    private String formatDateTimeWithDay(long timeSeconds) {
        int dayOffset = getDayOffset(timeSeconds);
        long normalizedTime = getNormalizedTime(timeSeconds);

        // Start with base date (today's date)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Add day offset
        cal.add(Calendar.DAY_OF_YEAR, dayOffset);

        // Add normalized seconds
        cal.add(Calendar.SECOND, (int)normalizedTime);

        // Format using configurable format from TaxiConfig
        SimpleDateFormat sdf = new SimpleDateFormat(config.getExportDatetimeFormat());
        return sdf.format(cal.getTime());
    }

    // Store zones for zone lookup during export
    private List<DestinationZone> zones;

    /**
     * Export all simulation data
     * @param taxis List of taxi agents
     * @param trips List of trips (including empty trips)
     */
    public void exportAll(List<TaxiAgent> taxis, List<TaxiTrip> trips) {
        exportTaxis(taxis);
        exportTrips(trips);
        exportTripsPseudoPFlow(trips);  // V3.1: NEW - Pseudo PFLOW format
        exportTripsWithZones(taxis, trips);  // V4.0: NEW - Trips with zone information
        // exportTrajectories(taxis);  // Trajectory feature disabled
        exportSummary(taxis, trips);
    }

    /**
     * Set destination zones for zone lookup
     * @param destinationZones List of destination zones
     */
    public void setDestinationZones(List<DestinationZone> destinationZones) {
        this.zones = destinationZones;
    }

    /**
     * Export taxi agent information (with type and area info)
     */
    private void exportTaxis(List<TaxiAgent> taxis) {
        String filename = runDirectory + "/taxis.csv";
        int taxiCount = 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header - includes taxi type and familiar area info (V4.0: added familiar_zone_ids)
            writer.println("taxi_id,taxi_type,home_lon,home_lat,familiar_radius_km,familiar_area_zone," +
                "familiar_zone_ids,shift_start,shift_end,total_trips,total_distance_km,total_revenue_yen,status");

            // Data rows
            for (TaxiAgent taxi : taxis) {
                // V4.0: Format familiar zone IDs as semicolon-separated list
                String familiarZoneIdsList = "";
                if (taxi.getFamiliarZoneIds() != null && !taxi.getFamiliarZoneIds().isEmpty()) {
                    familiarZoneIdsList = String.join(";", taxi.getFamiliarZoneIds());
                }

                writer.printf("%d,%s,%.6f,%.6f,%.2f,%s,\"%s\",%s,%s,%d,%.2f,%.2f,%s%n",
                    taxi.getTaxiId(),
                    taxi.getTaxiType().toString(),
                    taxi.getHomeLongitude(),
                    taxi.getHomeLatitude(),
                    taxi.getFamiliarAreaRadiusKm(),
                    taxi.getFamiliarAreaZoneId() != null ? taxi.getFamiliarAreaZoneId() : "N/A",
                    familiarZoneIdsList,  // V4.0: Semicolon-separated list
                    formatDateTime(taxi.getShiftStartTime()),
                    formatDateTime(taxi.getShiftEndTime()),
                    taxi.getAssignedTrips().size(),
                    taxi.getTotalDistanceKm(),
                    taxi.getTotalRevenueYen(),
                    taxi.getCurrentStatus().toString()
                );
                taxiCount++;
            }

            System.out.println("✓ Exported taxis.csv: " + taxiCount + " taxis");

        } catch (IOException e) {
            System.err.println("✗ Error exporting taxis: " + e.getMessage());
        }
    }

    /**
     * Export trip information (original format)
     */
    private void exportTrips(List<TaxiTrip> trips) {
        String filename = runDirectory + "/trips.csv";
        int tripCount = 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header - added day offset columns for multi-day trip tracking
            writer.println("trip_id,taxi_id,pickup_lon,pickup_lat,dropoff_lon,dropoff_lat," +
                "request_time,request_day,pickup_time,pickup_day,dropoff_time,dropoff_day," +
                "distance_km,fare_yen,is_night_trip,passenger_in,status");

            // Data rows
            for (TaxiTrip trip : trips) {
                writer.printf("%d,%d,%.6f,%.6f,%.6f,%.6f,%s,%d,%s,%d,%s,%d,%.2f,%.2f,%s,%s,%s%n",
                    trip.getTripId(),
                    trip.getAssignedTaxiId(),
                    trip.getPickupLongitude(),
                    trip.getPickupLatitude(),
                    trip.getDropoffLongitude(),
                    trip.getDropoffLatitude(),
                    formatDateTimeWithDay(trip.getRequestTime()),
                    getDayOffset(trip.getRequestTime()),
                    formatDateTimeWithDay(trip.getPickupTime()),
                    getDayOffset(trip.getPickupTime()),
                    formatDateTimeWithDay(trip.getDropoffTime()),
                    getDayOffset(trip.getDropoffTime()),
                    trip.getDistanceKm(),
                    trip.getFareYen(),
                    trip.isNightTrip() ? "true" : "false",
                    trip.isPassengerIn() ? "true" : "false",
                    trip.getStatus().toString()
                );
                tripCount++;
            }

            System.out.println("✓ Exported trips.csv: " + tripCount + " trips");

        } catch (IOException e) {
            System.err.println("✗ Error exporting trips: " + e.getMessage());
        }
    }

    /**
     * V3.1: Export trip information in Pseudo PFLOW format
     *
     * PSEUDO PFLOW STANDARD FORMAT:
     * Columns: ["id", "starttime", "start lon", "start lat", "end lon", "end lat",
     *           "transport mode", "purpose", "occupation",
     *           "passenger_in", "taxi_id", "distance_km", "fare_yen", "is_night_trip"]
     *
     * TRANSPORT MODE CODES (Updated per user requirements):
     * - 1 = Private vehicle (car)
     * - 2 = Train/Railway
     * - 3 = Bus
     * - 4 = Bicycle
     * - 5 = Walk
     * - 6 = (occupied by other mode)
     * - 7 = (occupied by other mode)
     * - 8 = Taxi (USER SPECIFIED: use code 8)
     *
     * PURPOSE CODES (from Pseudo PFLOW):
     * - 1 = Commute
     * - 2 = Business
     * - 3 = Free/Shopping/Entertainment
     * - 4 = Go to home
     * - 0 = Not applicable (USER SPECIFIED: for taxi empty trips)
     *
     * OCCUPATION CODES (from Pseudo PFLOW):
     * - 0 = Not applicable (taxis don't have fixed occupation)
     * - Can be extended if needed to track passenger demographics
     *
     * TIME FORMAT:
     * - USER SPECIFIED: starttime should be seconds from midnight (0:00), not Unix timestamp
     */
    private void exportTripsPseudoPFlow(List<TaxiTrip> trips) {
        String filename = runDirectory + "/trips_pseudo_pflow.csv";
        int tripCount = 0;
        int passengerTrips = 0;
        int emptyTrips = 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header - Pseudo PFLOW standard columns + taxi-specific extensions + human-readable time + simulation_day at end
            writer.println("id,starttime,start_lon,start_lat,end_lon,end_lat," +
                "transport_mode,purpose,occupation," +
                "passenger_in,taxi_id,distance_km,fare_yen,is_night_trip,starttime_h,simulation_day");

            // Data rows
            for (TaxiTrip trip : trips) {
                // Standard Pseudo PFLOW fields
                int transportMode = 8;  // USER SPECIFIED: 8 = Taxi
                int purpose = trip.isPassengerIn() ? 3 : 0;  // USER SPECIFIED: 0 for empty trips
                int occupation = 0;  // Not applicable for taxis

                // Normalize time for multi-day trips
                long pickupTime = trip.getPickupTime();
                long normalizedTime = getNormalizedTime(pickupTime);
                int simulationDay = getDayOffset(pickupTime);

                writer.printf("%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%s,%d,%.2f,%.2f,%s,%s,%d%n",
                    // Standard Pseudo PFLOW columns
                    trip.getTripId(),
                    normalizedTime,  // USER SPECIFIED: Normalized seconds from midnight (0-86400)
                    trip.getPickupLongitude(),
                    trip.getPickupLatitude(),
                    trip.getDropoffLongitude(),
                    trip.getDropoffLatitude(),
                    transportMode,
                    purpose,
                    occupation,
                    // Taxi-specific extensions
                    trip.isPassengerIn() ? "true" : "false",  // passenger_in flag
                    trip.getAssignedTaxiId(),
                    trip.getDistanceKm(),
                    trip.getFareYen(),
                    trip.isNightTrip() ? "true" : "false",
                    formatTimeHMS(normalizedTime),  // starttime_h (human-readable, normalized)
                    simulationDay   // Day offset: 0 = simulation day, 1 = next day, etc. (LAST COLUMN)
                );
                tripCount++;
                if (trip.isPassengerIn()) {
                    passengerTrips++;
                } else {
                    emptyTrips++;
                }
            }

            System.out.println("✓ Exported trips_pseudo_pflow.csv: " + tripCount + " trips (" +
                passengerTrips + " passenger, " + emptyTrips + " empty)");

        } catch (IOException e) {
            System.err.println("✗ Error exporting Pseudo PFLOW trips: " + e.getMessage());
        }
    }

    /**
     * V4.0: Export trips with agent type and zone information
     * This export includes taxi type, origin zone, and destination zone
     * but NOT in P-Flow format yet (as user requested)
     */
    private void exportTripsWithZones(List<TaxiAgent> taxis, List<TaxiTrip> trips) {
        String filename = runDirectory + "/trips_with_zones.csv";
        int tripCount = 0;

        // Build a map of taxi ID to taxi agent for quick lookup
        Map<Integer, TaxiAgent> taxiMap = new java.util.HashMap<>();
        for (TaxiAgent taxi : taxis) {
            taxiMap.put(taxi.getTaxiId(), taxi);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header - V4.0: added origin/dest zone_type and transport_hub columns
            writer.println("trip_id,taxi_id,taxi_type,pickup_lon,pickup_lat,dropoff_lon,dropoff_lat," +
                "origin_zone,origin_zone_type,origin_transport_hub," +
                "destination_zone,dest_zone_type,dest_transport_hub," +
                "request_time,request_day,pickup_time,pickup_day," +
                "dropoff_time,dropoff_day,distance_km,fare_yen,is_night_trip,passenger_in,status");

            // Data rows
            for (TaxiTrip trip : trips) {
                TaxiAgent taxi = taxiMap.get(trip.getAssignedTaxiId());
                String taxiType = taxi != null ? taxi.getTaxiType().toString() : "UNKNOWN";

                // V4.0: Find origin and destination zones with full zone info
                DestinationZone originZoneObj = findZoneObjectForLocation(trip.getPickupLongitude(), trip.getPickupLatitude());
                DestinationZone destZoneObj = findZoneObjectForLocation(trip.getDropoffLongitude(), trip.getDropoffLatitude());

                String originZone = originZoneObj != null ? originZoneObj.getZoneId() : "OUTSIDE";
                String originZoneType = originZoneObj != null ? originZoneObj.getZoneType() : "N/A";
                String originHub = originZoneObj != null ? (originZoneObj.isTransportHub() ? "TRUE" : "FALSE") : "FALSE";

                String destZone = destZoneObj != null ? destZoneObj.getZoneId() : "OUTSIDE";
                String destZoneType = destZoneObj != null ? destZoneObj.getZoneType() : "N/A";
                String destHub = destZoneObj != null ? (destZoneObj.isTransportHub() ? "TRUE" : "FALSE") : "FALSE";

                writer.printf("%d,%d,%s,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%d,%.2f,%.2f,%s,%s,%s%n",
                    trip.getTripId(),
                    trip.getAssignedTaxiId(),
                    taxiType,
                    trip.getPickupLongitude(),
                    trip.getPickupLatitude(),
                    trip.getDropoffLongitude(),
                    trip.getDropoffLatitude(),
                    originZone,
                    originZoneType,
                    originHub,
                    destZone,
                    destZoneType,
                    destHub,
                    formatDateTimeWithDay(trip.getRequestTime()),
                    getDayOffset(trip.getRequestTime()),
                    formatDateTimeWithDay(trip.getPickupTime()),
                    getDayOffset(trip.getPickupTime()),
                    formatDateTimeWithDay(trip.getDropoffTime()),
                    getDayOffset(trip.getDropoffTime()),
                    trip.getDistanceKm(),
                    trip.getFareYen(),
                    trip.isNightTrip() ? "true" : "false",
                    trip.isPassengerIn() ? "true" : "false",
                    trip.getStatus().toString()
                );
                tripCount++;
            }

            System.out.println("✓ Exported trips_with_zones.csv: " + tripCount + " trips");

        } catch (IOException e) {
            System.err.println("✗ Error exporting trips with zones: " + e.getMessage());
        }
    }

    /**
     * Find which zone a location belongs to
     */
    private String findZoneForLocation(double lon, double lat) {
        if (zones == null || zones.isEmpty()) {
            return "UNKNOWN";
        }

        for (DestinationZone zone : zones) {
            if (zone.containsPoint(lon, lat)) {
                return zone.getZoneId();
            }
        }
        return "OUTSIDE";
    }

    /**
     * V4.0: Find which zone object a location belongs to (returns full zone, not just ID)
     */
    private DestinationZone findZoneObjectForLocation(double lon, double lat) {
        if (zones == null || zones.isEmpty()) {
            return null;
        }

        for (DestinationZone zone : zones) {
            if (zone.containsPoint(lon, lat)) {
                return zone;
            }
        }
        return null;  // Location is outside all zones
    }

    /**
     * Export taxi trajectories (spatiotemporal paths)
     * @deprecated Trajectory feature not implemented - always exports empty file
     */
    @Deprecated
    private void exportTrajectories(List<TaxiAgent> taxis) {
        String filename = runDirectory + "/trajectories.csv";
        int pointCount = 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header - added timestamp_day for multi-day trajectory tracking
            writer.println("taxi_id,timestamp,timestamp_day,longitude,latitude,sequence_num");

            // Data rows
            for (TaxiAgent taxi : taxis) {
                int seqNum = 0;
                for (TaxiAgent.TrajectoryPoint point : taxi.getTrajectory()) {
                    writer.printf("%d,%s,%d,%.6f,%.6f,%d%n",
                        taxi.getTaxiId(),
                        formatDateTimeWithDay(point.getTimestamp()),
                        getDayOffset(point.getTimestamp()),
                        point.getLongitude(),
                        point.getLatitude(),
                        seqNum++
                    );
                    pointCount++;
                }
            }

            System.out.println("✓ Exported trajectories.csv: " + pointCount + " points");

        } catch (IOException e) {
            System.err.println("✗ Error exporting trajectories: " + e.getMessage());
        }
    }

    /**
     * Export summary statistics (updated to include empty trip stats)
     */
    private void exportSummary(List<TaxiAgent> taxis, List<TaxiTrip> trips) {
        String filename = runDirectory + "/summary.csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Calculate statistics
            int totalTaxis = taxis.size();
            int totalTrips = trips.size();

            double totalDistance = 0;
            double totalRevenue = 0;
            int nightTrips = 0;
            int passengerTrips = 0;
            int emptyTrips = 0;
            double passengerDistance = 0;
            double emptyDistance = 0;

            for (TaxiTrip trip : trips) {
                totalDistance += trip.getDistanceKm();
                totalRevenue += trip.getFareYen();
                if (trip.isNightTrip()) {
                    nightTrips++;
                }
                if (trip.isPassengerIn()) {
                    passengerTrips++;
                    passengerDistance += trip.getDistanceKm();
                } else {
                    emptyTrips++;
                    emptyDistance += trip.getDistanceKm();
                }
            }

            double avgTripsPerTaxi = (double) totalTrips / totalTaxis;
            double avgDistancePerTaxi = totalDistance / totalTaxis;
            double avgRevenuePerTaxi = totalRevenue / totalTaxis;
            double avgTripDistance = totalDistance / totalTrips;
            double avgFare = passengerTrips > 0 ? totalRevenue / passengerTrips : 0;
            double nightTripPercentage = 100.0 * nightTrips / totalTrips;
            double passengerTripPercentage = 100.0 * passengerTrips / totalTrips;
            double emptyTripPercentage = 100.0 * emptyTrips / totalTrips;
            double emptyDistancePercentage = 100.0 * emptyDistance / totalDistance;

            // Write summary
            writer.println("metric,value");
            writer.println("total_taxis," + totalTaxis);
            writer.println("total_trips," + totalTrips);
            writer.println("passenger_trips," + passengerTrips);
            writer.println("empty_trips," + emptyTrips);
            writer.println("passenger_trip_percentage," + String.format("%.2f", passengerTripPercentage));
            writer.println("empty_trip_percentage," + String.format("%.2f", emptyTripPercentage));
            writer.println("total_distance_km," + String.format("%.2f", totalDistance));
            writer.println("passenger_distance_km," + String.format("%.2f", passengerDistance));
            writer.println("empty_distance_km," + String.format("%.2f", emptyDistance));
            writer.println("empty_distance_percentage," + String.format("%.2f", emptyDistancePercentage));
            writer.println("total_revenue_yen," + String.format("%.2f", totalRevenue));
            writer.println("night_trips," + nightTrips);
            writer.println("night_trip_percentage," + String.format("%.2f", nightTripPercentage));
            writer.println("avg_trips_per_taxi," + String.format("%.2f", avgTripsPerTaxi));
            writer.println("avg_distance_per_taxi_km," + String.format("%.2f", avgDistancePerTaxi));
            writer.println("avg_revenue_per_taxi_yen," + String.format("%.2f", avgRevenuePerTaxi));
            writer.println("avg_trip_distance_km," + String.format("%.2f", avgTripDistance));
            writer.println("avg_fare_yen," + String.format("%.2f", avgFare));
            writer.println("revenue_per_km_yen," + String.format("%.2f", totalRevenue / totalDistance));

            System.out.println("✓ Exported summary.csv");

        } catch (IOException e) {
            System.err.println("✗ Error exporting summary: " + e.getMessage());
        }
    }

    /**
     * Get the run directory path
     * @return Full path to the current run directory
     */
    public String getRunDirectory() {
        return runDirectory;
    }
}
