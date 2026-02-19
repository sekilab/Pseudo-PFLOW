package truck.sim;

/**
 * Point of Interest (POI) - Discrete destination locations for INTRA-metropolitan trips.
 *
 * POIs represent actual facilities where trucks make deliveries/pickups:
 * - Logistic centers: Distribution hubs, cargo terminals, warehouses
 * - Retail shops: Commercial districts, retail clusters
 * - Shopping malls: Large retail destinations
 *
 * @author Truck ABM Framework
 * @version 2.0
 */
public class PointOfInterest {

    /**
     * POI type classification.
     */
    public enum POIType {
        LOGISTIC_CENTER,    // Distribution hubs, cargo terminals
        RETAIL_SHOP,        // Commercial districts, retail stores
        SHOPPING_MALL,      // Large retail destinations
        INDUSTRIAL_SITE,    // Factories, manufacturing plants
        PORT_TERMINAL       // Ports, freight terminals
    }

    private final String poiId;
    private final String name;
    private final double longitude;
    private final double latitude;
    private final String zoneId;
    private final FacilityType facilityType;
    private final POIType poiType;
    private final String additionalInfo;  // capacity_level, business_type, or size_class

    /**
     * Constructor for POI.
     *
     * @param poiId Unique POI identifier
     * @param name POI name
     * @param longitude Longitude coordinate
     * @param latitude Latitude coordinate
     * @param zoneId Zone ID this POI belongs to
     * @param facilityType Facility type
     * @param poiType POI classification
     * @param additionalInfo Additional info (capacity/business type/size)
     */
    public PointOfInterest(String poiId, String name, double longitude, double latitude,
                          String zoneId, FacilityType facilityType, POIType poiType,
                          String additionalInfo) {
        this.poiId = poiId;
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.zoneId = zoneId;
        this.facilityType = facilityType;
        this.poiType = poiType;
        this.additionalInfo = additionalInfo;
    }

    // Getters
    public String getPoiId() { return poiId; }
    public String getName() { return name; }
    public double getLongitude() { return longitude; }
    public double getLatitude() { return latitude; }
    public String getZoneId() { return zoneId; }
    public FacilityType getFacilityType() { return facilityType; }
    public POIType getPoiType() { return poiType; }
    public String getAdditionalInfo() { return additionalInfo; }

    /**
     * Calculate distance to another point (in km).
     */
    public double distanceTo(double lon, double lat) {
        // Haversine formula
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(lat);
        double deltaLat = Math.toRadians(lat - this.latitude);
        double deltaLon = Math.toRadians(lon - this.longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                  Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                  Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;  // Earth radius in km
    }

    @Override
    public String toString() {
        return String.format("POI[%s, %s, type=%s, zone=%s, (%.4f,%.4f)]",
            poiId, name, poiType, zoneId, longitude, latitude);
    }
}
