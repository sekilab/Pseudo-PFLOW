package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

import pseudo.res.EGender;
import pseudo.res.ELabor;

public class EducationAccessor {
	
	private Map<Integer, Map<EGender, Double[]>> data;
	
	public EducationAccessor(String filename) {
		this.load(filename);
	}
	
	public int size() {
		return data.size();
	}
	
	public Map<EGender, Double[]> get(int preCode){
		if (data.containsKey(preCode)) {
			return data.get(preCode);
		}
		return null;
	}
	
	public ELabor getEducationCode(int index, int age) {
		if (age < 20) {
			switch (index) {
			case 1: return ELabor.COLLEGE;
			case 2: return ELabor.JUNIOR_COLLEGE;
			default:return ELabor.UNDEFINED;
			}
		}else {
			switch (index) {
			case 1: return ELabor.COLLEGE;
			default:return ELabor.UNDEFINED;
			}
		}
	}
	
	private int load(String filename){
		this.data = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	int pref = Integer.valueOf(items[0]);
            	double male4 = Double.valueOf(items[2]);
            	double female4 = Double.valueOf(items[3]);
            	double male2 = Double.valueOf(items[4]);
            	double female2 = Double.valueOf(items[5]);
            	
            	Double[] male = {1-(male4+male2), male4, male2};
            	Double[] female = {1-(female4+female2), female4, female2};
            	
            	Map<EGender, Double[]> sub = new HashMap<>();
            	sub.put(EGender.MALE, male);
            	sub.put(EGender.FEMALE, female);
            	data.put(pref, sub);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }		
		return 1;
	}
}
