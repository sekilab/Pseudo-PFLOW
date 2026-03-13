package truck.sim;

import truck.sim.spatial.GeoValidator;
import truck.sim.spatial.PointGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static truck.sim.TruckSimulationConstants.*;

/**
 * Selects destinations for delivery and empty trips.
 *
 * <p>Uses pre-computed zone distance matrix and nearby-zone lists from
 * {@link ZoneManager} to eliminate per-trip Haversine calculations and
 * linear zone scans.
 *
 * @author Truck ABM Framework
 * @version 3.0
 * @see DestinationResult
 */
public class DestinationSelector {

    private final TruckConfig config;
    private final ZoneManager zoneManager;
    private final GeoValidator geoValidator;
    private final PointGenerator pointGenerator;
    private final POIManager poiManager;
    private final OriginDestinationMatrix odMatrix;
    private final GenerationAttractionBalancer gaBalancer;
    private final CommodityRouter commodityRouter;
    private final MetropolitanConfig metroConfig;

    public DestinationSelector(TruckConfig config,
                               ZoneManager zoneManager, GeoValidator geoValidator,
                               PointGenerator pointGenerator, POIManager poiManager,
                               OriginDestinationMatrix odMatrix,
                               GenerationAttractionBalancer gaBalancer,
                               CommodityRouter commodityRouter,
                               MetropolitanConfig metroConfig) {
        this.config = config;
        this.zoneManager = zoneManager;
        this.geoValidator = geoValidator;
        this.pointGenerator = pointGenerator;
        this.poiManager = poiManager;
        this.odMatrix = odMatrix;
        this.gaBalancer = gaBalancer;
        this.commodityRouter = commodityRouter;
        this.metroConfig = metroConfig;
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTRA-METRO DESTINATION SELECTION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects destination for intra-metropolitan delivery trip.
     *
     * @param originZoneId Pre-computed origin zone ID (avoids redundant findZoneForLocation)
     * @return DestinationResult, or null if zone at capacity
     */
    public DestinationResult selectIntraMetroDestination(TruckAgent truck, double[] origin,
                                                         long currentTime, String commodityType,
                                                         String originZoneId) {
        // Strategy 1: G-A balanced selection
        if (gaBalancer != null && gaBalancer.canGenerate(originZoneId)) {
            DestinationResult balanced = selectBalancedDestination(truck, origin, originZoneId, currentTime, commodityType);
            if (balanced != null) return balanced;
        }

        // DELIVERY trucks: distance-constrained nearby fallback
        if (truck.getTruckType() == TruckType.DELIVERY) {
            return selectNearbyDestination(truck, origin, 35.0, originZoneId);
        }

        // Strategy 2: POI-based selection (non-DELIVERY trucks)
        if (config.getUsePOIDestinations() && poiManager != null && poiManager.hasPOIs()) {
            int timePeriod = zoneManager.getTimePeriod(currentTime);
            PointOfInterest targetPOI = poiManager.selectPOIForINTRATrip(truck, originZoneId, timePeriod);

            if (targetPOI != null) {
                double lon = targetPOI.getLongitude();
                double lat = targetPOI.getLatitude();

                if (geoValidator.isOnLand(lon, lat) && geoValidator.isValidPOILandUse(lon, lat)) {
                    double[] coords = pointGenerator.jitterPoint(lon, lat);
                    return new DestinationResult(coords, null, targetPOI.getPoiId());
                }
            }
        }

        // Strategy 3: Facility-based fallback
        double[] coords = selectFacilityBasedCoords(truck, originZoneId, currentTime);
        return DestinationResult.coordsOnly(coords);
    }

    /**
     * Legacy overload — computes originZoneId internally.
     * @deprecated Use the 5-argument version with pre-computed originZoneId.
     */
    public DestinationResult selectIntraMetroDestination(TruckAgent truck, double[] origin,
                                                         long currentTime, String commodityType) {
        String originZoneId = zoneManager.findZoneForLocation(origin[0], origin[1]);
        return selectIntraMetroDestination(truck, origin, currentTime, commodityType, originZoneId);
    }

    /**
     * Selects destination for inter-metropolitan delivery trip.
     */
    public DestinationResult selectInterMetroDestination(double[] origin) {
        MetropolitanConfig.Metropolitan secondaryMetro = metroConfig.getSecondaryMetro();
        for (int attempt = 0; attempt < 20; attempt++) {
            double lon = secondaryMetro.centerLon + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
            double lat = secondaryMetro.centerLat + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
            if (geoValidator.isValidTripEndpoint(lon, lat, 50.0)
                    && geoValidator.isOnMainland(lon, lat)) {
                return DestinationResult.coordsOnly(new double[]{lon, lat});
            }
        }
        return DestinationResult.coordsOnly(
            new double[]{secondaryMetro.centerLon, secondaryMetro.centerLat});
    }

    // ════════════════════════════════════════════════════════════════════════
    // BALANCED DESTINATION (OD MATRIX + G-A)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects destination with G-A balance constraints.
     * Uses pre-computed zone distance matrix instead of per-trip Haversine.
     */
    public DestinationResult selectBalancedDestination(TruckAgent truck, double[] origin,
                                                       String originZoneId, long currentTime,
                                                       String commodityType) {
        List<DeliveryZone> zones = zoneManager.getZones();
        int originIndex = zoneManager.getZoneListIndex(originZoneId);
        if (originIndex < 0) {
            double[] coords = selectSmartCoords(truck, currentTime);
            return DestinationResult.coordsOnly(coords);
        }

        Map<String, Double> candidates = new HashMap<>();
        TruckType truckType = truck.getTruckType();

        for (int destIndex = 0; destIndex < zones.size(); destIndex++) {
            DeliveryZone destZone = zones.get(destIndex);
            if (destZone.getZoneId().equals("MFS62")) continue;

            double odProb = odMatrix.getFlow(originIndex, destIndex);
            if (odProb > 0.0) {
                boolean isIntraZone = (destIndex == originIndex);
                // Fast distance from actual origin to zone center (not center-to-center)
                double dist = zoneManager.calculateDistanceFast(origin[0], origin[1],
                    destZone.getCenterLongitude(), destZone.getCenterLatitude());

                if (truckType == TruckType.DELIVERY) {
                    if (dist > 35.0 && !isIntraZone) continue;
                    odProb *= Math.exp(-dist / DELIVERY_DISTANCE_DECAY_FACTOR);
                } else if (truckType == TruckType.MIXED_OPERATION) {
                    if (dist > 100.0 && !isIntraZone) continue;
                    odProb *= Math.exp(-dist / MIXED_DISTANCE_DECAY_FACTOR);
                }

                if (isIntraZone) {
                    odProb *= INTRAZONE_DAMPING_FACTOR;
                }

                if (odProb > 1e-10) {
                    candidates.put(destZone.getZoneId(), odProb);
                }
            }
        }

        String selectedZoneId = gaBalancer.selectBalancedDestination(originZoneId, candidates);
        if (selectedZoneId == null) {
            double[] coords = selectSmartCoords(truck, currentTime);
            return DestinationResult.coordsOnly(coords);
        }

        DeliveryZone selectedZone = zoneManager.findZoneByZoneId(selectedZoneId);
        if (selectedZone == null) {
            double[] coords = selectSmartCoords(truck, currentTime);
            return DestinationResult.coordsOnly(coords);
        }

        // DELIVERY trucks: bypass POI for configurable fraction
        if (truckType == TruckType.DELIVERY
                && ThreadLocalRandom.current().nextDouble() < getZoneAwareBypass(selectedZone, config.getDeliveryRandomDestRatio())) {
            double[] coords = pointGenerator.generatePointInZone(selectedZone);
            return DestinationResult.withZone(coords, selectedZoneId);
        }

        // MIXED_OPERATION trucks: bypass POI for configurable fraction
        if (truckType == TruckType.MIXED_OPERATION
                && ThreadLocalRandom.current().nextDouble() < getZoneAwareBypass(selectedZone, config.getMixedRandomDestRatio())) {
            double[] coords = pointGenerator.generatePointInZone(selectedZone);
            return DestinationResult.withZone(coords, selectedZoneId);
        }

        // POI-based destination selection (commodity-aware routing)
        if (poiManager != null && poiManager.hasPOIs()) {
            int timePeriod = zoneManager.getTimePeriod(currentTime);
            PointOfInterest targetPOI = poiManager.selectPOIForTrip(
                truck, selectedZoneId, commodityType, timePeriod);

            if (targetPOI != null && geoValidator.isOnLand(targetPOI.getLongitude(), targetPOI.getLatitude())
                    && geoValidator.isValidPOILandUse(targetPOI.getLongitude(), targetPOI.getLatitude())) {
                double[] coords = pointGenerator.jitterPoint(
                    targetPOI.getLongitude(), targetPOI.getLatitude(), selectedZone.getRadiusKm());
                return new DestinationResult(coords, selectedZoneId, targetPOI.getPoiId());
            }
        }

        double[] coords = pointGenerator.generatePointInZone(selectedZone);
        return DestinationResult.withZone(coords, selectedZoneId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SMART DESTINATION (ZONE ATTRACTIVENESS)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects destination using time-dependent attractiveness scoring.
     * Uses pre-computed distance matrix for DELIVERY truck distance checks.
     */
    private double[] selectSmartCoords(TruckAgent truck, long currentTime) {
        List<DeliveryZone> zones = zoneManager.getZones();
        TruckType truckType = truck.getTruckType();
        int timePeriod = config.getTimePeriod(currentTime);
        int originZoneIndex = zoneManager.findNearestZoneIndex(
            truck.getCurrentLongitude(), truck.getCurrentLatitude());

        double[] zoneScores = new double[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            DeliveryZone zone = zones.get(i);
            if (zone.getZoneId().equals("MFS62")) continue;

            double odFlow = (originZoneIndex >= 0) ? odMatrix.getFlow(originZoneIndex, i) : 1.0;
            double attractiveness = zone.calculateAttractiveness(timePeriod,
                config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(), config.getAttractivenessBeta4());

            double truckTypeMultiplier = 1.0;
            if (truckType == TruckType.DELIVERY) {
                // Use pre-computed distance matrix when possible
                double distance;
                if (originZoneIndex >= 0) {
                    distance = zoneManager.getZoneDistance(originZoneIndex, i);
                } else {
                    distance = zoneManager.calculateDistanceFast(
                        truck.getHomeLongitude(), truck.getHomeLatitude(),
                        zone.getCenterLongitude(), zone.getCenterLatitude());
                }
                truckTypeMultiplier = (distance <= truck.getFamiliarAreaRadiusKm()) ? 30.0 : 0.01;
            }

            zoneScores[i] = odFlow * attractiveness * truckTypeMultiplier;
        }

        int selectedIndex = utils.Roulette.choice(zoneScores, ThreadLocalRandom.current().nextDouble());
        DeliveryZone selectedZone = zones.get(selectedIndex);
        return pointGenerator.generatePointInZone(selectedZone);
    }

    // ════════════════════════════════════════════════════════════════════════
    // FACILITY-BASED DESTINATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects destination using facility-type flow logic (MFS File 07).
     */
    private double[] selectFacilityBasedCoords(TruckAgent truck, String originZoneId, long currentTime) {
        List<DeliveryZone> zones = zoneManager.getZones();
        DeliveryZone originZone = zoneManager.findZoneByZoneId(originZoneId);
        FacilityType originType = (originZone != null) ? originZone.getFacilityType() : FacilityType.MIXED;

        String targetTypeKey = commodityRouter.selectDestinationFacilityType(originType);
        FacilityType targetType = commodityRouter.mapKeyToFacilityType(targetTypeKey);

        String originIndustry = (originZone != null) ? originZone.getDominantIndustry() : "0";
        String targetIndustry = commodityRouter.selectDestinationIndustry(originIndustry);
        boolean useIndustryFilter = commodityRouter.hasIndustryFlowData() && !"0".equals(targetIndustry);

        List<DeliveryZone> candidates = new ArrayList<>();
        List<Double> candidateScores = new ArrayList<>();

        for (DeliveryZone zone : zones) {
            if (zone.getZoneId().equals("MFS62")) continue;

            boolean facilityMatches = matchesFacilityType(zone, targetType);
            boolean industryMatches = !useIndustryFilter || zone.hasIndustryPresence(targetIndustry);

            if (facilityMatches && industryMatches) {
                candidates.add(zone);
                double baseScore = zone.calculateAttractiveness(config.getTimePeriod(currentTime),
                    config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                    config.getAttractivenessBeta3(), config.getAttractivenessBeta4());
                if (useIndustryFilter) {
                    baseScore *= (1.0 + zone.getIndustryPresenceWeight(targetIndustry));
                }
                candidateScores.add(baseScore);
            }
        }

        if (candidates.isEmpty() && useIndustryFilter) {
            for (DeliveryZone zone : zones) {
                if (matchesFacilityType(zone, targetType)) {
                    candidates.add(zone);
                    candidateScores.add(zone.calculateAttractiveness(config.getTimePeriod(currentTime),
                        config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                        config.getAttractivenessBeta3(), config.getAttractivenessBeta4()));
                }
            }
        }

        if (candidates.isEmpty()) {
            return selectSmartCoords(truck, currentTime);
        }

        int selectedIndex = utils.Roulette.choice(candidateScores, ThreadLocalRandom.current().nextDouble());
        DeliveryZone selectedZone = candidates.get(selectedIndex);
        return pointGenerator.generatePointInZone(selectedZone);
    }

    private boolean matchesFacilityType(DeliveryZone zone, FacilityType targetType) {
        switch (targetType) {
            case INDUSTRIAL: return zone.getFacilityType() == FacilityType.INDUSTRIAL;
            case LOGISTICS_HUB: return zone.getWarehousesWeight() > 0.3;
            case SMALL_RETAIL: return zone.getRetailWeight() > 0.3;
            case CONSTRUCTION_SITE: return zone.getConstructionWeight() > 0.3;
            case RESIDENTIAL: return zone.getResidentialWeight() > 0.3;
            default: return true;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // NEARBY DESTINATION (DELIVERY TRUCKS)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects nearby destination for DELIVERY trucks (max distance constraint).
     * Uses fast equirectangular distance from actual origin point.
     */
    public DestinationResult selectNearbyDestination(TruckAgent truck, double[] origin,
                                                     double maxDistanceKm, String originZoneId) {
        List<DeliveryZone> zones = zoneManager.getZones();
        List<DeliveryZone> nearbyZones = new ArrayList<>();
        List<Double> nearbyScores = new ArrayList<>();

        for (DeliveryZone zone : zones) {
            double dist = zoneManager.calculateDistanceFast(origin[0], origin[1],
                zone.getCenterLongitude(), zone.getCenterLatitude());
            if (dist <= maxDistanceKm) {
                nearbyZones.add(zone);
                double score = zone.calculateAttractiveness(0,
                    config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                    config.getAttractivenessBeta3(), config.getAttractivenessBeta4());
                if (zone.getZoneId().equals(originZoneId)) {
                    score *= INTRAZONE_DAMPING_FACTOR;
                }
                nearbyScores.add(score);
            }
        }

        if (!nearbyZones.isEmpty()) {
            int selectedIndex = utils.Roulette.choice(nearbyScores, ThreadLocalRandom.current().nextDouble());
            DeliveryZone selectedZone = nearbyZones.get(selectedIndex);
            double[] coords = pointGenerator.generatePointInZone(selectedZone);
            return DestinationResult.withZone(coords, selectedZone.getZoneId());
        }

        // No zones within range: find nearest
        int nearestIdx = zoneManager.findNearestZoneIndex(origin[0], origin[1]);
        DeliveryZone nearestZone = zones.get(Math.max(0, nearestIdx));
        double[] coords = pointGenerator.generatePointInZone(nearestZone);
        return DestinationResult.withZone(coords, nearestZone.getZoneId());
    }

    /**
     * Legacy overload — computes originZoneId internally.
     */
    public DestinationResult selectNearbyDestination(TruckAgent truck, double[] origin,
                                                     double maxDistanceKm) {
        String originZoneId = zoneManager.findZoneForLocation(origin[0], origin[1]);
        return selectNearbyDestination(truck, origin, maxDistanceKm, originZoneId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DELIVERY TOUR STOP (V5.2)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects destination for a delivery tour stop with progressive distance constraint.
     *
     * <p>For the first stop (stopNumber=0), uses wider radius and softer decay from depot.
     * For subsequent stops (stopNumber>0), uses tight radius and aggressive decay to
     * produce realistic 2-5km inter-stop legs.
     *
     * @param truck       The truck agent
     * @param origin      Current position [lon, lat]
     * @param maxDistKm   Max allowed distance (first stop: 15km, subsequent: 5km)
     * @param decayFactor Distance decay for exp(-dist/decayFactor) scoring
     * @param originZoneId Pre-computed origin zone ID
     * @param currentTime  Current simulation time
     * @param commodityType Commodity being carried
     * @return DestinationResult with coordinates and zone
     */
    public DestinationResult selectDeliveryTourStop(TruckAgent truck, double[] origin,
                                                     double maxDistKm, double decayFactor,
                                                     String originZoneId, long currentTime,
                                                     String commodityType) {
        List<DeliveryZone> zones = zoneManager.getZones();
        List<DeliveryZone> candidateZones = new ArrayList<>();
        List<Double> candidateScores = new ArrayList<>();

        for (DeliveryZone zone : zones) {
            if (zone.getZoneId().equals("MFS62")) continue;

            double dist = zoneManager.calculateDistanceFast(origin[0], origin[1],
                zone.getCenterLongitude(), zone.getCenterLatitude());

            if (dist > maxDistKm) continue;

            // Score = attractiveness × distance decay
            double score = zone.calculateAttractiveness(0,
                config.getAttractivenessBeta1(), config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(), config.getAttractivenessBeta4());

            score *= Math.exp(-dist / decayFactor);

            // Slight intra-zone damping to avoid trivial same-point trips
            if (zone.getZoneId().equals(originZoneId)) {
                score *= INTRAZONE_DAMPING_FACTOR;
            }

            if (score > 1e-10) {
                candidateZones.add(zone);
                candidateScores.add(score);
            }
        }

        if (!candidateZones.isEmpty()) {
            int selectedIndex = utils.Roulette.choice(candidateScores, ThreadLocalRandom.current().nextDouble());
            DeliveryZone selectedZone = candidateZones.get(selectedIndex);
            String selectedZoneId = selectedZone.getZoneId();

            // POI-based destination within selected zone
            if (poiManager != null && poiManager.hasPOIs()
                    && ThreadLocalRandom.current().nextDouble() >= getZoneAwareBypass(selectedZone, config.getDeliveryRandomDestRatio())) {
                int timePeriod = zoneManager.getTimePeriod(currentTime);
                PointOfInterest targetPOI = poiManager.selectPOIForTrip(
                    truck, selectedZoneId, commodityType, timePeriod);
                if (targetPOI != null
                        && geoValidator.isOnLand(targetPOI.getLongitude(), targetPOI.getLatitude())
                        && geoValidator.isValidPOILandUse(targetPOI.getLongitude(), targetPOI.getLatitude())) {
                    double[] coords = pointGenerator.jitterPoint(
                        targetPOI.getLongitude(), targetPOI.getLatitude(), selectedZone.getRadiusKm());
                    return new DestinationResult(coords, selectedZoneId, targetPOI.getPoiId());
                }
            }

            double[] coords = pointGenerator.generatePointInZone(selectedZone);
            return DestinationResult.withZone(coords, selectedZoneId);
        }

        // Fallback: nearest zone
        int nearestIdx = zoneManager.findNearestZoneIndex(origin[0], origin[1]);
        DeliveryZone nearestZone = zones.get(Math.max(0, nearestIdx));
        double[] coords = pointGenerator.generatePointInZone(nearestZone);
        return DestinationResult.withZone(coords, nearestZone.getZoneId());
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTER-ZONE DESTINATION (LONG_HAUL)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects INTER-metro destination for LONG_HAUL trucks.
     * Uses pre-computed distance matrix for zone distance calculations.
     */
    public DestinationResult selectInterZoneDestination(TruckAgent truck, double[] origin) {
        final double MIN_DISTANCE_KM = 100.0;

        List<DeliveryZone> zones = zoneManager.getZones();

        // Find origin zone index for distance matrix lookup
        int originIdx = zoneManager.findNearestZoneIndex(origin[0], origin[1]);

        // Find the 10 farthest zones from Tokyo center using pre-computed distances
        // Tokyo center is roughly zone MFS01 area — use pre-computed matrix
        int tokyoCenterIdx = zoneManager.getZoneListIndex("MFS01");
        if (tokyoCenterIdx < 0) tokyoCenterIdx = 0;

        // Build sorted list of zones by distance from Tokyo center
        int[] sortedByDist = new int[zones.size()];
        double[] distFromTokyo = new double[zones.size()];
        for (int i = 0; i < zones.size(); i++) {
            sortedByDist[i] = i;
            distFromTokyo[i] = zoneManager.getZoneDistance(tokyoCenterIdx, i);
        }
        // Simple selection of top-10 farthest (no need to sort all)
        int numFarZones = Math.min(10, zones.size());
        for (int k = 0; k < numFarZones; k++) {
            int maxIdx = k;
            for (int i = k + 1; i < zones.size(); i++) {
                if (distFromTokyo[sortedByDist[i]] > distFromTokyo[sortedByDist[maxIdx]]) {
                    maxIdx = i;
                }
            }
            int tmp = sortedByDist[k];
            sortedByDist[k] = sortedByDist[maxIdx];
            sortedByDist[maxIdx] = tmp;
        }

        // Filter to zones beyond minimum distance from origin
        List<DeliveryZone> validFarZones = new ArrayList<>();
        List<Double> zoneWeights = new ArrayList<>();
        for (int k = 0; k < numFarZones; k++) {
            int zIdx = sortedByDist[k];
            double distFromOrigin = (originIdx >= 0) ?
                zoneManager.getZoneDistance(originIdx, zIdx) :
                zoneManager.calculateDistanceFast(origin[0], origin[1],
                    zones.get(zIdx).getCenterLongitude(), zones.get(zIdx).getCenterLatitude());

            if (distFromOrigin >= MIN_DISTANCE_KM) {
                validFarZones.add(zones.get(zIdx));
                zoneWeights.add(Math.exp(-distFromOrigin / LONGHAUL_DISTANCE_DECAY_FACTOR));
            }
        }

        // Fallback: force MFS67-71
        if (validFarZones.isEmpty()) {
            for (int k = 0; k < numFarZones; k++) {
                int zIdx = sortedByDist[k];
                String zoneId = zones.get(zIdx).getZoneId();
                if (zoneId.startsWith("MFS67") || zoneId.startsWith("MFS68") ||
                    zoneId.startsWith("MFS69") || zoneId.startsWith("MFS70") ||
                    zoneId.startsWith("MFS71")) {
                    validFarZones.add(zones.get(zIdx));
                    zoneWeights.add(1.0);
                }
            }
        }

        if (validFarZones.isEmpty()) {
            for (int k = 0; k < numFarZones; k++) {
                validFarZones.add(zones.get(sortedByDist[k]));
                zoneWeights.add(1.0);
            }
        }

        int selectedIndex = utils.Roulette.choice(zoneWeights, ThreadLocalRandom.current().nextDouble());
        DeliveryZone selectedZone = validFarZones.get(selectedIndex);

        double[] coords = pointGenerator.generatePointInZoneFarSide(selectedZone, origin);
        return DestinationResult.withZone(coords, selectedZone.getZoneId());
    }

    /**
     * Applies LONG_HAUL distance constraints with POI snapping.
     */
    public DestinationResult applyLongHaulConstraints(TruckAgent truck, double[] origin,
                                                      String commodityType) {
        DestinationResult interResult = selectInterZoneDestination(truck, origin);
        String destZoneId = interResult.zoneId;

        DeliveryZone destZoneLH = (destZoneId != null) ? zoneManager.findZoneByZoneId(destZoneId) : null;
        boolean bypass = ThreadLocalRandom.current().nextDouble() < getZoneAwareBypass(destZoneLH, config.getLongHaulRandomDestRatio());

        if (!bypass && poiManager != null && poiManager.hasPOIs() && destZoneId != null) {
            PointOfInterest poi = poiManager.selectPOIForTrip(truck, destZoneId, commodityType, 0);
            if (poi != null) {
                double lon = poi.getLongitude();
                double lat = poi.getLatitude();
                if (geoValidator.isOnLand(lon, lat) && geoValidator.isValidPOILandUse(lon, lat)) {
                    double lhRadius = (destZoneLH != null) ? destZoneLH.getRadiusKm() : 5.0;
                    double[] coords = pointGenerator.jitterPoint(lon, lat, lhRadius);
                    return new DestinationResult(coords, destZoneId, poi.getPoiId());
                }
            }
        }

        if (destZoneId != null) {
            DeliveryZone destZone = zoneManager.findZoneByZoneId(destZoneId);
            if (destZone != null) {
                double[] coords = pointGenerator.generatePointInZone(destZone);
                return DestinationResult.withZone(coords, destZoneId);
            }
        }

        return interResult;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EMPTY TRIP DESTINATION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Selects destination for empty repositioning trip.
     */
    public DestinationResult selectEmptyTripDestination(TruckAgent truck, double lastDropoffLon,
                                                        double lastDropoffLat, long currentTime) {
        double[] lastDropoff = new double[]{lastDropoffLon, lastDropoffLat};

        if (truck.getTruckType() == TruckType.DELIVERY) {
            return selectNearbyDestination(truck, lastDropoff, 8.0);
        } else if (truck.getTruckType() == TruckType.LONG_HAUL) {
            return selectInterZoneDestination(truck, lastDropoff);
        } else {
            String originZoneId = zoneManager.findZoneForLocation(lastDropoffLon, lastDropoffLat);
            double[] coords = selectFacilityBasedCoords(truck, originZoneId, currentTime);
            return DestinationResult.coordsOnly(coords);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════════════════════

    public double getZoneAwareBypass(DeliveryZone zone, double configuredRatio) {
        if (zone != null && zone.getRadiusKm() > config.getRuralZoneRadiusThresholdKm()) {
            return config.getRuralZoneBypassRatio();
        }
        return configuredRatio;
    }

    public double calculateInterMetroDistance() {
        MetropolitanConfig.InterMetroRoute route = metroConfig.getRoute(
            metroConfig.getPrimaryMetro(),
            metroConfig.getSecondaryMetro()
        );
        return route != null ? route.distanceKm : 350.0;
    }
}
