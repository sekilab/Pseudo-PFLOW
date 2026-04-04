package traj;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jp.ac.ut.csis.pflow.routing4.logic.AStar;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;

/**
 * Thread-safe dual-layer routing cache for trajectory generation.
 * <p>
 * Layer 1 — Node snap cache: maps rounded (lon,lat) to nearest network Node.
 * Eliminates repeated O(N) brute-force nearest-node lookups for nearby coordinates.
 * <p>
 * Layer 2 — Route cache: maps (srcNodeId|tgtNodeId) to A* Route result.
 * Eliminates repeated A* searches for popular OD pairs (stations, zone centers).
 * <p>
 * Layer 3 — Short-trip bypass: skips A* entirely when origin and destination
 * snap to the same node or trip distance is below a configurable threshold.
 * <p>
 * Thread safety: Uses ConcurrentHashMap.putIfAbsent() (not computeIfAbsent)
 * for route cache because computeIfAbsent holds the bucket lock during the
 * mapping function — this would serialize A* calls hitting the same bucket.
 * putIfAbsent allows harmless duplicate computation but never blocks threads.
 */
public class RoutingCache {

    // Layer 1: long-encoded (lon4d,lat4d) → nearest Node
    private final ConcurrentHashMap<Long, Node> snapCache;

    // Layer 2: NodePair(srcId,dstId) → Route (or NO_ROUTE sentinel)
    private final ConcurrentHashMap<NodePair, Route> routeCache;

    /** Compact route cache key — avoids String concatenation per lookup. */
    private static final class NodePair {
        final String srcId, dstId;
        private final int hash;

