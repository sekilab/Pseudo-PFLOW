package taxi.sim;

/**
 * Represents a single taxi trip (either with passenger or empty) with origin-destination information.
 *
 * V3.1 ENHANCEMENT - EMPTY TRIP SUPPORT:
 * - Added support for empty trips (repositioning/deadhead trips)
 * - Added passengerIn flag to distinguish passenger vs empty trips
 * - Empty trips have zero fare but still consume time and distance
 * - Enables traffic volume analysis including both occupied and empty taxis
 *
 * FARE CALCULATION FOR TOKYO:
 * ---------------------------
 * Based on official Tokyo taxi fares (as of 2024):
 *
 * DAYTIME RATES (05:00-22:00):
 * - Base fare: ¥500 (includes first 1.096 km)
 * - Distance-based: ¥100 per 255m after base distance
 * - Effective rate: ¥392/km (approximately ¥100/255m = ¥392/km)
 *
 * ALTERNATIVE CALCULATION (used in this simulation for simplicity):
 * - Base fare: ¥730 (includes first 2.0 km)
 * - Per kilometer: ¥320/km after 2.0 km
 * - Waiting/standing time: ¥3,085/hour (when speed < 10 km/h)
 *
 * NIGHT SURCHARGE (22:00-05:00):
 * - All fares increased by 20%
 *
 * V3.0 ENHANCEMENT:
 * - ALL fare constants now configurable through TaxiConfig
 * - No hardcoded values remain
 * - Night hours check uses TaxiConfig
 * - Distance calculation uses configurable Earth radius and Manhattan factor
 */
public class TaxiTrip {

    // Trip identification
    private final int tripId;
    private int assignedTaxiId;

    // Spatiotemporal information
    private double pickupLongitude;
    private double pickupLatitude;
    private double dropoffLongitude;
    private double dropoffLatitude;

    // Time information (in seconds since midnight)
    private long requestTime;
    private long pickupTime;
    private long dropoffTime;

    // Trip characteristics
    private double distanceKm;
    private double fareYen;
    private boolean isNightTrip;  // true if trip occurs during night hours
    private boolean passengerIn;  // V3.1: true if passenger onboard, false if empty trip

    // Status
    private TripStatus status;

    /**
     * Trip status enumeration
     *
     * CURRENT IMPLEMENTATION (V3.1):
     * - REQUESTED: Used for passenger trips [ACTIVE]
     * - ASSIGNED: Reserved for future use [RESERVED]
     * - EN_ROUTE: Reserved for future use [RESERVED]
     * - IN_PROGRESS: Used for empty trips [ACTIVE]
     * - COMPLETED: Used for all trips at end [ACTIVE]
     * - CANCELLED: Reserved for future use [RESERVED]
     */
    public enum TripStatus {
        REQUESTED,      // Trip requested by passenger (only for passenger trips) [ACTIVE]
        ASSIGNED,       // Assigned to a taxi [RESERVED]
        EN_ROUTE,       // Taxi en route to pickup (only for passenger trips) [RESERVED]
        IN_PROGRESS,    // Passenger onboard OR empty repositioning in progress [ACTIVE]
        COMPLETED,      // Trip completed [ACTIVE]
        CANCELLED       // Trip cancelled [RESERVED]
    }

    /**
     * Constructor for passenger trip (with fare)
     * @param tripId Unique trip identifier
     * @param pickupLon Pickup longitude
     * @param pickupLat Pickup latitude
     * @param dropoffLon Dropoff longitude
     * @param dropoffLat Dropoff latitude
     * @param requestTime When trip was requested (seconds since midnight)
     */
    public TaxiTrip(int tripId, double pickupLon, double pickupLat,
                    double dropoffLon, double dropoffLat, long requestTime) {
        this(tripId, pickupLon, pickupLat, dropoffLon, dropoffLat, requestTime, true);
    }

    /**
     * Constructor for trip with passenger flag
     * @param tripId Unique trip identifier
     * @param pickupLon Pickup longitude
     * @param pickupLat Pickup latitude
     * @param dropoffLon Dropoff longitude
     * @param dropoffLat Dropoff latitude
     * @param requestTime When trip was requested (seconds since midnight)
     * @param passengerIn True if passenger trip, false if empty trip
     */
    public TaxiTrip(int tripId, double pickupLon, double pickupLat,
                    double dropoffLon, double dropoffLat, long requestTime, boolean passengerIn) {
        this.tripId = tripId;
        this.pickupLongitude = pickupLon;
        this.pickupLatitude = pickupLat;
        this.dropoffLongitude = dropoffLon;
        this.dropoffLatitude = dropoffLat;
        this.requestTime = requestTime;
        this.passengerIn = passengerIn;

        this.status = passengerIn ? TripStatus.REQUESTED : TripStatus.IN_PROGRESS;
        this.assignedTaxiId = -1;

        // Calculate distance using Haversine formula
        this.distanceKm = calculateDistance(pickupLon, pickupLat,
            dropoffLon, dropoffLat);

        // Determine if night trip using TaxiConfig
        TaxiConfig config = TaxiConfig.getInstance();
        this.isNightTrip = config.isNightHours(requestTime);

        // Calculate fare (zero for empty trips)
        this.fareYen = passengerIn ? calculateFare(distanceKm, 0, isNightTrip) : 0.0;
    }

