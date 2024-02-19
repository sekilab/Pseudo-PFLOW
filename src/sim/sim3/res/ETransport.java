package sim.sim3.res;

/**
 * Transport Type
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public enum ETransport {
	/**	Stay		*/	STAY(99),
	/**	Walk		*/	WALK(1),
	/**	Car			*/	VEHICLE(2),
	/**	Train		*/	TRAIN(3),
	/**	Bicycle		*/	BICYCLE(4);
	
	private final int id;

	private ETransport(int id){
		this.id = id;
	}
	public int getId(){
		return this.id;
	}
	public static ETransport valueOf(int id){
		for (ETransport num : values()){
			if (num.getId() == id){
				return num;
			}
		}
		return null;
	}
	
	public ENetwork getNetwork(){
		switch (this){
		case VEHICLE: 
			return ENetwork.VEHICLE;
		case TRAIN:
			return ENetwork.TRAIN;
		case BICYCLE:
			return ENetwork.BICYCLE;
		default:
			return ENetwork.WALK;
		}
	}
}
