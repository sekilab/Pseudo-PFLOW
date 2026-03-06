package truck.traj;

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
 * Generates road-network trajectories for truck trips using A* routing.
 * <p>
 * Pipeline:
 *   1. TruckTripConverter reads trips_pseudo_pflow.csv -> List&lt;Person&gt; with Trip objects
 *   2. DrmNetworkLoader loads multi-prefecture DRM into merged Network (with road class filter)
 *   3. This class routes each trip via A*, assigns timestamps, writes waypoints
 * <p>
 * Usage:
 *   java truck.traj.TruckTrajectoryGenerator &lt;trips_csv&gt; [network_dir] [output_dir] [pref_codes] [maxRoadClass]
 * <p>
 * Example:
 *   java truck.traj.TruckTrajectoryGenerator \
 *     data/output/truck/run_20260305_182719/trips_pseudo_pflow.csv \
 *     N:/PFLOW/data/processing/network \
 *     N:/PFLOW/output/truck_trips \
 *     8,9,10,11,12,13,14 8
 */
public class TruckTrajectoryGenerator {

    // Base date for timestamp anchoring: 2020-10-01 00:00:00 JST (same as PFLOW DAY_OF_DATE)
    private static final long BASE_DATE_SEC = 1601478000L;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_HEADER = "truck_id,trip_id,unix_time_ms,datetime,lon,lat,transport_mode,purpose,goods_type,vehicle_size,link_id";

    // Default Kanto prefectures (covers MFS01-66)
    private static final int[] DEFAULT_PREFS = {8, 9, 10, 11, 12, 13, 14};

    private final Network road;
    private final Map<Integer, List<TruckTripRecord>> truckRecords;

    // Counters for summary
    private final AtomicInteger routedTrips = new AtomicInteger(0);
    private final AtomicInteger failedTrips = new AtomicInteger(0);
    private final AtomicLong totalWaypoints = new AtomicLong(0);

    public TruckTrajectoryGenerator(Network road, Map<Integer, List<TruckTripRecord>> truckRecords) {
        this.road = road;
        this.truckRecords = truckRecords;
    }

