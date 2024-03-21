package pseudo.gen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Node;
import pseudo.acs.CensusODAccessor;
import pseudo.acs.DataAccessor;
import pseudo.acs.MNLParamAccessor;
import pseudo.acs.MkChainAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.acs.SchoolRefAccessor;
import pseudo.acs.CensusODAccessor.EType;
import pseudo.res.Activity;
import pseudo.res.CensusOD;
import pseudo.res.City;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.EMarkov;
import pseudo.res.EPurpose;
import pseudo.res.ETransition;
import pseudo.res.Facility;
import pseudo.res.HouseHold;
import pseudo.res.Japan;
import pseudo.res.GLonLat;
import pseudo.res.Person;
import utils.Roulette;

public class Student extends ActGenerator {

	private CensusODAccessor odAcs;
	private SchoolRefAccessor schRefAcs;
	private static final double SCHOOL_MAX_DISTANCE = 5000;
	
	public Student(Japan japan, 
			Map<EMarkov,Map<EGender,MkChainAccessor>> mrkAcsMap, 
			MNLParamAccessor mnlAcs,
			CensusODAccessor odAcs,
			SchoolRefAccessor schRefAcs) {
		super(japan, mnlAcs,mrkAcsMap);
		
		this.odAcs = odAcs;
		this.schRefAcs = schRefAcs;
	}
	
	private class ActivityTask implements Callable<Integer> {
		private int id;
		private List<HouseHold> households;
		private Map<Integer, Integer> mapMotif;
		private int error;
		private int total;

		public ActivityTask(int id, List<HouseHold> households,
				Map<Integer, Integer> mapMotif){
			this.id = id;
			this.households = households;
			this.mapMotif = mapMotif;
			this.total = error = 0;
		}	
		
		
		private EMarkov getTypeMarkov(ELabor labor) {
			switch (labor) {
			case PRE_SCHOOL: 
			case PRIMARY_SCHOOL: 
			case SECONDARY_SCHOOL:
				return EMarkov.STUDENT1;
			case HIGH_SCHOOL:
			case JUNIOR_COLLEGE:
			case COLLEGE:
			default:
			}
			return EMarkov.STUDENT2;
		}
		
		private List<String> choiceCityWithSchools(ELabor labor, Set<String> names){
			List<String> res = new ArrayList<>();
			for (String e : names) {
				City city = japan.getCity(e);
				if (city != null) {
					List<Facility> schools = city.getSchools(labor);
					if (schools != null && schools.size() > 0) {
						res.add(e);
					}
				}
			}
			return res;
		}
		
		private ETransition freeTransitionFilter(ETransition transition) {
			if (	transition != ETransition.STAY && 
					transition != ETransition.HOME && 
					transition != ETransition.SHOPPING &&  
					transition != ETransition.EATING &&  
					transition != ETransition.HOSPITAL &&  
					transition != ETransition.FREE) {
				transition = ETransition.FREE;
			}
			return transition;
		}
		
		private GLonLat choiceSchool(GLonLat gloc, Person person) {
			City city = japan.getCity(gloc.getGcode());	
			ELabor labor = person.getLabor();
			if (city != null) {
				if (labor == ELabor.PRE_SCHOOL) {
					List<Facility> schools = city.getSchools(labor);
					if (schools != null && schools.size() > 0) {
						int choice = (int)(getRandom()*schools.size());
						return schools.get(choice);
					}
				
				}else if (labor == ELabor.PRIMARY_SCHOOL) {
					HouseHold household = person.getParent();
					return (household.getPrimarySchool());
				
				}else if (labor == ELabor.SECONDARY_SCHOOL) {
					HouseHold household = person.getParent();
					return (household.getSecondarySchool());
					
				}else {
					CensusOD censusOD = odAcs.get(EType.STUDENT, city.getId());
					if (censusOD != null) {
						EGender gender = person.getGender();
						Set<String> names = censusOD.getDestinationNames(gender);
						List<String> selectedNames = choiceCityWithSchools(labor, names);
						List<Double> capacities = censusOD.getCapacities(gender, selectedNames);
						if (capacities.size() > 0) {
							City dcity = null;
							{
								int choice = Roulette.choice(capacities, getRandom());
								String cityName = selectedNames.get(choice);
								dcity = japan.getCity(cityName);
							}
							if (dcity != null) {							
								List<Facility> schools = dcity.getSchools(labor);
								int choice = (int) (getRandom() * schools.size());
								return schools.get(choice);
							}
						}
					}
				}
			}
			return null;
		}
			
