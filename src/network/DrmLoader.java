package network;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.WKTReader;

import jp.ac.ut.csis.pflow.geom2.GeometryUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.res.DrmLink;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

public class DrmLoader {
	public static Network load(String filepath) {
		File file = new File(filepath);
		Network network = new Network();	
		WKTReader wktreader = new WKTReader();
		try(BufferedReader br = new BufferedReader(new FileReader(file));) {
          String record;
          while ((record = br.readLine()) != null) {
          	String[] items = record.split("\t");
        	String gid = String.valueOf(items[0]);
        	String src = String.valueOf(items[1]);
        	String tgt = String.valueOf(items[2]);
        	int length = Integer.valueOf(items[3]);
        	int rdwdcd = Integer.valueOf(items[4]);
        	int lanecd = Integer.valueOf(items[5]);
        	int regcd = Integer.valueOf(items[6]);
        	int rdclasscd = Integer.valueOf(items[7]);
        	
        	boolean way  = DrmLink.isOneway(regcd);
        	
			// parse geometry object ===================
			Geometry   geom = wktreader.read(items[12]);
			LineString line = null;
			if( geom instanceof LineString ) {
				line = LineString.class.cast(geom);
			}
			else if( geom instanceof MultiLineString ) {
				line = (LineString)MultiLineString.class.cast(geom).getGeometryN(0);
			}
        	
			// build network data =====================
			// nodes
			Point p0 = line.getStartPoint();
			Point p1 = line.getEndPoint(); 
			Node  n0,n1;
			List<ILonLat> list = GeometryUtils.createPointList(line); 
			if( DrmLink.isOnewayAndReverse(regcd) ) {
				n0 = network.hasNode(tgt) ? network.getNode(tgt) : new Node(tgt,p1.getX(),p1.getY());
				n1 = network.hasNode(src) ? network.getNode(src) : new Node(src,p0.getX(),p0.getY());
				if( list != null && !list.isEmpty() ) { Collections.reverse(list); }
			}
			else { 
				n0 = network.hasNode(src) ? network.getNode(src) : new Node(src,p0.getX(),p0.getY());
				n1 = network.hasNode(tgt) ? network.getNode(tgt) : new Node(tgt,p1.getX(),p1.getY());
			}
			// link 
			DrmLink link = new DrmLink(String.valueOf(gid),n0,n1,length,length,length,way,rdclasscd,rdwdcd,lanecd,list);
			network.addLink(link);
			
          }
        } catch (Exception e) {
        	e.printStackTrace();
        }
		return network;
	}
}
