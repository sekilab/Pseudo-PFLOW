package traj;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.logic.AStar;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.AStarLinkCost;
import jp.ac.ut.csis.pflow.routing4.logic.transport.DrmTransport;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import pseudo.res.Person;
import pseudo.res.Trip;

/**
 * Generic A* trajectory generator for any road vehicle type.
 * <p>
 * Generified from {@code truck.traj.TruckTrajectoryGenerator}. The routing engine
 * is identical for all vehicle types — vehicle-specific behavior is delegated to:
 * <ul>
 *   <li>{@link TrajectoryOutputWriter} for CSV column formatting</li>
 *   <li>{@link VehicleTripRecord} for trip metadata access</li>
 * </ul>
 * <p>
 * Pipeline:
 *   1. Receives pre-parsed Person+Trip objects and raw trip records
 *   2. Routes each trip via A* on DRM road network
 *   3. Interpolates timestamps proportional to cumulative haversine distance
 *      between waypoints (both lightweight and full-geometry modes)
 *   4. Falls back to 2-point direct trajectory if routing fails
 *   5. Writes waypoint CSVs in parallel batches
 * <p>
 * <b>Deferred architectural decision (T7):</b> A* cost uses a single
 * {@code DrmTransport.VEHICLE} link-cost function for trucks AND taxis. Per-
 * vehicle cost semantics — toll avoidance, truck weight restrictions, taxi
 * main-road bias — are not modelled. Revisit when routing fidelity becomes a
 * research priority; until then, trajectories are best interpreted as generic
 * motor-vehicle paths rather than mode-specific ones.
 *
 * @param <R> concrete trip record type (TruckTripRecord, TaxiTripRecord, etc.)
 */
public class VehicleTrajectoryGenerator<R extends VehicleTripRecord> {

    /**
     * Base epoch for trajectory timestamps: 2020-10-01 00:00:00 JST (UTC+9).
     * {@code Trip.depTime} is an offset in seconds from this base, so it must
     * lie in {@code [0, 7*86400]} (one week). Out-of-range depTimes would
     * silently shift output timestamps by days/years — see
     * {@link #assertDepTimeInRange}.
     */
    private static final long BASE_DATE_SEC = 1601478000L;

    /** Upper bound on trip depTime (seconds) — one week from BASE_DATE_SEC. */
    private static final long MAX_DEP_TIME_SEC = 7L * 86_400L;

    /**
     * Sanity check that a trip's depTime fits the BASE_DATE_SEC reference frame.
     * Logs a single warning per range violation (not thrown — we continue with
     * the possibly-skewed timestamp rather than drop trips).
     */
    private void assertDepTimeInRange(long depTimeSec, int vehicleId, long tripId) {
        if (depTimeSec < 0 || depTimeSec > MAX_DEP_TIME_SEC) {
            if (depTimeWarningsLogged.incrementAndGet() <= 5) {
                System.err.printf("[TRAJECTORY] depTime out of range: vehicle=%d trip=%d depTime=%ds (expected 0..%d)%n",
                        vehicleId, tripId, depTimeSec, MAX_DEP_TIME_SEC);
            }
        }
    }

    /** Caps depTime range-check warnings at 5 per run to avoid log flooding. */
    private final AtomicInteger depTimeWarningsLogged = new AtomicInteger(0);

    private final Network road;
    private final Network highwayRoad;         // nullable — highway-only network for long trips
    private final Map<Integer, List<R>> vehicleRecords;
    private final TrajectoryOutputWriter<R> writer;
    private final double fallbackSpeedKmh;
    private final RoutingCache cache;          // nullable — null means no caching
    private final RoutingCache highwayCache;   // nullable — cache for highway network
    private final double highwayThresholdKm;   // trips above this use highway network
    private final boolean includeGeometry;     // true = emit intermediate road shape points

