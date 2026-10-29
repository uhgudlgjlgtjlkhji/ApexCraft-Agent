package dev.apexcraft.core;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Dependency-free executable tests; assertions are always explicit. */
public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void throwsError(Class<? extends Throwable> type,Runnable action) {
        checks++;
        try { action.run(); } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError("Wrong exception",error);
        }
        throw new AssertionError("Expected "+type.getName());
    }
    private static Observation world(int count) {
        return new Observation(1,new Position(0,0,0),20,20,Map.of("stone",count));
    }
    private static final class TestAction implements Action {
        int steps,stops; boolean done=true,throwCleanup; Cancellation token;
        public void start(Cancellation token) { this.token=token; token.check(); }
        public void tick(Observation world) { token.check(); steps++; }
        public boolean complete() { return done; }
        public boolean verify(Observation world) { return world.inventory().getOrDefault("stone",0)>=1; }
        public void cancel() { stops++; if (throwCleanup) throw new IllegalStateException("Cleanup failed"); }
    }
    private static Task task(String id,TestAction action,int priority) {
        return new Task(id,action,event -> {},priority,2,1);
    }
    public static void main(String[] args) {
        var root=new Cancellation(); var child=new Cancellation(root);
        child.cancel(); check(!root.cancelled(),"Child cannot cancel parent");
        root.cancel(); throwsError(CancellationException.class,() -> new Cancellation(root).check());
        var inventory=new HashMap<String,Integer>(); inventory.put("stone",1);
        var observed=new Observation(1,new Position(0,0,0),20,20,inventory);
        inventory.clear(); check(observed.inventory().get("stone")==1,"Snapshot copies inventory");
        throwsError(UnsupportedOperationException.class,() -> observed.inventory().put("stone",2));
        throwsError(IllegalArgumentException.class,() -> new Observation(1,new Position(0,0,0),Double.NaN,20,Map.of()));
        var action=new TestAction(); var goal=task("verify",action,0);
        goal.tick(world(0)); check(goal.status()==Task.Status.BLOCKED,"Attempt is not success");
        goal.tick(world(1)); check(action.steps==1,"Blocked action cannot spin");
        goal.recover(new TestAction()); goal.tick(world(1)); check(goal.status()==Task.Status.SUCCESS,"Recovery verifies");
        throwsError(IllegalStateException.class,() -> goal.recover(new TestAction()));
        var slow=new TestAction(); slow.done=false;
        var timeout=task("timeout",slow,0); timeout.tick(world(0)); timeout.tick(world(0));
        check(timeout.status()==Task.Status.BLOCKED,"Bounded ticks");
        var parent=task("parent",new TestAction(),0); var prerequisite=task("child",new TestAction(),0);
        parent.addChild(prerequisite);
        throwsError(IllegalArgumentException.class,() -> prerequisite.addChild(parent));
        var scheduler=new Scheduler(() -> {}); scheduler.submit(parent);
        throwsError(IllegalArgumentException.class,() -> scheduler.submit(prerequisite));
        scheduler.tick(world(1)); check(prerequisite.status()==Task.Status.SUCCESS,"Children run first");
        scheduler.tick(world(1)); check(parent.status()==Task.Status.SUCCESS,"Parent follows verified child");
        var lowAction=new TestAction(); lowAction.done=false;
        var low=task("low",lowAction,0); var priority=new Scheduler(() -> {}); priority.submit(low); priority.tick(world(1));
        var high=task("high",new TestAction(),100); priority.submit(high); priority.tick(world(1));
        check(high.status()==Task.Status.SUCCESS && low.status()==Task.Status.PENDING,"Priority suspension");
        check(lowAction.stops==1,"Preemption releases inputs");
        priority.pause(); priority.tick(world(1)); check(lowAction.steps==1,"Paused means no work");
        priority.resume(); priority.tick(world(1)); check(low.status()==Task.Status.BLOCKED,"Resumed task retains budget");
        var cleanup=new TestAction(); cleanup.throwCleanup=true;
        var broken=task("broken",cleanup,0); var sibling=task("sibling",new TestAction(),0);
        int[] releases={0}; var stopper=new Scheduler(() -> releases[0]++);
        stopper.submit(broken); stopper.submit(sibling);
        throwsError(IllegalStateException.class,stopper::stop);
        check(releases[0]==1 && sibling.status()==Task.Status.CANCELLED,"Stop releases despite cleanup failure");
        testPaths(); testRecipes(); testActions();
        System.out.println("PASS: "+checks+" Java core checks");
    }
    private static void testPaths() {
        Position start=new Position(0,0,0),target=new Position(4,0,0);
        var graph=(java.util.function.Function<Position,List<PathSearch.Edge>>) p -> {
            var edges=new ArrayList<PathSearch.Edge>();
            for (int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Position n=p.add(delta[0],0,delta[1]);
                if (n.x()>=0 && n.x()<=4 && n.z()>=0 && n.z()<=2 && !n.equals(new Position(1,0,0)))
                    edges.add(new PathSearch.Edge(n,1));
            }
            return edges;
        };
        var search=new PathSearch(start,target,graph,100);
        while (!search.done()) search.step(new Cancellation(),1);
        check(search.path().getLast().equals(target),"Obstacle detour");
        check(!search.path().contains(new Position(1,0,0)),"Collision awareness");
        var bounded=new PathSearch(start,target,graph,1); bounded.step(new Cancellation(),100);
        check(bounded.failure()!=null,"Search bounded");
        var cancelled=new Cancellation(); cancelled.cancel();
        throwsError(CancellationException.class,() -> new PathSearch(start,target,graph,100).step(cancelled,1));
        var unreachable=new PathSearch(start,target,p -> List.of(),100); unreachable.step(new Cancellation(),10);
        check(unreachable.failure()!=null,"Unreachable terminates");
    }
    private static void testRecipes() {
        var recipes=Map.of("planks",new RecipePlanner.Recipe(4,Map.of("log",1),Map.of(),"craft"),
            "stick",new RecipePlanner.Recipe(4,Map.of("planks",2),Map.of(),"craft"));
        var planner=new RecipePlanner(recipes,Map.of("log",Map.of()));
        var required=new LinkedHashMap<String,Integer>(); required.put("planks",4); required.put("stick",4);
        var plan=planner.resolve(required,Map.of("log",1));
        check(plan.stream().filter(s -> s.item().equals("log")).mapToInt(RecipePlanner.Step::count).sum()==1,"No double spending");
        check(planner.resolve(Map.of("stick",4),Map.of("stick",4)).isEmpty(),"Reuse existing resources");
        throwsError(IllegalArgumentException.class,() -> planner.resolve(Map.of("unknown",1),Map.of()));
        var cycle=new RecipePlanner(Map.of("a",new RecipePlanner.Recipe(1,Map.of("a",1),Map.of(),"craft")),Map.of());
        throwsError(IllegalArgumentException.class,() -> cycle.resolve(Map.of("a",1),Map.of()));
    }
    private static final class TestDriver implements ResourceDriver {
        Position position=new Position(0,0,0),source=new Position(4,0,0);
        boolean present=true,scanned,stuck,held;
        int count;
        public void beginScan(String item) { scanned=false; }
        public Optional<Position> scan(int budget) { scanned=true; return present?Optional.of(source):Optional.empty(); }
        public boolean scanComplete() { return scanned; }
        public boolean sourcePresent(Position p,String item) { return present; }
        public boolean canReach(Position p) { return p.distance(position)<=1; }
        public void mine(Position p) { present=false; count++; }
        public Position approach(Position p) { return p.add(-1,0,0); }
        public boolean traversable(Position p) { return p.y()==0 && p.z()==0 && p.x()>=0 && p.x()<=4; }
        public List<PathSearch.Edge> neighbors(Position p) {
            return List.of(p.add(1,0,0),p.add(-1,0,0)).stream().filter(this::traversable)
                .map(n -> new PathSearch.Edge(n,1)).toList();
        }
        public void moveToward(Position p) { held=true; if (!stuck) position=p; }
        public void releaseInputs() { held=false; }
        Observation observation(long tick) { return new Observation(tick,position,20,20,Map.of("log",count)); }
    }
    private static void testActions() {
        var driver=new TestDriver(); var move=new MoveAction(driver,new Position(3,0,0));
        var task=new Task("move",move,event -> {},0,300,0);
        for (int tick=0;tick<100 && !task.terminal();tick++) task.tick(driver.observation(tick));
        check(task.status()==Task.Status.SUCCESS,"Observed movement integration");
        check(!driver.held,"Movement releases on success");
        var stalled=new TestDriver(); stalled.stuck=true;
        var stuckTask=new Task("stuck",new MoveAction(stalled,new Position(3,0,0)),event -> {},0,300,0);
        for (int tick=0;tick<300 && !stuckTask.terminal();tick++) stuckTask.tick(stalled.observation(tick));
        check(stuckTask.status()==Task.Status.FAILED || stuckTask.status()==Task.Status.BLOCKED,"Stuck terminates");
        check(!stalled.held,"Stuck cleanup releases");
        var miner=new TestDriver();
        var collect=new Task("collect",new CollectAction(miner,"log",1),event -> {},0,100,0);
        for (int tick=0;tick<100 && !collect.terminal();tick++) collect.tick(miner.observation(tick));
        check(collect.status()==Task.Status.SUCCESS && miner.count==1,"Collection observes actual inventory delta");
        var absent=new TestDriver(); absent.present=false;
        var missing=new Task("missing",new CollectAction(absent,"log",1),event -> {},0,100,0);
        missing.tick(absent.observation(1));
        check(missing.status()==Task.Status.FAILED,"Missing resource is not success");
        var cancelled=new TestDriver();
        var cancelling=new Task("cancel",new MoveAction(cancelled,new Position(3,0,0)),event -> {},0,100,0);
        cancelling.tick(cancelled.observation(1)); cancelling.cancel();
        Position old=cancelled.position; cancelling.tick(cancelled.observation(2));
        check(!cancelled.held && cancelled.position.equals(old),"Cancel stops future movement");
    }

}
