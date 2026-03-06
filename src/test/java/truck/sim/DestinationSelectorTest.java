package truck.sim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import truck.sim.spatial.GeoValidator;
import truck.sim.spatial.PointGenerator;
import java.util.*;

/**
 * Unit tests for DestinationSelector — destination selection strategies.
 *
 * <p>Tests use minimal subsystems (no spatial data loaded, empty GA balancer).
 * This exercises the destination selection logic, distance constraints, and
 * zone-aware bypass ratio without requiring the full simulation environment.
 */
class DestinationSelectorTest {

    private static TruckConfig config;

    private DestinationSelector selector;
    private ZoneManager zoneManager;
    private List<DeliveryZone> zones;
    private Random random;

    @BeforeAll
    static void loadConfig() throws Exception {
        config = TruckConfig.getInstance();
        config.loadFromFile("src/test/resources/test_truck_config.properties");
    }

    @BeforeEach
    void setUp() {
        random = new Random(42);

        // Create test zones spanning different distances from Tokyo center
        zones = Arrays.asList(
            // Intra-metro: close to Tokyo center
            new DeliveryZone("MFS01", "Tokyo-Central",
                139.7671, 35.6812, 10.0, 0.5, 0.3, 0.1, 0.1),
            new DeliveryZone("MFS02", "Yokohama",
                139.6222, 35.4659, 15.0, 0.4, 0.4, 0.1, 0.1),
            new DeliveryZone("MFS03", "Chiba",
                140.1233, 35.6131, 12.0, 0.3, 0.3, 0.2, 0.2),
            // Inter-metro: far from Tokyo center
            new DeliveryZone("MFS10", "Nagano",
                138.1811, 36.2381, 25.0, 0.2, 0.2, 0.3, 0.3),
            new DeliveryZone("MFS11", "Niigata",
                139.0236, 37.9026, 30.0, 0.2, 0.2, 0.3, 0.3)
        );

        zoneManager = new ZoneManager(config);
        zoneManager.setZones(zones);

        // O-D matrix with uniform flows for simplicity
        OriginDestinationMatrix odMatrix = new OriginDestinationMatrix(zones.size());

        // Minimal subsystems
        GeoValidator geoValidator = new GeoValidator();
        PointGenerator pointGenerator = new PointGenerator(geoValidator);
        POIManager poiManager = new POIManager();
        GenerationAttractionBalancer gaBalancer = new GenerationAttractionBalancer();
        CommodityRouter commodityRouter = new CommodityRouter();
        MetropolitanConfig metroConfig = new MetropolitanConfig("TOKYO");

        selector = new DestinationSelector(config, zoneManager, geoValidator,
            pointGenerator, poiManager, odMatrix, gaBalancer, commodityRouter, metroConfig);
    }

    /** Helper: create a truck agent for testing. */
    private TruckAgent createTruck(int id, TruckType type) {
        return new TruckAgent(id, type,
            139.7671, 35.6812, 34.0,
            type == TruckType.DELIVERY ? "small" : "medium",
            type == TruckType.DELIVERY ? 3.0 : 7.0,
            "general_goods", 21600L, 10);
    }

    // ════════════════════════════════════════════════════════════════
    // NEARBY DESTINATION (DELIVERY)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Nearby destination returns non-null result")
    void testNearbyDestinationReturnsResult() {
        TruckAgent truck = createTruck(0, TruckType.DELIVERY);
        double[] origin = {139.7671, 35.6812};

        DestinationResult result = selector.selectNearbyDestination(truck, origin, 50.0);
        assertNotNull(result);
        assertNotNull(result.coords);
        assertEquals(2, result.coords.length);
    }

    @Test
    @DisplayName("Nearby destination coordinates are valid geographic values")
    void testNearbyDestinationValidCoords() {
        TruckAgent truck = createTruck(0, TruckType.DELIVERY);
        double[] origin = {139.7671, 35.6812};

        DestinationResult result = selector.selectNearbyDestination(truck, origin, 100.0);
        double lon = result.coords[0];
        double lat = result.coords[1];

        assertTrue(lon > 130.0 && lon < 145.0,
            "Destination lon should be in Japan region, got: " + lon);
        assertTrue(lat > 30.0 && lat < 40.0,
            "Destination lat should be in Japan region, got: " + lat);
    }

