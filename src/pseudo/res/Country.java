package pseudo.res;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;

public class Country{
	private Network cities;
	private Network station;
	private Map<String, GMesh> meshes;
	
	public Country() {
		super();
		this.cities = new Network();
		this.station = null;
		this.meshes = new HashMap<>();
	}
	
	public void setStation(Network network) {
		this.station = network;
	}

	public Network getStation() {
		return station;
	}

	public City getCity(String code) {
		return (City)cities.getNode(code);
	}
	
	public List<Node> getCities(){
		return cities.listNodes();
	}
	
	public void addCity(City city) {
		cities.addNode(city);
	}
	
	public int size() {
		return cities.nodeCount();
	}
	
	public List<City> searchCities(double radius, ILonLat lonlat) {
		List<Node> nodes = cities.queryNode(lonlat.getLon(), lonlat.getLat(), radius);
		List<City> res = new ArrayList<>();
		if (nodes != null) {
			for (Node e : nodes) {
				res.add((City)e);
			}
		}
		return res;
	}
	
	public GMesh getMesh(String mesh) {
		if (meshes.containsKey(mesh)) {
			return meshes.get(mesh);
		}
		return null;
	}
	
	public boolean hasMesh(String mesh) {
		return meshes.containsKey(mesh);
	}
	
	public void addMesh(GMesh mesh) {
		meshes.put(mesh.getId(), mesh);
	}
}

