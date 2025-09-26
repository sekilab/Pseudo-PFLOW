package pseudo.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.logic.transport.Transport;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import network.DrmLoader;
import network.RailLoader;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import pseudo.acs.DataAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.res.*;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Trip generator using WebAPI (Kobe variant).
 * Cleaned and reorganized for Java 11:
 * - Removed duplicated helpers/classes and unused code.
 * - Replaced comments with English.
 * - Switched to JDK ThreadLocalRandom.
 * - Kept original behavior and overall structure intact.
 */
public class TripGenerator_WebAPI_refactor_Kobe_v2 {

    /* ===========================
     * Constants / Parameters
     * =========================== */
    private static final double MIN_TRANSIT_DISTANCE = 1000;       // meters: only consider transit above this distance
    private static final double FARE_PER_KILOMETER = 51;           // JPY, vehicle only
    private static final double FARE_PER_HOUR = 1000;              // JPY, all modes (can be extended per prefecture)
    private static final double FATIGUE_INDEX_WALK = 2.5;          // fatigue penalty for walking
    private static final double FATIGUE_INDEX_BICYCLE = 1.5;       // fatigue penalty for bicycle
    private static final double FARE_INIT = 250;                    // JPY, vehicle only (initial cost)
    private static final double CAR_AVAILABILITY = 0.4;            // probability to use car without ownership

    /* ===========================
     * Global statistics for bus routes & station boardings
     * =========================== */

    // Count total boardings per bus route
    private static final ConcurrentHashMap<String, AtomicInteger> BUS_ROUTE_STATS = new ConcurrentHashMap<>();

    // Count boardings per station (stationKey = "name|lon,lat") by hour [0..23]
    private static final ConcurrentHashMap<String, int[]> STATION_BOARDINGS_HOURLY = new ConcurrentHashMap<>();

    // Count boardings per (routeId || stationKey) by hour [0..23]
    private static final ConcurrentHashMap<String, int[]> ROUTE_STATION_BOARDINGS_HOURLY = new ConcurrentHashMap<>();

    /* ===========================
     * Networks / HTTP client
     * =========================== */
    private final Network drm;
    private final Network railway;

    private final SSLContext sslContext;
    private final PoolingHttpClientConnectionManager connManager;
    private final CloseableHttpClient httpClient;
    private final String sessionId;

    /* ===========================
     * Properties
     * =========================== */
    private static Properties prop;

    /* ===========================
     * Helper data structure
     * =========================== */
    private static class TransportNode {
        final Node node;
        final ETransport mode;
        TransportNode(Node node, ETransport mode) {
            this.node = node;
            this.mode = mode;
        }
    }

