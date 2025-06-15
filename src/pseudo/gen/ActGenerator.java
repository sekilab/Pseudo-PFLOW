package pseudo.gen;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.util.internal.ThreadLocalRandom;

import jp.ac.ut.csis.pflow.geom.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import pseudo.acs.MNLParamAccessor;
import pseudo.acs.MkChainAccessor;
import pseudo.res.Activity;
import pseudo.res.City;
import pseudo.res.ECity;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.EMarkov;
import pseudo.res.EPurpose;
import pseudo.res.ETransition;
import pseudo.res.Facility;
import pseudo.res.Country;
import pseudo.res.GLonLat;
import pseudo.res.HouseHold;
import pseudo.res.GMesh;
import pseudo.res.Person;
import pt.MotifAnalyzer;
import utils.Roulette;

public abstract class ActGenerator {
	public Map<Integer, Integer> mapMotif = new HashMap<>();
	protected Country japan;
	protected MNLParamAccessor mnlAcs;
	protected Map<EMarkov,Map<EGender,MkChainAccessor>> mrkAcsMap;
	
	protected static final Dijkstra routing = new Dijkstra();
	
	protected static final long TRAIN_SERVICE_START_TIME = 5 * 3600;
	protected static final int timeInterval = 15 * 60;
	
	protected static final int MAX_SEARCH_DISTANDE = 20000;

	
	public ActGenerator(Country japan,
						MNLParamAccessor mnlAcs,
						Map<EMarkov,Map<EGender,MkChainAccessor>> mrkAcsMap) {
		this.japan = japan;
		this.mnlAcs = mnlAcs;
		this.mrkAcsMap = mrkAcsMap;
	}
	
	protected synchronized double getRandom() {
		return ThreadLocalRandom.current().nextDouble();
	}
	
	protected int formatTime(int time, int interval) {
		return (time / interval) * interval;
	}
	
	protected static Activity createActivity(Activity preActivity, 
			GLonLat dest, int startTime, int endTime, EPurpose purpose) {
		
		// Pre activity
		long preDuration = startTime - preActivity.getStartTime();
		preActivity.setDuration(preDuration);
		
		// Next activity 
		int duration = endTime - startTime;
		Activity res = new Activity(dest,startTime, duration, purpose);
		return res;
	}
	
	protected int setMotif(Person person) {
		List<Activity> acts  = person.getActivities();
		if (acts.size() > 0) {
			List<Integer> list = new ArrayList<>();
			int counter = 100;
			for (Activity a : acts) {
				EPurpose purpose = a.getPurpose();
				int loc = 0;
				if (purpose == EPurpose.HOME || 
						purpose == EPurpose.OFFICE || 
						purpose == EPurpose.SCHOOL) {
					loc = purpose.getId();
				}else {
					loc = counter++;
				}
				list.add(loc);
			}
			list = MotifAnalyzer.compress(list);
			return MotifAnalyzer.getType(1, list);
		}
		return -1;
	}
	
	//
	protected double getMeshCapacity(ETransition transition, GMesh mesh, EGender gender) {
		List<Double> values = mesh.getEconomics();
		if (values.size() > 0) {
			switch (transition) {
			case OFFICE:
				return gender!=EGender.FEMALE ? mesh.getEconomics(14) : mesh.getEconomics(15);
			case SHOPPING:
				return mesh.getEconomics(4);
			case EATING:
				return mesh.getEconomics(new int[]{8,9});
			case FREE:
				return mesh.getEconomics(new int[]{5,7,10,12});
			case BUSINESS:
				return mesh.getEconomics(0);
			default:
				return mesh.getEconomics(0);
			}
		}else {
			return 0;
		}
	}
	
	protected List<Double> getMeshCapacity(ETransition transition, List<GMesh> meshes, EGender gender) {
		List<Double> res = new ArrayList<>();
		for (GMesh mesh : meshes) {
			double capacity = (transition != ETransition.HOSPITAL) ? 
					getMeshCapacity(transition, mesh, gender) : mesh.getHospitalCapacity();
			res.add(capacity);
		}
		return res;
	}
	
	protected List<Facility> getFacilities(ETransition transition, GMesh mesh){
		if (transition == ETransition.HOSPITAL) {
			return mesh.getHospitals();
		}else if(transition==ETransition.SHOPPING){
			return mesh.getRetails();
		}else if(transition==ETransition.EATING){
			return mesh.getRestaurants();
		}else {
			return mesh.getFacilities();
		}
	}

