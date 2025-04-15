package gtfs;

import jp.ac.ut.csis.pflow.routing2.res.Network;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GeoUtils {

    private static final double EARTH_RADIUS = 6371; // Radius of the Earth in kilometers

    // Method to calculate distance between two points (lat1, lon1) and (lat2, lon2)
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                Math.pow(Math.sin(dLon / 2), 2) *
                        Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // Returns distance in meters
    }

    // Method to find the nearest stop
    public static Stop findNearestStop(List<Stop> stops, double lat, double lon) {
        Stop nearestStop = null;
        double minDistance = Double.MAX_VALUE;

        for (Stop stop : stops) {
            double distance = haversine(lat, lon, stop.getLatitude(), stop.getLongitude());
            if (distance < minDistance) {
                minDistance = distance;
                nearestStop = stop;
            }
        }
        return nearestStop;
    }

    public static List<Stop> findNearestStops(List<Stop> stops, double lat, double lon, int numberOfResults) {
        double maxDistanceInMeters = 0.5; //km

        return stops.stream()
                .filter(stop -> haversine(stop.getLatitude(), stop.getLongitude(), lat, lon) <= maxDistanceInMeters)
                .sorted(Comparator.comparingDouble(stop -> haversine(stop.getLatitude(), stop.getLongitude(), lat, lon)))
                .limit(numberOfResults)
                .collect(Collectors.toList());
    }

    public static long calculateWalkingTime(double lat1, double lon1, double lat2, double lon2) {
        double distanceInMeters = haversine(lat1, lon1, lat2, lon2) * 1000;
        double walkingSpeed = 1.3;
        return Math.round(distanceInMeters / walkingSpeed / 60);
    }

}