        NodePair(String srcId, String dstId) {
            this.srcId = srcId;
            this.dstId = dstId;
            this.hash = srcId.hashCode() * 31 + dstId.hashCode();
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodePair)) return false;
            NodePair p = (NodePair) o;
            return srcId.equals(p.srcId) && dstId.equals(p.dstId);
        }
    }

    // Sentinel for unreachable pairs (ConcurrentHashMap prohibits null values)
    private static final Route NO_ROUTE = new Route();

    // Layer 3: bypass thresholds
    private final double bypassDistanceKm;       // skip A* below this (default 0.3 km)
    private final double maxRoutingDistanceKm;    // skip A* above this (default 500 km)

    // Optional KD-tree accelerator (Phase 2A)
    private final NodeKDTree kdTree;
    private final Node[] nodeArray;

    // Statistics
    private final AtomicLong snapHits = new AtomicLong(0);
    private final AtomicLong snapMisses = new AtomicLong(0);
    private final AtomicLong routeHits = new AtomicLong(0);
    private final AtomicLong routeMisses = new AtomicLong(0);
    private final AtomicLong bypassed = new AtomicLong(0);

    /**
     * @param bypassDistanceKm skip A* for trips shorter than this (default 0.3 km)
     */
    /** Default max routing distance — trips longer than this skip A* (highway fallback). */
    private static final double DEFAULT_MAX_ROUTING_KM = 500.0;

    public RoutingCache(double bypassDistanceKm) {
        this(bypassDistanceKm, DEFAULT_MAX_ROUTING_KM, null, null);
    }

    public RoutingCache(double bypassDistanceKm, NodeKDTree kdTree, Node[] nodeArray) {
        this(bypassDistanceKm, DEFAULT_MAX_ROUTING_KM, kdTree, nodeArray);
    }

    /**
     * Full constructor with all options.
     *
     * @param bypassDistanceKm   skip A* for trips shorter than this (default 0.3 km)
     * @param maxRoutingDistanceKm skip A* for trips longer than this (default 500 km)
     * @param kdTree             pre-built KD-tree from network nodes (null to use pflowlib)
     * @param nodeArray          parallel Node array indexed by KD-tree result
     */
    public RoutingCache(double bypassDistanceKm, double maxRoutingDistanceKm,
                        NodeKDTree kdTree, Node[] nodeArray) {
        this.bypassDistanceKm = bypassDistanceKm;
        this.maxRoutingDistanceKm = maxRoutingDistanceKm;
        this.snapCache = new ConcurrentHashMap<>(65536);
        this.routeCache = new ConcurrentHashMap<>(32768);
        this.kdTree = kdTree;
        this.nodeArray = nodeArray;
    }

    /**
     * Snap a (lon, lat) coordinate to the nearest network node, with caching.
     * Grid resolution: 4 decimal places (~11m). Points within ~11m of each other
     * share the same cache key and return the same snapped node.
     *
     * @return nearest Node, or null if no node found
     */
    public Node snapToNode(AStar routing, Network road, double lon, double lat) {
        long key = snapKey(lon, lat);
        Node cached = snapCache.get(key);
        if (cached != null) {
            snapHits.incrementAndGet();
            return cached;
        }

        // Cache miss — prefer: C KD-tree > Java KD-tree > pflowlib STRtree
        Node node;
        if (NativeNearestNode.isAvailable()) {
            int idx = NativeNearestNode.findNearest(lon, lat);
            node = (idx >= 0 && idx < nodeArray.length) ? nodeArray[idx] : null;
        } else if (kdTree != null) {
            int idx = kdTree.findNearestThreadSafe(lon, lat);
            node = (idx >= 0) ? nodeArray[idx] : null;
        } else {
            node = routing.getNearestNode(road, lon, lat);
        }
        if (node != null) {
            snapCache.putIfAbsent(key, node);
        }
        snapMisses.incrementAndGet();
        return node;
    }

    /**
     * Get a route between two pre-snapped nodes, with caching.
     * Returns null if the pair is unreachable.
     *
     * @param routing thread-local AStar instance
     * @param road    shared road network (read-only)
     * @param src     source node (from snapToNode)
     * @param dst     destination node (from snapToNode)
     * @return Route if reachable, null if unreachable
     */
    public Route getRoute(AStar routing, Network road, Node src, Node dst) {
        NodePair key = routeKey(src, dst);
        Route cached = routeCache.get(key);
        if (cached != null) {
            routeHits.incrementAndGet();
            return cached == NO_ROUTE ? null : cached;
        }

        // Cache miss — perform A* search
        Route route = routing.getRoute(road, src, dst);
        if (route != null && route.numNodes() > 0) {
            routeCache.putIfAbsent(key, route);
        } else {
            routeCache.putIfAbsent(key, NO_ROUTE);
        }
        routeMisses.incrementAndGet();
        return route;
    }

    /**
     * Check if a trip should bypass A* routing entirely.
     * Returns true if:
     * - Origin and destination snap to the same node
     * - Trip distance is below the short-trip threshold (< 0.3 km)
     * - Trip distance exceeds the long-haul threshold (> 500 km) — A* on 11.7M
     *   links for cross-country routes can take minutes per search; highway-speed
     *   direct fallback is a reasonable approximation for these trips.
     */
    public boolean shouldBypass(Node src, Node dst, double distanceKm) {
        if (src.getNodeID().equals(dst.getNodeID()) || distanceKm < bypassDistanceKm) {
            bypassed.incrementAndGet();
            return true;
        }
        if (distanceKm > maxRoutingDistanceKm) {
            bypassed.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Return cache statistics as a formatted string for logging.
     */
    public String getStats() {
        long sH = snapHits.get(), sM = snapMisses.get();
        long rH = routeHits.get(), rM = routeMisses.get();
        long bp = bypassed.get();
        long sTotal = sH + sM;
        long rTotal = rH + rM;
        return String.format(
                "[CACHE] snapCache: %,d entries, %,d/%,d hits (%.1f%%) | " +
                "routeCache: %,d entries, %,d/%,d hits (%.1f%%) | " +
                "bypassed: %,d trips",
                snapCache.size(), sH, sTotal, sTotal > 0 ? 100.0 * sH / sTotal : 0.0,
                routeCache.size(), rH, rTotal, rTotal > 0 ? 100.0 * rH / rTotal : 0.0,
                bp
        );
    }

    // Long-encoded 4-decimal-place grid (~11m). Eliminates String.format overhead.
    private static long snapKey(double lon, double lat) {
        int lonBits = (int) Math.floor(lon * 10000);
        int latBits = (int) Math.floor(lat * 10000);
        return ((long) lonBits << 32) | (latBits & 0xFFFFFFFFL);
    }

    private static NodePair routeKey(Node src, Node dst) {
        return new NodePair(src.getNodeID(), dst.getNodeID());
    }
}
