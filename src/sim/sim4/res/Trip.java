package sim.sim4.res;

import jp.ac.ut.csis.pflow.geom2.ILonLat;

public class Trip {
	private ILonLat destLL;
	private long depTime;
	private ETrip transport;
	
	public Trip(ILonLat destLL, long depTime, ETrip transport) {
		super();
		this.destLL = destLL;
		this.depTime = depTime;
		this.transport = transport;
	}

	public ILonLat getDestLL() {
		return destLL;
	}

	public ETrip getTransport() {
		return transport;
	}
	
	public long getDepTime() {
		return this.depTime;
	}
}
