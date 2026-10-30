package dev.apexcraft.core;

import java.util.function.Consumer;

/** A mouse hold emits one click edge, then a held state, and an explicit release. */
public final class HeldButton {
    private final Runnable click;
    private final Consumer<Boolean> down;
    private boolean held;
    public HeldButton(Runnable click,Consumer<Boolean> down) { this.click=click;this.down=down; }
    public void update(boolean requested) {
        if (requested && !held) click.run();
        down.accept(requested);
        held=requested;
    }
}
