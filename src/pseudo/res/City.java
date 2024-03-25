package pseudo.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.res.Node;

@SuppressWarnings("serial")
public class City extends Node{
	private EPTCity ptType;
	private ECity type;
	private double area;
	private double popRatio;
	private double officeRatio;
	private Set<GMesh> meshs;
	private Map<ELabor, Map<Integer, Facility>> schools;
	private Country parent;

	public City(Country parent, String code, EPTCity ptType,
                ECity type, double area, double popRatio, double officeRatio, ILonLat centroid) {
		super(code, centroid.getLon(), centroid.getLat());
		this.parent = parent;
		this.ptType = ptType;
		this.type = type;
		this.area = area;
		this.popRatio = popRatio;
		this.officeRatio = officeRatio;
		this.meshs = new HashSet<>();
		this.schools = new HashMap<>();
		
		this.parent.addCity(this);
	}

	public String getId() {
		return this.getNodeID();
	}
	
	public EPTCity getPTType() {
		return ptType;
	}

	public ECity getType() {
		return type;
	}

	public double getArea() {
		return area;
	}

	public double getPopRatio() {
		return popRatio;
	}

	public double getOfficeRatio() {
		return officeRatio;
	}
	 
	public void addMesh(GMesh mesh) {
		if (getMeshes().contains(mesh) != true) {
			meshs.add(mesh);
			this.parent.addMesh(mesh);
		}
	}
	
	public List<GMesh> getMeshes(){
		return new ArrayList<GMesh>(meshs);
	}
	public void addSchools(ELabor type, Facility facility) {
		Map<Integer,Facility> facilities = schools.containsKey(type) ? schools.get(type) : new HashMap<>();
		facilities.put(facility.getId(), facility);		
		this.schools.put(type, facilities);
	}

	public Facility getSchool(ELabor type, Integer id) {
		if (schools.containsKey(type)) {
			return (Facility)schools.get(type).get(id);
		}
		return null;
	}
	
	public List<Facility> getSchools(ELabor type) {
		if (schools.containsKey(type)) {
			return new ArrayList<>(schools.get(type).values());
		}
		return null;
	}

	public Country getParent() {
		return parent;
	}

	public void setParent(Country parent) {
		this.parent = parent;
	}
}
