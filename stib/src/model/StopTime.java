package model;

import java.time.Duration;
import java.time.LocalTime;

public record StopTime(String tripId, int departureTime /** minutes depuis minuit (peut dépasser 1440)**/, String stopId, int stopSequence) {

    // Conversion depuis LocalTime (accepte 25:30 etc.)
    public static int toExtendedTime(String timeStr) {
        String[] parts = timeStr.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        return hours * 60 + minutes;
    }

    // Conversion vers LocalTime pour affichage
    public LocalTime toLocalTime() {
        int normalizedHours = (departureTime / 60) % 24;
        int normalizedMinutes = departureTime % 60;
        return LocalTime.of(normalizedHours, normalizedMinutes);
    }
}
