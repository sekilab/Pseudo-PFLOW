package pseudo.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gtfs.*;
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
import pseudo.res.Trip;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;


class StationUsage {
	private final AtomicInteger getIn;
	private final AtomicInteger getOut;

	public StationUsage() {
		this.getIn = new AtomicInteger(0);
		this.getOut = new AtomicInteger(0);
	}

	public void incrementGetIn() {
		getIn.incrementAndGet();
	}
	public void incrementGetOut() {
		getOut.incrementAndGet();
	}

	public int getGetIn() {
		return getIn.get();
	}
	public int getGetOut() {
		return getOut.get();
	}
}


public class TripGenerator_WebAPI_GTFS {

    private final Network drm;
	private List<gtfs.Trip> trips;
	private List<StopTime> stopTimes;
	private List<Stop> stops;
	private List<FareRule> fareRules;
	private List<Fare> fareAttributes;

	private final SSLContext sslContext;
	private final PoolingHttpClientConnectionManager connManager;
	private final CloseableHttpClient httpClient;
	private final String sessionId;

	private static final double MIN_TRANSIT_DISTANCE = 500;
	// private static final double MAX_SEARCH_STATION_DISTANCE = 5000;
	private static final double FARE_PER_KILOMETER = 25; // Japanese yen, only for vehicle
	private static final double FARE_PER_HOUR = 1000; // Japanese yen, all modes, possible to extend to prefecture level
	private static final double FATIGUE_INDEX_WALK = 2.25;
	private static final double FATIGUE_INDEX_WALK_SLOPE = 1.3;
	private static final double FATIGUE_INDEX_BICYCLE = 1.2;
	private static final double FARE_INIT = 250; // Japanese yen, only for vehicle
	private static final double CAR_AVAILABILITY = 0.3; // Parameter for explain people using car without ownership
//	private static final HashMap<String, Integer> originStationCount = new HashMap<>();
//  Instead of a single HashMap for origin stations:
	private static final ConcurrentHashMap<String, StationUsage> stationUsageMap = new ConcurrentHashMap<>();


