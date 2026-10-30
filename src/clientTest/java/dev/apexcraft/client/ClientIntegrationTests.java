package dev.apexcraft.client;

import dev.apexcraft.core.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** Actual client, integrated server, terrain collision, normal mining input and real menu transactions. */
public final class ClientIntegrationTests implements FabricClientGameTest {
    public void runTest(ClientGameTestContext context) {
        try (var singleplayer=context.worldBuilder().create()) {
            var server=singleplayer.getServer();
            server.runCommand("fill -8 80 -8 12 80 8 minecraft:grass_block");
            server.runCommand("fill -8 81 -8 12 85 8 minecraft:air");
            server.runCommand("fill -1 81 -1 8 83 -1 minecraft:glass");
            server.runCommand("fill -1 81 1 8 83 1 minecraft:glass");
            server.runCommand("setblock 1 81 0 minecraft:short_grass");
            server.runCommand("setblock 2 81 0 minecraft:tall_grass[half=lower]");
            server.runCommand("setblock 2 82 0 minecraft:tall_grass[half=upper]");
            server.runCommand("tp @p 0.5 81 0.5");
            server.runCommand("gamemode survival @p");
            context.waitFor(mc -> mc.player!=null && mc.player.blockPosition().equals(new BlockPos(0,81,0))
                && mc.level.getBlockState(new BlockPos(0,80,0)).is(Blocks.GRASS_BLOCK));
            context.waitTicks(20);
            MinecraftDriver driver=context.computeOnClient(MinecraftDriver::new);
            Action movement=context.computeOnClient(mc -> driver.movement(new Position(4,81,0)));
            execute(context,driver,movement,800);
            context.runOnClient(mc -> {
                if (!mc.level.getBlockState(new BlockPos(1,81,0)).is(Blocks.SHORT_GRASS)
                    || !mc.level.getBlockState(new BlockPos(2,81,0)).is(Blocks.TALL_GRASS))
                    throw new AssertionError("Walking must pass through grass without trying to mine it");
            });
            System.out.println("APEXCRAFT_CLIENT_STAGE: grass walk passed");
            server.runCommand("give @p minecraft:iron_pickaxe");
            server.runCommand("setblock 6 81 0 minecraft:diamond_ore");
            context.waitFor(mc -> mc.level.getBlockState(new BlockPos(6,81,0)).is(Blocks.DIAMOND_ORE));
            execute(context,driver,new ObjectiveAction(driver,"diamond",1),1200);
            context.runOnClient(mc -> {
                if (mc.options.keyAttack.isDown() || MouseMiningInput.owned())
                    throw new AssertionError("Mining did not release normal mouse input");
            });
            System.out.println("APEXCRAFT_CLIENT_STAGE: mouse diamond mining passed");
            // A real missing-pickaxe goal: smelt raw iron, place/open workstations,
            // craft the pickaxe via menu clicks, and inspect the resulting inventory.
            server.runCommand("clear @p");
            server.runCommand("give @p minecraft:raw_iron 3");
            server.runCommand("give @p minecraft:coal 1");
            server.runCommand("give @p minecraft:stick 2");
            server.runCommand("give @p minecraft:furnace 1");
            server.runCommand("give @p minecraft:crafting_table 1");
            context.waitFor(mc -> driver.observe().inventory().getOrDefault("raw_iron",0)==3);
            execute(context,driver,new ObjectiveAction(driver,"iron_pickaxe",1),2400);
            context.runOnClient(mc -> {
                if (!mc.player.containerMenu.getCarried().isEmpty()) throw new AssertionError("Crafting left an item on the cursor");
            });
            MixinEnvironment.getCurrentEnvironment().audit();
            System.out.println("APEXCRAFT_CLIENT_TESTS_PASSED: grass walking, mouse mining, iron smelting/crafting, input cleanup");
        }
    }
    private void execute(ClientGameTestContext context,MinecraftDriver driver,Action action,int limit) {
        context.runOnClient(mc -> action.start(new Cancellation()));
        try {
            for (int tick=0;tick<limit;tick++) {
                boolean done=context.computeOnClient(mc -> {
                    Observation observation=driver.observe();action.tick(observation);
                    return action.complete() && action.verify(observation);
                });
                if (done) return;
                context.waitTick();
            }
            throw new AssertionError("Client integration action timed out: "+action.getClass().getSimpleName());
        } finally { context.runOnClient(mc -> action.cancel()); }
    }
}
