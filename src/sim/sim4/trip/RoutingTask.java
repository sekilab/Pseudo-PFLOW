package sim.sim4.trip;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.geom2.LonLatTime;
import jp.ac.ut.csis.pflow.geom2.TrajectoryUtils;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import jp.ac.ut.csis.pflow.routing4.res.Route;
import sim.sim4.res.Agent;
import sim.sim4.res.ETrip;
import sim.sim4.res.Trip;

public class RoutingTask <T extends Agent> implements Callable<Integer>{

	private List<T> listAgents;
	private long msTime;
	private Network network;
	private Dijkstra routing = new Dijkstra();

	public RoutingTask(List<T> listAgents, Network network, long msTime){
		this.listAgents = listAgents;
		this.network = network;
		this.msTime = msTime;
	}	
	
	private long createStayTrip(Agent agent, ILonLat oriLoc, Trip trip, long time, List<ILonLatTime> trajectory) {
		// destination
		time = trip.getDepTime();
		ILonLatTime dstLoc = new LonLatTime(oriLoc.getLon(), oriLoc.getLat(), new Date(time));
		trajectory.add(dstLoc);
		return time;
	}
	
	private long createMoveTrip(Agent agent, ILonLat oriLoc, Trip trip, long time, List<ILonLatTime> trajectory) {
		ILonLat dstLoc = trip.getDestLL();
		Route route = routing.getRoute(network, 
				oriLoc.getLon(),  oriLoc.getLat(), dstLoc.getLon(), dstLoc.getLat());
		
		List<Node> listNodes = route.listNodes();
		if (route != null && listNodes.size() > 1) {
			double speed = TrafficConfig.velocity(trip.getTransport());
			long duration = (long) (route.getLength() / speed);
			long dstTime = time + duration * 1000;
			
			List<ILonLatTime> subt = TrajectoryUtils.interpolateUnitTime(
					listNodes, new Date(time), new Date(dstTime));
			
			if (subt.size() > 1){
				trajectory.addAll(subt.subList(0, subt.size()));
				time = dstTime;
			}
		}
		return time;
	}

	private void searchRoute(Agent agent) {
		long time = msTime;
		List<Trip> listTrips =agent.getListTrips();
		List<ILonLatTime> trajectory = new ArrayList<>();
		for (int i = 0; i < listTrips.size(); i++) {
			Trip trip = listTrips.get(i);
			ILonLat curLoc = (i == 0) ? agent.getLocation() : trajectory.get(trajectory.size() - 1);
			if (curLoc != null) {
				ETrip typeTrip = trip.getTransport();
				if (typeTrip == ETrip.STAY) {
					time = createStayTrip(agent, trip.getDestLL(), trip, time, trajectory);
				}else {
					time = createMoveTrip(agent, curLoc, trip, time, trajectory);
				}
			}
		}
		
		// update trajectory
		if (trajectory.size() > 0) {
			List<ILonLatTime> t = agent.getTrajectory();
			t.clear();
			t.addAll(trajectory);
		}
		// remove trips
		listTrips.clear();
	}

	@Override
	public Integer call() throws Exception {
		for (Agent agent : listAgents) {
			searchRoute(agent);		
		}
		return 0;
	}
}
