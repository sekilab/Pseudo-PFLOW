package gtfs;

public class StopTime {
    private String tripId;
    private String stopId;
    private String arrivalTime;
    private String departureTime;
    private int stopSequence;

    // Constructor
    public StopTime(String tripId, String stopId, String arrivalTime, String departureTime, int stopSequence) {
        this.tripId = tripId;
        this.stopId = stopId;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.stopSequence = stopSequence;
    }

    public StopTime(){

    }

    // Getters and Setters
    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(int stopSequence) {
        this.stopSequence = stopSequence;
    }

    @Override
    public String toString() {
        return "StopTime{" +
                "tripId='" + tripId + '\'' +
                ", stopId='" + stopId + '\'' +
                ", arrivalTime='" + arrivalTime + '\'' +
                ", departureTime='" + departureTime + '\'' +
                ", stopSequence=" + stopSequence +
                '}';
    }
}

