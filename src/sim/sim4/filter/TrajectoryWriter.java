package sim.sim4.filter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import sim.sim4.res.Agent;

public class TrajectoryWriter<T extends Agent> implements IFilter<T> {

	private BufferedWriter writer;
	
	private static SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	
	public TrajectoryWriter(BufferedWriter writer) {
		super();
		this.writer = writer;
	}

	private void write(T agent, long time) throws IOException {
		ILonLat curLoc = agent.getLocation();
		if (curLoc != null) {
			String line = String.format("%s,%s,%f,%f",
					agent.getId(), 
					format.format(new Date(time)),
					curLoc.getLon(), curLoc.getLat());
			writer.write(line);
			writer.newLine();
		}
	}

	@Override
	public void run(long time, Network network, List<T> listAgents) {
		for (T agent : listAgents) {
			try {
				write(agent, time);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
