package sim.sim4.res;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.geom2.LonLatTime;

public class Agent implements Cloneable{
	protected int id;
	protected ILonLat home;
	protected List<Trip> listTrips;
	protected List<ILonLatTime> trajetory;
	
	public Agent(int id, ILonLat home) {
		super();
		this.id = id;
		this.home = home;
		this.listTrips = new ArrayList<>();
		this.trajetory = new ArrayList<>();
		
		LonLatTime llt = new LonLatTime(home.getLon(), home.getLat(),new Date(Long.MAX_VALUE));
		this.trajetory.add(llt);
	}

	public int getId() {
		return id;
	}

	public List<Trip> getListTrips() {
		return listTrips;
	}
	
	public ILonLat getHome() {
		return home;
	}
	
	public ILonLatTime getLocation() {
		return trajetory.get(0);
	}
	
	public ILonLatTime getLastLocation() {
		return trajetory.get(trajetory.size() - 1);
	}
	
	public List<ILonLatTime> getTrajectory(){
		return this.trajetory;
	}
	
	public void setTrajectory(List<ILonLatTime> trajectory) {
		this.trajetory = trajectory;
	}

	@Override
	protected Agent clone() throws CloneNotSupportedException {
		Agent clone = (Agent)super.clone();
		clone.listTrips = new ArrayList<>(listTrips);
		clone.trajetory = new ArrayList<>(trajetory);
		return clone;
	}
}
