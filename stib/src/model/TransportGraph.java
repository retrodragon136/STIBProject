package model;

import loader.CsvLoader;
import repository.DataRepository;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;


public class TransportGraph {
    private Map<String, Stop> stops;
    private Map<String, Route> routes;
    private Map<String, Trip> trips;
    private Map<String, List<StopTime>> stopTimesByTrip;
    private Map<String, List<StopTime>> stopTimesByStop;
    private Map<String, List<String>> tripsByRoute;
    static String[] agencies = {"STIB"};
    static String basePath = "stib/data/GTFS/";
    public DataRepository repo = new DataRepository();
    Set<GraphNode> visited = new HashSet<>();

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
        String startStopId = repo.getStopByName(startStopName).stopId();
        String endStopId = repo.getStopByName(endStopName).stopId();

        // Get first 5 potential starting trips
        List<String> initialTrips = getInitialTrips(startStopId, startTime);

        List<GraphNode> bestPath = Collections.emptyList();
        double bestTime = Double.MAX_VALUE;

        // Evaluate each potential starting trip
        for (String tripId : initialTrips) {
            PriorityQueue<GraphNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fScore));
            Set<GraphNode> closedSet = new HashSet<>();

            StopTime startStopTime = stopTimesByTrip.getOrDefault(tripId, Collections.emptyList()).stream()
                    .filter(st -> st.stopId().equals(startStopId))
                    .findFirst()
                    .orElse(null);

            if (startStopTime == null) {
                continue; // Skip this trip if no matching StopTime is found
            }
            // Initialize start node for this trip
            GraphNode startNode = new GraphNode(startStopTime, startStopTime.departureTime());
            startNode.gScore = 0;
            startNode.fScore = haversineHeuristic(startStopId, endStopId);
            openSet.add(startNode);

            // Run A* for this starting trip
            List<GraphNode> currentPath = runAStar(openSet, closedSet, endStopId);

            // Keep track of the fastest path
            if (!currentPath.isEmpty()) {
                double pathTime = getPathTotalTime(currentPath);
                if (pathTime < bestTime) {
                    bestTime = pathTime;
                    bestPath = currentPath;
                }
            }
        }

        return bestPath;
    }

    private List<GraphNode> runAStar(PriorityQueue<GraphNode> openSet,
                                     Set<GraphNode> closedSet,
                                     String endStopId) {
        while (!openSet.isEmpty()) {
            GraphNode current = openSet.poll();

            if (repo.getStopById(current.stopTime.stopId()).stopName().equals(repo.getStopById(endStopId).stopName())) {
                return reconstructPath(current);
            }

            closedSet.add(current);

            for (GraphNode neighbor : getNeighbors(current)) {
                if (closedSet.contains(neighbor)) continue;

                double timeDiff = getTimeDifference(current.time, neighbor.time);
                double tentativeGScore = current.gScore + timeDiff;

                if (tentativeGScore < neighbor.gScore) {
                    neighbor.parent = current;
                    neighbor.gScore = tentativeGScore;
                    neighbor.fScore = tentativeGScore + haversineHeuristic(neighbor.stopTime.stopId(), endStopId);

                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private double getPathTotalTime(List<GraphNode> path) {
        if (path.isEmpty()) return Double.MAX_VALUE;
        return getTimeDifference(path.get(0).time, path.get(path.size()-1).time);
    }

    private List<String> getInitialTrips(String stopId, LocalTime time) {
        return stopTimesByStop.getOrDefault(stopId, Collections.emptyList()).stream()
                .filter(st -> !st.departureTime().isBefore(time))
                .sorted(Comparator.comparing(StopTime::departureTime))
                .limit(50) // Get first 50 chronologically
                .map(StopTime::tripId)
                .distinct()
                .collect(Collectors.toList());
    }

    private int getCurrentStopSequence(String tripId, String stopId) {
        return stopTimesByTrip.getOrDefault(tripId, Collections.emptyList()).stream()
                .filter(st -> st.stopId().equals(stopId))
                .mapToInt(StopTime::stopSequence)
                .findFirst()
                .orElse(-1);
    }


    private List<GraphNode> getNeighbors(GraphNode node) {
        List<GraphNode> neighbors = new ArrayList<>();
        Set<String> addedStopIds = new HashSet<>(); // Track already added stops

        // 1. Continue current trip (only next stop)
        List<StopTime> tripStops = stopTimesByTrip.get(node.stopTime.tripId());
        if (tripStops != null) {
            int currentIndex = node.stopTime.stopSequence();

            // Add only the very next stop following the sequence incrementely
            if (currentIndex != -1 && currentIndex < tripStops.size() - 1) {
                StopTime nextStop = tripStops.get(currentIndex + 1);
                if (!nextStop.departureTime().isBefore(node.time) &&
                        !addedStopIds.contains(nextStop.stopId())) {

                    GraphNode neighbor = new GraphNode(nextStop, nextStop.departureTime());
                    neighbors.add(neighbor);
                    addedStopIds.add(nextStop.stopId());
                }
            }
        }

        // 2. Transfer to other trips (only first stop)
        stopTimesByStop.getOrDefault(node.stopTime.stopId(), Collections.emptyList()).stream()
                .filter(st -> !st.tripId().equals(node.stopTime.tripId())) // Different trip
                .filter(st -> !st.departureTime().isBefore(node.time)) // Future departures
                .sorted(Comparator.comparing(StopTime::departureTime)) // Earliest first
                .forEach(st -> {
                    List<StopTime> transferTripStops = stopTimesByTrip.get(st.tripId());
                    if (transferTripStops != null) {
                        int transferIndex = -1;
                        for (int i = 0; i < transferTripStops.size(); i++) {
                            if (transferTripStops.get(i).stopId().equals(node.stopTime.stopId())) {
                                transferIndex = i;
                                break;
                            }
                        }

                        if (transferIndex != -1 && transferIndex < transferTripStops.size() - 1) {
                            StopTime nextTransferStop = transferTripStops.get(transferIndex + 1);
                            if (!addedStopIds.contains(nextTransferStop.stopId())) {
                                GraphNode neighbor = new GraphNode(nextTransferStop, nextTransferStop.departureTime());
                                neighbors.add(neighbor);
                                addedStopIds.add(nextTransferStop.stopId());
                            }
                        }
                    }
                });

        return neighbors;
    }


    private  double getTimeDifference(LocalTime t1, LocalTime t2) {
        return Math.abs(t1.until(t2, java.time.temporal.ChronoUnit.MINUTES));
    }

    private  List<GraphNode> reconstructPath(GraphNode node) {
        List<GraphNode> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node);
            node = node.parent;
        }
        return path;
    }

    private  double haversineHeuristic(String stopId1, String stopId2) {
        double R = 6371; // Rayon de la Terre en kilomètres
        Stop stop1 = stops.get(stopId1);
        Stop stop2 = stops.get(stopId2);

        if (stop1 == null || stop2 == null) {
            return Double.POSITIVE_INFINITY;
        }

        double lat1 = Math.toRadians(stop1.stopLat());
        double lon1 = Math.toRadians(stop1.stopLon());
        double lat2 = Math.toRadians(stop2.stopLat());
        double lon2 = Math.toRadians(stop2.stopLon());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // Distance en km

        // Convertir en temps estimé (minutes) en supposant une vitesse moyenne de 30 km/h
        return (distance / 30) * 60;
    }

    public String formatPath(List<GraphNode> path) {
        if (path.isEmpty()) return "No path found";

        StringBuilder itinerary = new StringBuilder();
        String currentTripId = null;
        String currentRouteType = null;
        String currentRouteName = null;
        Stop currentStop = null;
        LocalTime currentDeparture = null;

        for (int i = 0; i < path.size(); i++) {
            GraphNode node = path.get(i);
            Stop stop = stops.get(node.stopTime.stopId());

            // Handle trip changes or walking segments
            if (currentTripId == null || !currentTripId.equals(node.stopTime.tripId())) {
                // Finish previous segment if exists
                if (currentStop != null && currentDeparture != null) {
                    itinerary.append(String.format(" to %s (%s)\n",
                            currentStop.stopName(),
                            node.time));

                    // Add walking time if needed
                    if (i < path.size() - 1 && node.stopTime.tripId() == null) {
                        itinerary.append(String.format("Walk from %s (%s) ",
                                currentStop.stopName(),
                                node.time));
                        currentDeparture = node.time;
                        currentStop = stop;
                        continue;
                    }
                }

                // Start new segment
                String tripId = node.stopTime.tripId();
                if (tripId != null) {
                    Trip trip = trips.get(tripId);
                    Route route = routes.get(trip.routeId());
                    currentRouteType = getTransportType(route.routeType());
                    currentRouteName = route.shortName();

                    itinerary.append(String.format("Take %s %s %s from %s (%s) ",
                            route.routeId().split("-")[0], // Agency name
                            currentRouteType,
                            currentRouteName,
                            stop.stopName(),
                            node.time));
                }

                currentTripId = tripId;
            }

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


//                System.out.println("Routes: " + DataRoutes.size());
//                System.out.println("Trips: " + DataTrips.size());
//                System.out.println("Stops: " + DataStops.size());
//                System.out.println("StopTimes: " + DataStopTimes.size());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
