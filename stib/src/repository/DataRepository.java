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
}
