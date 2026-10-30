package dev.apexcraft.client.mixin;

import dev.apexcraft.client.MouseMiningInput;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoped adapter, not a copy of Baritone's breaking implementation. */
@Mixin(targets="baritone.utils.BlockBreakHelper", remap=false)
public abstract class BaritoneMouseInputMixin {
    @Shadow(remap=false) @Final private IPlayerContext ctx;
    private boolean apexcraft$ownsPlayer() { return MouseMiningInput.owned() && ctx.player()==Minecraft.getInstance().player; }
    @Inject(method="tick(Z)V", at=@At("HEAD"), cancellable=true, remap=false)
    private void apexcraft$normalMouse(boolean leftClick, CallbackInfo info) {
        if (apexcraft$ownsPlayer()) {
            MouseMiningInput.set(leftClick);
            info.cancel();
        }
    }
    @Inject(method="stopBreakingBlock()V", at=@At("HEAD"), cancellable=true, remap=false)
    private void apexcraft$releaseMouse(CallbackInfo info) {
        if (apexcraft$ownsPlayer()) {
            MouseMiningInput.set(false);
            info.cancel();
        }
    }
}
