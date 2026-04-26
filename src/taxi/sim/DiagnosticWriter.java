package taxi.sim;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-cycle diagnostic CSV writer for B7 hypothesis testing (DESIGN.md §3,
 * BATCH_7_DIAGNOSTIC.md §7.1).
 *
 * <p>Captures per-leg records during {@link ShiftSimulator#simulateShift} so the
 * Python analyzer at {@code tools/b7_diagnostic_analyzer.py} can quantitatively
 * test seven hypotheses about the trips/taxi gap (H1–H7) plus three about the
 * loaded ratio (L1–L3). Writes to {@code <run_dir>/diagnostic.csv} alongside
 * {@code validation.csv}, {@code dashboard.csv}, and {@code trips.csv}.
 *
 * <p><b>Activation:</b> Off by default; enabled via
 * {@code taxi.diagnostics.enabled=true} in {@code taxi_config.properties}. When
 * disabled, all methods are no-ops with negligible overhead.
 *
 * <p><b>Stratified sampling:</b> {@link TaxiSimulation#buildDiagnosticSampleSet}
 * pre-selects which {@code taxi_id}s to emit, ensuring each {@link TaxiType}
 * has at least {@code taxi.diagnostics.sample.minimum.per.type} sampled units
 * (default 50) regardless of fleet share. Rare types (RIDE_HAIL_PRHS at 0.5%)
 * thus get full coverage — critical for per-type hypothesis tests.
 *
 * <p><b>Thread safety:</b> ShiftSimulator iterates the fleet sequentially per
 * {@code TaxiSimulation.generateTrips()}, so this writer is single-threaded by
 * construction. We buffer rows in memory and flush once at the end of the run
 * to avoid per-row IO overhead.
 *
 * <p><b>Schema:</b>
 * <pre>
 *   taxi_id, taxi_type, shift_start_sec, shift_duration_sec,
 *   cycle_idx, leg_type, distance_km, duration_sec,
 *   cumulative_shift_sec, shift_remaining_sec,
 *   manhattan_factor, end_reason
 * </pre>
 * {@code leg_type} ∈ {EMPTY_LEG, LOADED_LEG, AT_STAND_WAIT, SHIFT_END_MARKER}.
 * {@code end_reason} populated only on the SHIFT_END_MARKER row per taxi.
 */
final class DiagnosticWriter {

    enum LegType { EMPTY_LEG, LOADED_LEG, AT_STAND_WAIT, SHIFT_END_MARKER }

    enum EndReason {
        ONGOING,             // not the final emit for this taxi
        SHIFT_END_NORMAL,    // currentTime >= shiftEnd at top of loop
        PREDICTED_OVERRUN,   // dropoffTime > shiftEnd; can't fit next cycle
        GEO_INVALID,         // sampleLogNormalLoadedLeg returned null
        PRHS_NO_WINDOW       // RIDE_HAIL_PRHS with no MLIT window today
    }

    private final boolean enabled;
    private final List<String> rows;
    private final double manhattanFactor;

    DiagnosticWriter(TaxiConfig config) {
        this.enabled = config.isDiagnosticsEnabled();
        this.rows = enabled ? new ArrayList<>(64 * 1024) : null;
        this.manhattanFactor = config.getManhattanFactor();
        if (enabled) {
            // Header
            rows.add("taxi_id,taxi_type,shift_start_sec,shift_duration_sec,"
                + "cycle_idx,leg_type,distance_km,duration_sec,"
                + "cumulative_shift_sec,shift_remaining_sec,"
                + "manhattan_factor,end_reason");
        }
    }

    /** True if diagnostic output is active. Cheap call sites can guard on this. */
    boolean isEnabled() { return enabled; }

    /**
     * Append a leg record. {@code endReason} should be {@link EndReason#ONGOING}
     * for non-terminating cycles; the terminating row uses
     * {@link #emitShiftEnd(TaxiAgent, int, long, EndReason)}.
     */
    void emitLeg(TaxiAgent taxi, int cycleIdx, LegType leg,
                 double distanceKm, long durationSec,
                 long cumulativeShiftSec, long shiftRemainingSec) {
        if (!enabled) return;
        rows.add(String.format("%d,%s,%d,%d,%d,%s,%.4f,%d,%d,%d,%.4f,%s",
            taxi.getTaxiId(),
            taxi.getTaxiType().name(),
            taxi.getShiftStartTime(),
            taxi.getShiftEndTime() - taxi.getShiftStartTime(),
            cycleIdx,
            leg.name(),
            distanceKm,
            durationSec,
            cumulativeShiftSec,
            shiftRemainingSec,
            manhattanFactor,
            EndReason.ONGOING.name().toLowerCase()));
    }

    /** Final marker per taxi capturing end_reason. Distance/duration are 0. */
    void emitShiftEnd(TaxiAgent taxi, int finalCycleIdx,
                      long cumulativeShiftSec, EndReason reason) {
        if (!enabled) return;
        long shiftRemainingSec =
            (taxi.getShiftEndTime() - taxi.getShiftStartTime()) - cumulativeShiftSec;
        if (shiftRemainingSec < 0) shiftRemainingSec = 0;
        rows.add(String.format("%d,%s,%d,%d,%d,%s,0.0000,0,%d,%d,%.4f,%s",
            taxi.getTaxiId(),
            taxi.getTaxiType().name(),
            taxi.getShiftStartTime(),
            taxi.getShiftEndTime() - taxi.getShiftStartTime(),
            finalCycleIdx,
            LegType.SHIFT_END_MARKER.name(),
            cumulativeShiftSec,
            shiftRemainingSec,
            manhattanFactor,
            reason.name().toLowerCase()));
    }

    /**
     * Flush all buffered rows to {@code <runDir>/diagnostic.csv}. Called once
     * by TaxiSimulation after generateTrips() completes and the run_dir has
     * been created by TaxiDataExporter.
     */
    void flush(String runDir) throws IOException {
        if (!enabled || rows.isEmpty()) return;
        Path path = Paths.get(runDir, "diagnostic.csv");
        Files.createDirectories(path.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            for (String row : rows) {
                w.write(row);
                w.newLine();
            }
        }
        System.out.println("  [B7] diagnostic.csv: " + (rows.size() - 1) + " rows → " + path);
    }
}
