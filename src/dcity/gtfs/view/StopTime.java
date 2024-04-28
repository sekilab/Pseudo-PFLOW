package dcity.gtfs.view;

public class StopTime {
	private Stop stop;
	private int sequence;
	private long arrivalTime;
	private long departureTime;
	
	public StopTime(Stop stop, int sequence, long arrivalTime, long departureTime) {
		super();
		this.stop = stop;
		this.sequence = sequence;
		this.arrivalTime = arrivalTime;
		this.departureTime = departureTime;
	}

	public Stop getStop() {
		return stop;
	}

	public int getSequence() {
		return sequence;
	}

	public long getArrivalTime() {
		return arrivalTime;
	}

	public long getDepartureTime() {
		return departureTime;
	}
}
