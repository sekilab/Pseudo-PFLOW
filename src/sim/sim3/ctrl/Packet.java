package sim.sim3.ctrl;

import sim.sim3.res.Agent;

/**
 * Packet
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class Packet{
	/** Agent			*/	protected Agent agent;
	/** Arrival Time	*/	protected long arrivalTime;
	
	/**
	 * Initialization
	 * @param mAgent Agent
	 * @param mArrivalTime Arrival time
	 */
	public Packet(Agent agent, long arrivalTime) {
		this.agent = agent;
		this.arrivalTime = arrivalTime;
	}

	public Agent getAgent() {
		return agent;
	}

	public long getArrivalTime() {
		return arrivalTime;
	}
}
