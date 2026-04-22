package traj;

import util.PathResolver;

/**
 * JNI bridge to C KD-tree for high-performance nearest-node lookups.
 * <p>
 * Supports multiple named tree slots (e.g., full network + highway network).
 * The C implementation eliminates all JVM overhead from the hot nearest-neighbor path.
 * <p>
 * Falls back to Java KD-tree ({@link NodeKDTree}) if native library is unavailable.
 */
public class NativeNearestNode {

    /** Slot for the full road network (rdclass <= 9). */
    public static final int SLOT_FULL = 0;
    /** Slot for the highway-only network (rdclass <= 7). */
    public static final int SLOT_HIGHWAY = 1;

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

    public static boolean isAvailable() { return available; }

    /**
     * Build a KD-tree index in the given slot.
     * @param slot  tree slot (SLOT_FULL or SLOT_HIGHWAY)
     * @param lons  longitude array
     * @param lats  latitude array
     * @param count number of nodes
     */
    public static native void buildIndex(int slot, double[] lons, double[] lats, int count);

    /**
     * Find the nearest node in the given slot's tree.
     * @param slot tree slot
     * @return index into the original arrays passed to buildIndex()
     */
    public static native int findNearest(int slot, double lon, double lat);

    /** Free native memory for a slot. */
    public static native void dispose(int slot);
}
