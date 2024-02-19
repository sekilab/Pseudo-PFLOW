package pseudo.res;

public enum ECity {
	UNDER10(1),
	UPPER10(2),
	UPPER50(3);
	
	private final int id;
	
	private ECity(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}	
	
	public static ECity getType(final int id) {
		ECity[] types = ECity.values();
		for (ECity e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
