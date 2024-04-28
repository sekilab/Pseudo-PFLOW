package pseudo.res;

public class Speed {
	public static final double WALK = 4.8*1000/3600;
	public static final double BYCYCLE = 15.0*1000/3600;
	public static final double CAR = 20.0*1000/3600;
	public static final double TRAIN = 32.0*1000/3600;
	
	public static double get(ETransport mode) {
		switch(mode) {
		case WALK:return WALK;
		case BICYCLE:return BYCYCLE;
		case CAR:return CAR;
		case TRAIN:return TRAIN;
		default:return -1;
		}
	}
}
