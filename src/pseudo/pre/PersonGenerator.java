package pseudo.pre;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import pseudo.acs.EducationAccessor;
import pseudo.acs.HolydayAccessor;
import pseudo.acs.LaborAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.GLonLat;
import pseudo.res.HouseHold;
import pseudo.res.Person;
import utils.Roulette;

public class PersonGenerator{
	
	private static int pesonId = 1;
	private static LaborAccessor laborAcs;
	private static HolydayAccessor holyAcs;
	private static EducationAccessor educationAcs;	
	private static Random random = new Random(100);
	
	public static void writeIds(List<HouseHold> households, BufferedWriter bw) throws IOException{
		for (HouseHold e : households) {
			ILonLat home = e.getHome();
			String line = String.format("%s,%s,%s,%f,%f", e.getId(),
					e.getGcode().substring(0, 2),
					e.getGcode(),
					home.getLon(), home.getLat());
            bw.write(line);
            bw.newLine();
		}
	}
	
	private static int getAge5(int age) {
		switch(age) {
		case 200:return 0;
		case 201:return 5;
		case 202:return 10;
		case 203:return 15;
		case 204:return 20;
		case 205:return 25;
		case 206:return 30;
		case 207:return 35;
		case 208:return 40;
		case 209:return 45;
		case 210:return 50;
		case 211:return 55;
		case 212:return 60;
		case 213:return 65;
		case 214:return 70;
		case 215:return 75;
		case 216:return 80;
		default:return 85;
		}
	}
	
	private static int getAge(int age) {
		return getAge5(age) + random.nextInt(5);
	}
	
	private static HouseHold load(String line){
		HouseHold household = null;
    	String[] items = line.split(",");
    	
    	if (items[75].equals("") != true) {
    		String gid = items[0]; 
	    	int family = !items[8].equals("") ? Integer.valueOf(items[9]) : 16;
	    	String gyousei = String.format("%05d", Integer.valueOf(items[2]));
	    	GLonLat home = new GLonLat(Double.valueOf(items[75]),Double.valueOf(items[76]), gyousei);
	    	
	    	
	    	String householdId = String.format("%s_%s", gyousei, gid);
	    	
	    	// household
	    	household = new HouseHold(householdId, family, gyousei, home);
	    
	    	// husband
	    	if (items[14].equals("") != true && Integer.valueOf(items[14]) >= 0) {
	    		EGender gender = EGender.getType(Integer.valueOf(items[13]));
	    		int age = getAge(Integer.valueOf(items[14]));
	    		new Person(household, pesonId++, age, gender); 
	    	}
			
			// wife
	    	if (items[16].equals("") != true && Integer.valueOf(items[16]) >= 0) {
	    		EGender gender = EGender.getType(Integer.valueOf(items[15]));
	    		int age = getAge(Integer.valueOf(items[16]));
	    		new Person(household,pesonId++, age, gender); 
	    	}           	
	    	
	    	// father
	    	if (items[18].equals("") != true && Integer.valueOf(items[18]) >= 0) {
	    		int age = getAge(Integer.valueOf(items[18]));
	    		new Person(household,pesonId++, age, EGender.MALE); 
	    	}
			
			// mother
	    	if (items[20].equals("") != true && Integer.valueOf(items[20]) >= 0) {
	    		int age = getAge(Integer.valueOf(items[20]));
	    		new Person(household,pesonId++, age, EGender.FEMALE); 
	    	}
	    	
			// child, grandchild, other
	    	for (int i = 22; i <= 74; i+=2) {
	    		if (items[i].equals("") != true && Integer.valueOf(items[i]) >= 0) {
	    			EGender gender = EGender.getType(Integer.valueOf(items[i-1]));
	    			int age = getAge(Integer.valueOf(items[i]));
	        		new Person(household,pesonId++, age, gender); 
	    		}
	    	}
    	}
		return household;
	}
	
	private static int assignLabor(Person person, 
			Map<EGender, List<Double[]>> labor,
			Map<EGender, List<Double>> holyday,
			Map<EGender, Double[]> education) {
		
		int age = person.getAge();		
				
		// 
		if (age < 3) {
			person.setLabor(ELabor.INFANT);
		}else if (age < 6) {
			person.setLabor(ELabor.PRE_SCHOOL);
		}else if (age < 12) {
			person.setLabor(ELabor.PRIMARY_SCHOOL);
		}else if (age < 15) {
			person.setLabor(ELabor.SECONDARY_SCHOOL);
		}else if (age < 18){
			person.setLabor(ELabor.HIGH_SCHOOL);
		}else if (age < 22) {
			EGender gender = person.getGender();
			Double[] prob = education.get(gender);
			int choice = Roulette.choice(prob, random.nextDouble());
			ELabor type = educationAcs.getEducationCode(choice, age);
			person.setLabor(type);
		}
		
		if (person.getLabor() == ELabor.UNDEFINED) {
			int index = (age - 15) / 5;
			EGender gender = person.getGender();
			// labor or not
			List<Double[]> ages = labor.get(gender);
			Double[] values = ages.get(index);
			int choice = Roulette.choice(values, random.nextDouble());
			ELabor type = laborAcs.getLabor(choice);
			person.setLabor(type);
			
			// holiday or not
			if (type == ELabor.WORKER) {
				List<Double> probsHoliday = holyday.get(gender);
				index = index > 12 ? 12 : index;
				double probHoliday = probsHoliday.get(index);
				if (probHoliday <= random.nextDouble()) {
					person.setLabor(ELabor.NO_LABOR);
				}
			}
		}
		return 0;
	}
	
	
	private static int assignLabor(HouseHold household) {
		String gcode = household.getGcode();
		int pref = Integer.valueOf(gcode.substring(0, 2));
		
		Map<EGender, List<Double[]>> labor = laborAcs.get(gcode);
		Map<EGender, List<Double>> holiday = holyAcs.get(pref);
		Map<EGender, Double[]> education = educationAcs.get(pref);
		
		if (labor != null && education != null) {
			for (Person p : household.getListPersons()) {
				assignLabor(p, labor, holiday, education);
			}
		}
		return 1;
	}

	
	private static void load(String filename, List<HouseHold> households){
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
              	if (items[75].equals("") != true) {
	              	HouseHold household = load(line);
	              	if (household != null) {
	              		households.add(household);
	              		assignLabor(household);
	              	}
              	}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public static void main(String[] args) {
		String root = "C:/Users/kashiyama/Desktop/stat/";
		
		// load data
		String laborFile = String.format("%spre_labor_rate.csv", root);
		laborAcs = new LaborAccessor(laborFile);
		
		// load data
		String holyFile = String.format("%spre_holiday_rate.csv", root);
		holyAcs = new HolydayAccessor(holyFile);
		
		String educationFile = String.format("%spre_enrollment_rate.csv", root);
		educationAcs = new EducationAccessor(educationFile);
		
		// output ids
		String outfile = String.format("%s/person/agent/household_ids.csv", root);
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));){
			// generator household data
			File dir = new File(String.format("%skajiwara/", root));
			String dirName = String.format("%s/person/agent/", root);
			for (File f : dir.listFiles()) {
				List<HouseHold> households = new ArrayList<>();
				load(f.getAbsolutePath(), households);
				// write household data
				String output = new File(dirName, String.format("person_%s.csv", f.getName().substring(0, 5))).getAbsolutePath();
				PersonAccessor.write(output, households);
				// write ids
				writeIds(households, bw);
			}
		}catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("end");
	}
}
