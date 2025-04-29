import loader.CsvLoader;
import model.*;
import repository.DataRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.io.*;

public class Main {
    static String[] agencies = {"STIB", "SNCB", "TEC", "DELIJN"};

    public static void main(String[] args) {
        String basePath = "data/GTFS/";

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
