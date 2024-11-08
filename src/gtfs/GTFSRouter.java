package gtfs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GTFSRouter {

    public static TripResult planTrip(double actualOriginLat, double actualOriginLon,
                                  double actualDestinationLat, double actualDestinationLon,
                                  String userDepartureTime,
                                  List<Trip> trips, List<StopTime> stopTimes,
                                  List<Stop> stops, List<FareRule> fareRules,
                                  List<Fare> fareAttributes) {

        List<Stop> originCandidates = GeoUtils.findNearestStops(stops, actualOriginLat, actualOriginLon, 3);
        List<Stop> destinationCandidates = GeoUtils.findNearestStops(stops, actualDestinationLat, actualDestinationLon, 3);

        Trip bestTrip = TripFinder.findBestTrip(trips, stopTimes, originCandidates, destinationCandidates, userDepartureTime);

        if (bestTrip != null) {

            Stop originStop = originCandidates.get(0);
            Stop destinationStop = destinationCandidates.get(0);

            long walkTimeToOriginStation = GeoUtils.calculateWalkingTime(actualOriginLat, actualOriginLon, originStop.getLatitude(), originStop.getLongitude());

            long walkTimeToDestination = GeoUtils.calculateWalkingTime(destinationStop.getLatitude(), destinationStop.getLongitude(), actualDestinationLat, actualDestinationLon);


            StopTime originStopTime = findStopTime(stopTimes, bestTrip, originStop);

            long waitingTimeAtStation = calculateWaitingTime(userDepartureTime, originStopTime.getDepartureTime());

            // 选择步行时间和等待时间中的较大值作为用户从实际出发点到公交车站所需的时间
            long timeToOriginStation = Math.max(walkTimeToOriginStation, waitingTimeAtStation);

            long travelTime = TripFinder.calculateTravelTime(bestTrip, stopTimes, originStop, destinationStop);

            if (travelTime != -1 & travelTime != 0) {

                StopTime destinationStopTime = findStopTime(stopTimes, bestTrip, destinationStop);

                long totalTravelTime = Math.abs(travelTime) + timeToOriginStation + walkTimeToDestination;

                double fare = FareCalculator.calculateFare(bestTrip, fareRules, fareAttributes, originStop, destinationStop);
                if (fare == -1) {
                    fare = 0;
                }

                String result = "Origin Station: " + originStop.getStopName() + "\n" +
                        "Destination Station: " + destinationStop.getStopName() + "\n" +
                        "Departure Time: " + originStopTime.getDepartureTime() + "\n" +
                        "Arrival Time: " + destinationStopTime.getArrivalTime() + "\n" +
                        "Travel Time (including walking): " + totalTravelTime + " minutes\n" +
                        "Fare: " + fare + " currency units";

                return new TripResult(
                        originStop.getStopName(),
                        destinationStop.getStopName(),
                        originStopTime.getDepartureTime(),
                        destinationStopTime.getArrivalTime(),
                        totalTravelTime,
                        fare
                );
            } else {
                return null;
            }
        } else {
            return null;
        }
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


        // Step 2: Find a connecting trip considering the user's departure time
        // Trip trip = TripFinder.findConnectingTrip(trips, stopTimes, originStop, destinationStop, userDepartureTime);
        Trip bestTrip = TripFinder.findBestTrip(trips, stopTimes, originCandidates, destinationCandidates, userDepartureTime);

        if (bestTrip != null) {
            // 计算最佳路径的行程时间
            Stop originStop = originCandidates.get(0);
            Stop destinationStop = destinationCandidates.get(0);

            long travelTime = TripFinder.calculateTravelTime(bestTrip, stopTimes, originStop, destinationStop);
            if (travelTime != -1) {
                StopTime originStopTime = findStopTime(stopTimes, bestTrip, originStop);
                StopTime destinationStopTime = findStopTime(stopTimes, bestTrip, destinationStop);

                System.out.println("Origin Station: " + originStop.getStopName());
                System.out.println("Destination Station: " + destinationStop.getStopName());
                System.out.println("Departure Time: " + originStopTime.getDepartureTime());
                System.out.println("Arrival Time: " + destinationStopTime.getArrivalTime());
                System.out.println("Travel Time: " + travelTime + " minutes");

                double fare = FareCalculator.calculateFare(bestTrip, fareRules, fares, originStop, destinationStop);
                if (fare != -1) {
                    System.out.println("Fare: " + fare + " currency units");
                } else {
                    System.out.println("No valid fare found.");
                }
            } else {
                System.out.println("Could not calculate travel time.");
            }
        } else {
            System.out.println("No trip found.");
        }

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

        // 如果公交发车时间早于用户出发时间，说明需要等到第二天的公交
        if (busTime.isBefore(userTime)) {
            busTime = busTime.plusHours(24); // 加24小时处理跨天情况
        }

        // 计算时间差（以分钟为单位）
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
