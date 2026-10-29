package dev.apexcraft.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class Scheduler {
    private final List<Task> tasks=new ArrayList<>();
    private final Runnable release;
    private Task current;
    private boolean paused;
    public Scheduler(Runnable release) { this.release=release; }
    public List<Task> tasks() { return List.copyOf(tasks); }
    public void submit(Task task) {
        List<Task> incoming=new ArrayList<>(); flatten(task,incoming);
        var ids=new HashSet<String>();
        for (Task entry:tasks) ids.add(entry.id());
        for (Task entry:incoming) if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate task ID");
        tasks.addAll(incoming);
    }
    private void flatten(Task task,List<Task> result) {
        result.add(task); for (Task child:task.children()) flatten(child,result);
    }
    public void tick(Observation world) {
        if (paused) return;
        for (Task task:tasks) if (!task.terminal() && task.children().stream().anyMatch(
            child -> child.status()==Task.Status.FAILED || child.status()==Task.Status.CANCELLED)) task.tick(world);
        Task chosen=tasks.stream().filter(Task::canStart).max(Comparator.comparingInt(Task::priority)).orElse(null);
        if (current!=null && current!=chosen) current.suspend();
        current=chosen;
        if (chosen!=null) chosen.tick(world);
    }
    public void pause() {
        paused=true;
        try { if (current!=null) current.suspend(); } finally { current=null; release.run(); }
    }
    public void resume() { paused=false; }
    public void stop() {
        paused=true;
        RuntimeException failure=null;
        try {
            for (Task task:tasks) try { task.cancel(); } catch (RuntimeException error) {
                if (failure==null) failure=error; else failure.addSuppressed(error);
            }
        } finally { current=null; release.run(); }
        if (failure!=null) throw failure;
    }
    public void clearFinished() { tasks.removeIf(Task::terminal); }
}
