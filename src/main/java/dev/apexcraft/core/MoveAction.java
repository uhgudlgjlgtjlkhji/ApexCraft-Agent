package dev.apexcraft.core;

import java.util.*;

public final class MoveAction implements Action {
    private final MovementDriver driver;
    private final Position target;
    private final Set<Position> blacklist=new HashSet<>();
    private Cancellation token;
    private PathSearch search;
    private final ArrayDeque<Position> path=new ArrayDeque<>();
    private Position last;
    private int stalled,replans;
    private boolean complete;
    public MoveAction(MovementDriver driver,Position target) { this.driver=driver; this.target=target; }
    public int replans() { return replans; }
    public void start(Cancellation token) {
        this.token=token; token.check(); search=null; path.clear(); last=null; stalled=0; complete=false;
    }
    private void replan(Position bad) {
        driver.releaseInputs();
        if (++replans>4) throw new IllegalStateException("Stuck recovery exhausted");
        if (bad!=null) blacklist.add(bad);
        path.clear(); search=null; stalled=0;
    }
    public void tick(Observation world) {
        token.check();
        Position position=world.position();
        if (position.equals(target)) { complete=true; driver.releaseInputs(); return; }
        if (position.equals(path.peekFirst())) { path.removeFirst(); stalled=0; }
        if (last!=null && !last.equals(position)) {
            stalled=0;
            if (!path.isEmpty() && position.distance(path.peekFirst())>2) replan(null);
        } else stalled++;
        last=position;
        if (path.isEmpty()) {
            if (search==null) search=new PathSearch(position,target,p -> driver.neighbors(p).stream()
                .filter(edge -> !blacklist.contains(edge.position())).toList(),8192);
            search.step(token,128);
            if (search.failure()!=null) throw new IllegalStateException(search.failure());
            if (!search.done()) return;
            path.addAll(search.path()); path.removeFirst(); search=null;
        }
        if (path.isEmpty()) return;
        Position waypoint=path.peekFirst();
        if (!driver.traversable(waypoint) || stalled>=40) { replan(waypoint); return; }
        driver.moveToward(waypoint);
    }
    public boolean complete() { return complete; }
    public boolean verify(Observation world) { return world.position().equals(target); }
    public void cancel() { driver.releaseInputs(); search=null; path.clear(); }
}
