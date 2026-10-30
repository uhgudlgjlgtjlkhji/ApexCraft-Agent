package dev.apexcraft.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

/** Ends upstream cache/worker threads only after Minecraft has finished closing its worlds. */
public final class BaritoneShutdown {
    private BaritoneShutdown() {}
    public static void finish() {
        if (!(baritone.Baritone.getExecutor() instanceof ExecutorService executor)) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2,TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            LoggerFactory.getLogger("apexcraft").warn("Interrupted while stopping Baritone background workers",error);
        }
    }
}
