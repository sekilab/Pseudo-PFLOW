package truck.sim;

/**
 * Represents a single truck delivery trip.
 * 
 * Trip Types:
 * - Delivery trip: cargoLoaded = true, goodsType specified
 * - Empty trip: cargoLoaded = false (repositioning)
 * 
 * @author Truck ABM Framework
 * @version 1.0
 */
public class TruckTrip {
    
    // Trip identification
    private final long tripId;
    private final int truckId;
    
    // Spatial attributes
    private final double originLongitude;
    private final double originLatitude;
    private final double destLongitude;
    private final double destLatitude;
    
    // Zone information
    private String originZoneId;
    private String destZoneId;

    // Facility anchoring (POI IDs, null when road-snap fallback was used)
    private String originFacilityId;
    private String destFacilityId;
    
    // Temporal attributes (seconds from midnight)
    private final long requestTime;
    private long loadingStartTime;
    private long departureTime;
    private long arrivalTime;
    private long unloadingEndTime;
    
    // Trip characteristics
    private final double distanceKm;
    private final boolean cargoLoaded;  // false for empty trips
    private final String goodsType;     // From Dataset 7
    private final String vehicleSize;   // large, medium, small
    private final double cargoWeightTons;
    private final double capacityTons;
    
    // Loading/unloading times (minutes)
    private final double loadingTimeMinutes;
    private final double unloadingTimeMinutes;
    
    // Status
    private TruckStatus status;
    
    // Constraint types
    public enum LoadingConstraint { CAPACITY, WEIGHT }
    private LoadingConstraint loadingConstraint;

    // Inter-metropolitan flag (for deferred metrics recording in parallel mode)
    private boolean interMetro;

    // Time window constraints (from MFS File 05)
    private boolean hasTimeWindow;
    private int earliestDeliveryHour;  // 0-23
    private int latestDeliveryHour;    // 0-23

    /**
     * Constructor for delivery trip (with cargo).
     */
    public TruckTrip(long tripId, int truckId,
                    double originLon, double originLat,
                    double destLon, double destLat,
                    long requestTime,
                    double distanceKm,
                    String goodsType,
                    String vehicleSize,
                    double cargoWeightTons,
                    double capacityTons,
                    double loadingTimeMinutes,
                    double unloadingTimeMinutes) {
        this.tripId = tripId;
        this.truckId = truckId;
        this.originLongitude = originLon;
        this.originLatitude = originLat;
        this.destLongitude = destLon;
        this.destLatitude = destLat;
        this.requestTime = requestTime;
        this.distanceKm = distanceKm;
        this.cargoLoaded = true;
        this.goodsType = goodsType;
        this.vehicleSize = vehicleSize;
        this.cargoWeightTons = cargoWeightTons;
        this.capacityTons = capacityTons;
        this.loadingTimeMinutes = loadingTimeMinutes;
        this.unloadingTimeMinutes = unloadingTimeMinutes;
        this.status = TruckStatus.IDLE;
        this.loadingConstraint = LoadingConstraint.CAPACITY; // Default, will be set later if needed
    }
    
    /**
     * Constructor for empty trip (repositioning).
     */
    public TruckTrip(long tripId, int truckId,
                    double originLon, double originLat,
                    double destLon, double destLat,
                    long requestTime,
                    double distanceKm,
                    String vehicleSize) {
        this.tripId = tripId;
        this.truckId = truckId;
        this.originLongitude = originLon;
        this.originLatitude = originLat;
        this.destLongitude = destLon;
        this.destLatitude = destLat;
        this.requestTime = requestTime;
        this.distanceKm = distanceKm;
        this.cargoLoaded = false;
        this.goodsType = "EMPTY";
        this.vehicleSize = vehicleSize;
        this.cargoWeightTons = 0.0;
        this.capacityTons = 0.0;
        this.loadingTimeMinutes = 0.0;
        this.unloadingTimeMinutes = 0.0;
        this.status = TruckStatus.EMPTY_RUNNING;
    }
    
    /**
     * Calculate travel time based on distance and average speed.
     * 
     * @param avgSpeedKmh Average speed in km/h
     * @return Travel time in seconds
     */
    public long calculateTravelTime(double avgSpeedKmh) {
        return (long) (distanceKm / avgSpeedKmh * 3600);
    }
    