    // Counters for summary — failures split by FallbackReason so dashboards can
    // distinguish unreachable endpoints from disconnected routes.
    private final AtomicInteger routedTrips = new AtomicInteger(0);
    private final AtomicInteger noSnapFailures = new AtomicInteger(0);   // FallbackReason.NO_SNAP
    private final AtomicInteger noRouteFailures = new AtomicInteger(0);  // FallbackReason.NO_ROUTE
    private final AtomicInteger bypassedTrips = new AtomicInteger(0);    // FallbackReason.SHORT_TRIP_BYPASS
    private final AtomicInteger highwayRoutedTrips = new AtomicInteger(0);
    private final AtomicLong totalWaypoints = new AtomicLong(0);

    /** Single-network constructor (taxis, backward compatible). */
    public VehicleTrajectoryGenerator(Network road,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh) {
        this(road, vehicleRecords, writer, fallbackSpeedKmh, null, false);
    }

    /** Single-network with cache (taxis). */
    public VehicleTrajectoryGenerator(Network road,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh,
                                       RoutingCache cache) {
        this(road, null, vehicleRecords, writer, fallbackSpeedKmh, cache, null, Double.MAX_VALUE, false);
    }

    /** Single-network with cache and geometry mode (taxis). */
    public VehicleTrajectoryGenerator(Network road,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh,
                                       RoutingCache cache,
                                       boolean includeGeometry) {
        this(road, null, vehicleRecords, writer, fallbackSpeedKmh, cache, null, Double.MAX_VALUE, includeGeometry);
    }

    /**
     * Dual-network constructor for trucks: full network for short trips,
     * highway-only network for long-haul trips.
     *
     * @param road               full DRM road network (rdclass <= 9)
     * @param highwayRoad        highway-only network (rdclass <= 5), or null
     * @param vehicleRecords     trip records grouped by vehicle ID
     * @param writer             vehicle-specific output formatter
     * @param fallbackSpeedKmh   speed for direct fallback (truck=30)
     * @param cache              routing cache for full network
     * @param highwayCache       routing cache for highway network, or null
     * @param highwayThresholdKm trips above this use highway network (e.g. 200km)
     * @param includeGeometry    true = emit intermediate road shape points in output
     */
    public VehicleTrajectoryGenerator(Network road, Network highwayRoad,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh,
                                       RoutingCache cache, RoutingCache highwayCache,
                                       double highwayThresholdKm,
                                       boolean includeGeometry) {
        this.road = road;
        this.highwayRoad = highwayRoad;
        this.vehicleRecords = vehicleRecords;
        this.writer = writer;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
        this.cache = cache;
        this.highwayCache = highwayCache;
        this.highwayThresholdKm = highwayThresholdKm;
        this.includeGeometry = includeGeometry;
    }

    /**
     * Route all vehicle trips in parallel and write trajectory CSVs.
     */
    public void generate(List<Person> persons, String outputDir) {
        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService es = Executors.newFixedThreadPool(numThreads);
        System.out.printf("[TRAJECTORY] Starting routing with %d threads for %,d vehicles%n",
                numThreads, persons.size());

        int taskCount = numThreads * 2;
        int stepSize = Math.max(1, persons.size() / taskCount + (persons.size() % taskCount != 0 ? 1 : 0));

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < persons.size(); i += stepSize) {
            int end = Math.min(persons.size(), i + stepSize);
            List<Person> batch = persons.subList(i, end);
            int batchId = i / stepSize;
            String outFile = String.format("%s/trajectory_%04d.csv", outputDir, batchId);
            futures.add(es.submit(new RoutingTask(batch, outFile, batchId)));
        }

        es.shutdown();
        try {
            es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[TRAJ] Thread interrupted: " + e.getMessage());
        }

