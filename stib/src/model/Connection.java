package model;

public class Connection {
    GraphNode from;
    GraphNode to;
    int duration; // en minutes
    Trip trip;

    public Connection(GraphNode from, GraphNode to, int duration, Trip trip) {
        this.from = from;
        this.to = to;
        this.duration = duration;
        this.trip = trip;
    }

    public GraphNode getFrom() {
        return from;
    }

    public GraphNode getTo() {
        return to;
    }

    public int getDuration() {
        return duration;
    }

    public Trip getTrip() {
        return trip;
    }
}
