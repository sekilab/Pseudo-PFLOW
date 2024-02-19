package pt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TripGenerator {
	
	private static boolean  subtripFlag = false;
	
	public class TripTask implements Callable<Integer>{
		private String outputFilename;
		private List<File> files;
		
		public TripTask(List<File> files, String outputFilename){
			this.files = files;
			this.outputFilename = outputFilename;
		}	
		
		private void process1(File file, BufferedWriter writter) {
			try(BufferedReader br = new BufferedReader(new FileReader(file));) {
				String record;
				
				int pid = 0;
				String date = "";
				double lon = -1, lat = -1;
				int purpose=0, transport=0, magfac1=0, magfac2=0,age = 0, sex = 0, work = 0;
				int tripNo=0, subTripNo=0;
				
				int prePurpose = -1, preTransport = -1;
				double preLon = -1, preLat = -1;
				String preDate = "";
				int preTripNo = -1, preSubTripNo = -1;
				int hoseiSubTripNo = 0;
				while ((record = br.readLine()) != null) {
					String[] items = record.split(",");
					pid = Integer.valueOf(items[0]);
					tripNo = Integer.valueOf(items[1]);
					subTripNo = Integer.valueOf(items[2]);

					if ((tripNo == preTripNo && subTripNo < preSubTripNo) || tripNo < preTripNo) {
						continue;
					}
					
					
					date = String.valueOf(items[3]);
					lon = Double.valueOf(items[4]);
					lat = Double.valueOf(items[5]);
					sex = Integer.valueOf(items[6]);
					age = Integer.valueOf(items[7])*5;
					work = Integer.valueOf(items[9]);
					purpose = Integer.valueOf(items[10]);
					magfac1 = Integer.valueOf(items[11]);
					magfac2 = items[12].equals("") ? 0 : Integer.valueOf(items[12]);
					transport = Integer.valueOf(items[13]);
				
					if (tripNo != preTripNo || subTripNo != preSubTripNo) {
						if (preTripNo > 0 && preDate.equals(date) == false) {
							writter.write(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%f,%f,%f,%f", 
								pid, preTripNo, ++hoseiSubTripNo, prePurpose, preTransport, age, sex, work,magfac1,magfac2, 
								preDate, date,
								preLon, preLat, lon, lat));
							writter.newLine();
						}
						
						if (tripNo != preTripNo) {
							hoseiSubTripNo = 0;
						}
						
						
						preTripNo = tripNo;
						preSubTripNo = subTripNo;
						if (purpose != 99) {
							prePurpose = purpose;
						}
						preLon = lon;
						preLat = lat;
						preDate = date;
						preTransport = transport;
					}
					
				}
				
				writter.write(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%f,%f,%f,%f", 
						pid, preTripNo, ++hoseiSubTripNo, prePurpose, preTransport, age, sex, work,magfac1,magfac2,
						preDate, date,
						preLon, preLat, lon, lat));
				writter.newLine();
				
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
		}
		
		private void process(File file, BufferedWriter writter) {
			try(BufferedReader br = new BufferedReader(new FileReader(file));) {
				String record;
				
				int pid = 0;
				String date = "";
				double lon = -1, lat = -1;
				int purpose=0, transport=0, magfac1=0, magfac2=0,age = 0, sex = 0, work = 0;
				int tripNo=0;
				
				int prePurpose = -1, preTransport = -1;
				double preLon = -1, preLat = -1;
				String preDate = "";
				int preTripNo = -1;
				while ((record = br.readLine()) != null) {
					String[] items = record.split(",");
					pid = Integer.valueOf(items[0]);
					tripNo = Integer.valueOf(items[1]);

					if (tripNo < preTripNo) {
						continue;
					}
				
					date = String.valueOf(items[3]);
					lon = Double.valueOf(items[4]);
					lat = Double.valueOf(items[5]);
					sex = Integer.valueOf(items[6]);
					age = Integer.valueOf(items[7])*5;
					work = Integer.valueOf(items[9]);
					purpose = Integer.valueOf(items[10]);
					magfac1 = Integer.valueOf(items[11]);
					magfac2 = items[12].equals("") ? 0 : Integer.valueOf(items[12]);
					transport = Integer.valueOf(items[13]);
					transport = transport != 97 ? 1 : transport;
					
					if (tripNo != preTripNo) {
						if (preTripNo > 0 && preDate.equals(date) == false) {
							writter.write(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%f,%f,%f,%f", 
								pid, preTripNo, prePurpose, preTransport, age, sex, work,magfac1,magfac2, 
								preDate, date,
								preLon, preLat, lon, lat));
							writter.newLine();
						}
						
						preTripNo = tripNo;
						if (purpose != 99) {
							prePurpose = purpose;
						}
						preLon = lon;
						preLat = lat;
						preDate = date;
						preTransport = transport;
					}
				}
				
				writter.write(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%f,%f,%f,%f", 
						pid, preTripNo, prePurpose, preTransport, age, sex, work,magfac1,magfac2,
						preDate, date,
						preLon, preLat, lon, lat));
				writter.newLine();
				
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
		}
		
		@Override
		public Integer call() throws Exception {
			try(BufferedWriter bw = new BufferedWriter(new FileWriter(outputFilename));){
				for (File file : files) {
					if (subtripFlag) {
						process(file, bw);
					}else {
						process(file, bw);
					}
				}
				System.out.println(outputFilename);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return 0;
		}
	}
	
	public int process(List<File> files, String dirName) {
		int numThreads = Runtime.getRuntime().availableProcessors();
		ExecutorService es = Executors.newFixedThreadPool(numThreads);
		System.out.println("NumOfThreads:" + numThreads);
		
		List<Future<Integer>> features = new ArrayList<>();
		int listSize = files.size();
		int taskNum = numThreads * 10;
		int stepSize = listSize / taskNum + (listSize % taskNum != 0 ? 1 : 0);
		for (int i = 0; i < listSize; i+= stepSize){
			int end = i + stepSize;
			end = (listSize < end) ? listSize : end;
			List<File> subList = files.subList(i, end);
			String filename = String.format("%s%06d.csv", dirName, (int)i/stepSize);
			features.add(es.submit(new TripTask(subList, filename)));
		}
		es.shutdown();	
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return 0;
	}
		
	public static void main(String[] args) {
		String code = "shizuoka";
		File dir = new File(String.format("C:/Users/kashiyama/Desktop/stat/statdata/都市圏PT/%s/p-csv/", code));
		String outputDir = String.format("C:/Users/kashiyama/Desktop/stat/statdata/都市圏PT/%s/", code);
		
		subtripFlag = true;
		
		List<File> files = new ArrayList<>();
		for (File sdir : dir.listFiles()) {
			files.addAll(Arrays.asList(sdir.listFiles()));
		}
		System.out.println("NumofFiles:" + files.size());
		new TripGenerator().process(files, outputDir);
		System.out.println("end");
	}
}
