package gtfs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GTFSParser {

    public static List<Stop> parseStops(String filePath) throws IOException {
        List<Stop> stops = new ArrayList<>();

        // Use InputStreamReader with StandardCharsets.UTF_8 to handle BOM correctly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();

            // Remove BOM if it exists in the first header line
            if (firstLine != null && firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }

            // Parse the CSV after BOM is removed
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withHeader(firstLine.split(","))  // Ensure headers are correctly used from BOM-cleaned line
                    .parse(reader);

            for (CSVRecord record : parser) {
                String stopId = record.get("stop_id");
                String stopName = record.get("stop_name");

                // Handle optional platform_code (check if it's mapped and set)
                String platformCode = record.isSet("platform_code") ? record.get("platform_code") : "";

                // Parse lat/lon as double, handling empty fields
                double stopLat = record.isSet("stop_lat") && !record.get("stop_lat").isEmpty() ? Double.parseDouble(record.get("stop_lat")) : 0.0;
                double stopLon = record.isSet("stop_lon") && !record.get("stop_lon").isEmpty() ? Double.parseDouble(record.get("stop_lon")) : 0.0;

                String zoneId = record.get("zone_id");
                int locationType = record.isSet("location_type") && !record.get("location_type").isEmpty() ? Integer.parseInt(record.get("location_type")) : 0;

                Stop stop = new Stop(stopId, stopName, platformCode, stopLat, stopLon, zoneId, locationType);
                stops.add(stop);
            }
        }
        return stops;
    }

    public static List<Trip> parseTrips(String filePath) throws IOException {
        List<Trip> trips = new ArrayList<>();

        // Use InputStreamReader with StandardCharsets.UTF_8 to handle BOM correctly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();

            // Remove BOM if it exists in the first header line
            if (firstLine != null && firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }

            // Parse the CSV after BOM is removed
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withHeader(firstLine.split(","))  // Ensure headers are correctly used from BOM-cleaned line
                    .parse(reader);

            for (CSVRecord record : parser) {
                String routeId = record.get("route_id");
                String tripId = record.get("trip_id");
                String serviceId = record.get("service_id");

                // Handle optional fields (trip_headsign, direction_id, block_id, shape_id) as needed
                String tripHeadsign = record.isSet("trip_headsign") ? record.get("trip_headsign") : "";
                String directionId = record.isSet("direction_id") ? record.get("direction_id") : "";
                String blockId = record.isSet("block_id") ? record.get("block_id") : "";
                String shapeId = record.isSet("shape_id") ? record.get("shape_id") : "";

                // Create a Trip object and add it to the list
                Trip trip = new Trip(routeId, tripId, serviceId, tripHeadsign, directionId, blockId, shapeId);
                trips.add(trip);
            }
        }
        return trips;
    }

    public static List<StopTime> parseStopTimes(String filePath) throws IOException {
        List<StopTime> stopTimes = new ArrayList<>();

        // Use InputStreamReader with StandardCharsets.UTF_8 to handle BOM correctly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();

            // Remove BOM if it exists in the first header line
            if (firstLine != null && firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }

            // Parse the CSV after BOM is removed
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withHeader(firstLine.split(","))  // Ensure headers are correctly used from BOM-cleaned line
                    .parse(reader);

            for (CSVRecord record : parser) {
                String tripId = record.get("trip_id");
                String stopId = record.get("stop_id");

                // Handle missing or optional fields (arrival_time, departure_time)
                String arrivalTime = record.isSet("arrival_time") ? record.get("arrival_time") : "";
                String departureTime = record.isSet("departure_time") ? record.get("departure_time") : "";
                int stopSequence = record.isSet("stop_sequence") && !record.get("stop_sequence").isEmpty() ? Integer.parseInt(record.get("stop_sequence")) : 0;

                StopTime stopTime = new StopTime(tripId, stopId, arrivalTime, departureTime, stopSequence);
                stopTimes.add(stopTime);
            }
        }
        return stopTimes;
    }

    public static List<Fare> parseFareAttributes(String filePath) throws IOException {
        List<Fare> fares = new ArrayList<>();

        // Use InputStreamReader with StandardCharsets.UTF_8 to handle BOM correctly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();

            // Remove BOM if it exists in the first header line
            if (firstLine != null && firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }

            // Parse the CSV after BOM is removed
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withHeader(firstLine.split(","))  // Ensure headers are correctly used from BOM-cleaned line
                    .parse(reader);

            for (CSVRecord record : parser) {
                String fareId = record.get("fare_id");
                double price = Double.parseDouble(record.get("price"));
                String currencyType = record.get("currency_type");
                String paymentMethod = record.get("payment_method");
                String transfers = record.get("transfers");

                Fare fare = new Fare(fareId, price, currencyType, paymentMethod, transfers);
                fares.add(fare);
            }
        }
        return fares;
    }

    public static List<FareRule> parseFareRules(String filePath) throws IOException {
        List<FareRule> fareRules = new ArrayList<>();

        // Use InputStreamReader with StandardCharsets.UTF_8 to handle BOM correctly
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();

            // Remove BOM if it exists in the first header line
            if (firstLine != null && firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }

            // Parse the CSV after BOM is removed
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withHeader(firstLine.split(","))  // Ensure headers are correctly used from BOM-cleaned line
                    .parse(reader);

            for (CSVRecord record : parser) {
                String fareId = record.get("fare_id");
                String routeId = record.get("route_id");
                String originId = record.isSet("origin_id") ? record.get("origin_id") : "";
                String destinationId = record.isSet("destination_id") ? record.get("destination_id") : "";
                String containsId = record.isSet("contains_id") ? record.get("contains_id") : "";

                FareRule fareRule = new FareRule(fareId, routeId, originId, destinationId, containsId);
                fareRules.add(fareRule);
            }
        }
        return fareRules;
    }


}

