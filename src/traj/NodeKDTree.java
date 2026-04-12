package traj;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

import java.util.List;

/**
 * Array-based 2D KD-tree for O(log N) nearest-neighbor lookups on DRM network nodes.
 * <p>
 * Replaces pflowlib's STRtree-based getNearestNode() which has higher per-call
 * overhead (Envelope allocation, ArrayList, iterator) even though it's also O(log N + k).
 * This implementation uses parallel arrays for cache-friendly memory access and
 * performs zero heap allocation per query.
 * <p>
 * Build: O(N log N) via median-of-medians partitioning.
 * Query: O(log N) average, O(sqrt(N)) worst case for 2D.
 * Memory: 3 arrays of size N (lons, lats, indices) + 1 int array for tree structure.
 */
public class NodeKDTree {

    // Node coordinates in tree order
    private final double[] lons;
    private final double[] lats;
    // Maps tree position → original node array index
    private final int[] origIndex;
    private final int size;

    // Temp arrays used only during construction
    private double[] tmpLons;
    private double[] tmpLats;
    private int[] tmpIdx;

    /**
     * Build a KD-tree from pre-extracted node arrays.
     *
     * @param nodes array of Node objects
     * @return KD-tree ready for queries
     */
    public static BuildResult fromNodes(Node[] nodes) {
        int n = nodes.length;
        double[] lons = new double[n];
        double[] lats = new double[n];
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) {
            lons[i] = nodes[i].getLon();
            lats[i] = nodes[i].getLat();
            indices[i] = i;
        }
        NodeKDTree tree = new NodeKDTree(lons, lats, indices, n);
        return new BuildResult(tree, nodes);
    }

    /** Result of building a KD-tree: the tree + the node array for index lookups. */
    public static class BuildResult {
        public final NodeKDTree tree;
        public final Node[] nodes;
        public BuildResult(NodeKDTree tree, Node[] nodes) {
            this.tree = tree;
            this.nodes = nodes;
        }
    }

    private NodeKDTree(double[] srcLons, double[] srcLats, int[] srcIdx, int n) {
        this.size = n;
        this.lons = new double[n];
        this.lats = new double[n];
        this.origIndex = new int[n];

        // Working copies for in-place partitioning
        this.tmpLons = srcLons.clone();
        this.tmpLats = srcLats.clone();
        this.tmpIdx = srcIdx.clone();

        // Build tree by rearranging into implicit binary heap layout
        buildTree(0, n - 1, 0, 0);

        // Release temp arrays
        this.tmpLons = null;
        this.tmpLats = null;
        this.tmpIdx = null;
    }

    /**
     * Recursively build the KD-tree using median partitioning.
     * The tree is stored in an implicit array layout (like a binary heap):
     * - Root at index 0
     * - Left child of i: 2*i + 1
     * - Right child of i: 2*i + 2
     *
     * @param lo   lower bound in tmp arrays (inclusive)
     * @param hi   upper bound in tmp arrays (inclusive)
     * @param treeIdx position in the output tree arrays
     * @param depth  current depth (0 = split on lon, 1 = split on lat, ...)
     */
    private void buildTree(int lo, int hi, int treeIdx, int depth) {
        if (lo > hi || treeIdx >= size) return;

        if (lo == hi) {
            // Leaf node
            lons[treeIdx] = tmpLons[lo];
            lats[treeIdx] = tmpLats[lo];
            origIndex[treeIdx] = tmpIdx[lo];
            return;
        }

        int mid = (lo + hi) / 2;
        boolean splitOnLon = (depth % 2 == 0);

        // Partition around median using nth_element (quickselect)
        nthElement(lo, hi, mid, splitOnLon);

        // Place median at current tree position
        lons[treeIdx] = tmpLons[mid];
        lats[treeIdx] = tmpLats[mid];
        origIndex[treeIdx] = tmpIdx[mid];

        // Recurse on left and right halves
        buildTree(lo, mid - 1, 2 * treeIdx + 1, depth + 1);
        buildTree(mid + 1, hi, 2 * treeIdx + 2, depth + 1);
    }

    /**
     * Quickselect: partition tmp arrays so that element at index k is the median,
     * all elements [lo..k-1] are <= median, and all [k+1..hi] are >= median.
     */
    private void nthElement(int lo, int hi, int k, boolean splitOnLon) {
        while (lo < hi) {
            int pivotIdx = lo + (hi - lo) / 2;
            double pivotVal = splitOnLon ? tmpLons[pivotIdx] : tmpLats[pivotIdx];

            // Move pivot to end
            swap(pivotIdx, hi);

            int store = lo;
            for (int i = lo; i < hi; i++) {
                double val = splitOnLon ? tmpLons[i] : tmpLats[i];
                if (val < pivotVal) {
                    swap(i, store);
                    store++;
                }
            }
            swap(store, hi);

            if (store == k) return;
            else if (store < k) lo = store + 1;
            else hi = store - 1;
        }
    }

    private void swap(int i, int j) {
        double tl = tmpLons[i]; tmpLons[i] = tmpLons[j]; tmpLons[j] = tl;
        double ta = tmpLats[i]; tmpLats[i] = tmpLats[j]; tmpLats[j] = ta;
        int ti = tmpIdx[i]; tmpIdx[i] = tmpIdx[j]; tmpIdx[j] = ti;
    }

    /**
     * Thread-safe nearest-neighbor search. Allocates one small array per call
     * (only called on cache misses, so allocation cost is negligible).
     * Uses full double precision for distance comparisons.
     *
     * @return index into original node array, or -1 if tree is empty
     */
    public int findNearestThreadSafe(double queryLon, double queryLat) {
        if (size == 0) return -1;
        // state[0] = bestIdx (as double), state[1] = bestDistSq
        double[] state = {-1.0, Double.MAX_VALUE};
        searchNearestTS(0, queryLon, queryLat, 0, state);
        return (int) state[0];
    }

    private void searchNearestTS(int treeIdx, double qLon, double qLat, int depth, double[] state) {
        if (treeIdx >= size) return;

        double nodeLon = lons[treeIdx];
        double nodeLat = lats[treeIdx];
        double dLon = qLon - nodeLon;
        double dLat = qLat - nodeLat;
        double distSq = dLon * dLon + dLat * dLat;

        if (distSq < state[1]) {
            state[1] = distSq;
            state[0] = origIndex[treeIdx];
        }

        boolean splitOnLon = (depth % 2 == 0);
        double diff = splitOnLon ? dLon : dLat;

        int nearChild = (diff < 0) ? 2 * treeIdx + 1 : 2 * treeIdx + 2;
        int farChild  = (diff < 0) ? 2 * treeIdx + 2 : 2 * treeIdx + 1;

        searchNearestTS(nearChild, qLon, qLat, depth + 1, state);

        if (diff * diff < state[1]) {
            searchNearestTS(farChild, qLon, qLat, depth + 1, state);
        }
    }

    // NOTE: Non-thread-safe findNearest() was removed. Use findNearestThreadSafe() only.
    // The tree arrays (lons, lats, origIndex) are immutable after construction,
    // making concurrent queries safe via the thread-safe variant.

    public int size() { return size; }
}