    /**
     * Route all truck trips in parallel and write trajectory CSVs.
     */
    public void generate(List<Person> persons, String outputDir) {
        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService es = Executors.newFixedThreadPool(numThreads);
        System.out.printf("[TRAJECTORY] Starting routing with %d threads for %,d trucks%n",
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
        System.out.printf("[TRAJECTORY] Complete: %,d routed, %,d failed, %,d total waypoints%n",
                routedTrips.get(), failedTrips.get(), totalWaypoints.get());
        System.out.printf("[TRAJECTORY] Output: %s (%d files)%n", outputDir, futures.size());
    }

    /**
     * Callable task that routes a batch of trucks and writes trajectory CSV.
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
            int localRouted = 0;
            int localFailed = 0;
            long localWaypoints = 0;

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile), 65536)) {
                bw.write(CSV_HEADER);
                bw.newLine();

                for (Person person : persons) {
                    int truckId = (int) person.getId();
                    List<TruckTripRecord> records = truckRecords.get(truckId);
                    List<Trip> trips = person.listTrips();

                    // records and trips are in same order (both sorted by depTime)
                    for (int t = 0; t < trips.size(); t++) {
                        Trip trip = trips.get(t);
                        TruckTripRecord rec = (records != null && t < records.size()) ? records.get(t) : null;

                        String goodsType = rec != null ? rec.goodsType : "";
                        String vehicleSize = rec != null ? rec.vehicleSize : "";
                        int purpose = rec != null ? (rec.cargoLoaded ? 1 : 0) : 0;
                        long tripId = rec != null ? rec.tripId : 0;

                        ILonLat origin = trip.getOrigin();
                        ILonLat dest = trip.getDestination();

                        // Route via A*
                        Route route = routing.getRoute(road,
                                origin.getLon(), origin.getLat(),
                                dest.getLon(), dest.getLat());

                        if (route != null && route.numNodes() > 0) {
                            // Successful route — interpolate timestamps
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

                                writeWaypoint(bw, truckId, tripId, unixMs, lon, lat,
                                        9, purpose, goodsType, vehicleSize, linkId);
                                localWaypoints++;
                            }
                            localRouted++;
                        } else {
                            // Failed route — write 2-point direct trajectory
                            long startTimeSec = BASE_DATE_SEC + trip.getDepTime();
                            // Estimate travel time from distance (avg 30 km/h for trucks)
                            double distKm = rec != null ? rec.distanceKm : 30.0;
                            long travelSec = (long) (distKm / 30.0 * 3600);
                            long endTimeSec = startTimeSec + travelSec;

                            writeWaypoint(bw, truckId, tripId, startTimeSec * 1000,
                                    origin.getLon(), origin.getLat(),
                                    9, purpose, goodsType, vehicleSize, "DIRECT");
                            writeWaypoint(bw, truckId, tripId, endTimeSec * 1000,
                                    dest.getLon(), dest.getLat(),
                                    9, purpose, goodsType, vehicleSize, "DIRECT");
                            localWaypoints += 2;
                            localFailed++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.printf("[TRAJECTORY] Error in batch %d: %s%n", batchId, e.getMessage());
                e.printStackTrace();
            }

            routedTrips.addAndGet(localRouted);
            failedTrips.addAndGet(localFailed);
            totalWaypoints.addAndGet(localWaypoints);
            System.out.printf("[TRAJECTORY] Batch %d complete: %,d routed, %,d failed%n",
                    batchId, localRouted, localFailed);
            return 0;
        }

        private void writeWaypoint(BufferedWriter bw, int truckId, long tripId,
                                   long unixMs, double lon, double lat,
                                   int transportMode, int purpose,
                                   String goodsType, String vehicleSize, String linkId)
                throws java.io.IOException {
            String datetime;
            synchronized (DATE_FORMAT) {
                datetime = DATE_FORMAT.format(new Date(unixMs));
            }
            bw.write(String.format("%d,%d,%d,%s,%.6f,%.6f,%d,%d,%s,%s,%s",
                    truckId, tripId, unixMs, datetime, lon, lat,
                    transportMode, purpose, goodsType, vehicleSize, linkId));
            bw.newLine();
        }
    }

    // ── Main entry point ────────────────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: TruckTrajectoryGenerator <trips_csv> [network_dir] [output_dir] [pref_codes] [maxRoadClass]");
            System.out.println("  trips_csv    : path to trips_pseudo_pflow.csv");
            System.out.println("  network_dir  : directory with drm_XX.tsv files (default: N:/PFLOW/data/processing/network)");
            System.out.println("  output_dir   : trajectory output directory (default: N:/PFLOW/output/truck_trips)");
            System.out.println("  pref_codes   : comma-separated prefecture codes (default: 8,9,10,11,12,13,14)");
            System.out.println("  maxRoadClass : max road class to keep, 0=all (default: 8, skips minor roads)");
            return;
        }

        long startTime = System.currentTimeMillis();

        String tripsCsv = args[0];
        String networkDir = args.length > 1 ? args[1] : "N:/PFLOW/data/processing/network";
        String outputDir = args.length > 2 ? args[2] : "N:/PFLOW/output/truck_trips";
        int[] prefCodes = DEFAULT_PREFS;
        if (args.length > 3) {
            String[] parts = args[3].split(",");
            prefCodes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                prefCodes[i] = Integer.parseInt(parts[i].trim());
            }
        }
        int maxRoadClass = args.length > 4 ? Integer.parseInt(args[4].trim()) : 8;

        System.out.printf("[MAIN] Trips CSV: %s%n", tripsCsv);
        System.out.printf("[MAIN] Network dir: %s%n", networkDir);
        System.out.printf("[MAIN] Output dir: %s%n", outputDir);
        System.out.printf("[MAIN] Prefectures: %s%n", Arrays.toString(prefCodes));
        System.out.printf("[MAIN] Max road class: %d (%s)%n", maxRoadClass,
                maxRoadClass > 0 ? "filtering minor roads" : "all roads");

        // Step 1: Load truck trips
        System.out.println("\n[STEP 1] Loading truck trips...");
        long t1 = System.currentTimeMillis();
        List<Person> persons = TruckTripConverter.loadTruckTrips(tripsCsv);
        Map<Integer, List<TruckTripRecord>> records = TruckTripConverter.loadTruckTripRecords(tripsCsv);
        System.out.printf("[STEP 1] Done in %.1fs%n", (System.currentTimeMillis() - t1) / 1000.0);

        if (persons.isEmpty()) {
            System.err.println("[MAIN] No trips loaded, exiting.");
            return;
        }

        // Step 2: Load DRM road network
        System.out.println("\n[STEP 2] Loading DRM road network...");
        long t2 = System.currentTimeMillis();
        Network road = DrmNetworkLoader.loadPrefectures(networkDir, prefCodes, maxRoadClass);
        System.out.printf("[STEP 2] Done in %.1fs — %,d links loaded%n",
                (System.currentTimeMillis() - t2) / 1000.0, road.linkCount());

        if (road.linkCount() == 0) {
            System.err.println("[MAIN] No road network loaded. Check DRM files in: " + networkDir);
            System.err.println("[MAIN] Expected files: drm_08.tsv, drm_09.tsv, ... drm_14.tsv");
            return;
        }

        // Step 3: Generate trajectories
        System.out.println("\n[STEP 3] Generating trajectories...");
        long t3 = System.currentTimeMillis();
        TruckTrajectoryGenerator generator = new TruckTrajectoryGenerator(road, records);
        generator.generate(persons, outputDir);
        System.out.printf("[STEP 3] Done in %.1fs%n", (System.currentTimeMillis() - t3) / 1000.0);

        // Summary
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("\n[COMPLETE] Total time: %.1fs (%d:%02d)%n",
                elapsed / 1000.0, elapsed / 60000, (elapsed / 1000) % 60);
    }
}
