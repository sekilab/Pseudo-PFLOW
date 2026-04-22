package truck.sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import util.PathResolver;

/**
 * Loads MFS File 08 generation/attraction totals from
 * {@code config/truck/flows/ga_targets.csv}.
 *
 * <p>Schema:
 * {@code zone_id, generated_tons, generated_trucks, attracted_tons,
 * attracted_trucks, facility_type}
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link FleetFactory#buildZoneWeightArrays} — uses
 *     {@link #getGeneratedTrucks(String)} as the zone fleet seeding weight
 *     (replaces tonnage from {@link OriginDestinationMatrix#getRawOutflowTotal}).
 *   <li>{@code mfs/extract_phase4_zone_cargo_gamma.py} (offline twin) —
 *     same data, used to derive per-zone Gamma cargo weight parameters.
 * </ul>
 *
 * <p>Resolves audit finding F0084: zone fleet seeding was tons-weighted
 * but should be truck-weighted (the trucks are what we're allocating).
 */
public class GATargetsLoader {

    /** One row from ga_targets.csv. */
    public static final class Row {
        public final String zoneId;
        public final double generatedTons;
        public final int generatedTrucks;
        public final double attractedTons;
        public final int attractedTrucks;
        public final String facilityType;

        Row(String zoneId, double generatedTons, int generatedTrucks,
            double attractedTons, int attractedTrucks, String facilityType) {
            this.zoneId = zoneId;
            this.generatedTons = generatedTons;
            this.generatedTrucks = generatedTrucks;
            this.attractedTons = attractedTons;
            this.attractedTrucks = attractedTrucks;
            this.facilityType = facilityType;
        }
    }

    private final Map<String, Row> rowsByZoneId;

    private GATargetsLoader(Map<String, Row> rows) {
        this.rowsByZoneId = Collections.unmodifiableMap(rows);
    }

    /**
     * Load from the standard config location.
     * Path is resolved via {@link PathResolver#resolve} so it works on
     * Windows and macOS without env-var setup.
     */
    public static GATargetsLoader loadDefault() throws IOException {
        return loadFromCsv(PathResolver.resolve(
            "${PFLOW_HOME}/Pseudo-PFLOW/config/truck/flows/ga_targets.csv"));
    }

    public static GATargetsLoader loadFromCsv(String path) throws IOException {
        Map<String, Row> rows = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String header = br.readLine();
            if (header == null) {
                throw new IOException("ga_targets.csv is empty");
            }
            String line;
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 6) {
                    System.err.println("[GATargetsLoader] Skipping malformed "
                        + "line " + lineNo + ": " + line);
                    continue;
                }
                try {
                    String zoneId = parts[0].trim();
                    double genTons = Double.parseDouble(parts[1].trim());
                    int genTrucks = (int) Math.round(Double.parseDouble(parts[2].trim()));
                    double attTons = Double.parseDouble(parts[3].trim());
                    int attTrucks = (int) Math.round(Double.parseDouble(parts[4].trim()));
                    String facility = parts[5].trim();
                    rows.put(zoneId, new Row(zoneId, genTons, genTrucks,
                        attTons, attTrucks, facility));
                } catch (NumberFormatException e) {
                    System.err.println("[GATargetsLoader] Non-numeric on line "
                        + lineNo + " (" + e.getMessage() + "): " + line);
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IOException("ga_targets.csv had no usable rows: " + path);
        }
        System.out.println("[GATargetsLoader] Loaded " + rows.size()
            + " zones from " + path);
        return new GATargetsLoader(rows);
    }

    /** @return generated trucks/day for this zone, or 0 if unknown. */
    public int getGeneratedTrucks(String zoneId) {
        Row r = rowsByZoneId.get(zoneId);
        return r == null ? 0 : r.generatedTrucks;
    }

    public double getGeneratedTons(String zoneId) {
        Row r = rowsByZoneId.get(zoneId);
        return r == null ? 0.0 : r.generatedTons;
    }

    public int getAttractedTrucks(String zoneId) {
        Row r = rowsByZoneId.get(zoneId);
        return r == null ? 0 : r.attractedTrucks;
    }

    public double getAttractedTons(String zoneId) {
        Row r = rowsByZoneId.get(zoneId);
        return r == null ? 0.0 : r.attractedTons;
    }

    /** @return mean tons per truck for this zone (gen+att aggregated), or 0 if no truck data. */
    public double getMeanTonsPerTruck(String zoneId) {
        Row r = rowsByZoneId.get(zoneId);
        if (r == null) return 0.0;
        long totalTrucks = (long) r.generatedTrucks + r.attractedTrucks;
        if (totalTrucks == 0) return 0.0;
        return (r.generatedTons + r.attractedTons) / totalTrucks;
    }

    public Set<String> zoneIds() {
        return rowsByZoneId.keySet();
    }

    public int size() {
        return rowsByZoneId.size();
    }

    /** Smoke test — loads the default file and prints a summary. */
    public static void main(String[] args) throws IOException {
        GATargetsLoader loader = loadDefault();
        System.out.println("Loaded " + loader.size() + " zones");
        long totalGenTrucks = 0;
        for (String zid : loader.zoneIds()) {
            totalGenTrucks += loader.getGeneratedTrucks(zid);
        }
        System.out.println("Total generated trucks: " + totalGenTrucks);
        System.out.println("MFS01 generated trucks: "
            + loader.getGeneratedTrucks("MFS01")
            + " (expect 59628)");
        System.out.println("MFS01 mean tons/truck: "
            + String.format("%.3f", loader.getMeanTonsPerTruck("MFS01"))
            + " (expect ~6.19)");
        System.out.println("MFS62 generated trucks: "
            + loader.getGeneratedTrucks("MFS62")
            + " (expect 0)");
        System.out.println("Unknown zone returns: "
            + loader.getGeneratedTrucks("MFS999")
            + " (expect 0)");
    }
}
