package dcity.gtfs.otp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import utils.DateUtils;

public class Seminar6{
	
	private final static OptRouting OPT_ROUTING = new OptRouting();
	private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private final static Date TARGET_DATE = DateUtils.parse(DATE_FORMAT, "2020-11-11 09:00:00");
	
	private final static double BUS_USER_RATIO = 0.05;
	private final static int MAX_WALK_TRANSIT_DISTANCE = 500;
	private final static int MAX_WALK_DISTANCE = 300;
	
	public static OptRoute calculateAccessibility(OD od) {
		double distance = DistanceUtils.distance(
				od.getLon0(), od.getLat0(), od.getLon1(), od.getLat1());
		if (distance > MAX_WALK_DISTANCE) {
			try {
				return OPT_ROUTING.search(
						od.getLon0(), od.getLat0(), od.getLon1(), od.getLat1(),
						TARGET_DATE, MAX_WALK_TRANSIT_DISTANCE);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public int calculateAccessibility(List<OD> ods) {
		int count = 0;
		int numRoute = 0;
		Random random = new Random(100);
		for (int i = 0; i < ods.size(); i++) {
			OD od = ods.get(i);
			OptRoute route = calculateAccessibility(od);
			if (route != null && route.getLine().size() == 1) {
				numRoute++;
				if (random.nextDouble() < BUS_USER_RATIO) {
					od.setRoute(route);
				}
			}
			if (i % 100 == 0) {
				System.out.println(i + "/" + ods.size() + " " + numRoute);
			}
		}
		System.out.println(count);
		return 0;
	}
	
	public class OD{
		private String id;
		private double lon0;
		private double lat0;
		private double lon1;
		private double lat1;
		private boolean station;
		
		private OptRoute route;
		
		public OD(String id, double lon0, double lat0, double lon1, double lat1, boolean station) {
			super();
			this.id = id;
			this.lon0 = lon0;
			this.lat0 = lat0;
			this.lon1 = lon1;
			this.lat1 = lat1;
			this.route = null;
			this.station = station;
		}

		public String getId() {
			return id;
		}

		public double getLon0() {
			return lon0;
		}

		public double getLat0() {
			return lat0;
		}

		public double getLon1() {
			return lon1;
		}

		public double getLat1() {
			return lat1;
		}

		public OptRoute getRoute() {
			return route;
		}

		public void setRoute(OptRoute route) {
			this.route = route;
		}

		public boolean isStation() {
			return station;
		}
		
		
	}
	
	private double TRAIN_USER_RATIO = 0.1;
	
	public List<OD> loadOD(String path, Network stations) {
		List<OD> res = new ArrayList<>();
		Dijkstra routing = new Dijkstra();
		Random random = new Random(200);
		try (BufferedReader br = new BufferedReader(new FileReader(path));){
            String line;
            br.readLine();
        	
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	String gcode1 = String.valueOf(items[0]);
            	String gcode2 = String.valueOf(items[1]);
            	String id = String.valueOf(items[2]);
            	//String age = String.valueOf(items[3]);
            	int type = Integer.valueOf(items[5]);
            	double lon0 = Double.valueOf(items[6]);
            	double lat0 = Double.valueOf(items[7]);
            	double lon1 = Double.valueOf(items[8]);
            	double lat1 = Double.valueOf(items[9]);
            	
            	ILonLat dest = new LonLat(lon1, lat1);
            	if (type == 5 || type == 6 || type == 8) {
            		continue;
            	}
            	boolean isstation = false;
            	if (!gcode1.equals(gcode2)) {
            		Node station = routing.getNearestNode(stations, lon0, lat0, 10000);
        			if (station != null) {
                		if ((type == 5 || type == 6) || 
                				(type == 8 && random.nextDouble() < TRAIN_USER_RATIO)){	
                			dest = station;
                			isstation = true;
                		}
        			}
            	}
            	res.add(new OD(id, lon0, lat0, dest.getLon(), dest.getLat(),isstation));
            	res.add(new OD(id, dest.getLon(), dest.getLat(), lon0, lat0, isstation));
            }
		}catch (Exception e) {
            e.printStackTrace();
        }
		return res;
	}
	
	private static Network loadStation(String filename) {
		Network res = new Network();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	String id = String.valueOf(items[0]);
            	double lat = Double.valueOf(items[1]);
            	double lon = Double.valueOf(items[2]);
            	res.addNode(new Node(id, lon, lat));
            }
		}catch (Exception e) {
            e.printStackTrace();
        }
		return res;
	}
	
	
	private void write(String filename, List<OD> ods) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (int i = 0; i < ods.size(); i++) {
				OD od = ods.get(i);
				OptRoute route = od.getRoute();
				if (route != null) {
					if (route.getLine().size() == 1) {
						Node node0 = route.getFroms().get(0);
						Node node1 = route.getTos().get(0);
						
						bw.write(String.format("%s,%s,%s,%s,%f,%f,%f,%f,%b", od.getId(),
								route.getLine().get(0),
								node0.getNodeID(),
								node1.getNodeID(),
								node0.getLon(),node0.getLat(),
								node1.getLon(),node1.getLat(),
								od.isStation()
								));
						bw.newLine();
					}	
				}		
			}	
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) {
		String odPath = "C:/Users/ksym2/Desktop/input/result.csv";
		String stPath = "C:/Users/ksym2/Desktop/input/_station.csv";
		String resultPath = "C:/Users/ksym2/Desktop/result.csv";
		
		Network station = loadStation(stPath);
		
		Seminar6 seminar = new Seminar6();
		List<OD> ods = seminar.loadOD(odPath, station);
		
		seminar.calculateAccessibility(ods);
		
		seminar.write(resultPath, ods);
		
		System.out.println("Process finished");
	}
}
