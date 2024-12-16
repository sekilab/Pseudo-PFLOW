package pseudo.gen;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TripGenerator_GoogleMapAPI {

    private static final String API_KEY = "AIzaSyB4_2_KSvh8Cgy6EGrczN8uEdpl2U3AtVU";

    public static void main(String[] args) throws Exception {
        // Create a Gson instance for JSON creation
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Build the JSON payload
        JsonObject requestBody = new JsonObject();

        ZonedDateTime departureTime = ZonedDateTime.parse("2024-10-15T10:00:00Z");
        String rfc3339Timestamp = departureTime.format(DateTimeFormatter.ISO_INSTANT);

        // Origin location
        JsonObject origin = new JsonObject();
        JsonObject originLocation = new JsonObject();
        JsonObject originLatLng = new JsonObject();
        originLatLng.addProperty("latitude", 34.643834606143116);
        originLatLng.addProperty("longitude", 135.11090082771977);
        originLocation.add("latLng", originLatLng);
        origin.add("location", originLocation);

        // Destination location
        JsonObject destination = new JsonObject();
        JsonObject destinationLocation = new JsonObject();
        JsonObject destinationLatLng = new JsonObject();
        destinationLatLng.addProperty("latitude", 34.641830936559764);
        destinationLatLng.addProperty("longitude", 135.10362667688685);
        destinationLocation.add("latLng", destinationLatLng);
        destination.add("location", destinationLocation);

        // Add origin, destination to request body
        requestBody.add("origin", origin);
        requestBody.add("destination", destination);

        // Add other parameters
        requestBody.addProperty("travelMode", "TRANSIT");
        // requestBody.addProperty("routingPreference", "TRAFFIC_AWARE");
        requestBody.addProperty("departureTime", rfc3339Timestamp);
        requestBody.addProperty("computeAlternativeRoutes", true);

        // Add route modifiers
        JsonObject routeModifiers = new JsonObject();
        routeModifiers.addProperty("avoidTolls", false);
        routeModifiers.addProperty("avoidHighways", false);
        routeModifiers.addProperty("avoidFerries", false);
        requestBody.add("routeModifiers", routeModifiers);

        // Add routing preference (example: LESS_WALKING or FEWER_TRANSFERS)
        // requestBody.addProperty("routingPreference", "FEWER_TRANSFERS");

        // Add transit modes (e.g., BUS, RAIL)
        JsonArray modes = new JsonArray();
        modes.add("BUS");
        modes.add("RAIL");
        requestBody.add("modes", modes);

        requestBody.addProperty("languageCode", "en-US");
        // requestBody.addProperty("units", "IMPERIAL");

        // Convert the JSON object to a string
        String jsonRequest = gson.toJson(requestBody);

        // Prepare the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", API_KEY)
                .header("X-Goog-FieldMask", "routes.*")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        // Create an HTTP client and send the request
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Print the response
        System.out.println("Response code: " + response.statusCode());
        System.out.println("Response body: " + response.body());
    }
}
