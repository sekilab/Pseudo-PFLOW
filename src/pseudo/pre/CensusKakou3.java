package pseudo.pre;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;

public class CensusKakou3{

	private static void process(File in, File out) {	
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(out));
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(in),"SHIFT_JIS"));){
			
            String line;
            br.readLine();
			br.readLine();
            while ((line = br.readLine()) != null) {
            	line = line.replace("*", "0");
            	bw.write(line);
            	bw.newLine();
            }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		System.out.println("start");
		File[] files = new File("C:/Users/kashiyama/Desktop/stat/statdata/500/").listFiles();
		int count=0;
		for (File file : files) {
			File outFile = new File(file.getParent(), String.format("aa%02d.csv", count++));
			process(file, outFile);
			System.out.println(String.format("copy stat.s_mesh_population from '%s' WITH CSV;", outFile.getAbsoluteFile()));
		}
		System.out.println("end");
	}
	
}
