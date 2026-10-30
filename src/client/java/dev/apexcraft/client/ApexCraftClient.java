package dev.apexcraft.client;

import dev.apexcraft.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLScancode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;

public final class ApexCraftClient implements ClientModInitializer {
    private static final Logger LOG=LoggerFactory.getLogger("apexcraft");
    private MinecraftDriver driver;
    private Scheduler scheduler;
    private Task current;
    private long sequence;
    private boolean paused;
    private Object activeLevel;
    @Override public void onInitializeClient() {
        Minecraft client=Minecraft.getInstance();
        driver=new MinecraftDriver(client); scheduler=new Scheduler(driver::releaseInputs);
        KeyMapping stop=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.apexcraft.stop",
            InputConstants.Type.KEYBOARD,SDLScancode.SDL_SCANCODE_F8,KeyMapping.Category.MISC));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,context) -> dispatcher.register(
            ClientCommands.literal("apex")
                .executes(c -> reply("ApexCraft: "+status()))
                .then(ClientCommands.argument("command",StringArgumentType.greedyString()).executes(c -> {
                    try { return command(StringArgumentType.getString(c,"command")); }
                    catch (IllegalArgumentException | IllegalStateException error) { return reply(error.getMessage()); }
                }))));
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc,screen,width,height) ->
            net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register((shown,event) -> {
                if (stop.matches(event)) { stop();return false; }
                return true;
            }));
        ClientPlayConnectionEvents.DISCONNECT.register((listener,mc) -> stop());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (stop.consumeClick()) stop();
            if (current==null || current.terminal() || current.status()==Task.Status.BLOCKED || paused) return;
            if (mc.player==null || mc.level==null || mc.level!=activeLevel) { stop(); return; }
            // GUI/focus changes always release controls; resumption is explicit.
            if ((mc.gui.screen()!=null && !driver.ownsScreen()) || !mc.isWindowActive()) { scheduler.pause(); paused=true; return; }
            try {
                Observation observation=driver.observe();
                if (observation.health()<6 || observation.hunger()<6 || mc.player.isInWater()) {
                    scheduler.pause(); paused=true;
                    reply("Paused for health, hunger, or water danger. Recover safely, then /apex resume.");
                    return;
                }
                scheduler.tick(observation);
                if (current.terminal() || current.status()==Task.Status.BLOCKED) reply(status());
            } catch (RuntimeException error) {
                LOG.error("ApexCraft tick failed",error); stop(); reply("Stopped: "+error.getMessage());
            }
        });
        LOG.info("ApexCraft client initialized; F8 emergency stop. Automation starts only on /apex commands.");
    }
    private void stop() {
        try { scheduler.stop(); } finally { paused=false; }
    }
    private String status() {
        return current==null?"IDLE":(paused?"PAUSED ":"")+current.id()+" "+current.status()
            +(current.reason()==null?"":" — "+current.reason());
    }
    private int reply(String text) {
        var player=Minecraft.getInstance().player;
        if (player!=null) Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("[ApexCraft] "+text));
        return 1;
    }
    private void submit(Action action,String description) {
        stop(); scheduler.clearFinished();
        activeLevel=Minecraft.getInstance().level;
        current=new Task(++sequence+":"+description,action,event ->
            LOG.info("task={} status={} reason={}",event.id(),event.status(),event.reason()),0,144000,0);
        scheduler.submit(current); scheduler.resume();
    }
    private int command(String command) {
        var client=Minecraft.getInstance();
        String[] words=command.trim().split("\\s+");
        String verb=words[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "stop" -> { stop(); return reply("Stopped; inputs released."); }
            case "pause" -> { scheduler.pause(); paused=true; return reply(status()); }
            case "resume" -> { scheduler.resume(); paused=false; return reply(status()); }
            case "status", "tasks", "debug" -> { return reply(status()); }
            case "inventory" -> {
                if (client.player==null) throw new IllegalStateException("Join a world first");
                return reply(driver.observe().inventory().toString());
            }
        }
        if (client.player==null || client.level==null) throw new IllegalStateException("Join a permitted world first");
        switch (verb) {
            case "goto" -> {
                if (words.length!=4) throw new IllegalArgumentException("Usage: /apex goto x y z");
                var target=new Position(Integer.parseInt(words[1]),Integer.parseInt(words[2]),Integer.parseInt(words[3]));
                submit(driver.movement(target),"goto "+target);
            }
            case "collect" -> {
                if (words.length!=3) throw new IllegalArgumentException("Usage: /apex collect minecraft:oak_log 8");
                String item=ProgressionCatalog.canonical(words[1]);
                submit(new ObjectiveAction(driver,item,Integer.parseInt(words[2])),"collect "+item);
            }
            case "goal", "get", "craft" -> {
                GoalRequest request=GoalRequest.parse(command.substring(words[0].length()));
                submit(new ObjectiveAction(driver,request.item(),request.amount()),
                    "obtain "+request.amount()+" "+request.item());
            }
            default -> throw new IllegalArgumentException("Available: goal/get, goto, collect, craft, stop, pause, resume, status, tasks, inventory, debug");
        }
        return reply(status());
    }
}
