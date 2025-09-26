package pseudo.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.logic.transport.ITransport;
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
import org.jboss.netty.util.internal.ThreadLocalRandom;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TripGenerator_WebAPI_refactor_Kobe {

    private static class TransportNode {
        final Node node;
        final ETransport mode;
        TransportNode(Node node, ETransport mode) {
            this.node = node;
            this.mode = mode;
        }
    }


    private final Network drm;
	private final Network railway;

	private final SSLContext sslContext;
	private final PoolingHttpClientConnectionManager connManager;
	private final CloseableHttpClient httpClient;
	private final String sessionId;

	private static final double MIN_TRANSIT_DISTANCE = 1000;
	// private static final double MAX_SEARCH_STATION_DISTANCE = 5000;
	private static final double FARE_PER_KILOMETER = 51; // Japanese yen, only for vehicle
	private static final double FARE_PER_HOUR = 1000; // Japanese yen, all modes, possible to extend to prefecture level
	private static final double FATIGUE_INDEX_WALK = 2.5;
	private static final double FATIGUE_INDEX_BICYCLE = 1.5;
	private static final double FARE_INIT = 250; // Japanese yen, only for vehicle
	private static final double CAR_AVAILABILITY = 0.4; // Parameter for explain people using car without ownership

    // ==================== 全局: Bus Route 统计 ============================
    private static final ConcurrentHashMap<String, AtomicInteger> BUS_ROUTE_STATS = new ConcurrentHashMap<>();
    // 统计：每个车站（名+坐标）在各小时(0-23)的上车次数
    // 使用 int[24] 保存 24 小时的计数
    private static final java.util.concurrent.ConcurrentHashMap<String, int[]> STATION_BOARDINGS_HOURLY = new java.util.concurrent.ConcurrentHashMap<>();


    public static Map<String,Integer> getBusRouteStats() {
        Map<String,Integer> copy = new HashMap<>();
        BUS_ROUTE_STATS.forEach((k,v) -> copy.put(k, v.get()));
        return Collections.unmodifiableMap(copy);
    }
    public static void clearBusRouteStats() { BUS_ROUTE_STATS.clear(); }
    public static void printBusRouteStats() {
        System.out.println("=== Bus Route Usage Stats ===");
        getBusRouteStatsSorted().forEach(e -> System.out.println(e.getKey() + " : " + e.getValue()));
    }
    /**
     * 降序排序后的只读列表 (routeId, count)
     */
    public static List<Map.Entry<String,Integer>> getBusRouteStatsSorted() {
        List<Map.Entry<String,Integer>> list = new ArrayList<>();
        BUS_ROUTE_STATS.forEach((k,v) -> list.add(Map.entry(k, v.get())));
        list.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        return Collections.unmodifiableList(list);
    }
    /**
     * 生成 CSV 字符串 (含 header)
     */
    public static String busRouteStatsToCSV() {
        StringBuilder sb = new StringBuilder();
        sb.append("routeId,count\n");
        for (Map.Entry<String,Integer> e : getBusRouteStatsSorted()) {
            sb.append(e.getKey()).append(',').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }
    /**
     * 将统计结果写入 CSV 文件；若文件已存在则覆盖。
     */
    public static void exportBusRouteStatsToCSV(Path file) throws IOException {
        Files.writeString(file, busRouteStatsToCSV(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    private static final ConcurrentHashMap<String, int[]> ROUTE_STATION_BOARDINGS_HOURLY = new ConcurrentHashMap<>();


    /* ------------- 小工具 ------------- */
    private static boolean notEmpty(String s) { return s != null && !s.isEmpty() && !"null".equals(s); }
    private static boolean isRail(int c) { return c == 2 || c == 4; }
    private static boolean isRouteWalk(String routeId) {
        String r = routeId == null ? "" : routeId.trim();
        return r.equals("徒歩") || r.equalsIgnoreCase("walk");
    }
    private static String normalizeRouteId(String s) { return (s == null || "null".equals(s)) ? "" : s.trim(); }


    /* ------------- 主循环补间/统计 (节选) 中调用的记录函数 ------------- */
    private static void recordStationBoarding(String routeId, String stationName, double lon, double lat, int hour) {
        if (!notEmpty(routeId) || isRouteWalk(routeId)) return;
        if (hour < 0 || hour > 23) hour = 0;
        String key = routeId + "||" + stationKey(stationName, lon, lat);
        final int h = hour;
        ROUTE_STATION_BOARDINGS_HOURLY.compute(key, (k, arr) -> {
            if (arr == null) arr = new int[24];
            arr[h] += 1; return arr;
        });
    }


    private static String stationKey(String name, double lon, double lat) {
        return (name == null ? "" : name) + "|" + String.format(java.util.Locale.US, "%.6f", lon) + "," + String.format(java.util.Locale.US, "%.6f", lat);
    }


    // ========== 通用 CSV 工具：UTF-8 + BOM，CRLF，字段总是加引号 ==========
    private static java.io.BufferedWriter openCsvUtf8BomWriter(java.nio.file.Path file) throws java.io.IOException {
        java.io.OutputStream os = java.nio.file.Files.newOutputStream(
            file,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        // 写 BOM
        os.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
        return new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void csvRow(java.io.BufferedWriter w, String... fields) throws java.io.IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) w.write(',');
            String f = fields[i] == null ? "" : fields[i];
            // 总是加引号并转义内部引号
            w.write('"');
            w.write(f.replace("\"", "\"\""));
            w.write('"');
        }
        w.write("\r\n"); // 固定 CRLF
    }

    // ========== 1) 路线×站点×小时×人次（按 路线→小时→站名 排序） ==========
    public static void exportRouteStationBoardingsHourlyToCSV(java.nio.file.Path file) throws java.io.IOException {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (var e : ROUTE_STATION_BOARDINGS_HOURLY.entrySet()) {
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
        rows.sort((a,b) -> {
            int r = a[0].compareTo(b[0]);                     // routeId
            if (r != 0) return r;
            r = Integer.compare(Integer.parseInt(a[4]), Integer.parseInt(b[4])); // hour
            if (r != 0) return r;
            return a[1].compareTo(b[1]);                      // station_name
        });

        try (var w = openCsvUtf8BomWriter(file)) {
            csvRow(w, "routeId", "station_name", "lon", "lat", "hour", "count");
            for (String[] row : rows) csvRow(w, row);
        }
    }

    // ========== 2) 仅“站点×小时×人次”（如果你还在用） ==========
    public static void exportStationBoardingsHourlyToCSV(java.nio.file.Path file) throws java.io.IOException {
        try (var w = openCsvUtf8BomWriter(file)) {
            csvRow(w, "station_key", "station_name", "lon", "lat", "hour", "count");
            for (var e : STATION_BOARDINGS_HOURLY.entrySet()) {
                String key = e.getKey();                        // stationName|lon,lat
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



    /* ------------- 重置 ------------- */
    public static void resetRouteStationBoardingsHourly() { ROUTE_STATION_BOARDINGS_HOURLY.clear(); }



    public TripGenerator_WebAPI_refactor_Kobe(Country japan, Network drm, Network railway) throws Exception {
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
		SSLContext sslContext;
		try {
			sslContext = SSLContextBuilder.create()
					.loadTrustMaterial(new TrustSelfSignedStrategy())
					.build();
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize SSL context", e);
		}

		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
				sslContext,
				new String[]{"TLSv1.2", "TLSv1.3"},
				null,
				NoopHostnameVerifier.INSTANCE);

		PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(
				RegistryBuilder.<ConnectionSocketFactory>create()
						.register("https", sslSocketFactory)
						.register("http", PlainConnectionSocketFactory.INSTANCE)
						.build());

		connManager.setMaxTotal(32); // Adjust based on your expected total number of concurrent connections
		connManager.setDefaultMaxPerRoute(100); // Adjust per route limits based on your API and use case

		return connManager;
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

	public String createSession() throws Exception{

		HttpPost createSessionPost = new HttpPost(prop.getProperty("api.createSessionURL"));

		List<NameValuePair> sessionParams = new ArrayList<>();
		sessionParams.add(new BasicNameValuePair("UserID", prop.getProperty("api.userID")));
		sessionParams.add(new BasicNameValuePair("Password", prop.getProperty("api.password")));
		createSessionPost.setEntity(new UrlEncodedFormEntity(sessionParams));

		HttpResponse sessionResponse = executePostRequest(this.httpClient, createSessionPost);
		if (sessionResponse.getStatusLine().getStatusCode() == 200) {
			String sessionResponseBody = EntityUtils.toString(sessionResponse.getEntity());
			System.out.println("Session created successfully");
			System.out.println(sessionResponseBody);
			return sessionResponseBody.split(",")[1].trim().replace("\r", "").replace("\n", "");
		} else {
			System.out.println("Failed to create session: " + sessionResponse.getStatusLine().getStatusCode());
			return "";
		}
	}
	
	protected synchronized double getRandom() {
		return ThreadLocalRandom.current().nextDouble();
	}
	
	private class TripTask implements Callable<Integer> {
		private int id;
		private final List<Person> listAgents;
		private int error;
		private int total;
		LinkCost linkCost = new LinkCost();
		Dijkstra routing = new Dijkstra(linkCost);

		public TripTask(int id, List<Person> listAgents){
			this.id = id;
			this.listAgents = listAgents;
			this.total = error = 0;
		}	
		
		private EPurpose convertHomeMode(ELabor labor) {
			switch(labor) {
			case WORKER:
				return EPurpose.OFFICE;
			case JOBLESS:
			case NO_LABOR:
			case UNDEFINED:
			case INFANT:
				return EPurpose.FREE;
			case PRE_SCHOOL:
			case PRIMARY_SCHOOL:
			case SECONDARY_SCHOOL:
			case HIGH_SCHOOL:
			case COLLEGE:
			case JUNIOR_COLLEGE:
			default:
				return EPurpose.SCHOOL;
			}
		}

		private ETransport getTransport(int mode) { // mode defined by WebAPI
			switch (mode) {
				case 4:		return ETransport.WALK;
				case 3:	return ETransport.BUS;
                case 2:		return ETransport.TRAIN;
				case 0: return ETransport.NOT_DEFINED;
				default:		return ETransport.CAR;
			}
		}

		private int calculateMultiplier(ETransport mode) {
			switch (mode) {
				case WALK: return 6;
				case BICYCLE: return 3;
				case CAR: return 1;
				default: return 1;
			}
		}

		private void configureCalendar(Calendar calendar, Date date) {
			TimeZone timeZone = TimeZone.getTimeZone("Asia/Tokyo");
			calendar.setTime(date);
			calendar.setTimeZone(timeZone);
			calendar.add(Calendar.MILLISECOND, -timeZone.getOffset(calendar.getTimeInMillis()));
			calendar.add(Calendar.YEAR, 45);
			calendar.add(Calendar.MONTH, 9);
		}

		private ETransport determineTransportMode(Person person, double distance, Route route, Map<String, String> mixedparams, JsonNode[] mixedResultsHolder){
			ETransport nextMode;

			Map<ETransport, Double> choices = new LinkedHashMap<>();

			if(route!=null){
				double roadtime = route.getCost(); // seconds
				double roadfare = FARE_INIT + route.getLength() / 1000 * FARE_PER_KILOMETER; // length in meters, 150 as initial cost to avoid short distance car travel
				double roadcost = roadfare + roadtime / 3600 * FARE_PER_HOUR;
				if(person.hasCar() || getRandom() < CAR_AVAILABILITY){
					choices.put(ETransport.CAR, roadcost);
				}

				double walktime = route.getLength() / 1.38;
				double walkcost = walktime / 3600 * FARE_PER_HOUR * FATIGUE_INDEX_WALK;
				choices.put(ETransport.WALK, walkcost);

				if(person.hasBike()){
					double biketime = walktime / 2;
					double bikecost = biketime / 3600 * FARE_PER_HOUR * FATIGUE_INDEX_BICYCLE;
					choices.put(ETransport.BICYCLE, bikecost);
				}
			}

			if(distance>MIN_TRANSIT_DISTANCE){
				mixedResultsHolder[0] = getMixedRoute(httpClient, sessionId, mixedparams);
				boolean publicTransit = mixedResultsHolder[0].path("num_station").asInt() > 0 && mixedResultsHolder[0].path("fare").asInt() > 0;
				if (publicTransit) {
					double mixedfare = mixedResultsHolder[0].get("fare").asDouble();
					double mixedtime = mixedResultsHolder[0].get("total_time").asDouble(); // Travel time from WebAPI is in minute
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

		// Methods to refactor and modularize the code
		private long calculateTravelTime(Route route, int multiplier) {
			if (route != null) {
				return (long) route.getCost() * multiplier;
			} else {
				return 3600L;
			}
		}

		private void addSubpoints(List<Node> nodes, Map<Node, Date> timeMap, ETransport nextMode, EPurpose purpose, List<SPoint> subpoints) {
			for (ILonLat node : nodes) {
				Date date = timeMap.get(node);
				Calendar cl = Calendar.getInstance();
				configureCalendar(cl, date);
				date = cl.getTime();
				SPoint point = new SPoint(node.getLon(), node.getLat(), date, nextMode, purpose);
				subpoints.add(point);
			}
		}

		private void assignLinksToSubpoints(List<SPoint> subpoints, List<Link> links) {
			for (int k = 1; k <= links.size(); k++) {
				subpoints.get(k).setLink(links.get(k - 1).getLinkID());
			}
		}

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

            // ---------- 1. 计算时间窗 ----------
            long travelTimeSec = Math.max(1L, root.path("total_time").asLong() * 60L);
            long depTime = next.getStartTime() - travelTimeSec;
            long nowSec = System.currentTimeMillis() / 1000L;
            long segStart = depTime > 0 ? depTime : (startTimeParam > 0 ? startTimeParam : nowSec);
            long segEnd   = segStart + travelTimeSec;

            // ---------- 2. 解析节点（含铁路 & Bus 补间） ----------
            List<TransportNode> tnList = extractTransportNodesFromMixedResults(mixedResultsHolder);

            // ---------- 3. 切分子行程并写 Trip ----------
            processMixedTransportFeatures(mixedResultsHolder, depTime, person, purpose);

            // ---------- 4. 生成带时间戳 SPoint ----------
            addTimeStampedSubpoints(tnList, segStart, segEnd, purpose, subpoints);
            points.addAll(subpoints);
        }


//		private List<Node> extractNodesFromMixedResults(JsonNode[] mixedResultsHolder) {
//			List<Node> nodes = new ArrayList<>();
//			JsonNode routeData = mixedResultsHolder[0].path("features");
//			for (JsonNode feature : routeData) {
//				JsonNode coordinates = feature.path("geometry").path("coordinates");
//				String stationName =   feature.path("properties").path("station").toString();
//				if (coordinates.isArray()) {
//					double lon = coordinates.get(0).asDouble();
//					double lat = coordinates.get(1).asDouble();
//					nodes.add(new Node(feature.path("properties").path("id").toString(), lon, lat));
//				} else {
//					System.out.println("Coordinate from WebAPI is not an array!!");
//				}
//			}
//			return nodes;
//		}

        // ---------- 文本与路线上车判定 ----------

        private boolean isRouteWalk(String routeId) {
            String r = routeId == null ? "" : routeId.trim();
            return r.equals("徒歩") || r.equalsIgnoreCase("walk");
        }
        private String normalizeRouteId(String s) {
            if (s == null || "null".equals(s)) return "";
            return s.trim();
        }

        // ---------- 解析 station 的 hour ----------
        private int parseHourFromStationTimes(JsonNode stationNode) {
            if (stationNode == null || stationNode.isNull() || stationNode.isMissingNode()) return -1;
            // 优先 departure_time，其次 arrival_time
            String dep = stationNode.path("departure_time").asText("");
            String arr = stationNode.path("arrival_time").asText("");
            int h = parseHour(dep);
            if (h < 0) h = parseHour(arr);
            return h; // -1 表示未知
        }
        private int parseHour(String hhmm) {
            if (hhmm == null) return -1;
            String s = hhmm.trim();
            if (s.isEmpty() || "null".equals(s)) return -1;
            // 支持 "0834" 或 "08:34"
            try {
                if (s.length() >= 2 && Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))) {
                    return Math.min(23, Math.max(0, Integer.parseInt(s.substring(0, 2))));
                }
            } catch (Exception ignore) {}
            // 带冒号的格式
            int idx = s.indexOf(':');
            if (idx >= 1) {
                try {
                    return Math.min(23, Math.max(0, Integer.parseInt(s.substring(0, idx))));
                } catch (Exception ignore) {}
            }
            return -1;
        }



        // key：用 站名 + 6 位小数的坐标，降低重名歧义
        private String stationKey(String name, double lon, double lat) {
            return (name == null ? "" : name) + "|" + round6(lon) + "," + round6(lat);
        }
        private String round6(double x) {
            return String.format(java.util.Locale.US, "%.6f", x);
        }


        /* ------------- 主函数 ------------- */
        private List<TransportNode> extractTransportNodesFromMixedResults(JsonNode[] holder) {
            List<TransportNode> out = new ArrayList<>();
            if (holder == null || holder.length == 0) return out;
            JsonNode features = holder[0].path("features");
            if (features == null || !features.isArray()) return out;

            Set<String> tripRoutes = new HashSet<>();

            TransportNode prevStationTN = null;
            int            prevCode     = -1;
            String         prevRouteId  = null;

            int           prevTransCode = -1;

            boolean prevTransit = false;         // 上一个“站点”是否处于公共交通段
            String  prevTransitRouteId = null;   // 上一个公共交通段的 routeId（用于分段去重）


            for (JsonNode feat : features) {

                JsonNode coords = feat.path("geometry").path("coordinates");
                if (!coords.isArray()) continue;

                int  transCode = feat.path("properties").path("transportation").asInt();
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


                /* ================= 补间：前后都是 station ================= */
                if (isStation && prevStationTN != null) {

                    int netCode = (isRail(prevTransCode) || isRail(transCode)) ? 2 : 3; // Rail or Road
                    List<Node> seg = interpolateByTransport(prevStationTN.node, node, netCode);

                    // 插入 seg 中间点（首尾已经在 out / 将被添加）
                    for (int i = 1; i < seg.size() - 1; i++) {
                        out.add(new TransportNode(seg.get(i), curTN.mode));
                    }
                }

                if (notEmpty(routeId) && tripRoutes.add(routeId)) {
                    BUS_ROUTE_STATS.computeIfAbsent(routeId, k -> new AtomicInteger()).incrementAndGet();
                }

                // 站点对象
                JsonNode stationNode = feat.path("properties").path("station");

                // 当前站点的路线 ID（可能不存在）
                String routeIdRaw = (stationNode.isMissingNode() || stationNode.isNull())
                    ? "" : stationNode.path("route").asText("");

                // 站名（用于生成统计 key）
                String stationName = (stationNode.isMissingNode() || stationNode.isNull())
                    ? "" : stationNode.path("station_name").asText("");

                // “该站点是用于乘坐公共交通”的判定：有 route 且不是“徒歩/walk”
                routeId = normalizeRouteId(routeIdRaw);
                boolean isTransitStation = isStation && notEmpty(routeId) && !isRouteWalk(routeId);

                // —— 上车事件触发条件：进入一个新的公共交通段（或从非公共交通切换到公共交通）
                if (isTransitStation && (!prevTransit || !routeId.equals(prevTransitRouteId))) {
                    int hour = parseHourFromStationTimes(stationNode);  // 优先用 departure_time，否则 arrival_time
                    recordStationBoarding(routeId, stationName, lon, lat, hour);
                    prevTransit = true;
                    prevTransitRouteId = routeId;
                } else if (!isTransitStation) {
                    // 非公共交通站点（或没有 route）会重置段状态
                    prevTransit = false;
                    prevTransitRouteId = null;
                }


                /* ---- 写入当前节点 ---- */
                out.add(curTN);

                /* ---- 若当前是 station，则更新 prev* ---- */
                if (isStation) {
                    prevStationTN = curTN;
                    prevTransCode = transCode;
                }
            }
            if (out.isEmpty()) throw new IllegalStateException("No nodes parsed");
            return out;
        }

        public void exportStationBoardingsHourlyToCSV(java.nio.file.Path file) throws java.io.IOException {
            try (java.io.BufferedWriter w = java.nio.file.Files.newBufferedWriter(file)) {
                w.write("station_key,station_name,lon,lat,hour,count\n");
                for (var e : STATION_BOARDINGS_HOURLY.entrySet()) {
                    String key = e.getKey();
                    int[] bins = e.getValue();
                    // 解析 key
                    String[] parts = key.split("\\|", 2);
                    String stationName = parts.length > 0 ? parts[0] : "";
                    String lonlat = parts.length > 1 ? parts[1] : "0,0";
                    String[] ll = lonlat.split(",", 2);
                    String lon = ll.length > 0 ? ll[0] : "0";
                    String lat = ll.length > 1 ? ll[1] : "0";

                    for (int h = 0; h < 24; h++) {
                        int c = bins[h];
                        if (c == 0) continue;
                        w.write(String.format(java.util.Locale.US,
                            "%s,%s,%s,%s,%d,%d%n",
                            key, stationName.replace(',', ' '), lon, lat, h, c));
                    }
                }
            }
        }

        // 清空统计
        public void resetStationBoardingsHourly() {
            STATION_BOARDINGS_HOURLY.clear();
        }



        /* ------------- 小工具 ------------- */
        private boolean notEmpty(String s) { return s != null && !s.isEmpty() && !"null".equals(s); }
        private boolean isRail(int c)     { return c == 2 || c == 4; }

        /* ------------- 最短路插值 ------------- */
        private List<Node> interpolateByTransport(Node from, Node to, int code) {
            if (from.equals(to)) return List.of(from, to);
            LinkCost cost;
            if (code == 2) { // railway
                cost = new LinkCost(Transport.RAILWAY);
                Dijkstra dij = new Dijkstra(cost);
                Route r = dij.getRoute(railway, from.getLon(), from.getLat(), to.getLon(), to.getLat());
                if (r != null && r.numNodes() > 1) return r.listNodes();
            } else {        // road network (bus)
                Route r = routing.getRoute(drm, from.getLon(), from.getLat(), to.getLon(), to.getLat());
                if (r != null && r.numNodes() > 1) return r.listNodes();
            }
            // fallback: straight‑line two points
            return List.of(from, to);
        }



        private int determineInitialTransportMode(JsonNode[] mixedResultsHolder) {
			return mixedResultsHolder[0].path("features").get(0).path("properties").path("transportation").asInt();
		}

        private void processMixedTransportFeatures(JsonNode[] holder,
                                                   long depTime,
                                                   Person person,
                                                   EPurpose purpose) {

            JsonNode features = holder[0].path("features");
            boolean publicTransit = holder[0].path("num_station").asInt() > 0;

            List<JsonNode> current = new ArrayList<>();
            int  lastMode   = -1;
            long currentTime = safeEpochSec(depTime);
            JsonNode prevNode = null;

            for (JsonNode feat : features) {
                int curMode = feat.path("properties").path("transportation").asInt();

                // 首次初始化 lastMode
                if (lastMode == -1) lastMode = curMode;

                // ① 交通方式切换 → 结束上一子段
                if (curMode != lastMode && current.size() > 0) {
                    // 时间推进：简化为线路总传输时间 / walk penalty
                    long step = (lastMode == 1) ? 300 : holder[0].path("total_transport_time").asLong()*60;
                    currentTime += step;
                    addTripForSubtrip(current, lastMode, publicTransit, currentTime, person, purpose);
                    current = new ArrayList<>();
                }

                // ② 组装当前子段
                if (prevNode != null && current.isEmpty()) current.add(prevNode);
                current.add(feat);

                // 更新状态
                lastMode = curMode;
                prevNode = feat;
            }

            // ③ 收尾：处理最后一段
            if (!current.isEmpty()) {
                long step = (lastMode == 1) ? 300 : holder[0].path("total_transport_time").asLong()*60;
                currentTime += step;
                addTripForSubtrip(current, lastMode, publicTransit, currentTime, person, purpose);
            }
        }

		private void addTripForSubtrip(List<JsonNode> currentSubtrip, int lastMode, boolean publicTransit, long depTime, Person person, EPurpose purpose) {
			JsonNode ollCoords = currentSubtrip.get(0).path("geometry").path("coordinates");
			JsonNode dllCoords = currentSubtrip.get(currentSubtrip.size() - 1).path("geometry").path("coordinates");
//			ETransport mode = getTransport(currentSubtrip.get(1).path("properties").path("transportation").asInt());

			// 2025-02-20 feature railway interpolation with 1.2 algorithm
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
				mode = ETransport.WALK;
			}
			if (ollCoords.isArray() && dllCoords.isArray()) {
				LonLat moll = new LonLat(ollCoords.get(0).asDouble(), ollCoords.get(1).asDouble());
				LonLat mdll = new LonLat(dllCoords.get(0).asDouble(), dllCoords.get(1).asDouble());
				// depTime += (long) (DistanceUtils.distance(moll, mdll) / getTravelSpeed(mode.getId()));
				person.addTrip(new Trip(mode, purpose, depTime, moll, mdll));
			} else {
				System.out.println("No coordinate from API!");
			}
		}

        private class TransportNode {
            final Node node;
            final ETransport mode;
            TransportNode(Node n, ETransport m) { this.node = n; this.mode = m; }
        }

        /************************************
         * 安全 epoch‑seconds 工具：确保 >=1
         */
        private long safeEpochSec(long sec) {
            if (sec <= 0) return System.currentTimeMillis()/1000L;
            return sec;
        }

        private static final long DEFAULT_SEG_SEC = 15;   // 单点段缺省持续时间

        private void addTimeStampedSubpoints(List<TransportNode> tnList,
                                             long startTime, long endTime,
                                             EPurpose purpose,
                                             List<SPoint> subpoints) {
            // --- 防御性校正，避免出现 1970‑01‑01 ---
            long safeStart = startTime > 0 ? startTime : System.currentTimeMillis() / 1000;
            long safeEnd   = endTime   > safeStart ? endTime : safeStart + 30; // 至少 30s 间隔

            // 1. 还原成纯 Node 列表以复用原有插值逻辑
            List<Node> nodes = tnList.stream()
                .map(tn -> tn.node)
                .collect(Collectors.toList());

            // 2. 调用 TrajectoryUtils 按路径长度进行线性插值
            Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(
                nodes,
                new Date(safeStart * 1000),
                new Date(safeEnd   * 1000));

            // 3. 为每个节点生成带个体交通方式的 SPoint
            Calendar cal = Calendar.getInstance();
            for (TransportNode tn : tnList) {
                Date d = timeMap.get(tn.node);
                if (d == null) d = new Date(safeStart * 1000); // 兜底
                configureCalendar(cal, d);
                d = cal.getTime();
                SPoint sp = new SPoint(tn.node.getLon(), tn.node.getLat(), d, tn.mode, purpose);
                subpoints.add(sp);
            }
        }

        public String convertSecondsToHHMM(double totalSeconds) {
			// Calculate hours and minutes
			int hours = (int) (totalSeconds / 3600);
			int minutes = (int) ((totalSeconds % 3600) / 60);

			// Format hours and minutes to HHMM as WebAPI requests
			return String.format("%02d%02d", hours, minutes);
		}

		private int process(Person person) {
			List<SPoint> points = new ArrayList<>();

			List<Activity> activities = person.getActivities();
			Activity pre = activities.get(0);

			try {
				if (activities.size() == 1) {
					person.addTrip(new Trip(ETransport.NOT_DEFINED, EPurpose.HOME, 0, pre.getLocation(), pre.getLocation()));

					Calendar cl = Calendar.getInstance();
					Date startDate = new Date(0);
					configureCalendar(cl, startDate);
					startDate = cl.getTime();
					points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), startDate, ETransport.NOT_DEFINED, EPurpose.HOME));
					Date endDate = new Date(86399000);
					configureCalendar(cl, endDate);
					endDate = cl.getTime();
					points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), endDate, ETransport.NOT_DEFINED, EPurpose.HOME));
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
							ETransport nextMode;
							Map<String, String> mixedparams = getStringStringMap(oll, dll, startTime);

							JsonNode[] mixedResultsHolder = new JsonNode[1];

							Route route = routing.getRoute(drm, oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
							nextMode = determineTransportMode(person, distance, route, mixedparams, mixedResultsHolder);

							int multiplier = calculateMultiplier(nextMode);
							long travelTime = 0;

							if (nextMode == ETransport.WALK || nextMode == ETransport.BICYCLE || nextMode == ETransport.CAR) {
								travelTime = calculateTravelTime(route, multiplier);
								endTime += travelTime;

								List<Node> nodes = route.listNodes();
								Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(nodes, new Date(startTime * 1000), new Date(endTime * 1000));
								addSubpoints(nodes, timeMap, nextMode, purpose, subpoints);

								List<Link> links = route.listLinks();
								assignLinksToSubpoints(subpoints, links);
								points.addAll(subpoints);

								long depTime = next.getStartTime() - travelTime;
								person.addTrip(new Trip(nextMode, purpose, depTime, oll, dll));
							} else if (nextMode == ETransport.MIX) {
								if (mixedResultsHolder[0].path("features").get(0) == null) {
									System.out.println("empty mixed results!");
								}
								handleMixedTransport(nextMode, purpose, mixedResultsHolder, subpoints, points, person, next, route, startTime, endTime, oll, dll);
							} else {
								person.addTrip(new Trip(ETransport.NOT_DEFINED, next.getPurpose(), next.getStartTime(), pre.getLocation(), pre.getLocation()));
								Calendar cl = Calendar.getInstance();
								Date startDate = new Date(next.getStartTime());
								configureCalendar(cl, startDate);
								startDate = cl.getTime();
								points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), startDate, ETransport.NOT_DEFINED, EPurpose.HOME));
								Date endDate = new Date(next.getStartTime() + 300);
								configureCalendar(cl, endDate);
								endDate = cl.getTime();
								points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), endDate, ETransport.NOT_DEFINED, EPurpose.HOME));
								person.addTrajectory(points);
							}
						} else {
							person.addTrip(new Trip(ETransport.NOT_DEFINED, next.getPurpose(), next.getStartTime(), pre.getLocation(), pre.getLocation()));
							Calendar cl = Calendar.getInstance();
							Date startDate = new Date(next.getStartTime());
							configureCalendar(cl, startDate);
							startDate = cl.getTime();
							points.add(new SPoint(pre.getLocation().getLon(), pre.getLocation().getLat(), startDate, ETransport.NOT_DEFINED, next.getPurpose()));
							person.addTrajectory(points);
						}

						pre = next;
					}
				}
				person.addTrajectory(points);
			} catch (Exception e){
				System.err.println("Exception at person: " + person);
				e.printStackTrace();

			}

			return 0;
		}

		private Map<String, String> getStringStringMap(GLonLat oll, GLonLat dll, long startTime) {
			Map<String, String> params = new HashMap<>();
			params.put("UnitTypeCode", "2");
			params.put("StartLongitude", String.valueOf(oll.getLon()));
			params.put("StartLatitude", String.valueOf(oll.getLat()));
			params.put("GoalLongitude", String.valueOf(dll.getLon()));
			params.put("GoalLatitude", String.valueOf(dll.getLat()));

			Map<String, String> mixedparams = new HashMap<>(params);
//			if(getRandom()>0.5){
//				mixedparams.put("TransportCode", "1");
//			}else {
//				mixedparams.put("TransportCode", "3");
//			}
			mixedparams.put("TransportCode", "3");

			mixedparams.put("AppDate", "20240401");
			mixedparams.put("AppTime", convertSecondsToHHMM(startTime));
			return mixedparams;
		}

		@Override
		public Integer call() throws Exception {
			try {
			for (Person p : listAgents) {
				int res = process(p);
				if (res < 0) {
					this.error++;
				}
				this.total++;
			}
			}catch(Exception e) {
				e.printStackTrace();
			}
			// System.out.printf("[%d]-%d-%d%n",id, error, total);
			return 0;
		}
	}
	
	public void generate(List<Person> agents) {
		// prepare thread processing
		int numThreads = Runtime.getRuntime().availableProcessors();
		System.out.println("NumOfThreads:" + numThreads);
		
		List<Callable<Integer> > listTasks = new ArrayList<>();
		int listSize = agents.size();
		int taskNum = numThreads;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = Math.min(listSize, end);
			List<Person> subList = agents.subList(i, end);
			listTasks.add(new TripTask(i, subList));
		}
		System.out.println("NumOfTasks:" + listTasks.size());
		
		// execute thread processing
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		try {
			es.invokeAll(listTasks);
			es.shutdown();
		} catch (Exception exp) {
			exp.printStackTrace();
		}		
	}

	private static JsonNode getMixedRoute(CloseableHttpClient httpClient, String sessionid, Map<String, String> params) {
		HttpPost mixedRoutePost = new HttpPost(prop.getProperty("api.getMixedRouteURL"));

		List<NameValuePair> mixedRouteParams = new ArrayList<>();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			mixedRouteParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
		}

        try {
            mixedRoutePost.setEntity(new UrlEncodedFormEntity(mixedRouteParams));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        mixedRoutePost.setHeader("Cookie", "WebApiSessionID=" + sessionid);

        HttpResponse mixedRouteResponse = null;
        try {
            mixedRouteResponse = executePostRequest(httpClient, mixedRoutePost);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ObjectMapper mapper = new ObjectMapper();

		if (mixedRouteResponse.getStatusLine().getStatusCode() == 200) {
            String mixedRouteResponseBody = null;
            try {
                mixedRouteResponseBody = EntityUtils.toString(mixedRouteResponse.getEntity());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                return mapper.readTree(mixedRouteResponseBody);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
			System.out.println("Failed to get mixed route: " + mixedRouteResponse.getStatusLine().getStatusCode());
            try {
                return mapper.readTree("");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
	}

	private static JsonNode getRoadRoute(CloseableHttpClient httpClient, String sessionid, Map<String, String> params) throws Exception {
		HttpPost roadRoutePost = new HttpPost(prop.getProperty("api.getRoadRouteURL"));

		List<NameValuePair> roadRouteParams = new ArrayList<>();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			roadRouteParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
		}

		roadRoutePost.setEntity(new UrlEncodedFormEntity(roadRouteParams));
		roadRoutePost.setHeader("Cookie", "WebApiSessionID=" + sessionid);

		HttpResponse roadRouteResponse = executePostRequest(httpClient, roadRoutePost);
		ObjectMapper mapper = new ObjectMapper();

		if (roadRouteResponse.getStatusLine().getStatusCode() == 200) {
			String roadRouteResponseBody = EntityUtils.toString(roadRouteResponse.getEntity());
			return mapper.readTree(roadRouteResponseBody);
		} else {
			System.out.println("Failed to get road route: " + roadRouteResponse.getStatusLine().getStatusCode());
			return mapper.readTree("");
		}
	}
	private static Properties prop;
	private static void loadProperties() throws Exception {
		InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		prop = new Properties();
		prop.load(inputStream);
	}

	public static void main(String[] args) throws Exception {

		String inputDir;
		String root;

		loadProperties();
		InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		Properties prop = new Properties();
		prop.load(inputStream);

		root = prop.getProperty("root");
		inputDir = prop.getProperty("inputDir");
		System.out.println("Root Directory: " + root);
		System.out.println("Input Directory: " + inputDir);
		
		int mfactor = 1;

		Country japan = new Country();

		// load data
		String railFile = String.format("%srailnetwork.tsv", inputDir+"/network/");
		Network railway = RailLoader.load(railFile);


		String cityFile = String.format("%scity_boundary.csv", inputDir);
		DataAccessor.loadCityData(cityFile, japan);

		String stationFile = String.format("%sbase_station.csv", inputDir);
		Network station = DataAccessor.loadLocationData(stationFile);
		japan.setStation(station);

		String outputDir = "D:/large/PseudoPFLOW/";

		ArrayList<Integer> prefectureCodes = new ArrayList<>(Arrays.asList(
				28
//				22, 16, 28,
//				13,14,12,11,
//				1,2,3,5,6,8,10,15,
//				17,20,21,23,24,25,
//				30,33,36,37,39,40,41,42,
//				44,46
		));

		for (int i: prefectureCodes){

			File tripDir = new File(outputDir+"trip/", String.valueOf(i));
			File trajDir = new File(outputDir+"trajectory/", String.valueOf(i));
			System.out.println("Start prefecture:" + i +" "+ tripDir.mkdirs() +" "+ trajDir.mkdirs());

			String roadFile = String.format("%sdrm_%02d.tsv", inputDir+"/network/", i);

			Network road = DrmLoader.load(roadFile);
			Double carRatio = Double.parseDouble(prop.getProperty("car." + i));
			Double bikeRatio = Double.parseDouble(prop.getProperty("bike." + i));

			File actDir = new File(String.format("%s/activity_v2/", root), String.valueOf(i));
			for(File file: Objects.requireNonNull(actDir.listFiles())){
				if (file.getName().contains(".csv")) {
					String tripFileName = outputDir + "trip/" + i + "/trip_" + file.getName().substring(9, 14) + ".csv";
//					if(file.getName().substring(9, 14).equals("22101")||file.getName().substring(9, 14).equals("22102")){
//						continue;
//					}
					String trajectoryFileName = outputDir + "trajectory/" + i + "/trajectory_" + file.getName().substring(9,14) + ".csv";

					if(!file.getName().equals("Kobe_bus_act.csv")){
						continue;
					}

					// Check if the files already exist
//					if (new File(tripFileName).exists() || new File(trajectoryFileName).exists()) {
//                        System.out.println("outfile exists");
//
//						continue; // Skip to the next iteration
//					}

					long starttime = System.currentTimeMillis();
					TripGenerator_WebAPI_refactor_Kobe worker = new TripGenerator_WebAPI_refactor_Kobe(japan, road, railway);
                    System.out.println("start to load acts");
					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, carRatio, bikeRatio);
					System.out.printf("%s%n", file.getName());
					worker.generate(agents);
					PersonAccessor.writeTrips(tripFileName, agents);
					PersonAccessor.writeTrajectory(trajectoryFileName, agents);
                    getBusRouteStats();
                    printBusRouteStats();
                    Path out = Paths.get(outputDir+"trajectory/"+"large_hiyodori_bus_usage_stats.csv");
                    exportBusRouteStatsToCSV(out);
                    Path out2 = Paths.get(outputDir+"trajectory/"+"large_hiyodori_bus_station_usage_stats.csv");
                    exportRouteStationBoardingsHourlyToCSV(out2);
					long endtime = System.currentTimeMillis();
					System.out.println(file.getName() + ": " + (endtime - starttime));
				}
			}

		}
		System.out.println("end");
	}	
}
