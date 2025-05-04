package model;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class TransportGraph {
    private Map<String, Stop> stops;
    private Map<String, Route> routes;
    private Map<String, Trip> trips;
    private Map<String, List<StopTime>> stopTimesByStop;

    public List<Connection> findShortestPath(String startStopId, String endStopId, LocalTime startTime) {
        // Chargement initial des données (peut être fait une fois au début)
        loadData();

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
            if (currentNode.stop.getId().equals(endStopId)) {
                return reconstructPath(predecessors, currentNode);
            }

            // 1. Explorer les départs depuis cet arrêt après le temps courant
            List<StopTime> departures = stopTimesByStop.get(currentNode.stop.getId()).stream()
                    .filter(st -> st.departureTime.isAfter(currentNode.time))
                    .collect(Collectors.toList());

            for (StopTime departure : departures) {
                Trip trip = trips.get(departure.tripId);
                List<StopTime> tripStopTimes = trip.getStopTimes();

                // Trouver la position de ce départ dans la séquence du trajet
                int currentSequence = departure.sequence;

                // Explorer les arrêts suivants dans ce trajet
                for (int i = currentSequence; i < tripStopTimes.size(); i++) {
                    StopTime nextStopTime = tripStopTimes.get(i);
                    LocalTime arrivalTime = nextStopTime.departureTime;
                    int travelDuration = (int) ChronoUnit.MINUTES.between(
                            departure.departureTime, arrivalTime);

                    GraphNode nextNode = new GraphNode(nextStopTime.stop, arrivalTime);
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

    private void loadData() {
        // Charger les données depuis les fichiers CSV
        // et construire les structures:
        // stops, routes, trips, stopTimesByStop
    }
}
