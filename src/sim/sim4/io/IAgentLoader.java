package sim.sim4.io;

import java.io.File;
import java.util.List;

import sim.sim4.res.Agent;

public interface IAgentLoader {
	public List<Agent> load(File file);
}
