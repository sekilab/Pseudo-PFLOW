package pseudo.gen;

import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import org.geojson.FeatureCollection;
import org.jboss.netty.util.internal.ThreadLocalRandom;
import pseudo.acs.DataAccessor;
import pseudo.acs.ModeAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.res.*;
import utils.Roulette;

import java.io.File;
import java.io.FileNotFoundException;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;

public class TripGenerator_WebAPI {

	private ModeAccessor modeAcs;
	private Country japan;

	private SSLContext sslContext;
	private CloseableHttpClient httpClient;
	private String sessionId;

	private static final double MAX_WALK_DISTANCE = 3000;
	private static final double MAX_SEARCH_STATAION_DISTANCE = 5000;


	public TripGenerator_WebAPI(Country japan, ModeAccessor modeAcs) throws Exception {
		super();
		this.japan = japan;
		this.modeAcs = modeAcs;
		this.sslContext = createSSLContext();
		this.httpClient = createHttpClient();
		this.sessionId = createSession();
	}

	private SSLContext createSSLContext() throws Exception {
		return SSLContextBuilder.create()
				.loadTrustMaterial(new TrustSelfSignedStrategy())
				.build();
	}

	private CloseableHttpClient createHttpClient() {
		return HttpClients.custom().setSSLContext(this.sslContext)
				.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
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
		private List<Person> listAgents;
		private int error;
		private int total;
		private final Dijkstra routing = new Dijkstra();

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
			EGender gender = person.getGender();
			ELabor labor = person.getLabor();
			int age = person.getAge();
			Network station = japan.getStation();

			City city = japan.getCity(pre.getGcode());
			if(pre.getGcode().length()<5){
				city = japan.getCity("0" + pre.getGcode());
			}

			EPTCity type = city.getPTType();
			ETransport primaryMode = null;
			if (activities.size() <= 1) {
				person.addTrip(new Trip(ETransport.NOT_DEFINED, EPurpose.HOME, 0, pre.getLocation(), pre.getLocation()));
			}else {
				for (int i = 1; i < activities.size(); i++) {

					Activity next = activities.get(i);
					GLonLat oll = pre.getLocation();
					GLonLat dll = next.getLocation();
					
					EPurpose purpose = next.getPurpose();
					double distance = DistanceUtils.distance(oll, dll);

					if (distance > 0) {
						ETransport nextMode = null;

						Map<String, String> params = new HashMap<>();
						params.put("UnitTypeCode", "2");
						params.put("StartLongitude", String.valueOf(oll.getLon()));
						params.put("StartLatitude", String.valueOf(oll.getLat()));
						params.put("GoalLongitude", String.valueOf(dll.getLon()));
						params.put("GoalLatitude", String.valueOf(dll.getLat()));
						// params.put("TransportCode", "3");

						Map<String, String> carparams = new HashMap<>(params);
						carparams.put("TransportCode", "6");
						carparams.put("OutputNum", "1");

						Map<String, String> mixedparams = new HashMap<>(params);
						mixedparams.put("TransportCode", "3");

						// Map<String, String> walkparams = new HashMap<>(params);
						// walkparams.put("TransportCode", "1");

//						long starttime = System.currentTimeMillis();
						JsonNode mixedResults = getMixedRoute(httpClient, sessionId, mixedparams);
						double mixedfare = mixedResults.get("fare").asDouble();
						// System.out.println(mixedfare);
						double mixedtime = mixedResults.get("total_time").asDouble();
						Double mixedcost = mixedfare + mixedtime / 60 * 1000.0;

						JsonNode roadResults = getRoadRoute(httpClient, sessionId, carparams);
						double roadfare = roadResults.get("features").get(0).get("properties").get("fare").asDouble();
						// System.out.println(roadfare);
						double roadtime = roadResults.get("features").get(0).get("properties").get("required_time").asDouble();
						Double roadcost = roadfare + roadtime / 60 * 1000.0;

						// JsonNode walkResults = getRoadRoute(httpClient, sessionId, walkparams);
						double walktime = roadtime * 10;
						Double walkcost = walktime / 60 * 1000.0;


//						long endtime = System.currentTimeMillis();
//						System.out.println(endtime +" :"+ (endtime-starttime));
						// String features = results.get("features");
						// JsonNode routes = results.get("features");
						// for(JsonNode feature: routes){
								// System.out.println(feature);
						// };

						// choice mode

						if (purpose != EPurpose.HOME){
							Map<ETransport, Double> choices = new LinkedHashMap<>();
							choices.put(ETransport.MIX, mixedcost);
							choices.put(ETransport.WALK, walkcost);
							if(person.hasCar()){
								choices.put(ETransport.CAR, roadcost);
							}
							nextMode = choices.entrySet()
									.stream()
									.min(Comparator.comparing(Map.Entry::getValue))
									.map(Map.Entry::getKey)
									.orElse(ETransport.NOT_DEFINED);
							if(i==1&&nextMode==ETransport.CAR){
								primaryMode = nextMode;
							}
						}else{
							if(primaryMode!=null){
								nextMode = primaryMode;
							}else {
								Map<ETransport, Double> choices = new LinkedHashMap<>();
								choices.put(ETransport.MIX, mixedcost);
								choices.put(ETransport.WALK, walkcost);
								if (person.hasCar()) {
									choices.put(ETransport.CAR, roadcost);
								}
								nextMode = choices.entrySet()
										.stream()
										.min(Comparator.comparing(Map.Entry::getValue))
										.map(Map.Entry::getKey)
										.orElse(ETransport.NOT_DEFINED);
							}
						}

						Node station1 = routing.getNearestNode(station, oll.getLon(), oll.getLat(), MAX_SEARCH_STATAION_DISTANCE);
						Node station2 = routing.getNearestNode(station, dll.getLon(), dll.getLat(), MAX_SEARCH_STATAION_DISTANCE);

						if (purpose != EPurpose.HOME) {
							List<Double> modeProbs = modeAcs.get(type, gender, purpose, age, distance);
							if (station1 == null || station2 == null || station1.getNodeID().equals(station2.getNodeID())){
								modeProbs = modeProbs.subList(0, modeProbs.size() - 1);
							}
							int tindex = Roulette.choice(modeProbs, getRandom());
							nextMode = modeAcs.getCode(tindex);
						}else {
							nextMode = primaryMode;
							if(nextMode == ETransport.TRAIN && !(station1 != null && station2 != null)) {
								nextMode = ETransport.CAR;
							}else if (nextMode == ETransport.WALK && distance > MAX_WALK_DISTANCE) {
								nextMode = ETransport.CAR;
							}
						}

						// store primary mode
						if (purpose == EPurpose.OFFICE || purpose == EPurpose.SCHOOL) {
							primaryMode = nextMode;
						}else {
							primaryMode = nextMode;
						}

						// create trip or sub trips
//						if (nextMode != ETransport.TRAIN) {
//							// single mode
//							long travelTime = (long)(distance/Speed.get(nextMode));
//							long depTime = next.getStartTime() - travelTime;
//							person.addTrip(new Trip(nextMode, purpose, depTime, oll, dll));
//						}else {
//							long travelTime = 0;
//							long time1 = 0;
//							long time2 = 0;
//							ETransport accMode, egrMode;
//							// access time
//							{
//								EPurpose t_purpose = purpose != EPurpose.HOME ? purpose : convertHomeMode(labor);
//								distance = DistanceUtils.distance(oll, station1);
//								List<Double> probs = modeAcs.get(type, gender, t_purpose, age, distance);
//								probs = probs.subList(0, probs.size()-1);
//								int tindex = Roulette.choice(probs, getRandom());
//								accMode = modeAcs.getCode(tindex);
//
//								travelTime += (long)(distance / Speed.get(accMode));
//								time1 = travelTime;
//							}
//							// trip time
//							{
//								distance = DistanceUtils.distance(station1, station2);
//								travelTime += (long)(distance / Speed.get(nextMode));
//								time2 = travelTime;
//							}
//							// egress time
//							{
//								EPurpose t_purpose = purpose != EPurpose.HOME ? purpose : convertHomeMode(labor);
//								distance = DistanceUtils.distance(station2, dll);
//								List<Double> probs = modeAcs.get(type, gender, t_purpose, age, distance);
//								probs = probs.subList(0, probs.size()-1);
//								int tindex = Roulette.choice(probs, getRandom());
//								egrMode = modeAcs.getCode(tindex);
//								travelTime += (long)(distance / Speed.get(egrMode));
//							}
//							// create sub trips
//							long depTime = next.getStartTime()-travelTime;
//
//							person.addTrip(new Trip(accMode, purpose, depTime, oll, station1));
//							person.addTrip(new Trip(nextMode, purpose, depTime+time1, station1, station2));
//							person.addTrip(new Trip(egrMode, purpose, depTime+time2, station2, dll));
//						}
					}
					// pre = next;
				}
			}
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
		int taskNum = numThreads * 10;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
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

//	private static String createSession(CloseableHttpClient httpClient) throws Exception {
//		HttpPost createSessionPost = new HttpPost(prop.getProperty("api.createSessionURL"));
//
//		List<NameValuePair> sessionParams = new ArrayList<>();
//		sessionParams.add(new BasicNameValuePair("UserID", prop.getProperty("api.userID")));
//		sessionParams.add(new BasicNameValuePair("Password", prop.getProperty("api.password")));
//		createSessionPost.setEntity(new UrlEncodedFormEntity(sessionParams));
//
//		HttpResponse sessionResponse = executePostRequest(httpClient, createSessionPost);
//		if (sessionResponse.getStatusLine().getStatusCode() == 200) {
//			String sessionResponseBody = EntityUtils.toString(sessionResponse.getEntity());
//			System.out.println("Session created successfully");
//			System.out.println(sessionResponseBody);
//			return sessionResponseBody.split(",")[1].trim().replace("\r", "").replace("\n", "");
//		} else {
//			System.out.println("Failed to create session: " + sessionResponse.getStatusLine().getStatusCode());
//			return "";
//		}
//	}

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
	
		// create worker
		TripGenerator_WebAPI worker = new TripGenerator_WebAPI(japan, modeAcs);
		String outputDir = String.format("%s/trip/", root);

//		if (!worker.sessionId.isEmpty()) {
//			Map<String, String> params = new HashMap<>();
//			params.put("UnitTypeCode", "2");
//			params.put("StartLongitude", "139.56629225");
//			params.put("StartLatitude", "35.663611996");
//			params.put("GoalLongitude", "139.75884674036305");
//			params.put("GoalLatitude", "35.69638343647759");
//			params.put("TransportCode", "3");
//
//			getMixedRoute(worker.httpClient, worker.sessionId, params);
//		}


		int start = 1;
		for (int i = 13; i <= 13; i++){
			File prefDir = new File(outputDir, String.valueOf(i));
			System.out.println("Start prefecture:" + i + prefDir.mkdirs());
			Double ratio = Double.parseDouble(prop.getProperty("car." + i));

			File actDir = new File(String.format("%s/activity/", root), String.valueOf(i));
			for(File file: actDir.listFiles()){
				if (file.getName().contains(".csv")) {
					long starttime = System.currentTimeMillis();
					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, ratio);
					System.out.println(String.format("%s", file.getName()));
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
