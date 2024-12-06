package gtfs;

import jp.ac.ut.csis.pflow.routing4.logic.Dijkstra;
import jp.ac.ut.csis.pflow.routing4.logic.linkcost.LinkCost;
import jp.ac.ut.csis.pflow.routing4.res.Network;
import jp.ac.ut.csis.pflow.routing4.res.Route;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static gtfs.GTFSRouter.calculateWaitingTime;
import static gtfs.GTFSRouter.findStopTime;

public class TripFinder {

    public static Trip findConnectingTrip(List<Trip> trips, List<StopTime> stopTimes, Stop originStop, Stop destinationStop, String userDepartureTime) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime userTime = LocalTime.parse(normalizeTime(userDepartureTime), timeFormatter);

//        System.out.println("User departure time: " + userTime);
//        System.out.println("Origin Stop ID: " + originStop.getStopId());
//        System.out.println("Destination Stop ID: " + destinationStop.getStopId());

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

            String normalizedOriginStopId = originStop.getStopId().trim();
            String normalizedDestinationStopId = destinationStop.getStopId().trim();

            for (StopTime stopTime : tripStopTimes) {
                String normalizedStopTimeId = stopTime.getStopId().trim();

                if (normalizedStopTimeId.equals(normalizedOriginStopId)) {
                    String normalizedDepartureTime = normalizeTime(stopTime.getDepartureTime());
                    LocalTime stopDepartureTime = LocalTime.parse(normalizedDepartureTime, timeFormatter);

                    if (stopDepartureTime.isAfter(userTime)) {
                        if (originStopTime == null || stopDepartureTime.isBefore(LocalTime.parse(normalizeTime(originStopTime.getDepartureTime()), timeFormatter))) {
                            originStopTime = stopTime;
                            // System.out.println("Origin stop time updated: " + originStopTime.getDepartureTime());
                        }
                    }
                }
            }

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
                            if (destinationStopTime == null || stopArriveTime.isBefore(LocalTime.parse(normalizeTime(destinationStopTime.getDepartureTime()), timeFormatter))) {
                                destinationStopTime = _stopTime;
                                // System.out.println("Destination stop time updated: " + destinationStopTime.getDepartureTime());
                            }
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

    public static TripResult findBestTrip(Network net, List<Trip> trips, List<StopTime> stopTimes, List<Stop> originCandidates,
                                          List<Stop> destinationCandidates, String userDepartureTime,
                                          double actualOriginLat,
                                          double actualOriginLon,
                                          double actualDestinationLat,
                                          double actualDestinationLon) {
        Trip bestTrip = null;
        Stop bestOriginStop = null;
        Stop bestDestinationStop = null;
        StopTime bestOriginStopTime = null;
        StopTime bestDestinationStopTime = null;
        long shortestTravelTime = Long.MAX_VALUE;

        for (Stop originStop : originCandidates) {
            for (Stop destinationStop : destinationCandidates) {
                // 查找连接 originStop 和 destinationStop 的 trip
                Trip trip = TripFinder.findConnectingTrip(trips, stopTimes, originStop, destinationStop, userDepartureTime);

                if (trip != null) {
                    StopTime originStopTime = findStopTime(stopTimes, trip, originStop);
                    StopTime destinationStopTime = findStopTime(stopTimes, trip, destinationStop);

                    if (originStopTime != null && destinationStopTime != null) {
                        long travelTime = TripFinder.calculateTravelTime(trip, stopTimes, originStop, destinationStop);

                        if (travelTime < shortestTravelTime) {
                            shortestTravelTime = travelTime;
                            bestTrip = trip;
                            bestOriginStop = originStop;
                            bestDestinationStop = destinationStop;
                            bestOriginStopTime = originStopTime;
                            bestDestinationStopTime = destinationStopTime;
                        }
                    }
                }
            }
        }

        if (bestTrip != null && bestOriginStopTime != null && bestDestinationStopTime != null) {
            Stop originStop = bestOriginStop;
            Stop destinationStop = bestDestinationStop;

            // long walkTimeToOriginStation = GeoUtils.calculateWalkingTime(actualOriginLat, actualOriginLon, originStop.getLatitude(), originStop.getLongitude());
            LinkCost linkCost = new LinkCost();
            Dijkstra routing = new Dijkstra(linkCost);
            Route route1 = routing.getRoute(net,	 actualOriginLon, actualOriginLat, originStop.getLongitude(),  originStop.getLatitude());
            long walkTimeToOriginStation = (long) (route1.getLength() / 1.38) / 60;

            // long walkTimeToDestination = GeoUtils.calculateWalkingTime(destinationStop.getLatitude(), destinationStop.getLongitude(), actualDestinationLat, actualDestinationLon);
            Route route2 = routing.getRoute(net,	 destinationStop.getLongitude(), destinationStop.getLatitude(), actualDestinationLon,  actualDestinationLat);
            long walkTimeToDestination = (long) (route2.getLength() / 1.38) / 60;

            long waitingTimeAtStation = calculateWaitingTime(userDepartureTime, bestOriginStopTime.getDepartureTime());

            long timeToOriginStation = Math.max(walkTimeToOriginStation, waitingTimeAtStation);

            long totalTravelTime = timeToOriginStation + shortestTravelTime + walkTimeToDestination;
            double fare = 240;

            return new TripResult(
                    originStop.getStopId()+originStop.getStopName(),
                    destinationStop.getStopId()+destinationStop.getStopName(),
                    bestOriginStopTime.getDepartureTime(),
                    bestDestinationStopTime.getArrivalTime(),
                    totalTravelTime,
                    fare,
                    false
            );
        }

        return null;
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

            Duration duration = Duration.between(departureTime, arrivalTime);
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


