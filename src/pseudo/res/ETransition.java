package pseudo.res;

public enum ETransition {
	STAY(0),
	HOME(1),
	OFFICE(2),
	SCHOOL(3),
	SHOPPING(100),
	EATING(200),
	HOSPITAL(300),
	FREE(400),
	BUSINESS(500);
	
	private final int id;
	
	private ETransition(int id) {
		this.id = id;
	}
	public int getId() {
		return id;
	}
	
	public static ETransition getType(final int id) {
		ETransition[] types = ETransition.values();
		for (ETransition e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
	
	public static ETransition get(EPurpose purpose) {
		return getType(purpose.getId());
	}
	
	public EPurpose getPurpose() {
		return EPurpose.getType(this.getId());
	}
}
