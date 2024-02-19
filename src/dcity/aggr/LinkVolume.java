package dcity.aggr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

public class LinkVolume {
	
	private static final long TIME_THRETHOLD = 5 * 60 *1000;
	private static final Dijkstra routing = new Dijkstra();
	private static final Calendar calendar = Calendar.getInstance();
	private static final int MFACTOR = 10;
	private static final int INTERVAL = 15*60;
	
	private static String getTimeCode(Date date) {
		Date tmp = new Date(date.getTime() - date.getTime() % (INTERVAL*1000));
		calendar.setTime(tmp);
		String key = String.format("%02d%02d", 
				calendar.get(Calendar.HOUR_OF_DAY),
				calendar.get(Calendar.MINUTE));
		return key;
	}
	
	private void countUp(String timeCode, String link, Map<String,Map<String, Integer>> map) {
		Map<String, Integer> submap = map.containsKey(timeCode) ? map.get(timeCode) : new HashMap<>();
		int volume = submap.containsKey(link) ? submap.get(link) : 0;
		submap.put(link, volume + MFACTOR);
		map.put(timeCode, submap);
	}
	
	private void process(Person person, Network network, Map<String,Map<String, Integer>> aggr) {
		List<ILonLatTime> llts = person.getListLLTs();
		for (int i = 1; i < llts.size(); i++) {
			ILonLatTime e0 = llts.get(i-1);
			ILonLatTime e1 = llts.get(i);
			Date preDate = e0.getTimeStamp();
			Date nextDate = e1.getTimeStamp();
			String preCode = getTimeCode(preDate);
			long time = nextDate.getTime() - preDate.getTime();
	
			if (time < TIME_THRETHOLD) {
				Node preNode = routing.getNearestNode(network, e0.getLon(), e0.getLat(), 10); 
				Node nextNode = routing.getNearestNode(network, e1.getLon(), e1.getLat(), 10);
				
				if (preNode !=null && nextNode != null) {
					Link link = network.getLink(preNode, nextNode);
					if (link != null) {
						countUp(preCode, link.getLinkID(), aggr);
					}
				}
			}
		}
	}
		
	private Map<String,Map<String, Integer>> process(List<Person> listPerson, Network network) {
		int count = 0;
		Map<String,Map<String, Integer>> aggr = new HashMap<>();
		for (Person p : listPerson) {
			process(p, network, aggr);
			if (count++ % 1000 == 0) {
				System.out.println(count);
			}
		}
		return aggr;
	}
	
	private static void write(File file, Map<String,Map<String, Integer>> result, Network network) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(file));){
			for (Link link : network.listLinks()) {
				String id = link.getLinkID();
				List<String> vols = new ArrayList<>();
				for (int i = -32400; i < (-32400+24*3600); i+=INTERVAL) {
					String code = getTimeCode(new Date(i*1000));
					if (result.containsKey(code)) {
						Map<String, Integer> map = result.get(code);
						long volume = map.containsKey(id) ? map.get(id) : 0;
						vols.add(String.valueOf(volume));
					}else {
						vols.add("0");
					}
				}
				bw.write(String.format("%s,%s", id, StringUtils.join(vols, ",")));
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
		
		File nFile = new File("C:/Users/ksym2/Desktop/digitalcity/drm/seidrm2017_22.txt");
		Network network = DataLoader.loadNetwork(nFile);
		System.out.println(network.linkCount());
		
		File oFile = new File("C:/Users/ksym2/Desktop/output/result.csv");
		LinkVolume volume = new LinkVolume();
		Map<String,Map<String, Integer>> result = volume.process(listPerson, network);
		write(oFile, result, network);
		
		System.out.println("end");
	}
}
