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
 * Unit tests for FleetFactory — heterogeneous truck fleet generation.
 *
 * <p>Uses a small test config (fleet.size=100) and minimal stubs for spatial subsystems.
 * GeoValidator without loaded data gracefully degrades (assumes all points on land),
 * so FleetFactory falls through to zone-centroid home locations. This is acceptable
 * for verifying fleet composition, type constraints, and ID sequencing.
 */
class FleetFactoryTest {

    private static TruckConfig config;
    private FleetFactory factory;
    private List<TruckAgent> fleet;

    @BeforeAll
    static void loadConfig() throws Exception {
        config = TruckConfig.getInstance();
        // Load test config with small fleet size (100 trucks)
        config.loadFromFile("src/test/resources/test_truck_config.properties");
    }

    @BeforeEach
    void setUp() {
        Random random = new Random(42);  // deterministic seed

        // Create 3 test zones (radius-based, no polygons)
        List<DeliveryZone> zones = Arrays.asList(
            new DeliveryZone("MFS01", "Tokyo-Central",
                139.7671, 35.6812, 10.0, 0.5, 0.3, 0.1, 0.1),
            new DeliveryZone("MFS02", "Yokohama",
                139.6222, 35.4659, 15.0, 0.4, 0.4, 0.1, 0.1),
            new DeliveryZone("MFS03", "Chiba",
                140.1233, 35.6131, 12.0, 0.3, 0.3, 0.2, 0.2)
        );

        // O-D matrix with 3 zones; no raw flows → all 0.0 → FleetFactory uses 1.0 fallback
        OriginDestinationMatrix odMatrix = new OriginDestinationMatrix(3);

        // Minimal subsystems (spatial data not loaded — graceful degradation)
        GeoValidator geoValidator = new GeoValidator();
        PointGenerator pointGenerator = new PointGenerator(geoValidator);
        POIManager poiManager = new POIManager();

        ZoneManager zoneManager = new ZoneManager(config);
        zoneManager.setZones(zones);

        factory = new FleetFactory(config, random, zones, odMatrix,
            poiManager, pointGenerator, zoneManager);

        fleet = factory.createFleet();
    }

    // ════════════════════════════════════════════════════════════════
    // FLEET CREATION — BASIC
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createFleet() returns correct number of trucks (100)")
    void testFleetSize() {
        assertEquals(100, fleet.size());
    }

    @Test
    @DisplayName("Every truck has a valid type")
    void testAllTrucksHaveValidType() {
        for (TruckAgent truck : fleet) {
            assertNotNull(truck.getTruckType(),
                "Truck " + truck.getTruckId() + " should have a type");
            assertTrue(
                truck.getTruckType() == TruckType.DELIVERY ||
                truck.getTruckType() == TruckType.LONG_HAUL ||
                truck.getTruckType() == TruckType.MIXED_OPERATION,
                "Truck type should be one of the three defined types");
        }
    }

    @Test
    @DisplayName("Every truck has positive capacity")
    void testAllTrucksHavePositiveCapacity() {
        for (TruckAgent truck : fleet) {
            assertTrue(truck.getCapacityTons() > 0,
                "Truck " + truck.getTruckId() + " capacity: " + truck.getCapacityTons());
        }
    }

