package truck.sim.spatial;

import org.locationtech.jts.geom.*;

/**
 * Value object representing a rail segment from the rail network shapefile.
 *
 * Stores rail attributes for future multi-modal facility analysis.
 * Currently loaded but not actively used - reserved for Phase 3 enhancements.
 *
 * Attributes from raill_jpn.shp:
 * - f_code: Rail classification type
 * - exs: Existence/operational status
 * - fco: Function code
 * - loc: Location type
 */
public class RailSegment {
    private final LineString geometry;
    private final String fCode;           // Rail classification
    private final int existence;          // exs attribute
    private final int functionCode;       // fco attribute
    private final int location;           // loc attribute

    /**
     * Create a rail segment with attributes.
     *
     * @param fCode Rail classification code
     * @param exs Existence/operational status
     * @param fco Function code
     * @param loc Location type
     * @param geom LineString geometry of the rail segment
     */
    public RailSegment(String fCode, int exs, int fco, int loc, LineString geom) {
        this.fCode = fCode;
        this.existence = exs;
        this.functionCode = fco;
        this.location = loc;
        this.geometry = geom;
    }

    // Getters

    public LineString getGeometry() {
        return geometry;
    }

    public String getFCode() {
        return fCode;
    }

    public int getExistence() {
        return existence;
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public int getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return String.format("RailSegment[fCode=%s, exs=%d, fco=%d, loc=%d]",
            fCode, existence, functionCode, location);
    }
}
