package pseudo.gen;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RoadRouteTest {

    public static void main(String[] args) {
        // Create session and obtain the cookie
        String sessionUrl = "https://157.82.223.35/webapi/CreateSession";
        String sessionParams = "UserID=Pang_Yanbo&Password=Pyb-37167209";

        String sessionCookie = createSessionAndGetCookie(sessionUrl, sessionParams);
        if (sessionCookie == null) {
            System.out.println("Failed to create session");
            return;
        }

        // Proceed with road route requests
        String roadRouteUrl = "https://157.82.223.35/webapi/GetRoadRoute";
        String roadRouteParams = "UnitTypeCode=2&StartLongitude=139.67727019&StartLatitude=35.66412606&"
                + "GoalLongitude=138.808493&GoalLatitude=36.017459&WayLongitude=&WayLatitude=&"
                + "RoadKindCode=&RoadNo=&TransportCode=6&OutputNum=1";

        // Start the timer
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            try {
                URL url = new URL(roadRouteUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                // Set the request method and properties
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Cookie", sessionCookie); // Use the session cookie here
                conn.setDoOutput(true);

                // Send the road route request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = roadRouteParams.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read the response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                }

                // Process the response
                System.out.println("Response length for request " + (i + 1) + ": " + response.length());

                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // End the timer and print the total time
        long endTime = System.currentTimeMillis();
        System.out.println("Total time for 100 requests: " + (endTime - startTime) + " ms");
        
    }

    private static String createSessionAndGetCookie(String urlString, String params) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Set the request method and properties for session creation
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            // Send the session creation request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = params.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read the session creation response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // Extract the session cookie from the response
            if (conn.getResponseCode() == 200) {
                String headerName;
                for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++) {
                    if (headerName.equals("Set-Cookie")) {
                        String cookie = conn.getHeaderField(i);
                        if (cookie.startsWith("WebApiSessionID")) {
                            System.out.println("Session cookie obtained: " + cookie);
                            return cookie; // Return the session cookie
                        }
                    }
                }
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
