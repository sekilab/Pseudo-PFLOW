package pseudo.res;

public enum EPTCity {
	BIG3(1),
	NO_BIG3(2);
	
	private final int id;
	
	private EPTCity(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}	
	
	public static EPTCity getType(final int id) {
		EPTCity[] types = EPTCity.values();
		for (EPTCity e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
