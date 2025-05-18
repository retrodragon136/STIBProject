package model;

import loader.CsvLoader;
import repository.DataRepository;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;


public class TransportGraph {
    private Map<String, Stop> stops;
    private Map<String, Route> routes;
    private Map<String, Trip> trips;
    private Map<String, Map<String, List<StopTime>>> transfersByStopAndRoute;
    private Map<String, List<StopTime>> stopTimesByTrip;
    private Map<String, List<StopTime>> stopTimesByStop;
    private Map<String, List<String>> tripsByRoute;
    static String[] agencies = {"STIB", "TEC", "SNCB", "DELIJN"};
    static String basePath = "stib/data/GTFS/";
    public DataRepository repo = new DataRepository();

    public TransportGraph() {
        this.stops = new HashMap<>();
        this.routes = new HashMap<>();
        this.trips = new HashMap<>();
        this.stopTimesByTrip = new HashMap<>();
        this.stopTimesByStop = new HashMap<>();
        this.tripsByRoute = new HashMap<>();
    }

    // Méthode principale pour trouver le chemin
    public List<GraphNode> findShortestPath(String startStopName, String endStopName, LocalTime startTime) {
        // Get all possible stop IDs for start/end
        List<Stop> startStops = repo.getStopsByName(startStopName);
        List<Stop> endStops = repo.getStopsByName(endStopName);

        if (startStops.isEmpty() || endStops.isEmpty()) {
            return Collections.emptyList();
        }

        // Create super start/end nodes
        GraphNode superStart = new GraphNode(null, startTime);
        GraphNode superEnd = new GraphNode(null, LocalTime.MAX);

        // Initialize priority queue
        PriorityQueue<GraphNode> openSet = new PriorityQueue<>(
                Comparator.comparing((GraphNode node) -> node.fScore)  // Primary sort by A* heuristic
                        .thenComparing(node -> node.time)       // Secondary sort by time
        );
        Set<GraphNode> closedSet = new HashSet<>();

        // Add all possible starting points
        for (Stop startStop : startStops) {
            getInitialTrips(startStop.stopId(), startTime).stream()
                    .map(tripId -> {
                        // Find the existing StopTime for this trip and stop
                        StopTime existingStopTime = stopTimesByTrip.getOrDefault(tripId, Collections.emptyList()).stream()
                                .filter(st -> st.stopId().equals(startStop.stopId()))
                                .filter(st -> !st.departureTime().isBefore(startTime))
                                .filter(st -> st.departureTime().isBefore(startTime.plus(Duration.ofMinutes(20)))) // Limit to 1 hour
                                .findFirst()
                                .orElse(null);

                        if (existingStopTime != null) {
                            GraphNode node = new GraphNode(existingStopTime, existingStopTime.departureTime());
                            node.gScore = 0;
                            node.fScore = haversineHeuristic(startStop.stopId(), endStops);
                            node.parent = superStart;
                            return node;
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .forEach(openSet::add);
        }

        while (!openSet.isEmpty()) {
            GraphNode current = openSet.poll();

            // Check if reached any end stop (excluding super nodes)
            if (current.stopTime != null && endStops.stream()
                    .anyMatch(end -> end.stopId().equals(current.stopTime.stopId()))) {
                // First solution found is guaranteed earliest arrival
                return reconstructPath(current);
            }

            closedSet.add(current);
            List<GraphNode> neighbors = getNeighbors(current);
            for (GraphNode neighbor : neighbors) {
                if (closedSet.contains(neighbor)) continue;

                double timeDiff = getTimeDifference(current.time, neighbor.time);
                double tentativeGScore = current.gScore + timeDiff;

                if (tentativeGScore < neighbor.gScore) {
                    neighbor.parent = current;
                    neighbor.gScore = tentativeGScore;
                    neighbor.fScore = tentativeGScore +
                            haversineHeuristic(neighbor.stopTime.stopId(), endStops);

                    if (!openSet.contains(neighbor)  || openSet.contains(neighbor) &&
                            neighbor.fScore < current.fScore) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> getInitialTrips(String stopId, LocalTime time) {
        return stopTimesByStop.getOrDefault(stopId, Collections.emptyList()).stream()
                .filter(st -> !st.departureTime().isBefore(time))
                .sorted(Comparator.comparing(StopTime::departureTime))// Get first 50 chronologically
                .map(StopTime::tripId)
                .distinct()
                .collect(Collectors.toList());
    }

    // Helper method to add pairs
    private void addStopRouteCombo(Map<String, List<String>> map, String stopId, String route) {
        map.computeIfAbsent(stopId, k -> new ArrayList<>()).add(route);
    }


    private List<GraphNode> getNeighbors(GraphNode node) {
        List<GraphNode> neighbors = new ArrayList<>();
        Map<String, List<String>> addedStopRouteCombos = new HashMap<>();
        String currentRouteId = trips.get(node.stopTime.tripId()).routeId();

        // 1. Continue current trip (only next stop)
        List<StopTime> tripStops = stopTimesByTrip.get(node.stopTime.tripId());
        if (tripStops != null) {
            int currentIndex = node.stopTime.stopSequence();

            // Add only the very next stop following the sequence incrementely
            if (currentIndex != -1 && currentIndex < tripStops.size()) {
                StopTime nextStop = tripStops.get(currentIndex);
                if (!nextStop.departureTime().isBefore(node.time)) {
                    GraphNode neighbor = new GraphNode(nextStop, nextStop.departureTime());
                    neighbors.add(neighbor);
                    addStopRouteCombo(addedStopRouteCombos, nextStop.stopId(), trips.get(nextStop.tripId()).routeId());
                }
            }
        }

        // 2. Transfer to other trips (only first stop)
        neighbors.addAll(getTransitNeighbors(node, currentRouteId, addedStopRouteCombos));

        // 3. Then add walking connections to nearby stops
        neighbors.addAll(getWalkingNeighbors(node));

        return neighbors;
    }

    private List<GraphNode> getTransitNeighbors(GraphNode node, String currentRouteId,
                                                Map<String, List<String>> addedStopRouteCombos)  {
        List<GraphNode> neighbors = new ArrayList<>();
        List<String> stopIdsBySameName = repo.getStopsIdsWithSameName(node.stopTime.stopId());
        for(String stopId : stopIdsBySameName) {
            List<StopTime> stopTimesBy = stopTimesByStop.getOrDefault(stopId, Collections.emptyList()).stream()
                    .filter(st -> {
                        // Only allow transfers to different routes
                        String transferRouteId = trips.get(st.tripId()).routeId();
                        return !transferRouteId.equals(currentRouteId); // Different route check
                    })
                    .filter(st -> !st.departureTime().isBefore(node.time)) // Future departures
                    .filter(st -> st.departureTime().isBefore(node.time.plus(Duration.ofMinutes(30))))
                    .sorted(Comparator.comparing(StopTime::departureTime)).toList(); // Earliest first
            for (StopTime stopTime : stopTimesBy) {
                List<StopTime> transferTripStops = stopTimesByTrip.get(stopTime.tripId());
                if (transferTripStops != null) {
                    int transferIndex = -1;
                    for (int i = 0; i < transferTripStops.size(); i++) {
                        if (transferTripStops.get(i).stopId().equals(stopId)) {
                            transferIndex = i;
                            break;
                        }
                    }

                    if (transferIndex != -1 && transferIndex < transferTripStops.size() - 1) {
                        StopTime nextTransferStop = transferTripStops.get(transferIndex);
                        String nextStopRouteId = trips.get(nextTransferStop.tripId()).routeId();
                        if (isRouteAbsentForStop(addedStopRouteCombos, nextTransferStop.stopId(), nextStopRouteId)) {
                            GraphNode neighbor = new GraphNode(nextTransferStop, nextTransferStop.departureTime());
                            neighbors.add(neighbor);
                            addStopRouteCombo(addedStopRouteCombos, nextTransferStop.stopId(), nextStopRouteId);
                        }

                    }
                }
            }
        }
        return neighbors;
    }

    private List<GraphNode> getWalkingNeighbors(GraphNode node) {
        List<GraphNode> walkingNeighbors = new ArrayList<>();
        Stop currentStop = stops.get(node.stopTime.stopId());

        // Get all stops within walking distance (e.g., 500 meters)
        List<Stop> nearbyStops = findNearbyStops(currentStop, 2000);

        for (Stop nearbyStop : nearbyStops) {
            // Calculate walking time in seconds
            int walkingTimeSeconds = calculateWalkingTime(currentStop, nearbyStop);

            // Find the earliest possible departure at the target stop after walking
            LocalTime arrivalTime = node.time.plusSeconds(walkingTimeSeconds);

            stopTimesByStop.getOrDefault(nearbyStop.stopId(), Collections.emptyList()).stream()
                    .filter(st -> !st.departureTime().isBefore(arrivalTime)) // Only future departures
                    .min(Comparator.comparing(StopTime::departureTime)) // Get earliest departure
                    .ifPresent(earliestDeparture -> {
                        GraphNode neighbor = new GraphNode(earliestDeparture, earliestDeparture.departureTime());
                        neighbor.parent = node;
                        neighbor.gScore = node.gScore + walkingTimeSeconds/60.0; // Convert to minutes for consistency
                        walkingNeighbors.add(neighbor);
                    });
        }

        return walkingNeighbors;
    }

    private List<Stop> findNearbyStops(Stop currentStop, double radiusMeters) {
        List<Stop> nearbyStops = new ArrayList<>();

        for (Stop otherStop : stops.values()) {
            // Skip the same stop
            if (otherStop.stopId().equals(currentStop.stopId())) {
                continue;
            }

            double distance = haversineDistanceMeters(
                    currentStop.stopLat(),
                    currentStop.stopLon(),
                    otherStop.stopLat(),
                    otherStop.stopLon()
            );

            if (distance <= radiusMeters) {
                nearbyStops.add(otherStop);
            }
        }

        return nearbyStops;
    }

    private int calculateWalkingTime(Stop fromStop, Stop toStop) {
        // Calculate distance in meters
        double distance = haversineDistanceMeters(
                fromStop.stopLat(),
                fromStop.stopLon(),
                toStop.stopLat(),
                toStop.stopLon()
        );

        // Average walking speed: 1.4 m/s (about 5 km/h)
        double walkingSpeed = 1.4;

        // Calculate time in seconds and round up
        return (int) Math.ceil(distance / walkingSpeed);
    }

    // Haversine distance calculation in meters
    private double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in kilometers

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        // Convert to meters
        return R * c * 1000;
    }

    private boolean isRouteAbsentForStop(Map<String, List<String>> map,
                                        String stopId, String route) {
        // If key doesn't exist, the route is certainly absent
        if (!map.containsKey(stopId)) {
            return true;
        }
        // Check if route is not in the list
        return !map.get(stopId).contains(route);
    }


    private  double getTimeDifference(LocalTime t1, LocalTime t2) {
        return Math.abs(t1.until(t2, java.time.temporal.ChronoUnit.MINUTES));
    }

    private List<GraphNode> reconstructPath(GraphNode node) {
        List<GraphNode> path = new ArrayList<>();
        while (node != null) {
            if (node.stopTime != null) { // Only add real nodes
                path.add(0, node);
            }
            node = node.parent;
        }
        return path;
    }

    // Modified heuristic to consider all end stops
    private double haversineHeuristic(String stopId, List<Stop> endStops) {
        return endStops.stream()
                .mapToDouble(end -> haversineDistance(stopId, end.stopId()))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private  double haversineDistance(String stopId1, String stopId2) {
        Stop stop1 = stops.get(stopId1);
        Stop stop2 = stops.get(stopId2);

        if (stop1 == null || stop2 == null) {
            return Double.POSITIVE_INFINITY;
        }

        double distanceInKm = haversineDistanceMeters(stop1.stopLat(), stop1.stopLon(), stop2.stopLat(), stop2.stopLon())/1000;

        // Convertir en temps estimé (minutes) en supposant une vitesse moyenne de 30 km/h
        return (distanceInKm / 30) * 60;
    }

    public String formatPath(List<GraphNode> path) {
        if (path.isEmpty()) return "No path found";

        StringBuilder itinerary = new StringBuilder();
        String currentTripId = null;
        String currentRouteType = null;
        String currentRouteName = null;
        Stop currentStop = null;
        LocalTime currentDeparture = null;

        // Filter out super nodes
        List<GraphNode> filteredPath = path.stream()
                .filter(node -> node.stopTime != null)
                .collect(Collectors.toList());

        for (int i = 0; i < filteredPath.size(); i++) {
            GraphNode node = filteredPath.get(i);
            Stop stop = stops.get(node.stopTime.stopId());
            String nodeTripId = node.stopTime.tripId();

            // Determine if we should keep this node
            boolean keepNode = true;

            if (i > 0 && i < filteredPath.size() - 1) {
                GraphNode prevNode = filteredPath.get(i - 1);
                GraphNode nextNode = filteredPath.get(i + 1);

                String prevNodeName = stops.get(prevNode.stopTime.stopId()).stopName();
                String nextNodeName = stops.get(nextNode.stopTime.stopId()).stopName();

                boolean sameStopAsPrev = stops.get(node.stopTime.stopId()).stopName().equals(prevNodeName);
                boolean sameStopAsNext =  stops.get(node.stopTime.stopId()).stopName().equals(nextNodeName);
                boolean sameTripAsPrev = nodeTripId.equals(prevNode.stopTime.tripId());
                boolean sameTripAsNext = nodeTripId.equals(nextNode.stopTime.tripId());

                // Skip if:
                // 1. Same stop as previous and next
                // 2. Different trip from previous
                // 3. Different trip from next
                if (sameStopAsPrev && sameStopAsNext &&
                        !sameTripAsPrev && !sameTripAsNext) {
                    keepNode = false;
                }
            }

            if (!keepNode) {
                continue;
            }

            // Handle trip changes
            if (currentTripId == null || !currentTripId.equals(nodeTripId)) {
                // Finish previous segment if exists
                if (currentStop != null && currentDeparture != null) {
                    itinerary.append(String.format(" to %s (%s)\n",
                            currentStop.stopName(),
                            currentDeparture));
                }

                // Start new segment
                Trip trip = trips.get(nodeTripId);
                Route route = routes.get(trip.routeId());
                currentRouteType = getTransportType(route.routeType());
                currentRouteName = route.shortName();

                itinerary.append(String.format("Take %s %s %s from %s (%s) ",
                        route.routeId().split("-")[0],
                        currentRouteType,
                        currentRouteName,
                        stop.stopName(),
                        node.time));
            }

            currentTripId = nodeTripId;
            currentStop = stop;
            currentDeparture = node.time;
        }

        // Add final destination
        if (currentStop != null) {
            itinerary.append(String.format("to %s (%s)",
                    currentStop.stopName(),
                    currentDeparture));
        }

        return itinerary.toString();
    }

    private String getTransportType(String routeType) {
        return switch(routeType.toUpperCase()) {
            case "TRAIN" -> "TRAIN";
            case "METRO" -> "METRO";
            case "TRAM" -> "TRAM";
            case "BUS" -> "BUS";
            default -> "TRANSPORT";
        };
    }

    public void loadData() {
        try {
            for (String agency : agencies) {
                System.out.println("\n== Loading " + agency + " ==");

                List<Route> DataRoutes = CsvLoader.loadRoutes(basePath + agency + "/routes.csv");
                List<Trip> DataTrips = CsvLoader.loadTrips(basePath + agency + "/trips.csv");
                List<Stop> DataStops = CsvLoader.loadStops(basePath + agency + "/stops.csv");
                List<StopTime> DataStopTimes = CsvLoader.loadStopTimes(basePath + agency + "/stop_times.csv");
                repo.indexStops(DataStops);
                repo.indexRoutes(DataRoutes);
                repo.indexTrips(DataTrips);
                repo.indexStopTimes(DataStopTimes);

                // Remplissage des maps
                for (Route route : DataRoutes) {
                    routes.put(route.routeId(), route);
                }

                for (Trip trip : DataTrips) {
                    trips.put(trip.tripId(), trip);
                    tripsByRoute.computeIfAbsent(trip.routeId(), k -> new ArrayList<>()).add(trip.tripId());
                }

                for (Stop stop : DataStops) {
                    stops.put(stop.stopId(), stop);
                }

                for (StopTime stopTime : DataStopTimes) {
                    stopTimesByTrip.computeIfAbsent(stopTime.tripId(), k -> new ArrayList<>()).add(stopTime);
                    stopTimesByStop.computeIfAbsent(stopTime.stopId(), k -> new ArrayList<>()).add(stopTime);
                }

                stopTimesByTrip.values().forEach(list ->
                        list.sort(Comparator.comparingInt(StopTime::stopSequence)));



            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
