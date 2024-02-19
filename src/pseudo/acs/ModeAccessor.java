package pseudo.acs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.EPTCity;
import pseudo.res.EGender;
import pseudo.res.EPurpose;
import pseudo.res.ETransport;

public class ModeAccessor {
	
	private Map<EPTCity, Map<EGender, Map<EPurpose, Map<Integer, Map<Integer,List<Double>>>>>> data;
	
	
	public ETransport getCode(int index) {
		switch (index) {
		case 0:return ETransport.WALK;
		case 1:return ETransport.BICYCLE;
		case 2:return ETransport.CAR;
		case 3:return ETransport.TRAIN;
		default:return ETransport.CAR;
		}
	}
	
	
	
	public ModeAccessor(String filename) {
		super();
		this.load(filename);
	}

	public int size() {
		return data.size();
	}
	
	public List<Double> get(EPTCity type, EGender gender, EPurpose purpose, int age, double distance){
		int ageCode = 0;
		if (age <= 14) {
			ageCode = 1;
		}else if (age <= 64) {
			ageCode = 2;
		}else if (age <= 74) {
			ageCode = 3;
		}else if (age <= 100) {
			ageCode = 4;
		}
		
		int distanceCode = 0;
		if (distance <= 200) {
			distanceCode = 2;
		}else if (distance <= 500) {
			distanceCode = 3;
		}else if (distance <= 1000) {
			distanceCode = 4;
		}else if (distance <= 1500) {
			distanceCode = 5;
		}else if (distance <= 2000) {
			distanceCode = 6;		
		}else if (distance <= 3000) {
			distanceCode = 7;
		}else if (distance <= 4000) {
			distanceCode = 8;
		}else if (distance <= 5000) {
			distanceCode = 9;
		}else if (distance <= 10000) {
			distanceCode = 10;
		}else if (distance <= 15000) {
			distanceCode = 11;
		}else if (distance <= 20000) {
			distanceCode = 12;
		}else {
			distanceCode = 13;
		}
		List<Double> res = null;
		try {
			res = data.get(type).get(gender).get(purpose).get(ageCode).get(distanceCode);
		}catch(Exception e) {
			e.printStackTrace();
		}
		return res;
	}
	
	private int load(String filename) {
		data = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            br.readLine();
            
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	EPTCity type = EPTCity.getType(Integer.valueOf(items[1]));
            	EGender gender = EGender.getType(Integer.valueOf(items[2]));
            	int age = Integer.valueOf(items[3]);
               	int distance = Integer.valueOf(items[4]); 	
            	List<EPurpose> purposes = new ArrayList<>();
            	switch (Integer.valueOf(items[5])) {
            	case 1: purposes.add(EPurpose.OFFICE);break;
            	case 2: purposes.add(EPurpose.SCHOOL);break;
            	case 5: 
            		purposes.add(EPurpose.SHOPPING);
            		purposes.add(EPurpose.EATING);
            		purposes.add(EPurpose.HOSPITAL);
            		purposes.add(EPurpose.FREE);
            		purposes.add(EPurpose.BUSINESS);break;
            	}
            	
            	List<Double> probs = new ArrayList<>();
            	probs.add(Double.valueOf(items[19]));
            	probs.add(Double.valueOf(items[18]));
            	probs.add(Double.valueOf(items[13]));
            	probs.add(Double.valueOf(items[12]));
            	
            	
            	Map<EGender, Map<EPurpose, Map<Integer, Map<Integer,List<Double>>>>> l1 = data.containsKey(type) ? data.get(type) : new HashMap<>();
            	data.put(type, l1);
            	
            	Map<EPurpose, Map<Integer, Map<Integer,List<Double>>>> l2 = l1.containsKey(gender) ? l1.get(gender) : new HashMap<>();
            	l1.put(gender, l2);
            	
            	for (EPurpose e : purposes) {
            		Map<Integer, Map<Integer,List<Double>>> l3 = l2.containsKey(e) ? l2.get(e) : new HashMap<>();
            		l2.put(e, l3);
            		
                	Map<Integer,List<Double>> l4 = l3.containsKey(age) ? l3.get(age) : new HashMap<>();
                	l3.put(age, l4);
                	
                	l4.put(distance, probs);
            	}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		return 1;
	}
}
