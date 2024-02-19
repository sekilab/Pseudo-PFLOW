package pseudo.aggr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class Sampling {

	private static final int step = 100;
	
	public static void write(String filename, BufferedWriter bw) {
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            int pre = 0;
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	int id = Integer.valueOf(items[0]);
            	if (id != pre) {
            		bw.write(line);
                	bw.newLine();
            	}
            	pre = id;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void write2(String filename, BufferedWriter bw) {
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            int pre = 0;
            long targetTime = 3600*12;
            double lon = 0f, lat = 0f;
            while ((line = br.readLine()) != null) {
            	String[] items = line.split(",");
            	int id = Integer.valueOf(items[0]);
            	long time = Long.valueOf(items[1]);
            	if (id != pre) {
            		if (pre > 0) {
            			bw.write(String.format("%d,%f,%f", id, lat, lon));
                    	bw.newLine();
            		}
        			lon = Double.valueOf(items[2]);
        			lat = Double.valueOf(items[3]);
            	}else {
            		if (time <= targetTime) {
            			lon = Double.valueOf(items[2]);
            			lat = Double.valueOf(items[3]);
            		}
            	}
            	pre = id;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		String input = "C:/Users/kashiyama/Desktop/input/";
		String output = "C:/Users/kashiyama/Desktop/trj12.csv";
		File[] files = (new File(input)).listFiles();
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(output));){
			for (File file : files) {
				write2(file.getAbsolutePath(), bw);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("end");
	}
}
