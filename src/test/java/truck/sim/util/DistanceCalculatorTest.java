package truck.sim.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DistanceCalculator utility class.
 *
 * Tests the Haversine distance calculation formula for geographic coordinates.
 */
class DistanceCalculatorTest {

    private static final double TOLERANCE = 1.0; // 1 km tolerance for real-world distances

    @Test
    @DisplayName("Calculate distance between Tokyo and Osaka (~ 400km)")
    void testTokyoOsakaDistance() {
        // Tokyo Station: 35.6762° N, 139.6503° E
        // Osaka Station: 34.6937° N, 135.5023° E
        double distance = DistanceCalculator.calculateDistance(
            35.6762, 139.6503,
            34.6937, 135.5023
        );

        // Expected distance is approximately 400-410 km
        assertTrue(distance >= 390 && distance <= 420,
            "Tokyo-Osaka distance should be ~400km, got: " + distance);
    }

    @Test
    @DisplayName("Same point distance should be zero")
    void testSamePointDistance() {
        double distance = DistanceCalculator.calculateDistance(
            35.6762, 139.6503,
            35.6762, 139.6503
        );

        assertEquals(0.0, distance, 0.001,
            "Distance from point to itself should be 0");
    }

    @Test
    @DisplayName("Distance should be symmetric (A to B == B to A)")
    void testDistanceSymmetry() {
        double lat1 = 35.6762, lon1 = 139.6503; // Tokyo
        double lat2 = 34.6937, lon2 = 135.5023; // Osaka

        double distanceAB = DistanceCalculator.calculateDistance(lat1, lon1, lat2, lon2);
        double distanceBA = DistanceCalculator.calculateDistance(lat2, lon2, lat1, lon1);

        assertEquals(distanceAB, distanceBA, 0.001,
            "Distance should be symmetric");
    }

    @Test
    @DisplayName("Calculate distance along equator (1 degree longitude)")
    void testEquatorDistance() {
        // At equator, 1 degree longitude ≈ 111 km
        double distance = DistanceCalculator.calculateDistance(
            0.0, 0.0,  // Equator, Prime Meridian
            0.0, 1.0   // Equator, 1 degree east
        );

        assertTrue(distance >= 110 && distance <= 112,
            "1 degree at equator should be ~111km, got: " + distance);
    }

    @Test
    @DisplayName("Calculate distance along prime meridian (1 degree latitude)")
    void testPrimeMeridianDistance() {
        // 1 degree latitude ≈ 111 km everywhere
        double distance = DistanceCalculator.calculateDistance(
            0.0, 0.0,  // Equator, Prime Meridian
            1.0, 0.0   // 1 degree north
        );

        assertTrue(distance >= 110 && distance <= 112,
            "1 degree latitude should be ~111km, got: " + distance);
    }

    @Test
    @DisplayName("North pole to south pole distance (~20,000 km)")
    void testPoleToPoleDistance() {
        // North pole to south pole via prime meridian
        double distance = DistanceCalculator.calculateDistance(
            90.0, 0.0,   // North Pole
            -90.0, 0.0   // South Pole
        );

        // Half Earth's circumference ≈ 20,000 km
        assertTrue(distance >= 19900 && distance <= 20100,
            "Pole-to-pole distance should be ~20,000km, got: " + distance);
    }

    @Test
    @DisplayName("Date line crossing (180° longitude)")
    void testDateLineCrossing() {
        // Point just west of date line to point just east
        double distance = DistanceCalculator.calculateDistance(
            0.0, 179.0,  // Just west of date line
            0.0, -179.0  // Just east of date line
        );

        // Should be ~222 km (2 degrees at equator)
        assertTrue(distance >= 220 && distance <= 224,
            "Date line crossing should be ~222km, got: " + distance);
    }

    @Test
    @DisplayName("Short distance in Tokyo (< 10 km)")
    void testShortDistanceInTokyo() {
        // Tokyo Station to Shinjuku Station
        double distance = DistanceCalculator.calculateDistance(
            35.6762, 139.6503,  // Tokyo Station
            35.6896, 139.7006   // Shinjuku Station
        );

        // Straight-line Haversine: ~4.8 km (coordinates are closer than expected)
        assertTrue(distance >= 4 && distance <= 10,
            "Tokyo-Shinjuku distance should be ~4-8km, got: " + distance);
    }

    @Test
    @DisplayName("Network distance includes Manhattan factor")
    void testNetworkDistance() {
        double straightLine = DistanceCalculator.calculateDistance(
            35.6762, 139.6503,
            35.6896, 139.7006
        );

        double networkDist = DistanceCalculator.calculateNetworkDistance(
            35.6762, 139.6503,
            35.6896, 139.7006
        );

        // Network distance should be 1.72x straight line (MANHATTAN_FACTOR)
        assertEquals(straightLine * 1.72, networkDist, 0.01,
            "Network distance should be 1.72x straight line distance");
    }

    @Test
    @DisplayName("Negative coordinates (Southern/Western hemispheres)")
    void testNegativeCoordinates() {
        // Sydney (-33.8688° S, 151.2093° E)
        // Buenos Aires (-34.6037° S, -58.3816° W)
        double distance = DistanceCalculator.calculateDistance(
            -33.8688, 151.2093,
            -34.6037, -58.3816
        );

        // Expected: ~11,800 km
        assertTrue(distance >= 11700 && distance <= 11900,
            "Sydney-Buenos Aires distance should be ~11,800km, got: " + distance);
    }

    @Test
    @DisplayName("Zero longitude distance")
    void testZeroLongitudeDistance() {
        double distance = DistanceCalculator.calculateDistance(
            0.0, 0.0,
            1.0, 1.0
        );

        assertTrue(distance > 0,
            "Distance should be positive for different coordinates");
    }

    @Test
    @DisplayName("Very small distance (meters)")
    void testVerySmallDistance() {
        // Two points 100 meters apart (approximately)
        double distance = DistanceCalculator.calculateDistance(
            35.6762, 139.6503,
            35.6771, 139.6503  // 0.0009 degrees north ≈ 100m
        );

        assertTrue(distance >= 0.09 && distance <= 0.11,
            "100m distance should be ~0.1km, got: " + distance);
    }
}
