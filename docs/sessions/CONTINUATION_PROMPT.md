# Tokyo Taxi ABM Rewrite — 6-Batch Continuation Prompts

The 8-phase rewrite (DESIGN.md §4) is grouped into **6 batches**, each ending with a stop-and-report checkpoint where you review before greenlighting the next batch.

| Batch | Phases | Code time | Compute | Why this checkpoint |
|-------|--------|-----------|---------|---------------------|
| **B1** | 2 + 3 | ~7 h | minimal | First measurable behavioral change (distance bias resolved) |
| **B2** | 4 | ~5 h | minimal | DUAL_MODE — architectural inflection, high-judgment |
| **B3** | 5 + 6 | ~6 h | minimal | AT_STAND + PRHS regulatory windows — mechanical pair |
| **B4** | 7 | ~2 h | minimal | Legacy removal — irreversible |
| **B5** | 8 | ~7 h + ~25 h compute | overnight Sobol | GSA — research findings, paper §7 results |
| **B6** | 9 + paper polish | ~1 h | minimal | Final 6 validation runs + paper sections refresh |

After each batch, paste the next batch's prompt block.

---

## Environment prep (do once, before B1)

### File map (all should already exist)

| File | Purpose | Path |
|------|---------|------|
| `CONTINUE.md` | Full handoff context | `H:\Dropbox\PFLOW\CONTINUE.md` |
| `DESIGN.md` v3 | 13 locked decisions, 8-phase plan | `H:\Dropbox\PFLOW\references\taxi_data_sources\DESIGN.md` |
| `RESEARCH_QUESTION.md` | Paper positioning | `H:\Dropbox\PFLOW\references\taxi_data_sources\RESEARCH_QUESTION.md` |
| Updated enums | Phase 1 result | `H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\TaxiType.java` + `TaxiStatus.java` |
| Updated config | v6.x + v7.0 keys | `H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties` |
| Legacy backups | Rollback safety | `H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\*.legacy.bak` (5 files) |

### Optional pre-batch checklist

```cmd
:: Confirm legacy backups
dir H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\*.legacy.bak

:: Smoke-test Phase 1 — should produce Grade C
cd H:\Dropbox\PFLOW\scripts
compile_taxi_v52.bat
run_taxi.bat ..\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties

:: Optional: tag current state for rollback
cd H:\Dropbox\PFLOW
git add -A
git commit -m "Phase 0+1 complete; ready for B1"
git tag pre-batch1-2026-04-26
```

### Launch context

Set Claude Code working directory to **`H:\Dropbox\PFLOW`** so `CLAUDE.md` is auto-discovered.

---

## Batch 1 — Phases 2 + 3 (scaffold + log-normal distances)

**Paste this block to start B1:**

````
I'm continuing the Tokyo Taxi ABM rewrite for the Pseudo-PFLOW project (Sekimoto Lab, UTokyo CSIS). Phases 0 and 1 are complete. We're starting Batch 1 = Phases 2 + 3 of the 8-phase plan.

Read in order before any action:
1. H:\Dropbox\PFLOW\CONTINUE.md
2. H:\Dropbox\PFLOW\CLAUDE.md
3. H:\Dropbox\PFLOW\references\taxi_data_sources\DESIGN.md (especially §4 Phase plan)
4. H:\Dropbox\PFLOW\references\taxi_data_sources\RESEARCH_QUESTION.md
5. H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\TaxiType.java
6. H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\TaxiStatus.java
7. H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties

Then verify Phase 1 baseline:
  cd H:\Dropbox\PFLOW\scripts
  compile_taxi_v52.bat
  run_taxi.bat ..\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties
Expected: Grade C (legacy engine still default). If compile fails, debug Phase 1 first; do NOT proceed.

Then execute Phase 2 (~4 hours):
- Create H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\ShiftSimulator.java
- Method simulateShift(taxi, simStartSeconds, simDurationSeconds) returning List<TaxiTrip>
- Phase 2 implementation: delegate to a private method that reproduces legacy logic exactly
- Wire engine flag branch into TaxiSimulation.java's trip-generation entry point: if config.isShiftTimeEngine() then call ShiftSimulator else legacy
- Verify: with engine=legacy → unchanged Grade C. With engine=shift_time → IDENTICAL Grade C (delegation preserves behavior).
- Phase 2 contract: ZERO behavioral change between engines.

