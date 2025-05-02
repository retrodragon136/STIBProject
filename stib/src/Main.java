import loader.CsvLoader;
import model.*;
import repository.DataRepository;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Scanner;

public class Main {
    static String[] agencies = {"STIB", "SNCB", "TEC", "DELIJN"};

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Arrêt de départ : ");
        String start = scanner.nextLine().trim();

        System.out.print("Arrêt de destination : ");
        String destination = scanner.nextLine().trim();

        Duration departureTime;
        boolean validInput = false;

        while (!validInput) {
            System.out.print("Heure de départ (HH:mm) :");
            String timeInput = scanner.nextLine().trim();
            try {
                LocalTime time = LocalTime.parse(timeInput, DateTimeFormatter.ofPattern("HH:mm"));
                departureTime = Duration.between(LocalTime.MIN, time);
                System.out.println("Durée en secondes depuis minuit : " + departureTime.getSeconds());
                validInput = true;
            } catch (DateTimeParseException e) {
                System.out.println("Format invalide. Veuillez entrer l'heure au format HH:mm.");
            }
        }

        String basePath = "stib/data/GTFS/";
        try {
            for (String agency : agencies) {
                System.out.println("\n== Loading " + agency + " ==");

                List<Route> routes = CsvLoader.loadRoutes(basePath + agency + "/routes.csv");
                List<Trip> trips = CsvLoader.loadTrips(basePath + agency + "/trips.csv");
                List<Stop> stops = CsvLoader.loadStops(basePath + agency + "/stops.csv");
                List<StopTime> stopTimes = CsvLoader.loadStopTimes(basePath + agency + "/stop_times.csv");
                DataRepository repo = new DataRepository();
                repo.indexStops(stops);
                repo.indexRoutes(routes);
                repo.indexTrips(trips);
                repo.indexStopTimes(stopTimes);

                System.out.println("Routes: " + routes.size());
                System.out.println("Trips: " + trips.size());
                System.out.println("Stops: " + stops.size());
                System.out.println("StopTimes: " + stopTimes.size());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
