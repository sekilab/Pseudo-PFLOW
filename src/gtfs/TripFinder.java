package gtfs;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TripFinder {

    public static Trip findConnectingTrip(List<Trip> trips, List<StopTime> stopTimes, Stop originStop, Stop destinationStop, String userDepartureTime) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime userTime = LocalTime.parse(normalizeTime(userDepartureTime), timeFormatter);

//        System.out.println("User departure time: " + userTime);
//        System.out.println("Origin Stop ID: " + originStop.getStopId());
//        System.out.println("Destination Stop ID: " + destinationStop.getStopId());

        // Loop over each trip
        for (Trip trip : trips) {
            StopTime originStopTime = null;
            StopTime destinationStopTime = null;

            // Filter stop times for the current trip
            List<StopTime> tripStopTimes = new ArrayList<>();
            for (StopTime stopTime : stopTimes) {
                if (stopTime.getTripId().equals(trip.getTripId())) {
                    tripStopTimes.add(stopTime);
                }
            }

            // Normalize the stop IDs before comparison
            String normalizedOriginStopId = originStop.getStopId().trim();
            String normalizedDestinationStopId = destinationStop.getStopId().trim();

            // Step 1: Find the origin stop time
            for (StopTime stopTime : tripStopTimes) {
                String normalizedStopTimeId = stopTime.getStopId().trim();

                // Check if this stop matches the origin stop
                if (normalizedStopTimeId.equals(normalizedOriginStopId)) {
                    String normalizedDepartureTime = normalizeTime(stopTime.getDepartureTime());
                    LocalTime stopDepartureTime = LocalTime.parse(normalizedDepartureTime, timeFormatter);

                    // Only consider trips that depart after the user-specified time
                    if (stopDepartureTime.isAfter(userTime)) {
                        originStopTime = stopTime;
                        // System.out.println("Origin stop time found: " + originStopTime.getDepartureTime());
                        // Break out of loop once origin stop is found
                        // System.out.println("Breaking out of origin stop loop for trip: " + trip.getTripId());
                        break;
                    }
                }
            }

            // Step 2: Find the destination stop time (after the origin stop is found)
            if (originStopTime!=null){
                for (StopTime _stopTime : tripStopTimes) {
                    String normalizedStopTimeId = _stopTime.getStopId().trim();

                    // Check if this stop matches the destination stop
                    if (normalizedStopTimeId.equals(normalizedDestinationStopId)) {

                        String normalizedDepartureTime = normalizeTime(originStopTime.getDepartureTime());
                        LocalTime stopDepartureTime = LocalTime.parse(normalizedDepartureTime, timeFormatter);

                        String normalizedArriveTime = normalizeTime(_stopTime.getDepartureTime());
                        LocalTime stopArriveTime = LocalTime.parse(normalizedArriveTime, timeFormatter);

                        if(stopArriveTime.isAfter(stopDepartureTime)){
                            destinationStopTime = _stopTime;
                            break;
                        }
                        // System.out.println("Destination stop time found: " + destinationStopTime.getArrivalTime() + " for stop ID: " + normalizedStopTimeId);
                        // break; // Break out of loop once destination stop is found
                    }
                }
            }

            // If both origin and destination stops are found and in the correct sequence
            if (originStopTime != null && destinationStopTime != null &&
                    originStopTime.getStopSequence() < destinationStopTime.getStopSequence()) {
//                System.out.println("Valid trip found with origin stop sequence: " + originStopTime.getStopSequence() +
//                        " and destination stop sequence: " + destinationStopTime.getStopSequence());
                return trip;  // Return the first valid trip found
            }
        }

        return null;  // No valid trip found
    }

    public static Trip findBestTrip(List<Trip> trips, List<StopTime> stopTimes, List<Stop> originCandidates, List<Stop> destinationCandidates, String userDepartureTime) {
        Trip bestTrip = null;
        long shortestTravelTime = Long.MAX_VALUE;

        for (Stop originStop : originCandidates) {

            for (Stop destinationStop : destinationCandidates) {

                Trip trip = TripFinder.findConnectingTrip(trips, stopTimes, originStop, destinationStop, userDepartureTime);

                if (trip != null) {

                    long travelTime = calculateTravelTime(trip, stopTimes, originStop, destinationStop);

                    if (travelTime < shortestTravelTime) {
                        shortestTravelTime = travelTime;
                        bestTrip = trip;
                    }
                }
            }
        }

        return bestTrip;
    }

    public static long calculateTravelTime(Trip trip, List<StopTime> stopTimes, Stop originStop, Stop destinationStop) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm:ss");
        StopTime originStopTime = null;
        StopTime destinationStopTime = null;

        for (StopTime stopTime : stopTimes) {
            if (stopTime.getTripId().equals(trip.getTripId())) {
                if (stopTime.getStopId().equals(originStop.getStopId())) {
                    originStopTime = stopTime;
                }
                if (stopTime.getStopId().equals(destinationStop.getStopId())) {
                    destinationStopTime = stopTime;
                }
            }
        }

        if (originStopTime != null && destinationStopTime != null) {
            LocalTime departureTime = LocalTime.parse(originStopTime.getDepartureTime(), timeFormatter);
            LocalTime arrivalTime = LocalTime.parse(destinationStopTime.getArrivalTime(), timeFormatter);

            if (arrivalTime.isBefore(departureTime)) {
                arrivalTime = arrivalTime.plusHours(24);
            }

            Duration duration = Duration.between(arrivalTime, departureTime);
            return duration.toMinutes();
        }

        return -1;
    }


    public static String normalizeTime(String time) {
        String[] parts = time.split(":");
        if (parts[0].length() == 1) {
            parts[0] = "0" + parts[0];  // Add leading zero to hours if needed
        }
        return String.join(":", parts);
    }
}


