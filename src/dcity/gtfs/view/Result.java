package dcity.gtfs.view;

import java.util.List;

import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.routing4.res.Link;

public class Result {
	private Trip trip;
	private List<ILonLatTime> trajectory;
	private List<Link> listLinks;
	
	public Result(Trip trip, List<ILonLatTime> trajectory, List<Link> listLinks) {
		super();
		this.trip = trip;
		this.trajectory = trajectory;
		this.listLinks = listLinks;
	}
	
	
	
	public Trip getTrip() {
		return trip;
	}

	public List<ILonLatTime> getTrajectory() {
		return trajectory;
	}

	public List<Link> getListLinks() {
		return listLinks;
	}

	
}
