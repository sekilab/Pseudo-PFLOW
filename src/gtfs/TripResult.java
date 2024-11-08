package gtfs;

public class TripResult {
    private String originStation;
    private String destinationStation;
    private String departureTime;
    private String arrivalTime;
    private long totalTravelTime;  // 包含步行时间的总行程时间，单位为分钟
    private double fare;  // 票价

    // 构造函数
    public TripResult(String originStation, String destinationStation, String departureTime, String arrivalTime, long totalTravelTime, double fare) {
        this.originStation = originStation;
        this.destinationStation = destinationStation;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.totalTravelTime = totalTravelTime;
        this.fare = fare;
    }

    // Getter 方法
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

    public double getFare() {
        return fare;
    }

    // 你可以根据需要添加更多方法，例如格式化输出等
}
