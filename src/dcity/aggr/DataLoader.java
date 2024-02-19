package dcity.aggr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.io.WKTReader;

import jp.ac.ut.csis.pflow.geom2.GeometryUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.geom2.LonLatTime;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import utils.DateUtils;

public class DataLoader {
	
	private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	
	public static List<Person> loadPFlow(File file) {
		List<Person> plist = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(file));) {
			String line;
			String preid = "";
			List<ILonLatTime> logs = null;
			while ((line = br.readLine()) != null) {
				String[] items = line.split(",");
				String id = String.valueOf(items[0]);
				Date date = DateUtils.parse(DATE_FORMAT, items[1]);
				double lon = Double.valueOf(items[2]);
				double lat = Double.valueOf(items[3]);
				
//				if (lat < 35.146091) {
//					continue;
//				}
				
				if (!preid.equals(id)) {
					logs = new ArrayList<>();
					plist.add(new Person(id, logs));
				}
				logs.add(new LonLatTime(lon, lat, date));
				preid = id;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return plist;
	}
	
	public static Network loadNetwork(File file) {
		Network network = new Network();
		WKTReader wktreader = new WKTReader();
		try (BufferedReader br = new BufferedReader(new FileReader(file));) {
			String record;
			br.readLine();
			while ((record = br.readLine()) != null) {
				String[] items = record.split("\t");
				String gid = String.valueOf(items[0]);
				String source = String.valueOf(items[1]);
				String target = String.valueOf(items[2]);
				int length = Integer.valueOf(items[3]);
				double x0 = Double.valueOf(items[4]);
				double y0 = Double.valueOf(items[5]);
				double x1 = Double.valueOf(items[6]);
				double y1 = Double.valueOf(items[7]);
//
//				if (y0 < 35.146091) {
//					continue;
//				}
//				
				Geometry geom = wktreader.read(items[9]);

				LineString line = null;
				if (geom instanceof LineString) {
					line = LineString.class.cast(geom);
				} else if (geom instanceof MultiLineString) {
					line = (LineString) MultiLineString.class.cast(geom).getGeometryN(0);
				}

				Node node1 = network.hasNode(source) ? network.getNode(source) : new Node(source, x0, y0);
				Node node2 = network.hasNode(target) ? network.getNode(target) : new Node(target, x1, y1);

				List<ILonLat> listPoints = GeometryUtils.createPointList(line);

				Link link = new Link(gid, node1, node2, length, length, length, false, listPoints);
				network.addLink(link);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return network;
	}
}
