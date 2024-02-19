package pt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MotifAnalyzer {


	public static List<Integer> compress(List<Integer> source){
		List<Integer> history = new ArrayList<>();
		Integer pre = -1;
		for (Integer e : source) {
			if (pre != e) {
				history.add(e);
				pre = e;
			}
		}
		
		List<Integer> idhistory = new ArrayList<>();
		for (Integer e : history) {
			for (int i = 0; i < history.size(); i++) {
				if (e ==history.get(i)) {
					idhistory.add(i+1);
					break;
				}
			}
		}
		return idhistory;
	}
	
	public static int getType(int id , List<Integer> locs) {
		int res = -1;
		int len = locs.size();
		try {
		if (locs.get(0) == locs.get(len-1)) {
			if (len==1) {
				res = 1;//1
			}else if (len==3) {
				res = 2;//1-2-1
			}else if (len==4) {
				res = 4;//1-2-3-1
			}else if (len==5) {
				if (locs.get(2) == locs.get(0)) {
					// 1-2-1-3-1
					return 3;
				}else if (locs.get(1) == locs.get(3)) {
					// 1-2-3-2-1
					return 5;
				}else {
					// 1-2-3-4-1
					return 7;
				}
			}else if (len==6) {
				if (locs.get(4)==5) {
					//1-2-3-4-5-1
					return 11;
				}else {
					//1-2-3-1-4-1, 1-2-1-3-4-1
					return 6;
				}
			}else if (len==7){
				if (locs.contains(6)) {
					// 1-2-3-4-5-6-1
					return 15;
				}else if (locs.get(3)==1) {
					// 1-2-3-1-4-5-1
					return 13;
				}else if (locs.contains(5)) {
					// 1-2-3-4-1-5-1
					return 10;
				}else if (locs.get(2)==1 && locs.get(4)==1) {
					// 1-2-1-3-1-4-1
					return 8;
				}else {
					//1-2-3-4-3-2-1
					return 9;
				}
			}else {
				return 99;
			}
		}
		}catch(Exception e) {
			System.out.println(id);
			e.printStackTrace();
			}
		return res;
	}
	
	private static Map<Integer, Integer> mapMotif = new HashMap<>();
	private static Map<Integer, Integer> mapMagfac = new HashMap<>();
	
	private static int calculate(String filename) {
		mapMotif.clear(); 
		mapMagfac.clear();
		try(BufferedReader br = new BufferedReader(new FileReader(filename));) {
			String record = null;
			int prePid = 0, preMagfac = 0;
			int HOME = 100;
			int OFFICE = 101;
			List<Integer> route = null;
			int loc = 0;
			while ((record = br.readLine()) != null) {
				String[] items = record.split(",");
				int pid = Integer.valueOf(items[0]);
				int tripno = Integer.valueOf(items[1]);
				int purpose = Integer.valueOf(items[2]);
				int magfac = Integer.valueOf(items[3]);
				
				
				if (pid != prePid) {
					if (route != null){
						int motif = getType(pid, compress(route));
						mapMotif.put(prePid, motif);
						mapMagfac.put(prePid, preMagfac);
					}
					route = new ArrayList<>();
				}
		
				// tokyo, higashi
//				if (purpose==1 || purpose == 2) {
//					route.add(OFFICE);
//				}else if (purpose == 3 || purpose == -1 || tripno == 1) {
//					route.add(HOME);
//				}else {
//					route.add(++loc);
//				}
					// oosaka
					if (purpose==1 || purpose == 2 || purpose == 12) {
						route.add(OFFICE);
					}else if (purpose == 3 || purpose == -1 || tripno == 1) {
						route.add(HOME);
					}else {
						route.add(++loc);
					}

				prePid = pid;
				preMagfac = magfac;
			}
			
			int motif = getType(0,compress(route));
			mapMotif.put(prePid, motif);
			mapMagfac.put(prePid, preMagfac);
			
        } catch (Exception e) {
        	e.printStackTrace();
        }	
		return 0;
	}

	public static void main(String[] args) {
	
		// pid, tripno, purpose, magfac
		String inputDir = "C:/Users/kashiyama/Desktop/input/";
		for (File file : new File(inputDir).listFiles()) {
			String name = file.getName().replaceAll(".csv", "");
			File out = new File(file.getParent(),String.format("%s_motif.csv", name));
			try(BufferedWriter bw = new BufferedWriter(new FileWriter(out));){
				 calculate(file.getAbsolutePath());
//				 bw.write(String.format("pid,motif,magfac\n"));
//				 for (Map.Entry<Integer, Integer> e : mapMotif.entrySet()) {
//					 bw.write(String.format("%d,%d,%d\n", e.getKey(), e.getValue(), mapMagfac.get(e.getKey())));
//				 }
				 
				 Map<Integer, Integer> map = new HashMap<>();
				 for (Map.Entry<Integer, Integer> e : mapMotif.entrySet()) {
					 int key = e.getKey();
					 int value = e.getValue();
					 int total = map.containsKey(value) ? map.get(value) : 0;
					 map.put(value, total + mapMagfac.get(key));
				 }
				 bw.write(String.format("motif,volume\n"));
				 for (Map.Entry<Integer, Integer> e : map.entrySet()) {
					 bw.write(String.format("%d,%d\n", e.getKey(), e.getValue()));
				 }
				 
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("end");
	}
}
