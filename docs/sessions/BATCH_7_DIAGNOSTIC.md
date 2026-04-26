# Batch 7 — Scientific Diagnostic & Calibration Pass

**Status after B6:** Code architecture complete (5-type fleet, shift-time engine, GSA pipeline). Validation Grade B− (14/19, 73.7%). Three FAIL metrics share one root cause; two additional issues remain.

**B7 goal:** Resolve the four identified issues using a **hypothesis-driven scientific method**, not patch-and-hope. Each sub-phase generates evidence before action. The diagnostic data itself becomes part of the paper's defense.

This prompt is paste-able into a Claude Code session. It supersedes the "B7 cleanup" line in `CONTINUATION_PROMPT.md`.

---

## Why scientific method, not patches

The trips-per-taxi gap (14.36 vs 25.30) is a **41% shortfall** in the headline metric. We don't know the root cause. There are at least six plausible hypotheses (see §2 below). A patch-and-pray approach would:

- Tune `taxi.trips.average=25` to mask the symptom (curve-fitting)
- Or guess a parameter without evidence (40% chance of fixing the wrong thing)
- Forfeit the paper's strongest defense: *"the simulation tells us why the gap exists"*

The scientific approach instead:

1. **Instrument** the simulation to capture per-cycle data
2. **Formulate hypotheses** explicitly with falsifiable predictions
3. **Run controlled experiments** that distinguish hypotheses
4. **Apply the fix** supported by the evidence
5. **Document the diagnostic trail** in paper §6 (Validation) and §8 (Discussion)

Reviewers will reward this approach. The paper's claim *"calibrated by construction"* is strengthened, not weakened, by transparent diagnostics.

---

## The four issues, restated

| # | Issue | Symptom | Cost if unfixed |
|---|-------|---------|-----------------|
| **I1** | Trips/active-taxi/day = 14.36 vs target 25.30 | 41% shortfall | Headline metric FAILs; paper's calibration claim weakens |
| **I2** | Loaded distance ratio = 45.06% vs target 47.5% | 2.4 pp / 5.1% off | 実車率 emergence claim unsubstantiated for FY2024 |
| **I3** | Min trip distance = 0.16 km vs 0.5 km floor | Bounds violation | Suggests truncation logic broken; not a calibration issue itself but undermines reproducibility |
| **I4** | GSA design at N=16 Sobol, 4-trajectory Morris | "Demonstration grade"; 9 of 12 params show 0 effect | Reviewer rejects as inconclusive |

**Hierarchy of importance:** I1 ≫ I2 > I3 > I4. I1 is the headline; I4 is the most compute-expensive but least scientifically risky (well-known small-N artifact, paper acknowledges).

---

## Paste this block to start B7

```
B6 (Phase 9 + paper polish) completed with Grade B- (14/19, 73.7%). Three FAIL metrics — passenger trips/taxi (14.36 vs 25.3), total km/taxi (142 vs 244), daily revenue (¥27,678 vs ¥56,139) — share one unidentified root cause. Plus two minor issues: loaded ratio 45.06% vs 47.5% target (FAIL), and log-normal sampling violates the 0.5 km min floor (observed 0.16 km).

Begin B7 = scientific diagnostic & calibration pass. This is NOT a patch run. Each sub-phase produces evidence before action. Seven hypotheses (H1-H7 for trips/taxi gap, L1-L3 for loaded ratio) are pre-formulated; expanding the hypothesis space (H8+, L4+) requires user check-in.

Required reading before any code:
1. H:\Dropbox\PFLOW\CONTINUE.md
2. H:\Dropbox\PFLOW\BATCH_7_DIAGNOSTIC.md (this file extended; the spec)
3. H:\Dropbox\PFLOW\references\taxi_data_sources\DESIGN.md §2 (architecture) and §4 (Phase 9 acceptance criteria)
4. H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\ShiftSimulator.java (current shift-time loop)
5. H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\ModeMixResolver.java
6. H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\PrhsWindow.java
7. H:\Dropbox\PFLOW\output\trips\taxi\tokyo\run_20260426_220120\validation.csv (latest baseline run)

Then execute sub-phases 7.1 through 7.7 sequentially. Each ends with a stop-and-report checkpoint. If any sub-phase reveals a hypothesis we did not anticipate, STOP and ask the user before proceeding.

Constraints:
- Aggregated-only data input preserved (no GPS, no app logs, no surveys)
- Configuration-driven (no hardcoded constants — all new parameters become config keys)
- All diagnostic instrumentation is opt-in via taxi.diagnostics.* config flags (default off in production)
- Diagnostic findings get written to docs/taxi_abm_paper.tex sections 6 and 8 as the diagnostic data accumulates

Working preferences (CLAUDE.md): terse responses, action first, ★ Insight blocks for findings, push back when needed, STOP and ask if findings contradict expectations.

Proceed with sub-phase 7.1.
```

