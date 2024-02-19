package sim.sim3.res;

import jp.ac.ut.csis.pflow.routing4.res.Link;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import sim.sim3.support.INetworkVisitor;

/**
 * Network Link
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
@SuppressWarnings("serial")
public class SLink extends Link implements INetworkAcceptor{
	/** Network				*/	protected SSNetwork parent;
	/** Normal Direction	*/	protected SDirection normal;
	/** Reverse Direction	*/	protected SDirection reverse;
	/** Driving time		*/	protected long drivingTime;
	/** Attribute			*/	protected double capacity;
	

	/**
	 * Initialization
	 * @param id Link id
	 * @param node1 Source node
	 * @param node2 Target node
	 * @param closed Closed flag for normal link
	 * @param rclosed Closed flag for reverse link
	 * @param time Driving time
	 * @param flowRate Traffic capacity
	 */
	public SLink(String id, Node node1, Node node2, boolean closed, boolean rclosed, long time, double capacity) {
		super(id, node1, node2, time , time,  false);
		this.drivingTime = time;
		this.capacity = capacity;
		this.normal = new SDirection(this, false, closed);
		this.reverse = new SDirection(this, true, rclosed);	
	}
	
	public SLink(String id, Node node1, Node node2, long time, double capacity) {
		this(id, node1, node2, false, false, time, capacity);
	}
	
	public SLink(String id, Node node1, Node node2, long time) {
		this(id, node1, node2, false, false, time, -1);
	}
	
	public SLink(String id, Node node1, Node node2, boolean closed, boolean rclosed, long time) {
		this(id, node1, node2, closed , rclosed, time,  -1);
	}

	public void setParent(SSNetwork parent){
		this.parent = parent;
	}
	
	public SSNetwork getParent(){
		return parent;
	}
		
	public long getDrivingTime() {
		return drivingTime;
	}
	
	public SDirection getLane(boolean isReverse) {
		return isReverse ? reverse : normal;
	}
	
	public SDirection getNormal(){
		return normal;
	}
	
	public SDirection getReverse(){
		return reverse;
	}

	public boolean isDestNode(Node node){
		return (getHeadNode() == node);
	}
	
	public double getCapacity(){
		return capacity;
	}
	
	public void setCapacity(double capacity){
		this.capacity = capacity;
	}
	

	@Override
	public void accept(INetworkVisitor networkVisitor) {
		networkVisitor.visit(this);
	}
}
