package truck.sim.core;

import truck.sim.DeliveryZone;
import truck.sim.util.CSVLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Component responsible for loading and initializing delivery zones.
 *
 * <p>This component handles all zone-related data loading including:
 * <ul>
 *   <li>Intra-metropolitan delivery zones</li>
 *   <li>Inter-metropolitan delivery zones</li>
 *   <li>Zone mappings (MFS to simulation zones)</li>
 *   <li>Establishment data (retail, logistics, industrial counts)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ZoneInitializer initializer = new ZoneInitializer();
 * List<DeliveryZone> zones = initializer.loadDualModeZones(
 *     "config/zones_intra_metro_combined.csv",
 *     "config/zones_inter_metro.csv"
 * );
 * }</pre>
 *
 * <p>This component is extracted from TruckSimulation.java to improve:
 * <ul>
 *   <li>Testability - Can test zone loading in isolation</li>
 *   <li>Reusability - Can be used across different simulation modes</li>
 *   <li>Maintainability - Single responsibility for zone management</li>
 * </ul>
 *
 * @author Truck ABM Framework
 * @version 2.0
 * @since 2.0
 */
public class ZoneInitializer {

    /**
     * Loads intra-metropolitan delivery zones from CSV file.
     *
     * <p>Expected CSV format:
     * <pre>
     * zone_id,zone_name,center_lat,center_lon,area_km2,...
     * MFS01,Chiyoda,35.6938,139.7536,11.66,...
     * </pre>
     *
     * @param filePath Path to zones CSV file
     * @return List of delivery zones
     * @throws IOException If file cannot be read or parsed
     */
    public List<DeliveryZone> loadIntraMetroZones(String filePath) throws IOException {
        System.out.println("[ZoneInitializer] Loading intra-metro zones from: " + filePath);

        List<DeliveryZone> zones = CSVLoader.loadCSV(filePath, (line, lineNum) -> {
            String[] parts = line.split(",");

            if (parts.length < 15) {
                throw new IllegalArgumentException(
                    "Expected at least 15 columns, got " + parts.length);
            }

            // Parse zone data
            String zoneId = parts[0].trim();
            String zoneName = parts[1].trim();
            double centerLat = Double.parseDouble(parts[2]);
            double centerLon = Double.parseDouble(parts[3]);
            double areaKm2 = Double.parseDouble(parts[4]);

            // Create delivery zone
            DeliveryZone zone = new DeliveryZone(
                zoneId, zoneName, centerLat, centerLon, areaKm2
            );

            // Parse additional zone attributes (population, employment, etc.)
            if (parts.length >= 6) {
                try {
                    zone.setPopulation(Integer.parseInt(parts[5]));
                } catch (NumberFormatException e) {
                    // Skip if not parseable
                }
            }

            return zone;
        });

        System.out.println("[ZoneInitializer] Loaded " + zones.size() + " intra-metro zones");
        return zones;
    }

    /**
     * Loads inter-metropolitan delivery zones from CSV file.
     *
     * <p>Inter-metropolitan zones represent connections to other metropolitan
     * areas outside Tokyo (e.g., Osaka, Nagoya).
     *
     * @param filePath Path to inter-metro zones CSV file
     * @return List of inter-metropolitan delivery zones
     * @throws IOException If file cannot be read or parsed
     */
    public List<DeliveryZone> loadInterMetroZones(String filePath) throws IOException {
        System.out.println("[ZoneInitializer] Loading inter-metro zones from: " + filePath);

        List<DeliveryZone> zones = CSVLoader.loadCSV(filePath, (line, lineNum) -> {
            String[] parts = line.split(",");

            if (parts.length < 5) {
                throw new IllegalArgumentException(
                    "Expected at least 5 columns for inter-metro zones");
            }

            String zoneId = parts[0].trim();
            String zoneName = parts[1].trim();
            double centerLat = Double.parseDouble(parts[2]);
            double centerLon = Double.parseDouble(parts[3]);
            double areaKm2 = Double.parseDouble(parts[4]);

            return new DeliveryZone(zoneId, zoneName, centerLat, centerLon, areaKm2);
        });

        System.out.println("[ZoneInitializer] Loaded " + zones.size() + " inter-metro zones");
        return zones;
    }

    /**
     * Loads zones for dual-mode simulation (intra + inter metropolitan).
     *
     * <p>This method combines both intra-metropolitan and inter-metropolitan
     * zones into a single list for dual-mode simulation.
     *
     * @param intraPath Path to intra-metro zones CSV
     * @param interPath Path to inter-metro zones CSV
     * @return Combined list of all delivery zones
     * @throws IOException If either file cannot be read
     */
    public List<DeliveryZone> loadDualModeZones(String intraPath, String interPath)
            throws IOException {

        System.out.println("[ZoneInitializer] Loading dual-mode zones...");

        List<DeliveryZone> allZones = new ArrayList<>();

        // Load intra-metropolitan zones
        List<DeliveryZone> intraZones = loadIntraMetroZones(intraPath);
        allZones.addAll(intraZones);

        // Load inter-metropolitan zones
        List<DeliveryZone> interZones = loadInterMetroZones(interPath);
        allZones.addAll(interZones);

        System.out.println("[ZoneInitializer] Total zones loaded: " + allZones.size() +
                         " (" + intraZones.size() + " intra + " +
                         interZones.size() + " inter)");

        return allZones;
    }

