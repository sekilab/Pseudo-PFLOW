package gtfs;

public class Trip {
    private String routeId;
    private String tripId;
    private String serviceId;
    private String tripHeadsign;
    private String directionId;
    private String blockId;
    private String shapeId;

    // Constructor with all fields
    public Trip(String routeId, String tripId, String serviceId, String tripHeadsign, String directionId, String blockId, String shapeId) {
        this.routeId = routeId;
        this.tripId = tripId;
        this.serviceId = serviceId;
        this.tripHeadsign = tripHeadsign;
        this.directionId = directionId;
        this.blockId = blockId;
        this.shapeId = shapeId;
    }

    // Getters and Setters
    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public String toString() {
        return "Trip{" +
                "routeId='" + routeId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", tripHeadsign='" + tripHeadsign + '\'' +
                ", directionId='" + directionId + '\'' +
                ", blockId='" + blockId + '\'' +
                ", shapeId='" + shapeId + '\'' +
                '}';
    }

}

