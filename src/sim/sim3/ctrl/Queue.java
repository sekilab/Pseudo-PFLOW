package sim.sim3.ctrl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import jp.ac.ut.csis.pflow.routing4.res.Node;
import sim.sim3.res.Agent;
import sim.sim3.res.SDirection;
import sim.sim3.res.Trip;

/**
 * Queue
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class Queue{
	/** Direction 			*/	protected SDirection parent;
	/** List of Packets		*/	protected LinkedList<Packet> listPackets;
	/** Traffic Capacity	*/	protected double capacity;
	
	/**
	 * Initialization 
	 * @param mParent Direction
	 */
	public Queue(SDirection parent) {
		super();
		this.parent = parent;
		this.listPackets = new LinkedList<Packet>();
		this.capacity = 0;
	}
	
	public SDirection getParent(){
		return parent;
	}
	
	public long getDrivingTime(){
		return parent.getDrivingTime();
	}
	
	/**
	 * Update traffic capacity volume
	 * @param capacity Traffic capacity
	 */
	public void updateStatus(double capacity){
		if (listPackets.size() != 0 || capacity < 0){
			this.capacity += capacity;
		}else{
			this.capacity = 0;
		}
	}

	
	/**
	 * Calculate sum of magnification factor
	 * @return result
	 */
	public double sumStocks(){
		double sum = 0d;
		for (Packet packet : listPackets){
			sum += packet.getAgent().getTrip().getMfactor1();
		}
		return sum;
	}
	
	/**
	 *　Dequeue packets
	 * @param controller TrafficController 
	 */
	public List<TripInfo> dequeue(TrafficController controller){
		List<TripInfo> ret = new ArrayList<TripInfo>();
		try{
			long time = controller.getTime();
			Iterator<Packet> iterator = listPackets.iterator();
			while(iterator.hasNext()) {
				Packet packet = (Packet)iterator.next();
				Agent agent = packet.getAgent();
	    		Trip trip = agent.getTrip();
	    		double mfactor = trip.getMfactor1();
	    		if (packet.getArrivalTime() < time && capacity >= 0){
	    		//if (true){
	    			iterator.remove();
	    			TripInfo info = null;
	    			if (agent.isEndLinkOfRoute()){
						agent.removeTrip();
						info = controller.nextTrip(agent);
					}else{
						agent.getRoute().remove(0);
						info = controller.nextLink(agent);
					}
	    			if (info != null){
	    				ret.add(info);
	    			}
	    			capacity -= mfactor;
	    		}else{
	    			break;
	    		}
			}
		}catch(Exception exp){
			exp.printStackTrace(); 
		}
		return ret;
	}
	
	/**
	 * Enqueue a packet
	 * @param packet Packet
	 */
	public void enqueue(Packet packet){
		listPackets.add(packet);
	}
	
	/**
	 * Remove packet
	 */
	public void clear(Set<Agent> setAgents){
		Iterator<Packet> iter = listPackets.iterator();
		while(iter.hasNext()){
			Packet packet = iter.next();
			Agent agent = packet.getAgent();
			if (setAgents.contains(agent)){
				List<Node> route = agent.getRoute();
				Node node0 = route.get(0);
				Trip trip = agent.getTrip();
				trip.setDepId(Integer.valueOf(node0.getNodeID()));
				route.clear();
				route.add(node0);
				iter.remove();
			}
		}
	}
}