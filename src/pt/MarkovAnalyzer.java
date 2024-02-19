package pt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pseudo.res.EPurpose;
import pseudo.res.ETransition;
import utils.DateUtils;

public class MarkovAnalyzer {


	private static final SimpleDateFormat sfd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final int TIME_INTERVAL = 15 * 60;
	private static final int NUM_STEPS = (24*3600)/TIME_INTERVAL;
	private static final long DATE_LIMIT = DateUtils.parse(sfd, "2008-10-02 00:00:00").getTime();
	
	public class Record {
		public int time;
		public EPurpose purpose;
		public Record(EPurpose purpose, int time) {
			super();
			this.time = time;
			this.purpose = purpose;
		}
	}
	
	public class DailyState{
		public int pid;
		public int magfac;
		public EPurpose[] purpose;
		public int[] purpose_;
		public EPurpose[][] transition;
		
		public DailyState(int pid, int magfac, 
				EPurpose[] purpose, int[] purpose_,
				EPurpose[][] transition) {
			super();
			this.pid = pid;
			this.magfac = magfac;
			this.purpose = purpose;
			this.purpose_ = purpose_;
			this.transition = transition;
		}
	}
	
	private boolean check(List<Record> route) {
		for (int i = 1; i < route.size(); i++) {
			Record pre = route.get(i-1);
			Record next = route.get(i);
			if (pre.time == next.time) {
				return false;
			}
		}
		return true;
	}
	
	private DailyState creatEPTState(int pid, int magfac, List<Record> route) {
		DailyState res = null;
		if (check(route)) {
			res = new DailyState(pid, magfac, 
					new EPurpose[NUM_STEPS], new int[NUM_STEPS],
					new EPurpose[NUM_STEPS][2]);
			
			int add = 0;
			int size = route.size();
			EPurpose prePurpose = null;
			for (int i = 0; i < size; i++) {
				// 時間毎の状態を決定
				int startTime = route.get(i).time;
				EPurpose purpose = route.get(i).purpose;
				int endTime = 24*3600;
				if (i < (size - 1)) {
					endTime = route.get(i+1).time;
				}
				// 自由と業務は場所の移動を検知するためIDを変更する
				int purpose_ = purpose.getId();
				if (purpose_ >= EPurpose.SHOPPING.getId()) {
					purpose_ += (add++);
				}
				for (int j = startTime; j < endTime; j += TIME_INTERVAL) {
					int index = j/TIME_INTERVAL;
					res.purpose[index] = purpose; 
					res.purpose_[index] = purpose_;
				}
				
				// 移動を記録
				if (i > 0) {
					int tindex = (startTime / TIME_INTERVAL) - 1;
					res.transition[tindex][0] = prePurpose;
					res.transition[tindex][1] = purpose;
				}
				prePurpose = purpose;
			}
		}
		return res;
	}
	
	private List<DailyState> calculate(String filename) {
		List<DailyState> res = new ArrayList<>();
		try(BufferedReader br = new BufferedReader(new FileReader(filename));) {
			String record = null;
			int prePid = 0, preTripno = -1, preMagfac = 0;

			List<Record> route = null;
			Calendar cal = Calendar.getInstance();
			while ((record = br.readLine()) != null) {
				String[] items = record.split(",");
				int pid = Integer.valueOf(items[0]);
				int tripno = Integer.valueOf(items[1]);
				int purpose = Integer.valueOf(items[2]);
				int magfac = Integer.valueOf(items[3]);
				Date date = DateUtils.parse(sfd, items[4]);
				
				cal.setTime(date);
				int hour = cal.get(Calendar.HOUR_OF_DAY);
				int min = cal.get(Calendar.MINUTE);
				
				if (date.getTime() >= DATE_LIMIT) {
					hour = 23;
					min = 59;
				}
				int time = hour * 3600 + min * 60;
				
				
				if (pid != prePid) {
					if (prePid != 0) {
						DailyState state = creatEPTState(prePid, preMagfac, route);
						if (state != null) {
							res.add(state);
						}
					}
					route = new ArrayList<>();
					route.add(new Record(EPurpose.HOME, time));
				}

				if (pid != prePid || tripno != preTripno) {
					EPurpose ePurpose = getState(purpose,tripno);
					if (ePurpose != null) {
						route.add(new Record(ePurpose, time));
					}
				}
				
				preTripno = tripno;
				prePid = pid;
				preMagfac = magfac;
			}
        } catch (Exception e) {
        	e.printStackTrace();
        }	
		return res;
	}
	
	
	private static EPurpose getState(int purpose, int tripno) {
		if (purpose==1) {
			return EPurpose.OFFICE;
		}else if (purpose==2) {
				return EPurpose.SCHOOL;
		}else if (purpose == 3) {
			return EPurpose.HOME;
		}else if (purpose == 4) {
			return EPurpose.SHOPPING;
		}else if (purpose == 5) {
			return EPurpose.EATING;
		}else if (purpose == 7) {
			return EPurpose.HOSPITAL;
		}else if (purpose >=10 && purpose <=14) {
			return EPurpose.BUSINESS;
		}else if (tripno != 1) {
			return EPurpose.FREE;
		}
		return null;
	}

