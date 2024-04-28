package pseudo.pre;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

public class CensusDownloader {

	  public static void download(String path, int i ) {
	    try {
	      URL url = new URL(path);
	      HttpURLConnection conn =
	          (HttpURLConnection) url.openConnection();
	      conn.setAllowUserInteraction(true);
	      conn.setInstanceFollowRedirects(true);
	      conn.setRequestMethod("POST");
	      conn.setDoOutput(true);
	      
	      
	      conn.connect();
	     

	      int httpStatusCode = conn.getResponseCode();
	      if (httpStatusCode != HttpURLConnection.HTTP_OK) {
	        throw new Exception("HTTP Status " + httpStatusCode);
	      }

	      String contentType = conn.getContentType();
	      String disposition = conn.getHeaderField("Content-Disposition");
	      int contentLength = conn.getContentLength(); 
	      
	      System.out.println("Content-Type: " + contentType);
	      System.out.println("Content-Disposition: " + disposition);
	      System.out.println("Content-Length: " + contentLength);
	      System.out.println(path);
	      System.out.println(i);
	      
          InputStream inputStream = conn.getInputStream();
          String saveFilePath = String.format("C:/Users/kashiyama/Desktop/stat/statdata/国勢調査27人口500/%d.zip", i);
         
          // opens an output stream to save into file
          FileOutputStream outputStream = new FileOutputStream(saveFilePath);

          int bytesRead = -1;
          byte[] buffer = new byte[4096];
          while ((bytesRead = inputStream.read(buffer)) != -1) {
              outputStream.write(buffer, 0, bytesRead);
          }

          outputStream.close();
          inputStream.close();
          

	    } catch (FileNotFoundException e) {
	      e.printStackTrace();
	    } catch (ProtocolException e) {
	      e.printStackTrace();
	    } catch (MalformedURLException e) {
	      e.printStackTrace();
	    } catch (IOException e) {
	      e.printStackTrace();
	    } catch (Exception e) {
	      System.out.println(e.getMessage());
	      e.printStackTrace();
	    }
	  }
	  
	  
	public static void main(String[] args) {
		
//			String[] paths = {
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=01&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=02&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=03&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=04&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=05&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=06&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=07&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=08&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=09&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=10&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=11&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=12&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=13&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=14&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=15&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=16&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=17&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=18&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=19&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=20&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=21&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=22&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=23&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=24&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=25&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=26&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=27&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=28&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=29&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=30&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=31&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=32&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=33&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=34&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=35&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=36&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=37&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=38&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=39&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=40&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=41&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=42&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=43&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=44&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=45&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=46&coordSys=1&format=shape&downloadType=5&datum=2000",
//					"https://www.e-stat.go.jp/gis/statmap-search/data?dlserveyId=A002005212015&code=47&coordSys=1&format=shape&downloadType=5&datum=2000"
//			};



		String[] paths = {
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6848&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6847&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6842&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6841&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6840&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6748&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6747&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6742&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6741&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6740&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6647&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6646&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6645&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6644&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6643&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6642&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6641&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6546&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6545&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6544&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6543&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6542&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6541&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6540&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6445&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6444&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6443&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6442&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6441&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6440&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6439&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6343&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6342&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6341&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6340&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6339&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6243&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6241&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6240&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6239&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6141&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6140&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6139&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6041&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6040&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=6039&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5942&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5941&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5940&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5939&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5841&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5840&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5839&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5741&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5740&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5739&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5738&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5641&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5640&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5639&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5638&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5637&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5636&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5541&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5540&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5539&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5538&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5537&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5536&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5531&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5440&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5439&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5438&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5437&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5436&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5435&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5433&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5432&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5340&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5339&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5338&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5337&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5336&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5335&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5334&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5333&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5332&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5240&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5239&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5238&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5237&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5236&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5235&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5234&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5233&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5232&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5231&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5229&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5139&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5138&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5137&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5136&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5135&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5134&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5133&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5132&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5131&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5130&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5129&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5039&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5038&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5036&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5035&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5034&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5033&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5032&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5031&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5030&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=5029&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4939&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4934&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4933&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4932&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4931&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4930&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4929&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4928&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4839&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4831&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4830&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4829&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4828&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4740&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4739&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4731&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4730&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4729&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4728&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4631&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4630&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4629&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4540&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4531&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4530&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4529&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4440&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4429&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4329&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4328&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4230&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4229&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4142&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4129&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4128&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4042&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4040&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4028&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=4027&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3942&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3928&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3927&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3926&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3841&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3831&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3824&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3823&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3741&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3725&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3724&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3653&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3641&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3631&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3624&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3623&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3622&downloadType=2",
				"https://www.e-stat.go.jp/gis/statmap-search/data?statsId=T000847&code=3036&downloadType=2"};		


	
		for (int i = 0; i < paths.length; i++) {
			download(paths[i], i);
		}
		System.out.println("end");
		
	}

}
