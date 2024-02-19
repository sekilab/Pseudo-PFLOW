package pseudo.res;

import java.util.ArrayList;
import java.util.List;


public class HouseHold {
	private String id;
	private int familyType;
	private GLonLat home;
	private String gcode;
	private List<Person> listPersons;
	private GLonLat primarySchool;
	private GLonLat secondarySchool;

	public HouseHold(String id, int familyType, String gcode, GLonLat home) {
		super();
		this.id = id;
		this.familyType = familyType;
		this.home = home;
		this.gcode = gcode;
		this.listPersons = new ArrayList<>();
		this.primarySchool = this.secondarySchool = null;
	}
	
	public String getId() {
		return this.id;
	}

	public int getFamilyType() {
		return familyType;
	}

	public List<Person> getListPersons() {
		return listPersons;
	}
	
	public GLonLat getHome() {
		return home;
	}
	
	public String getGcode() {
		return this.gcode;
	}
	
	public void addMember(Person person) {
		this.listPersons.add(person);
	}

	public GLonLat getPrimarySchool() {
		return primarySchool;
	}

	public GLonLat getSecondarySchool() {
		return secondarySchool;
	}

	public void setSchool(ELabor type, GLonLat school) {
		if (type == ELabor.PRIMARY_SCHOOL) {
			this.primarySchool = school;
		}else {
			this.secondarySchool = school;
		}
	}
}
