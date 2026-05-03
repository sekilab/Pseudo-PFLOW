package taxi.sim;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports taxi simulation data to CSV files in PFlow-compatible format.
 *
 * Output structure (per run):
 *   taxis.csv                 - Taxi agent information
 *   trips.csv                 - Comprehensive trip records (24 columns)
 *   trips_pseudo_pflow.csv    - Pseudo PFLOW format (16 columns)
 *
 * Date/time formats are configurable through TaxiConfig.
 */
public class TaxiDataExporter {

    private String outputDirectory;
    private String runDirectory;
    private TaxiConfig config;
    private SimpleDateFormat datetimeFormat;

    /**
     * Constructor
     * @param outputDir Base output directory
     */
    public TaxiDataExporter(String outputDir) {
        this.config = TaxiConfig.getInstance();
        this.datetimeFormat = new SimpleDateFormat(config.getExportDatetimeFormat());
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

        return datetimeFormat.format(cal.getTime());
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

        return datetimeFormat.format(cal.getTime());
    }

    // Store zones for zone lookup during export
    private List<DestinationZone> zones;

    /**
     * Export all simulation data
     * @param taxis List of taxi agents
     * @param trips List of trips (including empty trips)
     */
    public void exportAll(List<TaxiAgent> taxis, List<TaxiTrip> trips) {
        writeRunMetadata();
        exportTaxis(taxis);
        exportTripsComplete(taxis, trips);
        exportTripsPseudoPFlow(trips);
    }

    /**
     * Write a small JSON file capturing run provenance: git commit, config
     * path, timestamp, JVM, OS. Lets downstream consumers (Baseline Platform,
     * MVE, validation diff tools) reconcile a result package against the
     * exact code state that produced it.
     *
     * <p>Added 2026-05-03 in response to Shi's Baseline Platform v0.1 audit
     * (confirmation question G — runtime config snapshots).
     */
    private void writeRunMetadata() {
        String filename = runDirectory + "/run_metadata.json";
        String commit = captureGitCommit();
        String dirty = captureGitDirty();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
        String javaVersion = System.getProperty("java.version", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        String userDir = System.getProperty("user.dir", "");
        String configFilePath = (config != null) ? config.getConfigFilePath() : "unknown";

        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            w.println("{");
            w.println("  \"sim_module\": \"taxi\",");
            w.println("  \"timestamp\": \"" + timestamp + "\",");
            w.println("  \"git_commit\": \"" + commit + "\",");
            w.println("  \"git_dirty\": " + dirty + ",");
            w.println("  \"config_file\": \"" + jsonEscape(configFilePath) + "\",");
            w.println("  \"output_dir\": \"" + jsonEscape(runDirectory) + "\",");
            w.println("  \"java_version\": \"" + javaVersion + "\",");
            w.println("  \"os\": \"" + osName + " " + osVersion + "\",");
            w.println("  \"working_dir\": \"" + jsonEscape(userDir) + "\"");
            w.println("}");
            System.out.println("Wrote run metadata: " + filename + " (commit=" + commit + ")");
        } catch (IOException e) {
            System.err.println("Warning: failed to write run_metadata.json: " + e.getMessage());
        }
    }

    /** Capture {@code git rev-parse --short HEAD}; returns "unknown" on failure. */
    private static String captureGitCommit() {
        return runGitCommand(new String[]{"git", "rev-parse", "--short", "HEAD"}, "unknown");
    }

    /** Returns "true" if {@code git status --porcelain} reports any changes, else "false"; "false" on failure. */
    private static String captureGitDirty() {
        String out = runGitCommand(new String[]{"git", "status", "--porcelain"}, "");
        return (out != null && !out.isEmpty()) ? "true" : "false";
    }

    /** Run a git command with a 3-second timeout; return stdout trimmed, or fallback on any error. */
    private static String runGitCommand(String[] cmd, String fallback) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            Process p = pb.start();
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return fallback;
            }
            if (p.exitValue() != 0) return fallback;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
                return sb.toString().trim();
            }
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Minimal JSON string escape for backslash and double-quote (sufficient for paths). */
    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
     * Export comprehensive trip records with all columns including
     * taxi type, zone info, and day-offset timestamps.
     */
    private void exportTripsComplete(List<TaxiAgent> taxis, List<TaxiTrip> trips) {
        String filename = runDirectory + "/trips.csv";
        int tripCount = 0;

        Map<Integer, TaxiAgent> taxiMap = new java.util.HashMap<>();
        for (TaxiAgent taxi : taxis) {
            taxiMap.put(taxi.getTaxiId(), taxi);
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename), 65536))) {
            writer.println("trip_id,taxi_id,taxi_type," +
                "pickup_lon,pickup_lat,dropoff_lon,dropoff_lat," +
                "origin_zone,origin_zone_type,origin_transport_hub," +
                "destination_zone,dest_zone_type,dest_transport_hub," +
                "request_time,request_day,pickup_time,pickup_day," +
                "dropoff_time,dropoff_day," +
                "distance_km,fare_yen,is_night_trip,passenger_in,status");

            for (TaxiTrip trip : trips) {
                TaxiAgent taxi = taxiMap.get(trip.getAssignedTaxiId());
                String taxiType = taxi != null ? taxi.getTaxiType().toString() : "UNKNOWN";

                DestinationZone originZoneObj = findZoneObjectForLocation(
                    trip.getPickupLongitude(), trip.getPickupLatitude());
                DestinationZone destZoneObj = findZoneObjectForLocation(
                    trip.getDropoffLongitude(), trip.getDropoffLatitude());

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
                    originZone, originZoneType, originHub,
                    destZone, destZoneType, destHub,
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
     * Get the run directory path
     * @return Full path to the current run directory
     */
    public String getRunDirectory() {
        return runDirectory;
    }
}
