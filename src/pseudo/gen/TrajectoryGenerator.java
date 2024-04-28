package pseudo.gen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.AStar;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.AStarLinkCost;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.logic.transport.DrmTransport;
import jp.ac.ut.csis.pflow.routing4.logic.transport.ITransport;
import jp.ac.ut.csis.pflow.routing4.logic.transport.Transport;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import network.DrmLoader;
import network.RailLoader;
import pseudo.acs.PersonAccessor;
import pseudo.res.ETransport;
import pseudo.res.Person;
import pseudo.res.SPoint;
import pseudo.res.Trip;

public class TrajectoryGenerator {
	private Network road;
	private Network railway;

	private static final double MAX_WALK_DISTANCE = 3000;
	private static final double MAX_SEARCH_STATAION_DISTANCE = 5000;
	
	public TrajectoryGenerator(Network road, Network railway){
		this.road = road;
		this.railway = railway;
	}	

	public class RoutingTask implements Callable<Integer>{
		private int id;
		private String filenameHeader;
		private List<Person> persons;
		
		public RoutingTask(List<Person> persons, int id, String filenameHeader){
			this.id = id;
			this.filenameHeader = filenameHeader;
			this.persons = persons;
		}	
		
		private ITransport getTransport(ETransport mode) {
			switch (mode) {
			case WALK:		return Transport.WALK;
			case BICYCLE:	return Transport.BICYCLE;
			case CAR:		return DrmTransport.VEHICLE;
			case TRAIN:		return Transport.RAILWAY;
			default:		return DrmTransport.VEHICLE;
			}
		}
		
