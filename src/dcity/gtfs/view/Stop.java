package dcity.gtfs.view;

public class Stop {
	private String stopId;
	private double lon;
	private double lat;
	
	public Stop(String stopId, double lon, double lat) {
		super();
		this.stopId = stopId;
		this.lon = lon;
		this.lat = lat;
	}

	public String getStopId() {
		return stopId;
	}

	public double getLon() {
		return lon;
	}

	public double getLat() {
		return lat;
	}
}
