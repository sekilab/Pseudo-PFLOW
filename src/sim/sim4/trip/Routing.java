package sim.sim4.trip;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jp.ac.ut.csis.pflow.routing4.res.Network;
import sim.sim4.res.Agent;

public class Routing {

	public static <T extends Agent> int route(Network network, List<T> listAgents, long time) {
		int numThreads = Runtime.getRuntime().availableProcessors();
		
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		List<Future<Integer>> features = new ArrayList<Future<Integer>>();
		
		int listSize = listAgents.size();
		int taskNum = numThreads * 10;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
			List<T> subList = listAgents.subList(i, end);
			features.add(es.submit(new RoutingTask<T>(subList, network, time)));
		}
		es.shutdown();	
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return 0;
	}
}