---

## Sub-phase 7.1 — Instrumentation (~2 hours)

**Goal:** Add opt-in diagnostic logging that captures per-cycle, per-taxi behavior so we can answer "why does the shift end with only 14 cycles?"

### Add config flags

In `taxi_config.properties`, add a new section:

```properties
# ====== DIAGNOSTIC INSTRUMENTATION (B7, default off) ======
# When true, ShiftSimulator emits per-cycle records to diagnostic.csv in <run_dir>.
# Each row: taxi_id, taxi_type, cycle_idx, leg_type, distance_km, duration_sec,
#           cumulative_shift_sec, shift_remaining_sec, manhattan_factor, end_reason
# end_reason ∈ {ongoing, shift_exhausted, return_home_triggered, geo_invalid}
#
# Stratified sampling: minimum N taxis per type, regardless of overall fraction.
# This ensures rare types (RIDE_HAIL_PRHS at 0.5%) get adequate diagnostic coverage
# to test per-type hypotheses (H6).
taxi.diagnostics.enabled=true
taxi.diagnostics.sample.mode=stratified
taxi.diagnostics.sample.fraction=0.10
taxi.diagnostics.sample.minimum.per.type=50
```

With 10% overall sampling + 50-per-type floor:
- LOCAL: 50% × 24,697 × 0.10 = 1,235 sampled
- CITYWIDE: 16% × 24,697 × 0.10 = 395 sampled
- APP_PREFERRED: 20% × 24,697 × 0.10 = 494 sampled
- HUB: 9.5% × 24,697 × 0.10 = 235 sampled
- RIDE_HAIL_PRHS: max(50, 0.5% × 24,697 × 0.10) = max(50, 12) = **50** sampled (floor active)

Total: ~2,400 taxis × ~14 cycles = ~33,600 rows; ~5 MB CSV. Trivial.

### Add diagnostic CSV writer

New class `taxi/sim/DiagnosticWriter.java` with thread-safe append. Output goes to **`<run_dir>/diagnostic.csv`** — the same per-run-dir convention used by `validation.csv`, `trips.csv`, etc. Columns:

```
taxi_id, taxi_type, shift_start_sec, shift_duration_sec,
cycle_idx, leg_type, distance_km, duration_sec,
cumulative_shift_sec, shift_remaining_sec, manhattan_factor, end_reason
```

`leg_type ∈ {EMPTY_LEG, LOADED_LEG, AT_STAND_WAIT, RETURN_HOME_LEG}`

`manhattan_factor` is the ratio between actual driven distance (Manhattan-scaled) and the underlying straight-line sample. Captured per leg to support H7 testing (see §7.2).

`end_reason` is null for cycles that don't terminate the shift; populated on the final emit per taxi.

### Add diagnostic analyzer (Python)

The codebase norm is Java, but ad-hoc analytical queries against diagnostic.csv are faster to write in Python pandas. New script: **`H:\Dropbox\PFLOW\tools\b7_diagnostic_analyzer.py`** (single file, no install dependencies beyond pandas/numpy). Outputs `h1_h7_findings.md` next to the diagnostic.csv being analyzed. CLI: `python tools/b7_diagnostic_analyzer.py <run_dir>`.

