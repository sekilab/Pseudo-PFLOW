package gtfs;

import jp.ac.ut.csis.pflow.routing4.res.Network;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GTFSRouter {

    public static Stop findStopById(List<Stop> stops, String stopId) {
        for (Stop stop : stops) {
            if (stop.getStopId().equals(stopId)) {
                return stop;
            }
        }
        return null;
    }


    public static TripResult planTrip(Network net, double actualOriginLat, double actualOriginLon,
                                      double actualDestinationLat, double actualDestinationLon,
                                      String userDepartureTime,
                                      List<Trip> trips, List<StopTime> stopTimes,
                                      List<Stop> stops, List<FareRule> fareRules,
                                      List<Fare> fareAttributes) {

        List<Stop> originCandidates = GeoUtils.findNearestStops(stops, actualOriginLat, actualOriginLon, 2);
        List<Stop> destinationCandidates = GeoUtils.findNearestStops(stops, actualDestinationLat, actualDestinationLon, 2);

        TripResult bestTrip = TripFinder.findBestTrip(net, trips, stopTimes, originCandidates, destinationCandidates, userDepartureTime, actualOriginLat, actualOriginLon, actualDestinationLat, actualDestinationLon);


        return bestTrip;
    }

    public static void main(String[] args) throws IOException {
        // Parse GTFS data
//        try (BufferedReader reader = new BufferedReader(new FileReader("/Users/pang/Downloads/feed_kobecity_kobe-shiokaze_20241001_20240914083525/stops.txt"))) {
//            String firstLine = reader.readLine();
//            System.out.println("Headers: " + firstLine);
//        }
        String inputDir = "C:/Data/PseudoPFLOW/processing/";

        List<Stop> stops = GTFSParser.parseStops(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/stops.txt", inputDir));
        List<gtfs.Trip> trips = GTFSParser.parseTrips(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/trips.txt", inputDir));
        List<StopTime> stopTimes = GTFSParser.parseStopTimes(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/stop_times.txt", inputDir));
        List<Fare> fares = GTFSParser.parseFareAttributes(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/fare_attributes.txt", inputDir));
        List<FareRule> fareRules = GTFSParser.parseFareRules(String.format("%sfeed_kobecity_kobe-shiokaze_20241001_20240914083525/fare_rules.txt", inputDir));

        // Input origin and destination coordinates
        double originLat = 34.644170037069856;
        double originLon = 135.11339191269505;
        double destinationLat = 34.63889382011682;
        double destinationLon = 135.10093486953514;

        // Input departure time (in HH:mm:ss format)
        String userDepartureTime = "14:12:00";

        // Step 1: Find nearest stops
        // Stop originStop = GeoUtils.findNearestStop(stops, originLat, originLon);
        // Stop destinationStop = GeoUtils.findNearestStop(stops, destinationLat, destinationLon);
        List<Stop> originCandidates = GeoUtils.findNearestStops(stops, originLat, originLon, 3);  // 返回最近的3个车站
        List<Stop> destinationCandidates = GeoUtils.findNearestStops(stops, destinationLat, destinationLon, 3);  // 返回最近的3个车站


//        // Step 2: Find a connecting trip considering the user's departure time
//        // Trip trip = TripFinder.findConnectingTrip(trips, stopTimes, originStop, destinationStop, userDepartureTime);
//        Trip bestTrip = TripFinder.findBestTrip(trips, stopTimes, originCandidates, destinationCandidates, userDepartureTime);
//
//        if (bestTrip != null) {
//            Stop originStop = originCandidates.get(0);
//            Stop destinationStop = destinationCandidates.get(0);
//
//            long travelTime = TripFinder.calculateTravelTime(bestTrip, stopTimes, originStop, destinationStop);
//            if (travelTime != -1) {
//                StopTime originStopTime = findStopTime(stopTimes, bestTrip, originStop);
//                StopTime destinationStopTime = findStopTime(stopTimes, bestTrip, destinationStop);
//
//                System.out.println("Origin Station: " + originStop.getStopName());
//                System.out.println("Destination Station: " + destinationStop.getStopName());
//                System.out.println("Departure Time: " + originStopTime.getDepartureTime());
//                System.out.println("Arrival Time: " + destinationStopTime.getArrivalTime());
//                System.out.println("Travel Time: " + travelTime + " minutes");
//
//                double fare = FareCalculator.calculateFare(bestTrip, fareRules, fares, originStop, destinationStop);
//                if (fare != -1) {
//                    System.out.println("Fare: " + fare + " currency units");
//                } else {
//                    System.out.println("No valid fare found.");
//                }
//            } else {
//                System.out.println("Could not calculate travel time.");
//            }
//        } else {
//            System.out.println("No trip found.");
//        }

//        if (trip != null) {
//            // Step 3: Get StopTimes for the trip and the stops
//            StopTime originStopTime = StopTimeFinder.getStopTimeForTripAndStop(trip.getTripId(), originStop.getStopId(), stopTimes);
//            StopTime destinationStopTime = StopTimeFinder.getStopTimeForTripAndStop(trip.getTripId(), destinationStop.getStopId(), stopTimes);
//
//            // Step 4: Calculate travel time
//            long travelTime = TravelTimeCalculator.calculateTravelTime(originStopTime, destinationStopTime);
//            System.out.println("Travel time: " + travelTime + " minutes");
//
//            // Step 5: Calculate fare (if applicable)
//            double fare = FareCalculator.calculateFare(trip, fareRules, fares);
//            System.out.println("Travel fare: " + fare + " currency units");
//        } else {
//            System.out.println("No trip found that matches the criteria.");
//        }


    }

    public static long calculateWaitingTime(String userDepartureTime, String busDepartureTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm:ss");
        LocalTime userTime = LocalTime.parse(userDepartureTime, formatter);
        LocalTime busTime = LocalTime.parse(busDepartureTime, formatter);

        if (busTime.isBefore(userTime)) {
            busTime = busTime.plusHours(24);
        }

        return Math.abs(Duration.between(userTime, busTime).toMinutes());
    }

    public static StopTime findStopTime(List<StopTime> stopTimes, Trip trip, Stop stop) {
        for (StopTime stopTime : stopTimes) {
            if (stopTime.getTripId().equals(trip.getTripId()) && stopTime.getStopId().equals(stop.getStopId())) {
                return stopTime;
            }
        }
        return null;
    }

}
