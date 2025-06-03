package pseudo.aggr;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import pseudo.gen.Commuter;
import pseudo.res.ETransport;

public class LinkVolumeCalculator {

	private static final long TIME_INTERVAL_SECONDS = 3600 *1000;

	private static void calcurate(String filename, Map<String, Map<Long,Integer>> data) {
		System.out.println(filename);
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",", -1);
            	long time = Long.valueOf(items[1]) / TIME_INTERVAL_SECONDS;
            	ETransport transport = ETransport.getType(Integer.valueOf(items[5]));
            	String link = String.valueOf(items[8]);
            	if (link.equals("") !=true && transport != ETransport.TRAIN) {
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
	
	public static void main(String[] args) throws IOException {
        String dir;
        InputStream inputStream = Commuter.class.getClassLoader().getResourceAsStream("config.properties");
        if (inputStream == null) {
            throw new FileNotFoundException("config.properties file not found in the classpath");
        }
        Properties prop = new Properties();
        prop.load(inputStream);

        dir = prop.getProperty("root");
        String inputDir = String.format("%s/trajectory/", dir);
        String outputDir = String.format("%s/link_volume/", dir);
        int start = 22;
        int end = 22;

		//String input = "/home/ubuntu/Data/pseudo/trajectory/city/"; //args[0];
		//String output = "/home/ubuntu/Data/pseudo/link_volume.csv";//args[1];

        for (int i = start; i <= end; i++) {
            // create directory
            File prefDir = new File(outputDir, String.valueOf(i));
            System.out.println("Start prefecture:" + i + prefDir.mkdirs());
            File[] files = (new File(inputDir, String.valueOf(i))).listFiles();

            Map<String, Map<Long,Integer>> data = new HashMap<>();
            int count = 0;
            assert files != null;
            for (File file : files){
                System.out.printf("%d %d%n", count++, files.length);
                calcurate(file.getAbsolutePath(), data);
            }
            write(String.format("%s/link_volume.csv", prefDir.getAbsolutePath()), data);
        }
		

		System.out.println("end");
	}
}
