package pt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.TreeMap;


public class OutingAnalyzer {
	private static Map<Integer, Integer> mapHour = new TreeMap<>();
	
	private static int calculate(String filename) {
		mapHour.clear(); 
		for (int i = 0; i < 24; i++) {
			mapHour.put(i, 0);
		}
		try(BufferedReader br = new BufferedReader(new FileReader(filename));) {
			String record = null;
			int prePid = 0;
			int preTripno = 0;
			int hour = 0;
			while ((record = br.readLine()) != null) {
				String[] items = record.split(",");
				int pid = Integer.valueOf(items[0]);
				int tripno = Integer.valueOf(items[1]);
				int hour1 = Integer.valueOf(items[2]);
				int min1 = Integer.valueOf(items[3]);
				int hour2 = Integer.valueOf(items[4]);
				int purpose = Integer.valueOf(items[5]);
				int magfac = Integer.valueOf(items[6]);
				
				if (pid != prePid) {
					hour = -1;
					preTripno = -1;
				}
				if (purpose != -1 && purpose != 3 && (tripno-preTripno)>1) {
					hour1 = min1 != 0 ? hour1+1 : hour1;
					for (int i = hour1; i <= hour2; i++) {
						if (hour < i) {
							int total = mapHour.containsKey(i) ? mapHour.get(i) : 0;
							mapHour.put(i, total+magfac);
						}
					}
					hour = hour2;
				}
				prePid = pid;
				preTripno = tripno;
			}
        } catch (Exception e) {
        	e.printStackTrace();
        }	
		return 0;
	}

	public static void main(String[] args) {
	
		// pid, tripno, purpose, magfac
		String inputDir = "C:/Users/kashiyama/Desktop/input/";
		String outputDir = "C:/Users/kashiyama/Desktop/output/";
		for (File file : new File(inputDir).listFiles()) {
			String name = file.getName().replaceAll(".csv", "");
			File out = new File(outputDir,String.format("%s_outing.csv", name));
			try(BufferedWriter bw = new BufferedWriter(new FileWriter(out));){
				 calculate(file.getAbsolutePath());
				 
				 bw.write(String.format("hour,volume\n"));
				 for (Map.Entry<Integer, Integer> e : mapHour.entrySet()) {
					 bw.write(String.format("%d,%d\n", e.getKey(), e.getValue()));
				 }
				 
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("end");
	}
}