		private int createActivity(HouseHold household, Person person) {
			GLonLat home = new GLonLat(household.getHome(), household.getGcode());
			EGender fixedGender = EGender.MALE;	// Fixed value
			EGender gender = person.getGender();
		
			if (person.getId() == 352750) {
				System.out.println("aa");
			}
			
			// Markov Accessor
			ELabor labor = person.getLabor();
			EMarkov type = getTypeMarkov(labor);
			MkChainAccessor mkAcs = mrkAcsMap.get(type).get(fixedGender);
			
			// first activity
			EPurpose prePurpose = EPurpose.HOME;
			Activity homeAct = new Activity(home, 0, 24*3600, EPurpose.HOME);
			person.addAcitivity(homeAct);
			
			// second... activity
			GLonLat curloc = home;
			Activity preAct = homeAct;
			for (int i = 3*3600; i < 3600*24; i += timeInterval) {
				ETransition transition = null;
				List<Double> probs = mkAcs.getProbs(i, prePurpose);

				double randomValue = getRandom();
				int choice = Roulette.choice(probs, randomValue);
				transition = mkAcs.getTransition(choice);
				EPurpose purpose = transition.getPurpose();
				
				if (transition != ETransition.STAY) {
					if (transition == ETransition.HOME) {
						curloc = home;
					}else if (transition == ETransition.SCHOOL) {
						curloc = person.hasOffice() ? person.getOffice() : choiceSchool(curloc, person); 
						person.setOffice(curloc);
					}else {
						transition = freeTransitionFilter(transition);
						curloc = choiceFreeDestination(curloc, transition, true, gender, person.getLabor());
					}
					
					if (curloc == null) {
						person.getActivities().clear();
						person.addAcitivity(homeAct);
						return 3;
					}
					
					// Create an activity
					preAct = Student.createActivity(preAct, curloc, i, 3600*24, purpose);
					person.getActivities().add(preAct);
					
					prePurpose = purpose;
				}
			}					
			return 0;
		}
		
		private void process(HouseHold household) {
			for (Person person : household.getListPersons()) {
				int res = createActivity(household, person);
				if (res == 0) {
					int motif = setMotif(person);
					synchronized(mapMotif) {
						int vol = mapMotif.containsKey(motif) ? mapMotif.get(motif) : 0;
						mapMotif.put(motif, vol + 1);
					}
				}else {
					this.error++;
				}
				this.total++;
			}
		}

		private void assignSchool(HouseHold household) {
			String householdid = household.getId();
			ELabor[] types = {ELabor.PRIMARY_SCHOOL, ELabor.SECONDARY_SCHOOL};
			GLonLat home = household.getHome();
			String gcode = household.getGcode();
			City city = japan.getCity(gcode);
			for (ELabor labor : types) {
				GLonLat school = schRefAcs.getId(labor, householdid);
				if (school != null) {
					household.setSchool(labor, school);
				}else {
					List<Facility> facs = city.getSchools(labor);
					if (facs != null) {
						Network network = new Network();
						for (Facility f : facs) {
							network.addNode(new Node(String.valueOf(f.getId()), f.getLon(), f.getLat()));
						}
						Node node = routing.getNearestNode(network, home.getLon(), home.getLat(), SCHOOL_MAX_DISTANCE);
						if (node != null) {
							school = new GLonLat(node.getLon(), node.getLat(), gcode);
							household.setSchool(labor, school);
						}
					}
				}
			}
		}
		
