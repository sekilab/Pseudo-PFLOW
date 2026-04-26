package taxi.sim;

import java.util.List;
import java.util.Random;

/**
 * Shift-time taxi simulator (v7.0 engine). Activated by config key
 * {@code taxi.simulation.engine=shift_time}.
 *
 * <p><b>B2 / Phase 4 implementation (current):</b> 5-state machine driving
 * trip generation by elapsed shift time, not by config'd trip count. Each
 * taxi cycles through:
 * <pre>
 *   shift_start
 *     │
 *     ▼
 *   ┌─────────────┐  stand_share(type)   ┌──────────┐
 *   │  DUAL_MODE  │ ───────────────────▶│ AT_STAND │
 *   │  (cruising) │                      │ (B3 will │
 *   │             │ ◀────────────────────┤ add Exp  │
 *   │ empty leg + │  next iteration      │ wait)    │
 *   │ pickup mode │                      └────┬─────┘
 *   └──────┬──────┘                           │ pickup
 *          │  pickup (street or app)          │
 *          ▼                                  ▼
 *   ┌──────────────────────────────────────────┐
 *   │           OCCUPIED (loaded leg)          │
 *   │   distance ~ lognormal(4.6 km, σ=0.6)    │
 *   └──────┬──────────────────────────────────┘
 *          │  dropoff
 *          ▼
 *   (loop back to top — sample stand vs DUAL_MODE)
 * </pre>
 *
 * <p>Trip count emerges from {@code shift_h ÷ cycle_h} ≈ 14h / 0.54h ≈ 26
 * cycles, matching THTA FY2024 target of 25.3 trips/active-taxi/day by
 * construction (DESIGN.md §2.5). The legacy {@code taxi.trips.average} cap
 * is irrelevant in this engine.
 *
 * <p><b>B2 partial scope:</b> the AT_STAND branch in this batch moves the
 * taxi to the nearest hub zone but does NOT yet add an exponential wait
 * (mean 8/12/6 min by stand type). B3 / Phase 5 wires the {@code Exp(λ)}
 * wait. Stand pickups are still counted in the per-type mode-mix observer.
 *
 * <p><b>B3 / Phase 6:</b> RIDE_HAIL_PRHS taxis will have their shifts clipped
 * to MLIT-permitted operating windows ({@code prhs.window.N} config keys).
 * Currently they run full shifts.
 *
 * <p><b>B2 finding — cycle-overhead caveat (open, deferred to paper §7):</b>
 * DESIGN.md §2.5 predicted ~26 cycles per 14h shift from {@code shift / (4.6/18 + 5.1/18)} ≈
 * 14h / 0.539h. That math counts only driving time. The legacy config keeps
 * {@code taxi.pickup.time=300s} (5 min boarding) and {@code taxi.break.time=600s}
 * (10 min post-dropoff break) as artifacts of the count-driven legacy engine
 * where they were noise (the trip count was capped externally). In the
 * cycle-driven shift_time engine these become first-order — adding 15 min per
 * cycle nearly halves the achievable count to ~14 cycles. Realised:
 * 14.69 trips/taxi vs 25.3 target (-41.9%). All distance/revenue derivatives
 * follow.
 *
 * <p>Real Tokyo industry data (THTA FY2024 p.13: 243.7 km/day at 18 km/h
 * weighted-avg speed = 13.5h driving in a 14h shift) implies ~1 min/trip of
 * non-driving overhead — already absorbed into the 12-hour weighted speed of
 * 18 km/h. The B2 cycle should likely use ~zero pickup/break or a separate
 * shift_time-only key set. Decision deferred to post-B6 review.
 */
class ShiftSimulator {

    private final TaxiConfig config;
    private final TaxiSimulation host;
    private final ModeMixResolver modeMix;
    private final Random random;

    // Manhattan factor cached once per simulator (config getter is hot).
    private final double manhattanFactor;
    // Empty-leg log-normal parameters (DESIGN.md §2.6, also B1 config keys).
    private final double emptyMu;
    private final double emptySigma;
    // Empty-leg bounds (DESIGN.md §2.6: [0.3, 12] km road-km).
    private static final double EMPTY_MIN_ROAD_KM = 0.3;
    private static final double EMPTY_MAX_ROAD_KM = 12.0;

    ShiftSimulator(TaxiConfig config, TaxiSimulation host,
                   ModeMixResolver modeMix, Random random) {
        this.config = config;
        this.host = host;
        this.modeMix = modeMix;
        this.random = random;
        this.manhattanFactor = config.getManhattanFactor();
        // Lognormal mu corrected so E[exp(N(mu,sigma^2))] = empty.mean.km.
        this.emptySigma = config.getTripDistanceEmptySigma();
        this.emptyMu = Math.log(config.getTripDistanceEmptyMeanKm())
                       - 0.5 * emptySigma * emptySigma;
    }

