package pseudo.gen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.util.internal.ThreadLocalRandom;

import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import pseudo.acs.DataAccessor;
import pseudo.acs.ModeAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.res.Activity;
import pseudo.res.City;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.EPTCity;
import pseudo.res.EPurpose;
import pseudo.res.ETransport;
import pseudo.res.GLonLat;
import pseudo.res.Country;
import pseudo.res.Person;
import pseudo.res.Speed;
import pseudo.res.Trip;
import utils.Roulette;

public class TripGenerator {

	private ModeAccessor modeAcs;
	private Country japan;

	private static final double MAX_WALK_DISTANCE = 3000;
	private static final double MAX_SEARCH_STATAION_DISTANCE = 5000;
	
	
	public TripGenerator(Country japan, ModeAccessor modeAcs) {
		super();
		this.japan = japan;
		this.modeAcs = modeAcs;
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
		
		
		private int process(Person person) {
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
						// choice mode
						Node station1 = routing.getNearestNode(station, oll.getLon(), oll.getLat(), MAX_SEARCH_STATAION_DISTANCE);
						Node station2 = routing.getNearestNode(station, dll.getLon(), dll.getLat(), MAX_SEARCH_STATAION_DISTANCE);
						ETransport nextMode = null;
						
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
						if (nextMode != ETransport.TRAIN) {
							// single mode
							long travelTime = (long)(distance/Speed.get(nextMode));
							long depTime = next.getStartTime() - travelTime;
							person.addTrip(new Trip(nextMode, purpose, depTime, oll, dll));
						}else {
							long travelTime = 0;
							long time1 = 0;
							long time2 = 0;
							ETransport accMode, egrMode;
							// access time
							{
								EPurpose t_purpose = purpose != EPurpose.HOME ? purpose : convertHomeMode(labor);
								distance = DistanceUtils.distance(oll, station1);
								List<Double> probs = modeAcs.get(type, gender, t_purpose, age, distance);
								probs = probs.subList(0, probs.size()-1);
								int tindex = Roulette.choice(probs, getRandom());
								accMode = modeAcs.getCode(tindex);

								travelTime += (long)(distance / Speed.get(accMode));
								time1 = travelTime;
							}
							// trip time
							{
								distance = DistanceUtils.distance(station1, station2);
								travelTime += (long)(distance / Speed.get(nextMode));
								time2 = travelTime;
							}
							// egress time
							{
								EPurpose t_purpose = purpose != EPurpose.HOME ? purpose : convertHomeMode(labor);
								distance = DistanceUtils.distance(station2, dll);
								List<Double> probs = modeAcs.get(type, gender, t_purpose, age, distance);
								probs = probs.subList(0, probs.size()-1);
								int tindex = Roulette.choice(probs, getRandom());
								egrMode = modeAcs.getCode(tindex);
								travelTime += (long)(distance / Speed.get(egrMode));
							}
							// create sub trips 
							long depTime = next.getStartTime()-travelTime;

							person.addTrip(new Trip(accMode, purpose, depTime, oll, station1));
							person.addTrip(new Trip(nextMode, purpose, depTime+time1, station1, station2));
							person.addTrip(new Trip(egrMode, purpose, depTime+time2, station2, dll));
						}
					}
					pre = next;
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
	

	public static void main(String[] args) throws IOException {
		
		Country japan = new Country();
		
		System.out.println("start");

		String dir;

		InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		Properties prop = new Properties();
		prop.load(inputStream);

		dir = prop.getProperty("root");
		System.out.println("Root Directory: " + dir);
		
		int mfactor = 1;
		
		// load data
		String cityFile = String.format("%s/processing/city_boundary.csv", dir);
		DataAccessor.loadCityData(cityFile, japan);
		
		String stationFile = String.format("%s/processing/base_station.csv", dir);
		Network station = DataAccessor.loadLocationData(stationFile);
		japan.setStation(station);
	
		String modeFile = String.format("%s/processing/act_transport.csv", dir);
		ModeAccessor modeAcs = new ModeAccessor(modeFile);
	
		// create worker
		TripGenerator worker = new TripGenerator(japan, modeAcs);
		String inputDir = String.format("%s/activity/", dir);
		String outputDir = String.format("%s/trip/", dir);

		long starttime = System.currentTimeMillis();
		int start = 1;
		for (int i = 13; i <= 13; i++){
			File prefDir = new File(outputDir, String.valueOf(i));
			System.out.println("Start prefecture:" + i + prefDir.mkdirs());

			File actDir = new File(inputDir, String.valueOf(i));
			for(File file: actDir.listFiles()){
				if (file.getName().contains(".csv")) {
					List<Person> agents = PersonAccessor.loadActivity(file.getAbsolutePath(), mfactor, 0.4, 0.5);
					System.out.println(String.format("%s", file.getName()));
					worker.generate(agents);
					PersonAccessor.writeTrips(new File(outputDir+ i + "/trip_"+ file.getName().substring(9,14) + ".csv").getAbsolutePath(), agents);
				}
			}
		}
		System.out.println("end");
		long endtime = System.currentTimeMillis();
		System.out.println(endtime-starttime);
	}	
}
