package gtfs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class OTPTripPlanner {

    private static Map<String, String> stopIdToNameMap = new HashMap<>();

    public static void loadStopData(List<Stop> stops) {
        for (Stop stop : stops) {
            stopIdToNameMap.put(stop.getStopId(), stop.getStopName());
        }
    }

    public static TripResult planTripWithWalking(double originLat, double originLon, double destinationLat, double destinationLon, String departureTime) {
        try {
            String url = String.format("http://localhost:8082/otp/routers/default/plan?fromPlace=%f,%f&toPlace=%f,%f&time=%s&date=2024-10-13&mode=TRANSIT,WALK&maxWalkDistance=200",
                    originLat, originLon, destinationLat, destinationLon, departureTime);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());

            if (jsonResponse.has("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                String errorMsg = error.optString("msg", "Unknown error");
                System.out.println("Error from OTP API: " + errorMsg);
            }

            if (!jsonResponse.has("plan")) {
                System.out.println("No plan found in response.");
                return null;
            }

            JSONArray itineraries = jsonResponse.getJSONObject("plan").getJSONArray("itineraries");

            if (itineraries.length() > 0) {
                JSONObject itinerary = itineraries.getJSONObject(0);
                JSONArray legs = itinerary.getJSONArray("legs");

                long totalWalkingTime = 0;
                long transitTime = 0;
                double fare = calculateFare(itinerary);
                boolean usedTransit = false;

                String originStation = legs.getJSONObject(0).getJSONObject("from").getString("name");
                String destinationStation = legs.getJSONObject(legs.length() - 1).getJSONObject("to").getString("name");

                for (int i = 0; i < legs.length(); i++) {
                    JSONObject leg = legs.getJSONObject(i);
                    String mode = leg.getString("mode");

                    if (mode.equals("WALK")) {
                        totalWalkingTime += leg.getLong("duration") / 60;
                    } else if (mode.equals("TRANSIT")) {
                        transitTime += leg.getLong("duration") / 60;
                        usedTransit = true;  // 如果找到 TRANSIT 段，则设置为 true
                    }
                }

                // 解析出发时间和到达时间
                long startTimeMillis = itinerary.getLong("startTime");
                long endTimeMillis = itinerary.getLong("endTime");

                LocalTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTimeMillis), ZoneOffset.ofHours(9)).toLocalTime();
                LocalTime endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTimeMillis), ZoneOffset.ofHours(9)).toLocalTime();

                if (endTime.isBefore(startTime)) {
                    endTime = endTime.plusHours(24);
                }

                long totalTime = Duration.between(startTime, endTime).toMinutes();

                return new TripResult(originStation, destinationStation, startTime.toString(), endTime.toString(), totalTime, fare, usedTransit);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String convertToUTCTime(String hhmm) {
        LocalTime localTime = LocalTime.parse(hhmm.substring(0, 2) + ":" + hhmm.substring(2, 4));
        LocalTime utcTime = localTime.minusHours(9);
        return utcTime.toString() + ":00";
    }

    public static void main(String[] args) {

        double originLat = 34.642904;
        double originLon = 135.113179;
        double destinationLat = 34.64822658180787;
        double destinationLon = 135.1143155099588;

        String departureTimeHHMM = "1850";
        // tring formattedTime = convertTimeFormat(departureTimeHHMM);
        String utcDepartureTime = convertToUTCTime(departureTimeHHMM);

        TripResult result = planTripWithWalking(originLat, originLon, destinationLat, destinationLon, utcDepartureTime);

        if (result != null) {
            System.out.println("Trip planning result:");
            System.out.println(result);
        } else {
            System.out.println("No trip plan available for the given input.");
        }
    }

    public static String convertTimeFormat(String hhmm) {
        if (hhmm == null || hhmm.length() != 4) {
            throw new IllegalArgumentException("Input time must be in 'hhmm' format");
        }
        String hours = hhmm.substring(0, 2);
        String minutes = hhmm.substring(2, 4);
        return hours + ":" + minutes + ":00";
    }

    private static double calculateFare(JSONObject itinerary) {
        return 240.0;
    }
}
