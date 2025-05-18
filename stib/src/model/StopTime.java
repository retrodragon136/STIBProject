package model;

import java.time.Duration;
import java.time.LocalTime;

public record StopTime(String tripId, LocalTime departureTime /** minutes depuis minuit (peut dépasser 1440)**/, String stopId, int stopSequence) {

}
