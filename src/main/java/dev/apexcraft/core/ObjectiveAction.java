package dev.apexcraft.core;

import java.util.Map;

/** Replans from fresh inventory after every verified step; no blind multi-step script. */
public final class ObjectiveAction implements Action {
    private final ObjectiveDriver driver;
    private final String item;
    private final int amount;
    private Cancellation token;
    private Action step;
    private boolean done;
    private String resourceTool="";
    private int stepTicks,failures;
    public ObjectiveAction(ObjectiveDriver driver,String item,int amount) {
        if (amount<1 || amount>4096) throw new IllegalArgumentException("Quantity must be 1..4096");
        this.driver=driver; this.item=item; this.amount=amount;
    }
    public void start(Cancellation token) { this.token=token; token.check(); step=null; done=false; stepTicks=0; }
    public void tick(Observation world) {
        token.check();
        if (verify(world)) { done=true; if (step!=null) step.cancel(); driver.releaseInputs(); return; }
        if (step!=null && !resourceTool.isEmpty()
            && ProgressionCatalog.toolsSatisfied(world.inventory()).getOrDefault(resourceTool,0)==0) {
            // If a tool breaks during the mining step, stop holding attack before replanning.
            step.cancel();step=null;
        }
        if (step==null) {
            var plan=ProgressionCatalog.PLANNER.resolve(Map.of(item,amount),
                ProgressionCatalog.toolsSatisfied(driver.planningInventory(world)));
            if (plan.isEmpty()) throw new IllegalStateException("No action for unmet objective");
            var next=plan.getFirst();
            resourceTool=next.kind().equals("collect")?switch(next.item()) {
                case "diamond" -> "iron_pickaxe";case "raw_iron" -> "stone_pickaxe";
                case "coal","cobblestone" -> "wooden_pickaxe";default -> "";
            }:"";
            driver.decision(next.kind()+" "+next.item()+" x"+next.count());
            step=switch (next.kind()) {
                case "collect" -> driver.collect(next.item(),world.inventory().getOrDefault(next.item(),0)+Math.min(next.count(),8));
                case "craft" -> driver.craft(next.item());
                case "smelt" -> driver.smelt(Math.min(next.count(),8));
                default -> throw new IllegalStateException("Unsupported recipe operation");
            };
            step.start(token); stepTicks=0;
        }
        try {
            step.tick(world);
            if (step.complete()) {
                if (!step.verify(world)) throw new IllegalStateException("Step verification failed");
                step.cancel(); step=null;
            } else if (++stepTicks>24000) throw new IllegalStateException("Objective step exceeded 20-minute budget");
        } catch (IllegalStateException failure) {
            step.cancel(); step=null;
            if (++failures>2) throw new IllegalStateException("Recovery exhausted: "+failure.getMessage(),failure);
            driver.decision("Replanning after: "+failure.getMessage());
        }
    }
    public boolean complete() { return done; }
    public boolean verify(Observation world) { return world.inventory().getOrDefault(item,0)>=amount; }
    public void cancel() { try { if (step!=null) step.cancel(); } finally { step=null; driver.releaseInputs(); } }
}
