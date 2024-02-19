package sim.sim3.support;

import java.util.List;

import sim.sim3.res.Agent;
import sim.sim3.res.SNetwork;

/**
 * Interface for Filter
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public interface IFilter{
	/**
	 * Execute Filter
	 * @param network Network
	 * @param listAgents List of Agents
	 * @param step Step time (msec)
	 * @param time Current Time (msec)
	 * @return result
	 */
	int run(SNetwork network, List<Agent> listAgents,  int step, long time);
}
