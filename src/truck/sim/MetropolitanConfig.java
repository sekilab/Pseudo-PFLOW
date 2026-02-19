package truck.sim;

import java.util.*;

/**
 * Metropolitan configuration for multi-city truck operations.
 * 
 * Supports two operational modes:
 * 1. INTRA_METROPOLITAN: Trucks operate within single metro area (Tokyo, Nagoya, Osaka)
 * 2. INTER_METROPOLITAN: Trucks make round trips between metro areas (Tokyo ↔ Nagoya)
 * 
 * Based on Japanese metropolitan freight patterns and Dataset 32 (Interregional flows).
 * 
 * @version 1.0
 */
public class MetropolitanConfig {
    
    /**
     * Metropolitan area definition with geographic bounds.
     */
    public static class Metropolitan {
        public final String id;
        public final String name;
        public final double minLon, maxLon, minLat, maxLat;
        public final double centerLon, centerLat;
        public final int populationMillions;
        public final String zonesFile;
        
        public Metropolitan(String id, String name, 
                          double minLon, double maxLon, 
                          double minLat, double maxLat,
                          int populationMillions, String zonesFile) {
            this.id = id;
            this.name = name;
            this.minLon = minLon;
            this.maxLon = maxLon;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.centerLon = (minLon + maxLon) / 2.0;
            this.centerLat = (minLat + maxLat) / 2.0;
            this.populationMillions = populationMillions;
            this.zonesFile = zonesFile;
        }
        
        public boolean containsPoint(double lon, double lat) {
            return lon >= minLon && lon <= maxLon && 
                   lat >= minLat && lat <= maxLat;
        }
        
        public double getArea() {
            return (maxLon - minLon) * (maxLat - minLat);
        }
    }
    
    /**
     * Operational mode enum.
     */
    public enum OperationalMode {
        INTRA_METROPOLITAN("Intra-Metropolitan", "Operations within single metropolitan area"),
        INTER_METROPOLITAN("Inter-Metropolitan", "Round trips between metropolitan areas");
        
        public final String displayName;
        public final String description;
        
        OperationalMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
    
    /**
     * Inter-metropolitan route definition.
     */
    public static class InterMetroRoute {
        public final Metropolitan origin;
        public final Metropolitan destination;
        public final double distanceKm;
        public final double avgSpeedKmh;
        public final double flowVolumeMultiplier;  // From Dataset 32
        
        public InterMetroRoute(Metropolitan origin, Metropolitan destination,
                              double distanceKm, double avgSpeedKmh, 
                              double flowVolumeMultiplier) {
            this.origin = origin;
            this.destination = destination;
            this.distanceKm = distanceKm;
            this.avgSpeedKmh = avgSpeedKmh;
            this.flowVolumeMultiplier = flowVolumeMultiplier;
        }
        
        public long getTravelTimeSeconds() {
            return (long) (distanceKm / avgSpeedKmh * 3600);
        }
        
        public String getRouteId() {
            return origin.id + "-" + destination.id;
        }
    }
    
    // Predefined metropolitan areas
    private static final Map<String, Metropolitan> METROPOLITAN_AREAS = new HashMap<>();
    
    static {
        // Tokyo Metropolitan Area (Greater Tokyo)
        METROPOLITAN_AREAS.put("TOKYO", new Metropolitan(
            "TOKYO", "Tokyo Metropolitan Area",
            139.3, 140.0,      // Longitude: ~70km width
            35.5, 35.9,        // Latitude: ~45km height
            36,                // 36 million population
            "zones_tokyo_metro.csv"
        ));
        
        // Nagoya Metropolitan Area (Chukyo)
        METROPOLITAN_AREAS.put("NAGOYA", new Metropolitan(
            "NAGOYA", "Nagoya Metropolitan Area",
            136.7, 137.2,      // Longitude
            35.0, 35.4,        // Latitude
            9,                 // 9 million population
            "nagoya_zones.csv"
        ));
        
        // Osaka Metropolitan Area (Keihanshin)
        METROPOLITAN_AREAS.put("OSAKA", new Metropolitan(
            "OSAKA", "Osaka-Kobe-Kyoto Metropolitan Area",
            135.2, 135.8,      // Longitude
            34.5, 35.0,        // Latitude
            19,                // 19 million population
            "osaka_zones.csv"
        ));
        
        // Fukuoka Metropolitan Area
        METROPOLITAN_AREAS.put("FUKUOKA", new Metropolitan(
            "FUKUOKA", "Fukuoka Metropolitan Area",
            130.2, 130.6,      // Longitude
            33.4, 33.7,        // Latitude
            3,                 // 3 million population
            "fukuoka_zones.csv"
        ));
        
        // Sapporo Metropolitan Area
        METROPOLITAN_AREAS.put("SAPPORO", new Metropolitan(
            "SAPPORO", "Sapporo Metropolitan Area",
            141.1, 141.6,      // Longitude
            42.9, 43.2,        // Latitude
            2,                 // 2 million population
            "sapporo_zones.csv"
        ));

        // Inter-Metropolitan Long-Haul Zones (MFS Regions 61-71)
        // For long-distance freight between Tokyo and other regions
        METROPOLITAN_AREAS.put("INTER_METRO", new Metropolitan(
            "INTER_METRO", "Inter-Metropolitan Long-Haul Zones",
            135.0, 145.0,      // Wide longitude range (Hokkaido to Kyushu)
            30.0, 45.0,        // Wide latitude range
            50,                // 50 million total population (all regions)
            "zones_inter_metro.csv"
        ));
    }
    
