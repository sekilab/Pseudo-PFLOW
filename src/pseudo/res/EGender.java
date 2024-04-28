package pseudo.res;

public enum EGender {
	MALE(1),
	FEMALE(2);
	
	private final int id;
	
	private EGender(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}	
	
	public static EGender getType(final int id) {
		EGender[] types = EGender.values();
		for (EGender e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
