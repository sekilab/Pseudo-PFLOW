package pseudo.res;

public enum EMarkov {
	LABOR(1),
	STUDENT1(2),
	STUDENT2(3),
	NOLABOR_JUNIOR(4),
	NOLABOR_SENIOR(5);
	
	private final int id;
	
	private EMarkov(int id) {
		this.id = id;
	}
	public int getId() {
		return id;
	}
	
	public static EMarkov getType(final int id) {
		EMarkov[] types = EMarkov.values();
		for (EMarkov e : types) {
			if (e.getId() == id) {
				return e;
			}
		}
		return null;
	}
}
