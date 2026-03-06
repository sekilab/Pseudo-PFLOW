package truck.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeliveryTripResult — typed return from generateDeliveryTrip().
 */
class DeliveryTripResultTest {

    /** Helper: create a minimal TruckTrip for testing. */
    private static TruckTrip createTestTrip() {
        return new TruckTrip(1L, 0,
            139.77, 35.68, 139.62, 35.47,
            0L, 27.5, "General", "medium", 3.0, 7.0, 15.0, 10.0);
    }

    @Test
    @DisplayName("Full constructor preserves all fields")
    void testFullConstructor() {
        TruckTrip trip = createTestTrip();
        DeliveryTripResult result = new DeliveryTripResult(trip, 3600000L, true, "POI_001");

        assertSame(trip, result.trip);
        assertEquals(3600000L, result.updatedTime);
        assertTrue(result.isInterMetro);
        assertEquals("POI_001", result.destPOIId);
    }

    @Test
    @DisplayName("Constructor allows null trip")
    void testNullTrip() {
        DeliveryTripResult result = new DeliveryTripResult(null, 1000L, false, null);

        assertNull(result.trip);
        assertEquals(1000L, result.updatedTime);
        assertFalse(result.isInterMetro);
        assertNull(result.destPOIId);
    }

    @Test
    @DisplayName("atCapacity() creates result with null trip")
    void testAtCapacityFactory() {
        DeliveryTripResult result = DeliveryTripResult.atCapacity(5000L);

        assertNull(result.trip, "At-capacity result should have null trip");
        assertEquals(5000L, result.updatedTime);
        assertFalse(result.isInterMetro);
        assertNull(result.destPOIId);
    }

    @Test
    @DisplayName("atCapacity() preserves the current time")
    void testAtCapacityPreservesTime() {
        long time = 7200000L;
        DeliveryTripResult result = DeliveryTripResult.atCapacity(time);
        assertEquals(time, result.updatedTime);
    }
}
