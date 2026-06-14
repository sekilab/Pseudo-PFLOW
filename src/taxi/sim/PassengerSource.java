package taxi.sim;

import java.util.Random;

/**
 * Strategy interface for generating {@link Passenger} entities.
 *
 * <p>Introduced (W19, 2026-05) to address professor review comment on explicit
 * passenger modelling. Two implementations are provided to support a hybrid
 * deployment strategy:
 *
 * <ol>
 *   <li>{@link StubPassengerSource} (default) — anonymous passengers sampled
 *       from zone attractiveness, identical in calibration behaviour to the
 *       pre-W19 implicit-passenger model. {@code Passenger.personId = -1}.</li>
 *   <li>{@link PseudoPflowPassengerSource} (stub for future work) — passengers
 *       drawn from Pseudo-PFLOW's 130M-person synthetic population, with
 *       {@code Passenger.personId} pointing to the real person. Enables
 *       endogenous demand and activity-chain analysis. Currently throws
 *       {@code UnsupportedOperationException} pending the {@code pseudo.gen}
 *       integration described in §4.X of the paper as planned extension.</li>
 * </ol>
 *
 * <p>The active implementation is selected by the config key
 * {@code passenger.source}, with allowed values {@code stub} (default) and
 * {@code pseudo-pflow}. A bad value falls back to {@code stub} with a warning.
 *
 * @author Taxi ABM Framework
 * @since W19 (2026-05)
 */
public interface PassengerSource {

    /**
     * Generate the next passenger for a taxi cycle.
     *
     * <p>Called by {@link ShiftSimulator} when a taxi finishes its empty leg
     * and is ready to pick up a passenger.
     *
     * @param requestTime seconds since midnight at which the passenger
     *                    appears at the pickup point
     * @param originLon pickup longitude (taxi's current position)
     * @param originLat pickup latitude
     * @param destZone selected destination zone (already weighted by
     *                 time/space/weekday/land-use factors upstream)
     * @param random PRNG for sampling within the destination zone
     * @return a Passenger instance bound to (origin, dest, time)
     */
    Passenger next(long requestTime,
                   double originLon, double originLat,
                   DestinationZone destZone,
                   Random random);

    /**
     * Factory: choose the implementation based on a config string.
     *
     * @param sourceKind {@code "stub"} or {@code "pseudo-pflow"}
     * @return concrete PassengerSource
     */
    static PassengerSource forKind(String sourceKind) {
        if (sourceKind == null) sourceKind = "stub";
        switch (sourceKind.trim().toLowerCase()) {
            case "pseudo-pflow":
                return new PseudoPflowPassengerSource();
            case "stub":
            default:
                if (sourceKind != null && !"stub".equalsIgnoreCase(sourceKind.trim())) {
                    System.err.println("[PassengerSource] Unknown kind '"
                        + sourceKind + "', defaulting to stub");
                }
                return new StubPassengerSource();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // STUB IMPLEMENTATION (default)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Anonymous-passenger source. Preserves pre-W19 demand behaviour:
     * the passenger has no Pseudo-PFLOW identity, and pickup/dropoff are
     * sampled from the zone's attractiveness distribution as before.
     *
     * <p>Stub mode is calibration-neutral by construction: the only difference
     * from pre-W19 behaviour is that a Passenger object is now allocated per
     * cycle (negligible overhead). The 19-test Tokyo taxi suite should remain
     * at A+ (19/19) under stub mode.
     */
    final class StubPassengerSource implements PassengerSource {
        private int nextId = 0;

        @Override
        public synchronized Passenger next(long requestTime,
                                           double originLon, double originLat,
                                           DestinationZone destZone,
                                           Random random) {
            // Sample destination point within zone radius (uniform in a disk)
            double r = destZone.getRadiusKm() * Math.sqrt(random.nextDouble());
            double theta = 2.0 * Math.PI * random.nextDouble();
            double dLon = (r / 111.0) * Math.cos(theta) / Math.cos(Math.toRadians(destZone.getCenterLat()));
            double dLat = (r / 111.0) * Math.sin(theta);
            double destLon = destZone.getCenterLon() + dLon;
            double destLat = destZone.getCenterLat() + dLat;
            return new Passenger(++nextId, originLon, originLat, destLon, destLat, requestTime);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PSEUDO-PFLOW INTEGRATION (Phase B — follow-up paper)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Future-work source linking taxi demand to Pseudo-PFLOW's synthetic
     * person trips (130M agents with daily activity chains generated by
     * {@code pseudo.gen.*}). When activated, each Passenger carries the real
     * {@code person_id} so that downstream analysis can chain taxi trips to
     * other modes used by the same person on the same day.
     *
     * <p>Not implemented in W19 — left as a stub that surfaces a clear
     * exception so misconfiguration is obvious. Integration plan: at shift
     * start, load the person-trip CSV from {@code data/output/people-flow/}
     * filtered to {@code mode=taxi} for the simulation day; on each call to
     * {@link #next}, draw the next unmatched person-trip whose pickup is
     * within reasonable spatial range of the taxi's current position. See
     * paper §7 Discussion for the planned methodology.
     */
    final class PseudoPflowPassengerSource implements PassengerSource {
        @Override
        public Passenger next(long requestTime,
                              double originLon, double originLat,
                              DestinationZone destZone,
                              Random random) {
            throw new UnsupportedOperationException(
                "PseudoPflowPassengerSource is a stub in this release. "
                + "Set passenger.source=stub for current behaviour. "
                + "Real implementation deferred to follow-up paper on "
                + "Pseudo-PFLOW supply-demand integration.");
        }
    }
}
