package pseudo.res;

public enum EPurpose {
	HOME(1),
	OFFICE(2),
	SCHOOL(3),
	RETURN_OFFICE(4),
	SHOPPING(100),
	EATING(200),
	HOSPITAL(300),
	FREE(400),
	BUSINESS(500);
	
	private final int id;
	
	private EPurpose(int id) {
		this.id = id;
	}
	public int getId() {
		return id;
	}
	
	public static EPurpose getType(final int id) {
		EPurpose[] types = EPurpose.values();
		for (EPurpose e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
