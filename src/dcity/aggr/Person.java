package dcity.aggr;

import java.util.List;

import jp.ac.ut.csis.pflow.geom2.ILonLatTime;

public class Person {
	private String id;
	private List<ILonLatTime> listLLTs;
	
	public Person(String id, List<ILonLatTime> listLLTs) {
		super();
		this.id = id;
		this.listLLTs = listLLTs;
	}

	public String getId() {
		return id;
	}

	public List<ILonLatTime> getListLLTs() {
		return listLLTs;
	}	
	
}
