package gtfs;

public class Route {
    private String routeId;
    private String routeName;
    private String routeType;

    // Default Constructor
    public Route() {}

    // Constructor with all fields
    public Route(String routeId, String routeName, String routeType) {
        this.routeId = routeId;
        this.routeName = routeName;
        this.routeType = routeType;
    }

    // Getters and Setters
    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
    }
}

