package sim.sim3.ctrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jp.ac.ut.csis.pflow.routing4.res.Link;
import sim.sim3.res.Agent;
import sim.sim3.res.SDirection;
import sim.sim3.res.SLink;
import sim.sim3.res.SSNetwork;
import sim.sim3.Configuration;

/**
 * Queuing Logic
 * @author SekimotoLab@IIS. UT.
 * @since 2014/07/31
 */
public class QueuingLogic implements ITrafficLogic{
	/**	TrafficController		**/	private TrafficController parent;
	/**	Thread Count			**/	private int numThreads;
	/**	Queue map				**/	private HashMap<Integer, Queue> mapQueue;
	/**	List of Tasks			**/	private List<Callable<List<TripInfo>>> listTasks;
	/**	Simulation Step(msec)	**/	private long stepTime;
										
	/**
	 * Initialization
	 */
	public QueuingLogic(){
		this.numThreads = Configuration.NumThreads;
	}
	
	/**
	 * Queuing Task
	 * @author SekimotoLab@IIS. UT.
	 * @since 2014/07/31
	 */
	private class QueueTask implements Callable<List<TripInfo>>{
		/**	List of Queues	**/	private List<Queue> mListQueues;
		
		/**
		 * Initialization
		 * @param mListQueues List of Queues
		 */
		public QueueTask(List<Queue> mListQueues){
			this.mListQueues = mListQueues;
		}

		/**
		 * Process
		 * @param queue Queue
		 */
		private List<TripInfo> process(Queue queue){
			List<TripInfo> ret = null;
			
			SDirection direction = queue.getParent();
			SLink link = direction.getParent();
			
			// update lane status
			double flowRate = link.getCapacity();
			if (flowRate <= 0){
				flowRate = Integer.MAX_VALUE;
			}
			queue.updateStatus(stepTime * flowRate);
			
			// dequeue agent
			ret = queue.dequeue(parent);
				
			// update cost
			double passTime = queue.sumStocks() / flowRate + queue.getDrivingTime();
			double cost = (direction.isClosed()) ? 3600 * 1000 * 24 : passTime; 
			if (direction.isReverse()){
				link.setBackwardCost(cost);
			}else{
				link.setForwardCost(cost);	
			}
			return ret;
		}


		@Override
		public List<TripInfo> call() throws Exception {
			List<TripInfo> ret = new ArrayList<TripInfo>();
			for (Queue queue : mListQueues){
				List<TripInfo> result = process(queue);
				if (result != null){
					ret.addAll(result);
				}
			}
			return ret;
		}
	}
	
	@Override
	public void next(long stepTime) {
		this.stepTime = stepTime;
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		try {
			// execute tasks
			List<Future<List<TripInfo>>> features = es.invokeAll(listTasks);
			es.shutdown();
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
		} catch (Exception exp) {
			exp.printStackTrace();
		}
	}

	@Override
	public int insert(Agent agent, int linkid, boolean isReverse) {
		int key = linkid * (isReverse ? -1 : 1);
		Queue queue = mapQueue.get(key);
		long time = parent.getTime() + queue.getDrivingTime();
		queue.enqueue(new Packet(agent, time));		
		return 0;
	}

	@Override
	public void initialize(TrafficController controller, SSNetwork network) {
		//
		parent = controller;
		// 
		mapQueue = new HashMap<Integer, Queue>();
		List<Link> listLinks = network.listLinks();
		for (Link link : listLinks){
			SLink slink = (SLink)link;
			int linkid = Integer.valueOf(link.getLinkID());
			mapQueue.put(linkid, new Queue(slink.getNormal()));
			mapQueue.put(-1*linkid, new Queue(slink.getReverse()));
		}
		// mListTasks
		ArrayList<Queue> listQueues = new ArrayList<Queue>(mapQueue.values());
		Collections.shuffle(listQueues);
		
		listTasks = new ArrayList<Callable<List<TripInfo>>>();
		int listSize = listQueues.size();
		int taskNum = numThreads * 10;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
			listTasks.add(new QueueTask(listQueues.subList(i, end)));
		}		
	}

	@Override
	public void clear(Set<Agent> setAgents) {
		for (Map.Entry<Integer, Queue> entry : mapQueue.entrySet()){
			entry.getValue().clear(setAgents);
		}
	}
}
