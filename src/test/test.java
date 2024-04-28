package test;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import com.google.common.io.Files;


public class test {

	public static void main(String[] args) {
		System.out.println(System.currentTimeMillis());
		String inputDir = "C:/Users/kashiyama/Desktop/skymonitor/datasets/gen2/";
		String outputDir = "C:/Users/kashiyama/Desktop/skymonitor/datasets/gen1/";
		
		File[] files = new File(inputDir).listFiles(new FileFilterExtension());
		try {
			Map<Integer, Integer> ann = new TreeMap<>();
			for (int i = 0; i < files.length; i+=10) {
				Map<Integer, File> map = new TreeMap<>();
				for (int j = 0; j < 10; j++) {
					File file = files[i+j];
					BufferedImage read = ImageIO.read(file);
					int total = 0;
					int w = read.getWidth(),h=read.getHeight();
					for(int y=0;y<h;y++){
						for(int x=0;x<w;x++){
			                int c = read.getRGB(x, y);
			                int r = ImageUtility.r(c);
			                int g = ImageUtility.g(c);
			                int b = ImageUtility.b(c);
							total += ((r+g+b)/3.0);			                
						}
					}
					map.put(total, file);
				}

				File target = (File) map.values().toArray()[5];
				File output = new File(outputDir, target.getName());
				Files.copy(target, output);
				Files.copy(new File(target.getAbsolutePath().replaceAll("jpg", "txt")), new File(output.getAbsolutePath().replaceAll("jpg", "txt")));
				
				

//				File text = new File(target.getAbsolutePath().replaceAll("jpg", "txt"));
//				try(BufferedReader br = new BufferedReader(new FileReader(text));) {
//					String record = null;
//					while ((record = br.readLine()) != null) {
//						String[] items = record.split(" ");
//						int type = Integer.valueOf(items[0]);
//						int vol = ann.containsKey(type) ? ann.get(type) : 0;
//						ann.put(type, vol+1);
//					}
//		        } catch (Exception e) {
//		        	e.printStackTrace();
//		        }
												
			}
			
			for (Map.Entry<Integer,Integer> e : ann.entrySet()) {
				System.out.println(e.getKey() + "," + e.getValue());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("end");
	}

}
