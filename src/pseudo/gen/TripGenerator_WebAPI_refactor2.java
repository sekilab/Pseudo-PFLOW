package pseudo.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripGenerator_WebAPI_refactor2 {

	private final Country japan;

	private final Network drm;

	private final SSLContext sslContext;
	private final PoolingHttpClientConnectionManager connManager;
	private final CloseableHttpClient httpClient;
	private final String sessionId;

	private static final double MIN_TRANSIT_DISTANCE = 1000;
	// private static final double MAX_SEARCH_STATION_DISTANCE = 5000;
	private static final double FARE_PER_KILOMETER = 51; // Japanese yen, only for vehicle
	private static final double FARE_PER_HOUR = 1000; // Japanese yen, all modes, possible to extend to prefecture level
	private static final double FATIGUE_INDEX_WALK = 2.5;
	private static final double FATIGUE_INDEX_BICYCLE = 2;
	private static final double FARE_INIT = 75; // Japanese yen, only for vehicle
	private static final double CAR_AVAILABILITY = 0.25; // Parameter for explain people using car without ownership


	public TripGenerator_WebAPI_refactor2(Country japan, Network drm) throws Exception {
		super();
		this.japan = japan;
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

		private ETransport getTransport(int mode) {
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

		private ETransport determineTransportMode(Person person, double distance, Map<String, String> roadparams, Map<String, String> mixedparams, JsonNode[] resultsHolder) throws Exception {
			ETransport nextMode;

			resultsHolder[0] = getRoadRoute(httpClient, sessionId, roadparams);
			double roadtime = resultsHolder[0].get("total_time").asDouble();
			double roadfare = resultsHolder[0].get("length").asDouble() * FARE_PER_KILOMETER + resultsHolder[0].get("fare").asDouble();
			double roadcost = roadfare + roadtime / 3600 * FARE_PER_HOUR;

			double walktime = roadtime * 600 * 10;
			double walkcost = walktime / 3600 * FARE_PER_HOUR * FATIGUE_INDEX_WALK;

			Map<ETransport, Double> choices = new LinkedHashMap<>();

			if(distance>MIN_TRANSIT_DISTANCE){
				resultsHolder[1] = getMixedRoute(httpClient, sessionId, mixedparams);
				boolean publicTransit = resultsHolder[1].path("num_station").asInt() > 0 && resultsHolder[1].path("fare").asInt() > 0;
				if (publicTransit) {
					double mixedfare = resultsHolder[1].get("fare").asDouble();
					double mixedtime = resultsHolder[1].get("total_time").asDouble(); // Travel time from WebAPI is in minute
					double mixedcost = mixedfare + mixedtime / 60 * FARE_PER_HOUR;
					choices.put(ETransport.MIX, mixedcost);
					}
			}

			choices.put(ETransport.WALK, walkcost);
			if(person.hasBike()){
				double biketime = roadtime * 60 * 3;
				double bikecost = biketime / 3600 * FARE_PER_HOUR * FATIGUE_INDEX_BICYCLE;
				choices.put(ETransport.BICYCLE, bikecost);
			}

			if(person.hasCar() || getRandom() < CAR_AVAILABILITY) {
				choices.put(ETransport.CAR, roadcost);
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

		private void handleMixedTransport(ETransport nextMode, EPurpose purpose, JsonNode[] mixedResultsHolder, List<SPoint> subpoints, List<SPoint> points, Person person, Activity next, long startTime, long endTime, LonLat oll, LonLat dll) {
			long mixedTime = mixedResultsHolder[1].path("total_time").asLong() * 60;
			endTime += mixedTime;
			long travelTime = mixedTime;
			long depTime = next.getStartTime() - travelTime;

			List<Node> nodes = extractNodesFromMixedResults(mixedResultsHolder);
			boolean publicTransit = mixedResultsHolder[1].path("num_station").asInt() > 0;
			List<JsonNode> currentSubtrip = new ArrayList<>();
			int lastMode = determineInitialTransportMode(mixedResultsHolder);

			processMixedTransportFeatures(mixedResultsHolder, currentSubtrip, lastMode, publicTransit, depTime, person, purpose);
			addTimeStampedSubpoints(nodes, startTime, endTime, nextMode, purpose, subpoints);
			points.addAll(subpoints);
		}

		private void handleSingleTransport(ETransport nextMode, EPurpose purpose, JsonNode[] mixedResultsHolder, List<SPoint> subpoints, List<SPoint> points, Person person, Activity next, long startTime, long endTime, LonLat oll, LonLat dll) {
			long mixedTime = mixedResultsHolder[0].path("total_time").asLong() * 60;
			endTime += mixedTime;
			long travelTime = mixedTime;
			long depTime = next.getStartTime() - travelTime;

			List<Node> nodes = extractNodesFromMixedResults(mixedResultsHolder);
			boolean publicTransit = mixedResultsHolder[0].path("num_station").asInt() > 0;
			List<JsonNode> currentSubtrip = new ArrayList<>();
			int lastMode = determineInitialTransportMode(mixedResultsHolder);

			processMixedTransportFeatures(mixedResultsHolder, currentSubtrip, lastMode, publicTransit, depTime, person, purpose);
			addTimeStampedSubpoints(nodes, startTime, endTime, nextMode, purpose, subpoints);
			points.addAll(subpoints);
		}

		private List<Node> extractNodesFromMixedResults(JsonNode[] mixedResultsHolder) {
			List<Node> nodes = new ArrayList<>();
			JsonNode routeData = mixedResultsHolder[0].path("features");
			for (JsonNode feature : routeData) {
				JsonNode coordinates = feature.path("geometry").path("coordinates");
				if (coordinates.isArray()) {
					double lon = coordinates.get(0).asDouble();
					double lat = coordinates.get(1).asDouble();
					nodes.add(new Node(feature.path("properties").path("id").toString(), lon, lat));
				} else {
					System.out.println("Coordinate from WebAPI is not an array!!");
				}
			}
			return nodes;
		}

		private int determineInitialTransportMode(JsonNode[] mixedResultsHolder) {
			return mixedResultsHolder[0].path("features").get(0).path("properties").path("transportation").asInt();
		}

		private void processMixedTransportFeatures(JsonNode[] mixedResultsHolder, List<JsonNode> currentSubtrip, int lastMode, boolean publicTransit, long depTime, Person person, EPurpose purpose) {
			JsonNode routeData = mixedResultsHolder[0].path("features");
			JsonNode prevNode = null;
			for (JsonNode feature : routeData) {
				int currentMode = feature.path("properties").path("transportation").asInt();

				if (currentMode != lastMode) {
					if (currentSubtrip.size() > 1) {
						addTripForSubtrip(currentSubtrip, lastMode, publicTransit, depTime, person, purpose);
					}
					currentSubtrip = new ArrayList<>();
					currentSubtrip.add(prevNode);
					lastMode = currentMode;
				}
				currentSubtrip.add(feature);
				prevNode = feature;
			}
		}

		private void addTripForSubtrip(List<JsonNode> currentSubtrip, int lastMode, boolean publicTransit, long depTime, Person person, EPurpose purpose) {
			JsonNode ollCoords = currentSubtrip.get(0).path("geometry").path("coordinates");
			JsonNode dllCoords = currentSubtrip.get(currentSubtrip.size() - 1).path("geometry").path("coordinates");
			ETransport mode = getTransport(currentSubtrip.get(1).path("properties").path("transportation").asInt());
			if (mode == ETransport.CAR && publicTransit) {
				mode = ETransport.WALK;
			}
			if (ollCoords.isArray() && dllCoords.isArray()) {
				LonLat moll = new LonLat(ollCoords.get(0).asDouble(), ollCoords.get(1).asDouble());
				LonLat mdll = new LonLat(dllCoords.get(0).asDouble(), dllCoords.get(1).asDouble());
				person.addTrip(new Trip(mode, purpose, depTime, moll, mdll));
				depTime += (long) (DistanceUtils.distance(moll, mdll) / 8.33);
			} else {
				System.out.println("No coordinate from API!");
			}
		}

		private void addTimeStampedSubpoints(List<Node> nodes, long startTime, long endTime, ETransport nextMode, EPurpose purpose, List<SPoint> subpoints) {
			Map<Node, Date> timeMap = TrajectoryUtils.putTimeStamp(nodes, new Date(startTime * 1000), new Date(endTime * 1000));
			addSubpoints(nodes, timeMap, nextMode, purpose, subpoints);
		}

		public String convertSecondsToHHMM(double totalSeconds) {
			// Calculate hours and minutes
			int hours = (int) (totalSeconds / 3600);
			int minutes = (int) ((totalSeconds % 3600) / 60);

			// Format hours and minutes to HHMM as WebAPI requests
			return String.format("%02d%02d", hours, minutes);
		}

		private int process(Person person) throws Exception {
			List<SPoint> points = new ArrayList<>();

			List<Activity> activities = person.getActivities();
			Activity pre = activities.get(0);

			ETransport primaryMode = null;
			if (activities.size() <= 1) {
				person.addTrip(new Trip(ETransport.NOT_DEFINED, EPurpose.HOME, 0, pre.getLocation(), pre.getLocation()));
			}else {
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

						Map<String, String> params = new HashMap<>();
						params.put("UnitTypeCode", "2");
						params.put("StartLongitude", String.valueOf(oll.getLon()));
						params.put("StartLatitude", String.valueOf(oll.getLat()));
						params.put("GoalLongitude", String.valueOf(dll.getLon()));
						params.put("GoalLatitude", String.valueOf(dll.getLat()));

						Map<String, String> mixedparams = new HashMap<>(params);
						mixedparams.put("TransportCode", "3");
						mixedparams.put("AppDate", "20240401");
						mixedparams.put("AppTime", convertSecondsToHHMM(startTime));

						Map<String, String> roadparams = new HashMap<>(params);

						JsonNode[] mixedResultsHolder = new JsonNode[1];

						// Check if the purpose is not HOME
						if (purpose != EPurpose.HOME) {
							// Determine the next transport mode using the determineTransportMode method
							nextMode = determineTransportMode(person, distance, roadparams, mixedparams, mixedResultsHolder);
							// If this is the first iteration and the chosen transport mode is CAR, set it as the primary mode
							if (i == 1 && nextMode == ETransport.CAR) {
								primaryMode = nextMode;
							}
						} else {
							// If the purpose is HOME
							if (primaryMode != null) {
								// If primary mode is already set, use it as the next transport mode
								nextMode = primaryMode;
							} else {
								// If primary mode is not set, determine the next transport mode as usual
								nextMode = determineTransportMode(person, distance, roadparams, mixedparams, mixedResultsHolder);
							}
						}

						// create trip or sub-trip
						int multiplier = calculateMultiplier(nextMode);
						long travelTime = 0;

						if (nextMode == ETransport.WALK || nextMode == ETransport.BICYCLE || nextMode == ETransport.CAR) {

						} else if (nextMode == ETransport.MIX) {
							handleMixedTransport(nextMode, purpose, mixedResultsHolder, subpoints, points, person, next, startTime, endTime, oll, dll);
						} else {
							System.out.println("Did not find correct mode!");
							travelTime = 600;
							error++;
						}

					}
					pre = next;
				}
			}
			person.addTrajectory(points);
			return 0;
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
		String cityFile = String.format("%scity_boundary.csv", inputDir);
		DataAccessor.loadCityData(cityFile, japan);

		String stationFile = String.format("%sbase_station.csv", inputDir);
		Network station = DataAccessor.loadLocationData(stationFile);
		japan.setStation(station);

		String outputDir = "/large/PseudoPFLOW/";

		ArrayList<Integer> prefectureCodes = new ArrayList<>(Arrays.asList(
				0, 16, 31, 32, 39, 36, 18, 41, 1, 40, 46, 47, 6, 5, 37, 30, 3, 19, 38, 7, 45, 17, 42, 44, 2,
				29, 25, 33, 24, 15, 10, 35, 4, 43, 20, 21, 9, 8, 22, 34, 26, 12, 28, 11, 14, 23, 27, 13,
				4, 43, 20, 21, 9, 8, 22, 34, 26, 12, 28, 11, 14, 23, 27, 13
		));

		for (int i: prefectureCodes){

			File tripDir = new File(outputDir+"trip/", String.valueOf(i));
			File trajDir = new File(outputDir+"trajectory/", String.valueOf(i));
			System.out.println("Start prefecture:" + i +" "+ tripDir.mkdirs() +" "+ trajDir.mkdirs());

			String roadFile = String.format("%sdrm_%02d.tsv", inputDir+"/DRM/", i);
			if(i==0){
				roadFile =  String.format("%sdrm_%02d.tsv", inputDir+"/DRM/", 16);
			}
			Network road = DrmLoader.load(roadFile);
			Double carRatio = Double.parseDouble(prop.getProperty("car." + 16));
			Double bikeRatio = Double.parseDouble(prop.getProperty("bike." + 16));

			// create worker
			// TripGenerator_WebAPI worker = new TripGenerator_WebAPI(japan, modeAcs, road);

			File actDir = new File(String.format("%s/activity_merge3/", root), String.valueOf(i));
			for(File file: Objects.requireNonNull(actDir.listFiles())){
				if (file.getName().contains(".csv")) {
					long starttime = System.currentTimeMillis();
					TripGenerator_WebAPI_refactor2 worker = new TripGenerator_WebAPI_refactor2(japan, road);
					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, carRatio, bikeRatio);
					System.out.printf("%s%n", file.getName());
					worker.generate(agents);
					PersonAccessor.writeTrips(new File(outputDir + "trip/" + i + "/trip_"+ file.getName().substring(9,14) + ".csv").getAbsolutePath(), agents);
					PersonAccessor.writeTrajectory(new File(outputDir + "trajectory/"+ i + "/trajectory_"+ file.getName().substring(9,14) + ".csv").getAbsolutePath(), agents);
					long endtime = System.currentTimeMillis();
					System.out.println(file.getName() + ": " + (endtime - starttime));
				}
			}
		}
		System.out.println("end");
	}	
}
