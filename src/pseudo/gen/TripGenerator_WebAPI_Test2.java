package pseudo.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import network.DrmLoader;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
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
import pseudo.acs.ModeAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.res.*;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripGenerator_WebAPI_Test2 {

	private final ModeAccessor modeAcs;
	private final Country japan;

	private final Network drm;

	private final SSLContext sslContext;
	private final PoolingHttpClientConnectionManager connManager;
	private final CloseableHttpClient httpClient;
	private final String sessionId;

	private static final double MAX_WALK_DISTANCE = 1000;
	private static final double MAX_SEARCH_STATAION_DISTANCE = 5000;


	public TripGenerator_WebAPI_Test2(Country japan, ModeAccessor modeAcs, Network drm) throws Exception {
		super();
		this.japan = japan;
		this.modeAcs = modeAcs;
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
				new String[]{"TLSv1.2", "TLSv1.3"}, // Specify the TLS versions
				null,
				(hostname, session) -> hostname.equals("157.82.223.35")); // This bypasses hostname verification. Use with caution.


		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
				RegistryBuilder.<ConnectionSocketFactory>create()
						.register("https", sslSocketFactory)
						.build());

		cm.setMaxTotal(12); // Adjust based on your expected total number of concurrent connections
		cm.setDefaultMaxPerRoute(12); // Adjust per route limits based on your API and use case
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

		private int process(Person person) throws Exception {
			List<Activity> activities = person.getActivities();
			Activity pre = activities.get(0);

			ETransport primaryMode = null;

			for (int i = 1; i < activities.size(); i++) {
				Activity next = activities.get(i);
				GLonLat oll = pre.getLocation();
				GLonLat dll = next.getLocation();

				EPurpose purpose = next.getPurpose();
				double distance = DistanceUtils.distance(oll, dll);

				if (distance > 0) {
					ETransport nextMode = determineTransportMode(person, oll, dll, purpose, distance);
					if (i == 1) {
						primaryMode = nextMode;
					}

					// create trip or sub-trip
					long travelTime = calculateTravelTime(nextMode, oll, dll);
					long depTime = next.getStartTime() - travelTime;
					person.addTrip(new Trip(nextMode, purpose, depTime, oll, dll));
				}
				pre = next;
			}
			return 0;
		}

		private ETransport determineTransportMode(Person person, GLonLat oll, GLonLat dll, EPurpose purpose, double distance) throws Exception {
			Map<ETransport, Double> choices = new LinkedHashMap<>();

			if (distance > MAX_WALK_DISTANCE) {
				Map<String, String> mixedparams = prepareParams(oll, dll, "3");
				JsonNode mixedResults = getMixedRoute(httpClient, sessionId, mixedparams);
				double mixedfare = mixedResults.get("fare").asDouble();
				double mixedtime = mixedResults.get("total_time").asDouble();
				Double mixedcost = mixedfare + mixedtime / 60 * 1000.0;
				choices.put(ETransport.MIX, mixedcost);
			}

			// Calculate walking cost
			Route route = routing.getRoute(drm, oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
			double roadtime = route.getCost();
			double walktime = roadtime * 10;
			Double walkcost = walktime / 60 * 1000.0;
			choices.put(ETransport.WALK, walkcost);

			// Calculate driving cost if person has a car
			if (person.hasCar()) {
				double roadfare = route.getLength() * 51;
				double roadcost = roadfare + roadtime / 60 * 1000.0;
				choices.put(ETransport.CAR, roadcost);
			}

			return choices.entrySet()
					.stream()
					.min(Comparator.comparing(Map.Entry::getValue))
					.map(Map.Entry::getKey)
					.orElse(ETransport.NOT_DEFINED);
		}

		private long calculateTravelTime(ETransport mode, GLonLat oll, GLonLat dll) throws Exception {
			switch (mode) {
				case WALK:
					Route walkRoute = routing.getRoute(drm, oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
					double walkTime = walkRoute.getCost() * 10;
					return (long) walkTime * 60;
				case CAR:
					Route carRoute = routing.getRoute(drm, oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
					return (long) carRoute.getCost() * 60;
				case MIX:
					Map<String, String> mixedParams = prepareParams(oll, dll, "3");
					JsonNode mixedResults = getMixedRoute(httpClient, sessionId, mixedParams);
					double mixedTime = mixedResults.get("total_time").asDouble();
					return (long) mixedTime * 60;
				default:
					return 600; // Default travel time for undefined mode
			}
		}

		private Map<String, String> prepareParams(GLonLat oll, GLonLat dll, String transportCode) {
			Map<String, String> params = new HashMap<>();
			params.put("UnitTypeCode", "2");
			params.put("StartLongitude", String.valueOf(oll.getLon()));
			params.put("StartLatitude", String.valueOf(oll.getLat()));
			params.put("GoalLongitude", String.valueOf(dll.getLon()));
			params.put("GoalLatitude", String.valueOf(dll.getLat()));
			params.put("TransportCode", transportCode);
			return params;
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
			// System.out.println(String.format("[%d]-%d-%d",id, error, total));
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

	private static JsonNode getMixedRoute(CloseableHttpClient httpClient, String sessionid, Map<String, String> params) throws Exception {
		HttpPost mixedRoutePost = new HttpPost(prop.getProperty("api.getMixedRouteURL"));

		List<NameValuePair> mixedRouteParams = new ArrayList<>();
		for (Map.Entry<String, String> entry : params.entrySet()) {
			mixedRouteParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
		}

		mixedRoutePost.setEntity(new UrlEncodedFormEntity(mixedRouteParams));
		mixedRoutePost.setHeader("Cookie", "WebApiSessionID=" + sessionid);

		HttpResponse mixedRouteResponse = executePostRequest(httpClient, mixedRoutePost);
		ObjectMapper mapper = new ObjectMapper();

		if (mixedRouteResponse.getStatusLine().getStatusCode() == 200) {
			String mixedRouteResponseBody = EntityUtils.toString(mixedRouteResponse.getEntity());
			return mapper.readTree(mixedRouteResponseBody);
		} else {
			System.out.println("Failed to get mixed route: " + mixedRouteResponse.getStatusLine().getStatusCode());
			return mapper.readTree("");
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
		
		Country japan = new Country();
		
		System.out.println("start");

		loadProperties();

		String inputDir = null;
		String root = null;

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
		
		// load data
		String cityFile = String.format("%scity_boundary.csv", inputDir);
		DataAccessor.loadCityData(cityFile, japan);
		
		String stationFile = String.format("%sbase_station.csv", inputDir);
		Network station = DataAccessor.loadLocationData(stationFile);
		japan.setStation(station);
	
		String modeFile = String.format("%sact_transport.csv", inputDir);
		ModeAccessor modeAcs = new ModeAccessor(modeFile);

		String roadFile = String.format("%sdrm_%02d.tsv", "/home/mdxuser/Data/PseudoPFLOW/processing/DRM/" ,13);
		Network road = DrmLoader.load(roadFile);
		// System.out.println(road.linkCount());
	
		// create worker
		TripGenerator_WebAPI_Test2 worker = new TripGenerator_WebAPI_Test2(japan, modeAcs, road);
		String outputDir = String.format("%s/trip/", root);

		int start = 1;
		for (int i = 48; i <= 48; i++){

			File prefDir = new File(outputDir, String.valueOf(i));
			System.out.println("Start prefecture:" + i + prefDir.mkdirs());

			// Double ratio = Double.parseDouble(prop.getProperty("car." + i));

			File actDir = new File(String.format("%s/activity_merge2/", root), String.valueOf(i));
			for(File file: actDir.listFiles()){
				if (file.getName().contains(".csv")) {
					long starttime = System.currentTimeMillis();
					Double ratio = Double.parseDouble(prop.getProperty("car." + file.getName().substring(9, 11)));
					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, ratio);
					System.out.printf("%s%n", file.getName());
					worker.generate(agents);
					PersonAccessor.writeTrips(new File(outputDir+ i + "/trip_"+ file.getName().substring(9,14) + ".csv").getAbsolutePath(), agents);
					long endtime = System.currentTimeMillis();
					System.out.println(file.getName() + ": " + (endtime - starttime));
				}
			}
		}
		System.out.println("end");
		System.out.println(System.currentTimeMillis());

	}	
}
