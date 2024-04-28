package pseudo.res;

import jp.ac.ut.csis.pflow.geom2.ILonLat;

public class Trip {
	private ETransport transport;
	private EPurpose purpose;
	private long depTime;
	private ILonLat origin;
	private ILonLat destination;
	
	public Trip(ETransport transport, EPurpose purpose, long depTime, ILonLat origin, ILonLat destination) {
		this.transport = transport;
		this.purpose = purpose;
		this.origin = origin;
		this.destination = destination;
		this.depTime = depTime;
	}
	
	public EPurpose getPurpose() {
		return this.purpose;
	}

	public ETransport getTransport() {
		return transport;
	}

	public long getDepTime() {
		return depTime;
	}

	public void setDepTime(long time) {
		this.depTime = time;
	}
	
	public ILonLat getOrigin() {
		return origin;
	}

	public ILonLat getDestination() {
		return destination;
	}
}
