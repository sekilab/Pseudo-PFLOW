package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import jp.ac.ut.csis.pflow.geom2.DistanceUtils;
import pseudo.res.City;
import pseudo.res.CensusOD;
import pseudo.res.EGender;
import pseudo.res.Japan;

public class CensusODAccessor {
	private Map<EType, Map<String, CensusOD>> data = new HashMap<>();
	private static final double MAX_COMMUTE_DISTANCE = 100000;
	
	public enum EType {
		COMMUTER(1),
		STUDENT(2);
		
		private final int id;
		
		private EType(int id) {
			this.id = id;
		}
		
		public int getId() {
			return id;
		}	
		
		public static EType getType(final int id) {
			EType[] types = EType.values();
			for (EType e : types) {
				if (e.getId() == id) {
					return e;
				}
			}
			return null;
		}
	}
	
	
	public CensusODAccessor(String filename, Japan japan) {
		super();
		this.load(filename, japan);
	}
	
	public CensusOD get(EType type, String gcode) {
		Map<String, CensusOD> map = data.get(type);
		if (map.containsKey(gcode)) {
			return map.get(gcode);
		}
		return null;
	}
	
	private void load(String filename, Japan japan){
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	String o = items[0];
            	String d = items[1];
            	boolean isWorker = Boolean.valueOf(items[2]);
            	EGender gender = Boolean.valueOf(items[3]) ? EGender.MALE : EGender.FEMALE;
            	boolean isHome = Boolean.valueOf(items[4]);
                int volume = Integer.valueOf(items[5]);
                
                City c1 = japan.getCity(o);
                City c2 = japan.getCity(d);
                
                EType type = isWorker ? EType.COMMUTER : EType.STUDENT;
                
                if (c1 != null && c2 != null && DistanceUtils.distance(c1, c2) <= MAX_COMMUTE_DISTANCE) {    
                	Map<String, CensusOD> map = data.containsKey(type) ? data.get(type) : new HashMap<>();
                	data.put(type, map);
                	
                	CensusOD od = map.containsKey(o) ? map.get(o) : new CensusOD(o);
                	map.put(o, od);
                	
                	if (isHome) {
                		od.addHome(gender, volume);
                	}else {
                		od.addCities(gender, d, volume);
                	}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
}
