package truck.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TruckAgent class.
 */
class TruckAgentTest {

    /** Helper: create a TruckAgent with the full 10-arg constructor using sensible defaults. */
    private static TruckAgent createAgent(int id, TruckType type, String size, double capacity) {
        return new TruckAgent(id, type,
            139.77, 35.68,     // home lon/lat (Tokyo Station)
            34.0,              // familiar area radius km
            size, capacity,
            "General",         // primary goods type
            21600L,            // shift start (06:00)
            10);               // shift duration hours
    }

    @Test
    @DisplayName("Create DELIVERY truck with heavy capacity")
    void testCreateDeliveryTruck() {
        TruckAgent truck = createAgent(1, TruckType.DELIVERY, "heavy", 10.0);

        assertEquals(1, truck.getTruckId());
        assertEquals(TruckType.DELIVERY, truck.getTruckType());
        assertEquals("heavy", truck.getVehicleSize());
        assertEquals(10.0, truck.getCapacityTons(), 0.01);
    }

    @Test
    @DisplayName("Create LONG_HAUL truck with medium capacity")
    void testCreateLongHaulTruck() {
        TruckAgent truck = createAgent(2, TruckType.LONG_HAUL, "medium", 7.0);

        assertEquals(2, truck.getTruckId());
        assertEquals(TruckType.LONG_HAUL, truck.getTruckType());
        assertEquals("medium", truck.getVehicleSize());
        assertEquals(7.0, truck.getCapacityTons(), 0.01);
    }

    @Test
    @DisplayName("Create MIXED_OPERATION truck with small capacity")
    void testCreateMixedTruck() {
        TruckAgent truck = createAgent(3, TruckType.MIXED_OPERATION, "small", 3.0);

        assertEquals(3, truck.getTruckId());
        assertEquals(TruckType.MIXED_OPERATION, truck.getTruckType());
        assertEquals("small", truck.getVehicleSize());
        assertEquals(3.0, truck.getCapacityTons(), 0.01);
    }

    @Test
    @DisplayName("Create light vehicle truck")
    void testCreateLightTruck() {
        TruckAgent truck = createAgent(4, TruckType.DELIVERY, "light", 1.5);

        assertEquals(4, truck.getTruckId());
        assertEquals("light", truck.getVehicleSize());
        assertEquals(1.5, truck.getCapacityTons(), 0.01);
    }

    @Test
    @DisplayName("Truck ID should be unique")
    void testUniqueTruckIds() {
        TruckAgent truck1 = createAgent(1, TruckType.DELIVERY, "heavy", 10.0);
        TruckAgent truck2 = createAgent(2, TruckType.DELIVERY, "heavy", 10.0);

        assertNotEquals(truck1.getTruckId(), truck2.getTruckId());
    }

    @Test
    @DisplayName("Vehicle capacity should be positive")
    void testPositiveCapacity() {
        TruckAgent truck = createAgent(1, TruckType.DELIVERY, "heavy", 10.0);

        assertTrue(truck.getCapacityTons() > 0,
            "Truck capacity should be positive");
    }

    @Test
    @DisplayName("Heavy trucks should have capacity >= 10 tons")
    void testHeavyTruckCapacity() {
        TruckAgent truck = createAgent(1, TruckType.LONG_HAUL, "heavy", 12.0);

        assertTrue(truck.getCapacityTons() >= 10.0,
            "Heavy trucks should have capacity >= 10 tons");
    }

    @Test
    @DisplayName("Medium trucks should have capacity between 4-10 tons")
    void testMediumTruckCapacity() {
        TruckAgent truck = createAgent(1, TruckType.MIXED_OPERATION, "medium", 7.0);

        assertTrue(truck.getCapacityTons() >= 4.0 && truck.getCapacityTons() <= 10.0,
            "Medium trucks should have capacity between 4-10 tons");
    }

    @Test
    @DisplayName("Small trucks should have capacity between 2-4 tons")
    void testSmallTruckCapacity() {
        TruckAgent truck = createAgent(1, TruckType.DELIVERY, "small", 3.0);

        assertTrue(truck.getCapacityTons() >= 2.0 && truck.getCapacityTons() <= 4.0,
            "Small trucks should have capacity between 2-4 tons");
    }

    @Test
    @DisplayName("Light trucks should have capacity < 2 tons")
    void testLightTruckCapacity() {
        TruckAgent truck = createAgent(1, TruckType.DELIVERY, "light", 1.5);

        assertTrue(truck.getCapacityTons() < 2.0,
            "Light trucks should have capacity < 2 tons");
    }

    @Test
    @DisplayName("Truck type should not be null")
    void testNonNullTruckType() {
        TruckAgent truck = createAgent(1, TruckType.DELIVERY, "heavy", 10.0);

        assertNotNull(truck.getTruckType(),
            "Truck type should not be null");
    }

    @Test
    @DisplayName("Vehicle size should not be null")
    void testNonNullVehicleSize() {
        TruckAgent truck = createAgent(1, TruckType.DELIVERY, "heavy", 10.0);

        assertNotNull(truck.getVehicleSize(),
            "Vehicle size should not be null");
    }
}