Then execute Phase 3 (~3 hours):
- Replace selectDistanceGuidedDestination rejection-resampling with single-sample log-normal + nearest-valid-projection
- Loaded leg: lognormal(mu=ln(4.6)-0.18, sigma=0.6), bounded [0.5, 15] km
- Empty leg: lognormal(mu=ln(5.1)-0.245, sigma=0.7), bounded [0.3, 12] km
- Nearest-valid-projection: if sampled position fails spatial validation, project to nearest valid grid cell within 500m; if no valid cell, fallback to last known valid position
- This Phase 3 logic only runs in the shift_time engine path; legacy engine untouched
- Verify: with engine=shift_time, sim avg trip distance moves from ~6 km → ~4.6 km. Validation grade target B+ or A-.

After Phase 3 completes, run validation, write a brief report including:
- Compile + run status for both engines
- Grade with engine=legacy
- Grade with engine=shift_time
- Avg trip distance with shift_time
- Mean loaded distance, mean empty distance per emitted trip
- Any unexpected findings

Then STOP and await Batch 2 greenlight.

Constraints (apply throughout):
- Aggregated-only data input (no GPS, no app logs, no person-level surveys)
- Configuration-driven (no hardcoded numerical values)
- Backward compat: legacy engine must remain functional through Phase 6
- Trip count is OUTPUT in shift_time engine, not INPUT
- No multi-day simulation (single-day paradigm)
- Heuristic priors documented as such

