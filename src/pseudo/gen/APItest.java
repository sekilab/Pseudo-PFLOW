package pseudo.gen;

import org.apache.http.Header;
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
            // 创建一个自定义的信任所有证书的SSL上下文
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(new TrustSelfSignedStrategy())
                    .build();

            // 使用自定义的SSL上下文和主机名验证器创建HttpClient
            // 同时设置默认请求配置以使用标准Cookie规范
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSslcontext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setCookieSpec(CookieSpecs.STANDARD) // 使用标准Cookie规范
                            .build())
                    .build();

            // 第一个POST请求创建会话
            HttpPost createSessionPost = new HttpPost("https://157.82.223.35/webapi/CreateSession");

            List<NameValuePair> sessionParams = new ArrayList<>();
            sessionParams.add(new BasicNameValuePair("UserID", "Pang_Yanbo"));
            sessionParams.add(new BasicNameValuePair("Password", "Pyb-37167209"));
            createSessionPost.setEntity(new UrlEncodedFormEntity(sessionParams));

//            HttpResponse sessionResponse = httpClient.execute(createSessionPost);
//            Header[] headers = sessionResponse.getHeaders("Set-Cookie");
//            for (Header header : headers) {
//                System.out.println(header.getValue());
//            }



            HttpResponse sessionResponse = httpClient.execute(createSessionPost);
            if (sessionResponse.getStatusLine().getStatusCode() == 200) {
                String sessionResponseBody = EntityUtils.toString(sessionResponse.getEntity());
                System.out.println("Session created successfully");
                System.out.println(sessionResponseBody);
                String sessionid = sessionResponseBody.split(",")[1].trim().replace("\r", "").replace("\n", "");;
                System.out.println("WebApiSessionID=" + sessionid);

                long startTime = System.currentTimeMillis();

                // 第二个POST请求使用会话ID
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

                // 计算请求耗时
                long duration = endTime - startTime;

                // 打印请求耗时
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
