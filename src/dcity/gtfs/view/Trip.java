package dcity.gtfs.view;

import java.util.ArrayList;
import java.util.List;

public class Trip {
	private String tripId;
	private String serviceId;
	private String routeId;
	private List<StopTime> listStopTimes;
	
	public Trip(String tripId, String serviceId, String routeId) {
		super();
		this.tripId = tripId;
		this.serviceId = serviceId;
		this.routeId = routeId;
		this.listStopTimes = new ArrayList<>();
	}

	public String getTripId() {
		return tripId;
	}
	
	public String getServiceId() {
		return serviceId;
	}

	public List<StopTime> getListStopTimes() {
		return listStopTimes;
	}

	public String getRouteId() {
		return routeId;
	}	
}
