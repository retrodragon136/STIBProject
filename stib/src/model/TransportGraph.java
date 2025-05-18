package model;

import loader.CsvLoader;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Classe principale représentant le graphe de transport.
 * Cette classe contient les données et les méthodes nécessaires pour
 * modéliser un réseau de transport, calculer des itinéraires, et gérer
 * les préférences utilisateur.
 */
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
    private int maxWalkingDistance = 2000; // meters
    private String avoidedTransport = "";

    public TransportGraph() {
        this.stops = new HashMap<>();
        this.routes = new HashMap<>();
        this.trips = new HashMap<>();
        this.stopTimesByTrip = new HashMap<>();
        this.stopTimesByStop = new HashMap<>();
        this.tripsByRoute = new HashMap<>();
    }

    /**
     * Met à jour les préférences de l'utilisateur pour le calcul des chemins.
     *
     * @param maxWalkingDistance La distance maximale de marche en mètres que l'utilisateur est prêt à parcourir.
     * @param avoidedTransport   Le type de transport que l'utilisateur souhaite éviter (par exemple, "BUS", "TRAM").
     */
    public void updatePreferences(int maxWalkingDistance, String avoidedTransport) {
        this.maxWalkingDistance = maxWalkingDistance;
        this.avoidedTransport = avoidedTransport;
    }

    /**
     * Trouve le chemin le plus court entre deux arrêts en utilisant une approche A*.
     *
     * @param startStopName Le nom de l'arrêt de départ.
     * @param endStopName   Le nom de l'arrêt d'arrivée.
     * @param startTime     L'heure de départ pour commencer le trajet.
     * @return Une liste de `GraphNode` représentant le chemin le plus court, ou une liste vide si aucun chemin n'est trouvé.
     */
    public List<GraphNode> findShortestPath(String startStopName, String endStopName, LocalTime startTime) {
        // Get all possible stop IDs for start/end stops
        List<Stop> startStops = stops.values().stream()
                .filter(stop -> stop.stopName().equalsIgnoreCase(startStopName))
                .collect(Collectors.toList());
        List<Stop> endStops = stops.values().stream()
                .filter(stop -> stop.stopName().equalsIgnoreCase(endStopName))
                .collect(Collectors.toList());

        if (startStops.isEmpty() || endStops.isEmpty()) {
            return Collections.emptyList();
        }

        // Create super start/end nodes
        GraphNode superStart = new GraphNode(null, startTime);

        // Initialize priority queue
        PriorityQueue<GraphNode> openSet = new PriorityQueue<>(
                Comparator.comparing((GraphNode node) -> node.fScore)  // Primary: earliest arrival
                        .thenComparing(node -> node.connectionCount)      // Secondary: fewer transfers
                        .thenComparing(node -> node.time)                 // Tertiary: consistency
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
                                .filter(st -> st.departureTime().isBefore(startTime.plus(Duration.ofMinutes(60)))) // Limit to 1 hour
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
                    if(!avoidedTransport.isEmpty()) {
                        String routeType = routes.get(neighbor.stopTime.tripId()).routeType();
                        if (avoidedTransport.equalsIgnoreCase(routeType)) {
                            neighbor.fScore += 30; // Penalize avoided transport
                        }
                    }

                    if (!openSet.contains(neighbor)  || openSet.contains(neighbor) &&
                            neighbor.fScore < current.fScore) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Récupère une liste des identifiants de trajets (`tripId`) disponibles pour un arrêt donné
     * à partir d'une heure spécifique. Ces trajets constitueront les différents points de départ de l'algorithme de
     * recherche.
     *
     * @param stopId L'identifiant de l'arrêt pour lequel récupérer les trajets.
     * @param time   L'heure à partir de laquelle les trajets doivent être considérés.
     * @return Une liste des identifiants de trajets (`tripId`) triés par heure de départ,
     *         limités à 50 résultats distincts.
     */
    private List<String> getInitialTrips(String stopId, LocalTime time) {
        return stopTimesByStop.getOrDefault(stopId, Collections.emptyList()).stream()
                .filter(st -> !st.departureTime().isBefore(time))
                .sorted(Comparator.comparing(StopTime::departureTime))
                .limit(50)
                .map(StopTime::tripId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Ajoute une association entre un identifiant d'arrêt (`stopId`) et une route donnée
     * dans une map. Si l'identifiant d'arrêt n'existe pas encore dans la map, une nouvelle
     * liste est créée pour cet identifiant.
     *
     * @param map    La map contenant les associations entre les identifiants d'arrêt et les routes.
     * @param stopId L'identifiant de l'arrêt à associer à une route.
     * @param route  La route à associer à l'identifiant d'arrêt.
     */
    private void addStopRouteCombo(Map<String, List<String>> map, String stopId, String route) {
        map.computeIfAbsent(stopId, k -> new ArrayList<>()).add(route);
    }


    /**
     * Récupère les voisins d'un nœud donné dans le graphe de transport.
     * Cela inclut les arrêts suivants sur le même trajet, les transferts vers d'autres trajets
     * et les connexions piétonnes vers des arrêts à proximité.
     *
     * @param node Le nœud pour lequel récupérer les voisins.
     * @return Une liste de nœuds voisins.
     */
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
                    GraphNode neighbor = new GraphNode(nextStop, nextStop.departureTime(), node.connectionCount);
                    neighbors.add(neighbor);
                    addStopRouteCombo(addedStopRouteCombos, nextStop.stopId(), trips.get(nextStop.tripId()).routeId());
                }
            }
        }

        // 2. Transfer to other trips on same stop
        neighbors.addAll(getTransitNeighbors(node, currentRouteId));

        // 3. Walking connections to nearby stops
        neighbors.addAll(getWalkingNeighbors(node));

        return neighbors;
    }

    /**
     * Récupère les voisins de transit d'un nœud donné, c'est-à-dire les arrêts de transfert
     * vers d'autres trajets à partir du même arrêt.
     *
     * @param node          Le nœud pour lequel récupérer les voisins de transit.
     * @param currentRouteId L'identifiant de la route actuelle pour éviter les transferts sur la même route.
     * @return Une liste de nœuds voisins de transit.
     */
    private List<GraphNode> getTransitNeighbors(GraphNode node, String currentRouteId) {
        List<GraphNode> neighbors = new ArrayList<>();
        Map<String, List<StopTime>> routeToStopTimes =
                transfersByStopAndRoute.get(node.stopTime.stopId());

        if (routeToStopTimes != null) {
            routeToStopTimes.forEach((routeId, stopTimes) -> {
                if (!routeId.equals(currentRouteId)) {
                    stopTimes.stream()
                            .filter(st -> !st.departureTime().isBefore(node.time))
                            .min(Comparator.comparing(StopTime::departureTime))
                            .ifPresent(earliest -> neighbors.add(new GraphNode(earliest, earliest.departureTime(), node.connectionCount)));
                }
            });
        }
        return neighbors;
    }

    /**
     * Récupère les voisins de marche d'un nœud donné, c'est-à-dire les arrêts à proximité
     * accessibles à pied depuis l'arrêt actuel.
     *
     * @param node Le nœud pour lequel récupérer les voisins de marche.
     * @return Une liste de nœuds voisins de marche.
     */
    private List<GraphNode> getWalkingNeighbors(GraphNode node) {
        List<GraphNode> walkingNeighbors = new ArrayList<>();
        Stop currentStop = stops.get(node.stopTime.stopId());

        // Get all stops within walking distance
        List<Stop> nearbyStops = findNearbyStops(currentStop, maxWalkingDistance);

        for (Stop nearbyStop : nearbyStops) {
            // Calculate walking time in seconds
            int walkingTimeSeconds = calculateWalkingTime(currentStop, nearbyStop);

            // Find the earliest possible departure at the target stop after walking
            LocalTime arrivalTime = node.time.plusSeconds(walkingTimeSeconds);

            stopTimesByStop.getOrDefault(nearbyStop.stopId(), Collections.emptyList()).stream()
                    .filter(st -> !st.departureTime().isBefore(arrivalTime)) // Only future departures
                    .min(Comparator.comparing(StopTime::departureTime)) // Get earliest departure
                    .ifPresent(earliestDeparture -> {
                        GraphNode neighbor = new GraphNode(earliestDeparture, earliestDeparture.departureTime(), node.connectionCount + 1);
                        neighbor.parent = node;
                        neighbor.gScore = node.gScore + walkingTimeSeconds/60.0; // Convert to minutes for consistency
                        walkingNeighbors.add(neighbor);
                    });
        }

        return walkingNeighbors;
    }

    /**
     * Trouve les arrêts à proximité d'un arrêt donné dans un rayon spécifié.
     *
     * @param currentStop  L'arrêt actuel.
     * @param radiusMeters Le rayon de recherche en mètres.
     * @return Une liste d'arrêts à proximité.
     */
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

    /**
     * Calcule le temps de marche entre deux arrêts en utilisant la distance Haversine.
     *
     * @param fromStop L'arrêt de départ.
     * @param toStop   L'arrêt d'arrivée.
     * @return Le temps de marche en secondes.
     */
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

    /**
     * Calcule la distance entre deux points géographiques en utilisant la formule Haversine.
     *
     * @param lat1 Latitude du premier point.
     * @param lon1 Longitude du premier point.
     * @param lat2 Latitude du deuxième point.
     * @param lon2 Longitude du deuxième point.
     * @return La distance en mètres.
     */
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


    /**
     * Calcule la différence de temps entre deux heures.
     *
     * @param t1 La première heure.
     * @param t2 La deuxième heure.
     * @return La différence de temps en minutes.
     */
    private  double getTimeDifference(LocalTime t1, LocalTime t2) {
        return Math.abs(t1.until(t2, java.time.temporal.ChronoUnit.MINUTES));
    }

    /**
     * Reconstruit le chemin à partir d'un nœud donné jusqu'à la racine.
     *
     * @param node Le nœud de départ pour reconstruire le chemin.
     * @return Une liste de nœuds représentant le chemin reconstruit.
     */
    private List<GraphNode> reconstructPath(GraphNode node) {
        List<GraphNode> path = new ArrayList<>();
        int connectionCount = node.connectionCount;  // Capture final count
        while (node != null) {
            if (node.stopTime != null) {
                path.add(0, node);
                node.connectionCount = connectionCount--;  // Backpropagate count
            }
            node = node.parent;
        }
        return path;
    }

    /**
     * Calcule une heuristique de distance entre un arrêt donné et une liste d'arrêts de destination.
     * Utilise la distance Haversine pour estimer le temps de trajet.
     *
     * @param stopId    L'identifiant de l'arrêt de départ.
     * @param endStops  La liste des arrêts de destination.
     * @return La distance estimée en minutes.
     */
    private double haversineHeuristic(String stopId, List<Stop> endStops) {
        return endStops.stream()
                .mapToDouble(end -> haversineDistance(stopId, end.stopId()))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Calcule la distance entre deux arrêts en utilisant la formule Haversine.
     *
     * @param stopId1 L'identifiant du premier arrêt.
     * @param stopId2 L'identifiant du deuxième arrêt.
     * @return La distance estimée en minutes.
     */
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

    /**
     * Formate le chemin trouvé en une chaîne de caractères lisible.
     *
     * @param path La liste des nœuds représentant le chemin trouvé.
     * @return Une chaîne formatée représentant l'itinéraire.
     */
    public String formatPath(List<GraphNode> path) {
        if (path.isEmpty()) return "No path found";

        StringBuilder itinerary = new StringBuilder();
        String currentTripId = null;
        Stop currentStop = null;
        LocalTime currentDeparture = null;

        List<GraphNode> filteredPath = path.stream()
                .filter(node -> node.stopTime != null)
                .collect(Collectors.toList());

        for (int i = 0; i < filteredPath.size(); i++) {
            // Determine if we should keep this node
            boolean keepNode = true;

            if (i > 0 && i < filteredPath.size() - 1) {
                GraphNode currentNode = filteredPath.get(i);
                GraphNode prevNode = filteredPath.get(i - 1);
                GraphNode nextNode = filteredPath.get(i + 1);

                String prevNodeName = stops.get(prevNode.stopTime.stopId()).stopName();
                String nextNodeName = stops.get(nextNode.stopTime.stopId()).stopName();

                boolean sameStopNameAsPrev = stops.get(currentNode.stopTime.stopId()).stopName().equals(prevNodeName);
                boolean sameStopNameAsNext = stops.get(currentNode.stopTime.stopId()).stopName().equals(nextNodeName);
                boolean sameTripAsPrev = currentNode.stopTime.tripId().equals(prevNode.stopTime.tripId());
                boolean sameTripAsNext = currentNode.stopTime.tripId().equals(nextNode.stopTime.tripId());

                boolean differentStopIdAsPrev = !currentNode.stopTime.stopId().equals(prevNode.stopTime.stopId());
                boolean differentStopIDAsNext = !currentNode.stopTime.stopId().equals(nextNode.stopTime.stopId());

                if (differentStopIdAsPrev && differentStopIDAsNext && !sameTripAsPrev && !sameTripAsNext) {
                    i++;  // Skip next iteration
                }

                // Skip if:
                // 1. Same stop as previous and next
                // 2. Different trip from previous
                // 3. Different trip from next
                if (sameStopNameAsPrev && sameStopNameAsNext &&
                        !sameTripAsPrev && !sameTripAsNext) {
                    keepNode = false;
                }

            }

            GraphNode node = filteredPath.get(i);
            Stop stop = stops.get(node.stopTime.stopId());
            String nodeTripId = node.stopTime.tripId();

            if (!keepNode) {
                continue;
            }

            // Check for walking segments (for all nodes except last)
            if (i < filteredPath.size() - 1) {
                GraphNode nextNode = filteredPath.get(i + 1);
                Stop nextStop = stops.get(nextNode.stopTime.stopId());

                // Walking conditions:
                // 1. Different physical stops
                // 2. Different trips
                if (!stop.stopName().equals(nextStop.stopName()) &&
                        !nodeTripId.equals(nextNode.stopTime.tripId())) {

                    long walkMinutes = Duration.between(node.time, nextNode.time).toMinutes();

                    // First complete the current transit segment
                    if (currentTripId != null) {
                        itinerary.append(String.format(" to %s (%s)\n",
                                stop.stopName(),
                                node.time));
                    }
                    // Then show walking segment
                    itinerary.append(String.format("Walk from %s (%s) to %s (%s) (%d min walk)\n",
                            stop.stopName(), node.time,
                            nextStop.stopName(), nextNode.time,
                            walkMinutes));

                    // If walking to final destination, stop here
                    if (i + 1 == filteredPath.size() - 1) {
                        return itinerary.toString();
                    }

                    // Reset current trip tracking
                    currentTripId = null;
                    currentStop = null;
                    currentDeparture = null;
                    continue;
                }
            }

            // Handle transit segments
            if (currentTripId == null || !currentTripId.equals(nodeTripId)) {
                // Finish previous segment if exists
                if (currentStop != null && currentDeparture != null) {
                    itinerary.append(String.format(" to %s (%s)\n",
                            currentStop.stopName(),
                            currentDeparture));
                }

                // Start new transit segment
                Trip trip = trips.get(nodeTripId);
                Route route = routes.get(trip.routeId());

                itinerary.append(String.format("Take %s %s %s from %s (%s) ",
                        route.routeId().split("-")[0],
                        route.routeType(),
                        route.shortName(),
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

    /**
     * Pré-calculer les transferts entre les arrêts et les trajets.
     * Cette méthode remplit la map `transfersByStopAndRoute` pour faciliter
     * la recherche de transferts lors du calcul des itinéraires.
     */
    void precomputeTransfers() {
        transfersByStopAndRoute = new HashMap<>();
        stopTimesByStop.forEach((stopId, stopTimes) -> {
            Map<String, List<StopTime>> byRoute = stopTimes.stream()
                    .collect(Collectors.groupingBy(
                            st -> trips.get(st.tripId()).routeId()
                    ));
            transfersByStopAndRoute.put(stopId, byRoute);
        });
    }

    /**
     * Charge les données GTFS à partir des fichiers CSV.
     * Cette méthode parcourt les agences spécifiées et charge les données
     * dans les structures de données appropriées.
     */
    public void loadData() {
        try {
            for (String agency : agencies) {
                System.out.println("\n== Loading " + agency + " ==");

                List<Route> DataRoutes = CsvLoader.loadRoutes(basePath + agency + "/routes.csv");
                List<Trip> DataTrips = CsvLoader.loadTrips(basePath + agency + "/trips.csv");
                List<Stop> DataStops = CsvLoader.loadStops(basePath + agency + "/stops.csv");
                List<StopTime> DataStopTimes = CsvLoader.loadStopTimes(basePath + agency + "/stop_times.csv");

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

            precomputeTransfers();
            System.out.println("\n== Data loaded successfully ==");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}