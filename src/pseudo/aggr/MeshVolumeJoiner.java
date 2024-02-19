package pseudo.aggr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshVolumeJoiner {

	private static void calcurate(String filename, Map<String, List<Integer>> data) {
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");
            	String mesh = items[0];
            	List<Integer> vals = null;
            	if (data.containsKey(mesh)) {
            		vals = data.get(mesh);
            		for (int i = 1; i < items.length; i++) {
            			int pre = vals.get(i-1);
            			int next = Integer.valueOf(items[i]);
            			if (next > pre) {
            				vals.set(i-1, next);
            			}
            		}
            	}else {
            		vals = new ArrayList<>();
            		for (int i = 1; i < items.length; i++) {
            			vals.add(Integer.valueOf(items[i]));
            		}
                	data.put(mesh, vals);
            	}
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
					line = String.format("%s,%d", line, vols.get(i));
				}
				bw.write(line);
				bw.newLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
//		String input = args[0];
//		String output = args[1];
		
		
		String input = "/home/ubuntu/Data/pseudo/trajectory/16/";
		String output = "/home/ubuntu/Data/pseudo/16_mesh_volume.csv";
		
		Map<String, List<Integer>> data = new HashMap<>();
		File[] files = (new File(input)).listFiles();
		int count = 0;
		for (File file : files) {
			System.out.println(String.format("%d %d", count++, files.length));
			calcurate(file.getAbsolutePath(), data);
		}
		write(output, data);
		System.out.println("end");
	}
}
