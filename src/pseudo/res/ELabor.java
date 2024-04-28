package pseudo.res;

public enum ELabor {
	WORKER(21),
	JOBLESS(22),
	NO_LABOR(23),
	UNDEFINED(4),
	INFANT(10),
	PRE_SCHOOL(11),
	PRIMARY_SCHOOL(12),
	SECONDARY_SCHOOL(13),
	HIGH_SCHOOL(14),
	COLLEGE(15),
	JUNIOR_COLLEGE(16);
	
	private final int id;
	
	private ELabor(int id) {
		this.id = id;
	}
	
	public int getId() {
		return id;
	}	
	
	public static ELabor getType(final int id) {
		ELabor[] types = ELabor.values();
		for (ELabor e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
