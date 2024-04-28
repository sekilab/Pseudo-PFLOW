package sim.sim4.trip;

import sim.sim4.res.ETrip;

public class TrafficConfig {
	public static double velocity(ETrip type) {
		switch(type) {
		case CAR:
		case TRAIN:
			return (50000f/3600f);
		case BICYCLE:
			return (10000f/3600f);
		case WALK:	
		default:
			return (5000f/3600f);
		}
	}
}
