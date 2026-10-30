package dev.apexcraft.client;

import dev.apexcraft.core.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Delegates terrain search/recovery to Baritone; only observations can complete the action. */
public final class BaritoneAction implements Action {
    private final BaritoneSession session;
    private final Consumer<BaritoneSession> launch;
    private final Predicate<Observation> predicate;
    private Cancellation token;
    private boolean done;
    public BaritoneAction(BaritoneSession session,Consumer<BaritoneSession> launch,Predicate<Observation> predicate) {
        this.session=session; this.launch=launch; this.predicate=predicate;
    }
    public void start(Cancellation token) {
        this.token=token; token.check(); done=false; session.start(); launch.accept(session);
    }
    public void tick(Observation world) { token.check(); done=predicate.test(world); }
    public boolean complete() { return done; }
    public boolean verify(Observation world) { return predicate.test(world); }
    public void cancel() { session.stop(); }
}
