package taxi.sim;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import util.MetricsDashboard;

/**
 * Main simulation controller for Tokyo Taxi Agent-Based Model
 * Version 3.1 - PSEUDO PFLOW COMPATIBILITY WITH EMPTY TRIPS
 *
 * EMPTY TRIP LOGIC:
 * ----------------
 * Empty trips (repositioning/deadhead trips) occur between passenger trips when:
 * - Taxi moves from dropoff location to next pickup location
 * - Distance > threshold (e.g., 0.5 km)
 * - These trips have passenger_in=false and fare=0
 * - Critical for traffic volume analysis as empty taxis affect congestion
 *
 * REAL TOKYO VALIDATION TARGETS (from surveys):
 * - Total taxis: 50,000 registered (40,000 active daily at 80% operating rate)
 * - Daily trips: 433,000 total
 * - Trips per taxi: 26.8 average
 * - Trip distance: 4-6 km average (critical!)
 * - Shift duration: 12-16 hours
 * - Nearby trips: ~70% within 3km of previous dropoff
 * - Empty running ratio: ~40% of total distance (industry standard)
 */
public class TaxiSimulation {

    // ==================== CONFIGURATION ====================
    // All configuration now loaded from TaxiConfig singleton
    private TaxiConfig config;

    // Default config file path
    private static final String DEFAULT_CONFIG_FILE = "config/taxi/tokyo/taxi_config.properties";

    // ──────────────────────────────────────────────────────────────────────
    // LOCAL-taxi familiarity multipliers (Part D readability pass).
    // Applied to zone attractiveness scores in selectSmartDestination and
    // selectDistanceGuidedDestination. Values originally scattered as magic
    // numbers; names clarify intent and keep the two call sites in sync.
    //
    //   FAMILIAR_BOOST       — zone is in the taxi's familiar-zone list
    //   RADIUS_BOOST         — no zone list, but point is inside home radius
    //   RADIUS_PENALTY       — no zone list, point is outside home radius
    //   OUT_OF_TERRITORY_PENALTY — zone list exists and selected zone is NOT in it
    //
    // OUT_OF_TERRITORY_PENALTY is the TX5 soft escape factor (~5% long-tail).
    // ──────────────────────────────────────────────────────────────────────
    private static final double LOCAL_FAMILIAR_BOOST           = 5.0;
    private static final double LOCAL_RADIUS_BOOST             = 3.0;
    private static final double LOCAL_RADIUS_PENALTY           = 0.1;
    private static final double LOCAL_OUT_OF_TERRITORY_PENALTY = 0.05;

    // Data structures
    private List<TaxiAgent> taxiFleet;
    private List<TaxiTrip> allTrips;  // V3.1: Now includes both passenger and empty trips
    private List<DestinationZone> destinationZones;
    private Random random;

    // Spatial validation (null if disabled)
    private TaxiGeoValidator geoValidator;

    // Transport network index for station/airport proximity (null if disabled)
    private TaxiTransportIndex transportIndex;

    // TX5: LOCAL-taxi escape counter. Observability only — the 0.05× soft
    // penalty is an intentional modelling choice (~5% long-tail for realism);
    // we count escapes so the rate is visible in dashboard.csv rather than
    // hidden behind a silent soft decay.
    private final AtomicLong localTaxiOutOfTerritoryCount = new AtomicLong(0);

    // B1/Phase 2: Trip-generation counters promoted from generateTrips() locals to
    // class fields so the per-taxi inner block can be extracted into
    // runLegacyTripLoopForOneTaxi(), shared between the legacy and shift_time engine
    // paths. Reset at the top of generateTrips(). Single-threaded access — the
    // taxi fleet is iterated sequentially, no concurrent mutation.
    private int tripIdCounter;
    private int rejectedTooShort;
    private int rejectedTooLong;
    private int totalPassengerTrips;
    private int totalEmptyTrips;

    // B2/Phase 4: Per-type mode-mix counters for shift_time engine observability.
    // 5 types × 3 modes (street, app, stand). Reset at top of generateTrips().
    // Printed as a per-type breakdown after the trip loop completes.
    private final java.util.EnumMap<TaxiType, int[]> modeMixCounters =
        new java.util.EnumMap<>(TaxiType.class);
    /** B2: ModeMixResolver instance, lazily constructed when shift_time engine activates. */
    private ModeMixResolver modeMixResolver;

    /** B7: per-cycle diagnostic CSV writer (no-op when disabled). */
    private DiagnosticWriter diagnosticWriter;
    /** B7: pre-computed stratified sample of taxi IDs for diagnostic emit. */
    private java.util.Set<Integer> diagnosticSampleIds;

    // ==================== LEGACY HOTSPOT SYSTEM (REMOVED IN V4.0) ====================
    //
    // The hardcoded hotspot system has been replaced by zone-based transport hubs.
    //
    // MIGRATION GUIDE:
    // - Old: HUB taxis used DEFAULT_TOKYO_HOTSPOTS array with 8 hardcoded locations
    // - New: HUB taxis use transport hub zones from zones.csv (isTransportHub=true)
    //
    // To revert to hotspot system:
    // 1. Uncomment the Hotspot class and DEFAULT_TOKYO_HOTSPOTS array below
    // 2. Replace selectTransportHubZone() calls with selectHotspot()
    // 3. In selectSmartDestination(), use: Hotspot hotspot = selectHotspot(currentTime)
    //
    // REMOVED CODE (available in git history or below for reference):
    /*
    private static final Hotspot[] DEFAULT_TOKYO_HOTSPOTS = {
        new Hotspot("Tokyo Station", 139.7673, 35.6809, 0.20, "station"),
        new Hotspot("Shinjuku Station", 139.7005, 35.6762, 0.18, "station"),
        new Hotspot("Shibuya Station", 139.7103, 35.6586, 0.15, "station"),
        new Hotspot("Narita Airport Area", 140.3864, 35.7653, 0.12, "airport"),
        new Hotspot("Haneda Airport", 139.7798, 35.5494, 0.15, "airport"),
        new Hotspot("Ueno Station", 139.8107, 35.7100, 0.08, "station"),
        new Hotspot("Ikebukuro Station", 139.7111, 35.7295, 0.07, "station"),
        new Hotspot("Roppongi Area", 139.7308, 35.6642, 0.05, "entertainment")
    };

    private static class Hotspot {
        String name;
        double lon, lat;
        double weight;  // Probability weight
        String type;    // "station", "airport", "entertainment"

        Hotspot(String name, double lon, double lat, double weight, String type) {
            this.name = name; this.lon = lon; this.lat = lat;
            this.weight = weight; this.type = type;
        }
    }

    private Hotspot selectHotspot(long timeOfDay) {
        List<Hotspot> hotspots = getHotspots();

        // Calculate total weight considering time of day
        double totalWeight = 0.0;
        for (Hotspot hotspot : hotspots) {
            totalWeight += getHotspotWeight(hotspot, timeOfDay);
        }

        // Select based on weighted probability
        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (Hotspot hotspot : hotspots) {
            cumulative += getHotspotWeight(hotspot, timeOfDay);
            if (rand <= cumulative) {
                return hotspot;
            }
        }

        return hotspots.get(0);  // Fallback
    }

    private double getHotspotWeight(Hotspot hotspot, long timeOfDay) {
        double weight = hotspot.weight;

        // Boost airport hotspots during night hours (22:00-06:00)
        if (hotspot.type.equals("airport") && config.isNightHours(timeOfDay)) {
            weight *= config.getHotspotAirportNightMultiplier();
        }

        return weight;
    }

    private List<Hotspot> getHotspots() {
        return Arrays.asList(DEFAULT_TOKYO_HOTSPOTS);
    }
    */
    // ==================== END LEGACY CODE ====================

    /**
     * Constructor
     */
    public TaxiSimulation() {
        this.config = TaxiConfig.getInstance();
        this.taxiFleet = new ArrayList<>();
        this.allTrips = new ArrayList<>();
        this.destinationZones = new ArrayList<>();
    }

