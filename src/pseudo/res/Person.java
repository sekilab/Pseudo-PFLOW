package pseudo.res;

import java.util.ArrayList;
import java.util.List;

public class Person {
	private long id;
	private int age;
	private EGender gender;
	private ELabor labor;
	private HouseHold parent;

	private Boolean carowner;
	private Boolean bikeowner;
	
	private List<Activity> activities;
	private List<Trip> trips;
	private List<SPoint> trajectory;
	private GLonLat office;	// office-school
	
	public Person(long id, int age, EGender gender) {
		this(null, id, age, gender, ELabor.UNDEFINED);
	}	
	
	public Person(HouseHold parent, long id, int age, EGender gender) {
		this(parent, id, age, gender, ELabor.UNDEFINED);
	}

	public Person(HouseHold parent,long id, int age, EGender gender, ELabor labor) {
		this.id = id;
		this.age = age;
		this.gender = gender;
		this.labor = labor;
		this.parent = null;
		this.activities = new ArrayList<>();
		this.trips = new ArrayList<>();
		this.trajectory = new ArrayList<>();
		this.office = null;
		this.parent = parent;
		this.carowner = false;
		this.bikeowner = false;
		
		if (this.parent != null) {
			this.parent.addMember(this);
		}
	}

	public long getId() {
		return this.id;
	}
	
	public HouseHold getParent() {
		return parent;
	}
	
	public int getAge() {
		return this.age;
	}

	public EGender getGender() {
		return this.gender;
	}

	public ELabor getLabor() {
		return this.labor;
	}
	
	public void setLabor(ELabor labor) {
		this.labor = labor;
	}

	public void setCarowner(Boolean ownership){ this.carowner = ownership; }

	public List<Activity> getActivities() {
		return activities;
	}	
	
	public List<Trip> listTrips(){
		return trips;
	}
	
	public void addTrip(Trip trip) {
		this.trips.add(trip);
	}

	public List<SPoint> getTrajectory() {
		return trajectory;
	}

	public void addTrajectory(List<SPoint> points) {
		this.trajectory.addAll(points);
	}
	
	public void clearTrajectory() {
		this.trajectory.clear();
	}
	
	public void addTrajectory(SPoint point) {
		this.trajectory.add(point);
	}
	
	public void addAcitivity(Activity act) {
		this.activities.add(act);
	}
	
	
	public void clearActivity() {
		this.activities.clear();
	}

	public GLonLat getOffice() {
		return office;
	}

	public void setOffice(GLonLat office) {
		this.office = office;
	}
	
	public boolean hasOffice() {
		return (this.office != null);
	}

	public boolean hasCar(){return this.carowner; }

	public boolean hasBike(){return this.bikeowner; }


	public void setCarOwner(Boolean ownership){ this.carowner = ownership; }

	public void setBikeOwner(Boolean ownership){ this.bikeowner = ownership; }
}