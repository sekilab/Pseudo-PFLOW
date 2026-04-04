package traj;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.WKTReader;

import jp.ac.ut.csis.pflow.geom2.GeometryUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.res.DrmLink;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

/**
 * Multi-prefecture DRM (Digital Road Map) loader.
 * <p>
 * Loads multiple prefecture DRM TSV files into a single merged Network.
 * Cross-border connectivity emerges automatically because shared border
 * nodes have the same string IDs — network.hasNode() deduplicates them.
 * <p>
 * DRM TSV format (13 columns, tab-delimited, no header):
 * gid, src_node, tgt_node, length, rdwdcd, lanecd, regcd, rdclasscd, ..., WKT_geometry(col12)
 * <p>
 * Shared across all vehicle types (truck, taxi, bus, etc.).
 * Copied from truck.traj.DrmNetworkLoader — code unchanged.
 */
public class DrmNetworkLoader {

    /**
     * Load multiple prefecture DRM files into one merged road network (no road class filter).
     */
    public static Network loadPrefectures(String basePath, int[] prefCodes) {
        return loadPrefectures(basePath, prefCodes, 0);
    }

    /**
     * Load with road class filtering. maxRoadClass=0 means no filter (all roads).
     * Default maxRoadClass=9 includes all road classes including minor/local roads,
     * improving connectivity for delivery trucks and taxis in urban areas.
     *
     * @param basePath     directory containing drm_XX.tsv files
     * @param prefCodes    prefecture codes (e.g., {8..30} for Kanto+Chubu+Kansai)
     * @param maxRoadClass max road class to keep (0 = no filter, 9 = all classes)
     * @return merged Network with all links from specified prefectures
     */
    public static Network loadPrefectures(String basePath, int[] prefCodes, int maxRoadClass) {
        if (maxRoadClass > 0) {
            System.out.printf("[DRM] Road class filter: keeping rdclasscd <= %d (skipping minor roads)%n", maxRoadClass);
        }

        // Pipeline: parse files in parallel (bounded concurrency), merge as each completes.
        // This avoids holding all 11.7M parsed links in memory simultaneously.
        int nThreads = Math.min(4, Math.min(Runtime.getRuntime().availableProcessors(), prefCodes.length));
        System.out.printf("[DRM] Loading %d prefecture files with %d parse threads...%n", prefCodes.length, nThreads);
        long startMs = System.currentTimeMillis();

        ExecutorService es = Executors.newFixedThreadPool(nThreads);
        Network network = new Network();
        int totalLinks = 0;

        // Submit all parse tasks, then merge in order as each completes
        List<Future<ParsedPrefecture>> futures = new ArrayList<>();
        for (int pref : prefCodes) {
            final int p = pref;
            futures.add(es.submit(() -> parsePrefecture(basePath, p, maxRoadClass)));
        }
        es.shutdown();

        for (Future<ParsedPrefecture> f : futures) {
            ParsedPrefecture pp;
            try {
                pp = f.get();
            } catch (Exception e) {
                System.err.printf("[DRM] Error in parallel parse: %s%n", e.getMessage());
                continue;
            }
            if (pp == null) continue;

            int before = network.linkCount();
            for (ParsedLink pl : pp.links) {
                try {
                    Node n0, n1;
                    if (pl.reversed) {
                        n0 = network.hasNode(pl.tgt) ? network.getNode(pl.tgt) : new Node(pl.tgt, pl.p1x, pl.p1y);
                        n1 = network.hasNode(pl.src) ? network.getNode(pl.src) : new Node(pl.src, pl.p0x, pl.p0y);
                    } else {
                        n0 = network.hasNode(pl.src) ? network.getNode(pl.src) : new Node(pl.src, pl.p0x, pl.p0y);
                        n1 = network.hasNode(pl.tgt) ? network.getNode(pl.tgt) : new Node(pl.tgt, pl.p1x, pl.p1y);
                    }
                    DrmLink link = new DrmLink(pl.gid, n0, n1, pl.length, pl.length, pl.length,
                            pl.oneway, pl.rdclasscd, pl.rdwdcd, pl.lanecd, pl.points);
                    network.addLink(link);
                } catch (Exception e) {
                    // skip malformed link
                }
            }
            int added = network.linkCount() - before;
            totalLinks += added;
            System.out.printf("[DRM] Loaded prefecture %02d: %,d links (total: %,d)%n",
                    pp.prefCode, added, network.linkCount());
            // pp.links eligible for GC after this iteration
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("[DRM] Network complete: %,d total links from %d prefectures (%.1fs)%n",
                totalLinks, prefCodes.length, elapsedMs / 1000.0);
        return network;
    }

    /** Pre-parsed link data — intermediate representation between parse and merge. */
    private static class ParsedLink {
        final String gid, src, tgt;
        final int length, rdwdcd, lanecd, rdclasscd;
        final boolean oneway, reversed;
        final double p0x, p0y, p1x, p1y;
        final List<ILonLat> points;

        ParsedLink(String gid, String src, String tgt, int length, int rdwdcd, int lanecd,
                   int rdclasscd, boolean oneway, boolean reversed,
                   double p0x, double p0y, double p1x, double p1y, List<ILonLat> points) {
            this.gid = gid; this.src = src; this.tgt = tgt;
            this.length = length; this.rdwdcd = rdwdcd; this.lanecd = lanecd;
            this.rdclasscd = rdclasscd; this.oneway = oneway; this.reversed = reversed;
            this.p0x = p0x; this.p0y = p0y; this.p1x = p1x; this.p1y = p1y;
            this.points = points;
        }
    }

    private static class ParsedPrefecture {
        final int prefCode;
        final List<ParsedLink> links;
        ParsedPrefecture(int prefCode, List<ParsedLink> links) {
            this.prefCode = prefCode; this.links = links;
        }
    }

    /** Parse a single prefecture TSV in a worker thread (no shared state). */
    private static ParsedPrefecture parsePrefecture(String basePath, int pref, int maxRoadClass) {
        String filepath = String.format("%s/drm_%02d.tsv", basePath, pref);
        File file = new File(filepath);
        if (!file.exists()) {
            System.out.printf("[DRM] WARNING: %s not found, skipping prefecture %02d%n", filepath, pref);
            return null;
        }

        WKTReader wktreader = new WKTReader();
        List<ParsedLink> links = new ArrayList<>();
        int lineNum = 0, parseErrors = 0, filtered = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file), 65536)) {
            String record;
            while ((record = br.readLine()) != null) {
                lineNum++;
                try {
                    String[] items = record.split("\t");
                    if (items.length < 13) continue;

                    String gid = String.valueOf(items[0]);
                    String src = String.valueOf(items[1]);
                    String tgt = String.valueOf(items[2]);
                    int length = Integer.valueOf(items[3]);
                    int rdwdcd = Integer.valueOf(items[4]);
                    int lanecd = Integer.valueOf(items[5]);
                    int regcd = Integer.valueOf(items[6]);
                    int rdclasscd = Integer.valueOf(items[7]);

                    if (maxRoadClass > 0 && rdclasscd > maxRoadClass) { filtered++; continue; }

                    boolean oneway = DrmLink.isOneway(regcd);
                    boolean reversed = DrmLink.isOnewayAndReverse(regcd);

                    Geometry geom = wktreader.read(items[12]);
                    LineString line = null;
                    if (geom instanceof LineString) {
                        line = LineString.class.cast(geom);
                    } else if (geom instanceof MultiLineString) {
                        line = (LineString) MultiLineString.class.cast(geom).getGeometryN(0);
                    }
                    if (line == null) continue;

                    Point p0 = line.getStartPoint();
                    Point p1 = line.getEndPoint();
                    List<ILonLat> pts = GeometryUtils.createPointList(line);
                    if (reversed && pts != null && !pts.isEmpty()) {
                        Collections.reverse(pts);
                    }

                    links.add(new ParsedLink(gid, src, tgt, length, rdwdcd, lanecd, rdclasscd,
                            oneway, reversed, p0.getX(), p0.getY(), p1.getX(), p1.getY(), pts));
                } catch (Exception e) {
                    parseErrors++;
                }
            }
        } catch (Exception e) {
            System.err.printf("[DRM] Error reading %s: %s%n", filepath, e.getMessage());
        }
        return new ParsedPrefecture(pref, links);
    }

    /**
     * Load a single DRM TSV file into an existing Network.
     */
    public static void loadInto(Network network, String filepath, int maxRoadClass) {
        File file = new File(filepath);
        WKTReader wktreader = new WKTReader();
        int lineNum = 0;
        int parseErrors = 0;
        int filtered = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file), 65536)) {
            String record;
            while ((record = br.readLine()) != null) {
                lineNum++;
                try {
                    String[] items = record.split("\t");
                    if (items.length < 13) continue;

                    String gid = String.valueOf(items[0]);
                    String src = String.valueOf(items[1]);
                    String tgt = String.valueOf(items[2]);
                    int length = Integer.valueOf(items[3]);
                    int rdwdcd = Integer.valueOf(items[4]);
                    int lanecd = Integer.valueOf(items[5]);
                    int regcd = Integer.valueOf(items[6]);
                    int rdclasscd = Integer.valueOf(items[7]);

                    if (maxRoadClass > 0 && rdclasscd > maxRoadClass) { filtered++; continue; }

                    boolean way = DrmLink.isOneway(regcd);

                    Geometry geom = wktreader.read(items[12]);
                    LineString line = null;
                    if (geom instanceof LineString) {
                        line = LineString.class.cast(geom);
                    } else if (geom instanceof MultiLineString) {
                        line = (LineString) MultiLineString.class.cast(geom).getGeometryN(0);
                    }
                    if (line == null) continue;

                    Point p0 = line.getStartPoint();
                    Point p1 = line.getEndPoint();
                    Node n0, n1;
                    List<ILonLat> list = GeometryUtils.createPointList(line);
                    if (DrmLink.isOnewayAndReverse(regcd)) {
                        n0 = network.hasNode(tgt) ? network.getNode(tgt) : new Node(tgt, p1.getX(), p1.getY());
                        n1 = network.hasNode(src) ? network.getNode(src) : new Node(src, p0.getX(), p0.getY());
                        if (list != null && !list.isEmpty()) {
                            Collections.reverse(list);
                        }
                    } else {
                        n0 = network.hasNode(src) ? network.getNode(src) : new Node(src, p0.getX(), p0.getY());
                        n1 = network.hasNode(tgt) ? network.getNode(tgt) : new Node(tgt, p1.getX(), p1.getY());
                    }

                    DrmLink link = new DrmLink(gid, n0, n1, length, length, length, way, rdclasscd, rdwdcd, lanecd, list);
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
        if (filtered > 0) {
            System.out.printf("[DRM]   %s: %,d lines filtered (rdclasscd > %d), %,d kept (of %,d)%n",
                    new File(filepath).getName(), filtered, maxRoadClass, lineNum - filtered - parseErrors, lineNum);
        }
        if (parseErrors > 0) {
            System.out.printf("[DRM]   %s: %,d parse errors (of %,d lines)%n",
                    new File(filepath).getName(), parseErrors, lineNum);
        }
    }
}
