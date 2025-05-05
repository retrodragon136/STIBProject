package loader;

import model.*;
import java.io.*;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

public class CsvLoader {

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean insideQuote = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '\"') {
                insideQuote = !insideQuote;
            } else if (c == ',' && !insideQuote) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        result.add(current.toString().trim());
        return result;
    }

    public static LocalTime parseExtendedTime(String timeStr) {
        String[] parts = timeStr.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);

        if(hours < 23) {
            return LocalTime.of(hours, minutes, seconds);
        }

        // Adjust hours to be within 0-23 range
        hours = hours % 24;
        return LocalTime.of(hours, minutes, seconds);
    }

    public static List<Route> loadRoutes(String file) throws IOException {
        List<Route> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            List<String> p = parseCsvLine(line);
            if (p.size() >= 4) {
                list.add(new Route(p.get(0), p.get(1), p.get(2), p.get(3)));
            }
        }
        br.close();
        return list;
    }

    public static List<Trip> loadTrips(String file) throws IOException {
        List<Trip> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            List<String> p = parseCsvLine(line);
            if (p.size() >= 2) {
                list.add(new Trip(p.get(0), p.get(1)));
            }
        }
        br.close();
        return list;
    }

    public static List<Stop> loadStops(String file) throws IOException {
        List<Stop> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            List<String> p = parseCsvLine(line);
            if (p.size() >= 4) {
                String stopId = p.get(0);
                String stopName = p.get(1);
                double stopLat = Double.parseDouble(p.get(2));
                double stopLon = Double.parseDouble(p.get(3));
                list.add(new Stop(stopId, stopName, stopLat, stopLon));
            }
        }
        br.close();
        return list;
    }

    public static List<StopTime> loadStopTimes(String file) throws IOException {
        List<StopTime> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            List<String> p = parseCsvLine(line);
            if (p.size() >= 4) {
                list.add(new StopTime(p.get(0), parseExtendedTime(p.get(1)), p.get(2), Integer.parseInt(p.get(3))));
            }
        }
        br.close();
        return list;
    }
}
