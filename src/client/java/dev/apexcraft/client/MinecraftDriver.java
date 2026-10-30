package dev.apexcraft.client;

import baritone.api.pathing.goals.GoalBlock;
import dev.apexcraft.core.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Live inventory and menu adapter. Movement/mining are provided by the official Baritone dependency. */
public final class MinecraftDriver implements ObjectiveDriver {
    final Minecraft client;
    final BaritoneSession session=new BaritoneSession();
    private long version;
    private final Map<String,BlockPos> stations=new HashMap<>();
    Object ownedScreen;
    String expectedMenu;
    int openRequestAge;
    public MinecraftDriver(Minecraft client) { this.client=client; }
    public Observation observe() {
        var inventory=new HashMap<String,Integer>();
        for (var stack:client.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) inventory.merge(ProgressionCatalog.canonical(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),stack.getCount(),Integer::sum);
        }
        var p=client.player.blockPosition();
        return new Observation(++version,new Position(p.getX(),p.getY(),p.getZ()),
            client.player.getHealth(),client.player.getFoodData().getFoodLevel(),inventory);
    }
    public Map<String,Integer> planningInventory(Observation world) {
        var available=new HashMap<>(world.inventory());
        for (String station:List.of("crafting_table","furnace"))
            if (station(station)!=null) available.put(station,Math.max(1,available.getOrDefault(station,0)));
        return available;
    }
    public Action movement(Position target) {
        return new BaritoneAction(session,s -> s.bot().getCustomGoalProcess().setGoalAndPath(
            new GoalBlock(target.x(),target.y(),target.z())),w -> w.position().equals(target));
    }
    public Action collect(String item,int total) {
        var targets=ProgressionCatalog.SOURCES.get(item);
        if (targets==null) throw new IllegalArgumentException("No mining source for "+item);
        return new BaritoneAction(session,s -> {
            int level=switch(item) { case "diamond" -> -54; case "raw_iron" -> 16;
                default -> client.player.blockPosition().getY(); };
            s.miningLevel(level);
            s.bot().getMineProcess().mineByName(total,targets.toArray(String[]::new));
        },w -> w.inventory().getOrDefault(item,0)>=total);
    }
    public Action craft(String item) { return new CraftingAction(this,item,1); }
    public Action smelt(int amount) { return new CraftingAction(this,"iron_ingot",amount); }
    public void decision(String message) { LoggerFactory.getLogger("apexcraft").info("[PLAN] {}",message); }
    public void releaseInputs() {
        session.stop(); MouseMiningInput.release();
        client.options.keyUp.setDown(false); client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false); client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false); client.options.keySprint.setDown(false);
        client.options.keyUse.setDown(false);
        while (client.options.keyUse.consumeClick()) { /* no queued placement after cancellation */ }
    }
    public boolean ownsScreen() {
        Object screen=client.gui.screen();
        if (screen==null) return false;
        if (screen==ownedScreen) return true;
        if (expectedMenu!=null && openRequestAge++<40 && (
            expectedMenu.equals("crafting_table") && client.player.containerMenu instanceof CraftingMenu
            || expectedMenu.equals("furnace") && client.player.containerMenu instanceof FurnaceMenu)) {
            ownedScreen=screen;
            return true;
        }
        return false;
    }
    void closeOwnedScreen() {
        try {
            if (client.gui.screen()!=null && ownsScreen()) client.gui.screen().onClose();
        } finally { ownedScreen=null; expectedMenu=null; }
    }
    BlockPos station(String item) {
        BlockPos known=stations.get(item);
        if (known!=null && client.level.hasChunkAt(known) && blockId(known).equals(item)
            ) return known;
        stations.remove(item);
        BlockPos center=client.player.blockPosition();
        for (BlockPos pos:BlockPos.betweenClosed(center.offset(-3,-2,-3),center.offset(3,2,3))) {
            if (client.level.hasChunkAt(pos) && blockId(pos).equals(item)
                && client.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos))<4.4) {
                stations.put(item,pos.immutable()); return pos.immutable();
            }
        }
        return null;
    }
    private String blockId(BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString().replace("minecraft:","");
    }
    boolean face(Vec3 point) {
        Vec3 delta=point.subtract(client.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        client.player.setYRot(LookSmoother.step(client.player.getYRot(),yaw,12,true));
        client.player.setXRot(LookSmoother.step(client.player.getXRot(),pitch,10,false));
        double yawError=(yaw-client.player.getYRot()+540)%360-180;
        return Math.abs(yawError)<3 && Math.abs(pitch-client.player.getXRot())<3;
    }
    void clickUse() { KeyMapping.click(KeyMappingHelper.getBoundKeyOf(client.options.keyUse)); }
    void click(AbstractContainerMenu menu,int slot,int button,ContainerInput type) {
        if (client.player.containerMenu!=menu) throw new IllegalStateException("Container changed during crafting");
        client.gameMode.handleContainerInput(menu.containerId,slot,button,type,client.player);
    }
    int inventorySlot(AbstractContainerMenu menu,String item,Map<Integer,Integer> reserved) {
        for (int i=0;i<menu.slots.size();i++) {
            var slot=menu.getSlot(i); var stack=slot.getItem();
            if (slot.container==client.player.getInventory() && !stack.isEmpty()
                && ProgressionCatalog.canonical(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).equals(item)
                && stack.getCount()>reserved.getOrDefault(i,0)) return i;
        }
        throw new IllegalStateException("Missing inventory ingredient: "+item);
    }
    Action nearStation(BlockPos target) {
        return new BaritoneAction(session,s -> s.bot().getCustomGoalProcess().setGoalAndPath(
            new baritone.api.pathing.goals.GoalNear(target,2)),
            w -> client.player.getEyePosition().distanceTo(Vec3.atCenterOf(target))<4.2);
    }
    private boolean aimForInteraction(Vec3 point,BlockPos intended,Direction face) {
        if (!face(point) || !(client.hitResult instanceof BlockHitResult hit)) {
            MouseMiningInput.release();return false;
        }
        if (hit.getBlockPos().equals(intended) && (face==null || hit.getDirection()==face)) {
            MouseMiningInput.release();return true;
        }
        // Plants have a selection outline even though their collision shape is empty.
        // Clear only harmless vegetation that actually blocks this interaction ray.
        String obstruction=blockId(hit.getBlockPos());
        boolean grass=Set.of("short_grass","tall_grass","fern","large_fern").contains(obstruction);
        if (grass) MouseMiningInput.set(true); else MouseMiningInput.release();
        return false;
    }
    boolean prepareStation(String item,int tick) {
        BlockPos station=station(item);
        if (station!=null) {
            if (aimForInteraction(Vec3.atCenterOf(station),station,null) && tick%8==0) {
                expectedMenu=item; openRequestAge=0; clickUse();
            }
            return false;
        }
        // Place a real workstation using the selected item and an ordinary right click.
        var inv=client.player.getInventory();
        int index=-1;
        for (int i=0;i<inv.getNonEquipmentItems().size();i++)
            if (!inv.getItem(i).isEmpty() && BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).toString().equals("minecraft:"+item)) { index=i;break; }
        if (index<0) throw new IllegalStateException("Workstation unavailable: "+item);
        if (index>=9) {
            if (tick%4==0) click(client.player.inventoryMenu,index,8,ContainerInput.SWAP);
            return false;
        }
        inv.setSelectedSlot(index);
        BlockPos feet=client.player.blockPosition();
        for (Direction direction:Direction.Plane.HORIZONTAL) {
            BlockPos target=feet.relative(direction),floor=target.below();
            if (!client.level.getBlockState(target).canBeReplaced()
                || !client.level.getBlockState(floor).isFaceSturdy(client.level,floor,Direction.UP)
                || !client.level.getBlockState(floor).getFluidState().isEmpty()) continue;
            if (aimForInteraction(Vec3.atCenterOf(floor).add(0,0.5,0),floor,Direction.UP) && tick%8==0) clickUse();
            return false;
        }
        throw new IllegalStateException("No safe adjacent workstation placement site");
    }
}
