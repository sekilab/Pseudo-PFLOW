package dcity.gtfs.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.WKTReader;

import jp.ac.ut.csis.pflow.geom2.GeometryUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

public class DataLoader {
	
	private final static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	
	private static Date str2Date(String strTimestamp, SimpleDateFormat format) {
		try {
			return format.parse(strTimestamp);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static final String BOM = "\uFEFF";
	
	private static Map<String,Integer> getMapHeader(String header){
		HashMap<String,Integer> map = new HashMap<>();
		
		if (header.startsWith(BOM)) {
			header = header.substring(1);
		}
		
		header = header.replace("\"", "");
		
		String[] items = header.split(",");
		for (int i = 0; i < items.length; i++) {
			map.put(items[i], i);
		}
		return map;
	}
	
	public static Map<String, Stop> loadStops(File file){
		Map<String,Stop> map = new HashMap<>();
		try(BufferedReader br = new BufferedReader(new FileReader(file));) {
          String record;
          Map<String,Integer> headers = getMapHeader(br.readLine());
          while ((record = br.readLine()) != null) {
        	record = record.replace("\"", "");
          	String[] items = record.split(",");
        	String id = String.valueOf(items[headers.get("stop_id")]);
        	double lon = Double.valueOf(items[headers.get("stop_lon")]);
        	double lat = Double.valueOf(items[headers.get("stop_lat")]);
        	map.put(id, new Stop(id, lon, lat));
          }
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return map;
	}
	
	public static Map<String, Trip> loadTrips(File file){
		Map<String,Trip> map = new HashMap<>();
		try(BufferedReader br = new BufferedReader(new FileReader(file));) {
          String record;
          Map<String,Integer> header = getMapHeader(br.readLine());
          while ((record = br.readLine()) != null) {
        	  record = record.replace("\"", "");
        	  String[] items = record.split(",");
        	  String id = String.valueOf(items[header.get("trip_id")]);
        	  String serviceId = String.valueOf(items[header.get("service_id")]);
        	  String routeId = String.valueOf(items[header.get("route_id")]);
        	  map.put(id, new Trip(id, serviceId, routeId));
          }
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return map;
	}
	
	public static int loadStopTimes(File file, Map<String, Trip> mapTrips, Map<String, Stop> mapStops){
		
		try(BufferedReader br = new BufferedReader(new FileReader(file));) {
          String record;
          Map<String,Integer> header = getMapHeader(br.readLine());
          while ((record = br.readLine()) != null) {
			record = record.replace("\"", "");
			String[] items = record.split(",");
			String tripId = String.valueOf(items[header.get("trip_id")]);
			long arrivalTime = str2Date(items[header.get("arrival_time")], TIME_FORMAT).getTime();
			long departureTime = str2Date(items[header.get("departure_time")], TIME_FORMAT).getTime();
			String stopId = String.valueOf(items[header.get("stop_id")]);
			int sequence = Integer.valueOf(items[header.get("stop_sequence")]);
        	
        	StopTime stopTime = new StopTime(
        			mapStops.get(stopId), sequence, arrivalTime, departureTime);
        	
        	Trip trip = mapTrips.get(tripId);
        	trip.getListStopTimes().add(stopTime);
          }
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return 0;
	}
	
	public static Network load(File file, double[] area) {
		Network network = new Network();	
		WKTReader wktreader = new WKTReader();
		try(BufferedReader br = new BufferedReader(new FileReader(file));) {
          String record;
          br.readLine();
          while ((record = br.readLine()) != null) {
			String[] items = record.split("\t");
			String gid = String.valueOf(items[0]);
			String source = String.valueOf(items[1]);
			String target = String.valueOf(items[2]);
			int length = Integer.valueOf(items[3]);
//			int rclass = Integer.valueOf(items[7]);
//			int reg = Integer.valueOf(items[8]);
			Geometry geom = wktreader.read(items[9]);
			
			LineString line = null;
			if( geom instanceof LineString ) {
				line = LineString.class.cast(geom);
			}
			else if( geom instanceof MultiLineString ) {
				line = (LineString)MultiLineString.class.cast(geom).getGeometryN(0);
			}
			
			int pnum = line.getNumPoints();
			Point p1 = line.getPointN(0);
			Point p2 = line.getPointN(pnum-1);
			
			if (p1.getX() >= area[0] && p1.getX() < area[2] && p1.getY() >= area[1] && p1.getY() < area[3]) {
				Node node1 = network.hasNode(source) ? network.getNode(source) : new Node(source,p1.getX(),p1.getY());
				Node node2 = network.hasNode(target) ? network.getNode(target) : new Node(target,p2.getX(),p2.getY());
				
				List<ILonLat> listPoints = GeometryUtils.createPointList(line);
				
				Link link = new Link(gid, node1, node2, length, length, length, false, listPoints);
				network.addLink(link);
			}
          }
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return network;
	}
}
