# Continuation Brief — Tokyo Taxi ABM Rewrite

**Last session ended:** 2026-04-26, Phase 0 + Phase 1 of the v6.x → v7.0 shift-time rewrite complete. The 8-phase plan (DESIGN.md §4) is grouped into **6 batches** (CONTINUATION_PROMPT.md), each ending with a stop-and-report checkpoint. Next batch: **B1 = Phases 2 + 3** (`simulateShift` scaffold + log-normal distance samplers).

This file tells Claude Code what's been done, what comes next, and the constraints that must hold. **Read this entire file before any code action.**

---

## 1. Where we are in the project

We are extending the Pseudo-PFLOW codebase (Sekimoto Lab, UTokyo CSIS) with a Tokyo-specific Taxi ABM, targeting a peer-reviewed paper. The central research question:

> *"Given only aggregated industry and regulatory statistics — fleet size, operating rate, loaded-km ratio, fare revenue, time-of-day demand — and no individual trajectory data, can a multi-agent simulation reproduce trip-level taxi behavior in large metropolitan areas, validated against the same aggregate constraints?"*

**Geographic focus:** Tokyo 特別区・武三交通圏 (Special Wards + Musashino + Mitaka). FY2024 calibration target.

**Paper positioning:** drafted at `docs/taxi_abm_paper.tex`. Title: *"An aggregated-statistics multi-agent simulation of Tokyo taxi operations: calibration against THTA FY2024 and a baseline for the April 2024 ride-share legalization."*

---

## 2. Required reading (in order)

Before any code change, read these files in this order:

1. **`H:\Dropbox\PFLOW\CLAUDE.md`** — project-level context, paths, conventions
2. **`H:\Dropbox\PFLOW\references\taxi_data_sources\DESIGN.md`** ⭐ — architectural design v3, all 13 locked decisions, 8-phase plan. **This is the bible. Do not deviate without check-in.**
3. **`H:\Dropbox\PFLOW\references\taxi_data_sources\RESEARCH_QUESTION.md`** — paper positioning, contribution list, anticipated reviewer critiques + counters
4. **`H:\Dropbox\PFLOW\references\taxi_data_sources\MASTER_SOURCES.md`** — 71-source citation registry; per-claim lookup table for the paper
5. **`H:\Dropbox\PFLOW\docs\taxi_abm_paper.tex`** — paper draft (Tokyo-focused, after `[refocus]` revision)
6. **`H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\TaxiType.java`** — Phase 1 enum (5 types now)
7. **`H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim\TaxiStatus.java`** — Phase 1 enum (6 states now)
8. **`H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties`** — current config state with v7.0 keys added

---

## 3. What's done (Phases 0 + 1)

### Phase 0 — Preparation
- Legacy backups created at `src/taxi/sim/*.legacy.bak` (TaxiSimulation, TaxiAgent, TaxiType, TaxiStatus, TaxiTrip)
- Engine flag `taxi.simulation.engine=legacy|shift_time` plumbed through TaxiConfig.java + getter `isShiftTimeEngine()`
- Default is `legacy` — current behavior unchanged

### Phase 1 — Five-type fleet enum + state machine
- `TaxiType` enum extended from 3 to 5 values: LOCAL (50%), CITYWIDE (15%), APP_PREFERRED (20%), HUB (9.5%), RIDE_HAIL_PRHS (0.5%). Names preserved (no LEGACY_ prefix).
- `TaxiStatus` enum extended from {IDLE} to {IDLE, OFF_DUTY, DUAL_MODE, AT_STAND, OCCUPIED, RETURNING_HOME}. IDLE preserved for legacy engine.
- `taxi_config.properties` Tokyo file extended with ~70 new keys: 5-type shares, 5-type × 3-mode mode-mix, AT_STAND wait variants, RIDE_HAIL_PRHS regulatory windows.

### Verified via the bash sandbox
- All file edits applied successfully
- TaxiConfig.java loads the new flag
- 5 type defaults sum to 1.000

### NOT yet verified (must verify in this Claude Code session)
- **Compile**: `H:\Dropbox\PFLOW\scripts\compile_taxi_v52.bat`
- **Run**: `H:\Dropbox\PFLOW\scripts\run_taxi.bat config\taxi\tokyo\taxi_config.properties`
- **Expected outcome**: Grade C identical to last run (legacy engine still active, behavior unchanged)

**Compile + run this BEFORE Phase 2 work.** If compile or run fails, Phase 1 introduced a regression that must be fixed first.

---

## 4. The 13 locked design decisions (do NOT re-litigate)