	public TripGenerator_WebAPI_GTFS(Country japan, Network drm, List<gtfs.Trip> trips, List<StopTime> stopTimes, List<Stop> stops, List<FareRule> fareRules, List<Fare> fares) throws Exception {
		super();
        this.drm = drm;

		this.trips = trips;
		this.stopTimes = stopTimes;
		this.stops = stops;
		this.fareRules = fareRules;
		this.fareAttributes = fares;

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

		// use for API travel time
		private double getTravelSpeed(int mode){
			switch(mode){
				case 1:
                case 4:
                    return 1.39;
				case 2:		return 16.67;
                default:		return 8.33;
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

		// Occupation coefficient (based on ELabor)
		public double getOccupationCoefficient(ELabor labor) {
			switch (labor) {
				case WORKER:
					return 1.2;
				case JOBLESS:
                case NO_LABOR:
                    return 0.8;
                case INFANT:
				case PRE_SCHOOL:
				case PRIMARY_SCHOOL:
				case SECONDARY_SCHOOL:
				case HIGH_SCHOOL:
				case COLLEGE:
				case JUNIOR_COLLEGE:
					return 1.0;
				default:
					return 1.0;
			}
		}

		// Travel purpose coefficient (based on EPurpose)
		public double getPurposeCoefficient(EPurpose purpose) {
			switch (purpose) {
				case OFFICE:
                case SCHOOL:
                    return 1.5;
				case BUSINESS:
					return 2.0;
				case HOSPITAL:
					return 1.1;
				case HOME:
				case SHOPPING:
                case FREE:
                    return 0.75;
				case EATING:
					return 0.85;
                default:
					return 1.0;
			}
		}

		// Main function to calculate VOT (based only on occupation and purpose)
		public double calculateVOT(ELabor labor, EPurpose purpose) {
			double laborCoef = getOccupationCoefficient(labor);
			double purposeCoef = getPurposeCoefficient(purpose);

			return Math.round(FARE_PER_HOUR * laborCoef * purposeCoef * 10.0) / 10.0;
		}


		private ETransport determineTransportMode(Person person, EPurpose purpose, double distance, Route route, Map<String, String> mixedparams, JsonNode[] mixedResultsHolder) throws ParseException {
			ETransport nextMode;

			Map<ETransport, Double> choices = new LinkedHashMap<>();
			int age = person.getAge();
			ELabor labor = person.getLabor();

			double vot = calculateVOT(labor, purpose);

			if(route!=null){
				double roadtime = route.getCost(); // seconds
				double roadfare = FARE_INIT + route.getLength() / 1000 * FARE_PER_KILOMETER; // length in meters, 150 as initial cost to avoid short distance car travel
				double roadcost = roadfare + roadtime / 3600 * vot;
				if(person.hasCar() || getRandom() < CAR_AVAILABILITY){
					choices.put(ETransport.CAR, roadcost);
				}

				double walktime = route.getLength() / 1.38;
//				double walkcost = walktime / 3600 * vot * FATIGUE_INDEX_WALK;
//				if(age>65){
//					walkcost = walkcost * 1.33;
//				}
				double walkcost = calculateWalkCost(route, age, vot);
				choices.put(ETransport.WALK, walkcost);

				if(person.hasBike()){
					double biketime = walktime / 2;
					double bikecost = biketime / 3600 * vot * FATIGUE_INDEX_BICYCLE;
					choices.put(ETransport.BICYCLE, bikecost);
				}
			}

			if(distance>MIN_TRANSIT_DISTANCE){
				mixedResultsHolder[0] = getMixedRoute(httpClient, sessionId, mixedparams);
				boolean publicTransit = mixedResultsHolder[0].path("num_station").asInt() > 0 && mixedResultsHolder[0].path("fare").asInt() > 0;
				if (publicTransit) {
					double mixedfare = mixedResultsHolder[0].get("fare").asDouble();
					double mixedtime = mixedResultsHolder[0].get("total_time").asDouble(); // Travel time from WebAPI is in minute
					double mixedcost = mixedfare + mixedtime / 60 * vot;
					choices.put(ETransport.MIX, mixedcost);
				}
			}

			TripResult gtfs_result = GTFSRouter.planTrip(drm, Double.valueOf(mixedparams.get("StartLatitude")), Double.valueOf(mixedparams.get("StartLongitude")),
					Double.valueOf(mixedparams.get("GoalLatitude")), Double.valueOf(mixedparams.get("GoalLongitude")), convertTime(mixedparams.get("AppTime")), trips,
					stopTimes, stops, fareRules, fareAttributes
			);
			double gtfsfare = 0;
			double gtfstime;
			if(gtfs_result!=null){
				gtfsfare =  (age > 65) ? 120.0 : 240.0;
				gtfstime = gtfs_result.getTotalTravelTime();

				if(gtfstime!=0){
					double bustime = (double) (new SimpleDateFormat("HH:mm:ss").parse(gtfs_result.getArrivalTime()).getTime()
							- new SimpleDateFormat("HH:mm:ss").parse(gtfs_result.getDepartureTime()).getTime()) / 60000; // minutes

					double gtfscost = gtfsfare + gtfstime / 60 * vot;
//					if(purpose == EPurpose.OFFICE || purpose == EPurpose.SCHOOL || purpose == EPurpose.BUSINESS){
//						gtfscost = gtfscost * 1.5;
//					}
//					if(purpose == EPurpose.HOME){
//						gtfscost = gtfscost / 1.5;
//					}
//					if(purpose == EPurpose.SHOPPING|| purpose == EPurpose.EATING){
//						gtfscost = gtfscost / 1.5;
//					}
//					if(age<18){
//						gtfscost = gtfscost * 3;
//					}
					choices.put(ETransport.COMMUNITY, gtfscost);
				}
			}

			nextMode = choices.entrySet()
					.stream()
					.min                                                                                                                                                                                                                                                                                                                                                                              (Comparator.comparing(Map.Entry::getValue))
					.map(Map.Entry::getKey)
					.orElse(ETransport.NOT_DEFINED);

			if(nextMode==ETransport.COMMUNITY){
				String originStation = gtfs_result.getOriginStation();
				String destinationStation = gtfs_result.getDestinationStation();
				System.out.println("Using Community Bus!");
				System.out.println("Origin Station: " + originStation);
				System.out.println("Destination Station: " + destinationStation);
				System.out.println("Departure Time: " + gtfs_result.getDepartureTime());
				System.out.println("Arrival Time: " + gtfs_result.getArrivalTime());
				System.out.println("Travel Time (including walking): " + gtfs_result.getTotalTravelTime() + " minutes");
				System.out.println("Fare: " + gtfsfare +  " currency units");

//				String originStation = gtfs_result.getOriginStation();
//				originStationCount.put(originStation, originStationCount.getOrDefault(originStation, 0) + 1);

				// "Get in" at origin, "get out" at destination
				stationUsageMap.computeIfAbsent(originStation,  s -> new StationUsage()).incrementGetIn();
				stationUsageMap.computeIfAbsent(destinationStation, s -> new StationUsage()).incrementGetOut();
			}
			if(nextMode==ETransport.MIX){
				List<String> stationNames = List.of("須磨", "山陽須磨");
				determineTerminalTransportation(mixedResultsHolder[0], mixedparams, stationNames, purpose, age, vot);
			}
			return nextMode;

			// return ETransport.NOT_DEFINED;
		}

		public void determineTerminalTransportation(
				JsonNode jsonNode,
				Map<String, String> mixedParams,
				List<String> stationNames,
				EPurpose purpose,
				int age,
				double vot
		) throws ParseException {
			JsonNode features = jsonNode.get("features");

			if (features == null || !features.isArray()) {
				System.out.println("Invalid JSON structure: 'features' not found or not an array.");
				return;
			}

			JsonNode firstMatchingStation = null;
			JsonNode lastMatchingStation = null;

//			for (JsonNode feature : features) {
//				JsonNode station = feature.at("/properties/station");
//				if (station != null && !station.isNull()) {
//					String stationName = station.get("station_name").asText();
//					if (stationNames.contains(stationName)) {
//						if (firstMatchingStation == null) {
//							firstMatchingStation = feature;
//						}
//						lastMatchingStation = feature;
//					}
//				}
//			}

			for (JsonNode feature : features) {
				JsonNode stationNode = feature.at("/properties/station");

				if (stationNode != null && !stationNode.isNull()) {
					String stationName = stationNode.get("station_name").asText();
					if (stationNames.contains(stationName)) {
						firstMatchingStation = feature;
					}
					// 无论匹配与否，都 break，不再继续遍历
					break;
				}
			}

			for (int i = features.size() - 1; i >= 0; i--) {
				JsonNode feature = features.get(i);
				JsonNode stationNode = feature.at("/properties/station");
				if (stationNode != null && !stationNode.isNull()) {
					String stationName = stationNode.get("station_name").asText();
					if (stationNames.contains(stationName)) {
						lastMatchingStation = feature;
					}
					break; // 只处理最后一个 stationNode 就停止
				}
			}

			JsonNode firstPointCoordinates = features.get(0).at("/geometry/coordinates");
			JsonNode lastPointCoordinates = features.get(features.size() - 1).at("/geometry/coordinates");

            if (firstMatchingStation != null && !firstMatchingStation.isNull()) {
                processStation(
                        firstMatchingStation, firstPointCoordinates, mixedParams, age, purpose, vot, "First"
                );
            }
            if (lastMatchingStation != null && !lastMatchingStation.isNull()) {
                processStation(
                        lastMatchingStation, lastPointCoordinates, mixedParams, age, purpose, vot, "Last"
                );
            }
        }

		private void processStation(
				JsonNode station,
				JsonNode pointCoordinates,
				Map<String, String> mixedParams,
				int age,
				EPurpose purpose,
				double vot,
				String stationType
		) throws ParseException {
			if (station == null) {
				return;
			}

			JsonNode stationCoordinates = station.at("/geometry/coordinates");

			TripResult gtfsResult;
			if(stationType.equals("First")){
				gtfsResult = GTFSRouter.planTrip(
						drm,
						pointCoordinates.get(1).asDouble(),
						pointCoordinates.get(0).asDouble(),
						stationCoordinates.get(1).asDouble(),
						stationCoordinates.get(0).asDouble(),
						convertTime(mixedParams.get("AppTime")),
						trips, stopTimes, stops, fareRules, fareAttributes
				);
			}else{
				gtfsResult = GTFSRouter.planTrip(
						drm,
						stationCoordinates.get(1).asDouble(),
						stationCoordinates.get(0).asDouble(),
						pointCoordinates.get(1).asDouble(),
						pointCoordinates.get(0).asDouble(),
						convertTime(mixedParams.get("AppTime")),
						trips, stopTimes, stops, fareRules, fareAttributes
				);
			}


			// Calculate GTFS cost
			double gtfsCost = calculateGTFSRouteCost(gtfsResult, age, purpose, vot);

			// Calculate walking cost
			Route route = routing.getRoute(
					drm,
					pointCoordinates.get(0).asDouble(),
					pointCoordinates.get(1).asDouble(),
					stationCoordinates.get(0).asDouble(),
					stationCoordinates.get(1).asDouble()
			);

			double walkCost = calculateWalkCost(route, age, vot);

			if(stationType.equals("Last")){
				walkCost *= FATIGUE_INDEX_WALK_SLOPE;
			}

			// Compare costs and decide the transportation
			if (gtfsCost < walkCost) {
				System.out.printf("Using Community Bus (%s terminal)!%n", stationType);
				if (gtfsResult != null) {
					System.out.println("Origin Station: " + gtfsResult.getOriginStation());
					System.out.println("Destination Station: " + gtfsResult.getDestinationStation());
					System.out.println("Departure Time: " + gtfsResult.getDepartureTime());
					System.out.println("Arrival Time: " + gtfsResult.getArrivalTime());
					System.out.println("Travel Time (including walking): " + gtfsResult.getTotalTravelTime() + " minutes");
					System.out.println("Fare: " + gtfsResult.getFare() + " currency units");

//					String originStation = gtfsResult.getOriginStation();
//					originStationCount.put(originStation, originStationCount.getOrDefault(originStation, 0) + 1);

					String originStation = gtfsResult.getOriginStation();
					String destinationStation = gtfsResult.getDestinationStation();

					// "Get in" at origin, "get out" at destination
					stationUsageMap.computeIfAbsent(originStation,  s -> new StationUsage()).incrementGetIn();
					stationUsageMap.computeIfAbsent(destinationStation, s -> new StationUsage()).incrementGetOut();
				}
			}
		}

		private double calculateGTFSRouteCost(TripResult gtfsResult, int age, EPurpose purpose, double vot) throws ParseException {
			if (gtfsResult == null) {
				return Double.MAX_VALUE;
			}

			double fare = age > 65 ? 120.0 : 240.0;
			double travelTime = (double) gtfsResult.getTotalTravelTime() ;

			if (travelTime == 0) {
				return Double.MAX_VALUE;
			}

//			double busTime = calculateDuration(gtfsResult.getDepartureTime(), gtfsResult.getArrivalTime()) / 60; // in minutes
//			double fatigueCost = (busTime + (travelTime - busTime) * FATIGUE_INDEX_WALK) / 60 * FARE_PER_HOUR;
//
//			double totalCost = fare + fatigueCost;
			double totalCost = fare + travelTime / 60 * vot;

//			if(purpose == EPurpose.OFFICE || purpose == EPurpose.SCHOOL || purpose == EPurpose.BUSINESS){
//				totalCost = totalCost * 1.5;
//			}
//			if(purpose == EPurpose.HOME){
//				totalCost = totalCost / 1.25;
//			}
//			if(purpose == EPurpose.SHOPPING|| purpose == EPurpose.EATING){
//				totalCost = totalCost / 1.5;
//			}

			return totalCost;
		}

		private double calculateWalkCost(Route route, int age, double vot) {
			double walkTime = route.getLength() / 1.38; // walking speed
			double walkCost = walkTime / 3600 * vot * FATIGUE_INDEX_WALK;
			return age > 65 ? walkCost * 1.3 : walkCost;
		}

		private double calculateDuration(String startTime, String endTime) throws ParseException {
			SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
			return (double) (timeFormat.parse(endTime).getTime() - timeFormat.parse(startTime).getTime()) / 60000; // in minutes
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

		private void handleMixedTransport(ETransport nextMode, EPurpose purpose, JsonNode[] mixedResultsHolder, List<SPoint> subpoints, List<SPoint> points, Person person, Activity next, Route route, long startTime, long endTime, LonLat oll, LonLat dll) {
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
			long currentTime = depTime;
			for (JsonNode feature : routeData) {
				int currentMode = feature.path("properties").path("transportation").asInt();

				if (currentMode != lastMode) {
					if (currentSubtrip.size() > 1) {
						if(lastMode==1){
							long traveltime = 300;
							currentTime += traveltime;
						}else{
							currentTime +=  mixedResultsHolder[0].path("total_transport_time").asLong() * 60;
						}
						addTripForSubtrip(currentSubtrip, lastMode, publicTransit, currentTime, person, purpose);
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
				// depTime += (long) (DistanceUtils.distance(moll, mdll) / getTravelSpeed(mode.getId()));
				person.addTrip(new Trip(mode, purpose, depTime, moll, mdll));
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

		private int process(Person person) throws ParseException {
			List<SPoint> points = new ArrayList<>();

			List<Activity> activities = person.getActivities();
			Activity pre = activities.get(0);

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

						Map<String, String> mixedparams = getStringStringMap(oll, dll, startTime);

						JsonNode[] mixedResultsHolder = new JsonNode[1];

						Route route = routing.getRoute(drm,	oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
						nextMode = determineTransportMode(person, purpose, distance, route, mixedparams, mixedResultsHolder);

						int multiplier = calculateMultiplier(nextMode);
						long travelTime = 0;

						if (nextMode == ETransport.WALK || nextMode == ETransport.BICYCLE || nextMode == ETransport.CAR || nextMode == ETransport.COMMUNITY) {
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
							if(mixedResultsHolder[0].path("features").get(0)==null){
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

					}else{
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
			mixedparams.put("TransportCode", "1"); // 1 only train
			mixedparams.put("AppDate", "20240401");
			mixedparams.put("AppTime", convertSecondsToHHMM(startTime));
			return mixedparams;
		}

		public String convertTime(String timeInHHMM) {
			// Parse the input string as HHMM
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("HHmm");
			LocalTime time = LocalTime.parse(timeInHHMM, inputFormatter);

			// Format the time as HH:MM:SS, ensuring two-digit hour
			DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
			return time.format(outputFormatter);
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

		loadProperties();  // loads 'config.properties' into 'prop'
		String root = prop.getProperty("root");
		String inputDir = prop.getProperty("inputDir");
		System.out.println("Root Directory: " + root);
		System.out.println("Input Directory: " + inputDir);

		// --------------------------------------------------------------------
		// 1) Decide which GTFS version to use: original vs. revised
		//    You can use a boolean, a property, or a command-line argument.
		// --------------------------------------------------------------------
		boolean useRevised = true;
		// or read from properties, e.g.:
		// boolean useRevised = Boolean.parseBoolean(prop.getProperty("gtfs.useRevised"));

		// Build feed folder name based on the choice
		String feedFolder;
		String gtfsLabel;
		if (useRevised) {
			feedFolder = "feed_kobecity_kobe-shiokaze_reflecting_increased";
			gtfsLabel = "revised";
		} else {
			feedFolder = "feed_kobecity_kobe-shiokaze_20241001_20240914083525";
			gtfsLabel = "original";
		}

		// --------------------------------------------------------------------
		// 2) Parse the chosen GTFS data
		// --------------------------------------------------------------------
		List<Stop> stops = GTFSParser.parseStops(String.format("%s%s/stops.txt", inputDir, feedFolder));
		List<gtfs.Trip> trips = GTFSParser.parseTrips(String.format("%s%s/trips.txt", inputDir, feedFolder));
		List<StopTime> stopTimes = GTFSParser.parseStopTimes(String.format("%s%s/stop_times.txt", inputDir, feedFolder));
		List<Fare> fares = GTFSParser.parseFareAttributes(String.format("%s%s/fare_attributes.txt", inputDir, feedFolder));
		List<FareRule> fareRules = GTFSParser.parseFareRules(String.format("%s%s/fare_rules.txt", inputDir, feedFolder));

		// Print a brief sanity check
		System.out.println("Using GTFS: " + gtfsLabel);
		System.out.println("Stops: " + stops.size() + ", Trips: " + trips.size() + ", StopTimes: " + stopTimes.size());

		// --------------------------------------------------------------------
		// 3) Load city data, networks, etc.
		// --------------------------------------------------------------------
		Country japan = new Country();
		String cityFile = String.format("%scity_boundary.csv", inputDir);
		DataAccessor.loadCityData(cityFile, japan);

		String stationFile = String.format("%sbase_station.csv", inputDir);
		Network station = DataAccessor.loadLocationData(stationFile);
		japan.setStation(station);

		String outputDir = "D:/large/PseudoPFLOW/";

		// For demonstration, we only use code for prefecture 28 as in your example
		ArrayList<Integer> prefectureCodes = new ArrayList<>(List.of(28));

		// --------------------------------------------------------------------
		// 4) Generate trips & trajectories for each prefecture
		// --------------------------------------------------------------------
		for (int prefCode : prefectureCodes) {

			File tripDir = new File(outputDir + "trip/", String.valueOf(prefCode));
			File trajDir = new File(outputDir + "trajectory/", String.valueOf(prefCode));
			System.out.println("Start prefecture:" + prefCode
					+ "  tripDir mkdirs=" + tripDir.mkdirs()
					+ "  trajDir mkdirs=" + trajDir.mkdirs());

			// load the road network
			String roadFile = String.format("%sdrm_%02d.tsv", inputDir + "/network/", prefCode);
			Network road = DrmLoader.load(roadFile);

			// read car/bike ratios from properties
			Double carRatio = Double.parseDouble(prop.getProperty("car." + prefCode));
			Double bikeRatio = Double.parseDouble(prop.getProperty("bike." + prefCode));

			// activity files directory
			File actDir = new File(String.format("%s/activity_v2/", root), String.valueOf(prefCode));

			// Prepare the main TripGenerator with the chosen GTFS data
			TripGenerator_WebAPI_GTFS worker = new TripGenerator_WebAPI_GTFS(
					japan,         // the Country object
					road,          // road network
					trips,
					stopTimes,
					stops,
					fareRules,
					fares
			);

			for (File file : Objects.requireNonNull(actDir.listFiles())) {
				if(!file.getName().equals("filtered_activity_28107_2nd.csv")){
					continue;
				}
				if (file.getName().contains(".csv")) {

					// Build output filenames that include the GTFS label
					// e.g.: trip_original_12345.csv or trip_revised_12345.csv
					String fileSuffix = file.getName().substring(9, 14);
					String tripFileName      = tripDir  + "/trip_"       + gtfsLabel + "_" + fileSuffix + ".csv";
					String trajectoryFileName= trajDir  + "/trajectory_" + gtfsLabel + "_" + fileSuffix + ".csv";

					long starttime = System.currentTimeMillis();

					// Load person data
					int mfactor = 1;
					List<Person> agents = PersonAccessor.loadActivity(
							file.getAbsolutePath(),
							mfactor,
							carRatio,
							bikeRatio
					);

					// Generate trips for each agent
					System.out.printf("Processing file: %s (pref %d, GTFS=%s)%n",
							file.getName(), prefCode, gtfsLabel);
					worker.generate(agents);

					// Write trips/trajectories to CSV
					PersonAccessor.writeTrips(tripFileName, agents);
					PersonAccessor.writeTrajectory(trajectoryFileName, agents);

					long endtime = System.currentTimeMillis();
					System.out.println(file.getName() + " processed in " + (endtime - starttime) + " ms");

					// ----------------------------------------------------------------
					// 5) Print station usage to screen AND save to a CSV (optional)
					// ----------------------------------------------------------------
					System.out.println("Origin Station usage counts:");
					File stationCountFile = new File(tripDir, "station_count_" + gtfsLabel + "_" + fileSuffix + ".csv");
					try (PrintWriter pw = new PrintWriter(stationCountFile)) {
						pw.println("StationIDOrName,GetIn,GetOut,Total");
						// stationUsageMap is <String, StationUsage>
						for (Map.Entry<String, StationUsage> e : stationUsageMap.entrySet()) {
							String stationId   = e.getKey();
							StationUsage usage = e.getValue();
							int inCount  = usage.getGetIn();
							int outCount = usage.getGetOut();
							int total    = inCount + outCount;
							// Print to screen
							System.out.println(stationId + ": getIn=" + inCount +
									", getOut=" + outCount + ", total=" + total);
							// Write CSV row
							pw.println(stationId + "," + inCount + "," + outCount + "," + total);
						}
					}
				}
			}
		}

		System.out.println("All done.");
	}

//	public static void main(String[] args) throws Exception {
//
//		String inputDir;
//		String root;
//
//		loadProperties();
//		InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
//		if (inputStream == null) {
//			throw new FileNotFoundException("config.properties file not found in the classpath");
//		}
//		Properties prop = new Properties();
//		prop.load(inputStream);
//
//		root = prop.getProperty("root");
//		inputDir = prop.getProperty("inputDir");
//		System.out.println("Root Directory: " + root);
//		System.out.println("Input Directory: " + inputDir);
//
//		int mfactor = 1;
//
//		Country japan = new Country();
//		// load data
//		String cityFile = String.format("%scity_boundary.csv", inputDir);
//		DataAccessor.loadCityData(cityFile, japan);
//
//		// original GTFS data
////		List<Stop> stops = GTFSParser.parseStops(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/stops.txt", inputDir));
////		List<gtfs.Trip> trips = GTFSParser.parseTrips(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/trips.txt", inputDir));
////		List<StopTime> stopTimes = GTFSParser.parseStopTimes(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/stop_times.txt", inputDir));
////		List<Fare> fares = GTFSParser.parseFareAttributes(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/fare_attributes.txt", inputDir));
////		List<FareRule> fareRules = GTFSParser.parseFareRules(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/fare_rules.txt", inputDir));
//
//		// revised GTFS data
//		List<Stop> stops = GTFSParser.parseStops(String.format("%sfeed_kobecity_kobe-shiokaze_reflecting_increased/stops.txt", inputDir));
//		List<gtfs.Trip> trips = GTFSParser.parseTrips(String.format("%sfeed_kobecity_kobe-shiokaze_reflecting_increased/trips.txt", inputDir));
//		List<StopTime> stopTimes = GTFSParser.parseStopTimes(String.format("%sfeed_kobecity_kobe-shiokaze_reflecting_increased/stop_times.txt", inputDir));
//		List<Fare> fares = GTFSParser.parseFareAttributes(String.format("%sfeed_kobecity_kobe-shiokaze_reflecting_increased/fare_attributes.txt", inputDir));
//		List<FareRule> fareRules = GTFSParser.parseFareRules(String.format("%sfeed_kobecity_kobe-shiokaze_reflecting_increased/fare_rules.txt", inputDir));
//
//
//		String stationFile = String.format("%sbase_station.csv", inputDir);
//		Network station = DataAccessor.loadLocationData(stationFile);
//		japan.setStation(station);
//
//		String outputDir = "/large/PseudoPFLOW/";
//
//		ArrayList<Integer> prefectureCodes = new ArrayList<>(Arrays.asList(
//				28
//				// 22
//				// 0, 16, 31, 32, 39, 36, 18, 41, 1, 40, 46,
//				// 13,
//				// 11,
//				// 14,
//				// 12,
//				// 11, 15,
//				// 4, 43, 20, 21,
//				// 9,
//				// 41, 40, 39, 36, 32, 31, 18, 15,
//				// 8, 24, 25, 29, 33, 34, 35, 44, 45, 47,
//				// 19
//
//		));
//
//		for (int i: prefectureCodes){
//
//			File tripDir = new File(outputDir+"trip/", String.valueOf(i));
//			File trajDir = new File(outputDir+"trajectory/", String.valueOf(i));
//			System.out.println("Start prefecture:" + i +" "+ tripDir.mkdirs() +" "+ trajDir.mkdirs());
//
//			String roadFile = String.format("%sdrm_%02d.tsv", inputDir+"/network/", i);
//
//			Network road = DrmLoader.load(roadFile);
//			Double carRatio = Double.parseDouble(prop.getProperty("car." + i));
//			Double bikeRatio = Double.parseDouble(prop.getProperty("bike." + i));
//
//			File actDir = new File(String.format("%s/activity_v2/", root), String.valueOf(i));
//			for(File file: Objects.requireNonNull(actDir.listFiles())){
//				if (file.getName().contains(".csv")) {
//					String tripFileName = outputDir + "trip/" + i + "/trip_" + file.getName().substring(9,14) + ".csv";
//					String trajectoryFileName = outputDir + "trajectory/" + i + "/trajectory_" + file.getName().substring(9,14) + ".csv";
//
//					// Check if the files already exist
//
//					long starttime = System.currentTimeMillis();
//					TripGenerator_WebAPI_GTFS worker = new TripGenerator_WebAPI_GTFS(japan, road, trips, stopTimes, stops, fareRules, fares);
//					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, carRatio, bikeRatio);
//					System.out.printf("%s%n", file.getName());
//					worker.generate(agents);
//					PersonAccessor.writeTrips(tripFileName, agents);
//					PersonAccessor.writeTrajectory(trajectoryFileName, agents);
//					long endtime = System.currentTimeMillis();
//					System.out.println(file.getName() + ": " + (endtime - starttime));
//
//					for (String stop : originStationCount.keySet()) {
//						System.out.println(stop + ": " + originStationCount.get(stop) + " times");
//					}
//				}
//			}
//
//		}
//		System.out.println("end");
//	}
}
