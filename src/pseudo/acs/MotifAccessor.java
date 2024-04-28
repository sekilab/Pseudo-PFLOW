package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.EGender;
import pseudo.res.ELabor;




public class MotifAccessor {
	private Map<ELabor, Map<EGender, Map<Integer, List<Double>>>> data;
	
	private static final int INTERVAL = 5;

	public MotifAccessor(String filename) {
		super();
		this.load(filename);
	}

	public List<Double> getVolumes(ELabor labor, EGender gender, int age){
		age = (age / INTERVAL) * INTERVAL;
		return data.get(labor).get(gender).get(age);
	}
	
	public int size() {
		return data.size();
	}
	
	private int load(String filename) {
		data = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	
            	ELabor labor = null;
            	switch(Integer.valueOf(items[0])) {
            	case 15:labor = ELabor.NO_LABOR;break;
            	default:labor = ELabor.JOBLESS;break;
            	}
            	
            	EGender gender = EGender.getType(Integer.valueOf(items[1]));
            	
            	int age = Integer.valueOf(items[2]);
            	
            	List<Double> probs = new ArrayList<>();
            	probs.add(Double.valueOf(items[3]));
            	probs.add(Double.valueOf(items[4]));
            	
            	Map<EGender, Map<Integer, List<Double>>> l1 = data.containsKey(labor) ? data.get(labor) : new HashMap<>();
            	data.put(labor, l1);
            	
            	Map<Integer, List<Double>> l2 = l1.containsKey(gender) ? l1.get(gender) : new HashMap<>();
            	l1.put(gender, l2);
            	
            	l2.put(age, probs);
            	
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		return 1;
	}
}
