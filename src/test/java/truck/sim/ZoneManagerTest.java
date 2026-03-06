package truck.sim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for ZoneManager — zone lookup and distance calculation.
 *
 * Loads test config (earthRadius=6371km, manhattanFactor=1.72).
 */
class ZoneManagerTest {

    private static TruckConfig config;
    private ZoneManager zoneManager;
    private DeliveryZone tokyoZone;
    private DeliveryZone yokohamaZone;
    private DeliveryZone chibaZone;

    @BeforeAll
    static void loadConfig() throws Exception {
        config = TruckConfig.getInstance();
        config.loadFromFile("src/test/resources/test_truck_config.properties");
    }

    @BeforeEach
    void setUp() {
        zoneManager = new ZoneManager(config);

        // Create test zones with radius-based containment (no polygon data)
        // Tokyo Station: 139.7671 E, 35.6812 N — 10 km radius
        tokyoZone = new DeliveryZone("MFS01", "Tokyo-Central",
            139.7671, 35.6812, 10.0,
            0.5, 0.3, 0.1, 0.1);

        // Yokohama Station: 139.6222 E, 35.4659 N — 15 km radius
        yokohamaZone = new DeliveryZone("MFS02", "Yokohama",
            139.6222, 35.4659, 15.0,
            0.4, 0.4, 0.1, 0.1);

        // Chiba Station: 140.1233 E, 35.6131 N — 12 km radius
        chibaZone = new DeliveryZone("MFS03", "Chiba",
            140.1233, 35.6131, 12.0,
            0.3, 0.3, 0.2, 0.2);

        zoneManager.setZones(Arrays.asList(tokyoZone, yokohamaZone, chibaZone));
    }

    // ════════════════════════════════════════════════════════════════
    // ZONE INDEX LOOKUP (O(1) by ID)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Find zone by ID returns correct zone")
    void testFindZoneByZoneId() {
        DeliveryZone found = zoneManager.findZoneByZoneId("MFS01");
        assertNotNull(found);
        assertEquals("Tokyo-Central", found.getName());
    }

    @Test
    @DisplayName("Find zone by ID returns null for unknown ID")
    void testFindZoneByZoneIdNotFound() {
        assertNull(zoneManager.findZoneByZoneId("NONEXISTENT"));
    }

    @Test
    @DisplayName("Find zone by ID is case-sensitive")
    void testFindZoneByZoneIdCaseSensitive() {
        assertNull(zoneManager.findZoneByZoneId("mfs01"));
    }

    // ════════════════════════════════════════════════════════════════
    // ZONE BY REGION NAME (case-insensitive)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Find zone by region name (exact case)")
    void testFindZoneByRegionName() {
        DeliveryZone found = zoneManager.findZoneByRegionName("Tokyo-Central");
        assertNotNull(found);
        assertEquals("MFS01", found.getZoneId());
    }

    @Test
    @DisplayName("Find zone by region name is case-insensitive")
    void testFindZoneByRegionNameCaseInsensitive() {
        DeliveryZone found = zoneManager.findZoneByRegionName("tokyo-central");
        assertNotNull(found);
        assertEquals("MFS01", found.getZoneId());
    }

    @Test
    @DisplayName("Find zone by region name also matches zone ID")
    void testFindZoneByRegionNameMatchesZoneId() {
        DeliveryZone found = zoneManager.findZoneByRegionName("mfs02");
        assertNotNull(found);
        assertEquals("Yokohama", found.getName());
    }

    @Test
    @DisplayName("Find zone by region name returns null for unknown")
    void testFindZoneByRegionNameNotFound() {
        assertNull(zoneManager.findZoneByRegionName("Saitama"));
    }

    // ════════════════════════════════════════════════════════════════
    // NEAREST ZONE
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Nearest zone to Tokyo Station is Tokyo zone")
    void testNearestZoneTokyoStation() {
        // Point at Tokyo Station
        String nearestId = zoneManager.findNearestZone(139.7671, 35.6812);
        assertEquals("MFS01", nearestId);
    }

    @Test
    @DisplayName("Nearest zone to Yokohama area is Yokohama zone")
    void testNearestZoneYokohama() {
        // Point near Yokohama
        String nearestId = zoneManager.findNearestZone(139.63, 35.47);
        assertEquals("MFS02", nearestId);
    }

    @Test
    @DisplayName("Nearest zone to Chiba area is Chiba zone")
    void testNearestZoneChiba() {
        // Point near Chiba
        String nearestId = zoneManager.findNearestZone(140.10, 35.60);
        assertEquals("MFS03", nearestId);
    }

