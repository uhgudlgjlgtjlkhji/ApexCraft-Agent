package dev.apexcraft.core;

import java.util.*;
import java.util.function.Function;

/** Bounded incremental A*; the graph supplies only observed safe moves. */
public final class PathSearch {
    public record Edge(Position position,double cost) {
        public Edge { if (!Double.isFinite(cost) || cost<=0) throw new IllegalArgumentException("Invalid edge cost"); }
    }
    private record Node(Position position,double cost,double estimate,long order) {}
    private final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::estimate).thenComparingLong(Node::order));
    private final Map<Position,Double> costs=new HashMap<>();
    private final Map<Position,Position> parents=new HashMap<>();
    private final Position goal;
    private final Function<Position,List<Edge>> neighbors;
    private final int limit;
    private long sequence;
    private int expanded;
    private List<Position> path;
    private String failure;
    public PathSearch(Position start,Position goal,Function<Position,List<Edge>> neighbors,int limit) {
        if (limit<1) throw new IllegalArgumentException("Invalid node limit");
        this.goal=goal; this.neighbors=neighbors; this.limit=limit;
        costs.put(start,0.0); open.add(new Node(start,0,0,sequence++));
    }
    public boolean done() { return path!=null || failure!=null; }
    public String failure() { return failure; }
    public List<Position> path() { return path; }
    public int expanded() { return expanded; }
    public void step(Cancellation token,int budget) {
        if (budget<1) throw new IllegalArgumentException("Invalid search budget");
        for (int i=0;i<budget && !done();i++) {
            token.check();
            if (open.isEmpty()) { failure="No observed safe route"; return; }
            Node node=open.remove();
            if (node.cost()!=costs.get(node.position())) continue;
            if (node.position().equals(goal)) {
                var result=new ArrayList<Position>(); Position p=goal;
                while (p!=null) { result.add(p); p=parents.get(p); }
                Collections.reverse(result); path=List.copyOf(result); return;
            }
            if (++expanded>limit) { failure="Search node budget exceeded"; return; }
            for (Edge edge:neighbors.apply(node.position())) {
                double candidate=node.cost()+edge.cost();
                if (candidate<costs.getOrDefault(edge.position(),Double.POSITIVE_INFINITY)) {
                    costs.put(edge.position(),candidate); parents.put(edge.position(),node.position());
                    // Dijkstra is A* with a zero admissible heuristic; movement costs stay injectable.
                    open.add(new Node(edge.position(),candidate,candidate,sequence++));
                }
            }
        }
    }
}
