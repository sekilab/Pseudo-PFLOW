# Config Schema

All configuration lives under `config/`. Two simulators are configured here
(truck and taxi); the people-flow ABM reads its config via the classpath
resource `src/main/resources/config.properties`.

## `config/truck/`

| File / dir | Role |
|---|---|
| `truck_config.properties` | Main truck-sim config (full fleet) |
| `truck_config_1pct.properties` | 1 % subsample |
| `truck_config_5pct.properties` | 5 % subsample |
| `truck_config_expanded.properties` | EXPANDED 106-zone nationwide mode |
| `truck_config_expanded_5pct.properties` | EXPANDED + 5 % subsample |
| `truck_config_unified.properties` | UNIFIED 134-zone Kanto + Keihanshin mode |
| `zones/intra.csv` | 37 intra-metropolitan zones |
| `zones/inter.csv` | 33 inter-metropolitan zones |
| `flows/od_volume.csv` | 70×70 O-D matrix (tons/day per commodity) |
| `flows/od_volume_expanded.csv` | 106×106 O-D matrix (trucks/day, GDP-disaggregated) |
| `facilities/` | Logistics POIs (industrial, logistics, retail, malls, ports) |
| `operations/` | Operational parameters (vehicle sizes, time windows, …) |
| `overlays/` | Zone-specific overrides (e.g., Osaka GA targets) |
| `validation/mfs_baseline.csv` | 51 MLIT validation targets |
| `README.md` | Property-by-property reference |

`TruckConfig.java` is the singleton loader; entries are surfaced via
`TruckConfig.getProperty(...)` with `${PFLOW_HOME}` resolution baked in.

## `config/taxi/`

Per-city directories with the same shape:

```
config/taxi/
├── tokyo/         taxi_config.properties + zones.csv
├── osaka/         ...
├── nagoya/        ...
├── kanagawa/      ...
├── kyoto/         ...
├── yokohama/      ...
├── chiba/         ...
├── saitama/       ...
├── kobe/          ...
├── shizuoka/      ...
├── validation/    taxi_baseline_<city>_<period>.csv
└── README.md
```

Each `taxi_config.properties` carries ~80 parameters: fleet, fare schedule,
agent-type mix, shift structure, demand zones, diagnostic toggles. See
`config/taxi/README.md` for the per-property reference.

## `config/archive/`

Historical configs preserved for traceability. Not loaded by current runs.

## `${PFLOW_HOME}` tokens

Property values can include the literal token `${PFLOW_HOME}`, e.g.

```properties
export.output.dir=${PFLOW_HOME}/output/trips/truck
input.directory=${PFLOW_HOME}/data/
```

`src/util/PathResolver.java` substitutes the token at load time using:

1. The `PFLOW_HOME` environment variable, if set.
2. Otherwise an OS-aware auto-detect:
   - Windows → `H:/Dropbox/PFLOW`
   - macOS / Linux → `~/Dropbox/PFLOW`

The token convention lets the same `.properties` file work on Windows and
macOS without per-machine forks.

## Test-variant naming

`*.properties.test_*` is gitignored; use that suffix for local experiments
without polluting `git status`.

## Environment template

`.env.example` at the repo root documents the small set of env vars the
codebase honours (chiefly `PFLOW_HOME`). Copy to `.env` for local overrides;
`.env` is gitignored.
