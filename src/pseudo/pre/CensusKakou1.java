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

public class CensusKakou1{

	private final static String REGEX = ",";
	
	
	public class OD{
		private String code1;
		private String code2;
		private String type1;
		private String type2;
		private boolean home;						//　自宅、自宅外
		private final int[] syogyou = new int[2];	// 通勤男女
		private final int[] gakusei = new int[2];	// 通学男女
		
		public OD(String code1, String type1, String code2, String type2, boolean home) {
			super();
			this.code1 = code1;
			this.code2 = code2;
			this.type1 = type1;
			this.type2 = type2;
			this.home = home;
		}
	}
	
	private OD process12(String[] tokens, boolean home) {
		OD res = new OD(tokens[2],tokens[3], tokens[4],tokens[5], home);
		res.syogyou[0] = Integer.valueOf(tokens[14]);	// 男通勤
		res.gakusei[0] = Integer.valueOf(tokens[15]);	// 男学生
		res.syogyou[1] = Integer.valueOf(tokens[18]);	//　女通勤
		res.gakusei[1] = Integer.valueOf(tokens[19]);	//　女通学
		return res;
	}
	
	
	private List<OD> process11(BufferedReader br) throws IOException {
		List<OD> res = new ArrayList<>();
		String record = null;
		OD od = null;
		while ((record = br.readLine()) != null) {
			record = record.replace("-","0").replaceAll("\"", "");
			String [] tokens = record.split(REGEX);
			
			if (tokens[8].contains("自宅外")) {
				od = process12(tokens, false);
				String type = od.type1;
				if (type.equals("0") || type.equals("2") || type.equals("3")) {
					od.code2 = od.code1;
					od.type2 = od.type1;
					res.add(od);
				}
			}else if (tokens[8].contains("自宅")) {
				od = process12(tokens, true);
				String type = od.type1;
				if (type.equals("0") || type.equals("2") || type.equals("3")) {
					od.code2 = od.code1;
					od.type2 = od.type1;
					res.add(od);
				}
			}else if (tokens[3].length() > 0 && tokens[5].length() > 0){
				od = process12(tokens, false);
				String type1 = od.type1;
				String type2 = od.type2;
				if ((type1.equals("0") || type1.equals("2") || type1.equals("3")) 
						&& (type2.equals("0") || type2.equals("2") || type2.equals("3"))) {
					res.add(od);
				}
			}
		}
		return res;
	}
	
	private void process1() {
		String root = "C:/Users/kashiyama/Desktop/stat/";
		String dir =  String.format("%sstatdata/国勢調査27ー通勤/", root); 
		String outfile =  String.format("%scity_census_od.csv", root); 
		
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outfile))){
			bw.write(String.format("city1,city2,isCommuter,isMale,isHome,volume\n")); 
			
			for (File file : new File(dir).listFiles()) {
				InputStreamReader is = new InputStreamReader(new FileInputStream(file),"SHIFT_JIS");				
				try(BufferedReader br = new BufferedReader(is);){
					for (int i = 1; i <= 10; i++) {
						br.readLine();
					}
					List<OD> ods = process11(br);
					for (OD od : ods) {
						for (int i= 0; i < 2; i++) {
							if (od.syogyou[i] > 0) {
								bw.write(String.format("%s,%s,%b,%b,%b,%d\n", 
										od.code1, od.code2, true, i==0?true:false, od.home, od.syogyou[i]));
							}
							if (od.gakusei[i] > 0) {
								bw.write(String.format("%s,%s,%b,%b,%b,%d\n", 
										od.code1, od.code2, false, i==0?true:false, od.home,od.gakusei[i]));
							}
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
		(new CensusKakou1()).process1();
		System.out.println("end");
	}
	
}
