package model;

import java.time.LocalTime;
import java.util.Objects;

public class GraphNode {

    StopTime stopTime;
    LocalTime time;
    double gScore;  // Coût réel depuis le départ
    double fScore;  // gScore + heuristique
    GraphNode parent;

    public GraphNode(StopTime stopTime, LocalTime time) {
        this.stopTime = stopTime;
        this.time = time;
        this.gScore = Double.POSITIVE_INFINITY;
        this.fScore = Double.POSITIVE_INFINITY;
        this.parent = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphNode graphNode = (GraphNode) o;
        return stopTime.stopId().equals(graphNode.stopTime.stopId()) && time.equals(graphNode.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopTime.stopId(), time);
    }
}