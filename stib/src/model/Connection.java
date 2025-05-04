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
}
