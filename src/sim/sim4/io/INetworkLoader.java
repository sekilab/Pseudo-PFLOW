package sim.sim4.io;

import java.io.File;

import jp.ac.ut.csis.pflow.routing4.res.Network;

public interface INetworkLoader {
	public Network load(File file);
}