    /**
     * Simulate one taxi's full shift using the v7.0 state machine.
     *
     * <p>The taxi starts at home and enters DUAL_MODE (or AT_STAND for HUB types
     * that roll into a stand on their first decision). Each cycle:
     * <ol>
     *   <li>Decide stand vs DUAL_MODE based on type's stand share
     *   <li>Empty leg: log-normal distance, direction toward attractive zone
     *       (DUAL_MODE) or nearest hub (AT_STAND)
     *   <li>Sample pickup mode (street vs app, conditional on not stand)
     *   <li>OCCUPIED: log-normal loaded leg, dropoff
     *   <li>Loop until shift_remaining &lt; cycle_estimate
     * </ol>
     */
    void simulateShift(TaxiAgent taxi) {
        final TaxiType type = taxi.getTaxiType();
        final long shiftEnd = taxi.getShiftEndTime();
        final double emptyTripThresholdKm = config.getEmptyTripThresholdKm();

        long currentTime = taxi.getShiftStartTime();
        double curLon = taxi.getHomeLongitude();
        double curLat = taxi.getHomeLatitude();

        // Loop until shift end. Each iteration emits one OCCUPIED trip (and
        // possibly a preceding empty trip). The loop exits when an empty +
        // loaded cycle would push past shiftEnd.
        while (currentTime < shiftEnd) {
            // --- 1. Decide AT_STAND vs DUAL_MODE for this cycle ---
            boolean rollStand = random.nextDouble() < modeMix.standShare(type);
            // RIDE_HAIL_PRHS: regulatorily forbidden from stand; force DUAL_MODE.
            if (type == TaxiType.RIDE_HAIL_PRHS) rollStand = false;

            // Resolve stand zone — DESIGN.md §2.3 says "Move position to nearest
            // hub-zone". If no hub zones configured, falls back to DUAL_MODE.
            DestinationZone standZone = rollStand
                ? host.selectNearestHubZone(curLon, curLat)
                : null;
            final boolean atStand = (standZone != null);

            // --- 2. Empty leg + pickup position ---
            final double pickupLon, pickupLat;
            final ModeMixResolver.PickupMode pickupMode;
            final long emptyTravelTime;

            if (atStand) {
                // Move to hub-zone centroid; pickup is a STAND pickup.
                pickupLon = standZone.getCenterLon();
                pickupLat = standZone.getCenterLat();
                pickupMode = ModeMixResolver.PickupMode.STAND;
            } else {
                // DUAL_MODE: log-normal empty leg toward attractive zone centroid.
                double[] emptyResult = sampleDualModeEmptyLeg(taxi, curLon, curLat, currentTime);
                pickupLon = emptyResult[0];
                pickupLat = emptyResult[1];
                pickupMode = modeMix.sampleStreetVsApp(type);
            }

            double emptyRoadKm = host.calculateDistance(curLon, curLat, pickupLon, pickupLat);
            emptyTravelTime = TaxiTrip.estimateTravelTime(emptyRoadKm);

            // Emit empty-trip record for the cruise (above threshold).
            if (emptyRoadKm > emptyTripThresholdKm) {
                emitEmptyTrip(taxi, curLon, curLat, pickupLon, pickupLat, currentTime, emptyTravelTime);
            }
            currentTime += emptyTravelTime;
            // (B3 / Phase 5: Exp(λ) AT_STAND wait will be inserted here when atStand.)
            curLon = pickupLon;
            curLat = pickupLat;

            // --- 3. OCCUPIED loaded leg ---
            double[] dropoff = host.sampleLogNormalLoadedLeg(pickupLon, pickupLat);
            if (dropoff == null) {
                // Projection failed even after spiral — taxi loses this cycle.
                // End shift early rather than infinite-looping.
                break;
            }
            double dropLon = dropoff[0];
            double dropLat = dropoff[1];
            double loadedRoadKm = host.calculateDistance(pickupLon, pickupLat, dropLon, dropLat);
            long loadedTravel = TaxiTrip.estimateTravelTime(loadedRoadKm);

            long pickupReadyTime = currentTime + config.getTaxiPickupTime();
            long dropoffTime = pickupReadyTime + loadedTravel;
            if (dropoffTime > shiftEnd) {
                // Wouldn't finish in time — end shift cleanly.
                break;
            }

            TaxiTrip passengerTrip = new TaxiTrip(host.nextTripId(),
                pickupLon, pickupLat, dropLon, dropLat,
                currentTime, /*passengerIn=*/ true);
            passengerTrip.setTimes(pickupReadyTime, dropoffTime);
            passengerTrip.setAssignedTaxiId(taxi.getTaxiId());
            passengerTrip.setStatus(TaxiTrip.TripStatus.COMPLETED);
            taxi.assignTrip(passengerTrip);
            host.recordPassengerTripOutput(passengerTrip);
            host.recordPickupMode(type, pickupMode);

            // Advance to dropoff position for next cycle.
            curLon = dropLon;
            curLat = dropLat;
            currentTime = dropoffTime + config.getTaxiBreakTime();
        }
    }

