package sim.sim3.ctrl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import sim.sim3.res.Agent;
import sim.sim3.Configuration;

/**
 * Centroid Logic
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class CentroidLogic {
	/** Traffic Controller	**/	private TrafficController parent;
	/** Thread Count		**/	private int numThreads;
	/** AgentMap			**/	private TreeMap<Long, List<Agent>> mapAgents;

	/**
	 * Initialization
	 * @param parent Traffic Controller
	 */
	public CentroidLogic(TrafficController parent){
		this.numThreads = Configuration.NumThreads;
		this.parent = parent;
	}
	
	/**
	 * Task
	 * @author SekimotoLab@IIS. UT.
	 * @since 2014/07/31
	 */
	private class CentroidTask implements Callable<List<TripInfo>>{
		/** List of Agents 	**/	private List<Agent> listAgents;

		/**
		 * Initialization
		 * @param mListAgents List of Agents
		 */
		public CentroidTask(List<Agent> listAgents){
			this.listAgents = listAgents;
		}		

		@Override
		public List<TripInfo> call() throws Exception {
			List<TripInfo> ret = new ArrayList<TripInfo>();
			for (Agent agent : listAgents){
				TripInfo info = parent.nextTrip(agent);
				if (info != null){
					ret.add(info);
				}
			}
			return ret;
		}
	}
	
	/**
	 * Process
	 * @param listAgents List of Agents
	 */
	public void process(List<Agent> listAgents){
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		List<Future<List<TripInfo>>> features = new ArrayList<Future<List<TripInfo>>>();
		int listSize = listAgents.size();
		int taskNum = numThreads * 10;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
			List<Agent> subList = listAgents.subList(i, end);
			features.add(es.submit(new CentroidTask(subList)));
		}
		es.shutdown();	
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		for (Future<List<TripInfo>> feature : features){
			try {
				List<TripInfo> listTrips = feature.get();
				if (listTrips != null){
					parent.nextTrip(listTrips);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}			
		}
	}
	
	/**
	 * Preparing of process
	 * @param listAgents List of Agents
	 */
	public void initialize(List<Agent> listAgents){
		mapAgents = new TreeMap<Long, List<Agent>>();
		process(listAgents);
	}
	
	/**
	 * Go to next step
	 * @param time Current time
	 */
	public void next(long time){
		List<Agent> listAgents = new ArrayList<Agent>();
		for (Iterator<Entry<Long, List<Agent>>> iter = mapAgents.entrySet().iterator(); iter.hasNext();) {
		    Map.Entry<Long, List<Agent>> entry = iter.next();
		    long keyTime = entry.getKey();
		    if (keyTime <= time){
		    	iter.remove();
			    for (Agent agent : entry.getValue()){
			    	agent.removeTrip();
			    	listAgents.add(agent);
			    }
		    }
		}	
		process(listAgents);
	}
	
	/**
	 * Insert agent to Centroid
	 * @param time Current time
	 * @param agent Agent
	 */
	public void insert(long time, Agent agent){
		List<Agent> listAgent = mapAgents.get(time);
		if (listAgent == null){
			listAgent = new ArrayList<Agent>();
			mapAgents.put(time, listAgent);
		}
		listAgent.add(agent);
	}
	
	/**
	 * Remove agent
	 */
	public void clear(long time, Set<Agent> setAgents){
		for (Map.Entry<Long, List<Agent>> entry : mapAgents.entrySet()){
			long depTime = entry.getKey();
			long stayTime = depTime - time;
			
			Iterator<Agent> iter = entry.getValue().iterator();
			while(iter.hasNext()){
				Agent agent = iter.next();
				if (setAgents.contains(agent) && agent.hasTrip()){
					agent.getTrip().setStayTime(stayTime);
					iter.remove();
				}
			}
		}
	}
}
