package dev.apexcraft.client;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import java.util.ArrayDeque;

/** Applies a temporary task profile, and restores the user's settings afterwards. */
public final class BaritoneSession {
    private final ArrayDeque<Runnable> restore=new ArrayDeque<>();
    private boolean active;
    public IBaritone bot() { return BaritoneAPI.getProvider().getPrimaryBaritone(); }
    private <T> void setting(Settings.Setting<T> setting,T value) {
        T old=setting.value;
        restore.push(() -> setting.value=old);
        setting.value=value;
    }
    public void start() {
        if (active) return;
        active=true;
        var settings=BaritoneAPI.getSettings();
        setting(settings.freeLook,false);
        setting(settings.blockFreeLook,false);
        setting(settings.smoothLook,true);
        setting(settings.allowSprint,false);
        setting(settings.allowParkour,false);
        setting(settings.allowPlace,false);
        setting(settings.allowBreak,true);
        setting(settings.allowInventory,true);
        setting(settings.legitMine,true);
        setting(settings.legitMineIncludeDiagonals,false);
        // No delay manipulation: the vanilla client now owns all block breaking.
        MouseMiningInput.own(true);
    }
    public void miningLevel(int y) { setting(BaritoneAPI.getSettings().legitMineYLevel,y); }
    public void stop() {
        if (!active) return;
        try {
            bot().getPathingBehavior().cancelEverything();
            bot().getInputOverrideHandler().clearAllKeys();
        } finally {
            MouseMiningInput.own(false);
            while (!restore.isEmpty()) restore.pop().run();
            active=false;
        }
    }
}
