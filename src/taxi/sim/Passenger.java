package taxi.sim;

/**
 * Explicit passenger entity for a taxi trip.
 *
 * <p>Introduced (W19, 2026-05) to address professor review comment: prior to
 * this change, "people in the taxi" were implicit — a {@link TaxiTrip} carried
 * pickup/dropoff coordinates but no entity to represent who was riding. The
 * Passenger class makes the demand side explicit so that future extensions can
 * (a) link taxi demand to the Pseudo-PFLOW people-flow ABM (130M synthetic
 * persons with daily activity chains) and (b) report passenger-side metrics
 * such as wait time distribution or trip-purpose decomposition.
 *
 * <p><b>Hybrid design.</b> The class is instantiated by a
 * {@link PassengerSource}, of which two implementations exist: {@code
 * StubPassengerSource} (current behaviour — anonymous passengers sampled from
 * zone attractiveness) and {@code PseudoPflowPassengerSource} (stub for the
 * future {@code pseudo.gen.Trip} integration). The stub mode preserves
 * calibration; the linked mode is left for a follow-up paper.
 *
 * <p>The {@code personId} field is {@code -1} in stub mode, becoming a real
 * Pseudo-PFLOW person identifier once {@code PseudoPflowPassengerSource} is
 * implemented. Downstream analysis can use this to chain a taxi trip to the
 * passenger's other daily activities.
 *
 * @author Taxi ABM Framework
 * @since W19 (2026-05) — passenger-agent extension
 */
public final class Passenger {

    /** Per-simulation unique passenger ID (assigned by source). */
    private final int passengerId;

    /**
     * Pseudo-PFLOW person identifier, or {@code -1} in stub mode.
     * When non-negative, this links the passenger to a 130M-person synthetic
     * population, enabling demographic and activity-chain analysis.
     */
    private final long personId;

    /** Pickup location (WGS84). */
    private final double originLon;
    private final double originLat;

    /** Dropoff location (WGS84). */
    private final double destLon;
    private final double destLat;

    /** When the passenger arrived at the pickup point (seconds since midnight). */
    private final long arrivalTime;

    /** When the passenger boarded a taxi (set by ShiftSimulator after match). */
    private long boardTime = -1;

    /** When the passenger alighted (set after trip completion). */
    private long alightTime = -1;

    /** Actual fare paid (set after trip completion). */
    private double paidFare = 0.0;

    /**
     * Constructor for stub mode (anonymous passenger).
     *
     * @param passengerId per-simulation unique ID
     * @param originLon pickup longitude
     * @param originLat pickup latitude
     * @param destLon dropoff longitude
     * @param destLat dropoff latitude
     * @param arrivalTime seconds since midnight when passenger appeared at pickup
     */
    public Passenger(int passengerId,
                     double originLon, double originLat,
                     double destLon, double destLat,
                     long arrivalTime) {
        this(passengerId, -1L, originLon, originLat, destLon, destLat, arrivalTime);
    }

    /**
     * Constructor with Pseudo-PFLOW person link (future use).
     *
     * @param passengerId per-simulation unique ID
     * @param personId Pseudo-PFLOW person identifier (-1 for stub mode)
     * @param originLon pickup longitude
     * @param originLat pickup latitude
     * @param destLon dropoff longitude
     * @param destLat dropoff latitude
     * @param arrivalTime seconds since midnight when passenger appeared at pickup
     */
    public Passenger(int passengerId, long personId,
                     double originLon, double originLat,
                     double destLon, double destLat,
                     long arrivalTime) {
        this.passengerId = passengerId;
        this.personId = personId;
        this.originLon = originLon;
        this.originLat = originLat;
        this.destLon = destLon;
        this.destLat = destLat;
        this.arrivalTime = arrivalTime;
    }

    // ─── Lifecycle setters (called by ShiftSimulator post-match) ────────────

    public void setBoardTime(long boardTime) { this.boardTime = boardTime; }
    public void setAlightTime(long alightTime) { this.alightTime = alightTime; }
    public void setPaidFare(double paidFare) { this.paidFare = paidFare; }

    // ─── Getters ────────────────────────────────────────────────────────────

    public int getPassengerId() { return passengerId; }
    public long getPersonId() { return personId; }
    public double getOriginLon() { return originLon; }
    public double getOriginLat() { return originLat; }
    public double getDestLon() { return destLon; }
    public double getDestLat() { return destLat; }
    public long getArrivalTime() { return arrivalTime; }
    public long getBoardTime() { return boardTime; }
    public long getAlightTime() { return alightTime; }
    public double getPaidFare() { return paidFare; }

    /** @return true if this passenger is linked to a Pseudo-PFLOW person. */
    public boolean hasPersonLink() { return personId >= 0L; }

    /** @return wait time (board - arrival) in seconds, or -1 if not yet boarded. */
    public long getWaitTimeSeconds() {
        return boardTime >= 0 ? boardTime - arrivalTime : -1L;
    }

    /** @return ride duration (alight - board) in seconds, or -1 if not complete. */
    public long getRideDurationSeconds() {
        return (boardTime >= 0 && alightTime >= 0) ? alightTime - boardTime : -1L;
    }

    @Override
    public String toString() {
        return String.format(
            "Passenger[id=%d, person=%s, (%.4f,%.4f)->(%.4f,%.4f) @t=%ds wait=%ds]",
            passengerId,
            personId < 0 ? "stub" : Long.toString(personId),
            originLon, originLat, destLon, destLat,
            arrivalTime, getWaitTimeSeconds());
    }
}
