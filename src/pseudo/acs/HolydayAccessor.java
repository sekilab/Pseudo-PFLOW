package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.EGender;

public class HolydayAccessor {
	private Map<Integer, Map<EGender, List<Double>>> data;
	
	public HolydayAccessor(String filename) {
		this.load(filename);
	}
	
	public Map<EGender, List<Double>> get(int preCode){
		if (data.containsKey(preCode)) {
			return data.get(preCode);
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
            	int gcode = Integer.valueOf(items[0]);
            	EGender gender = EGender.getType(Integer.valueOf(items[1]));
            	//int age = Integer.valueOf(items[2]);
            	double job = Double.valueOf(items[3])/100;

            	Map<EGender, List<Double>> l1 = data.containsKey(gcode) ? data.get(gcode) : new HashMap<>();
            	data.put(gcode, l1);
            	
            	List<Double> probs = l1.containsKey(gender) ? l1.get(gender) : new ArrayList<>();
            	l1.put(gender, probs);
            	
            	probs.add(job);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }		
		return 1;
	}
	
}
