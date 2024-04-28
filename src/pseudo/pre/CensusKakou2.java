package pseudo.pre;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CensusKakou2{

	private final static String REGEX = ",";
	
	
	public class Roudou{
		public String code;
		public String name;
		public int gender;
		public int[] job;
		public int[] nojob;
		public int[] hijob;
	}
	
	private int[] process12(String[] tokens) {
		int[] res = new int[15];
		for (int i = 0; i < 15; i++) {
			res[i] = Integer.valueOf(tokens[10+i]);
		}
		return res;
	}
	
	private String cleaning(String record) {
		return record.replace("-","0").replaceAll("\"", "");
	}
	
	private List<Roudou> process11(BufferedReader br) throws IOException {
		List<Roudou> res = new ArrayList<>();
		String record = null;
		int[] val1 = null, val2 = null, val3 = null;
		while ((record = br.readLine()) != null) {
			record = record.replace("-","0").replaceAll("\"", "");
			String [] tokens = record.split(REGEX);
			
			String[] head = tokens[8].split(" ");
			String code = head[0];
			String name = head[1];
			
			for (int i = 1; i <= 6; i++) {
				br.readLine();
			}
			// 
			for (int gender = 1; gender <= 2; gender++) {
				record = br.readLine();
				record = br.readLine();
				val1 = process12(cleaning(br.readLine()).split(REGEX));
				val2 = process12(cleaning(br.readLine()).split(REGEX));
				val3 = process12(cleaning(br.readLine()).split(REGEX));
				br.readLine();
				
				Roudou roudou = new Roudou();
				roudou.code = code;
				roudou.name = name;
				roudou.gender = gender;
				roudou.job = val1;
				roudou.nojob = val2;
				roudou.hijob = val3;
				res.add(roudou);
			}
		}
		return res;
	}
	
	private void process1() {
		String root = "C:/Users/kashiyama/Desktop/stat/";
		String dir =  String.format("%sstatdata/国勢調査27ー労働/", root); 
		String outfile =  String.format("%spre_labor_rate.csv", root); 
		
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outfile))){
			// 
			bw.write(String.format("code,name,gender,age,job,nojob,hijob\n")); 
			for (File file : new File(dir).listFiles()) {
				try(
						InputStreamReader is = new InputStreamReader(new FileInputStream(file),"SHIFT_JIS");	
						BufferedReader br = new BufferedReader(is);){
					for (int i = 1; i <= 10; i++) {
						br.readLine();
					}
					List<Roudou> cities = process11(br);
					for (Roudou c : cities) {
						for (int i = 0; i < c.job.length; i++) {
							bw.write(String.format("%s,%s,%d,%d,%d,%d,%d\n", 
									c.code,c.name,c.gender,15+i*5, c.job[i],c.nojob[i],c.hijob[i]));
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}	
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		System.out.println("start");
		(new CensusKakou2()).process1();
		System.out.println("end");
	}
	
}