	private static List<Map<EPurpose,Map<ETransition, Double>>> caclulateProbability(List<DailyState> data) {
		List<Map<EPurpose, Integer>> listTotal = new ArrayList<>();
		List<Map<EPurpose,Map<EPurpose, Integer>>> listMove = new ArrayList<>();
		
		for (int i = 0; i < NUM_STEPS; i++) {
			listTotal.add(new HashMap<EPurpose, Integer>());
			listMove.add(new HashMap<EPurpose,Map<EPurpose, Integer>>());
		}
		
		//　状態と行動数を集計
		// 各時間、ステータスごとの人数を計測する listTotal
		// 各時間、ステータスごとの移動人数を計測する　listMove
		for (int i= 0; i < data.size(); i++) {			
			DailyState e = data.get(i);
			for (int j = 0; j < NUM_STEPS; j++) {
				EPurpose state = e.purpose[j];
				Map<EPurpose, Integer> map = listTotal.get(j);
				int total = map.containsKey(state) ? map.get(state) : 0;
				map.put(state, total + e.magfac);
				
				EPurpose[] move = e.transition[j];
				if (move[0] != null) {
					EPurpose ostate = move[0];
					EPurpose dstate = move[1];
					
					Map<EPurpose,Map<EPurpose, Integer>> tmap = listMove.get(j);
					Map<EPurpose, Integer> tomap = tmap.containsKey(ostate) ? tmap.get(ostate) : new HashMap<>();
					int vol = tomap.containsKey(dstate) ? tomap.get(dstate) : 0;
					tomap.put(dstate, vol + e.magfac);
					tmap.put(ostate, tomap);
				}
			}
		}
		
		// 確率を算出する。
		List<Map<EPurpose,Map<ETransition, Double>>> listProbs = new ArrayList<>();
		for (int i = 0; i < NUM_STEPS; i++) {
			Map<EPurpose,Map<ETransition, Double>> mapProbs = new HashMap<>();
			
			Map<EPurpose, Integer> mapTotal = listTotal.get(i);
			Map<EPurpose,Map<EPurpose, Integer>> mapMove = listMove.get(i);
			
			for (EPurpose s1 : EPurpose.values()) {
				Map<ETransition, Double> probs = new HashMap<>();
				if (mapTotal.containsKey(s1) && mapMove.containsKey(s1)) {
					double total = mapTotal.get(s1);
					Map<EPurpose, Integer> moves = mapMove.get(s1);
					double totalProbs = 0;
					for (EPurpose s2 : EPurpose.values()) {
						double prob = moves.containsKey(s2) ? moves.get(s2)/total : 0;
						probs.put(ETransition.get(s2), prob);
						totalProbs += prob;
					}
					probs.put(ETransition.STAY, 1 - totalProbs);
				}else {
					for (EPurpose s : EPurpose.values()) {
						probs.put(ETransition.get(s), 0d);
					}
					probs.put(ETransition.STAY, 1.0);
				}
				mapProbs.put(s1, probs);
			}
			listProbs.add(mapProbs);
		}
		
		return listProbs;
	}
	

//	copy (select pid, tripno, purpose, magfac1, date1 from ptdata.tky2008_trip
//			 where (subtripno=1 and work >=14 and work <= 14) 
//			  and (transport != 97 or purpose = -1)
//			  order by pid, tripno
//			 )
//		to 'C:\Users\kashiyama\Desktop\input\tky2008_trip_14-14.csv' WITH CSV
	
	public static void main(String[] args) {
	
		String[] names = {
//				"tky2008_trip_01-10_labor_male",
//				"tky2008_trip_01-10_labor_female",
//				"tky2008_trip_11-11_student1",
//				"tky2008_trip_12-13_student2",
//				"tky2008_trip_14-15_nolabor_female",
//				"tky2008_trip_14-15_nolabor_female_senior",
//				"tky2008_trip_14-15_nolabor_male",
//				"tky2008_trip_14-15_nolabor_male_senior"
				"test"
		};
		
		for (String name : names) {
			System.out.println(name);
			
			String tripFilename = String.format("C:/Users/kashiyama/Desktop/stat/markov/trip/%s.csv", name);
			//File trjFile1 = new File(String.format("C:/Users/kashiyama/Desktop/stat/markov/%s_time.csv", name));
			File trjFile2 = new File(String.format("C:/Users/kashiyama/Desktop/stat/markov/%s_prob.csv", name));
			try(
					//BufferedWriter bw1 = new BufferedWriter(new FileWriter(trjFile1));
					BufferedWriter bw2 = new BufferedWriter(new FileWriter(trjFile2));){
				 List<DailyState> data = new MarkovAnalyzer().calculate(tripFilename);	 
				 
//				 // header
//				 String head = String.format("pid,magfac");
//				 for (int i = 0; i < NUM_STEPS; i++) {
//					 int h = i / (NUM_STEPS / 24);
//					 int m = i - h * (NUM_STEPS / 24);
//					 
//					 head = String.format("%s,t%d%d", head, h, m);
//				 }
//				 bw1.write(head);bw1.newLine();
//				 
//				 for (DailyState e :  data) {
//					 String line = String.format("%05d,%03d", e.pid, e.magfac);
//					 for (int s : e.purpose_) {
//						 line = String.format("%s,%03d", line, s);
//					 }
//					 bw1.write(line);
//					 bw1.newLine();
//				 }
				 
	
				// 確率を計算
				List<Map<EPurpose,Map<ETransition, Double>>> listProbs = caclulateProbability(data);
				for (int i = 0; i < listProbs.size(); i++) {
					Map<EPurpose,Map<ETransition, Double>> mapProbs = listProbs.get(i);
					String line = String.format("%03d", i);
					for (EPurpose s : EPurpose.values()) {
						String inner = String.format("%03d", s.getId());
						Map<ETransition, Double> probs = mapProbs.get(s);
						for (ETransition a : ETransition.values()) {
							inner = String.format("%s %03d %.5f", inner, 
									a.getId(), probs.get(a));
						}
						line = String.format("%s,%s", line, inner);
					}
					bw2.write(line);
					bw2.newLine();
				}
			}catch(Exception e) {
				e.printStackTrace();
			}	
		}
		System.out.println("end:");
	}
}