		private long process(Trip trip, List<SPoint> points){
			List<SPoint> subpoints = new ArrayList<>();
			ETransport transport = trip.getTransport();
			Network network = transport != ETransport.TRAIN ? road : railway;

			ILonLat oll = trip.getOrigin();
			ILonLat dll = trip.getDestination();
			
			long startTime = trip.getDepTime();
			long endTime = startTime;
			
			// search route
			LinkCost linkCost = new LinkCost(getTransport(transport));
			Dijkstra routing = new Dijkstra(linkCost);
			Route route = routing.getRoute(network,	oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
//			if((trip.getTransport()!=ETransport.NOT_DEFINED)&& ((trip.getTransport()!=ETransport.WALK)) &&(route.numNodes()==2)){
//				System.out.println(route);
//			}
			// assign time stamp
			if (route != null && route.numNodes() > 0){
				List<Node> nodes = route.listNodes();
				endTime += route.getCost();
				
				Map<Node,Date> timeMap = TrajectoryUtils.putTimeStamp(
						nodes, new Date(startTime*1000), new Date(endTime*1000));

				if(endTime>1080000){ // if the time exceeded next day 6:00
					int count = 0;
					// Node dst = routing.getNearestNode(network, dll.getLon(), dll.getLat(), MAX_SEARCH_STATAION_DISTANCE);
					while(route.getLength()>400){
//						AStarLinkCost starLinkCost = new AStarLinkCost(getTransport(transport));
//						AStar starRouting = new AStar(100, starLinkCost);
//						route = starRouting.getRoute(network,	oll.getLon(), oll.getLat(), dll.getLon(), dll.getLat());
						Node ori = getNearbyNode(network, oll.getLon(), oll.getLat(), MAX_SEARCH_STATAION_DISTANCE);
						Node dst = getNearbyNode(network, dll.getLon(), dll.getLat(), MAX_SEARCH_STATAION_DISTANCE);
						route = routing.getRoute(network, ori, dst);
						oll.setLocation(ori.getLon(), ori.getLat());
						count++;
						System.out.println("count: "+ count+", # of nodes "+route.getLength());
						if(count>10){break;}
					}
				}

				for (int i = 0; i < nodes.size(); i++) {
					ILonLat node = nodes.get(i);
					Date date = timeMap.get(node);
					Calendar cl = Calendar. getInstance();
					cl.setTime(date);
					cl.add(Calendar.YEAR, 45);
					cl.add(Calendar.MONTH, 9);
					date = cl.getTime();
					if (i == 0) {
						node = oll;
					}
					if (i == (nodes.size()-1)) {
						node = dll;
					}
					
					SPoint point = new SPoint(node.getLon(), node.getLat()
							, date, trip.getTransport(), trip.getPurpose());
					subpoints.add(point);	
				}
					
				List<Link> links = route.listLinks();
				for (int i = 1; i <= links.size(); i++) {
					subpoints.get(i).setLink(links.get(i-1).getLinkID());	
				}
				points.addAll(subpoints);
			}
//			Prolixity trajectory debug
//			if (transport==ETransport.WALK){
//				System.out.println("Processing:"+endTime);
//			}
//			if (endTime > 99999){
//				System.out.println("Processing:"+endTime);
//			}
			return endTime;
		}
		
		private void process(Person person) {
			List<SPoint> points = new ArrayList<>();
			List<Trip> trips = person.listTrips();
			long baseTime = 0;
			for (Trip trip : trips) {
				if (trip.getDepTime() <= 0) {
					trip.setDepTime(baseTime);
				}
				baseTime = process(trip, points);
			}
			person.addTrajectory(points);
		}

		@Override
		public Integer call() throws Exception {
			try {
				for (Person p : persons) {
					process(p);		
				}
				String filename = String.format("%s%06d.csv", filenameHeader, id);
				PersonAccessor.writeTrajectory(filename, persons);
				for (Person p : persons) {
					p.clearTrajectory();
					p.clearActivity();
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
			return 0;
		}
	}

	private int process2(List<Person> persons, String header) {
		int numThreads = Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		System.out.println("NumOfThreads:" + numThreads + " " + header);
		
		List<Future<Integer>> features = new ArrayList<>();
		int listSize = persons.size();
		int taskNum = numThreads*2;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = Math.min(listSize, end);
			List<Person> subList = persons.subList(i, end);
			features.add(es.submit(new RoutingTask(subList, i/stepSize, header)));
		}
		es.shutdown();	
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	public int process(List<File> files, String header) {
		for (File file : files) {
			List<Person> person = PersonAccessor.loadTrips(file.getAbsolutePath());
			int stepSize = 40000;
			int listSize = person.size();
			int step = 0;
			for (int i = 0; i < listSize; i+=stepSize) {
				step++;
				int end = i + stepSize;
				end = Math.min(listSize, end);
				process2(person.subList(i, end), String.format("%s_%04d_", header, step));
				System.out.println(file.getName() + " " + step);
			}
		}
		return 0;
	}

	public Node getNearbyNode(Network network, double x, double y, double mindist) {
		Node node = null;
		double dist = mindist;
		double w = mindist * 1.2E-5D;
		double h = mindist * 1.2E-5D;

		for (Node n : network.queryNode(x - w, y - h, x + w, y + h)) {
			double d = DistanceUtils.distance(x, y, n.getLon(), n.getLat());
			// pick random nodes in the select area
			if (d < dist * 0.5 && n.getLat() != y) {
				node = n;
				dist = d;
				if(Math.random()>0.85){break;}
			}
		}

		return node;
	}
	
	public static void main(String[] args) throws IOException {

		String dir;

		InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		Properties prop = new Properties();
		prop.load(inputStream);

		dir = prop.getProperty("root");
		System.out.println("Root Directory: " + dir);
		String roaddir = String.format("%sprocessing/network/", dir);
		
		int start = 14;
		
		String railFile = String.format("%srailnetwork.tsv", roaddir);

		Network railway = RailLoader.load(railFile);

		String inputDir = String.format("%sTrip/", dir);
		String outputDir = String.format("%strajectory/", dir);

		// create trajectories
		for (int i = 13; i <= 13; i++) {
			// create directory
			File prefDir = new File(outputDir, String.valueOf(i));
			System.out.println("Start prefecture:"+i+prefDir.mkdirs());
			// load road network
			String roadFile = String.format("%sdrm_%02d.tsv", roaddir, i);
			Network road = DrmLoader.load(roadFile);
			//System.out.println("prefecture: " + i);
			System.out.println(roadFile + " with links #: " + road.linkCount());
			
			Map<String, List<File>> map = new TreeMap<>();

			File[] files = (new File(inputDir, String.valueOf(i))).listFiles();
			for (File file : files) {
//				if(done.contains(file.getName().substring(0,12))){continue;}
				int pref = Integer.parseInt(file.getName().substring(5, 7));
				if (pref == i) {
					String gcode = file.getName().substring(5, 10);
					List<File> list = map.containsKey(gcode) ? map.get(gcode) : new ArrayList<>();
					list.add(file);
					map.put(gcode, list);
				}
			}
			for (Map.Entry<String, List<File>> e : map.entrySet()) {
				System.out.print(e.getKey());
				long starttime = System.currentTimeMillis();
				TrajectoryGenerator worker = new TrajectoryGenerator(road, railway);
				String header = String.format("%strajectory_%s", outputDir+ i +"/", e.getKey());
				// String header = String.format("%sperson_%s", outputDir, e.getKey());
				Path p = Paths.get(header+"_0001_000000.csv");
				if (Files.exists(p)){
					System.out.println(p.toString()+" has been generated");
					continue;
				}
				worker.process(e.getValue(), header);
				long endtime = System.currentTimeMillis();
				System.out.println(endtime-starttime);
			}
		}
		
		System.out.println("end");		
	}

}
