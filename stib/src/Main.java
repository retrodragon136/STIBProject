import model.Connection;
import model.Route;
import model.TransportGraph;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        TransportGraph graph = new TransportGraph();
        graph.loadData();

//        Scanner scanner = new Scanner(System.in);
//        System.out.print("Arrêt de départ : ");
//        String start = scanner.nextLine().trim();
//
//        System.out.print("Arrêt de destination : ");
//        String destination = scanner.nextLine().trim();
//
//        LocalTime departureTime = null;
//        boolean validInput = false;
//
//        while (!validInput) {
//            System.out.print("Heure de départ (HH:mm) :");
//            String timeInput = scanner.nextLine().trim();
//            try {
//                LocalTime time = LocalTime.parse(timeInput, DateTimeFormatter.ofPattern("HH:mm"));
//                departureTime = time;
//                validInput = true;
//            } catch (DateTimeParseException e) {
//                System.out.println("Format invalide. Veuillez entrer l'heure au format HH:mm.");
//            }
//        }

        String start = "Machelen";
        String destination = "Van Cutsem";
        LocalTime departureTime = LocalTime.of(10, 0);


        List<Connection> path = graph.findShortestPath(start, destination, departureTime);
        System.out.println("Chemin le plus court trouvé entre " + start + " et " + destination + " à " + departureTime);

        if (path.isEmpty()) {
            System.out.println("Aucun chemin trouvé.");
        } else {
            for (Connection connection : path) {
                Route route = graph.repo.getRouteByTripId(connection.getTrip().tripId());
                System.out.println("Take " + route.getRouteId().split("-")[0] +" "+ route.getRouteType() +" " + route.getShortName() +
                        " from " + connection.getFrom().getStop().stopName() +
                        " (" + connection.getFrom().getTime().format(DateTimeFormatter.ofPattern("HH:mm")) + ")" +
                        " to " + connection.getTo().getStop().stopName() +
                        " (" + connection.getTo().getTime().format(DateTimeFormatter.ofPattern("HH:mm")) + ").");
            }
        }
    }
}
