package pseudo.res;

public enum ETransport {
	NOT_DEFINED(0),
	WALK(1),
	BICYCLE(2),
	CAR(3),
	TRAIN(4),
	BUS(5),
	MIX(6),
	COMMUNITY(7);
	
	private final int id;
	
	private ETransport(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}	
	
	public static ETransport getType(final int id) {
		ETransport[] types = ETransport.values();
		for (ETransport e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
