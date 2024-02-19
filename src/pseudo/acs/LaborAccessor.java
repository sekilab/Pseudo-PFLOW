package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.EGender;
import pseudo.res.ELabor;

public class LaborAccessor {
	private Map<String, Map<EGender, List<Double[]>>> data;
	
	public LaborAccessor(String filename) {
		super();
		this.load(filename);
	}

	public int size() {
		return data.size();
	}
	
	public ELabor getLabor(int index) {
		switch (index) {
		case 0:return ELabor.WORKER;
		case 1:return ELabor.JOBLESS;
		default:return ELabor.NO_LABOR;	
		}
	}
	
	public Map<EGender, List<Double[]>> get(String gcode){
		if (data.containsKey(gcode)) {
			return data.get(gcode);
		}
		return null;
	}
	
	private int load(String filename){
		this.data = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	String gcode = String.valueOf(items[0]);
            	EGender gender = EGender.getType(Integer.valueOf(items[2]));
            	
            	double job = Double.valueOf(items[4]);
            	double nojob = Double.valueOf(items[5]);
            	double hijob = Double.valueOf(items[6]);
            	Double[] labor = {job, nojob, hijob};
            	
            	Map<EGender, List<Double[]>> gyouseMap = data.containsKey(gcode) ? data.get(gcode) : new HashMap<>();
            	data.put(gcode, gyouseMap);
            	
            	List<Double[]> genders = gyouseMap.containsKey(gender) ? gyouseMap.get(gender) : new ArrayList<>();
            	gyouseMap.put(gender, genders);
            	genders.add(labor);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }		
		return 1;
	}
}