Do NOT in B1:
- implement DUAL_MODE state transitions (that's Phase 4 / B2)
- implement AT_STAND queue (Phase 5 / B3)
- implement PRHS windows (Phase 6 / B3)
- delete legacy engine code (Phase 7 / B4)
- modify TaxiAgent.java field set
- delete .legacy.bak backups
- run global sensitivity analysis (Phase 8 / B5)

Working preferences (CLAUDE.md): terse responses, action first, learning-mode insights when useful, push back when needed, STOP and ask if facing an unspecified decision.

Begin.
````

**B1 acceptance criteria** (what to verify before greenlighting B2):

- Both engines compile + run without error
- Legacy engine produces unchanged Grade C
- shift_time engine after Phase 3 produces avg loaded distance ~4.6 km (was ~6 km)
- shift_time engine grade ≥ legacy engine grade (no regression)
- No `[ERROR]` lines in either run's stdout

If acceptance criteria fail, paste the failure details and ask Claude Code to debug before proceeding.

---

## Batch 2 — Phase 4 (DUAL_MODE state + per-type mode-mix)

**Paste this block to start B2 after B1 is verified:**

````
B1 (Phases 2+3) verified. Beginning Batch 2 = Phase 4 of DESIGN.md.

If Claude Code memory was reset, re-read:
1. H:\Dropbox\PFLOW\CONTINUE.md
2. H:\Dropbox\PFLOW\references\taxi_data_sources\DESIGN.md §2 (architecture) and §4 Phase 4 spec
3. H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\ShiftSimulator.java (current state from B1)

Execute Phase 4 (~5 hours):
- Create H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\ModeMixResolver.java
  - Input: TaxiType
  - Output: probability vector (p_street, p_app, p_stand) summing to 1.0
  - Loads per-type shares from taxi_config.properties (taxi.type.*.mode.*)
- Update ShiftSimulator.simulateShift():
  - After each OCCUPIED dropoff, sample whether next is AT_STAND (prob = stand_share for taxi type) or DUAL_MODE
  - In DUAL_MODE: sample empty leg distance (lognormal from B1), move toward zone-attractiveness centroid, at end of leg sample pickup mode (street vs app proportionally to street/(street+app))
  - DUAL_MODE pickup → OCCUPIED state with loaded leg distance sampling and fare calculation
  - For RIDE_HAIL_PRHS: street probability is 0% in DUAL_MODE (regulatory)
- Phase 4 logic only runs in shift_time engine; legacy engine untouched

Verify after Phase 4:
- Compile + run with engine=shift_time
- Per-type mode shares match configured values within 5%:
  - LOCAL: 70/25/5 street/app/stand
  - CITYWIDE: 60/30/10
  - APP_PREFERRED: 20/75/5
  - HUB: 10/30/60
  - RIDE_HAIL_PRHS: 0/100/0
- Per-type validation report (passenger trips per type) produced
- Aggregate trips/active-taxi/day target ~25.3 (THTA FY2024)
- Grade target A-

Report:
- Per-type mode share table (configured vs observed)
- Per-type trip count (LOCAL gets 50% of trips, etc.)
- Aggregate validation grade
- Any unexpected interactions between DUAL_MODE state transitions and PRHS taxis

STOP and await B3.

Constraints unchanged from B1.

Do NOT in B2:
- implement AT_STAND queue dynamics (B3)
- implement PRHS regulatory windows (B3)
- delete legacy engine
- run sensitivity analysis (B5)

Begin.
````

**B2 acceptance criteria:**
- Each of 5 types receives the configured share of trips ±5%
- Mode-mix ratios per type within 5%
- Aggregate grade A- or better
- No new compile/runtime errors

---

## Batch 3 — Phases 5 + 6 (AT_STAND queue + PRHS regulatory windows)

**Paste this block after B2 is verified:**

````
B2 (Phase 4) verified. Beginning Batch 3 = Phases 5 + 6.

If memory reset, re-read CONTINUE.md and DESIGN.md §4 Phases 5 + 6.

Execute Phase 5 (~3 hours) — AT_STAND queue:
- Add AT_STAND state handling in ShiftSimulator.simulateShift()
- When taxi enters AT_STAND state:
  - Move position to nearest hub-zone (airport / major-station / entertainment classified zone)
  - Sample wait time from Exp(lambda) where 1/lambda is the configured mean for that stand type:
    - airport: 8 min mean (taxi.stand.wait.airport.mean.minutes)
    - major station: 12 min mean
    - entertainment: 6 min mean
  - Wait time accumulates against shift_remaining
  - At end of wait, transition to OCCUPIED with loaded leg as usual
- HUB taxis enter AT_STAND ~60% of the time (stand mode share)
- RIDE_HAIL_PRHS never enters AT_STAND (already enforced via 0% stand share)
- Document the 10-min default + variants as assumed values in code comments (no published primary source)

Verify after Phase 5:
- HUB-type taxis show >50% of time in AT_STAND state when in airport/station zones
- AT_STAND wait times follow Exp distribution (sample histogram)
- Aggregate trips/taxi may slightly drop (stand wait consumes shift time)

Execute Phase 6 (~3 hours) — PRHS regulatory windows:
- Add PRHS window enforcement in ShiftSimulator.simulateShift() for RIDE_HAIL_PRHS taxis
- Read prhs.window.N keys from config (count, window list as HHMM-HHMM,DAYS format)
- For PRHS taxis: nextValidPRHSWindow(shift_start) clamps shift to nearest valid window; returns null if none overlap
- If null window returned, taxi remains OFF_DUTY for the day (does not contribute trips)
- Create second config file H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\tokyo\taxi_config_tokyo_r8.properties as a copy of the default but with prhs.window.count=1 (only Mon-Fri 7-10am)
- Default config (taxi_config.properties) is FY2024 baseline = 4 windows

Verify after Phase 6:
- Run baseline (4 windows): PRHS trips concentrate inside the 4 published MLIT windows; total ~1,365/day target
- Run R8 contraction config (1 window): PRHS trips ~250-400/day (lower due to fewer windows)
- Both runs report PRHS share of total taxi trips within MLIT-published 0.22% range

Report after both phases complete:
- Phase 5: AT_STAND wait time distribution (mean, P50, P95) per stand type
- Phase 5: HUB taxi behavior (% of time in AT_STAND)
- Phase 6: PRHS trip count per scenario (FY2024 baseline vs R8 contraction)
- Phase 6: PRHS trips by window (which window contributed which fraction)
- Aggregate validation grade for both scenarios

STOP and await B4.

Do NOT in B3:
- delete legacy engine (B4)
- run sensitivity analysis (B5)

Begin.
````

**B3 acceptance criteria:**
- HUB taxis show ≥55% AT_STAND share in airport zones
- PRHS Scenario A (4 windows): ~1,365 trips/day, all within published windows
- PRHS Scenario B (1 window): clearly lower volume, all within Mon-Fri 7-10am
- Aggregate grade still A- or better

---

## Batch 4 — Phase 7 (legacy removal)

**Paste this block after B3 is verified:**

````
B3 (Phases 5+6) verified. Beginning Batch 4 = Phase 7 — legacy engine removal.

This is the irreversible step. Confirm B3 acceptance is solid before proceeding.

If memory reset, re-read CONTINUE.md and DESIGN.md §4 Phase 7.

Execute Phase 7 (~2 hours):
- Change default in taxi_config.properties: taxi.simulation.engine=shift_time
- In TaxiSimulation.java, REMOVE the legacy code path:
  - The findValidTripPair method
  - The MAX_ATTEMPTS rejection-resampling loop (now obsolete)
  - The original processTripGeneration / generateTrips trip-count loop
  - The selectDistanceGuidedDestination rejection-based sampler
  - Any helper methods used only by the legacy path
- KEEP the .legacy.bak files (do not delete; they're rollback safety until paper submission)
- Update taxi_config.properties comment block: remove "legacy/v6.x" labels; v7.0 is now the only engine
- Update H:\Dropbox\PFLOW\scripts\compile_taxi_v52.bat if it references removed source files

Verify after Phase 7:
- Compile clean (no unused-import warnings)
- Run with default config produces same output as B3's shift_time run
- Validation grade A- or better preserved
- Code is ~150 lines shorter than pre-B4 (verify via wc -l)

Update paper section in parallel:
- H:\Dropbox\PFLOW\docs\taxi_abm_paper.tex Section 5 (Implementation):
  - Remove references to "legacy v6.x engine"
  - Update class list (ShiftSimulator.java, ModeMixResolver.java added)
  - Update line count summary

Report:
- Compile clean? (no warnings)
- Validation grade post-cleanup
- Net lines removed
- Files affected

STOP and await B5.

Do NOT in B4:
- delete .legacy.bak files (keep until after paper submission)
- run sensitivity analysis (B5)

Begin.
````

**B4 acceptance criteria:**
- Compile clean, no warnings
- Default-config run reproduces post-B3 grade
- Net code reduction visible in TaxiSimulation.java
- Paper Section 5 reflects current architecture

---

## Batch 5 — Phase 8 (Global Sensitivity Analysis: Morris + Sobol)

**Paste this block after B4 is verified:**

````
B4 (Phase 7) verified — legacy engine removed. Beginning Batch 5 = Phase 8 GSA.

This batch has substantial compute time (~25 hours single-core for Sobol). Plan to run overnight or use parallelization.

If memory reset, re-read DESIGN.md §3 (heuristic priors table) and §4 Phase 8 spec.

Execute Phase 8.1 — Morris elementary effects screening (~3 hours code + ~50 sim runs ~2.5 hours):

- Create H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\analysis\MorrisDriver.java
- Reads parameter ranges from new file H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\tokyo\gsa_params.csv
- gsa_params.csv columns: parameter_name, default, min, max, config_key
- 12 parameters per DESIGN.md §3:
  1. taxi.type.app_preferred.share (0.20-0.60)
  2. taxi.type.local.mode.street (0.50-0.85)
  3. taxi.type.app_preferred.mode.app (0.50-0.95)
  4. taxi.type.hub.mode.stand (0.40-0.80)
  5. taxi.stand.wait.airport.mean.minutes (4-16)
  6. taxi.stand.wait.station.mean.minutes (6-24)
  7. trip.distance.empty.sigma (0.3-1.2) [new key, log-normal sigma for empty leg]
  8. trip.distance.loaded.sigma (0.3-1.0) [new key, log-normal sigma for loaded leg]
  9. attractiveness.beta1.jobs (0.5-1.5)
  10. attractiveness.beta2.shops (0.4-1.2)
  11. attractiveness.beta3.nightlife (0.3-1.0)
  12. attractiveness.beta4.residential (0.2-0.8)
- Morris sampling: 6 trajectories of length 12 = ~50 sim runs
- For each run, override the parameter via environment variable or config patch, capture validation.csv output
- Compute Morris mu* and sigma per parameter for output metric: aggregate validation grade
- Output: morris_results.csv with parameter_name, mu_star, sigma, rank_by_mu_star

Identify top-5 parameters by mu*. These go to Sobol.

Execute Phase 8.2 — Sobol indices (~4 hours code + ~22 hours single-core / ~3 hours 8-core compute):

- Create H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\analysis\SobolDriver.java
- Saltelli sampling N=64, p=5 -> N(p+2) = 448 sim runs
- For each output metric (5 metrics: 実働率, 実車率, trips/taxi, daily revenue, distribution overlap coefficient), compute first-order Sobol indices S_i and total-order indices S_Ti
- Output: sobol_results.csv with parameter, output_metric, S_first, S_total, confidence_interval

If parallelization available (xargs -P or PowerShell ForEach-Object -Parallel), run in 8 parallel streams to bring compute to ~3 hours. Otherwise run overnight (~22 hours).

Update paper Section 7 (Heuristic Sensitivity):
- Replace the previous text with Morris screening result table (12 -> 5 parameters)
- Add Sobol indices table per output metric
- Discussion: which parameters drive which metrics; interactions evident from S_total >> S_first

Report:
- Morris screening complete? (top-5 parameters identified)
- Sobol indices computed for 5 metrics?
- Top-3 most influential parameters per metric
- Any surprising findings (e.g., mode-mix more sensitive than fleet share)
- Updated taxi_abm_paper.tex Section 7

STOP and await B6.

Do NOT in B5:
- run final validation runs (B6)
- modify the simulation core code
- delete .legacy.bak files

Begin.
````

**B5 acceptance criteria:**
- morris_results.csv produced with 12 parameters ranked
- sobol_results.csv produced with 5 metrics × 5 parameters × 2 indices
- Paper Section 7 rewritten with sensitivity decomposition
- No sensitivity analysis run perturbed the production sim code

---

## Batch 6 — Phase 9 + paper polish

**Paste this block after B5 is verified:**

````
B5 (Phase 8 GSA) verified. Beginning Batch 6 = Phase 9 final validation runs + paper polish.

If memory reset, re-read DESIGN.md §4 Phase 9 and the paper draft.

Execute Phase 9 (~1 hour, ~6 sim runs):

Run 1 — In-sample baseline (Tokyo FY2024):
  cd H:\Dropbox\PFLOW\scripts
  run_taxi.bat ..\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties
  Expected: Grade A or better. Output dir: output/trips/taxi/tokyo/run_TIMESTAMP_baseline/

Run 2 — Temporal hold-out (Tokyo, calibrated FY2024, validate against FY2023):
  cd H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\validation
  ren taxi_baseline_tokyo.csv taxi_baseline_tokyo_fy2024.csv
  ren taxi_baseline_tokyo_fy2023.csv taxi_baseline_tokyo.csv
  cd H:\Dropbox\PFLOW\scripts
  run_taxi.bat ..\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties
  :: Restore:
  cd H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\validation
  ren taxi_baseline_tokyo.csv taxi_baseline_tokyo_fy2023.csv
  ren taxi_baseline_tokyo_fy2024.csv taxi_baseline_tokyo.csv

Run 3 — PRHS Scenario A (FY2024 4-window):
  Already produced in Run 1 (default config = 4-window).

Run 4 — PRHS Scenario B (R8 1-window contraction):
  run_taxi.bat ..\Pseudo-PFLOW\config\taxi\tokyo\taxi_config_tokyo_r8.properties

Run 5 — Distribution overlap analysis (Ranjit 2018 method):
  Compute overlap coefficient between simulation trip-distance distribution and THTA panel data.
  Implementation: post-process trip output CSV, bin into 0.5km histograms, compare to panel distribution from THTA monthly survey (28 companies, 1,939 taxis).
  Output: distribution_overlap.csv

Run 6 — Sensitivity sanity check (sample from Sobol top-3 parameters):
  Run 3 sims at min/median/max of the most influential parameter from B5 results.
  Verify the paper's claimed sensitivity behavior holds.

Update paper sections:
- Section 6 (Validation): fill in all [TBR] placeholders with actual numbers from runs 1-5
- Section 6.1 (In-sample): Run 1 results table
- Section 6.2 (Temporal hold-out): Run 2 vs FY2023 baseline comparison
- Section 6.3 (Distributional): Run 5 overlap coefficient table
- Section 6.4 (PRHS scenarios, NEW): Run 3 vs Run 4 comparison
- Section 7 (Sensitivity): already updated in B5
- Section 8 (Discussion): add observed vs target deltas; interpret any failures
- Section 9 (Conclusion): update with verified A-grade headline

Final cleanup:
- Remove [TBR] markers throughout taxi_abm_paper.tex
- Verify all citations in sources.bib resolve
- Spell-check + grammar pass
- Confirm xelatex compiles without error: cd H:\Dropbox\PFLOW\docs && xelatex taxi_abm_paper.tex

Report:
- All 6 runs completed with grade per run
- Paper section updates summary (which sections changed)
- Compile status of taxi_abm_paper.tex
- Any final issues blocking submission readiness

STOP. Project is ready for user review and submission preparation.

Begin.
````

**B6 acceptance criteria:**
- All 6 validation runs completed
- Paper sections 6, 7, 8, 9 fully populated (no [TBR])
- xelatex compiles without error
- Grade A or A+ on baseline run

---

## After B6 — what's left

### B7 — Scientific Diagnostic & Calibration (recommended next)

If the post-B6 validation grade is below A (typical: B− with three FAIL metrics sharing one root cause), proceed to **B7 = scientific diagnostic & calibration pass**.

B7 has its own dedicated spec:

📄 **`H:\Dropbox\PFLOW\BATCH_7_DIAGNOSTIC.md`** — full spec with paste-able prompt block (revised to v2 with hypothesis-space and sampling fixes)

B7 covers four issues hypothesis-by-hypothesis:
- **I1**: trips-per-taxi gap (14.36 vs 25.30) — **seven hypotheses (H1–H7)** including H7 Manhattan-factor inflation (the dominant prior). Diagnostic instrumentation with stratified sampling (≥50 taxis per type) → targeted fix from `gsa_params.csv` ranges only.
- **I2**: loaded ratio 45.06% vs 47.5% — three hypotheses (L1–L3); likely RETURN_HOME km accounting (validation engine fix, not a sim parameter change).
- **I3**: log-normal min-bound violation (0.16 km vs 0.5 floor) — bounded log-normal sampling with reject-and-redraw.
- **I4**: production-grade GSA — 12-trajectory Morris (~156 runs) + N=256 Sobol (~1,792 runs), with confidence intervals replacing the demo-grade negative S_first values.

**Sub-phase structure** (each ends with stop-and-report):
- 7.1 Instrumentation (DiagnosticWriter.java + stratified sampling, ~2 h)
- 7.2 Hypothesis testing (Python harness at `tools/b7_diagnostic_analyzer.py`, produces `<run_dir>/h1_h7_findings.md`, ~3 h)
- 7.3 Targeted fix (single-parameter, GSA-range constrained, ~3 h)
- 7.4 Loaded-ratio fix (likely Option A: exclude RETURN_HOME from 実車率 calc; document in new paper §4.6, ~2 h)
- 7.5 Truncation fix (~1 h)
- 7.6 Production-grade GSA (~8 h code + ~12 h overnight compute on 8-core)
- 7.7 Re-validate + paper polish (~2 h)

**Approach:** instrument → hypothesize → test → targeted fix. Each step generates evidence before action. The diagnostic trail itself becomes part of paper §6 (Diagnostic Trail subsection) and the new §4.6 (Stand Queue Dynamics and Return-Home Accounting).

**Acceptance:** either (a) **Grade A** (16+/19) on baseline, OR (b) **Grade A−** with every remaining FAIL metric quantitatively explained in `h1_h7_findings.md` and reproduced in paper §6. Either is publication-defensible.

**Effort:** ~21 hours code + ~12 hours overnight compute = ~33 hours wall over 2–3 days.

### Research-author tasks (Claude Code shouldn't attempt)

After B7 closes residual gaps:

1. Read the full paper draft and revise prose for journal style
2. Generate final figures (architecture diagram, validation table screenshots, Sobol heatmap)
3. Write cover letter for venue submission
4. Coauthor review
5. Submission to target venue (CACIE / TR Part C / SIGSPATIAL)

If you want Claude Code's help with any of these, paste a focused single-task prompt at that point.

---

## Failure recovery

If any batch fails to meet acceptance criteria:

1. Read Claude Code's report of what happened
2. Decide: debug-in-place or rollback?
3. **Debug in place:** paste a focused prompt with the specific failure and ask Claude Code to fix
4. **Rollback:** `git reset --hard pre-batch1-2026-04-26` (or equivalent tag from earlier batch)

The `.legacy.bak` files in `src/taxi/sim/` provide independent rollback for individual files if git is unavailable.

---

## Estimated total wall time

| Batch | Code | Compute | Wait |
|-------|------|---------|------|
| B1 | ~7 h | minimal | ~7 h |
| B2 | ~5 h | minimal | ~5 h |
| B3 | ~6 h | minimal | ~6 h |
| B4 | ~2 h | minimal | ~2 h |
| B5 | ~7 h | ~25 h Sobol | ~32 h (overnight) |
| B6 | ~1 h | minimal | ~1 h |
| **Total** | **~28 h** | **~25 h** | **~53 h ≈ 1 work-week** |

With 8-core parallelization on B5, drops to ~30 hours total. Without, plan B5 across two overnight stretches.
