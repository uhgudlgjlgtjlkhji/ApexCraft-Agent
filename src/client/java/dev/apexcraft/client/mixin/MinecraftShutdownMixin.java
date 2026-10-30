package dev.apexcraft.client.mixin;

import dev.apexcraft.client.BaritoneShutdown;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftShutdownMixin {
    @Inject(method="close()V",at=@At("RETURN"))
    private void apexcraft$closeWorkers(CallbackInfo info) { BaritoneShutdown.finish(); }
}
