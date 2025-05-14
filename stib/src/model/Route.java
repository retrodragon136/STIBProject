package model;

public record Route(String routeId,String shortName,String routeLongName,String routeType) {

    public String getRouteId() {
        return routeId;
    }

    public String getShortName() {
        return shortName;
    }

    public String getRouteLongName() {
        return routeLongName;
    }

    public String getRouteType() {
        return routeType;
    }
}