	protected GLonLat choiceDestination(City city, ETransition transition, EGender gender) {
		List<GMesh> meshes = city.getMeshes();
		List<Double> meshCapacities = getMeshCapacity(transition, meshes, gender);

		Set<Integer> excludedMeshes = new HashSet<>();

		while (excludedMeshes.size() < meshes.size()) {
			List<Double> filteredCapacities = new ArrayList<>();
			List<GMesh> filteredMeshes = new ArrayList<>();

			for (int i = 0; i < meshes.size(); i++) {
				if (!excludedMeshes.contains(i)) {
					filteredCapacities.add(meshCapacities.get(i));
					filteredMeshes.add(meshes.get(i));
				}
			}

			if (filteredMeshes.isEmpty()) {
				System.out.println("No valid meshes available.");
				return null;
			}

			int choice = Roulette.choice(filteredCapacities, getRandom());
			GMesh mesh = filteredMeshes.get(choice);

			if (mesh != null) {
				List<Facility> facilities = getFacilities(transition, mesh);
				if (!facilities.isEmpty()) {
					List<Double> facilityCapacities = new ArrayList<>();
					for (Facility f : facilities) {
						facilityCapacities.add(f.getCapacity());
					}
					int facilityChoice = Roulette.choice(facilityCapacities, getRandom());
					Facility fac = facilities.get(facilityChoice);
					return new GLonLat(fac, city.getId());
				} else {
					excludedMeshes.add(meshes.indexOf(mesh));
					System.out.println("No facilities found in mesh: " + mesh.getId() + ". Excluding...");
				}
			} else {
				System.out.println("Mesh selection returned null. Retrying...");
			}
		}

		for (GMesh mesh : meshes) {
			List<Facility> allFacilities = mesh.getFacilities();
			if (!allFacilities.isEmpty()) {
				System.out.println("Fallback: Selecting from all facilities in mesh: " + mesh.getId());
				List<Double> facilityCapacities = new ArrayList<>();
				for (Facility f : allFacilities) {
					facilityCapacities.add(f.getCapacity());
				}
				int facilityChoice = Roulette.choice(facilityCapacities, getRandom());
				Facility fac = allFacilities.get(facilityChoice);
				return new GLonLat(fac, city.getId());
			}
		}

		System.out.println("Fallback failed: No facilities found in any mesh.");
		return null;
	}


	// Only for commute
	protected GLonLat choiceDestination2(GLonLat origin, City city, ETransition transition, EGender gender) {
		List<GMesh> meshes = city.getMeshes();
		List<Double> capacities = getMeshCapacity(transition, meshes, gender);
		Set<Integer> excludedMeshes = new HashSet<>(); // Record indices of meshes without facilities

		while (excludedMeshes.size() < meshes.size()) {
			// Calculate probabilities dynamically, excluding checked meshes
			List<Double> probs = new ArrayList<>();
			List<GMesh> filteredMeshes = new ArrayList<>();
			for (int i = 0; i < meshes.size(); i++) {
				if (!excludedMeshes.contains(i)) {
					GMesh tmesh = meshes.get(i);
					ILonLat center = tmesh.getCenter();
					double distance = DistanceUtils.distance(
							origin.getLon(), origin.getLat(), center.getLon(), center.getLat());
					probs.add(capacities.get(i) / Math.pow(distance, 2));
					filteredMeshes.add(tmesh);
				}
			}

			if (filteredMeshes.isEmpty()) {
				System.out.println("No valid meshes available.");
				return null; // Exit if all meshes have been excluded
			}

			// Select a mesh based on the computed probabilities
			int choice = Roulette.choice(probs, getRandom());
			GMesh mesh = filteredMeshes.get(choice);

			// Search for facilities in the selected mesh
			if (mesh != null) {
				List<Facility> facilities = getFacilities(transition, mesh);
				if (!facilities.isEmpty()) {
					// If facilities are found, select one and return its location
					List<Double> facilityCapacities = new ArrayList<>();
					for (Facility f : facilities) {
						facilityCapacities.add(f.getCapacity());
					}
					int facilityChoice = Roulette.choice(facilityCapacities, getRandom());
					Facility fac = facilities.get(facilityChoice);
					return new GLonLat(fac, city.getId());
				} else {
					// Exclude this mesh and retry
					excludedMeshes.add(meshes.indexOf(mesh));
					System.out.println("No facilities found in mesh: " + mesh.getId() + ". Excluding...");
				}
			} else {
				System.out.println("Mesh selection returned null. Retrying...");
			}
		}

		for (GMesh mesh : meshes) {
			List<Facility> allFacilities = mesh.getFacilities();
			if (!allFacilities.isEmpty()) {
				System.out.println("Fallback: Selecting from all facilities in mesh: " + mesh.getId());
				List<Double> facilityCapacities = new ArrayList<>();
				for (Facility f : allFacilities) {
					facilityCapacities.add(f.getCapacity());
				}
				int facilityChoice = Roulette.choice(facilityCapacities, getRandom());
				Facility fac = allFacilities.get(facilityChoice);
				return new GLonLat(fac, city.getId());
			}
		}

		System.out.println("Fallback failed: No facilities found in any mesh.");
		return null;
	}


