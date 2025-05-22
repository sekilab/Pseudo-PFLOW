package pseudo.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.RailLoader;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import pseudo.acs.PersonAccessor;   // 假设 PersonAccessor 在此包
import pseudo.acs.DataAccessor;    // 假设 DataAccessor 在此包
import jp.ac.ut.csis.pflow.geom2.LonLat;
import network.DrmLoader;   // 假设 DrmLoader 在此包
import pseudo.res.*;
import pseudo.res.Activity;
import pseudo.res.Person;
import pseudo.res.Trip;
import pseudo.res.SPoint;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;


public class TripGenerator_WebAPI_Batch {

    private static final double MIN_TRANSIT_DISTANCE = 1000;
    private static final double FARE_PER_KILOMETER = 51;
    private static final double FARE_PER_HOUR = 1000;
    private static final double FATIGUE_INDEX_WALK = 1.5;
    private static final double FATIGUE_INDEX_BICYCLE = 1.2;
    private static final double FARE_INIT = 200;

    private final SSLContext sslContext;
    private final PoolingHttpClientConnectionManager connManager;
    private final CloseableHttpClient httpClient;
    private final String sessionId;
    private final Network drm;

    public TripGenerator_WebAPI_Batch(Network drm) throws Exception {
        this.drm = drm;

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

        connManager.setMaxTotal(32); // Adjust based on your expected total number of concurrent connections 32 default
        connManager.setDefaultMaxPerRoute(100); // Adjust per route limits based on your API and use case 100 default

        return connManager;
    }