		@Override
		public Integer call() throws Exception {
			try {
				for (HouseHold household : households) {
					if (household.getListPersons().size() > 0) {
						assignSchool(household);
						process(household);		
					}
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
			System.out.println(String.format("[%d] error:%d total:%d",id, error, total));
			return 0;
		}
	}
		
	protected Callable<Integer> createTask(Map<Integer, Integer> mapMotif, int id, List<HouseHold> households){
		return new ActivityTask(id, households, mapMotif);
	}
	
	public static void main(String[] args) throws IOException {
		
		Japan japan = new Japan();
		
		System.out.println("start");

		String inputDir = null;
		String root = null;

		InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		Properties prop = new Properties();
		prop.load(inputStream);

		root = prop.getProperty("root");
		inputDir = prop.getProperty("inputDir");
		System.out.println("Root Directory: " + root);
		System.out.println("Input Directory: " + inputDir);
		
		// load data
		String stationFile = String.format("%sbase_station.csv", inputDir);
		Network station = DataAccessor.loadLocationData(stationFile);
		japan.setStation(station);
		
		String cityFile = String.format("%scity_boundary.csv", inputDir);
		DataAccessor.loadCityData(cityFile, japan);
		
		String censusFile = String.format("%scity_census_od.csv", inputDir);
		CensusODAccessor odAcs = new CensusODAccessor(censusFile, japan);
		
		String hospitalFile = String.format("%scity_hospital.csv", inputDir);
		DataAccessor.loadHospitalData(hospitalFile, japan);
				
		String preschoolFile = String.format("%scity_pre_school.csv", inputDir);
		DataAccessor.loadPreSchoolData(preschoolFile, japan);
		
		String schoolFile = String.format("%scity_school.csv", inputDir);
		DataAccessor.loadSchoolData(schoolFile, japan);
				
		String meshFile = String.format("%smesh_ecensus.csv", inputDir);
		DataAccessor.loadEconomicCensus(meshFile, japan);

		// load data after ecensus
		String tatemonFile = String.format("%scity_tatemono.csv", inputDir);
		DataAccessor.loadZenrinTatemono(tatemonFile, japan, 1);
		
		// load markov data
		Map<EMarkov,Map<EGender,MkChainAccessor>> mrkMap = new HashMap<>();
		{	
			String maleFile = String.format("%s/markov/tky2008_trip_11-11_student1_prob.csv", inputDir);
			Map<EGender, MkChainAccessor> map = new HashMap<>();
			map.put(EGender.MALE, new MkChainAccessor(maleFile));
			mrkMap.put(EMarkov.STUDENT1, map);
		}
		{	
			String maleFile = String.format("%s/markov/tky2008_trip_12-13_student2_prob.csv", inputDir);
			Map<EGender, MkChainAccessor> map = new HashMap<>();
			map.put(EGender.MALE, new MkChainAccessor(maleFile));
			mrkMap.put(EMarkov.STUDENT2, map);
		}		
		
		// load MNL parmaeters
		MNLParamAccessor mnlAcs = new MNLParamAccessor();
		String mnlFile1 = String.format("%s/mnl/student1_params.csv", inputDir);
		mnlAcs.add(mnlFile1, ELabor.PRE_SCHOOL);
		mnlAcs.add(mnlFile1, ELabor.PRIMARY_SCHOOL); 
		mnlAcs.add(mnlFile1, ELabor.SECONDARY_SCHOOL);
		
		String mnlFile2 = String.format("%s/mnl/student2_params.csv", inputDir);
		mnlAcs.add(mnlFile2, ELabor.HIGH_SCHOOL);
		mnlAcs.add(mnlFile2, ELabor.JUNIOR_COLLEGE);
		mnlAcs.add(mnlFile2, ELabor.COLLEGE);
								
		
		// prepare an accessor for school
		SchoolRefAccessor schAcs = new SchoolRefAccessor();
		
		int mfactor = 1;
		String prePref = "";
		
		// create activities
		Student worker = new Student(japan, mrkMap, mnlAcs, odAcs, schAcs);
		String outputDir = String.format("%s/activity/", root);

		int start = 1;
		for (int i = start; i <= 47; i++) {
			// create directory
			File prefDir = new File(outputDir, String.valueOf(i));
			System.out.println("Start prefecture:" + i + prefDir.mkdirs());
			File householdDir = new File(String.format("%s/agent/", root), String.valueOf(i));
			// String householdDir = String.format("%s/agent/", root);

			for (File file : householdDir.listFiles()) {
				if (file.getName().contains(".csv")) {
					String name = file.getName();
					String pref = name.substring(7, 9);

					//　load school data
					if (!prePref.equals(pref)) {
						schAcs.load(String.format("%sschool/primary_%s.csv", inputDir, pref), ELabor.PRIMARY_SCHOOL);
						schAcs.load(String.format("%sschool/secondary_%s.csv", inputDir, pref), ELabor.SECONDARY_SCHOOL);
					}

					if (file.getName().contains(".csv")) {

						// load household
						List<HouseHold> households = PersonAccessor.load(file.getAbsolutePath(), new ELabor[]{
								ELabor.PRE_SCHOOL,
								ELabor.PRIMARY_SCHOOL,
								ELabor.SECONDARY_SCHOOL,
								ELabor.HIGH_SCHOOL,
								ELabor.JUNIOR_COLLEGE, ELabor.COLLEGE}, mfactor);
						System.out.println(file.getName() + " " + households.size());
						worker.assign(households);
						String resultName = String.format("%s%s%s%s_student.csv", outputDir, i, "/", file.getName().replaceAll(".csv", ""));
						PersonAccessor.writeActivities(resultName, households);
					}
				}
			}
			System.out.println("end");
		}
	}
}
