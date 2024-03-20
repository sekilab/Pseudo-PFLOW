package pseudo.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CensusOD {	
	private String origin;
	private Map<EGender, Double> home;
	private Map<EGender, LinkedHashMap<String, Double>> cities;
	
	public CensusOD(String origin) {
		super();
		this.origin = origin;
		this.home = new HashMap<>();
		this.cities = new HashMap<>();
	}

	public String getOrigin() {
		return this.origin;
	}
	
	public void addHome(EGender gender, double volume) {
		home.put(gender, volume);
	}	

	public void addCities(EGender gender, String destination, double volume) {
		LinkedHashMap<String, Double> map = cities.containsKey(gender) ? cities.get(gender) : new LinkedHashMap<>();
		map.put(destination, volume);
		cities.put(gender, map);
	}

	// for student
	public List<Double> getCapacities(EGender gender, List<String> names){
		List<Double> values = new ArrayList<>();
		if (cities.containsKey(gender)) {
			LinkedHashMap<String, Double> map = cities.get(gender);
			for (String name : names) {
				values.add(map.get(name));
			}
		}
		return values;
	}

	// for commuter
	public boolean isHome(int choice) {
		return (choice == 0);
	}
	
	public String getDestination(EGender gender, int choice) {
		Object[] keys = cities.get(gender).keySet().toArray();
		return (String)(keys[choice-1]);
	}
	
	public Set<String> getDestinationNames(EGender gender){
		if (cities.containsKey(gender)) {
			return cities.get(gender).keySet();
		}
		return null;
	}
	public List<Double> getCapacities(EGender gender){
		List<Double> values = new ArrayList<>();
		values.add(home.containsKey(gender) ? home.get(gender) : 0.0);
		if (cities.containsKey(gender)) {
			values.addAll(cities.get(gender).values());
		}
		return values;
	}	
}