    /**
     * Load configuration and initialize random generator
     */
    private void loadConfiguration(String configPath) {
        // Load configuration from file
        config.loadFromFile(configPath);

        // Derive config directory from config file path for zone file resolution
        java.io.File configFile = new java.io.File(configPath);
        String configDir = configFile.getParent();
        if (configDir != null) {
            config.setConfigDir(configDir.replace('\\', '/') + "/");
        }

        // Initialize spatial validator if enabled
        if (config.isSpatialValidationEnabled()) {
            geoValidator = new TaxiGeoValidator(config.getShapefileDir());
            geoValidator.setRiverBufferKm(config.getRiverBufferKm());
            double[] configBounds = {
                config.getBoundsMinLon(), config.getBoundsMaxLon(),
                config.getBoundsMinLat(), config.getBoundsMaxLat()
            };
            geoValidator.loadAll(config.getPrefectureCodes(), configBounds);
        } else {
            System.out.println("[SPATIAL] Spatial validation DISABLED");
            geoValidator = null;
        }

        // Initialize random generator with seed from config
        int seed = config.getRandomSeed();
        if (seed >= 0) {
            this.random = new Random(seed);
        } else {
            this.random = new Random();
        }

        // Initialize destination zones
        initializeDestinationZones();

        // Initialize transport network index and enrich zones
        initializeTransportIndex();
    }

    /**
     * V5.0: Initialize transport network index from station/airport shapefiles
     * and enrich destination zones with proximity metadata.
     */
    private void initializeTransportIndex() {
        if (!config.isTransportIndexEnabled()) {
            System.out.println("[TRANSPORT] Transport network index DISABLED");
            transportIndex = null;
            return;
        }

        transportIndex = new TaxiTransportIndex();
        transportIndex.loadStations(config.getShapefileDir() + "rstatp_jpn.shp");
        transportIndex.loadAirports(config.getShapefileDir() + "airp_jpn.shp");
        transportIndex.loadSettlements(config.getShapefileDir() + "builtupp_jpn.shp");
        transportIndex.loadPorts(config.getShapefileDir() + "portp_jpn.shp");

        if (!transportIndex.hasStations() && !transportIndex.hasAirports()) {
            System.out.println("[TRANSPORT] No station or airport data loaded — skipping zone enrichment");
            transportIndex = null;
            return;
        }

        // Enrich zones with nearest station/airport metadata
        for (DestinationZone zone : destinationZones) {
            // Find nearest railway station
            if (transportIndex.hasStations()) {
                TaxiTransportIndex.NearestResult station =
                    transportIndex.findNearestStation(
                        zone.getCenterLon(), zone.getCenterLat(), 5.0);
                if (station != null) {
                    zone.setNearestStation(station.distanceKm, station.name);
                }

                int stationCount = transportIndex.countStationsInRadius(
                    zone.getCenterLon(), zone.getCenterLat(), 1.0);
                zone.setStationsWithin1km(stationCount);
            }

            // Find nearest airport
            if (transportIndex.hasAirports()) {
                TaxiTransportIndex.NearestResult airport =
                    transportIndex.findNearestAirport(
                        zone.getCenterLon(), zone.getCenterLat(), 10.0);
                if (airport != null) {
                    zone.setNearestAirport(airport.distanceKm, airport.name);
                }
            }
        }

        // Log enrichment results
        long zonesNearStation = destinationZones.stream()
            .filter(DestinationZone::isNearStation).count();
        long zonesNearAirport = destinationZones.stream()
            .filter(DestinationZone::isNearAirport).count();
        System.out.println("[TRANSPORT] Enriched " + destinationZones.size() + " zones: " +
            zonesNearStation + " near stations, " + zonesNearAirport + " near airports");

        // Print top zones by station proximity
        destinationZones.stream()
            .filter(DestinationZone::isNearStation)
            .sorted((a, b) -> Double.compare(a.getNearestStationDistKm(), b.getNearestStationDistKm()))
            .limit(5)
            .forEach(z -> System.out.println("  " + z.getName() + " → " +
                z.getNearestStationName() + " (" +
                String.format("%.1f", z.getNearestStationDistKm()) + " km, " +
                z.getStationsWithin1km() + " stations within 1km)"));

        // Append additional zones from shapefile features not covered by existing zones
        enrichZonesFromShapefiles();
    }

    /**
     * V5.1: Discover geographic features from shapefiles within the metro bounds
     * and append them as new zones — both in-memory (for this run) and permanently
     * to the zones.csv file.
     *
     * <p>Only skips exact location duplicates (within 0.3km of an existing zone center).
     * Uses the config bounds (with margin) to filter features.
     *
     * <p>Zone IDs use prefixes: ZS=station, ZP=settlement, ZA=airport, ZR=port.
     */
    private void enrichZonesFromShapefiles() {
        if (!config.isZoneEnrichmentEnabled()) {
            System.out.println("[ENRICH] Zone enrichment from shapefiles DISABLED");
            return;
        }

        int csvZoneCount = destinationZones.size();
        double dedupKm = 0.3;  // Only skip near-exact duplicates

        // ── Compute metro center and radius from existing zones ──────────
        double totalWeight = 0, weightedLon = 0, weightedLat = 0;
        for (DestinationZone z : destinationZones) {
            double w = z.getJobsWeight() + 0.01;
            totalWeight += w;
            weightedLon += z.getCenterLon() * w;
            weightedLat += z.getCenterLat() * w;
        }
        double centerLon = weightedLon / totalWeight;
        double centerLat = weightedLat / totalWeight;

        double metroRadiusKm = 0;
        for (DestinationZone z : destinationZones) {
            double d = haversineKm(centerLon, centerLat, z.getCenterLon(), z.getCenterLat());
            if (d > metroRadiusKm) metroRadiusKm = d;
        }
        double searchRadiusKm = metroRadiusKm + 5.0;

        System.out.println("[ENRICH] Metro center: " + String.format("%.4f,%.4f", centerLon, centerLat) +
            " radius=" + String.format("%.1f", metroRadiusKm) + "km, search=" +
            String.format("%.1f", searchRadiusKm) + "km");

        java.util.List<String> newCsvLines = new java.util.ArrayList<>();
        int stationsAdded = 0, settlementsAdded = 0, airportsAdded = 0, portsAdded = 0;

        // TX6: Seed counters from existing zones so re-runs never mint duplicate
        // IDs. Location-based dedup (0.3km) already skips already-enriched
        // features, so on a normal re-run nothing is appended; but if the
        // shapefile grows or bounds change, new features must get fresh IDs.
        int stationCounter    = nextCounter("ZS");
        int settlementCounter = nextCounter("ZP");
        int airportCounter    = nextCounter("ZA");
        int portCounter       = nextCounter("ZR");

        // ── 1. Stations → hub zones ─────────────────────────────────────
        if (transportIndex.hasStations()) {
            java.util.List<TaxiTransportIndex.NearestResult> stations =
                transportIndex.findStationsInRadius(centerLon, centerLat, searchRadiusKm);
            for (TaxiTransportIndex.NearestResult st : stations) {
                if (!isWithinBounds(st.lon, st.lat)) continue;
                if (isLocationCovered(st.lon, st.lat, dedupKm)) continue;

                String id = String.format("ZS%02d", stationCounter++);
                String name = st.name + " Station Area";
                DestinationZone zone = new DestinationZone(
                    id, "hub", name, st.lon, st.lat, 1.5,
                    0.7, 0.5, 0.3, 0.2, true);
                destinationZones.add(zone);
                newCsvLines.add(formatZoneCsv(id, "hub", name, st.lon, st.lat, 1.5,
                    0.7, 0.5, 0.3, 0.2, true));
                stationsAdded++;
            }
        }

        // ── 2. Settlements → type by distance from center ───────────────
        if (transportIndex.hasSettlements()) {
            java.util.List<TaxiTransportIndex.NearestResult> settlements =
                transportIndex.findSettlementsInRadius(centerLon, centerLat, searchRadiusKm);
            for (TaxiTransportIndex.NearestResult st : settlements) {
                if (!isWithinBounds(st.lon, st.lat)) continue;
                if (isLocationCovered(st.lon, st.lat, dedupKm)) continue;

                double distFromCenter = haversineKm(centerLon, centerLat, st.lon, st.lat);
                double normalizedDist = (metroRadiusKm > 0) ? distFromCenter / metroRadiusKm : 0.5;

                String zoneType;
                double jobs, shops, nightlife, residential;
                if (normalizedDist < 0.20) {
                    zoneType = "commercial";
                    jobs = 0.9; shops = 0.8; nightlife = 0.6; residential = 0.2;
                } else if (normalizedDist < 0.45) {
                    zoneType = "mixed";
                    jobs = 0.6; shops = 0.6; nightlife = 0.4; residential = 0.5;
                } else if (normalizedDist < 0.70) {
                    zoneType = "mixed";
                    jobs = 0.3; shops = 0.4; nightlife = 0.2; residential = 0.7;
                } else {
                    zoneType = "residential";
                    jobs = 0.2; shops = 0.3; nightlife = 0.1; residential = 0.9;
                }

                String id = String.format("ZP%02d", settlementCounter++);
                DestinationZone zone = new DestinationZone(
                    id, zoneType, st.name, st.lon, st.lat, 2.0,
                    jobs, shops, nightlife, residential, false);
                destinationZones.add(zone);
                newCsvLines.add(formatZoneCsv(id, zoneType, st.name, st.lon, st.lat, 2.0,
                    jobs, shops, nightlife, residential, false));
                settlementsAdded++;
            }
        }

        // ── 3. Airports → hub zones ─────────────────────────────────────
        if (transportIndex.hasAirports()) {
            java.util.List<TaxiTransportIndex.NearestResult> airports =
                transportIndex.findAirportsInRadius(centerLon, centerLat, searchRadiusKm);
            for (TaxiTransportIndex.NearestResult ap : airports) {
                if (!isWithinBounds(ap.lon, ap.lat)) continue;
                if (isLocationCovered(ap.lon, ap.lat, dedupKm)) continue;

                String id = String.format("ZA%02d", airportCounter++);
                DestinationZone zone = new DestinationZone(
                    id, "hub", ap.name, ap.lon, ap.lat, 3.0,
                    0.5, 0.3, 0.2, 0.1, true);
                destinationZones.add(zone);
                newCsvLines.add(formatZoneCsv(id, "hub", ap.name, ap.lon, ap.lat, 3.0,
                    0.5, 0.3, 0.2, 0.1, true));
                airportsAdded++;
            }
        }

        // ── 4. Ports → hub zones ────────────────────────────────────────
        if (transportIndex.hasPorts()) {
            java.util.List<TaxiTransportIndex.NearestResult> ports =
                transportIndex.findPortsInRadius(centerLon, centerLat, searchRadiusKm);
            for (TaxiTransportIndex.NearestResult pt : ports) {
                if (!isWithinBounds(pt.lon, pt.lat)) continue;
                if (isLocationCovered(pt.lon, pt.lat, dedupKm)) continue;

                String id = String.format("ZR%02d", portCounter++);
                // Avoid "Port Port" if name already ends with "Port"
                String name = pt.name.toLowerCase().endsWith("port") ? pt.name : pt.name + " Port";
                DestinationZone zone = new DestinationZone(
                    id, "hub", name, pt.lon, pt.lat, 2.0,
                    0.4, 0.2, 0.1, 0.2, true);
                destinationZones.add(zone);
                newCsvLines.add(formatZoneCsv(id, "hub", name, pt.lon, pt.lat, 2.0,
                    0.4, 0.2, 0.1, 0.2, true));
                portsAdded++;
            }
        }

        int totalAdded = stationsAdded + settlementsAdded + airportsAdded + portsAdded;
        if (totalAdded > 0) {
            System.out.println("[ENRICH] Appended " + totalAdded + " zones from shapefiles " +
                "(stations=" + stationsAdded + ", settlements=" + settlementsAdded +
                ", airports=" + airportsAdded + ", ports=" + portsAdded + ")");
            System.out.println("[ENRICH] Total zones: " + csvZoneCount + " (CSV) + " +
                totalAdded + " (shapefile) = " + destinationZones.size());

            // Write enriched zones to CSV file permanently
            appendZonesToCsv(newCsvLines);
        } else {
            System.out.println("[ENRICH] No new zones to add from shapefiles");
        }
    }

