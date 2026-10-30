package dev.apexcraft.client;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

/** Uses exactly the key binding the player has assigned to the normal attack mouse button. */
public final class MouseMiningInput {
    private static boolean owned;
    private static final dev.apexcraft.core.HeldButton BUTTON=new dev.apexcraft.core.HeldButton(
        () -> KeyMapping.click(KeyMappingHelper.getBoundKeyOf(Minecraft.getInstance().options.keyAttack)),
        down -> Minecraft.getInstance().options.keyAttack.setDown(down));
    private MouseMiningInput() {}
    public static boolean owned() { return owned; }
    public static void own(boolean value) {
        if (!value) {
            set(false);
            while (Minecraft.getInstance().options.keyAttack.consumeClick()) { /* discard this task's queued press */ }
        }
        owned=value;
    }
    public static void set(boolean requested) {
        Minecraft mc=Minecraft.getInstance();
        boolean down=requested && mc.player!=null && mc.level!=null && mc.gui.screen()==null
            && mc.isWindowActive() && mc.hitResult instanceof BlockHitResult;
        if (down && mc.hitResult instanceof BlockHitResult hit) {
            var state=mc.level.getBlockState(hit.getBlockPos());
            down=!state.requiresCorrectToolForDrops() || mc.player.getMainHandItem().isCorrectToolForDrops(state);
        }
        BUTTON.update(down);
    }
}