    /**
     * Get total trip duration including loading, travel, and unloading.
     * 
     * @param avgSpeedKmh Average speed in km/h
     * @return Total duration in seconds
     */
    public long getTotalDuration(double avgSpeedKmh) {
        long travelTime = calculateTravelTime(avgSpeedKmh);
        long loadingTimeSec = (long) (loadingTimeMinutes * 60);
        long unloadingTimeSec = (long) (unloadingTimeMinutes * 60);
        
        return loadingTimeSec + travelTime + unloadingTimeSec;
    }
    
    // Setters for temporal attributes
    public void setLoadingStartTime(long time) { this.loadingStartTime = time; }
    public void setDepartureTime(long time) { this.departureTime = time; }
    public void setArrivalTime(long time) { this.arrivalTime = time; }
    public void setUnloadingEndTime(long time) { this.unloadingEndTime = time; }
    public void setStatus(TruckStatus status) { this.status = status; }
    public void setOriginZoneId(String zoneId) { this.originZoneId = zoneId; }
    public void setDestZoneId(String zoneId) { this.destZoneId = zoneId; }
    public void setLoadingConstraint(LoadingConstraint constraint) { this.loadingConstraint = constraint; }
    public void setOriginFacilityId(String poiId) { this.originFacilityId = poiId; }
    public void setDestFacilityId(String poiId) { this.destFacilityId = poiId; }
    public void setInterMetro(boolean interMetro) { this.interMetro = interMetro; }
    public boolean isInterMetro() { return interMetro; }

    // Time window setters
    public void setTimeWindow(int earliest, int latest) {
        this.hasTimeWindow = true;
        this.earliestDeliveryHour = earliest;
        this.latestDeliveryHour = latest;
    }
    
    // Getters
    public long getTripId() { return tripId; }
    public int getTruckId() { return truckId; }
    public double getOriginLongitude() { return originLongitude; }
    public double getOriginLatitude() { return originLatitude; }
    public double getDestLongitude() { return destLongitude; }
    public double getDestLatitude() { return destLatitude; }
    public String getOriginZoneId() { return originZoneId; }
    public String getDestZoneId() { return destZoneId; }
    public String getOriginFacilityId() { return originFacilityId; }
    public String getDestFacilityId() { return destFacilityId; }
    public long getRequestTime() { return requestTime; }
    public long getLoadingStartTime() { return loadingStartTime; }
    public long getDepartureTime() { return departureTime; }
    public long getArrivalTime() { return arrivalTime; }
    public long getUnloadingEndTime() { return unloadingEndTime; }
    public double getDistanceKm() { return distanceKm; }
    public boolean isCargoLoaded() { return cargoLoaded; }
    public String getGoodsType() { return goodsType; }
    public String getVehicleSize() { return vehicleSize; }
    public double getCargoWeightTons() { return cargoWeightTons; }
    public double getCapacityTons() { return capacityTons; }
    public double getLoadingTimeMinutes() { return loadingTimeMinutes; }
    public double getUnloadingTimeMinutes() { return unloadingTimeMinutes; }
    public TruckStatus getStatus() { return status; }
    public LoadingConstraint getLoadingConstraint() { return loadingConstraint; }

    // Time window getters and validation
    public boolean hasTimeWindow() { return hasTimeWindow; }
    public int getEarliestDeliveryHour() { return earliestDeliveryHour; }
    public int getLatestDeliveryHour() { return latestDeliveryHour; }

    /**
     * Check if arrival time falls within the delivery time window.
     * Returns true if no time window constraint or if within window.
     */
    public boolean isWithinTimeWindow() {
        if (!hasTimeWindow) return true;
        int arrivalHour = (int)(arrivalTime / 3600) % 24;
        return arrivalHour >= earliestDeliveryHour && arrivalHour <= latestDeliveryHour;
    }

    /**
     * Get the earliest feasible departure time to meet time window.
     * Returns adjusted departure time in seconds from midnight.
     */
    public long getEarliestFeasibleDeparture(double avgSpeedKmh) {
        if (!hasTimeWindow) return departureTime;
        long travelTimeSec = calculateTravelTime(avgSpeedKmh);
        // Earliest arrival should be at earliestDeliveryHour
        long earliestArrivalSec = earliestDeliveryHour * 3600L;
        return Math.max(0, earliestArrivalSec - travelTimeSec);
    }
    
    /**
     * Format time in seconds to HH:MM:SS.
     */
    public static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    @Override
    public String toString() {
        return String.format("TruckTrip[%d: truck=%d, %s, %.2fkm, cargo=%s, goods=%s]",
                           tripId, truckId, 
                           cargoLoaded ? "DELIVERY" : "EMPTY",
                           distanceKm, cargoLoaded, goodsType);
    }
}