    /**
     * Sample a DUAL_MODE empty leg: log-normal road-km distance toward a zone
     * centroid weighted by attractiveness.
     *
     * <p>If no zones are configured or all attractiveness scores are zero, falls
     * back to a uniform random bearing. If the resulting position fails spatial
     * validation, projects to the nearest valid cell using the same spiral-grid
     * scan as the loaded-leg path.
     */
    private double[] sampleDualModeEmptyLeg(TaxiAgent taxi, double curLon, double curLat, long currentTime) {
        // Sample log-normal road-km empty distance, bound clipped per DESIGN.md §2.6.
        double roadKm = Math.exp(emptyMu + emptySigma * random.nextGaussian());
        roadKm = Math.max(EMPTY_MIN_ROAD_KM, Math.min(EMPTY_MAX_ROAD_KM, roadKm));
        double haversineKm = roadKm / manhattanFactor;

        // Pick target zone weighted by attractiveness.
        DestinationZone target = pickAttractiveZone(currentTime);

        // Determine bearing: toward target centroid, or uniform random if no zone.
        double dxKm, dyKm;
        if (target != null) {
            double cosLat = Math.cos(Math.toRadians(curLat));
            dxKm = (target.getCenterLon() - curLon) * 111.32 * cosLat;
            dyKm = (target.getCenterLat() - curLat) * 111.32;
            double mag = Math.sqrt(dxKm * dxKm + dyKm * dyKm);
            if (mag < 1e-6) {
                // Already at the centroid — pick a random bearing.
                double bearing = 2.0 * Math.PI * random.nextDouble();
                dxKm = Math.cos(bearing);
                dyKm = Math.sin(bearing);
            } else {
                dxKm /= mag;
                dyKm /= mag;
            }
        } else {
            double bearing = 2.0 * Math.PI * random.nextDouble();
            dxKm = Math.cos(bearing);
            dyKm = Math.sin(bearing);
        }

        // Move haversineKm in that direction.
        double cosLat = Math.cos(Math.toRadians(curLat));
        double newLon = curLon + (haversineKm * dxKm) / (111.32 * cosLat);
        double newLat = curLat + (haversineKm * dyKm) / 111.32;

        // Validate; project if invalid.
        TaxiGeoValidator gv = host.getGeoValidator();
        if (gv != null && !gv.isValidLocation(newLon, newLat)) {
            double[] projected = host.projectToNearestValidCell(newLon, newLat,
                config.getProjectionRadiusKm());
            if (projected != null) return projected;
            // Fall back to current position if projection fails (zero empty leg).
            return new double[]{curLon, curLat};
        }
        return new double[]{newLon, newLat};
    }

    /**
     * Pick a destination zone weighted by attractiveness (without distance match).
     * Returns null if no zones or all scores are zero.
     */
    private DestinationZone pickAttractiveZone(long currentTime) {
        List<DestinationZone> zones = host.getDestinationZonesList();
        if (zones == null || zones.isEmpty()) return null;
        int timePeriod = host.getTimePeriod(currentTime);

        double[] scores = new double[zones.size()];
        double totalScore = 0.0;
        for (int i = 0; i < zones.size(); i++) {
            scores[i] = zones.get(i).calculateAttractiveness(timePeriod,
                config.getAttractivenessBeta1(),
                config.getAttractivenessBeta2(),
                config.getAttractivenessBeta3(),
                config.getAttractivenessBeta4());
            totalScore += scores[i];
        }
        if (totalScore <= 0) return null;

        double r = random.nextDouble() * totalScore;
        double cumulative = 0.0;
        for (int i = 0; i < zones.size(); i++) {
            cumulative += scores[i];
            if (r <= cumulative) return zones.get(i);
        }
        return zones.get(zones.size() - 1);
    }

    /** Construct + record an empty trip and update its taxi's assigned list. */
    private void emitEmptyTrip(TaxiAgent taxi, double srcLon, double srcLat,
                               double dstLon, double dstLat,
                               long startTime, long travelTime) {
        TaxiTrip emptyTrip = TaxiTrip.createEmptyTrip(host.nextTripId(),
            srcLon, srcLat, dstLon, dstLat, startTime);
        emptyTrip.setTimes(startTime, startTime + travelTime);
        emptyTrip.setAssignedTaxiId(taxi.getTaxiId());
        emptyTrip.setStatus(TaxiTrip.TripStatus.COMPLETED);
        taxi.assignTrip(emptyTrip);
        host.recordEmptyTripOutput(emptyTrip);
    }
}
