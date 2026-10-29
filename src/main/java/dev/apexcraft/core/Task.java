package dev.apexcraft.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CancellationException;

public final class Task {
    public enum Status { PENDING, RUNNING, BLOCKED, SUCCESS, FAILED, CANCELLED }
    public record Event(String id, Status status, String reason) {}
    private final String id;
    private Action action;
    private final Consumer<Event> events;
    private final List<Task> children = new ArrayList<>();
    private Task parent;
    private Cancellation token = new Cancellation();
    private Status status = Status.PENDING;
    private String reason;
    private int ticks, retries;
    private final int maxTicks, maxRetries, priority;

    public Task(String id, Action action, Consumer<Event> events, int priority, int maxTicks, int maxRetries) {
        if (maxTicks < 1 || maxRetries < 0) throw new IllegalArgumentException("Invalid task budgets");
        this.id=id; this.action=action; this.events=events; this.priority=priority;
        this.maxTicks=maxTicks; this.maxRetries=maxRetries;
    }
    public String id() { return id; }
    public int priority() { return priority; }
    public Status status() { return status; }
    public String reason() { return reason; }
    public List<Task> children() { return List.copyOf(children); }
    public boolean terminal() { return status==Status.SUCCESS || status==Status.FAILED || status==Status.CANCELLED; }
    public void addChild(Task child) {
        if (status!=Status.PENDING || child.status!=Status.PENDING || child.parent!=null)
            throw new IllegalStateException("Only unowned pending children can be attached");
        for (Task node=this; node!=null; node=node.parent)
            if (node==child) throw new IllegalArgumentException("Task cycle");
        child.parent=this;
        child.rebind(new Cancellation(token));
        children.add(child);
    }
    private void rebind(Cancellation replacement) {
        token=replacement;
        for (Task child:children) child.rebind(new Cancellation(token));
    }
    private void transition(Status next,String message) {
        status=next; reason=message; events.accept(new Event(id,next,message));
    }
    public boolean canStart() {
        return !terminal() && status!=Status.BLOCKED && !token.cancelled()
            && children.stream().allMatch(t -> t.status==Status.SUCCESS);
    }
    public void tick(Observation world) {
        if (terminal()) return;
        if (token.cancelled()) { cancel(); return; }
        if (children.stream().anyMatch(t -> t.status==Status.FAILED || t.status==Status.CANCELLED)) {
            try { action.cancel(); } finally { transition(Status.FAILED,"Prerequisite failed"); }
            return;
        }
        if (status==Status.BLOCKED || !canStart()) return;
        try {
            token.check();
            if (status==Status.PENDING) { action.start(token); transition(Status.RUNNING,null); }
            ticks++;
            action.tick(world);
            token.check();
            if (action.complete()) {
                boolean verified=action.verify(world);
                action.cancel();
                transition(verified?Status.SUCCESS:Status.BLOCKED,verified?null:"Verification failed");
            } else if (ticks>=maxTicks) {
                action.cancel(); transition(Status.BLOCKED,"Tick budget exceeded");
            }
        } catch (CancellationException error) { cancel(); }
        catch (RuntimeException error) {
            try { action.cancel(); } finally { transition(Status.FAILED,error.toString()); }
        }
    }
    public void suspend() {
        if (status==Status.RUNNING) { action.cancel(); transition(Status.PENDING,null); }
    }
    public void recover(Action replacement) {
        if (status!=Status.BLOCKED || retries>=maxRetries) throw new IllegalStateException("Recovery exhausted");
        action.cancel(); action=replacement; ticks=0; retries++; transition(Status.PENDING,null);
    }
    public void cancel() {
        if (terminal()) return;
        token.cancel();
        RuntimeException failure=null;
        try { action.cancel(); } catch (RuntimeException error) { failure=error; }
        for (Task child:children) {
            try { child.cancel(); } catch (RuntimeException error) {
                if (failure==null) failure=error; else failure.addSuppressed(error);
            }
        }
        transition(Status.CANCELLED,"Cancelled");
        if (failure!=null) throw failure;
    }
}
