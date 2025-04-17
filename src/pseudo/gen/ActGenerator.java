package pseudo.gen;

import java.util.*;
import java.util.concurrent.*;

import jp.ac.ut.csis.pflow.geom.DistanceUtils;
import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import pseudo.acs.MNLParamAccessor;
import pseudo.acs.MkChainAccessor;
import pseudo.res.*;
import pt.MotifAnalyzer;
import utils.Roulette;

public abstract class ActGenerator {

	protected Country japan;
	protected MNLParamAccessor mnlAcs;
	protected Map<EMarkov,Map<EGender,MkChainAccessor>> mrkAcsMap;
	
	protected static final Dijkstra routing = new Dijkstra();
	
	// protected static final long TRAIN_SERVICE_START_TIME = 5 * 3600;
	protected static final int timeInterval = 15 * 60;
	protected static final int MAX_SEARCH_DISTANCE = 20000;

    public Map<Integer, Integer> mapMotif = new HashMap<>();

	
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

		preActivity.setDuration(startTime - preActivity.getStartTime());
		return new Activity(dest, startTime, endTime - startTime, purpose);
	}
	
	protected int setMotif(Person person) {
		List<Activity> acts  = person.getActivities();
		if (!acts.isEmpty()) {
			List<Integer> list = new ArrayList<>();
			int counter = 100;
			for (Activity a : acts) {
				EPurpose purpose = a.getPurpose();
				int loc;
				switch (purpose){
                    case HOME:
                    case OFFICE:
                    case SCHOOL:
                        loc = purpose.getId();
                        break;
                    default:
                        loc = counter++;
                        break;
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
		if (values.isEmpty()) return 0;

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
        default:
            return mesh.getEconomics(0);
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

    protected List<Facility> getFacilities(ETransition transition, GMesh mesh) {
        switch (transition) {
            case HOSPITAL:
                return mesh.getHospitals();
            case SHOPPING:
                return mesh.getRetails();
            case EATING:
                return mesh.getRestaurants();
            default:
                return mesh.getFacilities();
        }
    }

    /**
     * Choose a destination based solely on facility capacity, without considering distance.
     * This is typically used for leisure or general-purpose activity destination selection.
     */
	protected GLonLat choiceByFacilityCapacity(City city, ETransition transition, EGender gender) {
		List<GMesh> meshes = city.getMeshes();
		GMesh mesh = null;
		{
			List<Double> capacities = getMeshCapacity(transition, meshes, gender);
			int choice = Roulette.choice(capacities, getRandom());
			mesh = meshes.get(choice);
		}
		// Search a poi
		if (mesh != null) {
			List<Facility> facilities = getFacilities(transition, mesh);
			List<Double> capacities = new ArrayList<>();
			if (!facilities.isEmpty()) {
				for (Facility f : facilities) {
					capacities.add(f.getCapacity());
				}
				int choice = Roulette.choice(capacities, getRandom());
				Facility fac = facilities.get(choice);
				return new GLonLat(fac, city.getId());
			}
		}
		return null;
	}

	// Only for workplace choice, need to improve
	protected GLonLat choiceByDistanceWeightedCapacity(GLonLat origin, City city, ETransition transition, EGender gender) {
		List<GMesh> meshes = city.getMeshes();
		GMesh mesh = null;
		{
			List<Double> probs = new ArrayList<>();
			List<Double> capacities = getMeshCapacity(transition, meshes, gender);
			for (int i = 0; i < meshes.size(); i++) {
				GMesh tmesh = meshes.get(i);
				double capacity = capacities.get(i);
				ILonLat center = tmesh.getCenter();
				double distance = DistanceUtils.distance(
						origin.getLon(), origin.getLat(), center.getLon(), center.getLat());
				probs.add(capacity/Math.pow(distance, 2));
			}
			int choice = Roulette.choice(probs, getRandom());
			mesh = meshes.get(choice);
		}
		// Search a poi
		if (mesh != null) {
			List<Facility> facilities = getFacilities(transition, mesh);
			List<Double> capcities = new ArrayList<>();
			if (!facilities.isEmpty()) {
				for (Facility f : facilities) {
					capcities.add(f.getCapacity());
				}
				int choice = Roulette.choice(capcities, getRandom());
				Facility fac = facilities.get(choice);
				return new GLonLat(fac, city.getId());
			}
		}
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
		List<City> cities = japan.searchCities(MAX_SEARCH_DISTANCE, city);
		{
			List<Double> capcities = new ArrayList<>();
			double deno = 0;
			for (City ecity : cities) {
				double dx = ecity.getLon() - city.getLon();
				double dy = ecity.getLat() - city.getLat();
				double distance = Math.sqrt(dx*dx+dy*dy);
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
				return choiceByFacilityCapacity(dcity, transition, gender);
			}else {
				return choiceByDistanceWeightedCapacity(origin, dcity, transition, gender);
			}
		}
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
