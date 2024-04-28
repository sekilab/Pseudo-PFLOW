package sim.sim3.res;

import java.util.List;
import jp.ac.ut.csis.pflow.routing4.res.Node;

/**
 * Agent
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class Agent {
	/** ID							*/	protected long id;
	/** List of Trips				*/	protected List<Trip> listTrips;
	/** Route of Current Trip		*/	protected List<Node> route;
	/** Update Flag					*/	protected boolean update;
	/** Death Flag					*/	protected boolean alive;
	/** Magnification Mactor		*/	protected double mfactor;
	/** Link ID						*/	protected int linkId;
	
	/**
	 * Initialization
	 * @param mId	Agent id
	 * @param mMfactor	Magnification factor
	 * @param mListTrips	List of Trips
	 */
	public Agent(long id, double mfactor, List<Trip> listTrips) {
		super();
		this.id = id;
		this.listTrips = listTrips;
		this.route = null;
		this.update = true;
		this.linkId = 0;
		this.alive = true;
		this.mfactor = mfactor;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public double getMfactor() {
		return mfactor;
	}

	public void setMfactor(double mfactor) {
		this.mfactor = mfactor;
	}
	
	public List<Node> getRoute() {
		return route;
	}

	public void setRoute(List<Node> route) {
		this.route = route;
	}

	public Node getNode(int index) {
		return route != null && route.size() > 0 ? route.get(index) : null;
	}
	
	public boolean isUpdated() {
		return update;
	}

	public void setUpdate(boolean update) {
		this.update = update;
	}

	public List<Trip> listTrips() {
		return listTrips;
	}

	public boolean isEndLinkOfRoute(){
		return (route.size() <= 2);
	}
	
	public boolean hasTrip(){
		int size = listTrips.size();
		return (size != 0);
	}
	
	public Trip getTrip(){
		if (hasTrip())
			return listTrips.get(0);
		return null;
	}
	
	public Trip getTrip(int index){
		return listTrips.get(index);
	}
	
	public void removeTrip(Trip trip){
		listTrips.remove(trip);
	}
	
	public void removeTrip(){
		if (listTrips.size() > 0){
			listTrips.remove(0);
		}
	}
	
	public void removeAllTrip(){
		listTrips.clear();
	}
	
	public void insertTrip(Trip trip){
		listTrips.add(trip);
	}
	
	public boolean isAlive(){
		return alive;
	}
	
	public void kill(){
		this.alive = false;
	}
	
	public void setLinkId(int mLinkId){
		this.linkId = mLinkId;
	}
	
	public int getLinkId(){
		return linkId;
	}
	
    public Node getCurrentPosition(){
        if (route != null && route.size() > 0){
        	return route.get(0);
        }
        return null;
    }
}

