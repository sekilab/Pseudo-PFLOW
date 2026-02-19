package truck.sim.util;

import truck.sim.DeliveryZone;
import dcity.aggr.Boundaries;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Maps MFS delivery zones to shapefile administrative boundaries.
 *
 * Loads zone_boundary_mapping.csv and associates DeliveryZone objects
 * with corresponding boundary polygons from the shapefile.
 *
 * Phase 2 - Polygon-Based Zone Architecture
 */
public class ZoneBoundaryMapper {

    // zone_id -> list of adm_codes
    private Map<String, List<String>> zoneToAdmCodes;

    // adm_code -> geometry (loaded from shapefile)
    private Map<String, Geometry> admCodeToGeometry;

    // adm_code -> SimpleFeature (for metadata access)
    private Map<String, SimpleFeature> admCodeToFeature;

    public ZoneBoundaryMapper() {
        this.zoneToAdmCodes = new HashMap<>();
        this.admCodeToGeometry = new HashMap<>();
        this.admCodeToFeature = new HashMap<>();
    }

    /**
     * Load zone-to-administrative code mapping from CSV.
     *
     * @param csvPath Path to zone_boundary_mapping.csv
     * @throws IOException If file cannot be read
     */
    public void loadMapping(String csvPath) throws IOException {
        System.out.println("[ZONE MAPPER] Loading zone-to-boundary mapping from: " + csvPath);

        int mappingCount = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean headerSkipped = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Skip header
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                // Parse CSV line: zone_id,adm_codes,notes
                String[] parts = line.split(",", 3);
                if (parts.length < 2) {
                    continue;
                }

                String zoneId = parts[0].trim();
                String admCodesStr = parts[1].trim();

                // Remove quotes if present
                admCodesStr = admCodesStr.replaceAll("^\"|\"$", "");

                // Split adm_codes by semicolon
                String[] codes = admCodesStr.split(";");
                List<String> admCodes = new ArrayList<>();
                for (String code : codes) {
                    String trimmedCode = code.trim();
                    if (!trimmedCode.isEmpty()) {
                        admCodes.add(trimmedCode);
                    }
                }

                if (!admCodes.isEmpty()) {
                    zoneToAdmCodes.put(zoneId, admCodes);
                    mappingCount++;
                }
            }
        }

        System.out.println("[ZONE MAPPER] Loaded " + mappingCount + " zone-to-boundary mappings");
    }

    /**
     * Load boundary geometries from shapefile.
     * Builds adm_code -> geometry index for fast lookup.
     *
     * @param shapefilePath Path to shapefile
     */
    public void loadBoundaryGeometries(String shapefilePath) {
        System.out.println("[ZONE MAPPER] Indexing boundary geometries by adm_code...");

        try {
            List<SimpleFeature> features = dcity.aggr.ShpLoader.load(shapefilePath);
            int indexed = 0;

            for (SimpleFeature feature : features) {
                // Get adm_code attribute
                Object admCodeObj = feature.getAttribute("adm_code");
                if (admCodeObj == null) {
                    continue;
                }

                String admCode = admCodeObj.toString().trim();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null && !admCode.isEmpty()) {
                    admCodeToGeometry.put(admCode, geom);
                    admCodeToFeature.put(admCode, feature);
                    indexed++;
                }
            }

            System.out.println("[ZONE MAPPER] Indexed " + indexed + " boundary geometries by adm_code");
        } catch (Exception e) {
            System.err.println("[ZONE MAPPER] Error loading shapefile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Associate DeliveryZone objects with boundary geometries.
     * Updates zones with polygon boundaries based on mapping.
     *
     * @param zones List of DeliveryZone objects to update
     */
    public void associateZoneBoundaries(List<DeliveryZone> zones) {
        System.out.println("[ZONE MAPPER] Associating zones with boundary polygons...");

        int polygonZones = 0;
        int radiusZones = 0;

        for (DeliveryZone zone : zones) {
            String zoneId = zone.getZoneId();
            List<String> admCodes = zoneToAdmCodes.get(zoneId);

            if (admCodes == null || admCodes.isEmpty()) {
                // No mapping - keep radius-based zone
                radiusZones++;
                continue;
            }

            // Collect boundary geometries for this zone
            List<Geometry> polygons = new ArrayList<>();
            List<String> foundCodes = new ArrayList<>();

            for (String admCode : admCodes) {
                // Check if this is a wildcard pattern (e.g., "01xxx" for all Hokkaido municipalities)
                if (admCode.endsWith("xxx")) {
                    // Extract prefecture code prefix (first 2 digits)
                    String prefecturePrefix = admCode.substring(0, 2);

                    // Find all geometries matching this prefecture
                    int prefectureMatches = 0;
                    for (Map.Entry<String, Geometry> entry : admCodeToGeometry.entrySet()) {
                        String code = entry.getKey();
                        if (code.startsWith(prefecturePrefix)) {
                            polygons.add(entry.getValue());
                            foundCodes.add(code);
                            prefectureMatches++;
                        }
                    }

                    if (prefectureMatches > 0) {
                        System.out.println("[ZONE MAPPER]   Pattern '" + admCode +
                            "' matched " + prefectureMatches + " municipalities");
                    } else {
                        System.err.println("[ZONE MAPPER] Warning: Pattern '" + admCode +
                            "' matched no municipalities for zone " + zoneId);
                    }
                } else {
                    // Exact match
                    Geometry geom = admCodeToGeometry.get(admCode);
                    if (geom != null) {
                        polygons.add(geom);
                        foundCodes.add(admCode);
                    } else {
                        System.err.println("[ZONE MAPPER] Warning: adm_code '" + admCode +
                            "' not found in shapefile for zone " + zoneId);
                    }
                }
            }

            if (!polygons.isEmpty()) {
                // Set polygon boundaries for this zone
                zone.setBoundaryPolygons(polygons);
                zone.setAdministrativeCodes(foundCodes);
                polygonZones++;

                // For large zones, show summary instead of all codes
                if (foundCodes.size() <= 10) {
                    System.out.println("[ZONE MAPPER] " + zoneId + " (" + zone.getName() +
                        ") mapped to " + polygons.size() + " boundary polygon(s): " +
                        String.join(", ", foundCodes));
                } else {
                    System.out.println("[ZONE MAPPER] " + zoneId + " (" + zone.getName() +
                        ") mapped to " + polygons.size() + " boundary polygon(s) from " +
                        admCodes.size() + " pattern(s)");
                }
            } else {
                // No geometries found - keep radius-based
                radiusZones++;
                System.err.println("[ZONE MAPPER] Warning: No geometries found for zone " +
                    zoneId + " - using radius-based fallback");
            }
        }

        System.out.println("[ZONE MAPPER] Zone association complete: " +
            polygonZones + " polygon-based, " + radiusZones + " radius-based");
    }

    /**
     * Get geometry for a specific administrative code.
     *
     * @param admCode Administrative code
     * @return Geometry or null if not found
     */
    public Geometry getGeometryByAdmCode(String admCode) {
        return admCodeToGeometry.get(admCode);
    }

    /**
     * Get feature for a specific administrative code.
     *
     * @param admCode Administrative code
     * @return SimpleFeature or null if not found
     */
    public SimpleFeature getFeatureByAdmCode(String admCode) {
        return admCodeToFeature.get(admCode);
    }

    /**
     * Get mapping statistics.
     *
     * @return Map with statistics (zones_mapped, boundaries_indexed)
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("zones_mapped", zoneToAdmCodes.size());
        stats.put("boundaries_indexed", admCodeToGeometry.size());
        return stats;
    }
}
