package dcity.gtfs.otp;


public class Trip {
	private int id;
	private double lon1;
	private double lat1;
	private double lon2;
	private double lat2;
	private int transport;
	private long starttime;
	private long endtime;
	private OptRoute route;
	private int age;
	private double distance;
	
	public Trip(int id, double lon1, double lat1, double lon2, double lat2) {
		super();
		this.id = id;
		this.lon1 = lon1;
		this.lat1 = lat1;
		this.lon2 = lon2;
		this.lat2 = lat2;
		this.route = null;
		this.age = 0;
	}
	
	public Trip(int id, double lon1, double lat1, double lon2, double lat2, long starttime, int age) {
		this(id, lon1, lat1, lon2, lat2);
		this.starttime = starttime;
		this.age = age;
	}
	
	public Trip(int id, double lon1, double lat1, double lon2, double lat2, int transport,long starttime,long endtime) {
		this(id, lon1, lat1, lon2, lat2);
		this.transport=transport;
		this.starttime = starttime;
		this.endtime = endtime;
	}
	
	public int getId() {
		return id;
	}

	public double getLon1() {
		return lon1;
	}

	public double getLat1() {
		return lat1;
	}

	public double getLon2() {
		return lon2;
	}

	public double getLat2() {
		return lat2;
	}
	
	public int getTransport() {
		return transport;
	}

	public long getStarttime() {
		return starttime;
	}

	public long getEndtime() {
		return endtime;
	}

	public OptRoute getRoute() {
		return route;
	}	
	
	public void setRoute(OptRoute route) {
		this.route  = route;
	}
	
	public void setTransport(int transport) {
		this.transport = transport;
	}
	
	public int getAge() {
		return this.age;
	}

	public double getDistance() {
		return distance;
	}

	public void setDistance(double distance) {
		this.distance = distance;
	}
	
}
