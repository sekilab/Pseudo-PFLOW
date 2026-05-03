# Architecture

The `src/` tree contains four simulation modules and shared infrastructure.

## Modules

### Truck ABM (`src/truck/sim/`)

Agent-based truck freight simulation calibrated to MLIT Material Flow
Statistics 2013.

| Property | Value |
|---|---|
| Entry point | `truck.sim.TruckSimulation` |
| Fleet | 1,434,510 active trucks |
| Daily trips | 2,789,209 |
| Truck types | DELIVERY (37%), MIXED_OPERATION (28.5%), LONG_HAUL (34.5%) |
| O-D zones | 70 (37 intra-metro + 33 inter-metro) |
| Commodity types | 9 |

Key classes (47 in module):

- `FleetFactory` — creates the agent fleet via parallel `ThreadLocalRandom`.
- `DestinationSelector` — multi-tier destination choice (G-A balance → POI → nearby zone → random).
- `TripGenerator` — trip-chain generation with commodity routing and time windows.
- `ZoneManager` — 70 zones with precomputed distance matrices.
- `ValidationEngine` — 51-test validation suite vs MLIT targets.
- `POIManager` — 586+ logistics POIs.
- `GeoValidator` — 3-layer land/water/raster spatial validation.
- `TruckConfig` — singleton config loader.
- `StandaloneValidator` — re-runs validation against an existing run directory.
- Subpackages: `spatial/` (`PointGenerator`, `TransportNetworkIndex`), `util/` (`CSVLoader`, `DistanceCalculator`, `ZoneBoundaryMapper`).

### Taxi ABM (`src/taxi/sim/`)

Multi-city taxi simulation calibrated to taxi-association statistics. Active
cities: Tokyo, Osaka, Nagoya, Kanagawa, Kyoto, Yokohama, Chiba. Configured but
not in routine use: Saitama, Kobe, Shizuoka.

| Property | Value |
|---|---|
| Entry point | `taxi.sim.TaxiSimulation` |
| Fleet (Tokyo) | 40,000 |
| Agent types (5) | LOCAL, CITYWIDE, APP_PREFERRED, HUB, RIDE_HAIL_PRHS |
| Fare model | base + per-km + night surcharge |
| Shifts | 12–16 h staggered |

Key classes:

- `TaxiConfig` — 80+ configurable parameters.
- `TaxiGeoValidator` — 3-layer spatial validation (land / water / river).
- `TaxiTransportIndex` — station / airport proximity enrichment.
- `DestinationZone` — time-dependent attractiveness scoring.
- `TaxiAgent` — agent with type, shift, familiar zones.
- `TaxiTrip` — fare calculation.
- `DiagnosticWriter` — per-leg diagnostic CSV (B7 instrumentation; opt-in).
- Subpackage: `analysis/`.

### Trajectory Generator (`src/traj/`)

Reconstructs GPS-like waypoint trajectories from trip O-D pairs using A*
routing on the DRM road network.

| Property | Value |
|---|---|
| Entry point | `traj.TrajectoryMain <truck\|taxi> <trips_csv> [args]` |
| Vehicle interface | `VehicleTripRecord` (generic over truck/taxi) |

Key classes (13 in module):

- `VehicleTrajectoryGenerator` — generic A* routing with parallel batching.
- `RoutingCache` — three-layer optimization (see below).
- `DrmNetworkLoader` — multi-prefecture DRM loader.
- `TruckTripRecord` / `TaxiTripRecord` — per-vehicle records.
- `TruckTripCsvParser` / `TaxiTripCsvParser` — CSV input.
- `TruckTrajectoryWriter` / `TaxiTrajectoryWriter` / `TrajectoryOutputWriter` — output.
- `FallbackReason` — enum capturing why a trip fell back from A* (short-trip bypass, projection failure, …).

**RoutingCache 3-layer design:**