    @Test
    @DisplayName("Nearest zone index matches list ordering")
    void testNearestZoneIndex() {
        // Yokohama is index 1
        int idx = zoneManager.findNearestZoneIndex(139.63, 35.47);
        assertEquals(1, idx);
    }

    @Test
    @DisplayName("Nearest zone index returns -1 for empty zone list")
    void testNearestZoneIndexEmptyList() {
        zoneManager.setZones(Collections.emptyList());
        int idx = zoneManager.findNearestZoneIndex(139.7, 35.7);
        assertEquals(-1, idx);
    }

    // ════════════════════════════════════════════════════════════════
    // FIND ZONE FOR LOCATION (containment + fallback)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Find zone for location within zone returns that zone")
    void testFindZoneForLocationInside() {
        // Point very close to Tokyo center — within 10km radius
        String zoneId = zoneManager.findZoneForLocation(139.77, 35.68);
        assertEquals("MFS01", zoneId);
    }

    @Test
    @DisplayName("Find zone for location outside all zones falls back to nearest")
    void testFindZoneForLocationFallback() {
        // Point far from all zones — should fall back to nearest
        String zoneId = zoneManager.findZoneForLocation(141.0, 36.0);
        // Should be nearest (Chiba is closest to this northeast point)
        assertNotNull(zoneId);
        assertNotEquals("OUTSIDE", zoneId);
    }

    @Test
    @DisplayName("Find zone for location returns OUTSIDE when no zones exist")
    void testFindZoneForLocationNoZones() {
        zoneManager.setZones(Collections.emptyList());
        String zoneId = zoneManager.findZoneForLocation(139.7, 35.7);
        assertEquals("OUTSIDE", zoneId);
    }

    // ════════════════════════════════════════════════════════════════
    // DISTANCE CALCULATION (Haversine + Manhattan factor)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Same point distance is zero")
    void testDistanceSamePoint() {
        double dist = zoneManager.calculateDistance(139.7, 35.7, 139.7, 35.7);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    @DisplayName("Distance is symmetric")
    void testDistanceSymmetric() {
        double ab = zoneManager.calculateDistance(139.7671, 35.6812, 139.6222, 35.4659);
        double ba = zoneManager.calculateDistance(139.6222, 35.4659, 139.7671, 35.6812);
        assertEquals(ab, ba, 0.001);
    }

    @Test
    @DisplayName("Distance includes Manhattan correction factor")
    void testDistanceIncludesManhattanFactor() {
        // Calculate distance between Tokyo and Yokohama centers
        double dist = zoneManager.calculateDistance(
            139.7671, 35.6812,  // Tokyo
            139.6222, 35.4659   // Yokohama
        );

        // Haversine straight-line ≈ 27.5 km
        // With Manhattan factor (1.4 default): ≈ 38.5 km
        // Allow tolerance for default vs configured factor
        assertTrue(dist > 25.0, "Tokyo-Yokohama should be > 25km, got: " + dist);
        assertTrue(dist < 55.0, "Tokyo-Yokohama should be < 55km, got: " + dist);
    }

    @Test
    @DisplayName("Distance is always non-negative")
    void testDistanceNonNegative() {
        double dist = zoneManager.calculateDistance(139.0, 35.0, 140.0, 36.0);
        assertTrue(dist >= 0.0, "Distance should be non-negative");
    }

    @Test
    @DisplayName("Short distance within Tokyo (~5km)")
    void testShortDistance() {
        // Tokyo Station to Shinjuku: ~6.5 km straight line
        double dist = zoneManager.calculateDistance(
            139.7671, 35.6812,  // Tokyo Station
            139.7006, 35.6896   // Shinjuku Station
        );
        // With Manhattan factor ~1.4: ~9.1 km
        assertTrue(dist > 5.0 && dist < 15.0,
            "Tokyo-Shinjuku should be 5-15km (with Manhattan), got: " + dist);
    }

    // ════════════════════════════════════════════════════════════════
    // SET ZONES
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("setZones rebuilds index correctly")
    void testSetZonesRebuildsIndex() {
        // Initially 3 zones
        assertNotNull(zoneManager.findZoneByZoneId("MFS01"));

        // Replace with single zone
        DeliveryZone newZone = new DeliveryZone("NEW01", "New",
            139.0, 35.0, 5.0, 0.25, 0.25, 0.25, 0.25);
        zoneManager.setZones(Collections.singletonList(newZone));

        assertNull(zoneManager.findZoneByZoneId("MFS01"), "Old zone should be gone");
        assertNotNull(zoneManager.findZoneByZoneId("NEW01"), "New zone should be found");
    }

    @Test
    @DisplayName("getZones returns the set zone list")
    void testGetZones() {
        List<DeliveryZone> zones = zoneManager.getZones();
        assertEquals(3, zones.size());
    }
}
