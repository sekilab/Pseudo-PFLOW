package truck.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DestinationResult — bundles destination coords + metadata.
 */
class DestinationResultTest {

    @Test
    @DisplayName("Full constructor preserves all fields")
    void testFullConstructor() {
        double[] coords = {139.7671, 35.6812};
        DestinationResult result = new DestinationResult(coords, "MFS01", "POI_042");

        assertArrayEquals(coords, result.coords);
        assertEquals("MFS01", result.zoneId);
        assertEquals("POI_042", result.poiId);
    }

    @Test
    @DisplayName("coordsOnly() sets null zone and POI")
    void testCoordsOnlyFactory() {
        double[] coords = {139.5, 35.5};
        DestinationResult result = DestinationResult.coordsOnly(coords);

        assertArrayEquals(coords, result.coords);
        assertNull(result.zoneId);
        assertNull(result.poiId);
    }

    @Test
    @DisplayName("withZone() sets zone but null POI")
    void testWithZoneFactory() {
        double[] coords = {140.0, 35.6};
        DestinationResult result = DestinationResult.withZone(coords, "MFS03");

        assertArrayEquals(coords, result.coords);
        assertEquals("MFS03", result.zoneId);
        assertNull(result.poiId);
    }

    @Test
    @DisplayName("Coordinates array is not defensively copied")
    void testCoordsAreSharedReference() {
        double[] coords = {139.0, 35.0};
        DestinationResult result = new DestinationResult(coords, null, null);

        // Verify it's the same array reference (not a copy)
        assertSame(coords, result.coords);
    }
}
