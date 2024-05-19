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
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripGenerator_WebAPI_refactor {

	private final Country japan;

	private final Network drm;

	private final SSLContext sslContext;
	private final PoolingHttpClientConnectionManager connManager;
	private final CloseableHttpClient httpClient;
	private final String sessionId;

	private static final double MAX_WALK_DISTANCE = 1000;
	private static final double MAX_SEARCH_STATION_DISTANCE = 1000;
	private static final double FARE_PER_KILOMETER = 51; // Japanese yen, only for vehicle
	private static final double FARE_PER_HOUR = 1000; // Japanese yen, all modes, possible to extend to prefecture level
	private static final double FARE_INIT = 150; // Japanese yen, only for vehicle
	private static final double CAR_AVAILABILITY = 0.2; // Parameter for explain people using car without ownership


	public TripGenerator_WebAPI_refactor(Country japan, Network drm) throws Exception {
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
				new String[]{"TLSv1.2", "TLSv1.3"}, // Specify the TLS versions
				null,
				(hostname, session) -> hostname.equals("157.82.223.35")); // This bypasses hostname verification. Use with caution.


		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
				RegistryBuilder.<ConnectionSocketFactory>create()
						.register("https", sslSocketFactory)
						.build());

		cm.setMaxTotal(100); // Adjust based on your expected total number of concurrent connections
		cm.setDefaultMaxPerRoute(100); // Adjust per route limits based on your API and use case
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
		private List<List<JsonNode>> createSubtrips(JsonNode routeData) {

			List<List<JsonNode>> subtrips = new ArrayList<>();
			if (routeData.size() == 0) return subtrips; // Early return if no data

			List<JsonNode> currentSubtrip = new ArrayList<>();
			// Initialize lastMode with the transportation mode of the first feature
			int lastMode = routeData.get(0).path("properties").path("transportation").asInt();
			JsonNode prevNode = null;

			for (JsonNode feature : routeData) {
				int currentMode = feature.path("properties").path("transportation").asInt();
				// Start a new subtrip if the mode changes
				if (currentMode != lastMode) {
					subtrips.add(currentSubtrip); // Add completed subtrip to list
					currentSubtrip = new ArrayList<>(); // Start a new list for the new subtrip
					currentSubtrip.add(prevNode);
					lastMode = currentMode; // Update the lastMode to the current mode
				}
				// Add current feature to the current subtrip
				currentSubtrip.add(feature);
				prevNode = feature;
			}

			// Add the last subtrip if not already added
			if (!currentSubtrip.isEmpty()) {
				subtrips.add(new ArrayList<>(currentSubtrip));
			}
			return subtrips;
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

		private ETransport determineTransportMode(Person person, LonLat oll, LonLat dll, double distance, Route route, Map<String, String> mixedparams, JsonNode mixedResults){
			ETransport nextMode;

			double roadtime = 1000000;
			double roadcost = 1000000;
			double walkcost = 1000000;

			if(route!=null){
				roadtime = route.getCost(); // seconds
				double roadfare = FARE_INIT + route.getLength() / 1000 * FARE_PER_KILOMETER; // length in meters, 150 as initial cost to avoid short distance car travel
				roadcost = roadfare + roadtime / 3600 * FARE_PER_HOUR;

				double walktime = roadtime * 10; // walk takes 10 times slower than vehicle
				walkcost = walktime / 3600 * FARE_PER_HOUR;
			}
			Map<ETransport, Double> choices = new LinkedHashMap<>();

			if(distance>MAX_WALK_DISTANCE){
				Node station1 = routing.getNearestNode(japan.getStation(), oll.getLon(), oll.getLat(), MAX_SEARCH_STATION_DISTANCE);
				Node station2 = routing.getNearestNode(japan.getStation(), dll.getLon(), dll.getLat(), MAX_SEARCH_STATION_DISTANCE);
				if(station1!=null && station2!=null){
					mixedResults = getMixedRoute(httpClient, sessionId, mixedparams);
					boolean publicTransit = mixedResults.path("num_station").asInt() > 0;
					if (publicTransit) {
						double mixedfare = mixedResults.get("fare").asDouble();
						double mixedtime = mixedResults.get("total_time").asDouble(); // Travel time from WebAPI is in minute
						double mixedcost = mixedfare + mixedtime / 60 * FARE_PER_HOUR;
						choices.put(ETransport.MIX, mixedcost);
					}
				}else{
					double biketime = roadtime * 3;
					double bikecost = biketime / 3600 * FARE_PER_HOUR;
					choices.put(ETransport.BICYCLE, bikecost);
				}
			}else{
				choices.put(ETransport.WALK, walkcost);
			}

			if((route!=null)){
				if(person.hasCar() || getRandom() < CAR_AVAILABILITY){
					choices.put(ETransport.CAR, roadcost);
				}
			}

			nextMode = choices.entrySet()
					.stream()
					.min(Comparator.comparing(Map.Entry::getValue))
					.map(Map.Entry::getKey)
					.orElse(ETransport.NOT_DEFINED);

			return nextMode;
		}

		private int process(Person person) {
			List<SPoint> points = new ArrayList<>();

			List<Activity> activities = person.getActivities();
			Activity pre = activities.get(0);

			Network station = japan.getStation();

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

						JsonNode mixedResults = null;

						Route route = routing.getRoute(drm,	oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());

						if (purpose != EPurpose.HOME){
							nextMode = determineTransportMode(person, oll, dll, distance, route, mixedparams, mixedResults);
							if(i==1&&nextMode==ETransport.CAR){
								primaryMode = nextMode;
							}
						}else{
							if(primaryMode!=null){
								nextMode = primaryMode;
							}else {
								nextMode = determineTransportMode(person, oll, dll, distance, route, mixedparams, mixedResults);
							}
						}
						// create trip or sub-trip
						int multiplier = calculateMultiplier(nextMode);

						long travelTime = 0;


						if (nextMode == ETransport.WALK || nextMode==ETransport.BICYCLE || nextMode==ETransport.CAR){
							if(route!=null){
								travelTime = (long) route.getCost() * multiplier;
							}else{
								travelTime = (long) 3600;
							}

							endTime += travelTime;

							List<Node> nodes = route.listNodes();

							Map<Node,Date> timeMap = TrajectoryUtils.putTimeStamp(
									nodes, new Date(startTime*1000), new Date(endTime*1000));

                            for (ILonLat node : nodes) {
                                Date date = timeMap.get(node);
                                Calendar cl = Calendar.getInstance();
                                configureCalendar(cl, date);

                                date = cl.getTime();

                                SPoint point = new SPoint(node.getLon(), node.getLat()
                                        , date, nextMode, purpose);
                                subpoints.add(point);
                            }
							List<Link> links = route.listLinks();
							for (int k = 1; k <= links.size(); k++) {
								subpoints.get(k).setLink(links.get(k-1).getLinkID());
							}
							points.addAll(subpoints);

							long depTime = next.getStartTime() - travelTime;
							person.addTrip(new Trip(nextMode, purpose, depTime, oll, dll));

						} else if (nextMode==ETransport.MIX && mixedResults != null){
							long mixedtime = mixedResults.path("total_time").asLong() * 60;
							endTime += mixedtime;
							travelTime = mixedtime;
							long depTime = next.getStartTime() - travelTime;

							List<Node> nodes = new ArrayList<>();
							JsonNode routeData = mixedResults.path("features");
							boolean publicTransit = mixedResults.path("num_station").asInt() > 0;

							List<JsonNode> currentSubtrip = new ArrayList<>();
							// Initialize lastMode with the transportation mode of the first feature
							int lastMode = routeData.get(0).path("properties").path("transportation").asInt();
							JsonNode prevNode = null;

                            for(JsonNode feature: routeData){
								int currentMode = feature.path("properties").path("transportation").asInt();

								String pid = feature.path("properties").path("id").toString();
								JsonNode coordinates = feature.path("geometry").path("coordinates");
								if(coordinates.isArray()){
									double lon = coordinates.get(0).asDouble();
									double lat= coordinates.get(1).asDouble();
									nodes.add(new Node(pid, lon, lat));
								}else {
									System.out.println("Coordinate from WebAPI is Not an Array!!");
								}
								if (currentMode != lastMode) {
//									if(prevNode!=null){
//										currentSubtrip.add(prevNode);
//									}
									if(currentSubtrip.size()>1) {
										JsonNode oll_coords = currentSubtrip.get(0).path("geometry").path("coordinates");
										JsonNode dll_coords = currentSubtrip.get(currentSubtrip.size()-1).path("geometry").path("coordinates");
										ETransport mode = getTransport(currentSubtrip.get(1).path("properties").path("transportation").asInt());
										if (mode == ETransport.CAR & publicTransit) {
											mode = ETransport.WALK;
										}
										if (oll_coords.isArray() & dll_coords.isArray()) {
											LonLat moll = new LonLat(oll_coords.get(0).asDouble(), oll_coords.get(1).asDouble());
											LonLat mdll = new LonLat(dll_coords.get(0).asDouble(), dll_coords.get(1).asDouble());
											person.addTrip(new Trip(mode, purpose, depTime, moll, mdll));
											depTime += (long) (DistanceUtils.distance(moll, mdll) / 8.33);
										}else{
											System.out.println("no coordinate from API!");
										}
									}
									currentSubtrip = new ArrayList<>(); // Start a new list for the new subtrip
									currentSubtrip.add(prevNode);
									lastMode = currentMode; // Update the lastMode to the current mode
								}
								currentSubtrip.add(feature);
								prevNode = feature;
							}
							Map<Node,Date> timeMap = TrajectoryUtils.putTimeStamp(
									nodes, new Date(startTime*1000), new Date(endTime*1000));
                            for (ILonLat node : nodes) {
                                Date date = timeMap.get(node);
                                Calendar cl = Calendar.getInstance();
                                configureCalendar(cl, date);

                                date = cl.getTime();

                                SPoint point = new SPoint(node.getLon(), node.getLat()
                                        , date, nextMode, purpose);
                                subpoints.add(point);
                            }
							points.addAll(subpoints);
						} else{
							System.out.println("Did not find correct mode!");
							travelTime = 600;
							error ++;
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
//				16, 31, 32, 39, 36, 18, 41, 1, 40, 46, 47, 6, 5, 37, 30, 3, 19, 38, 7, 45, 17, 42, 44, 2,
//				29, 25, 33, 24, 15, 10, 35, 4, 43, 20, 21, 9, 8, 22, 34, 26, 12, 28, 11, 14, 23, 27, 13
				4, 43, 20, 21, 9, 8, 22, 34, 26, 12, 28, 11, 14, 23, 27, 13
		));

		for (int i: prefectureCodes){

			File tripDir = new File(outputDir+"trip/", String.valueOf(i));
			File trajDir = new File(outputDir+"trajectory/", String.valueOf(i));
			System.out.println("Start prefecture:" + i + tripDir.mkdirs() + trajDir.mkdirs());

			String roadFile = String.format("%sdrm_%02d.tsv", "/home/mdxuser/Data/PseudoPFLOW/processing/DRM/", i);
			Network road = DrmLoader.load(roadFile);
			Double ratio = Double.parseDouble(prop.getProperty("car." + i));

			// create worker
			// TripGenerator_WebAPI worker = new TripGenerator_WebAPI(japan, modeAcs, road);

			File actDir = new File(String.format("%s/activity_merge3/", root), String.valueOf(i));
			for(File file: actDir.listFiles()){
				if (file.getName().contains(".csv")) {
					long starttime = System.currentTimeMillis();
					TripGenerator_WebAPI_refactor worker = new TripGenerator_WebAPI_refactor(japan, road);
					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, ratio);
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
