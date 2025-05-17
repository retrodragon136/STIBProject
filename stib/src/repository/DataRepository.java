package repository;

import model.*;
import java.util.*;

public class DataRepository {
    public final Map<String, Stop> stopsById = new HashMap<>();
    public final Map<String, Route> routesById = new HashMap<>();
    public final Map<String, List<StopTime>> stopTimesByTrip = new HashMap<>();
    public final Map<String, Trip> tripsById = new HashMap<>();

    public void indexStops(List<Stop> stops) {
        for (Stop stop : stops) {
            stopsById.put(stop.stopId(), stop);
        }
    }

    public void indexRoutes(List<Route> routes) {
        for (Route route : routes) {
            routesById.put(route.routeId(), route);
        }
    }

    public void indexTrips(List<Trip> trips) {
        for (Trip trip : trips) {
            tripsById.put(trip.tripId(), trip);
        }
    }

    public void indexStopTimes(List<StopTime> stopTimes) {
        for (StopTime st : stopTimes) {
            stopTimesByTrip.computeIfAbsent(st.tripId(), k -> new ArrayList<>()).add(st);
        }

        for (List<StopTime> list : stopTimesByTrip.values()) {
            list.sort(Comparator.comparingInt(StopTime::stopSequence));
        }
    }

    public List<StopTime> getStopTimesByTripId (String tripId) {
        return stopTimesByTrip.getOrDefault(tripId, Collections.emptyList());
    }

    public Stop getStopById(String stopId) {
        return stopsById.get(stopId);
    }

    public Stop getStopByName(String stopName) {
        for (Stop stop : stopsById.values()) {
            if (stop.stopName().equalsIgnoreCase(stopName)) {
                return stop;
            }
        }
        return null;
    }

    public Trip getTripByStopTime(StopTime stopTime) {
        if (stopTime == null) {
            return null;
        }
        return tripsById.get(stopTime.getTripId());
    }

    public Route getRouteByTripId(String tripId) {
        Trip trip = tripsById.get(tripId);
        if (trip != null) {
            return routesById.get(trip.routeId());
        }
        return null;
    }

    public List<Stop> getStopsByName(String stopName) {
        List<Stop> matchingStops = new ArrayList<>();
        for (Stop stop : stopsById.values()) {
            if (stop.stopName().equalsIgnoreCase(stopName)) {
                matchingStops.add(stop);
            }
        }
        return matchingStops;
    }

    public List<String> getStopsIdsWithSameName(String stopId) {
        List<String> matchingStops = new ArrayList<>();
        for (Stop stop : stopsById.values()) {
            if (stop.stopName().equalsIgnoreCase(stopsById.get(stopId).stopName())) {
                matchingStops.add(stop.stopId());
            }
        }
        return matchingStops;
    }
}
