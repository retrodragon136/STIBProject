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
    private Map<String, List<StopTime>> stopTimesByStop;
    static String[] agencies = {"STIB", "SNCB", "TEC", "DELIJN"};
    static String basePath = "stib/data/GTFS/";
    public DataRepository repo = null;

    public List<Connection> findShortestPath(String startStopId, String endStopId, LocalTime startTime) {

        // Initialisation
        PriorityQueue<GraphNode> queue = new PriorityQueue<>(Comparator.comparing(n -> n.time));
        Map<GraphNode, Integer> distances = new HashMap<>();
        Map<GraphNode, Connection> predecessors = new HashMap<>();

        // Noeud initial
        Stop startStop = stops.get(startStopId);
        GraphNode startNode = new GraphNode(startStop, startTime);
        queue.add(startNode);
        distances.put(startNode, 0);

        while (!queue.isEmpty()) {
            GraphNode currentNode = queue.poll();
            int currentDistance = distances.get(currentNode);

            // Si on a atteint la destination
            if (currentNode.stop.getStopId().equals(endStopId)) {
                return reconstructPath(predecessors, currentNode);
            }

            // 1. Explorer les départs depuis cet arrêt après le temps courant
            List<StopTime> departures = stopTimesByStop.get(currentNode.stop.getStopId()).stream()
                    .filter(st -> st.departureTime().isAfter(currentNode.time))
                    .toList();

            for (StopTime departure : departures) {
                Trip trip = trips.get(departure.getTripId());
                List<StopTime> tripStopTimes = repo.getStopTimesByTripId(trip.tripId());

                // Trouver la position de ce départ dans la séquence du trajet
                int currentSequence = departure.getStopSequence();

                // Explorer les arrêts suivants dans ce trajet
                for (int i = currentSequence; i < tripStopTimes.size(); i++) {
                    StopTime nextStopTime = tripStopTimes.get(i);
                    LocalTime arrivalTime = nextStopTime.departureTime();
                    int travelDuration = (int)  java.time.Duration.between(
                            departure.departureTime(), arrivalTime).toMinutes();

                    GraphNode nextNode = new GraphNode(repo.getStopById(nextStopTime.getStopId()), arrivalTime);
                    int newDistance = currentDistance + travelDuration;

                    if (!distances.containsKey(nextNode) || newDistance < distances.get(nextNode)) {
                        distances.put(nextNode, newDistance);
                        Connection connection = new Connection(
                                currentNode, nextNode, travelDuration, trip);
                        predecessors.put(nextNode, connection);
                        queue.add(nextNode);
                    }
                }
            }

            // 2. Optionnel: Ajouter des possibilités de marche entre arrêts proches
            // (implémenter selon besoins)
        }

        return Collections.emptyList(); // Aucun chemin trouvé
    }

    private List<Connection> reconstructPath(Map<GraphNode, Connection> predecessors, GraphNode endNode) {
        LinkedList<Connection> path = new LinkedList<>();
        GraphNode current = endNode;

        while (predecessors.containsKey(current)) {
            Connection connection = predecessors.get(current);
            path.addFirst(connection);
            current = connection.from;
        }

        return path;
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

                System.out.println("Routes: " + DataRoutes.size());
                System.out.println("Trips: " + DataTrips.size());
                System.out.println("Stops: " + DataStops.size());
                System.out.println("StopTimes: " + DataStopTimes.size());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