### Wire into ShiftSimulator

After every leg emit, also call `DiagnosticWriter.write(...)` if config flag enabled and taxi sampled. At shift end, record the `end_reason` for the last cycle.

### Run baseline diagnostic

```cmd
cd H:\Dropbox\PFLOW\scripts
compile_taxi_v52.bat
run_taxi.bat ..\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties
```

Expect ~493 sampled taxis (24,697 × 0.02) × ~14 cycles = ~6,902 rows in diagnostic.csv. ~1 MB. Fast to read.

### Acceptance criteria (7.1)

- diagnostic.csv produced with non-zero row count
- Sample includes all 5 taxi types proportionally
- end_reason populated for each taxi's final row
- Existing validation.csv output unchanged (Grade B− preserved)

**Stop and report after 7.1:** confirm instrumentation works, share the head/tail of diagnostic.csv. Await user greenlight for 7.2.

---

## Sub-phase 7.2 — Hypothesis testing for trips-per-taxi gap (~3 hours)

**Goal:** Identify which of the six hypotheses below explains the trips-per-taxi shortfall.

### Hypotheses (mutually non-exclusive, falsifiable)

| # | Hypothesis | Predicted observation if true |
|---|-----------|-------------------------------|
| H1 | Empty-leg log-normal right tail consumes shift hours | Empty leg distance distribution skewed; mean significantly > 5.1 km in observed data |
| H2 | AT_STAND wait times accumulate beyond 5% mode share allowance | AT_STAND duration sum / total shift time > 0.10 |
| H3 | RETURNING_HOME consumes excess shift remaining | Mean RETURN_HOME_LEG distance > 5 km (significant overhead per shift) |
| H4 | Shift duration distribution mean is below configured 14h | Empirical mean shift_duration_sec / 3600 < 13 |
| H5 | Cycle time exceeds the cycle-math estimate (32.3 min) | Empirical mean cycle_time = (empty_time + loaded_time) > 32 min |
| H6 | HUB taxis spend disproportionate time in AT_STAND queues, dragging the per-active-taxi average down | trips_per_taxi[HUB] significantly < 14; AT_STAND share[HUB] >> 60% configured |
| H7 | Manhattan factor (1.4×) inflates effective driven distance beyond the straight-line cycle math | Mean manhattan_factor in diagnostic.csv > 1.0; cycle_time × 1.4 ≈ observed cycle_time |

