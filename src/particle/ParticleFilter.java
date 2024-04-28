package particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ParticleFilter {
	
	private boolean multiProcess;
	
	public ParticleFilter(boolean multiProcess) {
		super();
		this.multiProcess = multiProcess;
	}

	private int choice(List<Double> probability, double random) {
		int index = 0;
		double sum = 0;
		for (int i = 0; i < probability.size(); i++) {
			sum += probability.get(i);
			if (random < sum) {
				return i;
			}
		}
		return index;
	}
	
	private List<Double> normalize(List<Double> values) {
		double sum = 0;
		List<Double> rtn  = new ArrayList<Double>();
		for (Double d : values) {
			sum += d;
		}
		for (Double d : values) {
			rtn.add(d / sum);
		}
		return rtn;
	}
	
	
	
	public class PredictTask implements Callable<Integer>{
		private List<IParticle> listParticles;
		
		public PredictTask(List<IParticle> listParticles) {
			super();
			this.listParticles = listParticles;
		}

		@Override
		public Integer call() throws Exception {
			for (IParticle p : listParticles) {
				p.predict();
			}
			return null;
		}	
	}
	
	public int predict(List<IParticle> listParticles) {
		int numThreads = Runtime.getRuntime().availableProcessors();		
		int listSize = listParticles.size();
		int stepSize = listSize;
		
		if (multiProcess) {
			int taskNum = numThreads * 5;
			stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		}
		
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		List<Future<Integer>> features = new ArrayList<Future<Integer>>();
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
			List<IParticle> subList = listParticles.subList(i, end);
			features.add(es.submit(new PredictTask(subList)));
		}
		es.shutdown();	
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	private int updateWeight(List<IParticle> listParticles) {
		for (IParticle p : listParticles) {
			p.updateWeight();
		}
		return 0;
	}
	
	public List<Double> getNormalizeWeigth(List<IParticle> listParticles){
		// collect weights
		List<Double> listWeights = new ArrayList<Double>();
		for (IParticle p : listParticles) {
			listWeights.add(p.getWeight());
		}
		return normalize(listWeights);
	}
	
	private List<IParticle> resampling(List<IParticle> listParticles) {
		Random random = new Random(100);
		List<Double> listWeights = getNormalizeWeigth(listParticles);
		for (IParticle p : listParticles) {
			int index = choice(listWeights, random.nextDouble());
			IParticle tp = listParticles.get(index);
			p.resample(tp);
		}
		return listParticles;
	}
	
	public int execute(List<IParticle> listParticles, int numSteps) {
		for (int i = 0; i < numSteps; i++) {
			predict(listParticles);
			updateWeight(listParticles);
			listParticles = resampling(listParticles);
						
			// log
			double maxWeight = Double.MIN_VALUE;
			IParticle maxParticle = null;
			for (IParticle p : listParticles) {
				if (p.getWeight() > maxWeight) {
					maxParticle = p;
					maxWeight = p.getWeight();
				}
			}	
			List<Double> params = maxParticle.getParameters();
			System.out.println(String.format("%d ------------------------", i));
			for (Double p : params) {
				System.out.println(p);
			}
		}
		return 0;
	}
	
	
}
