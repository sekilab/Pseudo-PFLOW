package dcity.gtfs.view;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import com.vividsolutions.jts.geom.LineString;

import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.GeometryUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.geom2.LonLatTime;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;

public class Generator {
	private static final int TIME_INTERVAL = 10;
	
	private static int getAtNearestPoint(List<ILonLat> points, ILonLat lonlat) {
		double maxDistance = Double.MAX_VALUE;
		int at = -1;
		for (int i = 0; i < points.size(); i++) {
			double distance = DistanceUtils.distance(points.get(i), lonlat);
			if (distance < maxDistance) {
				at = i;
				maxDistance = distance;
			}
		}	
		return at;
	}
	
	private static List<Link> cleaning(List<Link> listLinks){
		List<Link> list = new ArrayList<>();
		String linkId = "";
		for (Link e : listLinks) {
			if (!linkId.equals(e.getLinkID())) {
				list.add(e);
				linkId = e.getLinkID();
			}
		}
		return list;
	}
	
	private static Result generate(Trip trip, Network network){
		Dijkstra routing = new Dijkstra();
		List<ILonLatTime> trajectory = new ArrayList<>();
		List<Link> listLinks = new ArrayList<>();
		
		List<StopTime> stopTimes = trip.getListStopTimes();
		stopTimes.sort(Comparator.comparing(StopTime::getSequence));

		long preDepTime = -1;
		Node preNode = null;
		LonLat preLonLat = null;
		for (StopTime st : stopTimes) {
			long nextArrTime = st.getArrivalTime();
			long nextDepTime = st.getDepartureTime();
			Stop stop = st.getStop();
			LonLat nextLonLat = new LonLat(stop.getLon(),stop.getLat());
			
			Node nextNode = routing.getNearestNode(network, nextLonLat.getLon(), nextLonLat.getLat());
			
			if (preNode != null) {
				if (preDepTime != nextArrTime) {
					// trip
					Route route = routing.getRoute(network, preNode, nextNode);		
					
					if (route != null) {
						listLinks.addAll(route.listLinks());
						
						List<ILonLat> listLLs = route.getTrajectory();
						int pos1 = getAtNearestPoint(listLLs, preLonLat);
						int pos2 = getAtNearestPoint(listLLs, nextLonLat);
						if (pos1 >= 0) {
							listLLs = listLLs.subList(pos1, pos2+1);
						}else {
							listLLs.add(0,preLonLat);
							listLLs.add(listLLs.size()-1, nextLonLat);
						}
						List<ILonLatTime> subTrajectory = TrajectoryUtils.interpolateUnitTime(
								listLLs, new Date(preDepTime), new Date(nextArrTime), TIME_INTERVAL);
						if (subTrajectory != null) {
							trajectory.addAll(subTrajectory.subList(0, subTrajectory.size()-1));
						}
					}
				}
				
				// stay
				for (long i = nextArrTime; i < nextDepTime; i+=TIME_INTERVAL) {
					trajectory.add(new LonLatTime(nextLonLat.getLon(), nextLonLat.getLat(), new Date(i)));
				}
			}
			if (preNode == null || preDepTime != nextArrTime) {
				preDepTime = nextDepTime;
				preNode = nextNode;
				preLonLat = nextLonLat;
			}
		}
		
		return new Result(trip, trajectory, cleaning(listLinks));
	}
	
	private static Map<String, Result> generate(
			Map<String, Trip> mapTrips, Set<String> target, Network network){
		
		Map<String, Result> results = new HashMap<>();
		for (Map.Entry<String, Trip> e : mapTrips.entrySet()) {
			Trip trip = e.getValue();
			String serviceId = trip.getServiceId();
			if (target.contains(serviceId)) {
				Result result = generate(trip, network);
				if (result != null) {
					results.put(e.getKey(),result);
				}
			}
		}
		return results;
	}
		
	private static void write1(File file, Map<String, Result> results) {
		try{
			DateFormat DFORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			int id = 0;
			for (Map.Entry<String, Result> e : results.entrySet()) {
				id++;
				Trip trip = e.getValue().getTrip();
				List<ILonLatTime> tr = e.getValue().getTrajectory();
				for (ILonLatTime llt : tr) {
					long time = llt.getTimeStamp().getTime();
					if (time % (60*1000) == 0) {	
						if (trip.getRouteId().contains("susotomi_suso")) {
						bw.write(String.format("%d,%s,%f,%f,%s,%s,%s", 
								id, DFORMAT.format(llt.getTimeStamp()), llt.getLon(), llt.getLat(), 
								trip.getTripId(),
								trip.getRouteId(), trip.getServiceId()));
						bw.newLine();
						}
					}
				}
			}
			bw.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unused")
	private static void write2(File file, Map<String, Result> results) {
		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			int id = 0;
			for (Map.Entry<String, Result> e : results.entrySet()) {
				id++;
				Trip trip = e.getValue().getTrip();
				List<Link> listLinks = e.getValue().getListLinks();
				for (Link link : listLinks) {
					LineString line = GeometryUtils.createLineString(link.getLineString());
					String wkt = GeometryUtils.createWKTString(line);
					bw.write(String.format("%s\t%s\t%s\t%s", 
							id, link.getLinkID(),
							trip.getTripId(),wkt));
					bw.newLine();
				}
			}
			bw.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}	
	
	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		
		// area, target, btsfDir,roadFile,outFile1,outFile2を書き換えてください�?
		// areaは、�??り�?�すDRMの�?囲です�?�xmin,ymin,xmax,ymaxです�??
		// targetは、対象とするサービスID名を追�?してください�?
		// BtsfDirは、解凍済みのBTFSフォル�?
		// roadFileは、DRMファイルパス?���?�国�?ータからareaを�??り�?�します�?
		// outFile1は、軌跡�?ータの出力�?�パス
		// outFile2は、リンク�?ータの出力�?�パス
		
		double[] area = {138.683004,35.111825,139.052431,35.381073};
		
		File btsfDir = new File("C:/Users/ksym2/Desktop/GTFS-JP/");
		
		File sfile = new File(btsfDir, "stops.txt");
		Map<String, Stop> mapStops = DataLoader.loadStops(sfile);
		System.out.println("stops:" + mapStops.size());
		
		File tfile = new File(btsfDir, "trips.txt");
		Map<String, Trip> mapTrips = DataLoader.loadTrips(tfile);
		System.out.println("trips:" + mapTrips.size());
		
		File stfile = new File(btsfDir, "stop_times.txt");
		DataLoader.loadStopTimes(stfile, mapTrips, mapStops);
		
		File roadFile = new File("C:/Users/ksym2/Desktop/digitalcity/drm/seidrm2017_22.txt");
		Network network = DataLoader.load(roadFile, area);
		System.out.println("links:" + network.linkCount());
		
		//
		Set<String> target = new HashSet<>();
		target.add("平日");
		Map<String, Result> results = generate(mapTrips, target, network);
		System.out.println("trajectories:" + results.size());
		
		File outFile1 = new File("C:/Users/ksym2/Desktop/trajectory.csv");
		@SuppressWarnings("unused")
		File outFile2 = new File("C:/Users/ksym2/Desktop/links.csv");
		
		// lon, lat, time
		write1(outFile1, results);
		// list of links
		//write2(outFile2, results);
		
		System.out.println("end");		
	}
}
