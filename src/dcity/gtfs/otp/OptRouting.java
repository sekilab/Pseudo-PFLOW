package dcity.gtfs.otp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.util.PolylineEncoder;
import org.opentripplanner.util.model.EncodedPolylineBean;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.res.Node;

public class OptRouting{

	private String ROUTING_PATH = "http://localhost:8080/otp/routers/default/plan";
	
	private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	private final static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	
	private String createUrl(double x0, double y0, double x1, double y1, Date startDate, int maxWalkDistance) {
		Map<String, String> map = new HashMap<String, String>();
		map.put("fromPlace", String.format("%.6f,%.6f", y0, x0));
		map.put("toPlace", String.format("%.6f,%.6f", y1, x1));
		map.put("time", TIME_FORMAT.format(startDate));
		map.put("date", DATE_FORMAT.format(startDate));
		map.put("mode", "TRANSIT,WALK");
		map.put("maxWalkDistance", String.valueOf(maxWalkDistance));
		map.put("arriveBy", "false");
		map.put("numItineraries", "1");
		map.put("maxTransfers", "0");
		
		StringJoiner query = new StringJoiner("&");
		for (Map.Entry<String, String> parameter : map.entrySet()) {
		  query.add(parameter.getKey() + "=" + parameter.getValue());
		}
		
		StringJoiner url = new StringJoiner("?");
		url.add(ROUTING_PATH);
		url.add(query.toString());
		
		return url.toString();
	}
	
	private OptRoute createRoute(String strJson) {
		JSONObject root = new JSONObject(strJson);
		if (!root.has("plan")) {
			return null;
		}
		JSONObject plan = (JSONObject)root.get("plan");
		JSONArray itineraries = (JSONArray)plan.get("itineraries");
		JSONObject itinerary = (JSONObject)itineraries.get(0);
		
		// fare
		int cents = 0;
		if (itinerary.has("fare")) {
			JSONObject fare = (JSONObject)itinerary.get("fare");
			fare = (JSONObject)fare.get("fare");
			JSONObject regular = (JSONObject)fare.get("regular");
			cents = regular.getInt("cents");
		}
		// duration
		int duration = itinerary.getInt("duration");
		
		// legs
		double distance = 0;
		List<List<ILonLatTime>> trajectory = new ArrayList<>();
		List<String> mode = new ArrayList<>();
		List<String> line = new ArrayList<>();
		List<Node> from_st = new ArrayList<>();
		List<Node> to_st = new ArrayList<>();
		JSONArray legs = (JSONArray)itinerary.get("legs");
		for (int i = 0; i < legs.length(); i++) {
			JSONObject e = legs.getJSONObject(i);
			long startTime = e.getLong("startTime");
			long endTime = e.getLong("endTime");
			
			distance += e.getDouble("distance");
			
			JSONObject geom = (JSONObject)e.get("legGeometry");
			
			String points = geom.getString("points");
			int length = geom.getInt("length");
			
			EncodedPolylineBean bean = new EncodedPolylineBean(points, null, length);
	        List<Coordinate> coords = PolylineEncoder.decode(bean);
	        List<ILonLat> lls = new ArrayList<>();
	        for (Coordinate coord : coords) {
	        	lls.add(new LonLat(coord.getX(), coord.getY()));
	        }
	        
	        List<ILonLatTime> sublist = TrajectoryUtils.interpolateUnitTime(
	        		lls, new Date(startTime), new Date(endTime));
	        trajectory.add(sublist);
	        
	        mode.add(e.getString("mode"));	 
	        
	        
	        
	        if (e.getString("mode").equals("BUS")) {
	        	line.add(e.getString("route"));
	        	JSONObject from = (JSONObject)e.get("from");
	        	JSONObject to = (JSONObject)e.get("to");
	        	
	        	from_st.add(new Node(
	        			from.getString("name"),
	        			from.getDouble("lon"),
	        			from.getDouble("lat")));	        	
	        	to_st.add(new Node(
	        			to.getString("name"),
	        			to.getDouble("lon"),
	        			to.getDouble("lat")));
	        }
		}
		return new OptRoute(distance, duration, cents, trajectory, mode, line, from_st, to_st);
	}
	
	public OptRoute search(double x0, double y0, double x1, double y1, 
			Date startDate, int maxWalkDistance) {
		
		String strUrl = createUrl(x0, y0, x1, y1, startDate, maxWalkDistance);
		HttpURLConnection urlConn = null;
		OptRoute route = null;

		try{
			URL url = new URL(strUrl);
			urlConn = (HttpURLConnection)url.openConnection();
			urlConn.setRequestMethod("GET");
			urlConn.setRequestProperty("Accept", "application/json");
			urlConn.setConnectTimeout(1000*5);
			urlConn.connect();
			int status = urlConn.getResponseCode();
			if (status == HttpURLConnection.HTTP_OK) {
				StringBuffer result = new StringBuffer();
				try(BufferedReader reader = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));){
					String line = null;
					while ((line = reader.readLine()) != null) {
	                    result.append(line);
	                }
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				route = createRoute(result.toString());
			
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (urlConn != null) {
				urlConn.disconnect();
			}
		}
		return route;
	}
}
