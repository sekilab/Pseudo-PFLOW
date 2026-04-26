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
 * Known limitation: daily cumulative distance not validated. See Shi KR3.
 *
 * Shift start/end managed by TaxiSimulation.initializeFleet()
 *
 * Hotspot zone logic implemented in TaxiSimulation. Waiting area queueing is future work.
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

    // Trip and status tracking.
    // NOTE: matching/dispatch and passenger-demand modelling are out of scope
    // for this dataset-generation release — currentStatus therefore never
    // leaves IDLE. TaxiStatus was shrunk to a single value in 2026-04; reinstate
    // states here if a future release adds a live state machine.
    private List<TaxiTrip> assignedTrips;
    private TaxiStatus currentStatus;

    // Statistics
    private double totalDistanceKm;
    private double totalRevenueYen;

    // Trajectory tracking handled by traj.TrajectoryMain (separate phase)

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

        this.totalDistanceKm = 0.0;
        this.totalRevenueYen = 0.0;
        // Home depot initialization handled by FleetManager
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

}
