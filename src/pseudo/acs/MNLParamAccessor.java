package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.ECity;
import pseudo.res.ELabor;
import pseudo.res.ETransition;

public class MNLParamAccessor {

	private Map<ELabor, Map<ECity, Map<ETransition, List<Double>>>> dataset;
			
	public MNLParamAccessor() {
		dataset = new HashMap<>();
	}
	
	public List<Double> get(ELabor labor, ECity city, ETransition transition){
		return dataset.get(labor).get(city).get(transition);
	}
	
	public int getSize() {
		return dataset.values().size();
	}
	
	public int add(String filename, ELabor labar){
		Map<ECity, Map<ETransition, List<Double>>> data = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	ECity city = ECity.getType(Integer.valueOf(items[0]));
            	ETransition transition = ETransition.getType(Integer.valueOf(items[1]));
            	List<Double> params = new ArrayList<>();
            	for (int i=2; i < items.length; i++) {
            		params.add(Double.valueOf(items[i]));
            	}
            	Map<ETransition, List<Double>> map = data.containsKey(city) ? data.get(city) : new HashMap<>();
            	map.put(transition, params);
            	data.put(city, map);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		this.dataset.put(labar, data);
		return 1;
	}	
}
