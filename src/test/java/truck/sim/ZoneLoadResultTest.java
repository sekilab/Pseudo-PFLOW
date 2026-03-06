package truck.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import truck.sim.spatial.PointGenerator;
import truck.sim.spatial.TransportNetworkIndex;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for ZoneLoadResult — bundles 10 subsystems from zone init.
 */
class ZoneLoadResultTest {

    @Test
    @DisplayName("Constructor preserves all field references")
    void testConstructorFields() {
        List<DeliveryZone> zones = Collections.emptyList();

        ZoneLoadResult result = new ZoneLoadResult(
            zones, null, null, null, null, null, null, null, null, null);

        assertSame(zones, result.deliveryZones);
        assertNull(result.odMatrix);
        assertNull(result.commodityRouter);
        assertNull(result.tripGenerator);
        assertNull(result.poiManager);
        assertNull(result.gaBalancer);
        assertNull(result.metricsTracker);
        assertNull(result.destinationSelector);
        assertNull(result.pointGenerator);
        assertNull(result.networkIndex);
    }

    @Test
    @DisplayName("All fields are publicly accessible (no getters needed)")
    void testPublicFields() {
        List<DeliveryZone> zones = Arrays.asList(
            new DeliveryZone("Z1", "Zone1", 139.0, 35.0, 5.0, 0.25, 0.25, 0.25, 0.25)
        );

        ZoneLoadResult result = new ZoneLoadResult(
            zones, null, null, null, null, null, null, null, null, null);

        assertEquals(1, result.deliveryZones.size());
        assertEquals("Z1", result.deliveryZones.get(0).getZoneId());
    }
}
