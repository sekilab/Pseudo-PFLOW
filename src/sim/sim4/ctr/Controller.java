package sim.sim4.ctr;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.ac.ut.csis.pflow.geom2.ILonLatTime;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import sim.sim4.filter.IFilter;
import sim.sim4.res.Agent;

public class Controller<T extends Agent>{
	private Network network;
	private List<T> listAgents;
	private List<IFilter<T>> listFilters;
	private List<AgentTask> listTasks;
	private int numThreads;
	private boolean multiprocess;
	
	public Controller(Network network, List<T> listAgents, boolean multiprocess) {
		super();
		this.network = network;
		this.listAgents = listAgents;
		this.listFilters = new ArrayList<IFilter<T>>();
		this.numThreads = Runtime.getRuntime().availableProcessors();
		this.multiprocess = multiprocess;
	}

	public class AgentTask implements Callable<Integer> {
		private List<T> listAgents;
		private long msTime;

		public AgentTask(List<T> listAgents){
			this.listAgents = listAgents;
		}	
		
		private void updateState(T agent) {
			List<ILonLatTime> trajectory = agent.getTrajectory();
			int size = trajectory.size();
			
			int index = 0;
			for (int i = 0; i < size; i++) {
				ILonLatTime llt = trajectory.get(i);
				index = i;
				if (llt.getTimeStamp().getTime() > msTime) {
					break;
				}
			}
			agent.setTrajectory(trajectory.subList(index, size));
			
		}

		@Override
		public Integer call() throws Exception {
			for (T agent : listAgents) {
				updateState(agent);		
			}
			return 0;
		}
		
		public void updateTime(long second) {
			this.msTime = second;
		}
	}
	
	public void initialize() {
		this.listTasks = new ArrayList<>();
		int listSize = this.listAgents.size();
		int stepSize = listSize;
		if (this.multiprocess) {
			int taskNum = numThreads * 10;
			stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		}
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
			List<T> subList = listAgents.subList(i, end);
			listTasks.add(new AgentTask(subList));
		}
	}

	public int next(long time) {	
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		try {
			for (AgentTask task : listTasks) {
				task.updateTime(time);
			}
			es.invokeAll(listTasks);
			es.shutdown();
		} catch (Exception exp) {
			exp.printStackTrace();
		}
		// execute filters
		for (IFilter<T> filter : listFilters) {
			filter.run(time, this.network, (this.listAgents));
		}	
		return 0;
	}
	
	public void add(IFilter<T> filter) {
		this.listFilters.add(filter);
	}
	
	public List<IFilter<T>> getListFilters(){
		return this.listFilters;
	}
	
	public List<T> getListAgents(){
		return this.listAgents;
	}
	
	public Network getNetwork() {
		return network;
	}
}
