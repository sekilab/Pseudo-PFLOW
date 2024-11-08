package gtfs;

import java.util.List;

public class StopTimeFinder {

    // Method to find a StopTime for a given tripId and stopId
    public static StopTime getStopTimeForTripAndStop(String tripId, String stopId, List<StopTime> stopTimes) {
        for (StopTime stopTime : stopTimes) {
            if (stopTime.getTripId().equals(tripId) && stopTime.getStopId().equals(stopId)) {
                return stopTime;
            }
        }
        return null; // Return null if no matching stop time is found
    }
}

