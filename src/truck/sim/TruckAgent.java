package truck.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an individual truck agent with heterogeneous behavioral patterns.
 * 
 * Agent Types:
 * - DELIVERY: Local delivery, familiar area operations (50%)
 * - LONG_HAUL: Inter-regional freight (30%)
 * - MIXED_OPERATION: City-wide flexible routing (20%)
 * 
 * @author Truck ABM Framework
 * @version 1.0
 */
public class TruckAgent {
    
    // Agent identification
    private final int truckId;
    private final TruckType truckType;
    
    // Home base
    private final double homeLongitude;
    private final double homeLatitude;
    private String homePOIId;  // POI anchoring first-trip origin (null if fallback)
    private FacilityType homeFacilityType;  // Origin facility type for truck-type routing
    
    // Familiar area (for DELIVERY type)
    private final double familiarAreaRadiusKm;
    private String familiarAreaZoneId;
    
    // Vehicle characteristics
    private final String vehicleSize;  // "large", "medium", "small"
    private final double capacityTons;
    private final String primaryGoodsType;
    
    // Shift scheduling
    private final long shiftStartTime;  // Seconds from midnight
    private final long shiftEndTime;
    private final int shiftDurationHours;
    
    // Current state
    private TruckStatus currentStatus;
    private double currentLongitude;
    private double currentLatitude;
    private long currentTime;
    
    // Trip history
    private List<TruckTrip> trips;
    
    // Statistics
    private double totalDistanceKm;
    private double totalCargoWeightTons;
    private int deliveryCount;
    private int emptyTripCount;
    
    /**
     * Constructor for TruckAgent.
     */
    public TruckAgent(int truckId, TruckType truckType,
                     double homeLon, double homeLat,
                     double familiarAreaRadiusKm,
                     String vehicleSize, double capacityTons,
                     String primaryGoodsType,
                     long shiftStartTime, int shiftDurationHours) {
        this.truckId = truckId;
        this.truckType = truckType;
        this.homeLongitude = homeLon;
        this.homeLatitude = homeLat;
        this.familiarAreaRadiusKm = familiarAreaRadiusKm;
        this.vehicleSize = vehicleSize;
        this.capacityTons = capacityTons;
        this.primaryGoodsType = primaryGoodsType;
        this.shiftStartTime = shiftStartTime;
        this.shiftDurationHours = shiftDurationHours;
        this.shiftEndTime = shiftStartTime + (shiftDurationHours * 3600L);
        
        // Initialize state
        this.currentStatus = TruckStatus.IDLE;
        this.currentLongitude = homeLon;
        this.currentLatitude = homeLat;
        this.currentTime = shiftStartTime;
        this.trips = new ArrayList<>();
        
        // Initialize statistics
        this.totalDistanceKm = 0.0;
        this.totalCargoWeightTons = 0.0;
        this.deliveryCount = 0;
        this.emptyTripCount = 0;
    }
    
    /**
     * Add a trip to this truck's history.
     */
    public void addTrip(TruckTrip trip) {
        trips.add(trip);
        totalDistanceKm += trip.getDistanceKm();
        
        if (trip.isCargoLoaded()) {
            deliveryCount++;
            totalCargoWeightTons += trip.getCargoWeightTons();
        } else {
            emptyTripCount++;
        }
    }
    
    /**
     * Check if truck has time remaining in shift.
     */
    public boolean hasTimeInShift(long requiredSeconds) {
        return (currentTime + requiredSeconds) <= shiftEndTime;
    }
    
    /**
     * Calculate empty running ratio.
     */
    public double getEmptyRunningRatio() {
        if (totalDistanceKm == 0) return 0.0;
        
        double emptyDistance = 0.0;
        for (TruckTrip trip : trips) {
            if (!trip.isCargoLoaded()) {
                emptyDistance += trip.getDistanceKm();
            }
        }
        return emptyDistance / totalDistanceKm;
    }
    
    /**
     * Get average cargo weight per delivery.
     */
    public double getAvgCargoWeight() {
        return deliveryCount > 0 ? totalCargoWeightTons / deliveryCount : 0.0;
    }
    
    // Setters
    public void setCurrentStatus(TruckStatus status) { this.currentStatus = status; }
    public void setCurrentLongitude(double lon) { this.currentLongitude = lon; }
    public void setCurrentLatitude(double lat) { this.currentLatitude = lat; }
    public void setCurrentTime(long time) { this.currentTime = time; }
    public void setFamiliarAreaZoneId(String zoneId) { this.familiarAreaZoneId = zoneId; }
    public void setHomePOIId(String poiId) { this.homePOIId = poiId; }
    public void setHomeFacilityType(FacilityType type) { this.homeFacilityType = type; }

    // Getters
    public int getTruckId() { return truckId; }
    public TruckType getTruckType() { return truckType; }
    public double getHomeLongitude() { return homeLongitude; }
    public double getHomeLatitude() { return homeLatitude; }
    public String getHomePOIId() { return homePOIId; }
    public FacilityType getHomeFacilityType() { return homeFacilityType; }
    public double getFamiliarAreaRadiusKm() { return familiarAreaRadiusKm; }
    public String getFamiliarAreaZoneId() { return familiarAreaZoneId; }
    public String getVehicleSize() { return vehicleSize; }
    public double getCapacityTons() { return capacityTons; }
    public String getPrimaryGoodsType() { return primaryGoodsType; }
    public long getShiftStartTime() { return shiftStartTime; }
    public long getShiftEndTime() { return shiftEndTime; }
    public int getShiftDurationHours() { return shiftDurationHours; }
    public TruckStatus getCurrentStatus() { return currentStatus; }
    public double getCurrentLongitude() { return currentLongitude; }
    public double getCurrentLatitude() { return currentLatitude; }
    public long getCurrentTime() { return currentTime; }
    public List<TruckTrip> getTrips() { return trips; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public double getTotalCargoWeightTons() { return totalCargoWeightTons; }
    public int getDeliveryCount() { return deliveryCount; }
    public int getEmptyTripCount() { return emptyTripCount; }
    public int getTotalTripCount() { return deliveryCount + emptyTripCount; }
    
    @Override
    public String toString() {
        return String.format("TruckAgent[%d: %s, %s, %.1ft, deliveries=%d, %.1fkm]",
                           truckId, truckType, vehicleSize, capacityTons,
                           deliveryCount, totalDistanceKm);
    }
}