    private static HttpResponse executePostRequest(CloseableHttpClient httpClient, HttpPost postRequest) throws Exception {
        return httpClient.execute(postRequest);
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

    public String createSession() throws Exception{

        HttpPost createSessionPost = new HttpPost("http://pflow-api.csis.u-tokyo.ac.jp/webapi/CreateSession");

        List<NameValuePair> sessionParams = new ArrayList<>();
        sessionParams.add(new BasicNameValuePair("UserID", "ma_jue"));
        sessionParams.add(new BasicNameValuePair("Password", "JdTl3v5OmB"));
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


    public void generateAll(List<Person> agents) {
        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("NumOfThreads: " + numThreads);

        List<Callable<Void>> tasks = new ArrayList<>();
        int totalAgents = agents.size();
        int stepSize = (int)Math.ceil(totalAgents * 1.0 / numThreads);

        for (int start = 0; start < totalAgents; start += stepSize) {
            int end = Math.min(start + stepSize, totalAgents);
            List<Person> subList = agents.subList(start, end);
            tasks.add(() -> {
                batchProcess(subList);
                return null;
            });
        }

        ExecutorService es = Executors.newFixedThreadPool(numThreads);
        try {
            es.invokeAll(tasks);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            es.shutdown();
        }
    }


    private void batchProcess(List<Person> persons) {
        List<BatchTripRequest> batchRequests = new ArrayList<>();
        Map<String, TripContext> contextMap = new HashMap<>();

        for (Person p : persons) {
            List<Activity> acts = p.getActivities();
            for (int i=0; i<acts.size()-1; i++) {
                Activity pre = acts.get(i);
                Activity next = acts.get(i+1);

                double dist = DistanceUtils.distance(pre.getLocation(), next.getLocation());
                if (dist > MIN_TRANSIT_DISTANCE) {

                    int appDate = readAppDate(); // static value now
                    int appTime = convertSecondsToHHmm(next.getStartTime()); // pseudo

                    String reqId = p.getId() + "_" + i + "_" + next.getStartTime();
                    BatchTripRequest req = new BatchTripRequest(
                        reqId,
                        2, // UnitTypeCode=2
                        pre.getLocation().getLon(), pre.getLocation().getLat(),
                        next.getLocation().getLon(), next.getLocation().getLat(),
                        appDate,
                        appTime,
                        1  // StartGoalType=1
                    );
                    batchRequests.add(req);

                    TripContext ctx = new TripContext(p, pre, next);
                    contextMap.put(reqId, ctx);
                }
            }
        }

        Map<String, BatchTripResponse> gtfsResults = Collections.emptyMap();
        if (!batchRequests.isEmpty()) {
            BatchRouteService svc = new BatchRouteService(httpClient, sessionId);
            List<File> gtfsFiles = Arrays.asList(new File("./download/yourGtfs.zip"));
            try {
                gtfsResults = svc.getGtfsBusRouteBatch(batchRequests, gtfsFiles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        LinkCost linkCost = new LinkCost();
        Dijkstra routing = new Dijkstra(linkCost);

        for (Person p : persons) {
            List<Activity> acts = p.getActivities();
            if (acts.size() <=1 ) continue;

            for (int i=0; i<acts.size()-1; i++) {
                Activity pre = acts.get(i);
                Activity nxt = acts.get(i+1);
                LonLat oll = pre.getLocation();
                LonLat dll = nxt.getLocation();

                double dist = DistanceUtils.distance(oll, dll);
                // 3.1 CAR/WALK/BIKE
                Route route = routing.getRoute(drm, oll.getLon(), oll.getLat(),
                    dll.getLon(), dll.getLat());
                double roadFare   = FARE_INIT + (route.getLength()/1000.0)* FARE_PER_KILOMETER;
                double roadTimeHr = route.getCost()/3600.0;
                double carCost    = roadFare + roadTimeHr*FARE_PER_HOUR;

                double walkTimeSec = route.getLength()/1.38;
                double walkCost    = (walkTimeSec/3600.0)*FARE_PER_HOUR*FATIGUE_INDEX_WALK;

                double bikeTimeSec = walkTimeSec/2.0;
                double bikeCost    = (bikeTimeSec/3600.0)*FARE_PER_HOUR*FATIGUE_INDEX_BICYCLE;

                // 3.2 GTFS
                double gtfsCost = Double.POSITIVE_INFINITY;
                String reqId = p.getId() + "_" + i + "_" + nxt.getStartTime();
                BatchTripResponse gResp = gtfsResults.get(reqId);
                if (gResp!=null) {
                    double fare      = gResp.getFare();
                    double totalTime = gResp.getTotalTime(); // 分钟
                    gtfsCost = fare + (totalTime/60.0)*FARE_PER_HOUR;
                }

                // 3.3 getMixedRoute
                double mixCost = Double.POSITIVE_INFINITY;
                if (dist>MIN_TRANSIT_DISTANCE) {

                    // JsonNode mixJson = getMixedRoute(httpClient, sessionId, someParams);
                    // parse fare/time
                    // ...
                    // mixCost = ...
                }

                // 3.4 对比
                Map<ETransport,Double> costMap = new HashMap<>();
                costMap.put(ETransport.CAR, carCost);
                costMap.put(ETransport.WALK, walkCost);
                costMap.put(ETransport.BICYCLE, bikeCost);
                if (gtfsCost< Double.POSITIVE_INFINITY) costMap.put(ETransport.COMMUNITY, gtfsCost);
                if (mixCost< Double.POSITIVE_INFINITY) costMap.put(ETransport.MIX, mixCost);

                ETransport best = costMap.entrySet().stream()
                    .min(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(ETransport.NOT_DEFINED);

//                long travelSec = estimateTravelTime(best, route, gResp /*for GTFS*/, null /*mixJson*/);
//                long depTime   = nxt.getStartTime()- travelSec;
//                p.addTrip(new Trip(best, nxt.getPurpose(), depTime, oll, dll));
//
//                List<SPoint> subPoints = buildTrajectory(best, route, depTime, nxt.getStartTime(), gResp, oll, dll);
//                p.addTrajectory(subPoints);
            }
        }
    }

    private int readAppDate() {

        return 20241027;
    }


    private int convertSecondsToHHmm(long startTimeSec) {

        long hours = (startTimeSec / 3600) % 24;
        long mins  = (startTimeSec % 3600) / 60;
        return (int)(hours*100 + mins);
    }



    private long estimateTravelTime(ETransport mode, Route route, BatchTripResponse br) {
        switch(mode) {
            case CAR:
                return (long)route.getCost(); // route.getCost()单位秒
            case WALK:

                double walkSec = route.getLength()/1.38;
                return (long)walkSec;
            case BICYCLE:

                double bikeSec = (route.getLength()/1.38)/2.0;
                return (long)bikeSec;
            case MIX:

                if (br!=null) {
                    double totalMin = br.getTotalTime();
                    return (long)(totalMin*60.0);
                }
                return 1800;
            default:
                // NOT_DEFINED, etc.
                return 0;
        }
    }


    private List<SPoint> buildMixedTrajectory(long depTimeSec, long arrTimeSec,
                                              EPurpose purpose,
                                              BatchTripResponse br,
                                              LonLat oll, LonLat dll) {
        List<SPoint> points = new ArrayList<>();

        Date dep = new Date(depTimeSec * 1000L);
        Date arr = new Date(arrTimeSec * 1000L);
        points.add(new SPoint(oll.getLon(), oll.getLat(), dep, ETransport.MIX, purpose));

        long midMillis = (dep.getTime() + arr.getTime())/2;
        double midLon = (oll.getLon() + dll.getLon())/2.0;
        double midLat = (oll.getLat() + dll.getLat())/2.0;
        points.add(new SPoint(midLon, midLat, new Date(midMillis), ETransport.MIX, purpose));

        points.add(new SPoint(dll.getLon(), dll.getLat(), arr, ETransport.MIX, purpose));

        return points;
    }



    private static Properties prop;

    private static void loadProperties() throws Exception {
        InputStream inputStream = TripGenerator_WebAPI_Batch.class.getClassLoader()
            .getResourceAsStream("config.properties");
        if (inputStream == null) {
            throw new FileNotFoundException("config.properties file not found in the classpath");
        }
        prop = new Properties();
        prop.load(inputStream);
    }

    public static void main(String[] args) throws Exception {

        loadProperties();
        String root = prop.getProperty("root");
        String inputDir = prop.getProperty("inputDir");
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

        String outputDir = "/large/PseudoPFLOW/";

        ArrayList<Integer> prefectureCodes = new ArrayList<>(Arrays.asList(
            16, 31, 32, 39, 36, 18, 41, 1, 40, 46, 47, 6, 5, 37, 30, 3, 19, 38, 7, 45, 17, 42, 44, 2,
            29, 25, 33, 24, 15, 10, 35, 4, 43, 20, 21, 9, 8, 22, 34, 26, 12, 28, 11, 14, 23, 27, 13,
            4, 43, 20, 21, 9, 8, 22, 34, 26, 12, 28, 11, 14, 23, 27, 13
        ));

        for (int prefCode : prefectureCodes) {

            File tripDir = new File(outputDir + "trip/", String.valueOf(prefCode));
            File trajDir = new File(outputDir + "trajectory/", String.valueOf(prefCode));
            tripDir.mkdirs();
            trajDir.mkdirs();

            System.out.println("Start prefecture: " + prefCode);
            String roadFile = String.format("%sDRM/drm_%02d.tsv", inputDir, prefCode);
            Network road = DrmLoader.load(roadFile);

            Double carRatio  = Double.parseDouble(prop.getProperty("car." + prefCode, "0.5"));
            Double bikeRatio = Double.parseDouble(prop.getProperty("bike." + prefCode, "0.3"));

            TripGenerator_WebAPI_Batch generator = new TripGenerator_WebAPI_Batch(road);

            File actDir = new File(String.format("%s/activity_merge3/", root), String.valueOf(prefCode));
            File[] actFiles = actDir.listFiles();
            if (actFiles == null) {
                System.out.println("No activity files found in " + actDir.getAbsolutePath());
                continue;
            }

            for (File file : actFiles) {
                if (file.getName().contains(".csv")) {
                    long starttime = System.currentTimeMillis();

                    List<Person> agents = PersonAccessor.loadActivity(
                        file.getAbsolutePath(),
                        mfactor,
                        carRatio,
                        bikeRatio
                    );

                    System.out.println("Generating for " + file.getName());
                    generator.generateAll(agents);


                    String fileId = file.getName().substring(9,14);
                    String tripOutPath = new File(tripDir, "trip_" + fileId + ".csv").getAbsolutePath();
                    String trajOutPath = new File(trajDir, "trajectory_" + fileId + ".csv").getAbsolutePath();

                    PersonAccessor.writeTrips(tripOutPath, agents);
                    PersonAccessor.writeTrajectory(trajOutPath, agents);

                    long endtime = System.currentTimeMillis();
                    System.out.println(file.getName() + ": " + (endtime - starttime) + " ms");
                }
            }
        }

        System.out.println("end");
    }

    public class BatchTripRequest {
        private String requestId;
        private int UnitTypeCode;
        private double StartLongitude;
        private double StartLatitude;
        private double GoalLongitude;
        private double GoalLatitude;
        private int AppDate;
        private int AppTime;
        private int StartGoalType;

        public BatchTripRequest(String requestId,
                                int unitTypeCode,
                                double startLon, double startLat,
                                double goalLon, double goalLat,
                                int appDate, int appTime,
                                int startGoalType) {
            this.requestId       = requestId;
            this.UnitTypeCode    = unitTypeCode;
            this.StartLongitude  = startLon;
            this.StartLatitude   = startLat;
            this.GoalLongitude   = goalLon;
            this.GoalLatitude    = goalLat;
            this.AppDate         = appDate;
            this.AppTime         = appTime;
            this.StartGoalType   = startGoalType;
        }

        // ----- Getter / Setter -----
        public String getRequestId() {
            return requestId;
        }
        public int getUnitTypeCode() {
            return UnitTypeCode;
        }
        public double getStartLongitude() {
            return StartLongitude;
        }
        public double getStartLatitude() {
            return StartLatitude;
        }
        public double getGoalLongitude() {
            return GoalLongitude;
        }
        public double getGoalLatitude() {
            return GoalLatitude;
        }
        public int getAppDate() {
            return AppDate;
        }
        public int getAppTime() {
            return AppTime;
        }
        public int getStartGoalType() {
            return StartGoalType;
        }
    }


    /** 批量API返回结果 */
    public static class BatchTripResponse {
        private String requestId;
        private double fare;      // 票价
        private double totalTime; // 总用时(分钟)
        private JsonNode raw;     // 可选, 保留原始 JSON

        public String getRequestId() {
            return requestId;
        }
        public double getFare() {
            return fare;
        }
        public double getTotalTime() {
            return totalTime;
        }
        public JsonNode getRaw() {
            return raw;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
        public void setFare(double fare) {
            this.fare = fare;
        }
        public void setTotalTime(double totalTime) {
            this.totalTime = totalTime;
        }
        public void setRaw(JsonNode raw) {
            this.raw = raw;
        }
    }

    public class BatchRouteService {

        private static final String API_GET_GTFS_BUS_ROUTE_BATCH_URL =
            "https://pflow-api.csis.u-tokyo.ac.jp/webapi/GetGTFSBusRoute2";

        private final CloseableHttpClient httpClient;
        private final String sessionId;
        private final ObjectMapper mapper;

        public BatchRouteService(CloseableHttpClient httpClient, String sessionId) {
            this.httpClient = httpClient;
            this.sessionId  = sessionId;
            this.mapper     = new ObjectMapper();
        }

        public Map<String, BatchTripResponse> getGtfsBusRouteBatch(
            List<BatchTripRequest> requests,
            List<File> gtfsFiles) throws IOException {

            HttpPost post = new HttpPost(API_GET_GTFS_BUS_ROUTE_BATCH_URL);
            post.setHeader("Cookie", "WebApiSessionID=" + sessionId);

            String jsonParams = mapper.writeValueAsString(requests);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            builder.addTextBody("params", jsonParams, ContentType.APPLICATION_JSON);

            if (gtfsFiles != null) {
                for (File f : gtfsFiles) {
                    builder.addPart("GTFS", new FileBody(f));
                }
            }

            post.setEntity(builder.build());

            HttpResponse resp = httpClient.execute(post);
            if (resp.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Failed to call GTFS API, code="
                    + resp.getStatusLine().getStatusCode());
            }


            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(body);
            if (!root.isArray()) {

                throw new IOException("Unexpected JSON: " + body);
            }


            List<BatchTripResponse> resultList = new ArrayList<>();
            for (JsonNode node : root) {
                BatchTripResponse r = new BatchTripResponse();
                r.setRequestId(node.path("requestId").asText(""));
                r.setFare(node.path("fare").asDouble(0.0));
                r.setTotalTime(node.path("total_time").asDouble(0.0));
                r.setRaw(node);

                resultList.add(r);
            }

            Map<String, BatchTripResponse> result = new HashMap<>();
            for (BatchTripResponse b : resultList) {
                result.put(b.getRequestId(), b);
            }
            return result;
        }


    }


    private static class TripContext {
        Person person;
        Activity pre;
        Activity next;
        public TripContext(Person p, Activity pre, Activity next) {
            this.person = p;
            this.pre = pre;
            this.next = next;
        }
    }
}

