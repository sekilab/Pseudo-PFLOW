package taxi.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single taxi agent in the Tokyo taxi simulation.
 *
 * This agent tracks:
 * - Home base location (depot/waiting area)
 * - Assigned trips throughout the day
 * - Current state (IDLE, SEARCHING, OCCUPIED, etc.)
 * - Complete trajectory (spatiotemporal path)
 * - Revenue and distance statistics
 *
 * VALIDATION & CALIBRATION NOTES:
 * --------------------------------
 * TODO: Daily distance validation
 * - Target: Average Tokyo taxi runs 200-300 km per day (verify with actual survey data)
 * - Current implementation generates trips that may exceed this
 * - Need to find: Official Tokyo taxi operation statistics
 * - Data sources to check:
 *   1. Tokyo Metropolitan Government taxi operation reports
 *   2. Japan Taxi Association annual statistics
 *   3. Ministry of Land, Infrastructure, Transport and Tourism (MLIT) data
 *
 * TODO: Initial state and operating hours
 * - Should taxis start at depot/garage at shift start?
 * - Define shift patterns: day shift (08:00-20:00), night shift (20:00-08:00)
 * - Late-night taxis (22:00-05:00) have +20% fare
 * - Consider multi-shift taxis vs single-shift operations
 *
 * TODO: Waiting areas and hotspots
 * - Major taxi stands: Tokyo Station, Shinjuku Station, Shibuya Station, airports
 * - Should idle taxis return to nearest hotspot after dropoff?
 * - Implement zone-based taxi waiting behavior
 *
 * V3.0 ENHANCEMENT:
 * - Night hours check now uses TaxiConfig
 * - No hardcoded values remain
 * - All time-related checks use configurable parameters
 */
public class TaxiAgent {

    // Basic identification
    private final int taxiId;

    // Agent type and behavior
    private final TaxiType taxiType;
    private final double familiarAreaRadiusKm;  // For LOCAL type taxis (legacy)
    private String familiarAreaZoneId;          // Zone ID for familiar area (legacy export)
    private List<String> familiarZoneIds;       // V4.0: List of 3-5 familiar zones for LOCAL taxis

    // Location information
    private double homeLongitude;
    private double homeLatitude;

    // Operational schedule
    private long shiftStartTime;  // in seconds since midnight
    private long shiftEndTime;    // in seconds since midnight

    // Trip and status tracking
    private List<TaxiTrip> assignedTrips;
    private TaxiStatus currentStatus;

    // Statistics
    private double totalDistanceKm;
    private double totalRevenueYen;

    // Trajectory (for spatiotemporal analysis)
    // TODO: Feature not implemented - trajectory tracking disabled
    // Uncomment when implementing spatiotemporal analysis
    // @Deprecated
    // private List<TrajectoryPoint> trajectory;

    /**
     * Constructor for TaxiAgent
     * @param taxiId Unique identifier for this taxi
     * @param taxiType Type of taxi (LOCAL, CITYWIDE, HUB)
     * @param homeLon Home base longitude (depot location)
     * @param homeLat Home base latitude (depot location)
     * @param shiftStart Shift start time in seconds since midnight
     * @param shiftEnd Shift end time in seconds since midnight
     * @param familiarRadius Familiar area radius in km (for LOCAL type)
     */
    public TaxiAgent(int taxiId, TaxiType taxiType, double homeLon, double homeLat,
                     long shiftStart, long shiftEnd, double familiarRadius) {
        this.taxiId = taxiId;
        this.taxiType = taxiType;
        this.homeLongitude = homeLon;
        this.homeLatitude = homeLat;
        this.shiftStartTime = shiftStart;
        this.shiftEndTime = shiftEnd;
        this.familiarAreaRadiusKm = familiarRadius;

        this.assignedTrips = new ArrayList<>();
        this.currentStatus = TaxiStatus.IDLE;
        this.familiarZoneIds = new ArrayList<>();  // V4.0: Initialize familiar zones list
        // this.trajectory = new ArrayList<>();  // Trajectory feature disabled

        this.totalDistanceKm = 0.0;
        this.totalRevenueYen = 0.0;

        // TODO: Initialize taxi at home depot at shift start
        // Add initial trajectory point at home location at shift start time
        // this.addTrajectoryPoint(homeLon, homeLat, shiftStart);
    }

    /**
     * Assign a trip to this taxi
     * @param trip The trip to assign
     */
    public void assignTrip(TaxiTrip trip) {
        assignedTrips.add(trip);
        // Update statistics
        totalDistanceKm += trip.getDistanceKm();
        totalRevenueYen += trip.getFareYen();
    }

    /**
     * Add a point to the taxi's trajectory
     * @param lon Longitude
     * @param lat Latitude
     * @param timestamp Time in seconds since midnight
     * @deprecated Trajectory feature not implemented - method disabled
     */
    @Deprecated
    public void addTrajectoryPoint(double lon, double lat, long timestamp) {
        // trajectory.add(new TrajectoryPoint(lon, lat, timestamp));
        // Feature disabled - uncomment when implementing spatiotemporal analysis
    }

    // ==================== Getters and Setters ====================

    public int getTaxiId() {
        return taxiId;
    }

    public TaxiType getTaxiType() {
        return taxiType;
    }

    public double getFamiliarAreaRadiusKm() {
        return familiarAreaRadiusKm;
    }

    public String getFamiliarAreaZoneId() {
        return familiarAreaZoneId;
    }

    public void setFamiliarAreaZoneId(String zoneId) {
        this.familiarAreaZoneId = zoneId;
    }

    // V4.0: Familiar zones list getter/setter
    public List<String> getFamiliarZoneIds() {
        return familiarZoneIds;
    }

    public void setFamiliarZoneIds(List<String> zoneIds) {
        this.familiarZoneIds = zoneIds;
    }

    public double getHomeLongitude() {
        return homeLongitude;
    }

    public double getHomeLatitude() {
        return homeLatitude;
    }

    public long getShiftStartTime() {
        return shiftStartTime;
    }

    public long getShiftEndTime() {
        return shiftEndTime;
    }

    public List<TaxiTrip> getAssignedTrips() {
        return assignedTrips;
    }

    public TaxiStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(TaxiStatus status) {
        this.currentStatus = status;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public double getTotalRevenueYen() {
        return totalRevenueYen;
    }

    /**
     * @deprecated Trajectory feature not implemented
     */
    @Deprecated
    public List<TrajectoryPoint> getTrajectory() {
        // return trajectory;
        return new ArrayList<>();  // Return empty list when feature is disabled
    }

    /**
     * Inner class representing a single point in the taxi's trajectory
     * @deprecated Trajectory feature not implemented
     */
    @Deprecated
    public static class TrajectoryPoint {
        private final double longitude;
        private final double latitude;
        private final long timestamp;  // seconds since midnight

        public TrajectoryPoint(double lon, double lat, long time) {
            this.longitude = lon;
            this.latitude = lat;
            this.timestamp = time;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
