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

    // Layer 1: "lon4d,lat4d" → nearest Node
    private final ConcurrentHashMap<String, Node> snapCache;

    // Layer 2: "srcNodeId|tgtNodeId" → Route (or NO_ROUTE sentinel)
    private final ConcurrentHashMap<String, Route> routeCache;

    // Sentinel for unreachable pairs (ConcurrentHashMap prohibits null values)
    private static final Route NO_ROUTE = new Route();

    // Layer 3: bypass threshold
    private final double bypassDistanceKm;

    // Statistics
    private final AtomicLong snapHits = new AtomicLong(0);
    private final AtomicLong snapMisses = new AtomicLong(0);
    private final AtomicLong routeHits = new AtomicLong(0);
    private final AtomicLong routeMisses = new AtomicLong(0);
    private final AtomicLong bypassed = new AtomicLong(0);

    /**
     * @param bypassDistanceKm skip A* for trips shorter than this (default 0.3 km)
     */
    public RoutingCache(double bypassDistanceKm) {
        this.bypassDistanceKm = bypassDistanceKm;
        this.snapCache = new ConcurrentHashMap<>(65536);
        this.routeCache = new ConcurrentHashMap<>(32768);
    }

    /**
     * Snap a (lon, lat) coordinate to the nearest network node, with caching.
     * Grid resolution: 4 decimal places (~11m). Points within ~11m of each other
     * share the same cache key and return the same snapped node.
     *
     * @return nearest Node, or null if no node found
     */
    public Node snapToNode(AStar routing, Network road, double lon, double lat) {
        String key = snapKey(lon, lat);
        Node cached = snapCache.get(key);
        if (cached != null) {
            snapHits.incrementAndGet();
            return cached;
        }

        // Cache miss — perform O(N) nearest-node lookup
        Node node = routing.getNearestNode(road, lon, lat);
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
        String key = routeKey(src, dst);
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
     * Returns true if origin and destination snap to the same node,
     * or if the trip distance is below the bypass threshold.
     */
    public boolean shouldBypass(Node src, Node dst, double distanceKm) {
        if (src.getNodeID().equals(dst.getNodeID()) || distanceKm < bypassDistanceKm) {
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

    // Round to 4 decimal places (~11m grid)
    private static String snapKey(double lon, double lat) {
        return String.format("%.4f,%.4f", lon, lat);
    }

    private static String routeKey(Node src, Node dst) {
        return src.getNodeID() + "|" + dst.getNodeID();
    }
}
