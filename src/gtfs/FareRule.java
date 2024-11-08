package gtfs;

public class FareRule {
    private String fareId;
    private String routeId;
    private String originId;
    private String destinationId;
    private String containsId;

    // Constructor
    public FareRule(String fareId, String routeId, String originId, String destinationId, String containsId) {
        this.fareId = fareId;
        this.routeId = routeId;
        this.originId = originId;
        this.destinationId = destinationId;
        this.containsId = containsId;
    }

    public FareRule(){

    }

    // Getters and Setters
    public String getFareId() {
        return fareId;
    }

    public void setFareId(String fareId) {
        this.fareId = fareId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getOriginId() {
        return originId;
    }

    public void setOriginId(String originZone) {
        this.originId = originZone;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationZone) {
        this.destinationId = destinationZone;
    }
}