    // Current configuration
    private OperationalMode mode;
    private Metropolitan primaryMetro;
    private Metropolitan secondaryMetro;  // For INTER_METROPOLITAN mode
    private List<InterMetroRoute> activeRoutes;
    
    /**
     * Constructor for intra-metropolitan mode.
     */
    public MetropolitanConfig(String metropolitanId) {
        this.mode = OperationalMode.INTRA_METROPOLITAN;
        this.primaryMetro = METROPOLITAN_AREAS.get(metropolitanId.toUpperCase());
        
        if (this.primaryMetro == null) {
            throw new IllegalArgumentException("Unknown metropolitan area: " + metropolitanId);
        }
        
        this.secondaryMetro = null;
        this.activeRoutes = new ArrayList<>();
    }
    
    /**
     * Constructor for inter-metropolitan mode.
     */
    public MetropolitanConfig(String primaryMetroId, String secondaryMetroId) {
        this.mode = OperationalMode.INTER_METROPOLITAN;
        this.primaryMetro = METROPOLITAN_AREAS.get(primaryMetroId.toUpperCase());
        this.secondaryMetro = METROPOLITAN_AREAS.get(secondaryMetroId.toUpperCase());
        
        if (this.primaryMetro == null) {
            throw new IllegalArgumentException("Unknown primary metropolitan area: " + primaryMetroId);
        }
        if (this.secondaryMetro == null) {
            throw new IllegalArgumentException("Unknown secondary metropolitan area: " + secondaryMetroId);
        }
        
        // Initialize inter-metropolitan routes
        this.activeRoutes = createInterMetroRoutes();
    }
    
    /**
     * Create inter-metropolitan routes based on known connections.
     * Flow volumes from Dataset 32 (Interregional Flow Volume by Transport Mode).
     */
    private List<InterMetroRoute> createInterMetroRoutes() {
        List<InterMetroRoute> routes = new ArrayList<>();
        
        String routeKey = primaryMetro.id + "-" + secondaryMetro.id;
        
        // Define major inter-metropolitan routes with realistic parameters
        switch (routeKey) {
            case "TOKYO-NAGOYA":
                // Tomei Expressway: 350km, high freight volume
                routes.add(new InterMetroRoute(primaryMetro, secondaryMetro, 
                    350.0, 80.0, 1.8));  // High flow multiplier
                routes.add(new InterMetroRoute(secondaryMetro, primaryMetro, 
                    350.0, 80.0, 1.5));  // Return flow slightly lower
                break;
                
            case "TOKYO-OSAKA":
                // Tokaido corridor: 500km, very high freight volume
                routes.add(new InterMetroRoute(primaryMetro, secondaryMetro, 
                    500.0, 80.0, 2.2));  // Very high flow
                routes.add(new InterMetroRoute(secondaryMetro, primaryMetro, 
                    500.0, 80.0, 2.0));
                break;
                
            case "NAGOYA-OSAKA":
                // Meishin Expressway: 180km, medium-high volume
                routes.add(new InterMetroRoute(primaryMetro, secondaryMetro, 
                    180.0, 80.0, 1.3));
                routes.add(new InterMetroRoute(secondaryMetro, primaryMetro, 
                    180.0, 80.0, 1.2));
                break;
                
            case "TOKYO-FUKUOKA":
                // Very long haul: 1000km, lower volume (more air/rail)
                routes.add(new InterMetroRoute(primaryMetro, secondaryMetro, 
                    1000.0, 75.0, 0.8));
                routes.add(new InterMetroRoute(secondaryMetro, primaryMetro, 
                    1000.0, 75.0, 0.7));
                break;
                
            case "TOKYO-SAPPORO":
                // Very long haul with ferry: 850km + ferry, low truck volume
                routes.add(new InterMetroRoute(primaryMetro, secondaryMetro, 
                    850.0, 60.0, 0.5));
                routes.add(new InterMetroRoute(secondaryMetro, primaryMetro, 
                    850.0, 60.0, 0.4));
                break;
                
            default:
                // Generic inter-metro route
                double distance = calculateDistance(
                    primaryMetro.centerLon, primaryMetro.centerLat,
                    secondaryMetro.centerLon, secondaryMetro.centerLat);
                routes.add(new InterMetroRoute(primaryMetro, secondaryMetro, 
                    distance, 75.0, 1.0));
                routes.add(new InterMetroRoute(secondaryMetro, primaryMetro, 
                    distance, 75.0, 0.9));
                break;
        }
        
        return routes;
    }
    
