package model;

import java.time.LocalTime;
import java.util.Objects;

public class GraphNode {

    String stopId;
    LocalTime time;
    double gScore;  // Coût réel depuis le départ
    double fScore;  // gScore + heuristique
    GraphNode parent;
    String tripIdUsed;

    public GraphNode(String stopId, LocalTime time) {
        this.stopId = stopId;
        this.time = time;
        this.gScore = Double.POSITIVE_INFINITY;
        this.fScore = Double.POSITIVE_INFINITY;
        this.parent = null;
        this.tripIdUsed = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GraphNode graphNode = (GraphNode) o;
        return stopId.equals(graphNode.stopId) && time.equals(graphNode.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, time);
    }
}