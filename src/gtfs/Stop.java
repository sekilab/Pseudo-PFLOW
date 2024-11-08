package gtfs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Stop {
    private String stopId;
    private String stopName;
    private String platformCode;
    private double latitude;
    private double longitude;
    private String zoneId;
    private int locationType;

    // Constructor
    public Stop(String stopId, String stopName, String platformCode, double latitude, double longitude, String zoneId, int locationType) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.platformCode = platformCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
        this.locationType = locationType;
    }

    public Stop() {
    }

    // Getters and Setters
    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public void setStopName(String stopName) {
        this.stopName = stopName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "Stop{" +
                "stopId='" + stopId + '\'' +
                ", stopName='" + stopName + '\'' +
                ", platformCode='" + platformCode + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", zoneId='" + zoneId + '\'' +
                ", locationType=" + locationType +
                '}';
    }
}

