package sim.sim4.io;

import java.util.List;
import java.util.Random;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import sim.sim4.res.Agent;
import sim.sim4.res.ETrip;
import sim.sim4.res.Trip;

public class TestTripCreater{

	private final static Random random = new Random(1000);
	
	private static void process(Agent agent) {
		if (random.nextDouble() < 0.01) {
			ILonLat home = agent.getHome();
			Trip trip1 = new Trip(home, 1800, ETrip.STAY);
			agent.getListTrips().add(trip1);
			
			LonLat destLL = new LonLat(137.057966, 36.801185);
			Trip trip2 = new Trip(destLL,0, ETrip.CAR);
			agent.getListTrips().add(trip2);
		
		}else {
			ILonLat home = agent.getHome();
			Trip trip1 = new Trip(home, 3600*24, ETrip.STAY);
			agent.getListTrips().add(trip1);
		}
	}
	
	public static void create(List<Agent> listAgents) {
		for (Agent agent : listAgents) {
			process(agent);
		}
	}
}
