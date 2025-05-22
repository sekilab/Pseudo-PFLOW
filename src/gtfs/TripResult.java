package gtfs;

public class TripResult {
    private final long walkTime;
    private String originStation;
    private String destinationStation;
    private String departureTime;
    private String arrivalTime;
    private long totalTravelTime;
    private double fare;  // 票价
    private boolean usedTransit;

    public TripResult(String originStation, String destinationStation, String departureTime, String arrivalTime, long totalTravelTime, long walkTime, double fare, boolean usedTransit) {
        this.originStation = originStation;
        this.destinationStation = destinationStation;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.totalTravelTime = totalTravelTime;
        this.walkTime = walkTime;
        this.fare = fare;
        this.usedTransit = usedTransit;
    }

    public String getOriginStation() {
        return originStation;
    }

    public String getDestinationStation() {
        return destinationStation;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public long getTotalTravelTime() {
        return totalTravelTime;
    }

    public long getWalkTime() {
        return walkTime;
    }

    public double getFare() {
        return fare;
    }

    public boolean isUsedTransit() { return usedTransit; }

    @Override
    public String toString() {
        return "Origin Station: " + originStation + "\n" +
                "Destination Station: " + destinationStation + "\n" +
                "Departure Time: " + departureTime + "\n" +
                "Arrival Time: " + arrivalTime + "\n" +
                "Total Time: " + totalTravelTime + " minutes\n" +
                "Fare: " + fare + " currency units"+
                "Used Transit: " + usedTransit;
    }
}
