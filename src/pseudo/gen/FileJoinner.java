package pseudo.gen;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import util.PathResolver;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileJoinner {
	
	private static void process(String in, String out) {
		// create trips
		File[] files = (new File(in)).listFiles();
		Map<String, List<File>> map = new TreeMap<>();
		for (File file : files) {
			String gcode = file.getName().substring(7, 12);
			List<File> list = map.containsKey(gcode) ? map.get(gcode) : new ArrayList<>();
			list.add(file);
			map.put(gcode, list);
		}
		
		System.out.println(map.size());
		
		int count = 0;
		byte[] buf = new byte[1024];
		int len;
		for (Map.Entry<String, List<File>> e : map.entrySet()) {
			System.out.println(count++ + " " + e.getKey());
			String zipfile = String.format("%sperson_%s.zip", out, e.getKey());
			String fileName = String.format("person_%s.csv", e.getKey());	
			try(ZipOutputStream zostr = new ZipOutputStream(new FileOutputStream(zipfile));){
				zostr.putNextEntry(new ZipEntry(fileName));
				for(File file : e.getValue()){
					try(BufferedInputStream bistr = new BufferedInputStream(new FileInputStream(file));){
			            while ((len = bistr.read(buf, 0, buf.length)) != -1) {
			                zostr.write(buf, 0, len);
			            }
					}catch(Exception ex) {
						ex.printStackTrace();
					}
				}
			}catch(Exception ex) {
                ex.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		// Load configuration from config.properties
		InputStream inputStream = FileJoinner.class.getClassLoader().getResourceAsStream("config.properties");
		if (inputStream == null) {
			throw new FileNotFoundException("config.properties file not found in the classpath");
		}
		Properties prop = new Properties();
		prop.load(inputStream);

		//process(args[0],args[1]);
		String a = PathResolver.resolve(prop.getProperty("legacy.person.trip.dir"));
		String b = PathResolver.resolve(prop.getProperty("legacy.input.dir"));
		process(a, b);
		System.out.println("end");
	}	
}
