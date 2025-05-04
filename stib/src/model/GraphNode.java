package model;

import java.time.Duration;
import java.time.LocalTime;

public class GraphNode {

    Stop stop;
    LocalTime time;

    // Pour la reconstruction du chemin
    GraphNode previousNode;
    Trip arrivingTrip;

    public GraphNode(Stop stop, LocalTime time) {
        this.stop = stop;
        this.time = time;
    }

    public Stop getStop() {
        return stop;
    }

    public LocalTime getTime() {
        return time;
    }

    public GraphNode getPreviousNode() {
        return previousNode;
    }

    public void setPreviousNode(GraphNode previousNode) {
        this.previousNode = previousNode;
    }

    public Trip getArrivingTrip() {
        return arrivingTrip;
    }

    public void setArrivingTrip(Trip arrivingTrip) {
        this.arrivingTrip = arrivingTrip;
    }
}
