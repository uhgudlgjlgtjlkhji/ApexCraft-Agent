package dev.apexcraft.core;

import java.util.concurrent.CancellationException;

public final class Cancellation {
    private final Cancellation parent;
    private boolean cancelled;
    public Cancellation() { this(null); }
    public Cancellation(Cancellation parent) { this.parent = parent; }
    public boolean cancelled() { return cancelled || parent != null && parent.cancelled(); }
    public void cancel() { cancelled = true; }
    public void check() { if (cancelled()) throw new CancellationException("Automation stopped"); }
}