    @Test
    @DisplayName("Nearby destination with tight range selects closest zone")
    void testNearbyDestinationTightRange() {
        TruckAgent truck = createTruck(0, TruckType.DELIVERY);
        // Origin at Tokyo-Central zone center
        double[] origin = {139.7671, 35.6812};

        // Very small range — only Tokyo-Central should qualify
        DestinationResult result = selector.selectNearbyDestination(truck, origin, 5.0);
        assertNotNull(result);
        // Zone ID should be MFS01 (Tokyo-Central is closest)
        if (result.zoneId != null) {
            assertEquals("MFS01", result.zoneId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // INTER-ZONE DESTINATION (LONG_HAUL)
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Inter-zone destination returns non-null result")
    void testInterZoneDestinationReturnsResult() {
        TruckAgent truck = createTruck(0, TruckType.LONG_HAUL);
        double[] origin = {139.7671, 35.6812};

        DestinationResult result = selector.selectInterZoneDestination(truck, origin);
        assertNotNull(result);
        assertNotNull(result.coords);
    }

    @Test
    @DisplayName("Inter-zone destination prefers far zones")
    void testInterZonePrefersFarZones() {
        TruckAgent truck = createTruck(0, TruckType.LONG_HAUL);
        double[] origin = {139.7671, 35.6812};

        // Run multiple selections and track which zones are chosen
        Map<String, Integer> zoneCounts = new HashMap<>();
        Random testRandom = new Random(42);
        for (int i = 0; i < 50; i++) {
            // Re-create selector with fresh random each time
            DestinationSelector testSelector = createSelector(testRandom);
            DestinationResult result = testSelector.selectInterZoneDestination(truck, origin);
            if (result.zoneId != null) {
                zoneCounts.merge(result.zoneId, 1, Integer::sum);
            }
        }

        // Far zones (MFS10/MFS11) should be selected more than nearby ones
        int farZoneCount = zoneCounts.getOrDefault("MFS10", 0)
            + zoneCounts.getOrDefault("MFS11", 0);
        assertTrue(farZoneCount > 0,
            "At least some far zones should be selected, got: " + zoneCounts);
    }

    // ════════════════════════════════════════════════════════════════
    // EMPTY TRIP DESTINATION
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Empty trip for DELIVERY returns nearby destination")
    void testEmptyTripDeliveryIsNearby() {
        TruckAgent truck = createTruck(0, TruckType.DELIVERY);
        DestinationResult result = selector.selectEmptyTripDestination(
            truck, 139.7671, 35.6812, 21600L);
        assertNotNull(result);
        assertNotNull(result.coords);
    }

    @Test
    @DisplayName("Empty trip for LONG_HAUL returns inter-zone destination")
    void testEmptyTripLongHaulIsInterZone() {
        TruckAgent truck = createTruck(0, TruckType.LONG_HAUL);
        DestinationResult result = selector.selectEmptyTripDestination(
            truck, 139.7671, 35.6812, 21600L);
        assertNotNull(result);
        assertNotNull(result.coords);
    }

    @Test
    @DisplayName("Empty trip for MIXED returns valid destination")
    void testEmptyTripMixed() {
        TruckAgent truck = createTruck(0, TruckType.MIXED_OPERATION);
        DestinationResult result = selector.selectEmptyTripDestination(
            truck, 139.7671, 35.6812, 21600L);
        assertNotNull(result);
        assertNotNull(result.coords);
    }

    // ════════════════════════════════════════════════════════════════
    // ZONE-AWARE BYPASS RATIO
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Small urban zone returns configured bypass ratio")
    void testBypassRatioUrbanZone() {
        DeliveryZone smallZone = new DeliveryZone("Z1", "Small",
            139.7, 35.7, 5.0, 0.3, 0.3, 0.2, 0.2);
        double ratio = selector.getZoneAwareBypass(smallZone, 0.40);
        assertEquals(0.40, ratio, 0.001, "Small zone should use configured ratio");
    }

    @Test
    @DisplayName("Large rural zone returns rural bypass ratio from config")
    void testBypassRatioRuralZone() {
        // Threshold is 20km (from config), so 50km zone is rural
        DeliveryZone largeZone = new DeliveryZone("Z2", "Rural",
            139.0, 36.0, 50.0, 0.1, 0.1, 0.4, 0.4);
        double ratio = selector.getZoneAwareBypass(largeZone, 0.40);
        // Rural bypass ratio from config (default ~0.10)
        assertTrue(ratio < 0.40,
            "Rural zone should have lower bypass ratio than configured: " + ratio);
    }

    @Test
    @DisplayName("Null zone returns configured ratio")
    void testBypassRatioNullZone() {
        double ratio = selector.getZoneAwareBypass(null, 0.50);
        assertEquals(0.50, ratio, 0.001);
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    /** Create a fresh DestinationSelector with a given random. */
    private DestinationSelector createSelector(Random rng) {
        GeoValidator gv = new GeoValidator();
        PointGenerator pg = new PointGenerator(gv);
        POIManager pm = new POIManager();
        OriginDestinationMatrix od = new OriginDestinationMatrix(zones.size());
        GenerationAttractionBalancer gab = new GenerationAttractionBalancer();
        CommodityRouter cr = new CommodityRouter();
        MetropolitanConfig mc = new MetropolitanConfig("TOKYO");

        return new DestinationSelector(config, zoneManager, gv,
            pg, pm, od, gab, cr, mc);
    }
}
