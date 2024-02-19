package dcity.aggr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import jp.ac.ut.csis.pflow.geom2.ILonLatTime;

public class BoundaryVolume {
	
	private static final Calendar calendar = Calendar.getInstance();
	private static final int MFACTOR = 10;
	private static final int INTERVAL = 15*60;
	private static final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

	private static int getTimeCode(Date date) {
		Date tmp = new Date(date.getTime() - date.getTime() % (INTERVAL*1000));
		calendar.setTime(tmp);
		String key = String.format("%02d%02d", 
				calendar.get(Calendar.HOUR_OF_DAY),
				calendar.get(Calendar.MINUTE));
		return Integer.valueOf(key);
	}
	
	private static void countUp(int timeCode, String boundary, Map<Integer,Map<String, Integer>> map) {
		Map<String, Integer> submap = map.containsKey(timeCode) ? map.get(timeCode) : new HashMap<>();
		int volume = submap.containsKey(boundary) ? submap.get(boundary) : 0;
		submap.put(boundary, volume + MFACTOR);
		map.put(timeCode, submap);
	}
	
	public String searchBoundary(double x, double y, Boundaries boundaries) {
		double r = 0.01;
		double w = r;
		double h = r;
		List<Geometry> geometries = boundaries.query(x - w, y - h, x + w, y + h);
		Point pt = geometryFactory.createPoint(new Coordinate(x, y));
		for (Geometry g : geometries) {
			if (g.covers(pt)==true) {
				return boundaries.getGeometry(g);
			}			
		}
		return null;
	}
	
	private void process(Person person, Boundaries boudaries, Map<Integer,Map<String, Integer>> aggr) {
		List<ILonLatTime> llts = person.getListLLTs();
		Map<Integer, String> map = new HashMap<>(); 
		for (int i=0; i < llts.size(); i++)	{
			ILonLatTime llt = llts.get(i);
			int code = getTimeCode(llt.getTimeStamp());
			if (i < llts.size() - 1) {
				ILonLatTime nextllt = llts.get(i+1);
				int nextcode = getTimeCode(nextllt.getTimeStamp());
				if (code == nextcode) {
					continue;
				}
			}
			String boundary = searchBoundary(llt.getLon(), llt.getLat(), boudaries);
			if (boundary != null) {
				map.put(code, boundary);
			}
		}
		
		String boundary = "";
		for (int i = -32400; i < (-32400+24*3600); i+=INTERVAL) {
			int code = getTimeCode(new Date(i*1000L));
			boundary = map.containsKey(code) ? map.get(code) : boundary;
			countUp(code, boundary, aggr);
		}
	}
		
	private Map<Integer,Map<String, Integer>> process(List<Person> listPerson, Boundaries boudaries) {
		int count = 0;
		Map<Integer,Map<String, Integer>> aggr = new HashMap<>();
		for (Person p : listPerson) {
			process(p, boudaries, aggr);
			if (count++ % 1000 == 0) {
				System.out.println(count);
			}
		}
		return aggr;
	}

	
	private static void write(File file, Map<Integer,Map<String, Integer>> result, Boundaries boudaries) {	
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(file));){
			List<Integer> listCodes = new ArrayList<>(result.keySet());
			Collections.sort(listCodes);
			List<String> names = boudaries.getNames();
			for (String name : names) {
				List<String> vols = new ArrayList<>();
				for (Integer code : listCodes) {
					Map<String, Integer> map = result.get(code);
					long volume = map.containsKey(name) ? map.get(name) : 0;
					vols.add(String.valueOf(volume));
				}
				bw.write(String.format("%s,%s", name, StringUtils.join(vols, ",")));
				bw.newLine();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}	
	}
	
	public static void main(String[] args) {
		File pFile = new File("C:/Users/ksym2/Desktop/tokyo/gisa/susono_2/output.csv");
		List<Person> listPerson = DataLoader.loadPFlow(pFile);
		System.out.println(listPerson.size());
		
		String shpFilename = "C:/Users/ksym2/Desktop/mesh/mesh.shp";
		Boundaries boudaries = Boundaries.create(shpFilename);
		System.out.println(boudaries.size());
		
		File oFile = new File("C:/Users/ksym2/Desktop/bresult.csv");
		BoundaryVolume volume = new BoundaryVolume();
		Map<Integer,Map<String, Integer>> result = volume.process(listPerson, boudaries);
		write(oFile, result, boudaries);
		
		System.out.println("end");
	}
}