    /**
     * Calculate Haversine distance between two points.
     */
    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        final double EARTH_RADIUS_KM = 6371.0;
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Get route for inter-metropolitan trip.
     */
    public InterMetroRoute getRoute(Metropolitan from, Metropolitan to) {
        for (InterMetroRoute route : activeRoutes) {
            if (route.origin.equals(from) && route.destination.equals(to)) {
                return route;
            }
        }
        return null;
    }
    
    /**
     * Check if a truck should make an inter-metropolitan trip.
     */
    public boolean shouldMakeInterMetroTrip(Random random, TruckType truckType) {
        if (mode != OperationalMode.INTER_METROPOLITAN) {
            return false;
        }

        // Inter-metro probability by truck type (using default values)
        double probability;
        switch (truckType) {
            case LONG_HAUL:
                probability = 0.60;  // Long haul trucks prefer inter-metro
                break;
            case MIXED_OPERATION:
                probability = 0.25;  // Some urban logistics do inter-metro
                break;
            case DELIVERY:
                probability = 0.10;  // Delivery rarely goes inter-metro
                break;
            default:
                probability = 0.20;
                break;
        }

        return random.nextDouble() < probability;
    }

    /**
     * Check if a truck should make an inter-metropolitan trip (with config).
     */
    public boolean shouldMakeInterMetroTrip(Random random, TruckType truckType, TruckConfig config) {
        if (mode != OperationalMode.INTER_METROPOLITAN) {
            return false;
        }

        // Inter-metro probability by truck type (from config)
        double probability;
        switch (truckType) {
            case LONG_HAUL:
                probability = config.getInterMetroLongHaulProb();
                break;
            case MIXED_OPERATION:
                probability = config.getInterMetroUrbanProb();
                break;
            case DELIVERY:
                probability = config.getInterMetroDeliveryProb();
                break;
            default:
                probability = 0.20;
                break;
        }

        return random.nextDouble() < probability;
    }
    
    /**
     * Get all available metropolitan areas.
     */
    public static Collection<Metropolitan> getAllMetropolitanAreas() {
        return METROPOLITAN_AREAS.values();
    }
    
    /**
     * Get metropolitan area by ID.
     */
    public static Metropolitan getMetropolitan(String id) {
        return METROPOLITAN_AREAS.get(id.toUpperCase());
    }
    
    // Getters
    public OperationalMode getMode() { return mode; }
    public Metropolitan getPrimaryMetro() { return primaryMetro; }
    public Metropolitan getSecondaryMetro() { return secondaryMetro; }
    public List<InterMetroRoute> getActiveRoutes() { return activeRoutes; }
    
    public boolean isIntraMetropolitan() {
        return mode == OperationalMode.INTRA_METROPOLITAN;
    }
    
    public boolean isInterMetropolitan() {
        return mode == OperationalMode.INTER_METROPOLITAN;
    }
    
    /**
     * Get configuration summary string.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Metropolitan Configuration:\n");
        sb.append("  Mode: ").append(mode.displayName).append("\n");
        sb.append("  Primary: ").append(primaryMetro.name).append(" (").append(primaryMetro.populationMillions).append("M population)\n");
        
        if (secondaryMetro != null) {
            sb.append("  Secondary: ").append(secondaryMetro.name).append(" (").append(secondaryMetro.populationMillions).append("M population)\n");
            sb.append("  Active Routes: ").append(activeRoutes.size()).append("\n");
            
            for (InterMetroRoute route : activeRoutes) {
                sb.append("    - ").append(route.origin.name)
                  .append(" → ").append(route.destination.name)
                  .append(": ").append(String.format("%.0f", route.distanceKm)).append(" km")
                  .append(" (flow multiplier: ").append(String.format("%.1f", route.flowVolumeMultiplier)).append(")\n");
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("MetropolitanConfig[%s: %s %s %s]",
            mode, primaryMetro.name,
            secondaryMetro != null ? "↔ " + secondaryMetro.name : "",
            activeRoutes.size() > 0 ? "(" + activeRoutes.size() + " routes)" : "");
    }
}
