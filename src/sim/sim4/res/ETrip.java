package sim.sim4.res;

public enum ETrip {
	STAY(0),
	CAR(1),
	WALK(2),
	BICYCLE(3),
	TRAIN(4);
	
	private final int code;
	
	ETrip(int code){
		this.code = code;
	}
	
	public int getId() {
		return code;
	}
}
