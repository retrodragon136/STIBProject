import model.GraphNode;
import model.TransportGraph;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static void main(String[] args) {

        TransportGraph graph = new TransportGraph();
        graph.loadData();

        promptUser(graph);

    }

    private static void promptUser(TransportGraph graph) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Arrêt de départ : ");
        String start = scanner.nextLine().trim();

        System.out.print("Arrêt de destination : ");
        String destination = scanner.nextLine().trim();

        LocalTime departureTime = null;
        boolean validInput = false;

        while (!validInput) {
            System.out.print("Heure de départ (HH:mm) :");
            String timeInput = scanner.nextLine().trim();
            try {
                LocalTime time = LocalTime.parse(timeInput, DateTimeFormatter.ofPattern("HH:mm"));
                departureTime = time;
                validInput = true;
            } catch (DateTimeParseException e) {
                System.out.println("Format invalide. Veuillez entrer l'heure au format HH:mm.");
            }
        }

        // Get walking preferences
        int maxWalkDistance = 500;
        String avoidedTransports = "";

        System.out.println("Avez-vous des préférences de trajet ? (Y/N)");
        String reponse = scanner.nextLine().trim();
        if (reponse.equalsIgnoreCase("Y")) {
            System.out.println("Préférences de marche :");
            // Préférences de marche
            while (true) {
                System.out.print("Distance maximale à pied (mètres, 0-2000) [500] : ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) break;
                try {
                    int value = Integer.parseInt(input);
                    if (value >= 0 && value <= 2000) {
                        maxWalkDistance = value;
                        break;
                    }
                    System.out.println("Veuillez entrer une valeur entre 0 et 2000.");
                } catch (NumberFormatException e) {
                    System.out.println("Veuillez entrer un nombre valide.");
                }
            }
            // Transports à éviter
            System.out.print("Types de transport à éviter (BUS - TRAM - METRO - TRAIN) Laisser vide pour ignorer: ");
            avoidedTransports = scanner.nextLine().trim();
            System.out.println("Préférences enregistrées");
        } else {
            System.out.println("Aucune préférence de trajet sélectionnée.");
        }

        System.out.println("--------------------------------------------------");
        System.out.println("Recherche du chemin entre " + start + " et " + destination + " à " + departureTime);
        graph.updatePreferences(maxWalkDistance, avoidedTransports);
        List<GraphNode> path = graph.findShortestPath(start, destination, departureTime);

        if (path == null || path.isEmpty()) {
            System.out.println("Aucun chemin trouvé entre " + start + " et " + destination + " à " + departureTime);
            return;
        }
        String pathString = graph.formatPath(path);
        System.out.println("\nChemin le plus court trouvé entre " + start + " et " + destination + " à " + departureTime+"\n");

        System.out.println(pathString);
    }

}