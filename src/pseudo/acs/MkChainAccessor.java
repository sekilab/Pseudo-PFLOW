package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.EPurpose;
import pseudo.res.ETransition;

public class MkChainAccessor {

	private List<Map<EPurpose,Map<ETransition, Double>>> data;
	private static final int TIME_INTERVAL = 15 * 60;
	
	public MkChainAccessor(String filename) {
		load(filename);
	}
	
	public ETransition getTransition(int index) {
		return ETransition.values()[index];
	}
		
	public List<Double> getProbs(Map<ETransition, Double> data) {
		List<Double> res = new ArrayList<>();
		for (ETransition e : ETransition.values()) {
			res.add(data.get(e));
		}
		return res;
	}
	
	public List<Double> getProbs(int seconds, EPurpose purpose) {
		int idx = (int) (seconds / TIME_INTERVAL);
		Map<EPurpose,Map<ETransition, Double>> tdata = data.get(idx);
		return getProbs(tdata.get(purpose));
	}
	
	public int getInterval() {
		return TIME_INTERVAL;
	}
	
	private int load(String filename){
		data = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	Map<EPurpose,Map<ETransition, Double>> mapTime = new HashMap<>();
            	for (int i = 1; i < items.length; i++) {
            		String[] es = items[i].split(" ");
            		EPurpose purpose = EPurpose.getType(Integer.valueOf(es[0]));
            		Map<ETransition, Double> mapState = new HashMap<>();
            		for (int j = 1; j < es.length; j+=2) {
            			ETransition transition = ETransition.getType(Integer.valueOf(es[j]));
            			double prob = Double.valueOf(es[j+1]);
            			mapState.put(transition, prob);
            		}
            		mapTime.put(purpose, mapState);
            	}
            	data.add(mapTime);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }		
		return 1;
	}	
}