    /**
     * Loads zone mapping from MFS survey zones to simulation zones.
     *
     * <p>The MFS (Metropolitan Freight Survey) uses a different zone system
     * than the simulation. This mapping allows translation between the two.
     *
     * @param filePath Path to zone mapping CSV file
     * @return Map of MFS zone ID to simulation zone ID
     * @throws IOException If file cannot be read
     */
    public Map<String, String> loadZoneMapping(String filePath) throws IOException {
        System.out.println("[ZoneInitializer] Loading zone mapping from: " + filePath);

        Map<String, String> mapping = new HashMap<>();

        CSVLoader.loadCSV(filePath, (line, lineNum) -> {
            String[] parts = line.split(",");

            if (parts.length >= 2) {
                String mfsZoneId = parts[0].trim();
                String simZoneId = parts[1].trim();
                mapping.put(mfsZoneId, simZoneId);
            }

            return null;  // Just building the map, no objects to return
        });

        System.out.println("[ZoneInitializer] Loaded " + mapping.size() + " zone mappings");
        return mapping;
    }

    /**
     * Loads establishment data (retail, logistics, industrial counts) for zones.
     *
     * <p>Enriches delivery zones with establishment counts used for POI generation
     * and trip attraction calculations.
     *
     * @param zones List of delivery zones to enrich
     * @param filePath Path to establishment data CSV
     * @throws IOException If file cannot be read
     */
    public void loadEstablishmentData(List<DeliveryZone> zones, String filePath)
            throws IOException {

        System.out.println("[ZoneInitializer] Loading establishment data from: " + filePath);

        // Build zone lookup map
        Map<String, DeliveryZone> zoneMap = new HashMap<>();
        for (DeliveryZone zone : zones) {
            zoneMap.put(zone.getZoneId(), zone);
        }

        // Load establishment counts
        int enrichedCount = 0;
        CSVLoader.loadCSV(filePath, (line, lineNum) -> {
            String[] parts = line.split(",");

            if (parts.length >= 4) {
                String zoneId = parts[0].trim();
                DeliveryZone zone = zoneMap.get(zoneId);

                if (zone != null) {
                    try {
                        int retailCount = Integer.parseInt(parts[1]);
                        int logisticsCount = Integer.parseInt(parts[2]);
                        int industrialCount = Integer.parseInt(parts[3]);

                        zone.setRetailEstablishments(retailCount);
                        zone.setLogisticsEstablishments(logisticsCount);
                        zone.setIndustrialEstablishments(industrialCount);

                    } catch (NumberFormatException e) {
                        System.err.println("[WARN] Failed to parse establishment counts " +
                                         "for zone " + zoneId + ": " + e.getMessage());
                    }
                }
            }

            return null;
        });

        // Count how many zones were enriched
        for (DeliveryZone zone : zones) {
            if (zone.getRetailEstablishments() > 0 ||
                zone.getLogisticsEstablishments() > 0 ||
                zone.getIndustrialEstablishments() > 0) {
                enrichedCount++;
            }
        }

        System.out.println("[ZoneInitializer] Enriched " + enrichedCount +
                         " zones with establishment data");
    }

    /**
     * Validates that all zones have required data.
     *
     * <p>Checks for:
     * <ul>
     *   <li>Valid zone IDs (non-null, non-empty)</li>
     *   <li>Valid coordinates (within Tokyo bounds)</li>
     *   <li>Positive area values</li>
     * </ul>
     *
     * @param zones List of zones to validate
     * @return true if all zones are valid
     */
    public boolean validateZones(List<DeliveryZone> zones) {
        if (zones == null || zones.isEmpty()) {
            System.err.println("[ERROR] Zone list is null or empty");
            return false;
        }

        int invalidCount = 0;

        for (DeliveryZone zone : zones) {
            // Check zone ID
            if (zone.getZoneId() == null || zone.getZoneId().isEmpty()) {
                System.err.println("[ERROR] Zone has null or empty ID");
                invalidCount++;
                continue;
            }

            // Check coordinates (Tokyo bounds: 35.0-36.0 lat, 138.0-140.0 lon)
            double lat = zone.getCenterLatitude();
            double lon = zone.getCenterLongitude();

            if (lat < 34.0 || lat > 37.0 || lon < 137.0 || lon > 141.0) {
                System.err.println("[WARN] Zone " + zone.getZoneId() +
                                 " has coordinates outside Tokyo region: " +
                                 lat + ", " + lon);
            }

            // Check area
            if (zone.getAreaKm2() <= 0) {
                System.err.println("[ERROR] Zone " + zone.getZoneId() +
                                 " has invalid area: " + zone.getAreaKm2());
                invalidCount++;
            }
        }

        if (invalidCount > 0) {
            System.err.println("[ERROR] Found " + invalidCount + " invalid zones");
            return false;
        }

        System.out.println("[ZoneInitializer] All " + zones.size() + " zones validated successfully");
        return true;
    }
}
