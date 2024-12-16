package pseudo.aggr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.ac.ut.csis.pflow.geom2.Mesh;
import jp.ac.ut.csis.pflow.geom2.MeshUtils;

public class MeshVolumeCalculator {

	private static final long TIME_INTERVAL_SECONDS = 360 * 1000;
	
	// 改修中
	private static void calculate(String filename, Map<String, List<Integer>> data) {
		System.out.println(filename);
		long startday = 1443625200000L;
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            int preid = 0, prestep = 0;
            int maxSteps = (int) (3600*24*1000 / TIME_INTERVAL_SECONDS);
            List<Integer> vals = null, prevals = null;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");
            	int id = Integer.valueOf(items[0]);
            	int step = (int)((Long.valueOf(items[1])-startday) / TIME_INTERVAL_SECONDS);
            	double lon = Double.valueOf(items[3]);
            	double lat = Double.valueOf(items[4]);
            	
            	// create a list
            	Mesh mesh = MeshUtils.createMesh(4, lon, lat);
            	String code = mesh.getCode();
            	if (data.containsKey(code)){
            		vals = data.get(code);
            	}else {
            		vals = new ArrayList<>();
            		for (int i = 0; i < maxSteps; i++) {
            			vals.add(0);
            		}
            		data.put(code, vals);
            	}
            
            	// set value to list
            	if (preid != id) {

            		// 0 ~ time
                	for (int i = 0; i <= step && i < maxSteps; i++) {
                		vals.set(i, vals.get(i)+1);
                	}
                	// time ~ max 
                	if (prevals != null) {
                		for (int i = prestep+1; i < maxSteps; i++) {
                			prevals.set(i, prevals.get(i)+1);
	                	}
						// data.put(code, prevals);
                	}

            	}else {
            		for (int i = prestep+1; i <= step && i < maxSteps; i++) {
                		vals.set(i, vals.get(i)+1);
                	}
            	}
           
            	preid = id;
            	prestep = step;
            	prevals = vals;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	
	public static void write(String filename, Map<String, List<Integer>> data) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (Map.Entry<String, List<Integer>> e : data.entrySet()) {
				List<Integer> vols = e.getValue();
				String line = String.format("%s", e.getKey());
				for (int i = 0; i < vols.size(); i++) {
					int vol = vols.get(i);
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
		int start = 22;
		int end = 26;
		for(int i=start; i<=end;i++){

			String input = String.format("/mnt/free/owner/PseudoPFLOW/%02d/", i);
			String output = String.format("/mnt/free/owner/PseudoPFLOW/mesh_pop/%02d_mesh_volume4.csv", i);

			Map<String, List<Integer>> data = new HashMap<>();
			File[] files = (new File(input)).listFiles();
			Arrays.sort(files);

			int count = 0;

			for (File file: files){
				System.out.println(String.format("%d %d", count++, files.length));
				calculate(file.getAbsolutePath(), data);

			}
			write(output, data);
			System.out.println("end");
		}
		// String input = "/mnt/free/owner/PseudoPFLOW/14/";
		// String output = "/mnt/free/owner/PseudoPFLOW/mesh_pop/14_mesh_volume4.csv";

		
//		Map<String, List<Integer>> data = new HashMap<>();
//		File[] files = (new File(input)).listFiles();
//		Arrays.sort(files);
//
//		int count = 0;
//		for (File file : files) {
//			System.out.println(String.format("%d %d", count++, files.length));
//			int pref = Integer.valueOf(file.getName().substring(7, 9));
//			if (pref >= start && pref <= end) {
//				calcurate(file.getAbsolutePath(), data);
//			// if(count==2){break;}
//
//			}
//		}
//		write(output, data);
//		System.out.println("end");
	}
}
