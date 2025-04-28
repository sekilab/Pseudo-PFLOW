
package pseudo.gen;


import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import org.opengis.referencing.FactoryException;
import pseudo.acs.CensusODAccessor;
import pseudo.acs.DataAccessor;
import pseudo.acs.MNLParamAccessor;
import pseudo.acs.MkChainAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.acs.CensusODAccessor.EType;
import pseudo.res.Activity;
import pseudo.res.CensusOD;
import pseudo.res.City;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.EMarkov;
import pseudo.res.EPurpose;
import pseudo.res.ETransition;
import pseudo.res.HouseHold;
import pseudo.res.Country;
import pseudo.res.GLonLat;
import pseudo.res.Person;
import utils.Roulette;

public class Commuter extends ActGenerator {

	private final CensusODAccessor odAcs;
	
	public Commuter(Country japan,
					Map<EMarkov,Map<EGender,MkChainAccessor>> mrkAcsMap,
					MNLParamAccessor mnlAcs,
					CensusODAccessor odAcs) {
		super(japan, mnlAcs, mrkAcsMap);
		this.odAcs = odAcs;
	}
	
	private class ActivityTask implements Callable<Integer> {
		private final int id;
		private final List<HouseHold> households;
		private final Map<Integer, Integer> mapMotif;
		private int error;
		private int total;

		public ActivityTask(int id, List<HouseHold> households,
				Map<Integer, Integer> mapMotif){
			this.id = id;
			this.households = households;
			this.mapMotif = mapMotif;
			this.total = error = 0;
		}	
		
		private GLonLat choiceOffice(GLonLat home, EGender gender) {
			City city = japan.getCity(home.getGcode());
			CensusOD censusOD = odAcs.get(EType.COMMUTER, city.getId());
			if (censusOD != null) {
				List<Double> capacities = censusOD.getCapacities(gender);
				if (!capacities.isEmpty()) {
					int choice = Roulette.choice(capacities, getRandom());	
					boolean isHome = censusOD.isHome(choice);
					if (isHome) {
						return home;
					}else {
						String cityName = censusOD.getDestination(gender, choice);
						City dcity = japan.getCity(cityName);
						if (dcity != null) {
							if (!city.getId().equals(dcity.getId())) {
								return choiceByFacilityCapacity(dcity, ETransition.OFFICE, gender);
							}else {
								return choiceByDistanceWeightedCapacity(home, dcity, ETransition.OFFICE, gender);
							}
						}
					}
				}
			}
			return null;
		}

		private ETransition freeTransitionFilter(ETransition transition) {
			if (	transition != ETransition.STAY && 
					transition != ETransition.HOME && 
					transition != ETransition.SHOPPING &&  
					transition != ETransition.EATING &&  
					transition != ETransition.HOSPITAL &&  
					transition != ETransition.FREE && 
					transition != ETransition.BUSINESS) {
				transition = ETransition.FREE;
			}
			return transition;
		}
		