	protected GLonLat choiceFreeDestination(GLonLat origin, ETransition transition, boolean senior, EGender gender, ELabor labor) {
		// search mnl parameters
		City city = japan.getCity(origin.getGcode());
		ECity cityType = city.getType();
		List<Double> params = mnlAcs.get(labor, cityType, transition);
		
		if (params == null) {
			System.out.println(transition);
		}
		// search a city
		City dcity = null;
		List<City> cities = japan.searchCities(MAX_SEARCH_DISTANDE, city);
		{
			List<Double> capcities = new ArrayList<>();
			double deno = 0;
			for (City ecity : cities) {
				double dx = ecity.getLon() - city.getLon();
				double dy = ecity.getLat() - city.getLat();
				double distance = Math.sqrt(dx*dx+dy*dy);
                assert params != null;
                double prob = Math.exp(
						params.get(0)*distance +
						params.get(1)*(gender!=EGender.MALE?1:0) +
						params.get(2)*(city.getId().equals(ecity.getId())?1:0) +
						params.get(3)*(senior?1:0) +
						params.get(4)*ecity.getArea() +
						params.get(5)*ecity.getPopRatio()/1000 +
						params.get(6)*ecity.getOfficeRatio()/1000);
				capcities.add(prob);
				deno += prob;
			}
			for (int i = 0; i < capcities.size(); i++) {
				capcities.set(i, capcities.get(i)/deno);
			}
			
			int choice = Roulette.choice(capcities, getRandom());
			dcity = cities.get(choice);
		}
		
		// search a mesh
		if (dcity != null) {
			if (!city.getId().equals(dcity.getId())) {
				return choiceDestination(dcity, transition, gender);
			}else {
				return choiceDestination2(origin, dcity, transition, gender);
			}
		}else{
			System.out.println(cities);
		}
		return null;
	}

    protected GLonLat choiceFreeDestination(GLonLat origin,
                                            ETransition transition,
                                            EGender gender,
                                            Integer threshold) {
        City city = japan.getCity(origin.getGcode());

        List<City> cities = japan.searchCities(threshold, city);
        List<GMesh> meshes = new ArrayList<>();

        for (City ecity : cities) {
            meshes.addAll(ecity.getMeshes());
        }
        List<Double> meshCapacities = getMeshCapacity(transition, meshes, gender);

        Set<Integer> excludedMeshes = new HashSet<>();

        while (excludedMeshes.size() < meshes.size()) {
            List<Double> filteredCapacities = new ArrayList<>();
            List<GMesh> filteredMeshes = new ArrayList<>();

            for (int i = 0; i < meshes.size(); i++) {
                if (!excludedMeshes.contains(i)) {
                    filteredCapacities.add(meshCapacities.get(i));
                    filteredMeshes.add(meshes.get(i));
                }
            }

            if (filteredMeshes.isEmpty()) {
                System.out.println("No valid meshes available.");
                return null;
            }

            int choice = Roulette.choice(filteredCapacities, getRandom());
            GMesh mesh = filteredMeshes.get(choice);

            if (mesh != null) {
                List<Facility> facilities = getFacilities(transition, mesh);
                if (!facilities.isEmpty()) {
                    List<Double> facilityCapacities = new ArrayList<>();
                    for (Facility f : facilities) {
                        facilityCapacities.add(f.getCapacity());
                    }
                    int facilityChoice = Roulette.choice(facilityCapacities, getRandom());
                    Facility fac = facilities.get(facilityChoice);
                    return new GLonLat(fac, city.getId());
                } else {
                    excludedMeshes.add(meshes.indexOf(mesh));
                    System.out.println("No facilities found in mesh: " + mesh.getId() + ". Excluding...");
                }
            } else {
                System.out.println("Mesh selection returned null. Retrying...");
            }
        }

        for (GMesh mesh : meshes) {
            List<Facility> allFacilities = mesh.getFacilities();
            if (!allFacilities.isEmpty()) {
                System.out.println("Fallback: Selecting from all facilities in mesh: " + mesh.getId());
                List<Double> facilityCapacities = new ArrayList<>();
                for (Facility f : allFacilities) {
                    facilityCapacities.add(f.getCapacity());
                }
                int facilityChoice = Roulette.choice(facilityCapacities, getRandom());
                Facility fac = allFacilities.get(facilityChoice);
                return new GLonLat(fac, city.getId());
            }
        }

        System.out.println("Fallback failed: No facilities found in any mesh.");
        return null;
    }



    protected abstract Callable<Integer> createTask(Map<Integer, Integer> mapMotif, int id, List<HouseHold> households);
	
	
	public int assign(List<HouseHold> household) {
		// prepare thread processing
		int numThreads = Runtime.getRuntime().availableProcessors();
		System.out.println("NumOfThreads:" + numThreads);
		
		List<Callable<Integer> > listTasks = new ArrayList<>();
		int listSize = household.size();
		int taskNum =numThreads * 10;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = Math.min(listSize, end);
			List<HouseHold> subList = household.subList(i, end);
			listTasks.add(createTask(mapMotif, i/stepSize, subList));
		}
		System.out.println("NumOfTasks:" + listTasks.size());
		
		// execute thread processing
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		try {
			es.invokeAll(listTasks);
			es.shutdown();
		} catch (Exception exp) {
			exp.printStackTrace();
		}		
		return 0;
	}
}
