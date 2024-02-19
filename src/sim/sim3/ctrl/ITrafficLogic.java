package sim.sim3.ctrl;

import java.util.Set;

import sim.sim3.res.Agent;
import sim.sim3.res.SSNetwork;

/**
 * Traffic logic interface
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public interface ITrafficLogic {
	/**
	 * Go to next step
	 * @param simStepTime Simulation step (msec)
	 */
	public void next(long simStepTime);
	
	/**
	 * Insert agent to link
	 * @param agent Agent
	 * @param linkid Link id
	 * @param isReverse Reverse flag
	 * @return result
	 */
	public int insert(Agent agent, int linkid, boolean isReverse);
	
	/**
	 * Preparing of process
	 * @param controller TrafficController
	 * @param network Network
	 */
	public void initialize(TrafficController controller, SSNetwork network);
	
	
	
	public void clear(Set<Agent> setAgents);
}