    /* ===========================
     * Basic helpers (static)
     * =========================== */

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty() && !"null".equals(s);
    }

    private static boolean isRail(int c) {
        // transportation code: 2,4 are railway-like in this context
        return c == 2 || c == 4;
    }

    private static boolean isRouteWalk(String routeId) {
        String r = routeId == null ? "" : routeId.trim();
        return r.equals("徒歩") || r.equalsIgnoreCase("walk");
    }

    private static String normalizeRouteId(String s) {
        return (s == null || "null".equals(s)) ? "" : s.trim();
    }

    private static String stationKey(String name, double lon, double lat) {
        return (name == null ? "" : name) + "|" +
            String.format(Locale.US, "%.6f", lon) + "," +
            String.format(Locale.US, "%.6f", lat);
    }

    /* ===========================
     * Statistics accessors / exporters
     * =========================== */

    public static Map<String, Integer> getBusRouteStats() {
        Map<String, Integer> copy = new HashMap<>();
        BUS_ROUTE_STATS.forEach((k, v) -> copy.put(k, v.get()));
        return Collections.unmodifiableMap(copy);
    }

    public static void clearBusRouteStats() { BUS_ROUTE_STATS.clear(); }

    public static void printBusRouteStats() {
        System.out.println("=== Bus Route Usage Stats ===");
        getBusRouteStatsSorted().forEach(e -> System.out.println(e.getKey() + " : " + e.getValue()));
    }

    /** Sorted (desc) immutable list of (routeId, count). */
    public static List<Map.Entry<String, Integer>> getBusRouteStatsSorted() {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        BUS_ROUTE_STATS.forEach((k, v) -> list.add(Map.entry(k, v.get())));
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return Collections.unmodifiableList(list);
    }

    /** Create CSV (with header) for route-level stats. */
    public static String busRouteStatsToCSV() {
        StringBuilder sb = new StringBuilder();
        sb.append("routeId,count\n");
        for (Map.Entry<String, Integer> e : getBusRouteStatsSorted()) {
            sb.append(e.getKey()).append(',').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    /** Write route-level stats to CSV (overwrite if exists). */
    public static void exportBusRouteStatsToCSV(Path file) throws IOException {
        Files.writeString(file, busRouteStatsToCSV(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Reset route-station-hourly stats. */
    public static void resetRouteStationBoardingsHourly() { ROUTE_STATION_BOARDINGS_HOURLY.clear(); }

    /* ---------- CSV utilities: UTF-8 + BOM, CRLF, always-quoted fields ---------- */
    private static BufferedWriter openCsvUtf8BomWriter(Path file) throws IOException {
        OutputStream os = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        // write BOM
        os.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
        return new BufferedWriter(new OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void csvRow(BufferedWriter w, String... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) w.write(',');
            String f = fields[i] == null ? "" : fields[i];
            w.write('"');
            w.write(f.replace("\"", "\"\"")); // escape quotes
            w.write('"');
        }
        w.write("\r\n"); // CRLF
    }

    /** (1) Export Route × Station × Hour × Count (sorted by Route -> Hour -> Station). */
    public static void exportRouteStationBoardingsHourlyToCSV(Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> e : ROUTE_STATION_BOARDINGS_HOURLY.entrySet()) {
            String key = e.getKey(); // routeId||stationName|lon,lat
            int[] bins = e.getValue();

            String[] parts = key.split("\\|\\|", 2);
            String routeId = parts.length > 0 ? parts[0] : "";
            String stationKey = parts.length > 1 ? parts[1] : "";
            String[] sk = stationKey.split("\\|", 2);
            String stationName = sk.length > 0 ? sk[0] : "";
            String lonlat = sk.length > 1 ? sk[1] : "0,0";
            String[] ll = lonlat.split(",", 2);
            String lon = ll.length > 0 ? ll[0] : "0";
            String lat = ll.length > 1 ? ll[1] : "0";

            for (int h = 0; h < 24; h++) {
                int c = bins[h];
                if (c == 0) continue;
                rows.add(new String[]{routeId, stationName, lon, lat, Integer.toString(h), Integer.toString(c)});
            }
        }
        rows.sort((a, b) -> {
            int r = a[0].compareTo(b[0]); // routeId
            if (r != 0) return r;
            r = Integer.compare(Integer.parseInt(a[4]), Integer.parseInt(b[4])); // hour
            if (r != 0) return r;
            return a[1].compareTo(b[1]); // station_name
        });

        try (BufferedWriter w = openCsvUtf8BomWriter(file)) {
            csvRow(w, "routeId", "station_name", "lon", "lat", "hour", "count");
            for (String[] row : rows) csvRow(w, row);
        }
    }

    /** (2) Optional: export Station × Hour × Count (if still used downstream). */
    public static void exportStationBoardingsHourlyToCSV(Path file) throws IOException {
        try (BufferedWriter w = openCsvUtf8BomWriter(file)) {
            csvRow(w, "station_key", "station_name", "lon", "lat", "hour", "count");
            for (Map.Entry<String, int[]> e : STATION_BOARDINGS_HOURLY.entrySet()) {
                String key = e.getKey(); // stationName|lon,lat
                int[] bins = e.getValue();
                String[] parts = key.split("\\|", 2);
                String stationName = parts.length > 0 ? parts[0] : "";
                String lonlat = parts.length > 1 ? parts[1] : "0,0";
                String[] ll = lonlat.split(",", 2);
                String lon = ll.length > 0 ? ll[0] : "0";
                String lat = ll.length > 1 ? ll[1] : "0";

                for (int h = 0; h < 24; h++) {
                    int c = bins[h];
                    if (c == 0) continue;
                    csvRow(w, key, stationName, lon, lat, Integer.toString(h), Integer.toString(c));
                }
            }
        }
    }

    /** Record a boarding event for stats (both station-only and route×station views). */
    private static void recordStationBoarding(String routeId, String stationName, double lon, double lat, int hour) {
        if (!notEmpty(routeId) || isRouteWalk(routeId)) return;
        if (hour < 0 || hour > 23) hour = 0;

        String sKey = stationKey(stationName, lon, lat);
        String rsKey = routeId + "||" + sKey;
        final int h = hour;

        // route × station × hour
        ROUTE_STATION_BOARDINGS_HOURLY.compute(rsKey, (k, arr) -> {
            if (arr == null) arr = new int[24];
            arr[h] += 1;
            return arr;
        });

        // station × hour (kept for compatibility/export)
        STATION_BOARDINGS_HOURLY.compute(sKey, (k, arr) -> {
            if (arr == null) arr = new int[24];
            arr[h] += 1;
            return arr;
        });
    }

    /* ===========================
     * Construction / HTTP setup
     * =========================== */

    public TripGenerator_WebAPI_refactor_Kobe_v2(Country japan, Network drm, Network railway) throws Exception {
        super();
        this.drm = drm;
        this.railway = railway;
        this.sslContext = createSSLContext();
        this.connManager = createConnManager();
        this.httpClient = createHttpClient();
        this.sessionId = createSession();
    }

    private SSLContext createSSLContext() throws Exception {
        return SSLContextBuilder.create()
            .loadTrustMaterial(new TrustSelfSignedStrategy())
            .build();
    }

    private PoolingHttpClientConnectionManager createConnManager() {
        SSLContext sc;
        try {
            sc = SSLContextBuilder.create()
                .loadTrustMaterial(new TrustSelfSignedStrategy())
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL context", e);
        }

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
            sc,
            new String[]{"TLSv1.2", "TLSv1.3"},
            null,
            NoopHostnameVerifier.INSTANCE
        );

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
            RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslSocketFactory)
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .build()
        );

        // You may tune these depending on your workload
        cm.setMaxTotal(32);
        cm.setDefaultMaxPerRoute(100);
        return cm;
    }

    private CloseableHttpClient createHttpClient() {
        return HttpClients.custom()
            .setSSLContext(this.sslContext)
            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .setConnectionManager(this.connManager)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .build())
            .build();
    }

    private static HttpResponse executePostRequest(CloseableHttpClient httpClient, HttpPost postRequest) throws Exception {
        return httpClient.execute(postRequest);
    }

    public String createSession() throws Exception {
        HttpPost createSessionPost = new HttpPost(prop.getProperty("api.createSessionURL"));

        List<NameValuePair> sessionParams = new ArrayList<>();
        sessionParams.add(new BasicNameValuePair("UserID", prop.getProperty("api.userID")));
        sessionParams.add(new BasicNameValuePair("Password", prop.getProperty("api.password")));
        createSessionPost.setEntity(new UrlEncodedFormEntity(sessionParams));

        HttpResponse sessionResponse = executePostRequest(this.httpClient, createSessionPost);
        if (sessionResponse.getStatusLine().getStatusCode() == 200) {
            String body = EntityUtils.toString(sessionResponse.getEntity());
            System.out.println("Session created successfully");
            System.out.println(body);
            return body.split(",")[1].trim().replace("\r", "").replace("\n", "");
        } else {
            System.out.println("Failed to create session: " + sessionResponse.getStatusLine().getStatusCode());
            return "";
        }
    }

    protected synchronized double getRandom() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
    }

    /* ===========================
     * Core: WebAPI calls
     * =========================== */

    private static JsonNode getMixedRoute(CloseableHttpClient httpClient, String sessionid, Map<String, String> params) {
        HttpPost post = new HttpPost(prop.getProperty("api.getMixedRouteURL"));

        List<NameValuePair> mixedRouteParams = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            mixedRouteParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }

        try {
            post.setEntity(new UrlEncodedFormEntity(mixedRouteParams));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        post.setHeader("Cookie", "WebApiSessionID=" + sessionid);

        HttpResponse resp;
        try {
            resp = executePostRequest(httpClient, post);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ObjectMapper mapper = new ObjectMapper();

        if (resp.getStatusLine().getStatusCode() == 200) {
            String body;
            try {
                body = EntityUtils.toString(resp.getEntity());
                return mapper.readTree(body);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Failed to get mixed route: " + resp.getStatusLine().getStatusCode());
            try {
                return mapper.readTree("");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /* ===========================
     * Inner worker (per-thread)
     * =========================== */
    private class TripTask implements Callable<Integer> {
        private final int id;
        private final List<Person> listAgents;
        private int error;
        private int total;
        private final LinkCost linkCost = new LinkCost();
        private final Dijkstra routing = new Dijkstra(linkCost);

        public TripTask(int id, List<Person> listAgents) {
            this.id = id;
            this.listAgents = listAgents;
            this.total = this.error = 0;
        }

        private EPurpose convertHomeMode(ELabor labor) {
            switch (labor) {
                case WORKER: return EPurpose.OFFICE;
                case JOBLESS:
                case NO_LABOR:
                case UNDEFINED:
                case INFANT: return EPurpose.FREE;
                case PRE_SCHOOL:
                case PRIMARY_SCHOOL:
                case SECONDARY_SCHOOL:
                case HIGH_SCHOOL:
                case COLLEGE:
                case JUNIOR_COLLEGE:
                default: return EPurpose.SCHOOL;
            }
        }

        /** Map WebAPI transportation code to ETransport. */
        private ETransport getTransport(int mode) {
            switch (mode) {
                case 4:  return ETransport.WALK;
                case 3:  return ETransport.BUS;
                case 2:  return ETransport.TRAIN;
                case 0:  return ETransport.NOT_DEFINED;
                default: return ETransport.CAR;
            }
        }

        private int calculateMultiplier(ETransport mode) {
            switch (mode) {
                case WALK:     return 6;
                case BICYCLE:  return 3;
                case CAR:      return 1;
                default:       return 1;
            }
        }

        /** Project date into target timezone and offset as original logic does. */
        private void configureCalendar(Calendar calendar, Date date) {
            TimeZone timeZone = TimeZone.getTimeZone("Asia/Tokyo");
            calendar.setTime(date);
            calendar.setTimeZone(timeZone);
            calendar.add(Calendar.MILLISECOND, -timeZone.getOffset(calendar.getTimeInMillis()));
            calendar.add(Calendar.YEAR, 45);
            calendar.add(Calendar.MONTH, 9);
        }

        /** Decide mode among CAR/WALK/BICYCLE/MIX given road route, distance and WebAPI. */
        private ETransport determineTransportMode(Person person,
                                                  double distance,
                                                  Route route,
                                                  Map<String, String> mixedparams,
                                                  JsonNode[] mixedResultsHolder) {
            ETransport nextMode;
            Map<ETransport, Double> choices = new LinkedHashMap<>();

            if (route != null) {
                // Road-based options
                double roadtime = route.getCost(); // seconds
                double roadfare = FARE_INIT + route.getLength() / 1000 * FARE_PER_KILOMETER;
                double roadcost = roadfare + roadtime / 3600 * FARE_PER_HOUR;
                if (person.hasCar() || getRandom() < CAR_AVAILABILITY) {
                    choices.put(ETransport.CAR, roadcost);
                }
                double walktime = route.getLength() / 1.38; // ~1.38 m/s
                double walkcost = walktime / 3600 * FARE_PER_HOUR * FATIGUE_INDEX_WALK;
                choices.put(ETransport.WALK, walkcost);

                if (person.hasBike()) {
                    double biketime = walktime / 2;
                    double bikecost = biketime / 3600 * FARE_PER_HOUR * FATIGUE_INDEX_BICYCLE;
                    choices.put(ETransport.BICYCLE, bikecost);
                }
            }

            if (distance > MIN_TRANSIT_DISTANCE) {
                mixedResultsHolder[0] = getMixedRoute(httpClient, sessionId, mixedparams);
                boolean publicTransit = mixedResultsHolder[0].path("num_station").asInt() > 0
                    && mixedResultsHolder[0].path("fare").asInt() > 0;
                if (publicTransit) {
                    double mixedfare = mixedResultsHolder[0].path("fare").asDouble();
                    double mixedtime = mixedResultsHolder[0].path("total_time").asDouble(); // minutes
                    double mixedcost = mixedfare + mixedtime / 60 * FARE_PER_HOUR;
                    choices.put(ETransport.MIX, mixedcost);
                }
            }

            nextMode = choices.entrySet()
                .stream()
                .min(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(ETransport.NOT_DEFINED);

            return nextMode;
        }

        private long calculateTravelTime(Route route, int multiplier) {
            return (route != null) ? (long) route.getCost() * multiplier : 3600L;
        }

        private void addSubpoints(List<Node> nodes,
                                  Map<Node, Date> timeMap,
                                  ETransport nextMode,
                                  EPurpose purpose,
                                  List<SPoint> subpoints) {
            for (ILonLat node : nodes) {
                Date date = timeMap.get(node);
                Calendar cl = Calendar.getInstance();
                configureCalendar(cl, date);
                Date d = cl.getTime();
                SPoint point = new SPoint(node.getLon(), node.getLat(), d, nextMode, purpose);
                subpoints.add(point);
            }
        }

        private void assignLinksToSubpoints(List<SPoint> subpoints, List<Link> links) {
            for (int k = 1; k <= links.size(); k++) {
                subpoints.get(k).setLink(links.get(k - 1).getLinkID());
            }
        }

        /** Handle mixed (public transit + walk/road) path from WebAPI and generate trajectories/trips. */
        private void handleMixedTransport(ETransport nextMode,
                                          EPurpose purpose,
                                          JsonNode[] mixedResultsHolder,
                                          List<SPoint> subpoints,
                                          List<SPoint> points,
                                          Person person,
                                          Activity next,
                                          Route route,
                                          long startTimeParam,
                                          long endTimeParam,
                                          LonLat oll,
                                          LonLat dll) {

            JsonNode root = mixedResultsHolder[0];

            // 1) Build time window
            long travelTimeSec = Math.max(1L, root.path("total_time").asLong() * 60L);
            long depTime = next.getStartTime() - travelTimeSec;
            long nowSec = System.currentTimeMillis() / 1000L;
            long segStart = depTime > 0 ? depTime : (startTimeParam > 0 ? startTimeParam : nowSec);
            long segEnd   = segStart + travelTimeSec;

            // 2) Extract nodes (with interpolation between station->station)
            List<TransportNode> tnList = extractTransportNodesFromMixedResults(mixedResultsHolder);

            // 3) Split into sub-trips and add Trip entries
            processMixedTransportFeatures(mixedResultsHolder, depTime, person, purpose);

            // 4) Build SPoint with timestamps
            addTimeStampedSubpoints(tnList, segStart, segEnd, purpose, subpoints);
            points.addAll(subpoints);
        }

        /** Extract transport nodes from WebAPI features, interpolating between consecutive stations. */
        private List<TransportNode> extractTransportNodesFromMixedResults(JsonNode[] holder) {
            List<TransportNode> out = new ArrayList<>();
            if (holder == null || holder.length == 0) return out;

            JsonNode features = holder[0].path("features");
            if (features == null || !features.isArray()) return out;

            Set<String> tripRoutes = new HashSet<>();
            TransportNode prevStationTN = null;
            int prevTransCode = -1;

            boolean prevTransit = false;
            String  prevTransitRouteId = null;

            for (JsonNode feat : features) {
                JsonNode coords = feat.path("geometry").path("coordinates");
                if (!coords.isArray()) continue;

                int transCode = feat.path("properties").path("transportation").asInt();
                boolean isStation = !feat.path("properties").path("station").isNull()
                    && !"null".equals(feat.path("properties").path("station").asText());

                double lon = coords.get(0).asDouble();
                double lat = coords.get(1).asDouble();

                String routeId = null;
                if (isStation) {
                    JsonNode rnode = feat.path("properties").path("station").path("route");
                    if (!rnode.isMissingNode()) routeId = rnode.asText();
                }

                Node node = new Node(feat.path("properties").path("id").asText(), lon, lat);
                TransportNode curTN = new TransportNode(node, getTransport(transCode));

                // Interpolate between consecutive stations
                if (isStation && prevStationTN != null) {
                    int netCode = (isRail(prevTransCode) || isRail(transCode)) ? 2 : 3; // 2: railway, 3: road/bus
                    List<Node> seg = interpolateByTransport(prevStationTN.node, node, netCode);
                    for (int i = 1; i < seg.size() - 1; i++) {
                        out.add(new TransportNode(seg.get(i), curTN.mode));
                    }
                }

                if (notEmpty(routeId) && tripRoutes.add(routeId)) {
                    BUS_ROUTE_STATS.computeIfAbsent(routeId, k -> new AtomicInteger()).incrementAndGet();
                }

                // Station node meta
                JsonNode stationNode = feat.path("properties").path("station");
                String routeIdRaw = (stationNode.isMissingNode() || stationNode.isNull())
                    ? "" : stationNode.path("route").asText("");
                String stationName = (stationNode.isMissingNode() || stationNode.isNull())
                    ? "" : stationNode.path("station_name").asText("");

                String normalizedRoute = normalizeRouteId(routeIdRaw);
                boolean isTransitStation = isStation && notEmpty(normalizedRoute) && !isRouteWalk(normalizedRoute);

                // Boarding event when entering a new transit segment
                if (isTransitStation && (!prevTransit || !normalizedRoute.equals(prevTransitRouteId))) {
                    int hour = parseHourFromStationTimes(stationNode);
                    recordStationBoarding(normalizedRoute, stationName, lon, lat, hour);
                    prevTransit = true;
                    prevTransitRouteId = normalizedRoute;
                } else if (!isTransitStation) {
                    prevTransit = false;
                    prevTransitRouteId = null;
                }

                out.add(curTN);

                if (isStation) {
                    prevStationTN = curTN;
                    prevTransCode = transCode;
                }
            }
            if (out.isEmpty()) throw new IllegalStateException("No nodes parsed");
            return out;
        }

        /** Interpolate shortest path between two nodes by transport network code. */
        private List<Node> interpolateByTransport(Node from, Node to, int code) {
            if (from.equals(to)) return List.of(from, to);
            if (code == 2) { // railway
                LinkCost cost = new LinkCost(Transport.RAILWAY);
                Dijkstra dij = new Dijkstra(cost);
                Route r = dij.getRoute(railway, from.getLon(), from.getLat(), to.getLon(), to.getLat());
                if (r != null && r.numNodes() > 1) return r.listNodes();
            } else { // road (bus)
                Route r = routing.getRoute(drm, from.getLon(), from.getLat(), to.getLon(), to.getLat());
                if (r != null && r.numNodes() > 1) return r.listNodes();
            }
            // fallback: straight segment
            return List.of(from, to);
        }

        /** Parse hour from station's departure_time or arrival_time. */
        private int parseHourFromStationTimes(JsonNode stationNode) {
            if (stationNode == null || stationNode.isNull() || stationNode.isMissingNode()) return -1;
            String dep = stationNode.path("departure_time").asText("");
            String arr = stationNode.path("arrival_time").asText("");
            int h = parseHour(dep);
            if (h < 0) h = parseHour(arr);
            return h;
        }

        /** Support "0834" or "08:34" formats. */
        private int parseHour(String hhmm) {
            if (hhmm == null) return -1;
            String s = hhmm.trim();
            if (s.isEmpty() || "null".equals(s)) return -1;
            try {
                if (s.length() >= 2 && Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))) {
                    return Math.min(23, Math.max(0, Integer.parseInt(s.substring(0, 2))));
                }
            } catch (Exception ignore) { /* ignored */ }
            int idx = s.indexOf(':');
            if (idx >= 1) {
                try {
                    return Math.min(23, Math.max(0, Integer.parseInt(s.substring(0, idx))));
                } catch (Exception ignore) { /* ignored */ }
            }
            return -1;
        }

        /** Safe epoch-seconds (avoid 0 which maps to 1970-01-01). */
        private long safeEpochSec(long sec) {
            return (sec <= 0) ? System.currentTimeMillis() / 1000L : sec;
        }

        /** Distribute timestamps along a path and create SPoints with per-node modes. */
        private void addTimeStampedSubpoints(List<TransportNode> tnList,
                                             long startTime, long endTime,
                                             EPurpose purpose,
                                             List<SPoint> subpoints) {
            long safeStart = startTime > 0 ? startTime : System.currentTimeMillis() / 1000;
            long safeEnd   = endTime   > safeStart ? endTime : safeStart + 30; // at least 30s

            List<Node> nodes = tnList.stream().map(tn -> tn.node).collect(Collectors.toList());

            Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(
                nodes, new Date(safeStart * 1000), new Date(safeEnd * 1000));

            Calendar cal = Calendar.getInstance();
            for (TransportNode tn : tnList) {
                Date d = timeMap.getOrDefault(tn.node, new Date(safeStart * 1000));
                configureCalendar(cal, d);
                SPoint sp = new SPoint(tn.node.getLon(), tn.node.getLat(), cal.getTime(), tn.mode, purpose);
                subpoints.add(sp);
            }
        }

        /** Add a Trip entry for a sub-segment (currentSubtrip) with inferred final mode. */
        private void addTripForSubtrip(List<JsonNode> currentSubtrip, int lastMode, boolean publicTransit, long depTime, Person person, EPurpose purpose) {
            JsonNode ollCoords = currentSubtrip.get(0).path("geometry").path("coordinates");
            JsonNode dllCoords = currentSubtrip.get(currentSubtrip.size() - 1).path("geometry").path("coordinates");

            int firstMode = currentSubtrip.get(0).path("properties").path("transportation").asInt();
            boolean allSame = true, hasMode2 = false, hasMode3 = false;

            for (JsonNode node : currentSubtrip) {
                int transportation = node.path("properties").path("transportation").asInt();
                if (transportation != firstMode) allSame = false;
                if (transportation == 2) hasMode2 = true;
                if (transportation == 3) hasMode3 = true;
            }

            int finalMode = allSame ? firstMode : (hasMode2 ? 2 : (hasMode3 ? 3 : lastMode));
            ETransport mode = getTransport(finalMode);

            if (mode == ETransport.CAR && publicTransit) {
                mode = ETransport.WALK; // avoid CAR for transit segment
            }
            if (ollCoords.isArray() && dllCoords.isArray()) {
                LonLat moll = new LonLat(ollCoords.get(0).asDouble(), ollCoords.get(1).asDouble());
                LonLat mdll = new LonLat(dllCoords.get(0).asDouble(), dllCoords.get(1).asDouble());
                person.addTrip(new Trip(mode, purpose, depTime, moll, mdll));
            } else {
                System.out.println("No coordinate from API!");
            }
        }

        /** Process WebAPI features: group by mode changes and create Trip entries. */
        private void processMixedTransportFeatures(JsonNode[] holder, long depTime, Person person, EPurpose purpose) {
            JsonNode features = holder[0].path("features");
            boolean publicTransit = holder[0].path("num_station").asInt() > 0;

            List<JsonNode> current = new ArrayList<>();
            int lastMode = -1;
            long currentTime = safeEpochSec(depTime);
            JsonNode prevNode = null;

            for (JsonNode feat : features) {
                int curMode = feat.path("properties").path("transportation").asInt();

                if (lastMode == -1) lastMode = curMode;

                // On mode change -> close previous segment
                if (curMode != lastMode && !current.isEmpty()) {
                    long step = (lastMode == 1) ? 300 : holder[0].path("total_transport_time").asLong() * 60;
                    currentTime += step;
                    addTripForSubtrip(current, lastMode, publicTransit, currentTime, person, purpose);
                    current = new ArrayList<>();
                }

                if (prevNode != null && current.isEmpty()) current.add(prevNode);
                current.add(feat);

                lastMode = curMode;
                prevNode = feat;
            }

            // Close tail segment
            if (!current.isEmpty()) {
                long step = (lastMode == 1) ? 300 : holder[0].path("total_transport_time").asLong() * 60;
                currentTime += step;
                addTripForSubtrip(current, lastMode, publicTransit, currentTime, person, purpose);
            }
        }

        public String convertSecondsToHHMM(double totalSeconds) {
            int hours = (int) (totalSeconds / 3600);
            int minutes = (int) ((totalSeconds % 3600) / 60);
            return String.format("%02d%02d", hours, minutes);
        }

        private Map<String, String> buildMixedParams(GLonLat oll, GLonLat dll, long startTime) {
            Map<String, String> params = new HashMap<>();
            params.put("UnitTypeCode", "2");
            params.put("StartLongitude", String.valueOf(oll.getLon()));
            params.put("StartLatitude", String.valueOf(oll.getLat()));
            params.put("GoalLongitude", String.valueOf(dll.getLon()));
            params.put("GoalLatitude", String.valueOf(dll.getLat()));

            Map<String, String> mixedparams = new HashMap<>(params);
            mixedparams.put("TransportCode", "3"); // fixed as original
            mixedparams.put("AppDate", "20240401");
            mixedparams.put("AppTime", convertSecondsToHHMM(startTime));
            return mixedparams;
        }

        private int process(Person person) {
            List<SPoint> points = new ArrayList<>();
            List<Activity> activities = person.getActivities();
            Activity pre = activities.get(0);

            try {
                if (activities.size() == 1) {
                    // Stay home all day
                    person.addTrip(new Trip(ETransport.NOT_DEFINED, EPurpose.HOME, 0, pre.getLocation(), pre.getLocation()));

                    Calendar cl = Calendar.getInstance();
                    Date startDate = new Date(0);
                    configureCalendar(cl, startDate);
                    points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), cl.getTime(), ETransport.NOT_DEFINED, EPurpose.HOME));
                    Date endDate = new Date(86399000);
                    configureCalendar(cl, endDate);
                    points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), cl.getTime(), ETransport.NOT_DEFINED, EPurpose.HOME));
                    person.addTrajectory(points);
                } else {
                    for (int i = 1; i < activities.size(); i++) {
                        List<SPoint> subpoints = new ArrayList<>();
                        Activity next = activities.get(i);
                        GLonLat oll = pre.getLocation();
                        GLonLat dll = next.getLocation();

                        long startTime = next.getStartTime();
                        long endTime = startTime;

                        EPurpose purpose = next.getPurpose();
                        double distance = DistanceUtils.distance(oll, dll);

                        if (distance > 0) {
                            Map<String, String> mixedparams = buildMixedParams(oll, dll, startTime);
                            JsonNode[] mixedResultsHolder = new JsonNode[1];

                            Route route = routing.getRoute(drm, oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
                            ETransport nextMode = determineTransportMode(person, distance, route, mixedparams, mixedResultsHolder);

                            int multiplier = calculateMultiplier(nextMode);
                            long travelTime;

                            if (nextMode == ETransport.WALK || nextMode == ETransport.BICYCLE || nextMode == ETransport.CAR) {
                                travelTime = calculateTravelTime(route, multiplier);
                                endTime += travelTime;

                                List<Node> nodes = route.listNodes();
                                Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(
                                    nodes, new Date(startTime * 1000), new Date(endTime * 1000));
                                addSubpoints(nodes, timeMap, nextMode, purpose, subpoints);

                                List<Link> links = route.listLinks();
                                assignLinksToSubpoints(subpoints, links);
                                points.addAll(subpoints);

                                long depTime = next.getStartTime() - travelTime;
                                person.addTrip(new Trip(nextMode, purpose, depTime, oll, dll));
                            } else if (nextMode == ETransport.MIX) {
                                if (mixedResultsHolder[0].path("features").get(0) == null) {
                                    System.out.println("Empty mixed results!");
                                }
                                handleMixedTransport(nextMode, purpose, mixedResultsHolder, subpoints, points, person, next, route, startTime, endTime, new LonLat(oll.getLon(), oll.getLat()), new LonLat(dll.getLon(), dll.getLat()));
                            } else {
                                // Not defined
                                person.addTrip(new Trip(ETransport.NOT_DEFINED, next.getPurpose(), next.getStartTime(), pre.getLocation(), pre.getLocation()));
                                Calendar cl = Calendar.getInstance();
                                Date startDate = new Date(next.getStartTime());
                                configureCalendar(cl, startDate);
                                points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), cl.getTime(), ETransport.NOT_DEFINED, EPurpose.HOME));
                                Date endDate = new Date(next.getStartTime() + 300);
                                configureCalendar(cl, endDate);
                                points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), cl.getTime(), ETransport.NOT_DEFINED, EPurpose.HOME));
                                person.addTrajectory(points);
                            }
                        } else {
                            // Zero-distance activity
                            person.addTrip(new Trip(ETransport.NOT_DEFINED, next.getPurpose(), next.getStartTime(), pre.getLocation(), pre.getLocation()));
                            Calendar cl = Calendar.getInstance();
                            Date startDate = new Date(next.getStartTime());
                            configureCalendar(cl, startDate);
                            points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), cl.getTime(), ETransport.NOT_DEFINED, next.getPurpose()));
                            person.addTrajectory(points);
                        }
                        pre = next;
                    }
                }
                person.addTrajectory(points);
            } catch (Exception e) {
                System.err.println("Exception at person: " + person);
                e.printStackTrace();
            }
            return 0;
        }

        @Override
        public Integer call() {
            try {
                for (Person p : listAgents) {
                    int res = process(p);
                    if (res < 0) this.error++;
                    this.total++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    /* ===========================
     * Batch generation
     * =========================== */

    public void generate(List<Person> agents) {
        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("NumOfThreads: " + numThreads);

        List<Callable<Integer>> listTasks = new ArrayList<>();
        int listSize = agents.size();
        int taskNum = numThreads;
        int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
        for (int i = 0; i < listSize; i += stepSize) {
            int end = Math.min(listSize, i + stepSize);
            List<Person> subList = agents.subList(i, end);
            listTasks.add(new TripTask(i, subList));
        }
        System.out.println("NumOfTasks: " + listTasks.size());

        ExecutorService es = Executors.newFixedThreadPool(numThreads);
        try {
            es.invokeAll(listTasks);
        } catch (Exception exp) {
            exp.printStackTrace();
        } finally {
            es.shutdown();
        }
    }

    /* ===========================
     * Properties
     * =========================== */

    private static void loadProperties() throws Exception {
        try (InputStream inputStream = TripGenerator_WebAPI_refactor_Kobe.class
            .getClassLoader().getResourceAsStream("config.properties")) {
            if (inputStream == null) {
                throw new FileNotFoundException("config.properties file not found in the classpath");
            }
            prop = new Properties();
            prop.load(inputStream);
        }
    }

    /* ===========================
     * Main
     * =========================== */

    public static void main(String[] args) throws Exception {
        loadProperties();

        String root = prop.getProperty("root");
        String inputDir = prop.getProperty("inputDir");
        System.out.println("Root Directory: " + root);
        System.out.println("Input Directory: " + inputDir);

        int mfactor = 1;
        Country japan = new Country();

        // Load network data
        String railFile = String.format("%srailnetwork.tsv", inputDir + "/network/");
        Network railway = RailLoader.load(railFile);

        String cityFile = String.format("%scity_boundary.csv", inputDir);
        DataAccessor.loadCityData(cityFile, japan);

        String stationFile = String.format("%sbase_station.csv", inputDir);
        Network station = DataAccessor.loadLocationData(stationFile);
        japan.setStation(station);

        String outputDir = "D:/large/PseudoPFLOW/";

        ArrayList<Integer> prefectureCodes = new ArrayList<>(Arrays.asList(
            28
        ));

        for (int i : prefectureCodes) {
            File tripDir = new File(outputDir + "trip/", String.valueOf(i));
            File trajDir = new File(outputDir + "trajectory/", String.valueOf(i));
            System.out.println("Start prefecture: " + i + " " + tripDir.mkdirs() + " " + trajDir.mkdirs());

            String roadFile = String.format("%sdrm_%02d.tsv", inputDir + "/network/", i);
            Network road = DrmLoader.load(roadFile);
            Double carRatio = Double.parseDouble(prop.getProperty("car." + i));
            Double bikeRatio = Double.parseDouble(prop.getProperty("bike." + i));

            File actDir = new File(String.format("%s/activity_v2/", root), String.valueOf(i));
            for (File file : Objects.requireNonNull(actDir.listFiles())) {
                if (!file.getName().endsWith(".csv")) continue;

                String tripFileName = outputDir + "trip/" + i + "/trip_" + file.getName().substring(9, 14) + ".csv";
                String trajectoryFileName = outputDir + "trajectory/" + i + "/trajectory_" + file.getName().substring(9, 14) + ".csv";

                // Keep this filter as in original code
                if (!file.getName().equals("Kobe_bus_act.csv")) {
                    continue;
                }

                long starttime = System.currentTimeMillis();
                TripGenerator_WebAPI_refactor_Kobe worker = new TripGenerator_WebAPI_refactor_Kobe(japan, road, railway);
                System.out.println("Start to load activities...");
                List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, carRatio, bikeRatio);
                System.out.printf("%s%n", file.getName());
                worker.generate(agents);
                PersonAccessor.writeTrips(tripFileName, agents);
                PersonAccessor.writeTrajectory(trajectoryFileName, agents);

                // Stats output
                getBusRouteStats();
                printBusRouteStats();
                Path out1 = Paths.get(outputDir + "trajectory/" + "large_hiyodori_bus_usage_stats.csv");
                exportBusRouteStatsToCSV(out1);
                Path out2 = Paths.get(outputDir + "trajectory/" + "large_hiyodori_bus_station_usage_stats.csv");
                exportRouteStationBoardingsHourlyToCSV(out2);

                long endtime = System.currentTimeMillis();
                System.out.println(file.getName() + ": " + (endtime - starttime));
            }
        }
        System.out.println("end");
    }
}