1. **Node-snap cache** — `ConcurrentHashMap<"lon4d,lat4d", Node>` (~11 m grid). Eliminates O(N) nearest-node lookups; 50–80 % hit rate.
2. **Route cache** — `ConcurrentHashMap<"srcId|tgtId", Route>`. Eliminates A* for repeated OD pairs; 10–50 % hit rate.
3. **Short-trip bypass** — Trips < 0.3 km or same-node OD skip A* entirely; ~3–5 % of trips.

Thread safety: `putIfAbsent()` (not `computeIfAbsent()`) avoids bucket-lock
serialization during A* computation.

### People-flow ABM (`src/pseudo/`)

Java implementation of the four-step Pseudo-PFLOW pipeline (population
generation, activity transition, location choice, mode choice). 56 classes
across six subpackages:

| Subpackage | Role |
|---|---|
| `pre/` | Preprocessing (census ingest, person generation) |
| `gen/` | Generators: `ActGenerator` (parent), `Commuter`, `NonCommuter`, `Student`, `TripGenerator`, `TrajectoryGenerator` |
| `aggr/` | Aggregation: link volume, mesh volume, sampling |
| `acs/` | Data accessors (census OD, labor, education, mode choice) |
| `res/` | Data models (`Person`, `HouseHold`, `Activity`, `Trip`, `Facility`) |
| `util/` | Helpers |

The classpath-resource config for this module is
`src/main/resources/config.properties`.

## Shared infrastructure

| Path | Role |
|---|---|
| `src/shared/gm-jp/` | Shapefiles used by all ABMs (`polbnda_jpn.shp`, `mainland_jpn.shp`, `roadl_jpn.shp`, `raill_jpn.shp`, `inwatera_jpn.shp`, `riverl_jpn.shp`, `rstatp_jpn.shp`, `airp_jpn.shp`) |
| `src/dcity/` | `ShpLoader`, `Boundaries`, plus `aggr/` and `gtfs/` |
| `src/util/` | `PathResolver` — resolves `${PFLOW_HOME}` tokens at runtime |
| `src/utils/` | Additional helpers |

Other source roots (`src/gtfs/`, `src/network/`, `src/otp/`, `src/particle/`,
`src/pt/`, `src/sim/`) hold legacy / experimental code retained for reference.

## `lib/pflowlib.jar` API

Core routing library used by the trajectory generator and the people-flow ABM:

```java
// Routing
AStar routing = new AStar(new AStarLinkCost(DrmTransport.VEHICLE));
Route route = routing.getRoute(network, lon1, lat1, lon2, lat2);   // with internal snap
Route route = routing.getRoute(network, srcNode, dstNode);          // pre-snapped (faster)
Node node   = routing.getNearestNode(network, lon, lat);            // explicit snap

// Route inspection
route.numNodes(); route.listNodes(); route.listLinks(); route.getCost();

// Timestamp interpolation
Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(nodeList, startDate, endDate);

// Network
Network network = new Network();
network.addLink(drmLink);            // cross-border nodes auto-deduplicate via string ID
network.hasNode(id); network.getNode(id); network.linkCount();

// Geometry
List<ILonLat> points = GeometryUtils.createPointList(lineString);   // WKT → point list
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `lib/pflowlib.jar` | bundled | Core routing (AStar, Network, Route) and geometry |
| `lib/osmpbf-1.3.3.jar` | bundled | OpenStreetMap PBF reader (people-flow ABM imports) |
| JTS | 1.16.0 + 1.13 (vividsolutions) | Geometry, WKT parsing |
| GeoTools | 20.1 | Shapefile loading, CRS, spatial indexing |
| HSQLDB | 2.4.1 | EPSG database (used by `gt-epsg-hsql`) |
| EJML | 0.34 | Matrix ops (pflowlib transitive) |
| **JDK** | **Java 21** | Eclipse Adoptium recommended |

Maven dependency resolution lives in `pom.xml`; the build itself is handled by
`native/build_*` (see [`pipeline.md`](pipeline.md)).
