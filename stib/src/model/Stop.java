package model;

public record Stop(String stopId,String stopName,double stopLat,double stopLon) {

    public String getStopId() { return stopId; }
}
