package pseudo.aggr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Properties;
import util.PathResolver;

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
	
	public static void main(String[] args) throws Exception {
		// Load configuration from config.properties
		InputStream inputStream = Sampling.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		Properties prop = new Properties();
		prop.load(inputStream);

		String input = PathResolver.resolve(prop.getProperty("legacy.input.dir"));
		String output = PathResolver.resolve(prop.getProperty("legacy.sampling.output"));
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