**On PRHS contribution to H6:** PRHS taxis with no overlapping shift window stay OFF_DUTY and do *not* contribute to the trips/active-taxi denominator (they're not "active" by MLIT definition). PRHS therefore cannot drag down the average via this mechanism. PRHS impact on aggregate trips/taxi is bounded by their share (0.5%) and is at most ~0.07 trips of pull-down — not a viable explanation. H6 is therefore framed around HUB only.

**On the cycle-math residual:** With H1+H3+H5 fully active, cycle time grows from 32.3 min → ~40 min and RETURN_HOME consumes 30 min, giving 13 h × 60 / 40 = 19.5 cycles per shift. Observed is 14. The residual 5-cycle gap likely comes from H7 (Manhattan factor inflation) and/or H2 (AT_STAND accumulation). H7 specifically: if Manhattan factor 1.4× applies to BOTH empty and loaded legs, cycle distance grows 9.7 km → 13.6 km, and cycle time grows 32 min → 45 min. 13 h × 60 / 45 = 17.3 cycles, still over 14 but in the right zone. AT_STAND adds 5–10% of shift time on top, dropping further to ~14–15 cycles. Reconciles.

### Diagnostic queries to run

Open `diagnostic.csv` from 7.1. Compute (in Python or R or Excel):

```python
import pandas as pd
df = pd.read_csv("diagnostic.csv")

# H1 - empty leg distribution
empty = df[df.leg_type == "EMPTY_LEG"]
print("Empty leg km — mean:", empty.distance_km.mean(), 
      "median:", empty.distance_km.median(),
      "P95:", empty.distance_km.quantile(0.95))

# H2 - AT_STAND duration share
total_time_per_taxi = df.groupby("taxi_id").duration_sec.sum()
stand_time_per_taxi = df[df.leg_type == "AT_STAND_WAIT"].groupby("taxi_id").duration_sec.sum()
print("AT_STAND share of shift time:", 
      (stand_time_per_taxi / total_time_per_taxi).mean())

# H3 - RETURN_HOME distance
ret = df[df.leg_type == "RETURN_HOME_LEG"]
print("Return home km — mean:", ret.distance_km.mean())

# H4 - shift duration mean
shift_mean = df.groupby("taxi_id").shift_duration_sec.first().mean() / 3600
print("Mean shift duration (hr):", shift_mean)

# H5 - cycle time
loaded = df[df.leg_type == "LOADED_LEG"]
cycle_time_min = (empty.duration_sec.mean() + loaded.duration_sec.mean()) / 60
print("Mean cycle time (min, EMPTY+LOADED only):", cycle_time_min)

# H6 - per-type breakdown (HUB focus)
trips_by_type = df[df.leg_type == "LOADED_LEG"].groupby("taxi_type").size() \
                / df.groupby("taxi_type").taxi_id.nunique()
print("Loaded trips per taxi by type:")
print(trips_by_type)
hub_stand_share = df[(df.taxi_type == "HUB") & (df.leg_type == "AT_STAND_WAIT")].duration_sec.sum() \
                / df[df.taxi_type == "HUB"].duration_sec.sum()
print(f"HUB AT_STAND share of total HUB time: {hub_stand_share:.1%} (target ≤60%)")

# H7 - Manhattan factor inflation
print("Manhattan factor distribution:")
print("  EMPTY mean:", df[df.leg_type == "EMPTY_LEG"].manhattan_factor.mean())
print("  LOADED mean:", df[df.leg_type == "LOADED_LEG"].manhattan_factor.mean())
inflated_cycle_min = (empty.distance_km.mean() * empty.manhattan_factor.mean() / 18 * 60) + \
                    (loaded.distance_km.mean() * loaded.manhattan_factor.mean() / 18 * 60)
print(f"Manhattan-inflated cycle time: {inflated_cycle_min:.1f} min (vs cycle-math 32.3)")

# end_reason distribution
print("Why shifts ended:")
print(df.groupby("taxi_id").end_reason.last().value_counts())
```

Save findings to `output/trips/taxi/tokyo/<run_dir>/h1_h7_findings.md` with a one-paragraph interpretation per hypothesis.

### Predicted answer pattern

The most likely explanation (my prior, falsifiable):

- **H1 partially true**: empty mean ~5.5 km not 5.1 (log-normal right tail bias); modest contribution
- **H2 partially true**: AT_STAND ~6–8%, slightly over configured 5%
- **H3 partially true**: RETURN_HOME adds ~5–10 km empty per shift, NOT counted in cycle math
- **H4 false**: shift duration matches ~14 h configured
- **H5 partially true**: cycle time may be 35–40 min not 32 min (geometry overhead)
- **H6 partially true**: HUB taxis show >60% AT_STAND share (target ≤60%); modest drag
- **H7 likely dominant**: Manhattan factor 1.4× inflates effective distance per cycle → cycle time 32 min → 45 min

If H7 is dominant: cycle distance 9.7 km × 1.4 = 13.6 km; cycle time = 13.6 / 18 × 60 = 45.3 min. Shift cycles = 14 h × 60 / 45.3 = 18.5. Add H2 (AT_STAND ~7%) and H3 (RETURN_HOME consuming 30 min of shift remainder): drops to ~14.5 cycles. **Reconciles with observed 14.36.**

If H7 is NOT the dominant explanation, **STOP** and report — the residual gap then needs deeper investigation (H8 candidates: re-entrancy bugs, overlapping shift logic, or distribution sampling bias not captured by H1).

### Acceptance criteria (7.2)

- Each of H1–H7 quantitatively answered with diagnostic evidence
- Stratified sampling confirmed (each of 5 taxi types has ≥50 sampled units)
- Root cause hypothesis (or combination) identified with quantified contribution
- Findings document `h1_h7_findings.md` committed to the run directory

**Stop and report after 7.2:** share `h1_h7_findings.md` and ask user to confirm the hypothesis ranking before applying any fix in §7.3.

---

## Sub-phase 7.3 — Apply targeted fix for trips-per-taxi gap (~3 hours)

**Goal:** Apply the specific fix supported by 7.2 evidence. Do NOT improvise.

### Fix recipe by hypothesis

**Constraint:** all parameter adjustments must stay within the `gsa_params.csv` ranges. Adjustments outside those bounds invalidate the §7.6 sensitivity decomposition and require a fresh GSA. If a fix needs an out-of-range value, expand the GSA range first (and document in §7.6 caveats).

| Confirmed hypothesis | Recommended fix | Stays in GSA range? |
|---------------------|-----------------|---------------------|
| H1 (empty right tail) | Tighten σ_E from 0.7 → 0.55; document in new paper §4.6 (added by 7.4) | ✓ (range [0.3, 1.2]) |
| H2 (AT_STAND too high) | Reduce HUB stand share 60% → 50%, or shorten airport wait 8 → 6 min | ✓ (range [0.40, 0.80] / [4, 16]) |
| H3 (return home overhead) | (a) exclude RETURN_HOME from 実車率 calc (validation-side fix; no parameter change); (b) start return earlier; (c) skip RETURN_HOME entirely | (a) ✓ no param; (b) new param needed; (c) no param |
| H4 (short shifts) | Re-examine `shift.duration.average` sampling; verify empirical mean = 14 h | ✓ no GSA param involved |
| H5 (cycle overhead) | Subsumed by H7 — investigate Manhattan factor before separately calibrating | ✓ |
| H6 (HUB stand drag) | Reduce HUB stand share (overlaps with H2 fix) | ✓ |
| H7 (Manhattan factor inflation) | Either (a) reduce `distance.manhattan.factor` 1.4 → 1.2; (b) keep 1.4 but adjust cycle-math expectations in paper §4.3 | (a) NOT in current GSA range — would need to add; (b) no code change |

### Implementation rule

Pick the **single highest-impact fix** from 7.2 evidence. Apply only that. Re-run baseline. Measure delta.

If the fix closes >50% of the trips-per-taxi gap, proceed to 7.4. If not, return to 7.2 and check the second-ranked hypothesis.

### Acceptance criteria (7.3)

- Single-parameter or single-logic fix applied
- Re-run produces trips/taxi within 25% of target (closer to 19+ vs current 14.36)
- No regression in other passing metrics
- Fix documented in code comment with hypothesis citation

**Stop and report after 7.3:** share the new validation grade. If grade is now A− (16+/19), proceed. If still B− or lower, return to 7.2.

---

## Sub-phase 7.4 — Loaded-ratio investigation (~2 hours)

**Goal:** Resolve why loaded ratio is 45.06% vs 47.5% target by construction.

### The math vs reality

By construction: `loaded_mean / (empty_mean + loaded_mean) = 4.6 / (5.1 + 4.6) = 47.4%`.

Observed: 4.47 km loaded, 9.92 km cycle, ratio 45.06%.

Three hypotheses:

| # | Hypothesis | Test |
|---|-----------|------|
| L1 | Empty mean is actually > 5.1 km (log-normal sampling bias) | Compute empirical mean from diagnostic.csv |
| L2 | RETURN_HOME_LEG inflates total km but isn't counted as loaded | Compute ratio with and without RETURN_HOME rows |
| L3 | AT_STAND_WAIT durations interleaved create additional implicit empty distance | Check if AT_STAND emits a non-zero distance |

### Diagnostic query

```python
loaded_total = df[df.leg_type == "LOADED_LEG"].distance_km.sum()
empty_total = df[df.leg_type == "EMPTY_LEG"].distance_km.sum()
return_total = df[df.leg_type == "RETURN_HOME_LEG"].distance_km.sum()

print(f"Without return home: loaded ratio = {loaded_total / (loaded_total + empty_total):.3f}")
print(f"With return home: loaded ratio = {loaded_total / (loaded_total + empty_total + return_total):.3f}")
print(f"Total km observed: {loaded_total + empty_total + return_total}")
print(f"Sim avg km/taxi: ?")
```

### Likely answer

Most likely L2 is the dominant cause: RETURN_HOME_LEG distance is ~5 km per taxi, summing to ~5 × 24,697 = ~125,000 extra empty km that's added to the denominator. Excluded from the 実車率 = loaded/(loaded+empty) calculation, it would push the ratio back toward 47.5%.

### Fix options for L2

- **Option A:** Don't emit RETURN_HOME as an empty leg in the validation engine; compute 実車率 only over OCCUPIED + DUAL_MODE legs
- **Option B:** Include RETURN_HOME and shrink the empty-leg mean to compensate (less principled)
- **Option C:** Skip RETURN_HOME entirely (don't model end-of-shift return)

Per MLIT 旅客自動車運送事業等報告規則, 走行キロ explicitly includes ALL driving — return-to-garage included. So the original definition would be Option C-equivalent (don't count return as part of revenue work). But this is contested; some companies report return-to-garage as work time, others don't.

**Recommendation:** Option A (validation engine excludes RETURN_HOME from 実車率 calc; total km still includes it for daily-distance metric).

**Paper documentation:** the existing paper §4 outline is 4.1 Aggregated-Data Input Set, 4.2 Agent State Machine, 4.3 Two-Dimensional Calibration, 4.4 Demand Zone Synthesis, 4.5 Fare Calculation. None of these owns the stand-queue / return-home discussion. Add a new **§4.6 (Stand Queue Dynamics and Return-Home Accounting)** that documents both the AT_STAND wait assumptions (from B6) and the L2 return-home decision (from B7). Approximate length: 250 words.

### Acceptance criteria (7.4)

- Loaded distance ratio corrected to within 1 pp of 47.5% target
- Decision documented (which option taken and why)
- Validation pass count improves by ≥1

**Stop and report after 7.4.** Move to 7.5.

---

## Sub-phase 7.5 — Log-normal truncation fix (~1 hour)

**Goal:** Eliminate the 0.16 km min violation. The bound is 0.5 km but log-normal sampling produces values below this.

### Two valid approaches

| Method | Pro | Con |
|--------|-----|-----|
| Clamp: `dist = max(0.5, sample())` | Fast, simple | Creates a spike at 0.5 km |
| Truncated log-normal: redraw if below floor | Cleaner distribution | Slightly slower; rare retry |

### Decision rule

If <2% of samples currently violate the floor: clamp is acceptable; the spike is small.
If >5% violate: truncate; the spike is significant.

Compute violation rate from diagnostic.csv:
```python
loaded = df[df.leg_type == "LOADED_LEG"]
violation_rate = (loaded.distance_km < 0.5).sum() / len(loaded)
print(f"Loaded leg below 0.5 km floor: {violation_rate:.1%}")
```

### Implementation

In `ShiftSimulator.sampleLoadedDistance()`:

```java
private double sampleLoadedDistance() {
    double sample;
    int attempts = 0;
    do {
        sample = lognormalRandom(loadedMu, loadedSigma);
        if (++attempts > 100) {
            // Fallback to clamp on extreme retry
            sample = Math.max(loadedMin, Math.min(loadedMax, sample));
            break;
        }
    } while (sample < loadedMin || sample > loadedMax);
    return sample;
}
```

Same pattern for empty-leg.

### Acceptance criteria (7.5)

- All loaded leg distances ∈ [0.5, 15] km
- All empty leg distances ∈ [0.3, 12] km  
- Trip Distance Min Check PASS in validation
- No measurable runtime degradation (<1% slowdown)

**Stop and report after 7.5.** Move to 7.6.

---

## Sub-phase 7.6 — Production-grade GSA (~1 day work + ~12 hours overnight compute)

**Goal:** Replace the demo-grade Morris (4 trajectories) and Sobol (N=16) with publication-grade resolution.

### Morris re-run

Increase `MorrisDriver` trajectories from 4 to **12** (Saltelli 2010 recommends r=10–50 for screening; 12 is the lower end of acceptable).

```
Trajectories: 12
Parameters: 12
Sim runs: 12 × (12+1) = 156 runs
Single-core wall: ~8 hours
8-core parallel: ~1 hour
```

After re-run, the previously zero μ* parameters should resolve to non-zero values (some will be small but distinguishable from noise).

### Sobol re-run

Increase `SobolDriver` base samples from N=16 to **N=256** (Saltelli 2010 recommends N ≥ 100 for moderate confidence; N=256 gives narrow CI).

```
Base samples N: 256
Parameters in scope: top-5 from new Morris
Sim runs: N × (p + 2) = 256 × 7 = 1,792 runs
Single-core wall: ~90 hours
8-core parallel: ~12 hours overnight
```

If 8-core parallelization is unavailable, fall back to N=128 → 896 runs, 6 hours.

### Output processing

Update `sobol_results.csv` with:
- `S_first` should now be non-negative for all rows (or differences within CI bounds)
- `S_total` should be stable
- Add `S_first_ci_low, S_first_ci_high` columns (95% CI from bootstrap)

### Update paper §7

Replace the "demonstration design" caveats with production-grade language. The S_first column becomes interpretable as variance fraction (no more negative values). Update the table to show CI bounds.

### Acceptance criteria (7.6)

- Morris with 12 trajectories produces μ* > 0 for at least 7 of 12 parameters
- Sobol N=256 produces S_first ∈ [0, 1] for all rows (or within CI)
- Paper §7 reflects production-grade indices with confidence intervals
- All compute runs to completion (no failed sub-runs)

**Stop and report after 7.6.** Move to 7.7.

---

## Sub-phase 7.7 — Re-validate, write findings into paper (~2 hours)

**Goal:** Final validation runs after all fixes; update paper with diagnostic findings as a strength, not a weakness.

### Re-run the six labeled validation runs (per Phase 9 spec)

1. In-sample baseline (Tokyo FY2024)
2. Temporal hold-out (Tokyo FY2023 baseline)
3. PRHS Scenario A (4-window FY2024)
4. PRHS Scenario B (1-window R8)
5. Distribution overlap analysis (Ranjit method)
6. Sensitivity sanity check (top-3 GSA parameters)

Expected validation grade: **A** (16+/19) on baseline. If still B+/A−, 7.2 hypothesis was incomplete; back to 7.2.

### Update paper sections

**§6 Validation:**
- Update Table 4 (in-sample) with new numbers
- Add a new subsection "Diagnostic Trail" describing the 7.2 hypothesis-test methodology and findings
- Frame the original 14.36 vs 25.30 gap as a *finding* about Tokyo taxi shift dynamics, not a sim bug

**§7 Heuristic Sensitivity:**
- Replace demo-grade tables with production-grade
- Discuss interaction effects newly visible at higher Morris resolution

**§8 Discussion:**
- New paragraph: "Fleet operating rate emerges by construction (zero GSA variance, exact match). The loaded-km ratio (実車率) emerges within 1 pp of THTA target after [the 7.4 fix]. Trips per active taxi (25.3 target, [observed]) [match within tolerance | exhibit a residual gap explained by H[X]]."
- Honest framing of any residual gaps

**§9 Conclusion:**
- Update headline grade if A achieved
- Note the diagnostic methodology as a methodological contribution

### Acceptance criteria (7.7)

Acceptable outcomes (either is sufficient):

**Outcome A — Grade A (16+/19) on baseline.** Pure success.

**Outcome A− with explained residual.** Grade A− (15/19) IF every remaining FAIL metric has a quantified explanation in `h1_h7_findings.md` AND that explanation is reproduced in paper §6 Diagnostic Trail subsection. The diagnostic itself becomes a methodological contribution — *"the simulation tells us why the gap exists, not just whether one exists."*

Either outcome requires:

- All 6 labeled runs produce validation.csv
- Paper compiles cleanly with `xelatex`
- Diagnostic trail added to §6 (new ~200 word subsection)
- Production-grade GSA tables in §7

If neither A nor A−-with-explanation holds (e.g., Grade B− and unexplained gaps), return to §7.2 and re-rank hypotheses with whatever new evidence the diagnostic exposed.

**Stop and report after 7.7.** This is the final B7 milestone.

---

## What B7 produces (deliverables)

| Artifact | Path |
|----------|------|
| Diagnostic CSV writer | `src/taxi/sim/DiagnosticWriter.java` |
| Per-cycle diagnostic data | `output/trips/taxi/tokyo/<run>/diagnostic.csv` |
| Hypothesis test findings | `output/trips/taxi/tokyo/<run>/h1_h7_findings.md` |
| Production-grade GSA outputs | `Pseudo-PFLOW/config/taxi/tokyo/morris_results.csv` (12-trajectory) + `sobol_results.csv` (N=256) |
| Updated paper | `docs/taxi_abm_paper.tex` (§6 Diagnostic Trail subsection, §7 production GSA, §8 honest discussion) |
| Final validation grade | A target (16+/19) |

---

## What NOT to do in B7

- ❌ Do not pre-tune parameters to match THTA targets without diagnostic evidence
- ❌ Do not silently change the loaded distance mean from 4.6 km (this is THTA-verified)
- ❌ Do not silently change empty distance mean from 5.1 km (this derives from σ_target)
- ❌ Do not delete `*.legacy.bak` files (still kept until paper submission)
- ❌ Do not skip the diagnostic step for any of I1, I2, I3
- ❌ Do not claim the residual gap is "negligible" without GSA evidence
- ❌ Do not increase compute spend before checking 8-core parallelization availability

---

## Estimated total time

| Sub-phase | Code work | Compute | Wall time |
|-----------|-----------|---------|-----------|
| 7.1 Instrumentation | 2 h | 1 run | 2 h |
| 7.2 Hypothesis testing | 3 h | included in 7.1 | 3 h |
| 7.3 Targeted fix | 3 h | 1-2 runs | 3 h |
| 7.4 Loaded-ratio fix | 2 h | 1 run | 2 h |
| 7.5 Truncation fix | 1 h | 1 run | 1 h |
| 7.6 GSA production | 8 h code | 12 h overnight (8-core) | 20 h |
| 7.7 Re-validate + paper | 2 h | 6 runs | 2 h |
| **Total** | **~21 hours code** | **~12 hours overnight compute** | **~33 hours over 2-3 days** |

---

## After B7

If grade reaches A and §6 / §7 / §8 are populated with diagnostic findings:
- Project is **submission-ready**
- Cover letter, figure generation, journal style polish are the remaining tasks

If grade stays B+/B−:
- Iterate on 7.2 hypothesis ranking
- Consider whether the 41% gap reflects a real Tokyo phenomenon (e.g., post-COVID labor undersupply) that the paper should *describe* rather than *erase*

The most academically defensible outcome is: **calibration matches FY2024 within 10% on critical metrics, and the diagnostic trail explains residual gaps as observable phenomena.** That's a paper. The other defensible outcome is: **Grade A across the board.** Either is fine.
