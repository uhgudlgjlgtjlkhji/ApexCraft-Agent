package dev.apexcraft.core;

import java.util.*;

public final class ProgressionTests {
    private static int checks;
    private static void check(boolean condition,String message) { checks++;if (!condition) throw new AssertionError(message); }
    private static final class Driver implements ObjectiveDriver {
        Map<String,Integer> inventory=new HashMap<>();
        List<String> executed=new ArrayList<>();
        boolean allowed=true,waiting,cancelled,waitingForLog;
        int ticks;
        public Map<String,Integer> planningInventory(Observation world) { return world.inventory(); }
        Action action(Runnable operation,String name) {
            return new Action() {
                boolean done;
                public void start(Cancellation token) { token.check(); }
                public void tick(Observation world) { if (!waiting && !(waitingForLog && name.equals("collect:log")) && !done) { operation.run();executed.add(name);done=true; } }
                public boolean complete() { return done; }
                public boolean verify(Observation world) { return done; }
                public void cancel() { cancelled=true; }
            };
        }
        public Action collect(String item,int total) {
            return action(() -> {
                if (!allowed) throw new IllegalStateException("No resource available");
                var stock=ProgressionCatalog.toolsSatisfied(inventory);
                String tool=switch(item) {case "diamond" -> "iron_pickaxe";case "raw_iron" -> "stone_pickaxe";
                    case "coal","cobblestone" -> "wooden_pickaxe";default -> "";};
                if (!tool.isEmpty() && stock.getOrDefault(tool,0)<1) throw new AssertionError("Mined without correct prerequisite: "+item);
                inventory.put(item,total);
            },"collect:"+item);
        }
        public Action craft(String item) {
            return action(() -> {
                for (String ingredient:ProgressionCatalog.CRAFTS.get(item).grid()) if (!ingredient.isEmpty()) {
                    check(inventory.getOrDefault(ingredient,0)>0,"No missing crafting ingredients");
                    inventory.merge(ingredient,-1,Integer::sum);
                }
                inventory.merge(item,ProgressionCatalog.CRAFTS.get(item).output(),Integer::sum);
            },"craft:"+item);
        }
        public Action smelt(int amount) {
            return action(() -> {
                check(inventory.getOrDefault("furnace",0)>0,"Furnace before smelting");
                check(inventory.getOrDefault("raw_iron",0)>=amount,"Raw iron available");
                check(inventory.getOrDefault("coal",0)>0,"Fuel available");
                inventory.merge("raw_iron",-amount,Integer::sum);inventory.merge("coal",-1,Integer::sum);
                inventory.merge("iron_ingot",amount,Integer::sum);
            },"smelt");
        }
        public void releaseInputs() { cancelled=true; }
        public void decision(String message) {}
        Observation observation() { return new Observation(++ticks,new Position(0,0,0),20,20,inventory); }
    }
    private static void run(Driver driver,ObjectiveAction objective) {
        objective.start(new Cancellation());
        for (int i=0;i<1000 && !objective.complete();i++) objective.tick(driver.observation());
        check(objective.complete(),"Objective completes");
        check(objective.verify(driver.observation()),"Observed final inventory verifies");
    }
    public static int run() {
        var fresh=new Driver();run(fresh,new ObjectiveAction(fresh,"diamond",3));
        check(fresh.executed.indexOf("craft:iron_pickaxe")<fresh.executed.indexOf("collect:diamond"),"Iron pickaxe before diamonds");
        check(fresh.executed.contains("craft:wooden_pickaxe") && fresh.executed.contains("craft:stone_pickaxe") && fresh.executed.contains("smelt"),"Full dependency chain");
        var equipped=new Driver();equipped.inventory.put("iron_pickaxe",1);
        run(equipped,new ObjectiveAction(equipped,"diamond",1));
        check(equipped.executed.equals(List.of("collect:diamond")),"Reuse existing iron pickaxe without recrafting");
        var upgrade=new Driver();upgrade.inventory.put("netherite_pickaxe",1);
        run(upgrade,new ObjectiveAction(upgrade,"diamond",1));
        check(upgrade.executed.equals(List.of("collect:diamond")),"Better tool satisfies requirement");
        var specific=new Driver();specific.inventory.put("diamond_pickaxe",1);
        run(specific,new ObjectiveAction(specific,"iron_pickaxe",1));
        check(specific.inventory.getOrDefault("iron_pickaxe",0)==1,"Explicit tool request creates exact item despite better tool");
        var broken=new Driver();broken.inventory.put("iron_pickaxe",1);broken.waiting=true;
        var repair=new ObjectiveAction(broken,"diamond",1);repair.start(new Cancellation());
        repair.tick(broken.observation());broken.inventory.remove("iron_pickaxe");broken.waiting=false;
        for (int i=0;i<1000 && !repair.complete();i++) repair.tick(broken.observation());
        check(repair.complete() && broken.executed.contains("craft:iron_pickaxe"),"Broken pickaxe causes prerequisite replan");
        var updated=new Driver();updated.waitingForLog=true;
        var dynamic=new ObjectiveAction(updated,"diamond",1);dynamic.start(new Cancellation());
        dynamic.tick(updated.observation());
        updated.inventory.put("iron_pickaxe",1);
        for (int i=0;i<30 && !dynamic.complete();i++) dynamic.tick(updated.observation());
        check(dynamic.complete() && !updated.executed.contains("collect:log"),"Newly available pickaxe supersedes an obsolete log acquisition");
        var ready=new Driver();ready.inventory.put("diamond",4);
        run(ready,new ObjectiveAction(ready,"diamond",3));check(ready.executed.isEmpty(),"Do not overcollect");
        var cancelled=new Driver();cancelled.waiting=true;
        var cancellation=new Cancellation();var objective=new ObjectiveAction(cancelled,"diamond",1);
        objective.start(cancellation);objective.tick(cancelled.observation());cancellation.cancel();
        try { objective.tick(cancelled.observation());throw new AssertionError("Cancellation ignored"); }
        catch (java.util.concurrent.CancellationException expected) { objective.cancel(); }
        check(cancelled.cancelled && cancelled.executed.isEmpty(),"Cancellation releases pending step");
        var absent=new Driver();absent.allowed=false;
        objective=new ObjectiveAction(absent,"diamond",1);objective.start(new Cancellation());
        boolean failed=false;
        try { for (int i=0;i<20;i++) objective.tick(absent.observation()); }
        catch (IllegalStateException expected) { failed=true; }
        check(failed,"Missing resources exhaust bounded recovery");
        int[] clicks={0};List<Boolean> states=new ArrayList<>();var button=new HeldButton(() -> clicks[0]++,states::add);
        button.update(true);button.update(true);button.update(false);button.update(true);button.update(false);
        check(clicks[0]==2,"Only rising mouse edges click");check(!states.getLast(),"Mouse hold releases");
        check(Math.abs(LookSmoother.step(179,-179,12,true)-179)<=2,"Yaw wraps through shortest direction");
        check(LookSmoother.step(0,180,12,false)==12,"Visible turn bounded per tick");
        check(LookSmoother.difference(990,-90,true)==0,"Positive full camera turns preserve alignment");
        check(LookSmoother.difference(-810,-90,true)==0,"Negative full camera turns preserve alignment");
        check(GoalRequest.parse("get diamonds").equals(new GoalRequest("diamond",1)),"Natural diamond objective");
        check(GoalRequest.parse("Collect 64 diamonds.").equals(new GoalRequest("diamond",64)),"Quantity and punctuation");
        check(GoalRequest.parse("iron pickaxe").item().equals("iron_pickaxe"),"Tool names with spaces");
        return checks;
    }
}