    /**
     * Create an empty trip (repositioning/deadhead trip)
     * @param tripId Unique trip identifier
     * @param startLon Starting longitude
     * @param startLat Starting latitude
     * @param endLon Ending longitude
     * @param endLat Ending latitude
     * @param startTime When trip starts (seconds since midnight)
     * @return TaxiTrip instance representing empty trip
     */
    public static TaxiTrip createEmptyTrip(int tripId, double startLon, double startLat,
                                           double endLon, double endLat, long startTime) {
        return new TaxiTrip(tripId, startLon, startLat, endLon, endLat, startTime, false);
    }

    /**
     * Calculate distance between two points using Haversine formula
     * Uses configurable Earth radius and Manhattan factor from TaxiConfig
     *
     * @param lon1 Origin longitude
     * @param lat1 Origin latitude
     * @param lon2 Destination longitude
     * @param lat2 Destination latitude
     * @return Distance in kilometers
     */
    private double calculateDistance(double lon1, double lat1,
                                     double lon2, double lat2) {
        TaxiConfig config = TaxiConfig.getInstance();
        final double EARTH_RADIUS_KM = config.getEarthRadiusKm();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Multiply by Manhattan factor to approximate road network distance
        // (straight-line distance × Manhattan factor)
        return EARTH_RADIUS_KM * c * config.getManhattanFactor();
    }

    /**
     * Calculate fare based on Tokyo taxi pricing structure
     * Uses configurable fare parameters from TaxiConfig
     *
     * OFFICIAL TOKYO TAXI FARE FORMULA:
     * - Base: ¥730 for first 2.0 km (configurable)
     * - Additional: ¥320 per km after 2.0 km (configurable)
     * - Night surcharge: +20% between 22:00-05:00 (configurable)
     * - Waiting/standing: ¥3,085/hour (not implemented in basic simulation)
     *
     * @param distanceKm Trip distance in kilometers
     * @param waitingMinutes Time spent waiting/in traffic (for future enhancement)
     * @param isNight Whether trip occurs during night hours
     * @return Fare in Japanese Yen
     */
    public static double calculateFare(double distanceKm, double waitingMinutes,
                                       boolean isNight) {
        TaxiConfig config = TaxiConfig.getInstance();
        double fare = 0.0;

        // Base fare (covers first X km - configurable)
        fare += config.getTaxiFareBase();

        // Distance-based fare (after base distance)
        if (distanceKm > config.getTaxiFareBaseDistance()) {
            double additionalDistance = distanceKm - config.getTaxiFareBaseDistance();
            fare += additionalDistance * config.getTaxiFarePerKm();
        }

        // Waiting/standing time fare (for future enhancement)
        // Currently not used, but ready for time-step simulation
        if (waitingMinutes > 0) {
            fare += (waitingMinutes / 60.0) * config.getTaxiFareWaitingPerHour();
        }

        // Night surcharge (configurable hours and multiplier)
        if (isNight) {
            fare *= config.getTaxiFareNightSurcharge();
        }

        // Round to nearest 10 yen (common practice in Japan)
        fare = Math.round(fare / 10.0) * 10.0;

        return fare;
    }

    /**
     * Estimate travel time based on average taxi speed in Tokyo
     * Uses configurable average speed from TaxiConfig
     *
     * @param distanceKm Distance in kilometers
     * @return Travel time in seconds
     */
    public static long estimateTravelTime(double distanceKm) {
        TaxiConfig config = TaxiConfig.getInstance();
        double avgSpeedKmh = config.getTaxiAverageSpeed();
        return (long) ((distanceKm / avgSpeedKmh) * 3600);
    }

    /**
     * Set pickup and dropoff times based on taxi assignment
     * @param pickup Pickup time (seconds since midnight)
     * @param dropoff Dropoff time (seconds since midnight)
     */
    public void setTimes(long pickup, long dropoff) {
        this.pickupTime = pickup;
        this.dropoffTime = dropoff;
    }

    /**
     * Set assigned taxi ID
     * @param taxiId Taxi ID
     */
    public void setAssignedTaxiId(int taxiId) {
        this.assignedTaxiId = taxiId;
    }

    // ==================== Getters and Setters ====================

    public int getTripId() {
        return tripId;
    }

    public int getAssignedTaxiId() {
        return assignedTaxiId;
    }

    public double getPickupLongitude() {
        return pickupLongitude;
    }

    public double getPickupLatitude() {
        return pickupLatitude;
    }

    public double getDropoffLongitude() {
        return dropoffLongitude;
    }

    public double getDropoffLatitude() {
        return dropoffLatitude;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public long getPickupTime() {
        return pickupTime;
    }

    public long getDropoffTime() {
        return dropoffTime;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getFareYen() {
        return fareYen;
    }

    public boolean isNightTrip() {
        return isNightTrip;
    }

    public boolean isPassengerIn() {
        return passengerIn;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        String tripType = passengerIn ? "PASSENGER" : "EMPTY";
        return String.format("Trip %d (%s): %.4f,%.4f -> %.4f,%.4f | %.2f km | ¥%.0f%s",
            tripId, tripType, pickupLongitude, pickupLatitude,
            dropoffLongitude, dropoffLatitude,
            distanceKm, fareYen, isNightTrip ? " (NIGHT)" : "");
    }
}
