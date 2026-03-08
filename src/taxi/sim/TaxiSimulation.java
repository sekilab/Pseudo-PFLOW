package taxi.sim;

import java.util.*;

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

    // Data structures
    private List<TaxiAgent> taxiFleet;
    private List<TaxiTrip> allTrips;  // V3.1: Now includes both passenger and empty trips
    private List<DestinationZone> destinationZones;
    private Random random;

    // Spatial validation (null if disabled)
    private TaxiGeoValidator geoValidator;

    // Transport network index for station/airport proximity (null if disabled)
    private TaxiTransportIndex transportIndex;

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
        int stationCounter = 1, settlementCounter = 1, airportCounter = 1, portCounter = 1;

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

        int numTaxis = config.getTaxiFleetSize();
        int localCount = 0, citywideCount = 0, hubCount = 0;

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

            // Determine taxi type based on configured probabilities
            TaxiType taxiType;
            double typeRoll = random.nextDouble();
            if (typeRoll < config.getTaxiTypeLocalProb()) {
                taxiType = TaxiType.LOCAL;
                localCount++;
            } else if (typeRoll < config.getTaxiTypeLocalProb() + config.getTaxiTypeCitywideProb()) {
                taxiType = TaxiType.CITYWIDE;
                citywideCount++;
            } else {
                taxiType = TaxiType.HUB;
                hubCount++;
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

            // Create taxi agent with type and familiar area radius
            double familiarRadius = config.getLocalFamiliarRadiusKm();
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
        System.out.println("  HUB: " + hubCount + " (" +
            String.format("%.1f%%", 100.0 * hubCount / numTaxis) + ")");
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
        System.out.println("[CHECKPOINT] Generating trips (passenger + empty)...");

        int tripIdCounter = 0;
        int rejectedTooShort = 0;
        int rejectedTooLong = 0;
        int nearbyTripsCount = 0;
        int totalPassengerTrips = 0;
        int totalEmptyTrips = 0;

        final double EMPTY_TRIP_THRESHOLD_KM = 0.05;  // Only create empty trip if distance > 0.05 km

        for (TaxiAgent taxi : taxiFleet) {
            long currentTime = taxi.getShiftStartTime();

            // Tracking variables for nearby trip logic
            double lastDropoffLon = taxi.getHomeLongitude();
            double lastDropoffLat = taxi.getHomeLatitude();
            boolean hasLastDropoff = false;

            // Determine number of trips for this taxi
            // Gaussian distribution around AVG_TRIPS_PER_TAXI
            int numTrips = (int)(config.getTaxiTripsAverage() +
                random.nextGaussian() * config.getTaxiTripsStddev());
            numTrips = Math.max(config.getTaxiTripsMin(),
                Math.min(config.getTaxiTripsMax(), numTrips));

            // Generate trips for this taxi
            for (int tripNum = 0; tripNum < numTrips; tripNum++) {
                // Decide if this trip should be near previous dropoff
                boolean useNearbyStart = config.isUseNearbyTrips() && hasLastDropoff &&
                    (random.nextDouble() < config.getNearbyTripProbability());

                // Try to generate a valid passenger trip
                final int MAX_ATTEMPTS = 50;
                TaxiTrip passengerTrip = null;

                for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                    double pickupLon, pickupLat;

                    // NEARBY TRIP LOGIC: Start near previous dropoff
                    if (useNearbyStart) {
                        // Generate pickup within configurable radius of last dropoff
                        double angle = random.nextDouble() * 2 * Math.PI;
                        double distance = random.nextDouble() * config.getNearbyTripRadiusKm();

                        // Convert to lat/lon offset (approximate)
                        double lonOffset = distance / 111.32 * Math.cos(Math.toRadians(lastDropoffLat));
                        double latOffset = distance / 111.32;

                        pickupLon = lastDropoffLon + lonOffset * Math.cos(angle);
                        pickupLat = lastDropoffLat + latOffset * Math.sin(angle);

                        // Spatial validation for nearby pickup (city boundary + land check)
                        if (!isValidLocation(pickupLon, pickupLat)) {
                            continue;  // retry with new random offset
                        }
                    } else {
                        // Use smart destination selection based on taxi type and time
                        double[] pickupLocation = selectSmartDestination(taxi, currentTime);
                        pickupLon = pickupLocation[0];
                        pickupLat = pickupLocation[1];

                        // Spatial validation for smart pickup
                        if (!isValidLocation(pickupLon, pickupLat)) {
                            continue;  // retry
                        }
                    }

                    // Generate dropoff using smart destination selection
                    double[] dropoffLocation = selectSmartDestination(taxi, currentTime);
                    double dropoffLon = dropoffLocation[0];
                    double dropoffLat = dropoffLocation[1];

                    // Validate dropoff (city boundary + land check)
                    if (!isValidLocation(dropoffLon, dropoffLat)) {
                        continue;  // Try again
                    }

                    // Create passenger trip
                    passengerTrip = new TaxiTrip(tripIdCounter, pickupLon, pickupLat,
                        dropoffLon, dropoffLat, currentTime, true);  // true = passenger trip

                    // Validate trip distance (actual Haversine distance)
                    double actualDistance = passengerTrip.getDistanceKm();

                    if (actualDistance < config.getTripDistanceMin()) {
                        rejectedTooShort++;
                        continue;
                    }
                    if (actualDistance > config.getTripDistanceMax()) {
                        rejectedTooLong++;
                        continue;
                    }

                    // Valid trip created!
                    break;
                }

                // If we successfully generated a passenger trip
                if (passengerTrip != null) {
                    // Store pickup/dropoff coordinates for passenger trip
                    double passengerPickupLon = passengerTrip.getPickupLongitude();
                    double passengerPickupLat = passengerTrip.getPickupLatitude();
                    double passengerDropoffLon = passengerTrip.getDropoffLongitude();
                    double passengerDropoffLat = passengerTrip.getDropoffLatitude();

                    // V3.1: Generate empty trip if taxi needs to reposition
                    if (hasLastDropoff) {
                        // Calculate distance from last dropoff to current pickup
                        double emptyDistance = calculateDistance(
                            lastDropoffLon, lastDropoffLat,
                            passengerPickupLon, passengerPickupLat
                        );

                        // Only create empty trip if distance > threshold
                        if (emptyDistance > EMPTY_TRIP_THRESHOLD_KM) {
                            // Create empty trip with current tripIdCounter
                            TaxiTrip emptyTrip = TaxiTrip.createEmptyTrip(
                                tripIdCounter,
                                lastDropoffLon, lastDropoffLat,
                                passengerPickupLon, passengerPickupLat,
                                currentTime
                            );

                            // Estimate times for empty trip
                            long emptyTravelTime = TaxiTrip.estimateTravelTime(emptyTrip.getDistanceKm());
                            long emptyPickupTime = currentTime;
                            long emptyDropoffTime = emptyPickupTime + emptyTravelTime;

                            emptyTrip.setTimes(emptyPickupTime, emptyDropoffTime);
                            emptyTrip.setAssignedTaxiId(taxi.getTaxiId());
                            emptyTrip.setStatus(TaxiTrip.TripStatus.COMPLETED);

                            taxi.assignTrip(emptyTrip);
                            allTrips.add(emptyTrip);
                            totalEmptyTrips++;

                            // Increment AFTER adding empty trip
                            tripIdCounter++;

                            // Update current time to after empty trip
                            currentTime = emptyDropoffTime;
                        }
                    }

                    // Now create the passenger trip with NEW tripIdCounter value
                    // (which may have been incremented if empty trip was created)
                    passengerTrip = new TaxiTrip(tripIdCounter,
                        passengerPickupLon, passengerPickupLat,
                        passengerDropoffLon, passengerDropoffLat,
                        currentTime, true);

                    // Process the passenger trip
                    long travelTime = TaxiTrip.estimateTravelTime(passengerTrip.getDistanceKm());
                    long pickupTime = currentTime + config.getTaxiPickupTime();
                    long dropoffTime = pickupTime + travelTime;

                    passengerTrip.setTimes(pickupTime, dropoffTime);
                    passengerTrip.setAssignedTaxiId(taxi.getTaxiId());
                    passengerTrip.setStatus(TaxiTrip.TripStatus.COMPLETED);

                    taxi.assignTrip(passengerTrip);
                    allTrips.add(passengerTrip);

                    // Update tracking variables for next trip
                    lastDropoffLon = passengerTrip.getDropoffLongitude();
                    lastDropoffLat = passengerTrip.getDropoffLatitude();
                    hasLastDropoff = true;

                    if (useNearbyStart) {
                        nearbyTripsCount++;
                    }

                    // Increment AFTER adding passenger trip
                    tripIdCounter++;
                    totalPassengerTrips++;
                    currentTime = dropoffTime + config.getTaxiBreakTime();

                    // Check if shift is ending
                    if (currentTime >= taxi.getShiftEndTime()) {
                        break;
                    }
                } else {
                    // Failed to generate valid trip after MAX_ATTEMPTS
                    break;
                }
            }
        }

        double emptyRatio = 100.0 * totalEmptyTrips / allTrips.size();
        System.out.println("[CHECKPOINT] Generated " + allTrips.size() + " trips " +
            "(" + totalPassengerTrips + " passenger, " + totalEmptyTrips + " empty, " +
            String.format("%.1f%%", emptyRatio) + " empty ratio)");
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
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
    private boolean isValidLocation(double lon, double lat) {
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
     * V4.0: Select a transport hub zone using weighted probability
     * Time-dependent: airports boosted during night hours
     *
     * @param timeOfDay Current time in seconds since midnight
     * @return Selected transport hub zone
     */
    private DestinationZone selectTransportHubZone(long timeOfDay) {
        // Filter transport hub zones
        List<DestinationZone> hubZones = new ArrayList<>();
        for (DestinationZone zone : destinationZones) {
            if (zone.isTransportHub()) {
                hubZones.add(zone);
            }
        }

        if (hubZones.isEmpty()) {
            // Fallback to random zone if no hubs defined
            return destinationZones.get(random.nextInt(destinationZones.size()));
        }

        // Calculate weights (boost airports at night)
        boolean isNight = config.isNightHours(timeOfDay);
        double totalWeight = 0.0;
        double[] weights = new double[hubZones.size()];

        for (int i = 0; i < hubZones.size(); i++) {
            DestinationZone zone = hubZones.get(i);
            double weight = 1.0;

            // Boost airports during night hours (22:00-06:00)
            // V5.0: Use shapefile-based airport detection instead of name heuristics
            if (isNight && zone.isNearAirport()) {
                weight *= config.getHotspotAirportNightMultiplier();
            } else if (isNight && zone.getName().toLowerCase().contains("airport")) {
                // Fallback: string matching for zones without transport index enrichment
                weight *= config.getHotspotAirportNightMultiplier();
            }

            weights[i] = weight;
            totalWeight += weight;
        }

        // Select based on weighted probability
        double rand = random.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (int i = 0; i < hubZones.size(); i++) {
            cumulative += weights[i];
            if (rand <= cumulative) {
                return hubZones.get(i);
            }
        }

        return hubZones.get(0);  // Fallback
    }

    /**
     * Select destination using time-dependent attractiveness scoring
     *
     * @param taxi The taxi agent generating the trip
     * @param currentTime Current time in seconds since midnight
     * @return array [lon, lat] of selected destination
     */
    private double[] selectSmartDestination(TaxiAgent taxi, long currentTime) {
        TaxiType taxiType = taxi.getTaxiType();

        // V4.0: HUB type taxis: use transport hub zone selection
        if (taxiType == TaxiType.HUB) {
            DestinationZone hubZone = selectTransportHubZone(currentTime);

            // Generate random point within selected hub zone, with spatial validation retry
            for (int retry = 0; retry <= 5; retry++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = random.nextDouble() * hubZone.getRadiusKm();

                double lonOffset = distance / 111.32 * Math.cos(Math.toRadians(hubZone.getCenterLat()));
                double latOffset = distance / 111.32;

                double lon = hubZone.getCenterLon() + lonOffset * Math.cos(angle);
                double lat = hubZone.getCenterLat() + latOffset * Math.sin(angle);

                if (retry == 5 || isValidLocation(lon, lat)) {
                    return new double[]{lon, lat};
                }
            }
            // Unreachable — loop always returns
            return new double[]{hubZone.getCenterLon(), hubZone.getCenterLat()};
        }

        // Get time period for attractiveness calculation
        int timePeriod = config.getTimePeriod(currentTime);

        // Calculate attractiveness scores for all zones
        double totalAttractiveness = 0.0;
        double[] attractiveness = new double[destinationZones.size()];

        for (int i = 0; i < destinationZones.size(); i++) {
            DestinationZone zone = destinationZones.get(i);
            double score = zone.calculateAttractiveness(timePeriod,
                config.getAttractivenessBeta1(),
                config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(),
                config.getAttractivenessBeta4());

            // V4.0: LOCAL taxis: boost attractiveness of familiar zones
            if (taxiType == TaxiType.LOCAL) {
                List<String> familiarZones = taxi.getFamiliarZoneIds();
                if (!familiarZones.isEmpty() && familiarZones.contains(zone.getZoneId())) {
                    score *= 5.0;  // Strong boost for familiar zones
                } else if (familiarZones.isEmpty()) {
                    // Fallback to radius-based if zone clustering failed
                    double distance = zone.getDistanceFromCenter(
                        taxi.getHomeLongitude(), taxi.getHomeLatitude());
                    if (distance <= taxi.getFamiliarAreaRadiusKm()) {
                        score *= 3.0;
                    } else {
                        score *= 0.1;
                    }
                } else {
                    score *= 0.05;  // Heavily penalize non-familiar zones
                }
            }

            attractiveness[i] = score;
            totalAttractiveness += score;
        }

        // Select zone based on weighted probability
        double rand = random.nextDouble() * totalAttractiveness;
        double cumulative = 0.0;
        DestinationZone selectedZone = destinationZones.get(0);

        for (int i = 0; i < destinationZones.size(); i++) {
            cumulative += attractiveness[i];
            if (rand <= cumulative) {
                selectedZone = destinationZones.get(i);
                break;
            }
        }

        // V5.0: Station-biased point generation — 40% chance to generate near station
        if (transportIndex != null && selectedZone.isNearStation() &&
            random.nextDouble() < config.getStationBiasProb()) {
            TaxiTransportIndex.NearestResult station = transportIndex.findNearestStation(
                selectedZone.getCenterLon(), selectedZone.getCenterLat(), 2.0);
            if (station != null) {
                // Generate within 500m of station
                double stationAngle = random.nextDouble() * 2 * Math.PI;
                double stationDist = random.nextDouble() * 0.5;  // 0-500m
                double lonOff = stationDist / 111.32 * Math.cos(Math.toRadians(station.lat));
                double latOff = stationDist / 111.32;
                double sLon = station.lon + lonOff * Math.cos(stationAngle);
                double sLat = station.lat + latOff * Math.sin(stationAngle);

                // Validate (city boundary + land check)
                if (isValidLocation(sLon, sLat)) {
                    return new double[]{sLon, sLat};
                }
                // If invalid (e.g., station is near river), fall through to normal generation
            }
        }

        // Generate random point within selected zone, with spatial validation retry
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * selectedZone.getRadiusKm();

        double lonOffset = distance / 111.32 * Math.cos(Math.toRadians(selectedZone.getCenterLat()));
        double latOffset = distance / 111.32;

        double lon = selectedZone.getCenterLon() + lonOffset * Math.cos(angle);
        double lat = selectedZone.getCenterLat() + latOffset * Math.sin(angle);

        // Spatial validation — if invalid, retry up to 10 times with new random point in same zone
        if (geoValidator != null && !geoValidator.isValidLocation(lon, lat)) {
            for (int retry = 0; retry < 10; retry++) {
                angle = random.nextDouble() * 2 * Math.PI;
                distance = random.nextDouble() * selectedZone.getRadiusKm();
                lonOffset = distance / 111.32 * Math.cos(Math.toRadians(selectedZone.getCenterLat()));
                latOffset = distance / 111.32;
                lon = selectedZone.getCenterLon() + lonOffset * Math.cos(angle);
                lat = selectedZone.getCenterLat() + latOffset * Math.sin(angle);
                if (geoValidator.isValidLocation(lon, lat)) break;
            }
        }

        return new double[]{lon, lat};
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
        exporter.setDestinationZones(destinationZones);  // Pass zones for zone lookup
        exporter.exportAll(taxiFleet, allTrips);

        System.out.println("\n[COMPLETE] Output: " + exporter.getRunDirectory());
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        TaxiSimulation sim = new TaxiSimulation();
        sim.run(args);
    }
}