    /**
     * Append new zone CSV lines to the zones.csv file.
     * Preserves all existing hand-crafted zones.
     */
    private void appendZonesToCsv(java.util.List<String> newLines) {
        String zonesFile = config.getZonesFile();
        String zonesPath = config.getConfigDir() + zonesFile;
        try (java.io.FileWriter fw = new java.io.FileWriter(zonesPath, true)) {  // append mode
            for (String line : newLines) {
                fw.write(line + "\n");
            }
            System.out.println("[ENRICH] Written " + newLines.size() + " new zones to " + zonesPath);
        } catch (java.io.IOException e) {
            System.err.println("[ENRICH] Warning: Could not write to " + zonesPath + ": " + e.getMessage());
        }
    }

    /**
     * Format a zone entry as a CSV line matching the 11-column zones.csv schema.
     */
    private String formatZoneCsv(String id, String type, String name, double lon, double lat,
                                  double radius, double jobs, double shops, double nightlife,
                                  double residential, boolean hub) {
        return String.format("%s,%s,%s,%.4f,%.4f,%.1f,%.1f,%.1f,%.1f,%.1f,%d",
            id, type, name, lon, lat, radius, jobs, shops, nightlife, residential, hub ? 1 : 0);
    }

    /**
     * TX6: Scan {@code destinationZones} for IDs beginning with {@code prefix}
     * (followed by digits, e.g. "ZS07") and return {@code max + 1}. If no
     * matching ID is found, returns 1. Used to seed enrichment counters so
     * re-runs don't collide with IDs from a prior enrichment pass.
     */
    private int nextCounter(String prefix) {
        int max = 0;
        for (DestinationZone zone : destinationZones) {
            String id = zone.getZoneId();
            if (id == null || !id.startsWith(prefix)) continue;
            try {
                int n = Integer.parseInt(id.substring(prefix.length()));
                if (n > max) max = n;
            } catch (NumberFormatException ignore) {
                // Non-numeric suffix — skip (defensive; current scheme uses %02d digits).
            }
        }
        return max + 1;
    }

