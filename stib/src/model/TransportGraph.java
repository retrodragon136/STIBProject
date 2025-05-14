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
    private Map<String, List<StopTime>> stopTimesByStop;
    static String[] agencies = {"STIB"};
    static String basePath = "stib/data/GTFS/";
    public DataRepository repo = new DataRepository();
    Set<GraphNode> visited = new HashSet<>();

    public TransportGraph() {
        this.stops = new HashMap<>();
        this.routes = new HashMap<>();
        this.trips = new HashMap<>();
        this.stopTimesByStop = new HashMap<>();
    }

    public List<Connection> findShortestPath(String startStopName, String endStopName, LocalTime startTime) {

        // Initialisation
        PriorityQueue<GraphNode> queue = new PriorityQueue<>(Comparator.comparing(n -> n.time));
        Map<GraphNode, Integer> distances = new HashMap<>();
        Map<GraphNode, Connection> predecessors = new HashMap<>();

        // Noeud initial
        Stop startStop = repo.getStopByName(startStopName);
        Stop endStop = repo.getStopByName(endStopName);
        GraphNode startNode = new GraphNode(startStop, startTime);
        queue.add(startNode);
        distances.put(startNode, 0);

        while (!queue.isEmpty()) {
            GraphNode currentNode = queue.poll();
            int currentDistance = distances.get(currentNode);

            // Si on a atteint la destination
            if (currentNode.stop.stopId().equals(endStop.stopId())) {
                return reconstructPath(predecessors, currentNode);
            }

            // Marquer le nœud comme visité
            visited.add(currentNode);

            // Explorer les départs depuis cet arrêt après le temps courant
            List<StopTime> departures = stopTimesByStop.get(currentNode.stop.stopId()).stream()
                    .filter(st -> !st.departureTime().isBefore(startTime)) // Filtrer les départs avant startTime
                    .filter(st -> st.departureTime().isAfter(currentNode.time)) // Départs après l'heure actuelle
                    .sorted(Comparator.comparing(StopTime::departureTime))
                    .limit(5)
                    .toList();

            for (StopTime departure : departures) {
                Trip trip = repo.getTripByStopTime(departure);
                List<StopTime> tripStopTimes = repo.getStopTimesByTripId(trip.tripId());

                // Trouver la position de ce départ dans la séquence du trajet
                int currentSequence = departure.getStopSequence();

                // Explorer les arrêts suivants dans ce trajet
                for (int i = currentSequence + 1; i < tripStopTimes.size(); i++) {
                    StopTime nextStopTime = tripStopTimes.get(i);

                    // Vérifier que le prochain StopTime est sur le même Trip
                    if (!nextStopTime.tripId().equals(departure.tripId())) {
                        continue;
                    }

                    // Vérifier que le numéro de séquence est valide
                    if (nextStopTime.getStopSequence() <= currentSequence) {
                        continue;
                    }

                    LocalTime arrivalTime = nextStopTime.departureTime();
                    int travelDuration = (int) Duration.between(departure.departureTime(), arrivalTime).toMinutes();

                    GraphNode nextNode = new GraphNode(repo.getStopById(nextStopTime.getStopId()), arrivalTime);
                    int newDistance = currentDistance + travelDuration;

                    if (!visited.contains(nextNode) && (!distances.containsKey(nextNode) || newDistance < distances.get(nextNode))) {
                        distances.put(nextNode, newDistance);
                        Connection connection = new Connection(currentNode, nextNode, travelDuration, trip);
                        predecessors.put(nextNode, connection);
                        queue.add(nextNode);
                    }
                }
            }
        }

            // 2. Optionnel: Ajouter des possibilités de marche entre arrêts proches
            // (implémenter selon besoins)

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
                for (StopTime st : DataStopTimes) {
                    stopTimesByStop.computeIfAbsent(st.stopId(), k -> new ArrayList<>()).add(st);
                }

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
