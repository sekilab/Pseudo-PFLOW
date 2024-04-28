package sim.sim4.filter;

import java.util.List;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import sim.sim4.res.Agent;

public interface IFilter<T extends Agent> {
	public void run(long time, Network network, List<T> listAgents);
}