    @Test
    @DisplayName("Every truck has a non-null vehicle size")
    void testAllTrucksHaveVehicleSize() {
        for (TruckAgent truck : fleet) {
            String size = truck.getVehicleSize();
            assertNotNull(size, "Truck " + truck.getTruckId() + " has null vehicle size");
            assertTrue(
                "light".equals(size) || "small".equals(size) ||
                "medium".equals(size) || "heavy".equals(size),
                "Truck " + truck.getTruckId() + " has invalid size: " + size);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // VEHICLE SIZE CONSTRAINTS BY TYPE
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELIVERY trucks only get light or small vehicles")
    void testDeliveryVehicleSizeConstraint() {
        for (TruckAgent truck : fleet) {
            if (truck.getTruckType() == TruckType.DELIVERY) {
                assertTrue(
                    "light".equals(truck.getVehicleSize()) ||
                    "small".equals(truck.getVehicleSize()),
                    "DELIVERY truck " + truck.getTruckId() + " got: " + truck.getVehicleSize());
            }
        }
    }

    @Test
    @DisplayName("LONG_HAUL trucks only get medium or heavy vehicles")
    void testLongHaulVehicleSizeConstraint() {
        for (TruckAgent truck : fleet) {
            if (truck.getTruckType() == TruckType.LONG_HAUL) {
                assertTrue(
                    "medium".equals(truck.getVehicleSize()) ||
                    "heavy".equals(truck.getVehicleSize()),
                    "LONG_HAUL truck " + truck.getTruckId() + " got: " + truck.getVehicleSize());
            }
        }
    }

    @Test
    @DisplayName("MIXED_OPERATION trucks only get small or medium vehicles")
    void testMixedVehicleSizeConstraint() {
        for (TruckAgent truck : fleet) {
            if (truck.getTruckType() == TruckType.MIXED_OPERATION) {
                assertTrue(
                    "small".equals(truck.getVehicleSize()) ||
                    "medium".equals(truck.getVehicleSize()),
                    "MIXED_OPERATION truck " + truck.getTruckId() + " got: " + truck.getVehicleSize());
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TYPE DISTRIBUTION
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Fleet has all three truck types represented")
    void testAllTypesPresent() {
        long deliveryCount = fleet.stream()
            .filter(t -> t.getTruckType() == TruckType.DELIVERY).count();
        long longHaulCount = fleet.stream()
            .filter(t -> t.getTruckType() == TruckType.LONG_HAUL).count();
        long mixedCount = fleet.stream()
            .filter(t -> t.getTruckType() == TruckType.MIXED_OPERATION).count();

        assertTrue(deliveryCount > 0, "Should have DELIVERY trucks");
        assertTrue(longHaulCount > 0, "Should have LONG_HAUL trucks");
        assertTrue(mixedCount > 0, "Should have MIXED_OPERATION trucks");
        assertEquals(100, deliveryCount + longHaulCount + mixedCount);
    }

    @Test
    @DisplayName("DELIVERY trucks are the majority (~55% configured)")
    void testDeliveryIsMajority() {
        long deliveryCount = fleet.stream()
            .filter(t -> t.getTruckType() == TruckType.DELIVERY).count();
        // With 55% probability and 100 trucks, expect roughly 40-70
        assertTrue(deliveryCount >= 35 && deliveryCount <= 75,
            "DELIVERY count " + deliveryCount + " should be ~55% of 100");
    }

    // ════════════════════════════════════════════════════════════════
    // HOME LOCATION
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("All trucks have home coordinates in Tokyo metro region")
    void testHomeLocationsWithinBounds() {
        for (TruckAgent truck : fleet) {
            double lon = truck.getHomeLongitude();
            double lat = truck.getHomeLatitude();

            // Zones centered around 139.6-140.1 E, 35.4-35.7 N
            // Allow generous margin for radius-based zone point generation
            assertTrue(lon > 138.0 && lon < 142.0,
                "Truck " + truck.getTruckId() + " home lon out of range: " + lon);
            assertTrue(lat > 34.0 && lat < 37.0,
                "Truck " + truck.getTruckId() + " home lat out of range: " + lat);
        }
    }

    @Test
    @DisplayName("Truck IDs are sequential starting from 0")
    void testSequentialTruckIds() {
        for (int i = 0; i < fleet.size(); i++) {
            assertEquals(i, fleet.get(i).getTruckId(),
                "Truck at index " + i + " should have ID " + i);
        }
    }

    @Test
    @DisplayName("DELIVERY trucks have familiar area zone ID set")
    void testDeliveryTrucksHaveFamiliarAreaZone() {
        for (TruckAgent truck : fleet) {
            if (truck.getTruckType() == TruckType.DELIVERY) {
                assertNotNull(truck.getFamiliarAreaZoneId(),
                    "DELIVERY truck " + truck.getTruckId() +
                    " should have familiar area zone ID");
            }
        }
    }
}
