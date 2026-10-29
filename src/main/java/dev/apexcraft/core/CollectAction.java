package dev.apexcraft.core;

/** Nearby block-item collection; completion is inventory-based, not block-breaking-based. */
public final class CollectAction implements Action {
    private final ResourceDriver driver;
    private final String item;
    private final int quantity;
    private Cancellation token;
    private Position source;
    private MoveAction movement;
    private boolean complete;
    private int stale;
    public CollectAction(ResourceDriver driver,String item,int quantity) {
        if (quantity<1 || quantity>100000) throw new IllegalArgumentException("Quantity out of range");
        this.driver=driver; this.item=item; this.quantity=quantity;
    }
    public void start(Cancellation token) {
        this.token=token; token.check(); source=null; movement=null; stale=0; complete=false;
        driver.beginScan(item);
    }
    public void tick(Observation world) {
        token.check();
        if (verify(world)) { complete=true; driver.releaseInputs(); return; }
        if (source==null) {
            source=driver.scan(128).orElse(null);
            if (source==null) {
                if (driver.scanComplete()) throw new IllegalStateException("No nearby observed source: "+item);
                return;
            }
        }
        if (movement!=null) {
            movement.tick(world);
            if (movement.complete()) { movement.cancel(); movement=null; }
            return;
        }
        if (!driver.sourcePresent(source,item)) {
            if (world.position().distance(source)<=1.5) {
                // Give ordinary item pickup a bounded time to arrive in inventory.
                if (++stale<20) return;
                source=null; driver.beginScan(item); return;
            }
            movement=new MoveAction(driver,driver.approach(source)); movement.start(token); return;
        }
        if (!driver.canReach(source)) {
            movement=new MoveAction(driver,driver.approach(source)); movement.start(token); return;
        }
        driver.mine(source);
    }
    public boolean complete() { return complete; }
    public boolean verify(Observation world) { return world.inventory().getOrDefault(item,0)>=quantity; }
    public void cancel() { if (movement!=null) movement.cancel(); driver.releaseInputs(); }
}
