package traj;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.io.WKTReader;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.GeometryUtils;
import jp.ac.ut.csis.pflow.routing4.res.DrmLink;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

/**
 * Optimized multi-prefecture DRM (Digital Road Map) loader.
 * <p>
 * Key optimizations over the original loader:
 * <ul>
 *   <li>GID dedup: DRM files overlap ~88%; skips WKT parsing for already-seen links</li>
 *   <li>No geometry: passes null geometry to DrmLink (saves ~3.7GB RAM); A* and
 *       trajectory output use only node positions, not intermediate road geometry</li>
 *   <li>No spatial indexes: Network(false,false) skips STRtree construction (saves ~0.8GB);
 *       the KD-tree in RoutingCache handles nearest-node lookups instead</li>
 *   <li>Parallel parse: TSV + WKT parsing runs in parallel threads, merged sequentially</li>
 * </ul>
 */
public class DrmNetworkLoader {

    public static Network loadPrefectures(String basePath, int[] prefCodes) {
        return loadPrefectures(basePath, prefCodes, 0, false);
    }

    /**
     * Load with road class filtering (lightweight mode, no geometry).
     */
    public static Network loadPrefectures(String basePath, int[] prefCodes, int maxRoadClass) {
        return loadPrefectures(basePath, prefCodes, maxRoadClass, false);
    }

