package taxi.sim;

/**
 * Enumeration of taxi agent types for heterogeneous behavior modeling.
 *
 * <p>Five types of taxi agents with distinct operational strategies. The first
 * three (LOCAL, CITYWIDE, HUB) match the v6.0 architecture; APP_PREFERRED and
 * RIDE_HAIL_PRHS are added in v7.0 (Phase 1 of the shift-time rewrite) to
 * model Tokyo's contemporary fleet composition.
 *
 * <p>Default share allocation (Tokyo 特別区・武三, FY2024):
 * <ul>
 *   <li>LOCAL: 50%
 *   <li>CITYWIDE: 15%
 *   <li>APP_PREFERRED: 20%
 *   <li>HUB: 9.5%
 *   <li>RIDE_HAIL_PRHS: 0.5%
 * </ul>
 *
 * <p>The APP_PREFERRED share is treated as a sensitivity-analysis parameter
 * over [0.20, 0.60]; LOCAL and CITYWIDE shares absorb the variation
 * proportionally (10:3 ratio); HUB and RIDE_HAIL_PRHS held fixed.
 *
 * <p>This heterogeneity creates more realistic taxi behavior patterns than
 * uniform random-walk models and matches the per-type mode-mix observed in
 * the Tokyo Hire-Taxi Association industry data.
 */
public enum TaxiType {
    /**
     * LOCAL (majority): Veteran 法人 + 個人 drivers operating within a
     * familiar 5–10 km zone cluster centered on their home base.
     * <ul>
     *   <li>Defined by a fixed radius from home base (familiar zone)
     *   <li>Prefer pickups and dropoffs within their familiar zone
     *   <li>Mode mix: 70% street-hail / 25% app-dispatch / 5% stand
     *   <li>Represents neighborhood/district-based taxi operations
     * </ul>
     */
    LOCAL,

    /**
     * CITYWIDE: Long-range willing 法人 drivers; no familiar zone restriction.
     * <ul>
     *   <li>No spatial restrictions; larger cruising range
     *   <li>Willing to take trips anywhere in 特別区・武三
     *   <li>Mode mix: 60% street / 30% app / 10% stand
     * </ul>
     */
    CITYWIDE,

    /**
     * APP_PREFERRED (v7.0): Younger 法人 drivers aligned with app-dispatch
     * platforms (GO / Uber / DiDi / S.RIDE).
     * <ul>
     *   <li>No familiar-zone restriction; follows demand intensity citywide
     *   <li>Mode mix: 20% street / 75% app / 5% stand
     *   <li>Shift pattern: 昼日勤 8–12 h or 隔日勤務 14–16 h (mixed)
     *   <li>Defends the industry observation that ~50% of contemporary
     *       Tokyo trips originate via app dispatch
     *   <li>Default share 20% (sensitivity range: 20%–60%)
     * </ul>
     */
    APP_PREFERRED,

    /**
     * HUB: Drivers concentrating at airports and major-station queues.
     * <ul>
     *   <li>Focus on high-traffic transportation hubs (Haneda, Narita,
     *       Tokyo Stn, Shinjuku, Shibuya, Ueno, Ikebukuro, Shinagawa)
     *   <li>Mode mix: 10% street / 30% app / 60% stand (FIFO queue)
     *   <li>Airport stand wait ~8 min; major-station stand ~12 min (Exp dist.)
     * </ul>
     */
    HUB,

    /**
     * RIDE_HAIL_PRHS (v7.0): 自家用車活用事業 (Private Vehicle Utilization
     * Business) drivers under the April 2024 legalization regime
     * (Road Transport Act Article 78-3 amendment).
     * <ul>
     *   <li>App dispatch only (regulatorily forbidden from street-hailing)
     *   <li>Citywide coverage but operating-hour windowed by MLIT
     *   <li>Mode mix: 0% street / 100% app / 0% stand
     *   <li>Default share 0.5% (matches MLIT-published 1,365 trips/day in
     *       Tokyo 特別区・武三 vs ~625K total taxi trips, ≈0.22%)
     *   <li>Shift duration 8–10 h, clipped to MLIT-permitted operating windows
     *       (FY2024: Mon–Fri 7–10am, Fri/Sat 16–19, Sat 0–4, Sun 10–13;
     *        R8 contraction: Mon–Fri 7–10am only)
     * </ul>
     */
    RIDE_HAIL_PRHS
}
