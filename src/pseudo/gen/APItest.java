package pseudo.gen;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.util.ArrayList;
import java.util.List;

public class APItest {

    public static void main(String[] args) {
        try {
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(new TrustSelfSignedStrategy())
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSslcontext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setCookieSpec(CookieSpecs.STANDARD)
                            .build())
                    .build();

            HttpPost createSessionPost = new HttpPost("https://157.82.223.35/webapi/CreateSession");

            List<NameValuePair> sessionParams = new ArrayList<>();
            sessionParams.add(new BasicNameValuePair("UserID", "Pang_Yanbo"));
            sessionParams.add(new BasicNameValuePair("Password", "Pyb-37167209"));
            createSessionPost.setEntity(new UrlEncodedFormEntity(sessionParams));

            HttpResponse sessionResponse = httpClient.execute(createSessionPost);
            if (sessionResponse.getStatusLine().getStatusCode() == 200) {
                String sessionResponseBody = EntityUtils.toString(sessionResponse.getEntity());
                System.out.println("Session created successfully");
                System.out.println(sessionResponseBody);
                String sessionid = sessionResponseBody.split(",")[1].trim().replace("\r", "").replace("\n", "");;
                System.out.println("WebApiSessionID=" + sessionid);

                long startTime = System.currentTimeMillis();

                HttpPost mixedRoutePost = new HttpPost("https://157.82.223.35/webapi/GetMixedRoute");
                List<NameValuePair> mixedRouteParams = new ArrayList<>();
                mixedRouteParams.add(new BasicNameValuePair("UnitTypeCode", "2"));
                mixedRouteParams.add(new BasicNameValuePair("StartLongitude", "139.56629225"));
                mixedRouteParams.add(new BasicNameValuePair("StartLatitude", "35.663611996"));
                mixedRouteParams.add(new BasicNameValuePair("GoalLongitude", "139.75884674036305"));
                mixedRouteParams.add(new BasicNameValuePair("GoalLatitude", "35.69638343647759"));
                mixedRouteParams.add(new BasicNameValuePair("TransportCode", "3"));
                mixedRoutePost.setEntity(new UrlEncodedFormEntity(mixedRouteParams));
                mixedRoutePost.setHeader("Cookie", "WebApiSessionID=" + sessionid);

                HttpResponse mixedRouteResponse = httpClient.execute(mixedRoutePost);
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                System.out.println("Request took: " + duration + " milliseconds");

                if (mixedRouteResponse.getStatusLine().getStatusCode() == 200) {
                    String mixedRouteResponseBody = EntityUtils.toString(mixedRouteResponse.getEntity());
                    System.out.println("Mixed Route Search successfully");
                    System.out.println(mixedRouteResponseBody);
                } else {
                    System.out.println("Failed to post data: " + mixedRouteResponse.getStatusLine().getStatusCode());
                }
            } else {
                System.out.println("Failed to create session: " + sessionResponse.getStatusLine().getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
