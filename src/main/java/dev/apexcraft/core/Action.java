package dev.apexcraft.core;

public interface Action {
    void start(Cancellation token);
    void tick(Observation world);
    boolean complete();
    boolean verify(Observation world);
    /** Idempotently release inputs and abort active interaction. */
    void cancel();
}
