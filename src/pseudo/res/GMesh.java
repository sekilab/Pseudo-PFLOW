package pseudo.res;

import java.util.ArrayList;
import java.util.List;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.Mesh;

public class GMesh{
	private String id;
	private ILonLat lonlat;
	private List<Facility> facilities;
	private List<Double> economics;
	private List<Facility> hospitals;
	
	public GMesh(String id, ILonLat lonlat) {
		this.id = id;
		this.lonlat = lonlat;
		this.economics = new ArrayList<>();
		this.facilities = new ArrayList<>();
		this.hospitals = new ArrayList<>();
	}
	
	public GMesh(Mesh mesh) {
		this(mesh.getCode(), mesh.getCenter());
	}

	public String getId() {
		return this.id;
	}
	
	public ILonLat getCenter() {
		return this.lonlat;
	}
	
	public List<Facility> getFacilities(){
		return this.facilities;
	}

	public Facility getFacility(int index) {
		return facilities.get(index);
	}
	
	public void addFacility(Facility facility) {
		this.facilities.add(facility);
	}
	
	public List<Facility> getHospitals(){
		return this.hospitals;
	}

	public Facility getHospital(int index) {
		return hospitals.get(index);
	}
	
	public void addHospital(Facility facility) {
		this.hospitals.add(facility);
	}

	public double getHospitalCapacity(){
		double res = 0;
		for (Facility e : hospitals) {
			res += e.getCapacity();
		}
		return res;
	}
	
	public List<Double> getEconomics(){
		return this.economics;
	}
	
	public double getEconomics(int[] indexes) {
		double res = 0;
		for (int e : indexes) {
			res += this.getEconomics(e);
		}
		return res;
	}
	
	public double getEconomics(int index) {
		return this.economics.get(index);
	}
	
	public void setEconomics(List<Double> values) {
		this.economics = new ArrayList<>(values);
	}

	@Override
	public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return this.id.equals(((GMesh)obj).id);
	}
	

}