    /**
     * Load with road class filtering and geometry mode selection.
     *
     * @param includeGeometry true = parse full WKT road geometry into each DrmLink (~14GB heap);
     *                        false = endpoints only, null geometry (~10GB heap)
     */
    public static Network loadPrefectures(String basePath, int[] prefCodes, int maxRoadClass,
                                           boolean includeGeometry) {
        if (maxRoadClass > 0) {
            System.out.printf("[DRM] Road class filter: keeping rdclasscd <= %d%n", maxRoadClass);
        }

        String geoLabel = includeGeometry ? "full-geometry" : "no-geometry";

        // GID dedup set — shared across parse threads. DRM files overlap ~88%;
        // this skips WKT parsing for duplicate links already seen in earlier files.
        Set<String> seenGids = ConcurrentHashMap.newKeySet(12_000_000);

        int nThreads = Math.min(4, Math.min(Runtime.getRuntime().availableProcessors(), prefCodes.length));
        System.out.printf("[DRM] Loading %d prefecture files (%d parse threads, dedup + %s mode)%n",
                prefCodes.length, nThreads, geoLabel);
        long startMs = System.currentTimeMillis();

        ExecutorService es = Executors.newFixedThreadPool(nThreads);

        // Network(useNodeIndex=false, useLinkIndex=false) — disables JTS STRtree
        // spatial indexes. A* routing traverses adjacency lists only; the C KD-tree
        // in RoutingCache handles nearest-node lookups. Saves ~0.8GB RAM and
        // eliminates O(log N) STRtree insertion overhead per addLink().
        Network network = new Network(false, false);
        int totalLinks = 0;
        long totalSkipped = 0;

        // Submit parse tasks, merge in order as each completes (pipeline — GC-friendly)
        List<Future<ParseResult>> futures = new ArrayList<>();
        for (int pref : prefCodes) {
            final int p = pref;
            futures.add(es.submit(() -> parsePrefecture(basePath, p, maxRoadClass, seenGids, includeGeometry)));
        }
        es.shutdown();

        for (Future<ParseResult> f : futures) {
            ParseResult pr;
            try {
                pr = f.get();
            } catch (Exception e) {
                System.err.printf("[DRM] Error in parallel parse: %s%n", e.getMessage());
                continue;
            }
            if (pr == null) continue;

            int before = network.linkCount();
            for (ParsedLink pl : pr.links) {
                try {
                    Node n0, n1;
                    if (pl.reversed) {
                        n0 = network.hasNode(pl.tgt) ? network.getNode(pl.tgt) : new Node(pl.tgt, pl.p1x, pl.p1y);
                        n1 = network.hasNode(pl.src) ? network.getNode(pl.src) : new Node(pl.src, pl.p0x, pl.p0y);
                    } else {
                        n0 = network.hasNode(pl.src) ? network.getNode(pl.src) : new Node(pl.src, pl.p0x, pl.p0y);
                        n1 = network.hasNode(pl.tgt) ? network.getNode(pl.tgt) : new Node(pl.tgt, pl.p1x, pl.p1y);
                    }
                    DrmLink link;
                    if (pl.geometry != null) {
                        // 11-arg constructor: full road geometry for road-following trajectories
                        link = new DrmLink(pl.gid, n0, n1, pl.length, pl.length, pl.length,
                                pl.oneway, pl.rdclasscd, pl.rdwdcd, pl.lanecd, pl.geometry);
                    } else {
                        // 10-arg constructor: no geometry (null internally). Saves ~3.7GB RAM.
                        link = new DrmLink(pl.gid, n0, n1, pl.length, pl.length, pl.length,
                                pl.oneway, pl.rdclasscd, pl.rdwdcd, pl.lanecd);
                    }
                    network.addLink(link);
                } catch (Exception e) {
                    // skip malformed link
                }
            }
            int added = network.linkCount() - before;
            totalLinks += added;
            totalSkipped += pr.skippedDupes;
            System.out.printf("[DRM] Loaded prefecture %02d: %,d new links, %,d dupes skipped (total: %,d)%n",
                    pr.prefCode, added, pr.skippedDupes, network.linkCount());
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("[DRM] Network complete: %,d links, %,d dupes skipped (%.1fs)%n",
                totalLinks, totalSkipped, elapsedMs / 1000.0);
        return network;
    }

    // ── Intermediate parse structures ──

    private static class ParsedLink {
        final String gid, src, tgt;
        final int length, rdwdcd, lanecd, rdclasscd;
        final boolean oneway, reversed;
        final double p0x, p0y, p1x, p1y;
        final List<ILonLat> geometry;  // null in lightweight mode

        ParsedLink(String gid, String src, String tgt, int length, int rdwdcd, int lanecd,
                   int rdclasscd, boolean oneway, boolean reversed,
                   double p0x, double p0y, double p1x, double p1y,
                   List<ILonLat> geometry) {
            this.gid = gid; this.src = src; this.tgt = tgt;
            this.length = length; this.rdwdcd = rdwdcd; this.lanecd = lanecd;
            this.rdclasscd = rdclasscd; this.oneway = oneway; this.reversed = reversed;
            this.p0x = p0x; this.p0y = p0y; this.p1x = p1x; this.p1y = p1y;
            this.geometry = geometry;
        }
    }

    private static class ParseResult {
        final int prefCode;
        final List<ParsedLink> links;
        final long skippedDupes;
        ParseResult(int prefCode, List<ParsedLink> links, long skippedDupes) {
            this.prefCode = prefCode; this.links = links; this.skippedDupes = skippedDupes;
        }
    }

    // ── Parse worker (runs in thread pool) ──

    /** Parse a single prefecture TSV. Skips GIDs already seen by other threads. */
    private static ParseResult parsePrefecture(String basePath, int pref, int maxRoadClass,
                                                Set<String> seenGids, boolean includeGeometry) {
        String filepath = String.format("%s/drm_%02d.tsv", basePath, pref);
        File file = new File(filepath);
        if (!file.exists()) {
            System.out.printf("[DRM] WARNING: %s not found, skipping prefecture %02d%n", filepath, pref);
            return null;
        }

        WKTReader wktreader = new WKTReader();
        List<ParsedLink> links = new ArrayList<>();
        int lineNum = 0, parseErrors = 0, filtered = 0;
        long skippedDupes = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file), 65536)) {
            String record;
            while ((record = br.readLine()) != null) {
                lineNum++;
                try {
                    String[] items = record.split("\t");
                    if (items.length < 13) continue;

                    String gid = items[0];

                    // GID dedup: skip WKT parsing for links already seen in other files
                    if (!seenGids.add(gid)) { skippedDupes++; continue; }

                    int rdclasscd = Integer.parseInt(items[7]);
                    if (maxRoadClass > 0 && rdclasscd > maxRoadClass) { filtered++; continue; }

                    String src = items[1];
                    String tgt = items[2];
                    int length = Integer.parseInt(items[3]);
                    int rdwdcd = Integer.parseInt(items[4]);
                    int lanecd = Integer.parseInt(items[5]);
                    int regcd = Integer.parseInt(items[6]);

                    boolean oneway = DrmLink.isOneway(regcd);
                    boolean reversed = DrmLink.isOnewayAndReverse(regcd);

                    // Parse WKT geometry for coordinates
                    double p0x, p0y, p1x, p1y;
                    List<ILonLat> geomPoints = null;
                    String wkt = items[12];

                    if (!includeGeometry && wkt.startsWith("LINESTRING(")) {
                        // Lightweight: fast string parse for endpoints only — avoids JTS allocation
                        int start = 11; // after "LINESTRING("
                        int firstSpace = wkt.indexOf(' ', start);
                        int firstComma = wkt.indexOf(',', firstSpace);
                        p0x = Double.parseDouble(wkt.substring(start, firstSpace));
                        p0y = Double.parseDouble(wkt.substring(firstSpace + 1, firstComma));

                        int lastParen = wkt.length() - 1; // before ")"
                        int lastComma = wkt.lastIndexOf(',');
                        int lastSpace = wkt.indexOf(' ', lastComma);
                        p1x = Double.parseDouble(wkt.substring(lastComma + 1, lastSpace));
                        p1y = Double.parseDouble(wkt.substring(lastSpace + 1, lastParen));
                    } else {
                        // Full geometry mode OR non-LINESTRING: parse via JTS WKTReader
                        Geometry geom = wktreader.read(wkt);
                        LineString line = null;
                        if (geom instanceof LineString) {
                            line = (LineString) geom;
                        } else if (geom instanceof MultiLineString) {
                            line = (LineString) ((MultiLineString) geom).getGeometryN(0);
                        }
                        if (line == null) continue;
                        Coordinate c0 = line.getCoordinateN(0);
                        Coordinate cN = line.getCoordinateN(line.getNumPoints() - 1);
                        p0x = c0.x; p0y = c0.y;
                        p1x = cN.x; p1y = cN.y;

                        if (includeGeometry) {
                            geomPoints = GeometryUtils.createPointList(line);
                        }
                    }

                    links.add(new ParsedLink(gid, src, tgt, length, rdwdcd, lanecd, rdclasscd,
                            oneway, reversed, p0x, p0y, p1x, p1y, geomPoints));
                } catch (Exception e) {
                    parseErrors++;
                }
            }
        } catch (Exception e) {
            System.err.printf("[DRM] Error reading %s: %s%n", filepath, e.getMessage());
        }
        return new ParseResult(pref, links, skippedDupes);
    }

    // ── Legacy single-file loader (kept for backward compatibility) ──

    public static void loadInto(Network network, String filepath, int maxRoadClass) {
        File file = new File(filepath);
        WKTReader wktreader = new WKTReader();
        int lineNum = 0, parseErrors = 0, filtered = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file), 65536)) {
            String record;
            while ((record = br.readLine()) != null) {
                lineNum++;
                try {
                    String[] items = record.split("\t");
                    if (items.length < 13) continue;
                    String gid = items[0], src = items[1], tgt = items[2];
                    int length = Integer.parseInt(items[3]);
                    int rdwdcd = Integer.parseInt(items[4]);
                    int lanecd = Integer.parseInt(items[5]);
                    int regcd = Integer.parseInt(items[6]);
                    int rdclasscd = Integer.parseInt(items[7]);
                    if (maxRoadClass > 0 && rdclasscd > maxRoadClass) { filtered++; continue; }

                    boolean way = DrmLink.isOneway(regcd);
                    Geometry geom = wktreader.read(items[12]);
                    LineString line = (geom instanceof LineString) ? (LineString) geom : null;
                    if (line == null && geom instanceof MultiLineString)
                        line = (LineString) ((MultiLineString) geom).getGeometryN(0);
                    if (line == null) continue;

                    Coordinate c0 = line.getCoordinateN(0);
                    Coordinate cN = line.getCoordinateN(line.getNumPoints() - 1);
                    Node n0, n1;
                    if (DrmLink.isOnewayAndReverse(regcd)) {
                        n0 = network.hasNode(tgt) ? network.getNode(tgt) : new Node(tgt, cN.x, cN.y);
                        n1 = network.hasNode(src) ? network.getNode(src) : new Node(src, c0.x, c0.y);
                    } else {
                        n0 = network.hasNode(src) ? network.getNode(src) : new Node(src, c0.x, c0.y);
                        n1 = network.hasNode(tgt) ? network.getNode(tgt) : new Node(tgt, cN.x, cN.y);
                    }
                    DrmLink link = new DrmLink(gid, n0, n1, length, length, length, way, rdclasscd, rdwdcd, lanecd);
                    network.addLink(link);
                } catch (Exception e) {
                    parseErrors++;
                    if (parseErrors <= 3) {
                        System.err.printf("[DRM] Parse error at line %d in %s: %s%n",
                                lineNum, new File(filepath).getName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.printf("[DRM] Error reading %s: %s%n", filepath, e.getMessage());
        }
    }
}
