package taxi.sim;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Resolves a {@link TaxiType} to a (street, app, stand) pickup-mode probability
 * triple, and samples pickup modes from those triples.
 *
 * <p>Used by {@link ShiftSimulator} in the v7.0 shift-time engine (B2 / Phase 4)
 * to:
 * <ol>
 *   <li>Decide whether a taxi enters AT_STAND or DUAL_MODE after each OCCUPIED
 *       dropoff — driven by {@code stand} share for the type.
 *   <li>At the end of a DUAL_MODE empty leg, sample whether the next pickup is
 *       a street-hail or app-dispatch — driven by the
 *       {@code street / (street + app)} ratio (stand is excluded because the
 *       AT_STAND branch is taken before this sampling).
 * </ol>
 *
 * <p>Defaults from DESIGN.md §2.1:
 * <pre>
 *   Type            street  app   stand
 *   LOCAL            0.70   0.25  0.05
 *   CITYWIDE         0.60   0.30  0.10
 *   APP_PREFERRED    0.20   0.75  0.05
 *   HUB              0.10   0.30  0.60
 *   RIDE_HAIL_PRHS   0.00   1.00  0.00   ← regulatory: street-hail prohibited
 * </pre>
 *
 * <p>Each row sums to 1.0. If a configured row drifts from 1.0 due to an
 * editor mistake, the resolver normalises silently to keep sampling correct
 * but logs a warning at construction.
 */
class ModeMixResolver {

    /** Pickup mode emitted by {@link #samplePickupMode}. */
    enum PickupMode { STREET, APP, STAND }

    /** Cached probability triple per type: [streetCDF, streetPlusAppCDF, 1.0]. */
    private final Map<TaxiType, double[]> cdfByType = new EnumMap<>(TaxiType.class);
    /** Raw stand share per type, retained for AT_STAND vs DUAL_MODE branch. */
    private final Map<TaxiType, Double> standShareByType = new EnumMap<>(TaxiType.class);
    /** Conditional P(street | not stand) per type, for DUAL_MODE pickup sampling. */
    private final Map<TaxiType, Double> streetGivenNotStandByType = new EnumMap<>(TaxiType.class);

    private final Random random;

    ModeMixResolver(TaxiConfig config, Random random) {
        this.random = random;

        register(TaxiType.LOCAL,
            config.getTaxiTypeLocalModeStreet(),
            config.getTaxiTypeLocalModeApp(),
            config.getTaxiTypeLocalModeStand());
        register(TaxiType.CITYWIDE,
            config.getTaxiTypeCitywideModeStreet(),
            config.getTaxiTypeCitywideModeApp(),
            config.getTaxiTypeCitywideModeStand());
        register(TaxiType.APP_PREFERRED,
            config.getTaxiTypeAppPreferredModeStreet(),
            config.getTaxiTypeAppPreferredModeApp(),
            config.getTaxiTypeAppPreferredModeStand());
        register(TaxiType.HUB,
            config.getTaxiTypeHubModeStreet(),
            config.getTaxiTypeHubModeApp(),
            config.getTaxiTypeHubModeStand());
        register(TaxiType.RIDE_HAIL_PRHS,
            config.getTaxiTypeRideHailPrhsModeStreet(),
            config.getTaxiTypeRideHailPrhsModeApp(),
            config.getTaxiTypeRideHailPrhsModeStand());
    }

    private void register(TaxiType type, double pStreet, double pApp, double pStand) {
        double sum = pStreet + pApp + pStand;
        if (Math.abs(sum - 1.0) > 1e-3) {
            System.out.println("[ModeMixResolver] WARN: " + type
                + " mode-mix sums to " + sum + " (≠ 1.0); normalising.");
            pStreet /= sum; pApp /= sum; pStand /= sum;
        }
        // CDF: [streetCDF, street+appCDF]; >= street+appCDF → STAND
        cdfByType.put(type, new double[]{pStreet, pStreet + pApp});
        standShareByType.put(type, pStand);

        // P(street | not stand). Guard divide-by-zero for RIDE_HAIL_PRHS-like
        // (no stand, no street): leave as 0.0 so app is forced.
        double notStand = pStreet + pApp;
        streetGivenNotStandByType.put(type,
            notStand > 1e-9 ? pStreet / notStand : 0.0);
    }

    /**
     * @return P(stand) for this type. Used by {@link ShiftSimulator} to decide
     *         AT_STAND vs DUAL_MODE after an OCCUPIED dropoff.
     */
    double standShare(TaxiType type) {
        return standShareByType.getOrDefault(type, 0.0);
    }

    /**
     * Sample a pickup mode for a taxi about to begin a passenger trip.
     *
     * <p>Uses the full (street, app, stand) distribution. Normally
     * {@link ShiftSimulator} calls {@link #sampleStreetVsApp(TaxiType)} after
     * the AT_STAND/DUAL_MODE branch decision; this fuller method exists for
     * cases (e.g. start-of-shift) where the branch hasn't been made yet.
     */
    PickupMode samplePickupMode(TaxiType type) {
        double[] cdf = cdfByType.get(type);
        if (cdf == null) return PickupMode.APP;  // defensive
        double r = random.nextDouble();
        if (r < cdf[0]) return PickupMode.STREET;
        if (r < cdf[1]) return PickupMode.APP;
        return PickupMode.STAND;
    }

    /**
     * Sample STREET vs APP given the AT_STAND branch was already excluded.
     *
     * <p>Used at the end of a DUAL_MODE empty leg: the taxi has been cruising
     * and listening for app dispatches, and the next pickup is either a
     * street-hail or an app dispatch (never a stand pickup — that's a
     * different state).
     */
    PickupMode sampleStreetVsApp(TaxiType type) {
        double pStreet = streetGivenNotStandByType.getOrDefault(type, 0.0);
        return random.nextDouble() < pStreet ? PickupMode.STREET : PickupMode.APP;
    }
}
