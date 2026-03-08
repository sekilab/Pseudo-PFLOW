package truck.traj;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

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
     * Default maxRoadClass=8 skips rdclasscd=9 (minor/local roads = 91% of links).
     *
     * @param basePath     directory containing drm_XX.tsv files
     * @param prefCodes    prefecture codes (e.g., {8,9,10,11,12,13,14} for Kanto)
     * @param maxRoadClass max road class to keep (0 = no filter, 8 = skip minor roads)
     * @return merged Network with all links from specified prefectures
     */
    public static Network loadPrefectures(String basePath, int[] prefCodes, int maxRoadClass) {
        Network network = new Network();
        int totalLinks = 0;
        if (maxRoadClass > 0) {
            System.out.printf("[DRM] Road class filter: keeping rdclasscd <= %d (skipping minor roads)%n", maxRoadClass);
        }
        for (int pref : prefCodes) {
            String filepath = String.format("%s/drm_%02d.tsv", basePath, pref);
            File file = new File(filepath);
            if (!file.exists()) {
                System.out.printf("[DRM] WARNING: %s not found, skipping prefecture %02d%n", filepath, pref);
                continue;
            }
            int before = network.linkCount();
            loadInto(network, filepath, maxRoadClass);
            int added = network.linkCount() - before;
            totalLinks += added;
            System.out.printf("[DRM] Loaded prefecture %02d: %,d links (total: %,d)%n", pref, added, network.linkCount());
        }
        System.out.printf("[DRM] Network complete: %,d total links from %d prefectures%n", totalLinks, prefCodes.length);
        return network;
    }

    /**
     * Load a single DRM TSV file into an existing Network.
     * Same parsing logic as network.DrmLoader.load() but appends to existing network
     * rather than creating a new one — enables multi-prefecture merging.
     *
     * @param network  existing network to add links into
     * @param filepath path to drm_XX.tsv file
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
                    if (items.length < 13) continue;  // skip malformed lines

                    String gid = String.valueOf(items[0]);
                    String src = String.valueOf(items[1]);
                    String tgt = String.valueOf(items[2]);
                    int length = Integer.valueOf(items[3]);
                    int rdwdcd = Integer.valueOf(items[4]);
                    int lanecd = Integer.valueOf(items[5]);
                    int regcd = Integer.valueOf(items[6]);
                    int rdclasscd = Integer.valueOf(items[7]);

                    // Road class filter — skip BEFORE expensive WKT parsing
                    if (maxRoadClass > 0 && rdclasscd > maxRoadClass) { filtered++; continue; }

                    boolean way = DrmLink.isOneway(regcd);

                    // Parse geometry (WKT in column 12)
                    Geometry geom = wktreader.read(items[12]);
                    LineString line = null;
                    if (geom instanceof LineString) {
                        line = LineString.class.cast(geom);
                    } else if (geom instanceof MultiLineString) {
                        line = (LineString) MultiLineString.class.cast(geom).getGeometryN(0);
                    }
                    if (line == null) continue;

                    // Build nodes (handle one-way reverse direction)
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

                    // Create DrmLink with road attributes
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
