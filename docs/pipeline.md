# Build & Pipeline

Compilation and the audit pipeline are driven by scripts at the repo root and
under `native/`.

## Native build (`native/`)

| Script | Purpose |
|---|---|
| `native/build_mac.sh` | Compile `src/` → `bin/` on macOS / Linux |
| `native/build_win.bat` | Compile `src/` → `bin/` on Windows |

`bin/` holds the compiled `.class` tree. It is gitignored, regenerable, and
required at runtime — every `run_audit_*` script expects it to be populated.

## Audit pipeline (repo root)

| Script | Purpose |
|---|---|
| `run_audit_pipeline.bat` | Full pipeline (Windows): compile → truck sim → taxi sim → trajectory → validation |
| `run_audit_pipeline.sh` | Full pipeline (Unix) |
| `run_audit_traj_only.bat` | Trajectory-generation phase only (re-uses prior trip CSVs) |

Pipeline stage flow:

```
native/build_*       → bin/                                    (compile if stale)
truck.sim.*          → ${PFLOW_HOME}/output/trips/truck/run_*/
taxi.sim.*           → ${PFLOW_HOME}/output/trips/taxi/<city>/run_*/
traj.TrajectoryMain  → ${PFLOW_HOME}/output/trajectory/{truck,taxi}/run_*/
validation           → run_*/validation.csv                    (51 MLIT tests / city baselines)
```

Outputs land at `${PFLOW_HOME}/output/`, which is configured per-machine and
treated as LOCAL-ONLY. See [`config-schema.md`](config-schema.md) for the
`${PFLOW_HOME}` resolution rules.

## Maven (`pom.xml`)

`pom.xml` is **not** the build system. It exists for dependency resolution
only:

```bash
mvn dependency:resolve
```

…populates `~/.m2/repository/` with JTS, GeoTools, HSQLDB, EJML, etc., which
the `native/build_*` scripts add to the classpath. The actual `javac`
invocation lives in those scripts.

## Heap sizing

| Workload | Recommended `-Xmx` |
|---|---|
| Truck sim | 12 GB |
| Taxi sim | 4 GB |
| Trajectory generator | 40 GB (DRM network is large) |

The audit-pipeline scripts pass these flags directly; if invoking a class by
hand, set them on the `java` command line.

## Output directories

The simulators write under `output/` relative to the repo root. That tree is
gitignored (regenerable, large). For long-term retention, use
`${PFLOW_HOME}/output/` via the corresponding config property — that location
is also LOCAL-ONLY but lives outside the Git working tree.