        // Print summary
        int hwRouted = highwayRoutedTrips.get();
        int noSnap = noSnapFailures.get();
        int noRoute = noRouteFailures.get();
        System.out.printf("[TRAJECTORY] Complete: %,d routed (%,d highway), %,d failed (%,d no-snap + %,d no-route), %,d bypassed, %,d total waypoints%n",
                routedTrips.get(), hwRouted,
                noSnap + noRoute, noSnap, noRoute,
                bypassedTrips.get(), totalWaypoints.get());
        if (cache != null) {
            System.out.println("[CACHE-FULL] " + cache.getStats());
        }
        if (highwayCache != null) {
            System.out.println("[CACHE-HWY]  " + highwayCache.getStats());
        }
        System.out.printf("[TRAJECTORY] Output: %s (%d files)%n", outputDir, futures.size());
    }

    /**
     * Write a routed trajectory: interpolate timestamps along route nodes.
     * <p>
     * In lightweight mode, emits one waypoint per network node (~150m apart).
     * In full geometry mode, emits intermediate road shape points from each link's
     * stored WKT geometry, producing road-following curves (~10-30m apart).
     * <p>
     * Both modes interpolate timestamps proportional to cumulative haversine
     * distance. The pflowlib {@code TrajectoryUtils.putTimeStamp} helper,
     * previously used in lightweight mode, produces node-index-uniform
     * timestamps which yield unrealistic velocity profiles on routes mixing
     * long expressway links with short local links.
     *
     * @return number of waypoints written
     */
    private int writeRoute(BufferedWriter bw, R rec, Trip trip, Route route,
                           ILonLat origin, ILonLat dest) throws Exception {
        List<Node> nodes = route.listNodes();
        List<Link> links = route.listLinks();
        assertDepTimeInRange(trip.getDepTime(), rec.getVehicleId(), rec.getTripId());
        long startTimeSec = BASE_DATE_SEC + trip.getDepTime();
        long endTimeSec = startTimeSec + (long) route.getCost();

        if (includeGeometry) {
            return writeRouteWithGeometry(bw, rec, links, origin, dest, startTimeSec, endTimeSec);
        }

        // Lightweight mode: one waypoint per network node, timestamped by
        // cumulative haversine distance (mirrors writeRouteWithGeometry).
        int n = nodes.size();
        double[] lons = new double[n];
        double[] lats = new double[n];
        double[] cumDists = new double[n];
        double cumDist = 0.0;
        for (int i = 0; i < n; i++) {
            ILonLat node = nodes.get(i);
            if (i == 0) {
                lons[0] = origin.getLon();
                lats[0] = origin.getLat();
            } else if (i == n - 1) {
                lons[i] = dest.getLon();
                lats[i] = dest.getLat();
            } else {
                lons[i] = node.getLon();
                lats[i] = node.getLat();
            }
            if (i > 0) {
                cumDist += haversineM(lats[i - 1], lons[i - 1], lats[i], lons[i]);
            }
            cumDists[i] = cumDist;
        }

        double totalDist = cumDist > 0 ? cumDist : 1.0;
        long durationMs = (endTimeSec - startTimeSec) * 1000;
        long startMs = startTimeSec * 1000;

        for (int i = 0; i < n; i++) {
            String linkId = (i > 0 && i <= links.size()) ? links.get(i - 1).getLinkID() : "";
            long unixMs = startMs + (long)(durationMs * cumDists[i] / totalDist);
            writer.writeWaypoint(bw, rec, unixMs, lons[i], lats[i], linkId, false);
        }
        return n;
    }

    /**
     * Full geometry mode: emit intermediate road shape points from each link.
     * Builds a flat point list from all link geometries, then interpolates
     * timestamps proportional to cumulative distance.
     */
    private int writeRouteWithGeometry(BufferedWriter bw, R rec, List<Link> links,
                                        ILonLat origin, ILonLat dest,
                                        long startTimeSec, long endTimeSec) throws Exception {
        // Build flat list of all waypoints with their link IDs and cumulative distances
        List<double[]> points = new ArrayList<>();   // [lon, lat, cumDist]
        List<String> linkIds = new ArrayList<>();
        double cumDist = 0.0;

        // First point: exact origin
        points.add(new double[]{origin.getLon(), origin.getLat(), 0.0});
        linkIds.add("");

        for (Link link : links) {
            List<ILonLat> geom = link.getLineString();
            String lid = link.getLinkID();

            if (geom != null && geom.size() > 1) {
                // Emit intermediate points (skip first — it's the previous node/point)
                for (int j = 1; j < geom.size(); j++) {
                    ILonLat prev = geom.get(j - 1);
                    ILonLat curr = geom.get(j);
                    cumDist += haversineM(prev.getLat(), prev.getLon(), curr.getLat(), curr.getLon());
                    points.add(new double[]{curr.getLon(), curr.getLat(), cumDist});
                    linkIds.add(lid);
                }
            } else {
                // No geometry on this link — use head node position
                Node head = link.getHeadNode();
                double[] prev = points.get(points.size() - 1);
                cumDist += haversineM(prev[1], prev[0], head.getLat(), head.getLon());
                points.add(new double[]{head.getLon(), head.getLat(), cumDist});
                linkIds.add(lid);
            }
        }

        // Override last point with exact destination, and recompute the final
        // segment's cumulative distance to match — otherwise cumDist refers to
        // the original last-node position and the endpoint appears both
        // spatially offset (visible kinks on QGIS) and temporally stretched.
        if (points.size() >= 2) {
            double[] prev = points.get(points.size() - 2);
            double[] last = points.get(points.size() - 1);
            double origSegmentDist = last[2] - prev[2];
            last[0] = dest.getLon();
            last[1] = dest.getLat();
            double newSegmentDist = haversineM(prev[1], prev[0], last[1], last[0]);
            last[2] = prev[2] + newSegmentDist;
            cumDist = cumDist - origSegmentDist + newSegmentDist;
        } else if (points.size() == 1) {
            // Degenerate route (origin == dest): just override coords.
            double[] last = points.get(0);
            last[0] = dest.getLon();
            last[1] = dest.getLat();
        }

        // Interpolate timestamps proportional to cumulative distance
        double totalDist = cumDist > 0 ? cumDist : 1.0;
        long durationMs = (endTimeSec - startTimeSec) * 1000;
        long startMs = startTimeSec * 1000;

        for (int i = 0; i < points.size(); i++) {
            double[] pt = points.get(i);
            long unixMs = startMs + (long)(durationMs * pt[2] / totalDist);
            writer.writeWaypoint(bw, rec, unixMs, pt[0], pt[1], linkIds.get(i), false);
        }
        return points.size();
    }

    /** Fast haversine distance in meters. */
    private static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Write a 2-point direct trajectory (fallback when routing fails or is bypassed).
     * Both waypoints are flagged {@code is_fallback=1} so downstream consumers can
     * filter fallback trips without parsing link_id sentinels.
     */
    private void writeDirect(BufferedWriter bw, R rec, Trip trip,
                             ILonLat origin, ILonLat dest) throws Exception {
        assertDepTimeInRange(trip.getDepTime(), rec.getVehicleId(), rec.getTripId());
        long startTimeSec = BASE_DATE_SEC + trip.getDepTime();
        double distKm = rec.getDistanceKm() > 0 ? rec.getDistanceKm() : 10.0;
        long travelSec = (long) (distKm / fallbackSpeedKmh * 3600);
        long endTimeSec = startTimeSec + travelSec;

        writer.writeWaypoint(bw, rec, startTimeSec * 1000,
                origin.getLon(), origin.getLat(), "DIRECT", true);
        writer.writeWaypoint(bw, rec, endTimeSec * 1000,
                dest.getLon(), dest.getLat(), "DIRECT", true);
    }

    /**
     * Callable task that routes a batch of vehicles and writes trajectory CSV.
     */
    private class RoutingTask implements Callable<Integer> {
        private final List<Person> persons;
        private final String outputFile;
        private final int batchId;

        RoutingTask(List<Person> persons, String outputFile, int batchId) {
            this.persons = persons;
            this.outputFile = outputFile;
            this.batchId = batchId;
        }

        @Override
        public Integer call() {
            AStarLinkCost linkCost = new AStarLinkCost(DrmTransport.VEHICLE);
            AStar routing = new AStar(linkCost);
            // Separate A* instance for highway network (thread-local, stateful)
            AStar hwRouting = (highwayRoad != null) ? new AStar(new AStarLinkCost(DrmTransport.VEHICLE)) : null;
            int localRouted = 0;
            int localNoSnap = 0;      // FallbackReason.NO_SNAP
            int localNoRoute = 0;     // FallbackReason.NO_ROUTE
            int localBypassed = 0;    // FallbackReason.SHORT_TRIP_BYPASS
            int localHwRouted = 0;
            long localWaypoints = 0;

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile), 65536)) {
                bw.write(writer.getCsvHeader());
                bw.newLine();

                for (Person person : persons) {
                    int vehicleId = (int) person.getId();
                    List<R> records = vehicleRecords.get(vehicleId);
                    List<Trip> trips = person.listTrips();

                    for (int t = 0; t < trips.size(); t++) {
                        Trip trip = trips.get(t);
                        R rec = (records != null && t < records.size()) ? records.get(t) : null;

                        if (rec == null) continue;

                        ILonLat origin = trip.getOrigin();
                        ILonLat dest = trip.getDestination();
                        double distKm = rec.getDistanceKm();

                        // Pick network based on trip distance
                        boolean useHighway = (highwayRoad != null && highwayCache != null
                                && distKm > highwayThresholdKm);
                        RoutingCache activeCache = useHighway ? highwayCache : cache;
                        AStar activeRouting = useHighway ? hwRouting : routing;
                        Network activeRoad = useHighway ? highwayRoad : road;

                        if (activeCache != null) {
                            Node srcNode = activeCache.snapToNode(activeRouting, activeRoad,
                                    origin.getLon(), origin.getLat());
                            Node dstNode = activeCache.snapToNode(activeRouting, activeRoad,
                                    dest.getLon(), dest.getLat());

                            if (srcNode == null || dstNode == null) {
                                // FallbackReason.NO_SNAP: endpoint outside network coverage.
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localNoSnap++;
                                continue;
                            }

                            if (activeCache.shouldBypass(srcNode, dstNode, distKm)) {
                                // FallbackReason.SHORT_TRIP_BYPASS: intentional — trip below threshold.
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localBypassed++;
                                continue;
                            }

                            Route route = activeCache.getRoute(activeRouting, activeRoad, srcNode, dstNode);
                            if (route != null && route.numNodes() > 0) {
                                localWaypoints += writeRoute(bw, rec, trip, route, origin, dest);
                                localRouted++;
                                if (useHighway) localHwRouted++;
                            } else {
                                // FallbackReason.NO_ROUTE: A* returned null/empty on cached path.
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localNoRoute++;
                            }
                        } else {
                            // Non-cached path (backward compatible)
                            Route route = routing.getRoute(road,
                                    origin.getLon(), origin.getLat(),
                                    dest.getLon(), dest.getLat());

                            if (route != null && route.numNodes() > 0) {
                                localWaypoints += writeRoute(bw, rec, trip, route, origin, dest);
                                localRouted++;
                            } else {
                                // FallbackReason.NO_ROUTE: includes both nearest-node-miss and
                                // disconnected-path cases — the uncached path doesn't distinguish.
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localNoRoute++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.printf("[TRAJECTORY] Error in batch %d: %s%n", batchId, e.getMessage());
                e.printStackTrace();
            }

            routedTrips.addAndGet(localRouted);
            noSnapFailures.addAndGet(localNoSnap);
            noRouteFailures.addAndGet(localNoRoute);
            bypassedTrips.addAndGet(localBypassed);
            highwayRoutedTrips.addAndGet(localHwRouted);
            totalWaypoints.addAndGet(localWaypoints);
            System.out.printf("[TRAJECTORY] Batch %d complete: %,d routed (%,d highway), %,d failed (%,d no-snap + %,d no-route), %,d bypassed%n",
                    batchId, localRouted, localHwRouted,
                    localNoSnap + localNoRoute, localNoSnap, localNoRoute,
                    localBypassed);
            return 0;
        }
    }
}
