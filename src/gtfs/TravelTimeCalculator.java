package gtfs;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

public class TravelTimeCalculator {

    public static long calculateTravelTime(StopTime originStopTime, StopTime destinationStopTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Parse the times
        LocalTime departureTime = LocalTime.parse(originStopTime.getDepartureTime(), formatter);
        LocalTime arrivalTime = LocalTime.parse(destinationStopTime.getArrivalTime(), formatter);

        // Calculate the duration in minutes
        Duration duration = Duration.between(departureTime, arrivalTime);
        return duration.toMinutes();
    }
}

