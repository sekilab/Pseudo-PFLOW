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
	private static void calcurate(String filename, Map<String, List<Integer>> data) {
		System.out.println(filename);
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            int preid = 0, prestep = 0;
            int maxSteps = (int) (3600*24*1000 / TIME_INTERVAL_SECONDS);
            List<Integer> vals = null, prevals = null;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");
            	int id = Integer.valueOf(items[0]);
            	int step = (int)(Long.valueOf(items[1]) / TIME_INTERVAL_SECONDS);
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
                		for (int i = prestep; i < maxSteps; i++) {
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
		String input = "/home/ubuntu/Data/pseudo/trajectory/city/";
		String output = "/home/ubuntu/Data/pseudo/mesh_volume.csv";
		int start = 1;
		int end = 47;
		
		Map<String, List<Integer>> data = new HashMap<>();
		File[] files = (new File(input)).listFiles();
		Arrays.sort(files);
		
		int count = 0;
		for (File file : files) {
			System.out.println(String.format("%d %d", count++, files.length));
			int pref = Integer.valueOf(file.getName().substring(7, 9));
			if (pref >= start && pref <= end) {
				calcurate(file.getAbsolutePath(), data);
			}
		}
		write(output, data);
		System.out.println("end");
	}
}