		private int createActivities(HouseHold household, Person person) {
			GLonLat home = new GLonLat(household.getHome(), household.getGcode());
			EGender gender = person.getGender();
		
			// Markov Accessor
			MkChainAccessor mkAcs = mrkAcsMap.get(EMarkov.LABOR).get(gender);
			boolean senior = person.getAge() >= 65;
			
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
					// choice destination
					if (transition == ETransition.HOME) {
						curloc = home;
					}else if (transition == ETransition.OFFICE) {
						curloc = person.hasOffice() ? person.getOffice() : choiceOffice(home, gender); 
						person.setOffice(curloc);
					}else {
						transition = freeTransitionFilter(transition);
						curloc = choiceFreeDestination(curloc, transition, senior, gender, person.getLabor());
					}
					if (curloc == null) {
						person.getActivities().clear();
						person.addAcitivity(homeAct);
						return 3;
					}
					
					// Create an activity
					preAct = Commuter.createActivity(preAct, curloc, i, 3600*24, purpose);
					person.getActivities().add(preAct);
					
					prePurpose = purpose;
				}
			}
//			if(person.getActivities().size()==1){
//				System.out.println("===================================================");
//			}
			return 0;
		}
		
		private void process(HouseHold household) {
			for (Person person : household.getListPersons()) {
				int res = createActivities(household, person);
				if (res == 0) {
					int motif = setMotif(person);
					synchronized(mapMotif) {
						int vol = mapMotif.getOrDefault(motif, 0);
						mapMotif.put(motif, vol + 1);
					}
				}else {
					this.error++;
				}
				this.total++;
			}
		}

		@Override
		public Integer call() throws Exception {
			try {
				for (HouseHold household : households) {
					process(household);		
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
			System.out.printf("[%d]-%d-%d%n",id, error, total);
			return 0;
		}
	}
	
	protected Callable<Integer> createTask(Map<Integer, Integer> mapMotif, int id, List<HouseHold> households){
		return new ActivityTask(id, households, mapMotif);
	}

	public static void main(String[] args) throws IOException, FactoryException {

        Country country = new Country();

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
        country.setStation(station);

        String cityFile = String.format("%scity_boundary.csv", inputDir);
        DataAccessor.loadCityData(cityFile, country);

        String censusFile = String.format("%scity_census_od.csv", inputDir);
        CensusODAccessor odAcs = new CensusODAccessor(censusFile, country);

        String hospitalFile = String.format("%scity_hospital.csv", inputDir);
        DataAccessor.loadHospitalData(hospitalFile, country);

		String restaurantFile = String.format("%scity_restaurant.csv", inputDir);
		DataAccessor.loadRestaurantData(restaurantFile, country);

		String retailFile = String.format("%scity_retail.csv", inputDir);
		DataAccessor.loadRetailData(retailFile, country);

        String meshFile = String.format("%smesh_ecensus.csv", inputDir);
        DataAccessor.loadEconomicCensus(meshFile, country);

        // load data after economic census
        String tatemonFile = String.format("%scity_tatemono.csv", inputDir);
        DataAccessor.loadZenrinTatemono(tatemonFile, country, 1);

        // load MNL parmaters
        String mnlFile = String.format("%s/mnl/labor_params.csv", inputDir);
        MNLParamAccessor mnlAcs = new MNLParamAccessor();
        mnlAcs.add(mnlFile, ELabor.WORKER);

        // 10 times
        int mfactor = 1;

        // create activities

        String outputDir = String.format("%s/activity/", root);

        long starttime = System.currentTimeMillis();
        int start = 10;
        for (int i = start; i <= 12; i++) {

			// load markov chains
			Map<EMarkov, Map<EGender, MkChainAccessor>> mrkMap = new HashMap<>();
			{
				String key = "pref." + i;
				String relativePath = prop.getProperty(key);
				String maleFile = inputDir+ relativePath + "_trip_labor_male_prob.csv";
				String femaleFile = inputDir+ relativePath + "_trip_labor_female_prob.csv";
				Map<EGender, MkChainAccessor> map = new HashMap<>();
				map.put(EGender.MALE, new MkChainAccessor(maleFile));
				map.put(EGender.FEMALE, new MkChainAccessor(femaleFile));
				mrkMap.put(EMarkov.LABOR, map);
			}
			Commuter worker = new Commuter(country, mrkMap, mnlAcs, odAcs);

            // create directory
            File prefDir = new File(outputDir, String.valueOf(i));
            System.out.println("Start prefecture:" + i + prefDir.mkdirs());
            File householdDir = new File(String.format("%s/agent/", root), String.valueOf(i));
            // String householdDir = String.format("%s/agent/", root);

            for (File file : householdDir.listFiles()) {
                if (file.getName().contains(".csv")) {
                    List<HouseHold> households = PersonAccessor.load(file.getAbsolutePath(), new ELabor[]{ELabor.WORKER}, mfactor);
                    System.out.println(file.getName() + " " + households.size());
                    worker.assign(households);
                    String resultName = String.format("%s%s%s%s_labor.csv", outputDir, i, "/", file.getName().replaceAll(".csv", ""));
                    PersonAccessor.writeActivities(resultName, households);
                }
            }
            System.out.println("end");
            long endtime = System.currentTimeMillis();
			System.out.println(worker.mapMotif);
            System.out.println(endtime - starttime);
        }
    }

}
