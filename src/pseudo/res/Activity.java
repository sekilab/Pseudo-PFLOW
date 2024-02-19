package pseudo.res;

public class Activity {
	private GLonLat location;
	private long startTime;
	private long duration;
	private EPurpose purpose;
	
	public Activity(GLonLat location, long startTime, long duration, EPurpose purpose) {
		super();
		this.location = location;
		this.startTime = startTime;
		this.duration = duration;
		this.purpose = purpose;
	}


	public GLonLat getLocation() {
		return location;
	}

	public long getEndTime() {
		return (startTime + duration);
	}

	public String getGcode() {
		return this.location.getGcode();
	}
	
	public long getStartTime() {
		return startTime;
	}

	public long getDuration() {
		return duration;
	}
	
	public void setDuration(long duration) {
		this.duration = duration;
	}
	
	public double getX() {
		return location.getLon();
	}
	
	public double getY() {
		return location.getLat();
	}

	public EPurpose getPurpose() {
		return purpose;
	}
}