| # | Decision | Choice |
|---|----------|--------|
| Q1 | Architecture scope | Option II (hybrid shift-time loop) |
| Q2 | Fleet types | 5-type fleet (Phase 1 done) |
| Q3 | Demand model | Implicit zone attractiveness (no explicit λ(z,t) Poisson surface) |
| Q4 | Empty-leg distribution | Log-normal $\ln \mathcal{N}(\mu, \sigma)$, mean 5.1 km |
| Q5 | PRHS shift restrictions | Loose sampling, MLIT hours strictly enforced |
| Q6 | Per-zone mode-mix overrides | DROPPED — taxi-type defaults only |
| Q7 | AT_STAND queue | Fixed exponential wait, mean 10 min + variants |
| Q8 | Implementation strategy | Scaffold-first, 8 phases |
| Q9 | Reference paper | Ranjit 2018 IJGI 7(5):177 (Shibasaki Lab predecessor) |
| Q10 | Methodology drafting | Parallel with code, code prioritized |
| NEW-1 | APP_PREFERRED reallocation | LOCAL+CITYWIDE absorb (10:3 ratio); HUB+PRHS fixed |
| NEW-2 | PRHS transition | Two-scenario approach (FY2024 4-window vs R8 1-window) |
| NEW-3 | Sensitivity analysis | Global Sobol indices (Morris screening + Sobol on top-5) |

If any new design decision arises that's not covered here, **stop and ask the user**, do not improvise.

---

## 5. Next: Batch 1 = Phases 2 + 3 (~7 hours)

The full prompt for Batch 1 lives in `H:\Dropbox\PFLOW\CONTINUATION_PROMPT.md`.
Below is the spec for the constituent phases.

### Phase 2 — `simulateShift` skeleton (~4 hours)

### Goal

Create a new file `src/taxi/sim/ShiftSimulator.java` containing a `simulateShift(TaxiAgent)` method that, when `taxi.simulation.engine=shift_time` is set, produces the same trip-level output as the legacy engine (within random-seed tolerance). The skeleton structure is set up to receive v7.0 logic in Phases 3–6, but Phase 2 itself introduces NO behavioral changes.

### Steps

1. **Read `src/taxi/sim/TaxiSimulation.java`** — find the trip-generation entry point (`generateTrips` or equivalent). Understand how it iterates over the fleet and generates per-taxi trips.
2. **Create `src/taxi/sim/ShiftSimulator.java`** with:
   - Class `ShiftSimulator` (package-private)
   - Constructor takes `TaxiConfig` and any helpers it needs (DestinationZone list, GeoValidator, etc.)
   - Method `List<TaxiTrip> simulateShift(TaxiAgent taxi, long simStartSeconds, long simDurationSeconds)`
   - **Phase 2 implementation**: delegate to a private method that reproduces the legacy logic for now. Do NOT yet implement the new state machine.
3. **Wire into TaxiSimulation**:
   - In the trip-generation loop, branch on `config.isShiftTimeEngine()`:
     - If `false` (legacy): existing code path
     - If `true` (shift_time): call `shiftSimulator.simulateShift(taxi, ...)` and collect its output
4. **Verify zero regression**:
   - With `taxi.simulation.engine=legacy` (default): identical output to baseline
   - With `taxi.simulation.engine=shift_time`: identical output to legacy (modulo seed)
5. **Commit Phase 2** when both engine modes produce equivalent validation grades.

### Validation milestone for Phase 2

```cmd
:: With default config (engine=legacy) — should match prior run
H:\Dropbox\PFLOW\scripts\run_taxi.bat H:\Dropbox\PFLOW\Pseudo-PFLOW\config\taxi\tokyo\taxi_config.properties

:: Then flip engine via temporary config edit:
:: taxi.simulation.engine=shift_time
:: Re-run; expect identical Grade C (legacy delegation produces same output)
```

If grade differs by >1 metric between the two engines, **stop and debug**. Phase 2's contract is *zero behavioral change*.

### What NOT to do in Phase 2

- ❌ Do not implement DUAL_MODE state transitions (Phase 4 / B2)
- ❌ Do not implement AT_STAND queue (Phase 5 / B3)
- ❌ Do not change any legacy-engine code path
- ❌ Do not modify `TaxiAgent.java` field set
- ❌ Do not delete the `*.legacy.bak` backups

The whole point of Phase 2 is the scaffold. Phase 3 (still part of B1) introduces log-normal distance sampling — the first behavioral change.

### Phase 3 — Log-normal distance samplers + nearest-valid-projection (~3 hours, part of B1)

After Phase 2 verified, immediately proceed within Batch 1:

1. Replace rejection-resampling in `selectDistanceGuidedDestination` with single-sample log-normal:
   - **Loaded leg:** $\ln \mathcal{N}(\ln(4.6) - 0.18, 0.6)$, bounded $[0.5, 15]$ km
   - **Empty leg:** $\ln \mathcal{N}(\ln(5.1) - 0.245, 0.7)$, bounded $[0.3, 12]$ km
2. **Nearest-valid-projection:** if sampled position fails spatial validation, project to nearest valid grid cell within 500 m (no re-sampling).
3. **shift_time engine path only** — legacy engine untouched.
4. Verify: avg trip distance moves from ~6 km → ~4.6 km. Grade improves from C toward B+/A−.

### Batch 1 acceptance criteria

- Both engines compile + run
- Legacy engine: unchanged Grade C
- shift_time engine: avg loaded distance ~4.6 km
- shift_time engine: grade ≥ legacy engine grade (no regression)

---

## 6. Constraints that hold across all phases

