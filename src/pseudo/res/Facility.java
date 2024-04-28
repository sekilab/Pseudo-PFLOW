package pseudo.res;

@SuppressWarnings("serial")
public class Facility extends GLonLat {;
	private int id;
	private double capacity;
	
	public Facility(int id, double lon, double lat, String gcode, double capacity) {
		super(lon, lat, gcode);
		this.id = id;
		this.capacity = capacity;
	}

	public int getId() {
		return this.id;
	}

	public double getCapacity() {
		return capacity;
	}
}
