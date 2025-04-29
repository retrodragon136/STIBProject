package model;

import java.time.Duration;

public record StopTime(String tripId, Duration departureTime, String stopId, int stopSequence) {
}
