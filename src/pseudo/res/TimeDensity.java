package pseudo.res;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TimeDensity {
	private long time;
	private LinkedHashMap<Long, TimeDensity> elements;
	private double capacity;
	
	public TimeDensity(long time, double capacity) {
		super();
		this.time = time;
		this.capacity = capacity;
		this.elements = new LinkedHashMap<>();
	}
	
	public TimeDensity put(long time) {
		TimeDensity res = elements.containsKey(time) ? elements.get(time) : new TimeDensity(time, 0);
		elements.put(time, res);
		return res;
	}
	
	public TimeDensity get(long time) {
		return elements.containsKey(time) ? elements.get(time) : null;
	}
	
	public void addCapacity(double capacity) {
		this.capacity += capacity;
	}
	
	public long getTime() {
		return this.time;
	}
	
	public TimeDensity getElement(int pos) {
		return (new ArrayList<TimeDensity>(elements.values())).get(pos);
	}
	
	public List<Double> getValues() {
		List<Double> res = new ArrayList<>();
		for (TimeDensity e : elements.values()) {
			res.add(e.getValue());
		}
		return res;
	}
	
	private double getValue() {
		double res = 0;
		if (this.elements.size() > 0) {
			for (TimeDensity time : elements.values()) {
				res += time.getValue();
			}
		}else {
			res = capacity;
		}
		return res;
	}	
}
