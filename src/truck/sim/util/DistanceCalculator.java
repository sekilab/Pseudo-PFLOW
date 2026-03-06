package truck.sim.util;

import truck.sim.TruckSimulationConstants;

/**
 * Utility class for geographic distance calculations.
 *
 * Provides Haversine formula implementation for calculating great-circle
 * distance between two points on Earth's surface.
 */
public final class DistanceCalculator {

    // Prevent instantiation
    private DistanceCalculator() {}

    /**
     * Calculate great-circle distance between two points using Haversine formula.
     *
     * <p>The Haversine formula determines the great-circle distance between two points
     * on a sphere given their longitudes and latitudes. This is accurate for most
     * purposes, with errors typically less than 0.5% due to Earth's ellipsoidal shape.
     *
     * @param lat1 Latitude of point 1 in degrees (-90 to 90)
     * @param lon1 Longitude of point 1 in degrees (-180 to 180)
     * @param lat2 Latitude of point 2 in degrees (-90 to 90)
     * @param lon2 Longitude of point 2 in degrees (-180 to 180)
     * @return Distance in kilometers
     *
     * @see <a href="https://en.wikipedia.org/wiki/Haversine_formula">Haversine formula</a>
     */
    public static double calculateDistance(
            double lat1, double lon1, double lat2, double lon2) {

        // Convert latitude and longitude differences to radians
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        // Haversine formula
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) *
                   Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Distance = radius × central angle
        return TruckSimulationConstants.EARTH_RADIUS_KM * c;
    }

    /**
     * Calculate network distance by applying Manhattan factor to straight-line distance.
     *
     * <p>In urban environments, actual road network distance is typically 1.5-2.5x
     * the straight-line distance due to street grid patterns. Tokyo's calibrated
     * factor is 2.25.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Network distance in kilometers
     */
    public static double calculateNetworkDistance(
            double lat1, double lon1, double lat2, double lon2) {
        double straightLineDistance = calculateDistance(lat1, lon1, lat2, lon2);
        return straightLineDistance * TruckSimulationConstants.MANHATTAN_FACTOR;
    }
}
