package dcity.gtfs.otp;

import java.util.List;

import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.routing4.res.Node;

public class OptRoute {
	private double distance;
	private int time;
	private double fare;
	private List<List<ILonLatTime>> trajectory;
	private List<String> mode;
	private List<String> line;
	private List<Node> froms;
	private List<Node> tos;
	
	public OptRoute(double distance, int time, double fare, 
			List<List<ILonLatTime>> trajectory, List<String> mode,
			List<String> line, List<Node> froms, List<Node> tos) {
		super();
		this.distance = distance;
		this.time = time;
		this.fare = fare;
		this.trajectory = trajectory;
		this.mode = mode;
		this.line = line;
		this.froms = froms;
		this.tos = tos;
	}
	
	public OptRoute(double distance, int time, double fare, List<List<ILonLatTime>> trajectory) {
		this(distance, time, fare, trajectory, null, null, null, null);
	}

	public double getDistance() {
		return distance;
	}

	public int getTime() {
		return time;
	}

	public double getFare() {
		return fare;
	}
	
	public void setFare(double fare) {
		this.fare = fare;
	}

	public List<List<ILonLatTime>> getTrajectory() {
		return trajectory;
	}
	
	public List<String> getMode() {
		return mode;
	}

	public List<String> getLine() {
		return line;
	}

	public List<Node> getFroms() {
		return froms;
	}

	public List<Node> getTos() {
		return tos;
	}	
	
	
}
