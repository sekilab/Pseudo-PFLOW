package pseudo.gen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import org.opengis.referencing.FactoryException;
import pseudo.acs.DataAccessor;
import pseudo.acs.MNLParamAccessor;
import pseudo.acs.MkChainAccessor;
import pseudo.acs.PersonAccessor;
import pseudo.res.Activity;
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

public class NonCommuter extends ActGenerator {

	public NonCommuter(Country japan,
					   Map<EMarkov,Map<EGender,MkChainAccessor>> mrkAcsMap,
					   MNLParamAccessor mnlAcs) {
		super(japan, mnlAcs, mrkAcsMap);
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

		private int createActivities(HouseHold household, Person person) {
			GLonLat home = new GLonLat(household.getHome(), household.getGcode());
			EGender gender = person.getGender();

			// Markov Accessor
			boolean senior = person.getAge() >= 65;
			EMarkov type = senior ? EMarkov.NOLABOR_SENIOR : EMarkov.NOLABOR_JUNIOR;
			MkChainAccessor mkAcs = mrkAcsMap.get(type).get(gender);

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
				transition = freeTransitionFilter(transition);

				EPurpose purpose = transition.getPurpose();

				if (transition != ETransition.STAY) {
					// choose a destination
					curloc = transition!=ETransition.HOME ?
							choiceFreeDestination(curloc, transition, senior, gender, person.getLabor()) : home;
                            //choiceByDistanceWeightedCapacity(curloc, null, transition, gender) : home;
					if (curloc == null) {
						person.getActivities().clear();
						person.addAcitivity(homeAct);
						return 3;
					}

					// Create an activity
					preAct = NonCommuter.createActivity(preAct, curloc, i, 3600*24, purpose);
					person.getActivities().add(preAct);

					prePurpose = purpose;
				}
			}
			return 0;
		}

		private void process(HouseHold household) {
			for (Person person : household.getListPersons()) {
				int res = createActivities(household, person);
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

		@Override
		public Integer call() throws Exception {
			try {
				for (HouseHold household : households) {
					process(household);
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
			System.out.println(String.format("[%d]-%d-%d",id, error, total));
			return 0;
		}
	}


	protected Callable<Integer> createTask(Map<Integer, Integer> mapMotif, int id, List<HouseHold> households){
		return new ActivityTask(id, households, mapMotif);
	}

	public static void main(String[] args) throws IOException, FactoryException {

		Country japan = new Country();

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

		String hospitalFile = String.format("%scity_hospital.csv", inputDir);
		DataAccessor.loadHospitalData(hospitalFile, japan);

		String restaurantFile = String.format("%scity_restaurant.csv", inputDir);
		DataAccessor.loadRestaurantData(restaurantFile, japan);

		String retailFile = String.format("%scity_retail.csv", inputDir);
		DataAccessor.loadRetailData(retailFile, japan);

		String meshFile = String.format("%smesh_ecensus.csv", inputDir);
		DataAccessor.loadEconomicCensus(meshFile, japan);

		// load data after ecensus
		String tatemonFile = String.format("%scity_tatemono.csv", inputDir);
		DataAccessor.loadZenrinTatemono(tatemonFile, japan, 1);


		// load MNL parmaters
		String mnlFile = String.format("%s/mnl/nolabor_params.csv", inputDir);
		MNLParamAccessor mnlAcs = new MNLParamAccessor();
		mnlAcs.add(mnlFile, ELabor.NO_LABOR);

		int mfactor = 1;

		// create activities
		String outputDir = String.format("%s/activity/", root);

		long starttime = System.currentTimeMillis();
		int start = 22;
        int end = 22;
		for (int i = start; i <= end; i++) {
			// create directory
			File prefDir = new File(outputDir, String.valueOf(i));
			System.out.println("Start prefecture:" + i + prefDir.mkdirs());
			File householdDir = new File(String.format("%s/agent/", root), String.valueOf(i));
			// String householdDir = String.format("%s/agent/", root);

			// load markov data
			Map<EMarkov,Map<EGender,MkChainAccessor>> mrkMap = new HashMap<>();
			{
				String key = "pref." + i;
				String relativePath = prop.getProperty(key);
				String maleFile = inputDir+ relativePath + "_trip_nolabor_male_prob.csv";
				String femaleFile = inputDir+ relativePath + "_trip_nolabor_female_prob.csv";
				Map<EGender, MkChainAccessor> map = new HashMap<>();
				map.put(EGender.MALE, new MkChainAccessor(maleFile));
				map.put(EGender.FEMALE, new MkChainAccessor(femaleFile));
				mrkMap.put(EMarkov.NOLABOR_JUNIOR, map);
			}
			{
				String key = "pref." + i;
				String relativePath = prop.getProperty(key);
				String maleFile = inputDir+ relativePath + "_trip_nolabor_male_senior_prob.csv";
				String femaleFile = inputDir+ relativePath + "_trip_nolabor_female_senior_prob.csv";
				Map<EGender, MkChainAccessor> map = new HashMap<>();
				map.put(EGender.MALE, new MkChainAccessor(maleFile));
				map.put(EGender.FEMALE, new MkChainAccessor(femaleFile));
				mrkMap.put(EMarkov.NOLABOR_SENIOR, map);
			}
			NonCommuter worker = new NonCommuter(japan, mrkMap, mnlAcs);

			for (File file : householdDir.listFiles()) {
				if (file.getName().contains(".csv")) {
					// load household
					List<HouseHold> households = PersonAccessor.load(file.getAbsolutePath(), new ELabor[] {ELabor.NO_LABOR}, mfactor);
					worker.assign(households);
					String resultName = String.format("%s%s%s%s_nolabor.csv", outputDir, i, "/", file.getName().replaceAll(".csv", ""));
					PersonAccessor.writeActivities(resultName, households);
				}
			}
			System.out.println("end");
			long endtime = System.currentTimeMillis();
			System.out.println(endtime-starttime);
			System.out.println(worker.mapMotif);
			System.out.println(endtime - starttime);
		}

	}
}
