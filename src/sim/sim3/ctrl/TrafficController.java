package sim.sim3.ctrl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.ac.ut.csis.pflow.geom2.LonLat;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import sim.sim3.res.Agent;
import sim.sim3.res.ENetwork;
import sim.sim3.res.ETransport;
import sim.sim3.res.SLink;
import sim.sim3.res.SNetwork;
import sim.sim3.res.SSNetwork;
import sim.sim3.res.Trip;
import sim.sim3.routing.RouteSearcher;
import sim.sim3.support.IFilter;

/**
 * Traffic Controller
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class TrafficController {
	/** Current Time				*/		private long time;
	/** StepTime					*/		private final static long stepTime = 5 * 1000;
											private final static int numNodeStep = 60;
	/** List of Filters				*/		private LinkedList<IFilter> listFilters;
	/** Traffic Logic for Link		*/		private Map<ENetwork, ITrafficLogic> mapLogics;
	/** Traffic Logic for Centroid	*/		private CentroidLogic centroidLogic;
	/** Network						*/		private SNetwork network;
	/** Step counter				*/		private int counter;
	/** List of Agents				*/		private List<Agent> listAgents;	
	

	/**
	 * Initialization
	 * @param startTime	Start time of simulation
	 * @param mNetwork Network
	 * @param mListAgents List of Agents
	 * @param mapLogics Map of Traffic Logics
	 */
	public TrafficController(long startTime, SNetwork network, List<Agent> listAgents,
			Map<ENetwork, ITrafficLogic> mapLogics) {
		super();
		this.listFilters = new LinkedList<IFilter>();
		this.network = network;
		this.listAgents = listAgents;
		this.mapLogics = mapLogics;
		this.time = startTime;
	}

	/**
	 * Get Current Time
	 * @return result
	 */
	public long getTime(){
		return time;
	}
	
	
	/**
	 * Preparing for simulation 
	 * @return result
	 */
	public int initialize(){
		// logic for centroid control
		centroidLogic = new CentroidLogic(this);
		centroidLogic.initialize(listAgents);
		
		// logic for link control
		for (Map.Entry<ENetwork, ITrafficLogic> entry : mapLogics.entrySet()){
			entry.getValue().initialize(this, network.getNetwork(entry.getKey()));
		}
		return 0;
	}
	
	
	/**
	 * On Step forward
	 * @return result
	 */
	public int next() {
		// Update staying agents
		if (counter % numNodeStep == 0){
			centroidLogic.next(time);
		}
		// Update moving agents
		for (Map.Entry<ENetwork, ITrafficLogic> entry : mapLogics.entrySet()){
			ITrafficLogic logic = entry.getValue();
			logic.next(stepTime);
		}
		// Execute Filter process
		for (IFilter filter : listFilters){
			filter.run(network, listAgents, counter, time);
		}
		// reflash update flag 
		for (Agent agent : listAgents){
			agent.setUpdate(false);
		}		
		counter++;
		time += stepTime;
		return 1;
	}
	
	/**
	 * Add a filter to TrafficManager
	 * @param filter
	 */
	public void addFilter(IFilter filter) {
		listFilters.add(filter);
	}

	
	/**
	 * Remove all filter
	 */
	public void clearFilter() {
		listFilters.clear();
	}
	
	/**
	 * Move the agent to next trip
	 * @param agent Agent
	 * @return result
	 */
	public TripInfo nextTrip(Agent agent){
		if (agent.hasTrip()){
			Trip trip = agent.getTrip();
			ETransport transport = trip.getTransport();
			int source = trip.getDepId();
			int target = trip.getArrId();
			SSNetwork snetwork = network.getNetwork(transport.getNetwork());
			// 
			if (transport != ETransport.STAY){
				if (source != target){
					LonLat sourceLL = network.getSNode(source);
					LonLat targetLL = network.getSNode(target);
					List<Node> route = RouteSearcher.search(source, target, sourceLL, targetLL, snetwork);
					if (route != null && route.size() >= 2){
						agent.setRoute(route);
						return nextLink(agent);
					}
				}else{
					agent.removeTrip();
					return nextTrip(agent);
				}
			}else{
				Node node = network.getSNode(source);
				if (node != null){
					return nextNode(agent, node);
				}
			}
		}
		agent.removeAllTrip();
		agent.kill();
		return null;
	}
			
	/**
	 * Move the agent to next linkfinal static,%d
	 * @param agent Agent
	 * @return result
	 */
	public TripInfo nextLink(Agent agent){
		Node node0 = agent.getNode(0);
		Node node1 = agent.getNode(1);

		ETransport transport = agent.getTrip().getTransport();
		SSNetwork snetwork = network.getNetwork(transport.getNetwork());
		SLink link = snetwork.getSLink(node0, node1);
		if (link != null){
			boolean isReverse = link.isDestNode(node0);
			int linkid = Integer.valueOf(link.getLinkID());
			agent.setUpdate(true);
			agent.setLinkId(linkid);
			return new TripInfo(snetwork.getType(), agent, linkid, isReverse);
		}
		return null;
	}
	
	/**
	 * Move the agent to next node
	 * @param agent Agent
	 * @param node Destination Node
	 * @return result
	 */
	private TripInfo nextNode(Agent agent, Node node){
		Trip trip = agent.getTrip();
		trip.setTransport(ETransport.STAY);
		long depTime = time + trip.getStayTime();
		long defaultTime = trip.getDepTime();
		
		if (defaultTime > 0){
			depTime = defaultTime;
		}

		final long tolerance = numNodeStep*stepTime;
		long idelKey = tolerance * (depTime / tolerance);
		agent.setUpdate(true);
		agent.setLinkId(0);
		agent.setRoute(Arrays.asList(node));
		return new TripInfo(agent, idelKey);
	}
	
	
	/**
	 * Move to next trip
	 * @param info Tripinfo
	 */
	private void nextTrip(TripInfo info){
		ENetwork networkType = info.getNetworkType();
		Agent agent = info.getAgent();
		if (networkType!= ENetwork.CENTROID){
			mapLogics.get(networkType).insert(
					agent, info.getLinkId(), info.isReverse());
		}else{
			centroidLogic.insert(info.getStartTime(), agent);
		}
	}
	
	
	/**
	 * Move to next trip
	 * @param listTrips List of Trips
	 */
	public void nextTrip(List<TripInfo> listTrips){
		for (TripInfo info : listTrips){
			nextTrip(info);
		}
	}
	
	public void reEntry(List<Agent> listAgents){
		centroidLogic.process(listAgents);
	}
	
	public int clear(List<Agent> listAgents){
		Set<Agent> setAgents = new HashSet<Agent>(listAgents);
		centroidLogic.clear(time, setAgents);
		
		for (Map.Entry<ENetwork, ITrafficLogic> entry : mapLogics.entrySet()){
			ITrafficLogic logic = entry.getValue();
			logic.clear(setAgents);
		}
		return 0;
	}
	
	/**
	 * Remove agents from network
	 */
	public void clear(){
		clear(listAgents);
	}
		
	/**
	 * Insert agents to network
	 */
	public void reEntry(){
		reEntry(listAgents);
	}
}
