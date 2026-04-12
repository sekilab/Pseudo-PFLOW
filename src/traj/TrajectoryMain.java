package traj;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import pseudo.res.Person;
import util.PathResolver;

/**
 * Unified CLI entry point for trajectory generation across all vehicle types.
 * <p>
 * Usage:
 *   java traj.TrajectoryMain &lt;vehicle_type&gt; &lt;trips_csv&gt; [network_dir] [output_dir] [pref_codes] [maxRoadClass]
 * <p>
 * Vehicle types:
 *   truck  - Truck ABM trajectories (transport_mode=9)
 *   taxi   - Taxi ABM trajectories (transport_mode=8)
 * <p>
 * Examples:
 *   java traj.TrajectoryMain truck $PFLOW_HOME/output/trips/truck/run_YYYYMMDD_HHMMSS/trips_pseudo_pflow.csv
 *   java traj.TrajectoryMain taxi  $PFLOW_HOME/output/trips/taxi/tokyo/run_YYYYMMDD_HHMMSS/trips_pseudo_pflow.csv
 *   java traj.TrajectoryMain truck trips.csv $PFLOW_HOME/data/processing/network $PFLOW_HOME/output/trajectory/truck 8,9,10,11,12,13,14 8
 */
public class TrajectoryMain {

    // Default: Tohoku + Kanto + Chubu + Kansai + Chugoku (prefectures 02-35)
    // Covers all expanded zones. Hokkaido (01), Shikoku/Kyushu (36-47) → DIRECT fallback.
    private static final int[] DEFAULT_PREFS = {
         2,  3,  4,  5,  6,  7,        // Tohoku
         8,  9, 10, 11, 12, 13, 14,    // Kanto
        15, 16, 17, 18, 19, 20, 21, 22, 23,  // Chubu
        24, 25, 26, 27, 28, 29, 30,    // Kansai
        31, 32, 33, 34, 35             // Chugoku
    };

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            return;
        }

        long startTime = System.currentTimeMillis();

        String vehicleType = args[0].toLowerCase();
        String tripsCsv = args[1];
        String networkDir = args.length > 2 ? args[2] : PathResolver.getPflowHome() + "/data/processing/network";
        String outputDir = args.length > 3 ? args[3] : null;  // default set per vehicle type
        int[] prefCodes = DEFAULT_PREFS;
        if (args.length > 4) {
            String[] parts = args[4].split(",");
            prefCodes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                prefCodes[i] = Integer.parseInt(parts[i].trim());
            }
        }
        int maxRoadClass = args.length > 5 ? Integer.parseInt(args[5].trim()) : 9;

        // Set default output dir based on vehicle type if not specified
        if (outputDir == null) {
            switch (vehicleType) {
                case "truck": outputDir = PathResolver.getPflowHome() + "/output/trajectory/truck"; break;
                case "taxi":  outputDir = PathResolver.getPflowHome() + "/output/trajectory/taxi"; break;
                default:      outputDir = PathResolver.getPflowHome() + "/output/trajectory/" + vehicleType; break;
            }
        }

        System.out.println("================================================================");
        System.out.printf("  TRAJECTORY GENERATOR — %s%n", vehicleType.toUpperCase());
        System.out.println("================================================================");
        System.out.printf("[MAIN] Vehicle type: %s%n", vehicleType);
        System.out.printf("[MAIN] Trips CSV: %s%n", tripsCsv);
        System.out.printf("[MAIN] Network dir: %s%n", networkDir);
        System.out.printf("[MAIN] Output dir: %s%n", outputDir);
        System.out.printf("[MAIN] Prefectures: %s%n", Arrays.toString(prefCodes));
        System.out.printf("[MAIN] Max road class: %d (%s)%n", maxRoadClass,
                maxRoadClass > 0 ? "filtering minor roads" : "all roads");

        // Dispatch to vehicle-specific pipeline
        switch (vehicleType) {
            case "truck":
                runTruck(tripsCsv, networkDir, outputDir, prefCodes, maxRoadClass);
                break;
            case "taxi":
                runTaxi(tripsCsv, networkDir, outputDir, prefCodes, maxRoadClass);
                break;
            default:
                System.err.printf("[MAIN] Unknown vehicle type: '%s'. Supported: truck, taxi%n", vehicleType);
                return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("\n[COMPLETE] Total time: %.1fs (%d:%02d)%n",
                elapsed / 1000.0, elapsed / 60000, (elapsed / 1000) % 60);
    }

    /** Distance threshold: trips above this use the highway-only network for A* routing. */
    private static final double HIGHWAY_THRESHOLD_KM = 500.0;
    /** Road class for the highway network (1=expressway .. 7=municipal main roads). */
    private static final int HIGHWAY_ROAD_CLASS = 7;

    private static void runTruck(String tripsCsv, String networkDir, String outputDir,
                                  int[] prefCodes, int maxRoadClass) {
        // Step 1: Parse trips (single read)
        System.out.println("\n[STEP 1] Loading truck trips...");
        long t1 = System.currentTimeMillis();
        TruckTripCsvParser parser = new TruckTripCsvParser();
        Map<Integer, List<TruckTripRecord>> records = parser.parseTripRecords(tripsCsv);
        List<Person> persons = parser.convertToPersons(records);
        System.out.printf("[STEP 1] Done in %.1fs%n", (System.currentTimeMillis() - t1) / 1000.0);

        if (persons.isEmpty()) {
            System.err.println("[MAIN] No trips loaded, exiting.");
            return;
        }

        // Step 2a: Load full DRM road network (rdclass ≤ 9, for short/medium trips)
        Network road = loadNetwork(networkDir, prefCodes, maxRoadClass);
        if (road == null) return;

        // Step 2b: Load highway-only network (rdclass ≤ 5, for long-haul trips > 200km)
        System.out.println("\n[STEP 2b] Loading highway-only network (rdclass <= " + HIGHWAY_ROAD_CLASS + ")...");
        long t2b = System.currentTimeMillis();
        Network highway = DrmNetworkLoader.loadPrefectures(networkDir, prefCodes, HIGHWAY_ROAD_CLASS);
        System.out.printf("[STEP 2b] Done in %.1fs — %,d highway links%n",
                (System.currentTimeMillis() - t2b) / 1000.0, highway.linkCount());

        // Step 3: Build KD-trees for both networks
        NodeKDTree.BuildResult kdFull = buildKDTree(road, "full");
        NodeKDTree.BuildResult kdHighway = buildKDTree(highway, "highway");

        // Step 4: Generate trajectories with dual-network routing
        System.out.println("\n[STEP 4] Generating truck trajectories...");
        System.out.printf("[STEP 4] Routing: <=%.0fkm on full network, %.0f-1200km on highway, >1200km direct%n",
                HIGHWAY_THRESHOLD_KM, HIGHWAY_THRESHOLD_KM);
        long t3 = System.currentTimeMillis();
        RoutingCache fullCache = new RoutingCache(0.3, kdFull.tree, kdFull.nodes,
                NativeNearestNode.SLOT_FULL);
        RoutingCache highwayCache = new RoutingCache(0.3, kdHighway.tree, kdHighway.nodes,
                NativeNearestNode.SLOT_HIGHWAY);
        VehicleTrajectoryGenerator<TruckTripRecord> gen =
                new VehicleTrajectoryGenerator<>(road, highway, records, new TruckTrajectoryWriter(),
                        30.0, fullCache, highwayCache, HIGHWAY_THRESHOLD_KM);
        gen.generate(persons, outputDir);
        System.out.printf("[STEP 4] Done in %.1fs%n", (System.currentTimeMillis() - t3) / 1000.0);
    }

    private static void runTaxi(String tripsCsv, String networkDir, String outputDir,
                                 int[] prefCodes, int maxRoadClass) {
        // Step 1: Parse trips (single read)
        System.out.println("\n[STEP 1] Loading taxi trips...");
        long t1 = System.currentTimeMillis();
        TaxiTripCsvParser parser = new TaxiTripCsvParser();
        Map<Integer, List<TaxiTripRecord>> records = parser.parseTripRecords(tripsCsv);
        List<Person> persons = parser.convertToPersons(records);
        System.out.printf("[STEP 1] Done in %.1fs%n", (System.currentTimeMillis() - t1) / 1000.0);

        if (persons.isEmpty()) {
            System.err.println("[MAIN] No trips loaded, exiting.");
            return;
        }

        // Step 2: Load DRM road network
        Network road = loadNetwork(networkDir, prefCodes, maxRoadClass);
        if (road == null) return;

        // Step 3: Build KD-tree for accelerated nearest-node snapping
        NodeKDTree.BuildResult kdBuild = buildKDTree(road);

        // Step 4: Generate trajectories (fallback speed: 25 km/h for taxis)
        System.out.println("\n[STEP 4] Generating taxi trajectories...");
        long t3 = System.currentTimeMillis();
        RoutingCache cache = new RoutingCache(0.3, kdBuild.tree, kdBuild.nodes,
                NativeNearestNode.SLOT_FULL);
        VehicleTrajectoryGenerator<TaxiTripRecord> gen =
                new VehicleTrajectoryGenerator<>(road, records, new TaxiTrajectoryWriter(), 25.0, cache);
        gen.generate(persons, outputDir);
        System.out.printf("[STEP 4] Done in %.1fs%n", (System.currentTimeMillis() - t3) / 1000.0);
    }

    /**
     * Build a KD-tree from all network nodes for O(log N) nearest-neighbor snapping.
     * Replaces pflowlib's STRtree which has higher per-call allocation overhead.
     */
    private static NodeKDTree.BuildResult buildKDTree(Network road) {
        return buildKDTree(road, "full");
    }

    private static NodeKDTree.BuildResult buildKDTree(Network road, String label) {
        System.out.printf("\n[KD-TREE] Building %s KD-tree index...%n", label);
        long t = System.currentTimeMillis();
        List<Node> nodeList = road.listNodes();
        Node[] nodeArray = nodeList.toArray(new Node[0]);

        NodeKDTree.BuildResult result = NodeKDTree.fromNodes(nodeArray);

        // Build native C KD-tree for both networks
        if (NativeNearestNode.isAvailable()) {
            int slot = "full".equals(label) ? NativeNearestNode.SLOT_FULL : NativeNearestNode.SLOT_HIGHWAY;
            double[] lons = new double[nodeArray.length];
            double[] lats = new double[nodeArray.length];
            for (int i = 0; i < nodeArray.length; i++) {
                lons[i] = nodeArray[i].getLon();
                lats[i] = nodeArray[i].getLat();
            }
            NativeNearestNode.buildIndex(slot, lons, lats, nodeArray.length);
            System.out.printf("[KD-TREE] C native slot %d built: %,d nodes%n", slot, nodeArray.length);
        }

        System.out.printf("[KD-TREE] %s done in %.1fs — %,d nodes%n",
                label, (System.currentTimeMillis() - t) / 1000.0, nodeArray.length);
        return result;
    }

    private static Network loadNetwork(String networkDir, int[] prefCodes, int maxRoadClass) {
        System.out.println("\n[STEP 2] Loading DRM road network...");
        long t2 = System.currentTimeMillis();
        Network road = DrmNetworkLoader.loadPrefectures(networkDir, prefCodes, maxRoadClass);
        System.out.printf("[STEP 2] Done in %.1fs — %,d links loaded%n",
                (System.currentTimeMillis() - t2) / 1000.0, road.linkCount());

        if (road.linkCount() == 0) {
            System.err.println("[MAIN] No road network loaded. Check DRM files in: " + networkDir);
            System.err.println("[MAIN] Expected files: drm_08.tsv, drm_09.tsv, ... drm_14.tsv");
            return null;
        }
        return road;
    }

    private static void printUsage() {
        System.out.println("Unified Trajectory Generator for Pseudo-PFLOW");
        System.out.println();
        System.out.println("Usage: TrajectoryMain <vehicle_type> <trips_csv> [network_dir] [output_dir] [pref_codes] [maxRoadClass]");
        System.out.println();
        System.out.println("  vehicle_type : truck | taxi");
        System.out.println("  trips_csv    : path to trips_pseudo_pflow.csv");
        System.out.println("  network_dir  : directory with drm_XX.tsv files (default: $PFLOW_HOME/data/processing/network)");
        System.out.println("  output_dir   : trajectory output directory (default: $PFLOW_HOME/output/trajectory/<type>)");
        System.out.println("  pref_codes   : comma-separated prefecture codes (default: 8,9,10,11,12,13,14)");
        System.out.println("  maxRoadClass : max road class to keep, 0=all (default: 9, includes minor roads)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  TrajectoryMain truck $PFLOW_HOME/output/trips/truck/run_LATEST/trips_pseudo_pflow.csv");
        System.out.println("  TrajectoryMain taxi  $PFLOW_HOME/output/trips/taxi/tokyo/run_LATEST/trips_pseudo_pflow.csv");
    }
}