- **Aggregated-only data input.** No GPS traces, no proprietary app logs, no person-level surveys. Verified by `SIM_AUDIT.md`.
- **Configuration-driven.** Zero hardcoded numerical values. Add config keys for any new parameter.
- **Backward compatibility.** The legacy v6.x engine must remain functional through Phase 6. Phase 7 removes it cleanly.
- **MLIT regulatory metrics emerge by construction.** 実働率 = (assigned active fleet) / (registered fleet). 実車率 emerges from empty/loaded distance ratio. **Trip count is OUTPUT, not INPUT** in shift_time engine. This is the central paper claim.
- **No multi-day simulation.** Single-day paradigm preserved. PRHS scenarios run as separate single-day configs (NEW-2 decision).
- **Heuristic priors documented as such.** All non-primary-source values flagged in code + paper.

---

## 7. The 6-batch plan (preview)

| Batch | Phases | Code | Compute | Greenlight gate |
|-------|--------|------|---------|------------------|
| **B1** | 2 + 3 | ~7 h | minimal | This batch (currently active) |
| **B2** | 4 (DUAL_MODE) | ~5 h | minimal | After B1 verification by user |
| **B3** | 5 + 6 (AT_STAND + PRHS) | ~6 h | minimal | After B2 |
| **B4** | 7 (legacy removal — irreversible) | ~2 h | minimal | After B3 |
| **B5** | 8 (Sobol GSA) | ~7 h | ~25 h overnight | After B4 |
| **B6** | 9 + paper polish | ~1 h | minimal | After B5 |

Each batch ends with a stop-and-report. The user reviews and pastes the next batch's prompt.

After **each batch**, validate, commit, update `docs/taxi_abm_paper.tex` to reflect what was actually implemented.

Full per-batch prompts live in `H:\Dropbox\PFLOW\CONTINUATION_PROMPT.md`.

---

## 8. Communication style with the user

The user (North) prefers:
- **Terse responses, no trailing summaries** (per `CLAUDE.md`)
- **Action first, reasoning second**
- **Learning-mode insights** when useful (`★ Insight ──────`)
- **Push back when needed** — don't agree just to agree

When stuck or facing an unspecified decision, **stop and ask** rather than improvise.

---

## 9. Recovery & rollback

If anything in Phase 2+ breaks the legacy engine:

```cmd
cd H:\Dropbox\PFLOW\Pseudo-PFLOW\src\taxi\sim
copy /Y TaxiType.java.legacy.bak TaxiType.java
copy /Y TaxiStatus.java.legacy.bak TaxiStatus.java
copy /Y TaxiSimulation.java.legacy.bak TaxiSimulation.java
copy /Y TaxiAgent.java.legacy.bak TaxiAgent.java
copy /Y TaxiTrip.java.legacy.bak TaxiTrip.java
```

Then restart the affected phase. Backups are intentionally preserved through Phase 7.

---

## 10. References folder layout (read for context)

```
H:\Dropbox\PFLOW\references\taxi_data_sources\
├── README.md                — index
├── MASTER_SOURCES.md ⭐     — 71-source registry, per-claim lookup
├── RESEARCH_QUESTION.md ⭐  — paper positioning + reviewer counters
├── DESIGN.md ⭐             — architectural design v3 (all 13 decisions)
├── LITERATURE_REVIEW.md     — Consensus/Scholar Gateway lit review
├── SIM_AUDIT.md             — verified "aggregated-only" claim
├── PHASE_AB_APPLIED.md      — Phase A+B summary (pre-rewrite calibration)
├── flag5_resolved.md        — ride-hail/car-share Tokyo volumes
├── VERIFIED.md              — direct PDF/CSV verification (4 ⚠E flags resolved)
├── RECHECK.md               — confidence-tier audit
├── SOURCES.md               — Wave 1 sources (legacy)
├── key_figures.md           — one-pager (legacy)
├── expanded_figures.md      — Wave 2 (legacy)
├── mobility_ecosystem.md    — Wave 3 (legacy)
└── sources.bib              — 71 BibTeX entries
```

The starred (⭐) files are the active references. The "legacy" ones are build history retained for traceability.

---

## 11. Final checks before starting Batch 1 (Phases 2 + 3)

- [ ] Read DESIGN.md §2 (final architecture) and §4 (8-phase plan)
- [ ] Read RESEARCH_QUESTION.md §3 (4-point contribution list)
- [ ] Read TaxiType.java + TaxiStatus.java (Phase 1 changes)
- [ ] Read taxi_config.properties (note v6.x and v7.0 sections)
- [ ] Compile + run with default `engine=legacy` — confirm still produces Grade C
- [ ] Confirm `*.legacy.bak` files exist in `src/taxi/sim/`

When all six confirmed: begin Phase 2.

After Phase 2 (engine flag toggle produces identical output) and Phase 3 (avg loaded distance moves to ~4.6 km), Batch 1 is complete. Pause, report to user, await **Batch 2 greenlight**.

**Stopping criteria within B1:**
- If Phase 2 fails to produce identical output between engines → STOP, debug, do not start Phase 3.
- If Phase 3 introduces a grade regression vs Phase 2 → STOP, report findings (could indicate incorrect distribution parameters).
- Otherwise complete both phases, then stop at the B1 acceptance gate.
