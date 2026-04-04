package traj;

import util.PathResolver;

/**
 * JNI bridge to C KD-tree for high-performance nearest-node lookups.
 * <p>
 * The C implementation eliminates all JVM overhead (object allocation,
 * boxing, GC pressure) from the hot nearest-neighbor path. The KD-tree
 * is built once after network load and queried from multiple threads.
 * <p>
 * Falls back to Java KD-tree ({@link NodeKDTree}) if native library
 * is unavailable. Check {@link #isAvailable()} before calling native methods.
 */
public class NativeNearestNode {

    private static boolean available = false;

    static {
        try {
            String pflowHome = PathResolver.getPflowHome();
            String os = System.getProperty("os.name", "").toLowerCase();
            String libPath;
            if (os.contains("mac") || os.contains("darwin")) {
                libPath = pflowHome + "/Pseudo-PFLOW/native/lib/libpflownn.dylib";
            } else if (os.contains("win")) {
                libPath = pflowHome + "/Pseudo-PFLOW/native/lib/pflownn.dll";
            } else {
                libPath = pflowHome + "/Pseudo-PFLOW/native/lib/libpflownn.so";
            }
            System.load(libPath);
            available = true;
            System.out.println("[NATIVE] Loaded C KD-tree: " + libPath);
        } catch (UnsatisfiedLinkError e) {
            System.out.println("[NATIVE] C KD-tree not available, using Java fallback: " + e.getMessage());
        }
    }

    /** @return true if the native library was loaded successfully */
    public static boolean isAvailable() { return available; }

    /**
     * Build a KD-tree index from node coordinate arrays.
     * Must be called before findNearest(). Thread-safe after build completes.
     *
     * @param lons longitude array
     * @param lats latitude array
     * @param count number of nodes
     */
    public static native void buildIndex(double[] lons, double[] lats, int count);

    /**
     * Find the nearest node to (lon, lat).
     * Thread-safe — multiple threads can query concurrently.
     *
     * @return index into the original arrays passed to buildIndex()
     */
    public static native int findNearest(double lon, double lat);

    /** Free native memory. Call when done with trajectory generation. */
    public static native void dispose();
}
