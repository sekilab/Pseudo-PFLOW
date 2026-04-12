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
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
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
 *   3. Interpolates timestamps via TrajectoryUtils.putTimeStamp()
 *   4. Falls back to 2-point direct trajectory if routing fails
 *   5. Writes waypoint CSVs in parallel batches
 *
 * @param <R> concrete trip record type (TruckTripRecord, TaxiTripRecord, etc.)
 */
public class VehicleTrajectoryGenerator<R extends VehicleTripRecord> {

    // Base date for timestamp anchoring: 2020-10-01 00:00:00 JST (same as PFLOW DAY_OF_DATE)
    private static final long BASE_DATE_SEC = 1601478000L;

    private final Network road;
    private final Network highwayRoad;         // nullable — highway-only network for long trips
    private final Map<Integer, List<R>> vehicleRecords;
    private final TrajectoryOutputWriter<R> writer;
    private final double fallbackSpeedKmh;
    private final RoutingCache cache;          // nullable — null means no caching
    private final RoutingCache highwayCache;   // nullable — cache for highway network
    private final double highwayThresholdKm;   // trips above this use highway network

    // Counters for summary
    private final AtomicInteger routedTrips = new AtomicInteger(0);
    private final AtomicInteger failedTrips = new AtomicInteger(0);
    private final AtomicInteger bypassedTrips = new AtomicInteger(0);
    private final AtomicInteger highwayRoutedTrips = new AtomicInteger(0);
    private final AtomicLong totalWaypoints = new AtomicLong(0);

    /** Single-network constructor (taxis, backward compatible). */
    public VehicleTrajectoryGenerator(Network road,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh) {
        this(road, vehicleRecords, writer, fallbackSpeedKmh, null);
    }

    /** Single-network with cache (taxis). */
    public VehicleTrajectoryGenerator(Network road,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh,
                                       RoutingCache cache) {
        this(road, null, vehicleRecords, writer, fallbackSpeedKmh, cache, null, Double.MAX_VALUE);
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
     */
    public VehicleTrajectoryGenerator(Network road, Network highwayRoad,
                                       Map<Integer, List<R>> vehicleRecords,
                                       TrajectoryOutputWriter<R> writer,
                                       double fallbackSpeedKmh,
                                       RoutingCache cache, RoutingCache highwayCache,
                                       double highwayThresholdKm) {
        this.road = road;
        this.highwayRoad = highwayRoad;
        this.vehicleRecords = vehicleRecords;
        this.writer = writer;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
        this.cache = cache;
        this.highwayCache = highwayCache;
        this.highwayThresholdKm = highwayThresholdKm;
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
            e.printStackTrace();
        }

        // Print summary
        int hwRouted = highwayRoutedTrips.get();
        System.out.printf("[TRAJECTORY] Complete: %,d routed (%,d highway), %,d failed, %,d bypassed, %,d total waypoints%n",
                routedTrips.get(), hwRouted, failedTrips.get(), bypassedTrips.get(), totalWaypoints.get());
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
     * @return number of waypoints written
     */
    private int writeRoute(BufferedWriter bw, R rec, Trip trip, Route route,
                           ILonLat origin, ILonLat dest) throws Exception {
        List<Node> nodes = route.listNodes();
        long startTimeSec = BASE_DATE_SEC + trip.getDepTime();
        long endTimeSec = startTimeSec + (long) route.getCost();

        Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(
                nodes,
                new Date(startTimeSec * 1000),
                new Date(endTimeSec * 1000)
        );

        List<Link> links = route.listLinks();

        for (int i = 0; i < nodes.size(); i++) {
            ILonLat node = nodes.get(i);
            Date date = timeMap.get(node);

            // Override first/last with exact trip coordinates
            double lon = node.getLon();
            double lat = node.getLat();
            if (i == 0) {
                lon = origin.getLon();
                lat = origin.getLat();
            } else if (i == nodes.size() - 1) {
                lon = dest.getLon();
                lat = dest.getLat();
            }

            String linkId = (i > 0 && i <= links.size()) ? links.get(i - 1).getLinkID() : "";
            long unixMs = date != null ? date.getTime() : 0;

            writer.writeWaypoint(bw, rec, unixMs, lon, lat, linkId);
        }
        return nodes.size();
    }

    /**
     * Write a 2-point direct trajectory (fallback when routing fails or is bypassed).
     */
    private void writeDirect(BufferedWriter bw, R rec, Trip trip,
                             ILonLat origin, ILonLat dest) throws Exception {
        long startTimeSec = BASE_DATE_SEC + trip.getDepTime();
        double distKm = rec.getDistanceKm() > 0 ? rec.getDistanceKm() : 10.0;
        long travelSec = (long) (distKm / fallbackSpeedKmh * 3600);
        long endTimeSec = startTimeSec + travelSec;

        writer.writeWaypoint(bw, rec, startTimeSec * 1000,
                origin.getLon(), origin.getLat(), "DIRECT");
        writer.writeWaypoint(bw, rec, endTimeSec * 1000,
                dest.getLon(), dest.getLat(), "DIRECT");
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
            int localFailed = 0;
            int localBypassed = 0;
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
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localFailed++;
                                continue;
                            }

                            if (activeCache.shouldBypass(srcNode, dstNode, distKm)) {
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
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localFailed++;
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
                                writeDirect(bw, rec, trip, origin, dest);
                                localWaypoints += 2;
                                localFailed++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.printf("[TRAJECTORY] Error in batch %d: %s%n", batchId, e.getMessage());
                e.printStackTrace();
            }

            routedTrips.addAndGet(localRouted);
            failedTrips.addAndGet(localFailed);
            bypassedTrips.addAndGet(localBypassed);
            highwayRoutedTrips.addAndGet(localHwRouted);
            totalWaypoints.addAndGet(localWaypoints);
            System.out.printf("[TRAJECTORY] Batch %d complete: %,d routed (%,d highway), %,d failed, %,d bypassed%n",
                    batchId, localRouted, localHwRouted, localFailed, localBypassed);
            return 0;
        }
    }
}