    /**
     * Check if a location is a near-duplicate of any existing zone center.
     * Uses tight threshold (0.3km) — only prevents true duplicates.
     */
    private boolean isLocationCovered(double lon, double lat, double thresholdKm) {
        for (DestinationZone zone : destinationZones) {
            if (zone.getDistanceFromCenter(lon, lat) < thresholdKm) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if coordinates fall within the actual zone extent (computed from
     * existing zone positions, not config bounds which may be narrower).
     * Uses 0.05° margin (~5km) beyond the outermost zone centers.
     */
    private boolean isWithinBounds(double lon, double lat) {
        // Compute extent from actual zone positions (lazy-init on first call)
        if (boundsMinLon == 0 && boundsMaxLon == 0) {
            boundsMinLon = Double.MAX_VALUE; boundsMaxLon = -Double.MAX_VALUE;
            boundsMinLat = Double.MAX_VALUE; boundsMaxLat = -Double.MAX_VALUE;
            for (DestinationZone z : destinationZones) {
                boundsMinLon = Math.min(boundsMinLon, z.getCenterLon());
                boundsMaxLon = Math.max(boundsMaxLon, z.getCenterLon());
                boundsMinLat = Math.min(boundsMinLat, z.getCenterLat());
                boundsMaxLat = Math.max(boundsMaxLat, z.getCenterLat());
            }
            double margin = 0.05;  // ~5km margin
            boundsMinLon -= margin; boundsMaxLon += margin;
            boundsMinLat -= margin; boundsMaxLat += margin;
        }
        return lon >= boundsMinLon && lon <= boundsMaxLon
            && lat >= boundsMinLat && lat <= boundsMaxLat;
    }
    private double boundsMinLon, boundsMaxLon, boundsMinLat, boundsMaxLat;

    /**
     * Haversine distance in kilometers between two WGS84 points.
     */
    private double haversineKm(double lon1, double lat1, double lon2, double lat2) {
        double R = config.getEarthRadiusKm();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Initialize destination zones from CSV file
     * Zones define different area types in Tokyo with time-dependent attractiveness
     */
    private void initializeDestinationZones() {
        System.out.println("[CHECKPOINT] Loading destination zones from CSV...");

        String zonesFile = config.getZonesFile();
        String zonesPath = config.getConfigDir() + zonesFile;

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(zonesPath))) {

            String line = reader.readLine(); // Skip header
            int lineNum = 1;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }

                String[] parts = line.split(",");
                if (parts.length != 11) {
                    System.err.println("  ⚠ Warning: Skipping malformed line " + lineNum +
                        " (expected 11 columns, got " + parts.length + ")");
                    continue;
                }

                try {
                    String zoneId = parts[0].trim();
                    String zoneType = parts[1].trim();
                    String name = parts[2].trim();
                    double centerLon = Double.parseDouble(parts[3].trim());
                    double centerLat = Double.parseDouble(parts[4].trim());
                    double radius = Double.parseDouble(parts[5].trim());
                    double jobsWeight = Double.parseDouble(parts[6].trim());
                    double shopsWeight = Double.parseDouble(parts[7].trim());
                    double nightlifeWeight = Double.parseDouble(parts[8].trim());
                    double residentialWeight = Double.parseDouble(parts[9].trim());
                    String hubValue = parts[10].trim();
                    boolean isTransportHub = hubValue.equals("1") || hubValue.equalsIgnoreCase("true");

                    DestinationZone zone = new DestinationZone(
                        zoneId, zoneType, name, centerLon, centerLat, radius,
                        jobsWeight, shopsWeight, nightlifeWeight, residentialWeight,
                        isTransportHub
                    );
                    destinationZones.add(zone);

                } catch (NumberFormatException e) {
                    System.err.println("  ⚠ Warning: Skipping line " + lineNum +
                        " due to number format error: " + e.getMessage());
                }
            }

            System.out.println("[CHECKPOINT] Loaded " + destinationZones.size() +
                " destination zones from " + zonesFile);

        } catch (java.io.IOException e) {
            System.err.println("✗ Error loading zones file: " + e.getMessage());
            System.err.println("  Using empty zone list");
        }
    }

    /**
     * Initialize taxi fleet with variable shift durations and staggered starts
     *
     * REAL TOKYO DATA IMPLEMENTATION + HETEROGENEOUS AGENT TYPES:
     * - 50,000 total taxis registered
     * - 80% operate daily (40,000 active)
     * - Shift duration: 12-16 hours (Gaussian distribution around 14 hours)
     * - Staggered shift starts (all configurable)
     * - Taxi types: LOCAL (70%), CITYWIDE (15%), HUB (15%)
     */
    private void initializeTaxis() {
        System.out.println("[CHECKPOINT] Initializing " + config.getTaxiFleetSize() + " taxis...");

        final int numTaxis = config.getTaxiFleetSize();

        int localCount = 0, citywideCount = 0, appPreferredCount = 0,
            hubCount = 0, rideHailPrhsCount = 0;

        // Pre-compute cumulative thresholds for the 5-type roll (DESIGN.md §2.2 formula).
        // Defaults at x=APP_PREFERRED=0.20: 0.5385 + 0.1615 + 0.20 + 0.095 + 0.005 = 1.000
        final double cumLocal         = config.getTaxiTypeLocalShare();
        final double cumCitywide      = cumLocal + config.getTaxiTypeCitywideShare();
        final double cumAppPreferred  = cumCitywide + config.getTaxiTypeAppPreferredShare();
        final double cumHub           = cumAppPreferred + config.getTaxiTypeHubShare();
        // PRHS is the residual (= cumHub + getTaxiTypeRideHailPrhsShare ≈ 1.0)

        for (int i = 0; i < numTaxis; i++) {
            // Random home location within city bounds, with spatial validation
            double homeLon, homeLat;
            int homeAttempt = 0;
            double[] homeBounds = getSamplingBounds();
            do {
                homeLon = homeBounds[0] + random.nextDouble() * (homeBounds[1] - homeBounds[0]);
                homeLat = homeBounds[2] + random.nextDouble() * (homeBounds[3] - homeBounds[2]);
                homeAttempt++;
            } while (homeAttempt < 50 && !isValidLocation(homeLon, homeLat));

            // 5-type fleet sample from configured shares (DESIGN.md §2.2 formula).
            TaxiType taxiType;
            double typeRoll = random.nextDouble();

            if (typeRoll < cumLocal) {
                taxiType = TaxiType.LOCAL;
                localCount++;
            } else if (typeRoll < cumCitywide) {
                taxiType = TaxiType.CITYWIDE;
                citywideCount++;
            } else if (typeRoll < cumAppPreferred) {
                taxiType = TaxiType.APP_PREFERRED;
                appPreferredCount++;
            } else if (typeRoll < cumHub) {
                taxiType = TaxiType.HUB;
                hubCount++;
            } else {
                taxiType = TaxiType.RIDE_HAIL_PRHS;
                rideHailPrhsCount++;
            }

            // Determine shift start time (staggered)
            long shiftStart;
            double shiftStartRoll = random.nextDouble();

            if (shiftStartRoll < config.getShiftStart1Prob()) {
                shiftStart = config.getShiftStart1Time();
            } else if (shiftStartRoll < config.getShiftStart1Prob() + config.getShiftStart2Prob()) {
                shiftStart = config.getShiftStart2Time();
            } else {
                shiftStart = config.getShiftStart3Time();
            }

            // Variable shift duration: Gaussian distribution
            double shiftDurationHours = config.getShiftDurationAverage() +
                random.nextGaussian() * config.getShiftDurationStddev();

            // Clamp to min/max bounds
            shiftDurationHours = Math.max(config.getShiftDurationMin(),
                Math.min(config.getShiftDurationMax(), shiftDurationHours));
            long shiftDuration = (long)(shiftDurationHours * 3600);
            long shiftEnd = shiftStart + shiftDuration;

            // B2: per-type familiar radius. LOCAL keeps its 5km legacy default;
            // APP_PREFERRED + RIDE_HAIL_PRHS are unrestricted (0 km).
            // CITYWIDE + HUB use the legacy default (radius unused — see TaxiAgent).
            double familiarRadius;
            switch (taxiType) {
                case APP_PREFERRED:    familiarRadius = config.getTaxiTypeAppPreferredFamiliarRadiusKm(); break;
                case RIDE_HAIL_PRHS:   familiarRadius = config.getTaxiTypeRideHailPrhsFamiliarRadiusKm(); break;
                default:               familiarRadius = config.getLocalFamiliarRadiusKm();
            }
            TaxiAgent taxi = new TaxiAgent(i, taxiType, homeLon, homeLat,
                shiftStart, shiftEnd, familiarRadius);

            // V4.0: Assign familiar zones for LOCAL taxis (zone clustering)
            if (taxiType == TaxiType.LOCAL) {
                List<String> familiarZones = assignFamiliarZones(taxi);
                taxi.setFamiliarZoneIds(familiarZones);

                // Legacy: also set single familiar area zone for export compatibility
                String zoneId = findNearestZone(homeLon, homeLat);
                taxi.setFamiliarAreaZoneId(zoneId);
            }

            taxiFleet.add(taxi);
        }

        System.out.println("[CHECKPOINT] Initialized " + numTaxis + " taxis:");
        System.out.println("  LOCAL: " + localCount + " (" +
            String.format("%.1f%%", 100.0 * localCount / numTaxis) + ")");
        System.out.println("  CITYWIDE: " + citywideCount + " (" +
            String.format("%.1f%%", 100.0 * citywideCount / numTaxis) + ")");
        System.out.println("  APP_PREFERRED: " + appPreferredCount + " (" +
            String.format("%.1f%%", 100.0 * appPreferredCount / numTaxis) + ")");
        System.out.println("  HUB: " + hubCount + " (" +
            String.format("%.1f%%", 100.0 * hubCount / numTaxis) + ")");
        System.out.println("  RIDE_HAIL_PRHS: " + rideHailPrhsCount + " (" +
            String.format("%.1f%%", 100.0 * rideHailPrhsCount / numTaxis) + ")");
    }

    /**
     * Find the nearest destination zone to a given location
     */
    private String findNearestZone(double lon, double lat) {
        double minDistance = Double.MAX_VALUE;
        String nearestZoneId = "Z01";  // Default

        for (DestinationZone zone : destinationZones) {
            double distance = zone.getDistanceFromCenter(lon, lat);
            if (distance < minDistance) {
                minDistance = distance;
                nearestZoneId = zone.getZoneId();
            }
        }

        return nearestZoneId;
    }

    /**
     * V3.1: Generate trips for all taxis with EMPTY TRIP SUPPORT
     *
     * CRITICAL IMPLEMENTATION:
     * - Generates passenger trips using nearby trip logic
     * - Generates empty trips (repositioning) between passenger trips
     * - Empty trips only created if distance > threshold
     * - Average trip distance: configurable (default 5 km, Gaussian distribution)
     * - Trips generated during each taxi's shift
     */
    private void generateTrips() {
        System.out.println("[CHECKPOINT] Generating trips (passenger + empty) "
            + "— v7.0 shift-time engine...");

        // Reset class-field counters before per-taxi loop.
        tripIdCounter = 0;
        rejectedTooShort = 0;
        rejectedTooLong = 0;
        totalPassengerTrips = 0;
        totalEmptyTrips = 0;
        modeMixCounters.clear();

        // B4 / Phase 7: legacy engine removed. ShiftSimulator (v7.0 5-state
        // machine, B2/Phase 4) is the only trip-generation path.
        // State machine: OFF_DUTY → DUAL_MODE ↔ AT_STAND → OCCUPIED.
        // Empty leg: log-normal toward attractive zone centroid (DUAL_MODE) or
        //            nearest hub zone (AT_STAND) + Exp(λ) wait (B3/Phase 5).
        // Pickup mode: per-type ratios (LOCAL/CITYWIDE/APP_PREFERRED/HUB/PRHS).
        // PRHS taxis additionally gated by MLIT regulatory windows (B3/Phase 6).
        modeMixResolver = new ModeMixResolver(config, random);

        // B7: instantiate diagnostic writer + build stratified sample-id set.
        // Both passed to ShiftSimulator. When diagnostics disabled, writer is
        // a no-op singleton and sampleIds is empty — overhead is negligible.
        diagnosticWriter = new DiagnosticWriter(config);
        diagnosticSampleIds = buildDiagnosticSampleSet();

        ShiftSimulator shiftSim = new ShiftSimulator(config, this, modeMixResolver,
            random, diagnosticWriter, diagnosticSampleIds);

        for (TaxiAgent taxi : taxiFleet) {
            shiftSim.simulateShift(taxi);
        }

        double emptyRatio = 100.0 * totalEmptyTrips / allTrips.size();
        System.out.println("[CHECKPOINT] Generated " + allTrips.size() + " trips " +
            "(" + totalPassengerTrips + " passenger, " + totalEmptyTrips + " empty, " +
            String.format("%.1f%%", emptyRatio) + " empty ratio)");

        // Per-type mode-mix observability — compare observed pickup-mode shares
        // (street/app/stand) against configured per-type ratios.
        if (!modeMixCounters.isEmpty()) {
            System.out.println();
            System.out.println("[MODE-MIX] Per-type pickup-mode realised shares vs configured:");
            System.out.println("  " + String.format("%-18s %-22s %-22s", "Type",
                "street/app/stand observed", "(configured)"));
            for (TaxiType type : TaxiType.values()) {
                int[] cnt = modeMixCounters.get(type);
                if (cnt == null) continue;
                int total = cnt[0] + cnt[1] + cnt[2];
                if (total == 0) continue;
                double obsStreet = 100.0 * cnt[0] / total;
                double obsApp    = 100.0 * cnt[1] / total;
                double obsStand  = 100.0 * cnt[2] / total;
                double cfgStreet, cfgApp, cfgStand;
                switch (type) {
                    case LOCAL:
                        cfgStreet = config.getTaxiTypeLocalModeStreet() * 100;
                        cfgApp = config.getTaxiTypeLocalModeApp() * 100;
                        cfgStand = config.getTaxiTypeLocalModeStand() * 100;
                        break;
                    case CITYWIDE:
                        cfgStreet = config.getTaxiTypeCitywideModeStreet() * 100;
                        cfgApp = config.getTaxiTypeCitywideModeApp() * 100;
                        cfgStand = config.getTaxiTypeCitywideModeStand() * 100;
                        break;
                    case APP_PREFERRED:
                        cfgStreet = config.getTaxiTypeAppPreferredModeStreet() * 100;
                        cfgApp = config.getTaxiTypeAppPreferredModeApp() * 100;
                        cfgStand = config.getTaxiTypeAppPreferredModeStand() * 100;
                        break;
                    case HUB:
                        cfgStreet = config.getTaxiTypeHubModeStreet() * 100;
                        cfgApp = config.getTaxiTypeHubModeApp() * 100;
                        cfgStand = config.getTaxiTypeHubModeStand() * 100;
                        break;
                    case RIDE_HAIL_PRHS:
                        cfgStreet = config.getTaxiTypeRideHailPrhsModeStreet() * 100;
                        cfgApp = config.getTaxiTypeRideHailPrhsModeApp() * 100;
                        cfgStand = config.getTaxiTypeRideHailPrhsModeStand() * 100;
                        break;
                    default:
                        continue;
                }
                System.out.println(String.format("  %-18s %5.1f / %5.1f / %5.1f%%   (cfg %4.1f / %4.1f / %4.1f)",
                    type.name(), obsStreet, obsApp, obsStand, cfgStreet, cfgApp, cfgStand));
            }
            System.out.println();
        }
    }

    // ═══ ShiftSimulator integration accessors ═══════════════════════════════
    // Package-private helpers exposed for the v7.0 state machine in
    // {@link ShiftSimulator}. After B4 / Phase 7 these are the only callers.

    /** Allocate a fresh trip ID and increment the counter. Sequential access only. */
    int nextTripId() { return tripIdCounter++; }

    /** Append a passenger trip to the global list and increment the counter. */
    void recordPassengerTripOutput(TaxiTrip trip) {
        allTrips.add(trip);
        totalPassengerTrips++;
    }

    /** Append an empty trip to the global list and increment the counter. */
    void recordEmptyTripOutput(TaxiTrip trip) {
        allTrips.add(trip);
        totalEmptyTrips++;
    }

    /** Read access to the destination zones list (attractive-centroid sampling in DUAL_MODE). */
    java.util.List<DestinationZone> getDestinationZonesList() { return destinationZones; }

    /** Time period (1=morning, 2=daytime, 3=night) — needed by ShiftSimulator for attractiveness. */
    int getTimePeriod(long currentTime) { return config.getTimePeriod(currentTime); }

    /** Current geo-validator (may be null if spatial validation disabled). */
    TaxiGeoValidator getGeoValidator() { return geoValidator; }

    /** Track a pickup mode for per-type mode-mix observability (B2 / Phase 4). */
    void recordPickupMode(TaxiType type, ModeMixResolver.PickupMode mode) {
        int[] counters = modeMixCounters.computeIfAbsent(type, k -> new int[3]);
        counters[mode.ordinal()]++;
    }

    /**
     * B7 / Phase 7.1 — Build a stratified sample of taxi IDs for diagnostic emit.
     *
     * <p>For each {@link TaxiType}, sample
     * {@code max(diagnosticsSampleMinimumPerType, fleetSize × fraction)} IDs.
     * This guarantees rare types (RIDE_HAIL_PRHS at 0.5%) get at least the
     * minimum-per-type coverage, even though their natural fraction would yield
     * fewer than the floor. Returns an empty set when diagnostics are disabled
     * — keeps the {@code diagnosticSampleIds.contains(id)} check in
     * {@link ShiftSimulator} a fast no-op.
     */
    private java.util.Set<Integer> buildDiagnosticSampleSet() {
        java.util.Set<Integer> sampled = new java.util.HashSet<>();
        if (!config.isDiagnosticsEnabled()) return sampled;

        // Group taxiFleet by type
        java.util.EnumMap<TaxiType, java.util.List<Integer>> byType =
            new java.util.EnumMap<>(TaxiType.class);
        for (TaxiAgent t : taxiFleet) {
            byType.computeIfAbsent(t.getTaxiType(), k -> new java.util.ArrayList<>())
                  .add(t.getTaxiId());
        }

        double fraction = config.getDiagnosticsSampleFraction();
        int minPerType = config.getDiagnosticsSampleMinimumPerType();
        Random sampleRng = new Random(config.getRandomSeed() + 7777L);  // independent RNG

        for (java.util.Map.Entry<TaxiType, java.util.List<Integer>> e : byType.entrySet()) {
            java.util.List<Integer> ids = e.getValue();
            int target = Math.max(minPerType, (int)(ids.size() * fraction));
            target = Math.min(target, ids.size());  // cap at type fleet size
            // Reservoir-style: shuffle a copy and take first `target`
            java.util.Collections.shuffle(ids, sampleRng);
            for (int i = 0; i < target; i++) sampled.add(ids.get(i));
            System.out.println("  [B7] " + e.getKey() + ": sampled " + target + "/" + ids.size());
        }
        return sampled;
    }

    /**
     * Calculate distance between two points using Haversine formula × Manhattan factor.
     * Package-private — used by ShiftSimulator state machine.
     */
    double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
        final double EARTH_RADIUS_KM = config.getEarthRadiusKm();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c * config.getManhattanFactor();
    }

    /**
     * Validate coordinates against spatial layers (shapefile-based land/water check).
     * Returns true if point is on valid driveable land, or if spatial validation is disabled.
     *
     * @param lon Longitude
     * @param lat Latitude
     * @return true if valid location for taxi operation
     */
    /** Package-private — also used by ShiftSimulator (B2). */
    boolean isValidLocation(double lon, double lat) {
        if (geoValidator == null) return true;  // validation disabled
        return geoValidator.isValidLocation(lon, lat);
    }

    /**
     * Returns sampling bounds for random point generation.
     * Uses city polygon envelope if prefecture boundary is configured,
     * otherwise falls back to config rectangle bounds.
     *
     * @return double[] {minLon, maxLon, minLat, maxLat}
     */
    private double[] getSamplingBounds() {
        if (geoValidator != null && geoValidator.isCityBoundaryEnabled()) {
            org.locationtech.jts.geom.Envelope env = geoValidator.getCityEnvelope();
            return new double[]{env.getMinX(), env.getMaxX(), env.getMinY(), env.getMaxY()};
        }
        return new double[]{
            config.getBoundsMinLon(), config.getBoundsMaxLon(),
            config.getBoundsMinLat(), config.getBoundsMaxLat()
        };
    }

    /**
     * Pick the NEAREST transport-hub zone to {@code (lon, lat)}. Used when a
     * taxi enters AT_STAND mid-shift — DESIGN.md §2.3 says "Move position to
     * nearest hub-zone". Returns null if no hub zones are configured.
     */
    DestinationZone selectNearestHubZone(double lon, double lat) {
        DestinationZone nearest = null;
        double minDist = Double.MAX_VALUE;
        for (DestinationZone zone : destinationZones) {
            if (!zone.isTransportHub()) continue;
            double d = zone.getDistanceFromCenter(lon, lat);
            if (d < minDist) {
                minDist = d;
                nearest = zone;
            }
        }
        return nearest;
    }

    /**
     * V5.2: Generate a random point near a center using configurable spatial distribution.
     * Replaces the old radial-uniform polar sampling (random() * radius) that produced
     * visible starburst/circular artifacts due to density proportional to 1/r.
     *
     * Supports two modes:
     * - "gaussian": Bivariate Gaussian in Cartesian coordinates with configurable aspect ratio.
     *   Produces natural bell-curve density decay from center — denser core, soft edges.
     * - "uniform": Sqrt-corrected polar sampling (same as truck DeliveryZone.java:458).
     *   Produces flat density across the circle — no center-clustering.
     *
     * Both modes support per-city aspect ratio to break circular symmetry (e.g., Kobe E-W).
     *
     * @param centerLon center longitude of zone/station/previous dropoff
     * @param centerLat center latitude
     * @param radiusKm  effective radius in km
     * @return double[]{lon, lat}
     */
    private double[] generatePointNearCenter(double centerLon, double centerLat, double radiusKm) {
        String mode = config.getSpatialDistributionMode();
        double aspectX = config.getSpatialAspectX();
        double aspectY = config.getSpatialAspectY();
        double jitterM = config.getSpatialJitterMeters();

        double dx, dy; // offsets in km (Cartesian: dx = east-west, dy = north-south)

        if ("gaussian".equals(mode)) {
            double sigma = radiusKm / config.getSpatialGaussianSigmaFactor();
            dx = random.nextGaussian() * sigma * aspectX;
            dy = random.nextGaussian() * sigma * aspectY;

            // Soft clip to prevent extreme outliers beyond zone edge
            double dist = Math.sqrt(dx * dx + dy * dy);
            double maxDist = radiusKm * config.getSpatialGaussianClipFactor();
            if (dist > maxDist) {
                double scale = maxDist / dist;
                dx *= scale;
                dy *= scale;
            }
        } else {
            // "uniform" — sqrt-corrected for proper area density
            // Math.sqrt(random) ensures uniform point density across the circle area
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(random.nextDouble()) * radiusKm;
            dx = distance * Math.cos(angle) * aspectX;
            dy = distance * Math.sin(angle) * aspectY;
        }

        // Add micro-jitter to break any residual geometric patterns
        if (jitterM > 0) {
            dx += (random.nextDouble() - 0.5) * 2 * (jitterM / 1000.0);
            dy += (random.nextDouble() - 0.5) * 2 * (jitterM / 1000.0);
        }

        // Convert km offsets to lon/lat (latitude correction for longitude)
        double lon = centerLon + dx / (111.32 * Math.cos(Math.toRadians(centerLat)));
        double lat = centerLat + dy / 111.32;

        return new double[]{lon, lat};
    }

    /**
     * Nearest-valid-cell spiral-grid scan (DESIGN.md §2.6).
     *
     * <p>When a sampled dropoff fails {@link TaxiGeoValidator#isValidLocation},
     * search outward for the nearest valid land cell. Replaces the legacy
     * rejection-resampling approach in the shift_time engine — it eliminates
     * the water/river-coast bias where rejected pairs cluster near boundaries
     * (Sumida / Arakawa / Tama / Tokyo Bay coast) and the surviving sample
     * distribution drifts to longer inland trips.
     *
     * <p>Search pattern: concentric rings at radii 50m, 100m, 150m, ... up to
     * {@code radiusKm}; at each ring, 8 angular samples 45° apart (N, NE, E, SE,
     * S, SW, W, NW). Up to ~80 {@code isValidLocation} calls for 500m radius.
     * The underlying {@link TaxiGeoValidator#isOnLand} caches results at ~100m
     * resolution, so repeated nearby calls are cheap.
     *
     * <p>The flat-earth approximation for converting km offsets to lon/lat is
     * accurate to better than 1m at Tokyo's latitude over a 500m radius —
     * negligible compared to the 50m grid resolution.
     *
     * @param lon initial sampled longitude (failed validation)
     * @param lat initial sampled latitude  (failed validation)
     * @param radiusKm maximum search radius in km
     * @return {@code [lon, lat]} of nearest valid cell, or {@code null} if none
     *         found within radius (caller falls back to legacy resampling)
     */
    /** Package-private — also used by ShiftSimulator (B2). */
    double[] projectToNearestValidCell(double lon, double lat, double radiusKm) {
        if (geoValidator == null) {
            return new double[]{lon, lat};  // no validator → accept as-is
        }
        if (geoValidator.isValidLocation(lon, lat)) {
            return new double[]{lon, lat};  // already valid (defensive)
        }
        final double STEP_KM = 0.05;       // 50 m grid spacing
        final int ANGULAR_SAMPLES = 8;     // 45° apart
        final double cosLat = Math.cos(Math.toRadians(lat));
        final int maxRings = (int) Math.ceil(radiusKm / STEP_KM);

        for (int ring = 1; ring <= maxRings; ring++) {
            double r = ring * STEP_KM;
            for (int a = 0; a < ANGULAR_SAMPLES; a++) {
                double bearing = 2.0 * Math.PI * a / ANGULAR_SAMPLES;
                // km offsets → lon/lat (flat-earth at Tokyo latitude is fine over 500m)
                double dLon = (r * Math.cos(bearing)) / (111.32 * cosLat);
                double dLat = (r * Math.sin(bearing)) / 111.32;
                double candLon = lon + dLon;
                double candLat = lat + dLat;
                if (geoValidator.isValidLocation(candLon, candLat)) {
                    return new double[]{candLon, candLat};
                }
            }
        }
        return null;  // no valid cell within radius
    }

    /**
     * Phase 3 (B1) — Single-sample log-normal loaded-leg dropoff (DESIGN.md §2.6).
     *
     * <p>Sample the loaded-leg distance from {@code lognormal(mu, sigma)} where
     * {@code mu = ln(mean) - sigma^2/2} so the arithmetic mean equals
     * {@code config.getTripDistanceAverage()} (4.6 km for FY2024). Pick a
     * uniform random bearing and place the dropoff at exactly that distance
     * from the pickup. If the resulting position fails spatial validation,
     * project to the nearest valid land cell within
     * {@code config.getProjectionRadiusKm()} via the spiral-grid scan.
     *
     * <p>This is the "no rejection-resampling" path — distance is sampled once,
     * and a failure to find a valid cell within the projection radius returns
     * null so the caller can fall back to the legacy zone-based selection.
     *
     * <p>The unbiased flat-earth approximation is accurate to {@literal <}0.1% over
     * the realized distance range [0.5, 15] km at Tokyo's latitude.
     *
     * @return {@code [lon, lat]} of the loaded-leg dropoff, or {@code null} if
     *         spatial projection failed (caller falls through to legacy path)
     */
    /** Package-private — also called by ShiftSimulator (B2 / Phase 4 state machine). */
    double[] sampleLogNormalLoadedLeg(double pickupLon, double pickupLat) {
        // Sample log-normal ROAD distance (road-km, matching THTA's reporting unit)
        // with -sigma^2/2 correction so arithmetic mean of the unbounded sample
        // equals config.getTripDistanceAverage() = 4.6 km.
        //
        // B7.5: reject-and-redraw within [trip.distance.min, trip.distance.max]
        // instead of clamping. Eliminates the at-floor spike artifact (Trip
        // Distance Min Check observed 0.07 km below 0.5 km floor pre-fix).
        // Attempt budget 100; fall back to clamp on exhaustion (rare).
        final double sigma = config.getTripDistanceLoadedSigma();
        final double mu = Math.log(config.getTripDistanceAverage()) - 0.5 * sigma * sigma;
        final double minRoad = config.getTripDistanceMin();
        final double maxRoad = config.getTripDistanceMax();
        double roadKm;
        int attempts = 0;
        do {
            roadKm = Math.exp(mu + sigma * random.nextGaussian());
            attempts++;
        } while ((roadKm < minRoad || roadKm > maxRoad) && attempts < 100);
        if (roadKm < minRoad || roadKm > maxRoad) {
            roadKm = Math.max(minRoad, Math.min(maxRoad, roadKm));
        }

        // CRITICAL: TaxiTrip.distanceKm() stores trip distance as Haversine ×
        // Manhattan factor (1.4 for Tokyo). To produce a stored road-km of
        // `roadKm`, place the dropoff at `roadKm / manhattan_factor` Haversine
        // distance from pickup. Without this conversion, the realized trip
        // distribution would be 1.4× too long.
        final double haversineKm = roadKm / config.getManhattanFactor();

        // Uniform random bearing → place dropoff at exactly `haversineKm` from pickup.
        // Flat-earth approximation at Tokyo's latitude (35.6°N) is accurate to
        // <0.1% over the realized [0.5/1.4, 15/1.4] km Haversine range.
        final double bearing = 2.0 * Math.PI * random.nextDouble();
        final double cosLat = Math.cos(Math.toRadians(pickupLat));
        final double dLon = (haversineKm * Math.cos(bearing)) / (111.32 * cosLat);
        final double dLat = (haversineKm * Math.sin(bearing)) / 111.32;
        final double dropLon = pickupLon + dLon;
        final double dropLat = pickupLat + dLat;

        // Validate; project to nearest valid cell if invalid.
        double[] result;
        if (geoValidator != null && !geoValidator.isValidLocation(dropLon, dropLat)) {
            result = projectToNearestValidCell(dropLon, dropLat,
                config.getProjectionRadiusKm());  // may return null → caller falls back
            if (result == null) return null;
        } else {
            result = new double[]{dropLon, dropLat};
        }

        // B7.5: post-projection floor check. Projection can shorten the road
        // distance below the configured minimum (the spiral lands within 500 m
        // of the original target, which may be CLOSER to pickup than the
        // sample). If the final road distance falls below trip.distance.min,
        // reject this sample so the caller re-tries (continue cycle from
        // current position; cheap retry budget per cycle).
        double finalRoadKm = TaxiTrip.distanceKm(pickupLon, pickupLat, result[0], result[1]);
        if (finalRoadKm < minRoad) {
            return null;
        }
        return result;
    }

    /**
     * V4.0: Assign 3-5 familiar zones to a LOCAL taxi based on geographic clustering
     *
     * Strategy:
     * 1. Find seed zone closest to taxi's home location
     * 2. Find 2-4 additional zones within 5-10km of seed zone
     * 3. Prefer zone type diversity (mix residential, commercial, business)
     *
     * @param taxi The LOCAL taxi to assign zones to
     * @return List of 3-5 zone IDs
     */
    private List<String> assignFamiliarZones(TaxiAgent taxi) {
        List<String> familiarZones = new ArrayList<>();

        if (destinationZones.isEmpty()) {
            return familiarZones;  // Return empty list if no zones loaded
        }

        // Step 1: Find seed zone (closest to home)
        DestinationZone seedZone = findClosestZone(taxi.getHomeLongitude(), taxi.getHomeLatitude());
        familiarZones.add(seedZone.getZoneId());

        // Step 2: Find nearby zones within 5-10km
        List<DestinationZone> candidates = new ArrayList<>();
        for (DestinationZone zone : destinationZones) {
            if (zone.getZoneId().equals(seedZone.getZoneId())) continue;

            double distance = seedZone.getDistanceFromCenter(
                zone.getCenterLon(), zone.getCenterLat());

            if (distance >= 5.0 && distance <= 10.0) {
                candidates.add(zone);
            }
        }

        // Step 3: Select 2-4 zones with type diversity
        Map<String, Integer> typeCount = new HashMap<>();
        typeCount.put(seedZone.getZoneType(), 1);

        int targetCount = 3 + random.nextInt(3); // 3-5 zones
        while (familiarZones.size() < targetCount && !candidates.isEmpty()) {
            // Prefer zones with underrepresented types
            DestinationZone bestZone = null;
            int minTypeCount = Integer.MAX_VALUE;

            for (DestinationZone candidate : candidates) {
                int count = typeCount.getOrDefault(candidate.getZoneType(), 0);
                if (count < minTypeCount) {
                    minTypeCount = count;
                    bestZone = candidate;
                }
            }

            if (bestZone != null) {
                familiarZones.add(bestZone.getZoneId());
                typeCount.put(bestZone.getZoneType(),
                    typeCount.getOrDefault(bestZone.getZoneType(), 0) + 1);
                candidates.remove(bestZone);
            } else {
                break;
            }
        }

        return familiarZones;
    }

    /**
     * V4.0: Find zone closest to given coordinates
     *
     * @param lon Longitude
     * @param lat Latitude
     * @return Closest destination zone
     */
    private DestinationZone findClosestZone(double lon, double lat) {
        DestinationZone closest = destinationZones.get(0);
        double minDistance = Double.MAX_VALUE;

        for (DestinationZone zone : destinationZones) {
            double distance = zone.getDistanceFromCenter(lon, lat);
            if (distance < minDistance) {
                minDistance = distance;
                closest = zone;
            }
        }

        return closest;
    }

    /**
     * Print essential statistics summary
     */
    private void printStatistics() {
        // Separate passenger and empty trips
        long passengerTrips = allTrips.stream().filter(TaxiTrip::isPassengerIn).count();
        long emptyTrips = allTrips.stream().filter(t -> !t.isPassengerIn()).count();

        double passengerDistance = allTrips.stream()
            .filter(TaxiTrip::isPassengerIn)
            .mapToDouble(TaxiTrip::getDistanceKm).sum();
        double emptyDistance = allTrips.stream()
            .filter(t -> !t.isPassengerIn())
            .mapToDouble(TaxiTrip::getDistanceKm).sum();

        double totalDistance = passengerDistance + emptyDistance;
        double totalRevenue = allTrips.stream()
            .filter(TaxiTrip::isPassengerIn)
            .mapToDouble(TaxiTrip::getFareYen).sum();

        double emptyRunningRatio = 100.0 * emptyDistance / totalDistance;
        double avgPassengerTripsPerTaxi = (double) passengerTrips / config.getTaxiFleetSize();

        System.out.println("\n[SUMMARY]");
        System.out.println("  Taxis: " + config.getTaxiFleetSize());
        System.out.println("  Passenger trips: " + passengerTrips +
            " (avg " + String.format("%.1f", avgPassengerTripsPerTaxi) + " per taxi)");
        System.out.println("  Empty trips: " + emptyTrips);
        System.out.println("  Total distance: " + String.format("%.0f", totalDistance) + " km");
        System.out.println("  Empty running ratio: " + String.format("%.1f%%", emptyRunningRatio));
        System.out.println("  Total revenue: ¥" + String.format("%,d", (long)totalRevenue));
    }

    /**
     * Write unified dashboard.csv to run directory via MetricsDashboard.
     * Includes per-taxi-type breakdown (LOCAL/CITYWIDE/HUB) and reference targets.
     */
    private void writeDashboard(String runDir) {
        long passengerTrips = allTrips.stream().filter(TaxiTrip::isPassengerIn).count();
        long emptyTrips = allTrips.stream().filter(t -> !t.isPassengerIn()).count();
        double passengerDist = allTrips.stream().filter(TaxiTrip::isPassengerIn)
                .mapToDouble(TaxiTrip::getDistanceKm).sum();
        double emptyDist = allTrips.stream().filter(t -> !t.isPassengerIn())
                .mapToDouble(TaxiTrip::getDistanceKm).sum();
        double totalRevenue = allTrips.stream().filter(TaxiTrip::isPassengerIn)
                .mapToDouble(TaxiTrip::getFareYen).sum();
        long nightTrips = allTrips.stream().filter(TaxiTrip::isPassengerIn)
                .filter(TaxiTrip::isNightTrip).count();
        double avgFare = passengerTrips > 0 ? totalRevenue / passengerTrips : 0;
        double avgDist = passengerTrips > 0 ? passengerDist / passengerTrips : 0;

        int localCount = (int) taxiFleet.stream().filter(t -> t.getTaxiType() == TaxiType.LOCAL).count();
        int citywideCount = (int) taxiFleet.stream().filter(t -> t.getTaxiType() == TaxiType.CITYWIDE).count();
        int hubCount = (int) taxiFleet.stream().filter(t -> t.getTaxiType() == TaxiType.HUB).count();

        Map<String, Double> metrics = MetricsDashboard.buildTaxiMetrics(
                config.getTaxiFleetSize(), localCount, citywideCount, hubCount,
                passengerTrips, emptyTrips,
                passengerDist, emptyDist,
                totalRevenue, nightTrips, avgFare, avgDist);

        // Per-taxi-type breakdown (LOCAL / CITYWIDE / HUB) — single pass
        Map<Integer, TaxiType> taxiTypeMap = new HashMap<>();
        for (TaxiAgent taxi : taxiFleet) {
            taxiTypeMap.put(taxi.getTaxiId(), taxi.getTaxiType());
        }

        Map<TaxiType, long[]> typeTripCounts = new EnumMap<>(TaxiType.class);
        Map<TaxiType, double[]> typeDistAndRevenue = new EnumMap<>(TaxiType.class);
        for (TaxiType type : TaxiType.values()) {
            typeTripCounts.put(type, new long[]{0});
            typeDistAndRevenue.put(type, new double[]{0.0, 0.0});
        }
        for (TaxiTrip trip : allTrips) {
            TaxiType type = taxiTypeMap.get(trip.getAssignedTaxiId());
            if (type == null) continue;
            typeTripCounts.get(type)[0]++;
            typeDistAndRevenue.get(type)[0] += trip.getDistanceKm();
            if (trip.isPassengerIn()) {
                typeDistAndRevenue.get(type)[1] += trip.getFareYen();
            }
        }
        for (TaxiType type : TaxiType.values()) {
            String label = type.toString().toLowerCase();
            long trips = typeTripCounts.get(type)[0];
            double dist = typeDistAndRevenue.get(type)[0];
            double revenue = typeDistAndRevenue.get(type)[1];
            metrics.put("TAXI_TYPE." + label + "_trips", (double) trips);
            metrics.put("TAXI_TYPE." + label + "_distance_km", dist);
            metrics.put("TAXI_TYPE." + label + "_avg_dist_km", trips > 0 ? dist / trips : 0.0);
            metrics.put("TAXI_TYPE." + label + "_revenue_yen", revenue);
        }

        // TX5: LOCAL-taxi territory escape rate (observability only)
        long localPassengerTrips = typeTripCounts.get(TaxiType.LOCAL)[0];
        long outOfTerritory = localTaxiOutOfTerritoryCount.get();
        metrics.put("LOCAL_TAXI.out_of_territory_trips", (double) outOfTerritory);
        metrics.put("LOCAL_TAXI.out_of_territory_rate",
                localPassengerTrips > 0 ? (double) outOfTerritory / localPassengerTrips : 0.0);

        // Reference targets
        metrics.put("REFERENCE.configured_fleet_size", (double) config.getTaxiFleetSize());
        metrics.put("REFERENCE.avg_passenger_trips_per_taxi",
                passengerTrips > 0 ? (double) passengerTrips / config.getTaxiFleetSize() : 0.0);

        MetricsDashboard.writeDashboard(runDir, "taxi", config.getCityName(), metrics);
    }

    /**
     * Main simulation execution
     */
    public void run(String[] args) {
        // Load configuration
        String configFile = (args.length > 0) ? args[0] : DEFAULT_CONFIG_FILE;
        loadConfiguration(configFile);

        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  " + config.getCityName().toUpperCase() + " TAXI ABM V5.0 - MULTI-CITY PSEUDO PFLOW");
        System.out.println("═══════════════════════════════════════════════════════");

        // Run simulation phases
        initializeTaxis();
        generateTrips();
        printStatistics();

        // Print spatial validation stats if enabled
        if (geoValidator != null) {
            geoValidator.printStatistics();
        }

        // Export results
        System.out.println("\n[CHECKPOINT] Exporting results...");
        TaxiDataExporter exporter = new TaxiDataExporter(config.getOutputDirectory());
        exporter.setDestinationZones(destinationZones);
        exporter.exportAll(taxiFleet, allTrips);

        // Write unified dashboard.csv to run directory
        String runDir = exporter.getRunDirectory();
        writeDashboard(runDir);

        // B7: flush diagnostic CSV (no-op if disabled)
        if (diagnosticWriter != null) {
            try {
                diagnosticWriter.flush(runDir);
            } catch (java.io.IOException e) {
                System.err.println("[B7] WARN: failed to flush diagnostic.csv: " + e.getMessage());
            }
        }

        // Validation against baseline metrics
        String baselinePath = "config/taxi/validation/taxi_baseline_" +
                config.getCityName().toLowerCase() + ".csv";
        File baselineFile = new File(baselinePath);
        if (baselineFile.exists()) {
            TaxiValidationEngine validator = new TaxiValidationEngine();
            validator.loadBaseline(baselinePath);
            TaxiValidationEngine.ValidationReport report = validator.validate(taxiFleet, allTrips, config);
            report.printReport();
            report.writeToFile(runDir);
        } else {
            System.out.println("\n[VALIDATION] No baseline CSV found at: " + baselinePath);
            System.out.println("[VALIDATION] Skipping validation (create config/taxi/validation/taxi_baseline_"
                    + config.getCityName().toLowerCase() + ".csv to enable).");
        }

        System.out.println("\n[COMPLETE] Output: " + runDir);
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        TaxiSimulation sim = new TaxiSimulation();
        sim.run(args);
    }
}
