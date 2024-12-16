package pseudo.aggr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import pseudo.res.ETransport;

public class LinkVolumeCalculator {

	private static final long TIME_INTERVAL_SECONDS = 3600 *1000;

	private static void calculate(String filename, Map<String, Map<Long,Integer>> data) {
		System.out.println(filename);
		long startday = 1443625200000L;
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",", -1);
            	long time = (Long.valueOf(items[1]) - startday) / TIME_INTERVAL_SECONDS;
            	ETransport transport = ETransport.getType(Integer.valueOf(items[5]));
            	String link = String.valueOf(items[8]);
            	if (link.equals("") !=true && transport != ETransport.TRAIN && transport != ETransport.WALK) {
            		Map<Long,Integer> vols = data.containsKey(link) ? data.get(link) : new HashMap<>();
            		int volume = vols.containsKey(time) ? vols.get(time) : 0;
            		vols.put(time, volume+1);
            		data.put(link, vols);
            	}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

	public static void write(String filename, Map<String, Map<Long,Integer>> data) {
		long numSteps = (3600*24*1000)/ TIME_INTERVAL_SECONDS;
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (Map.Entry<String, Map<Long,Integer>> e : data.entrySet()) {
				Map<Long,Integer> vols = e.getValue();
				String line = String.format("%s", e.getKey());
				for (long i = 0; i < numSteps; i++) {
					int vol = vols.containsKey(i) ? vols.get(i) : 0;
					line = String.format("%s,%d", line, vol);
				}
				bw.write(line);
				bw.newLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		String output = "/mnt/free/owner/link_volume_22_multiple_pref_all_mode.csv";//args[1];
		int start = 13;//Integer.valueOf(args[2]);
		int end = 23;//Integer.valueOf(args[3]);

		Map<String, Map<Long,Integer>> data = new HashMap<>();
		for(int i=start; i<=end;i++){

			String input = String.format("/mnt/large/data/PseudoPFLOW/ver2.0/trajectory/%02d/", i);
//			Map<String, Map<Long,Integer>> data = new HashMap<>();

			File[] files = (new File(input)).listFiles();
			int count = 0;
			for (File file : files){
				System.out.println(String.format("%d %d", count++, files.length));
				calculate(file.getAbsolutePath(), data);
			}
			// write(output, data);
		}
		write(output, data);
		System.out.println("end");
	}
}
