
package sim.sim3.res;

/**
 * Network type
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public enum ENetwork {
	/**	Centroid		*/	CENTROID(0),
	/**	Walk			*/	WALK(1),
	/**	Vehicle			*/	VEHICLE(2),
	/**	Train			*/	TRAIN(3),
	/**	Bicycle			*/	BICYCLE(4);
	
	private final int id;

	private ENetwork(int id){
		this.id = id;
	}
	public int getId(){
		return this.id;
	}
	public static ENetwork valueOf(int id){
		for (ENetwork num : values()){
			if (num.getId() == id){
				return num;
			}
		}
		return null;
	}
}
