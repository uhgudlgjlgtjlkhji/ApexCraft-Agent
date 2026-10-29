package dev.apexcraft.client;

import dev.apexcraft.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Normal client inputs and interactions only; unknown chunks are impassable. */
public final class MinecraftDriver implements ResourceDriver {
    private final Minecraft client;
    private long version;
    private Position scanOrigin;
    private int scanIndex;
    private String scanItem;
    private static final int WIDTH=17, HEIGHT=9, VOLUME=WIDTH*WIDTH*HEIGHT;
    public MinecraftDriver(Minecraft client) { this.client=client; }
    private static BlockPos block(Position p) { return new BlockPos(p.x(),p.y(),p.z()); }
    private Position position() {
        BlockPos p=client.player.blockPosition(); return new Position(p.getX(),p.getY(),p.getZ());
    }
    public Observation observe() {
        var inventory=new HashMap<String,Integer>();
        var slots=client.player.getInventory();
        for (int i=0;i<slots.getContainerSize();i++) {
            var stack=slots.getItem(i);
            if (!stack.isEmpty()) inventory.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
        }
        return new Observation(++version,position(),client.player.getHealth(),client.player.getFoodData().getFoodLevel(),inventory);
    }
    public boolean traversable(Position p) {
        if (client.level==null || !client.level.hasChunkAt(block(p))) return false;
        BlockPos feet=block(p),head=feet.above(),floor=feet.below();
        var footState=client.level.getBlockState(feet); var headState=client.level.getBlockState(head);
        var floorState=client.level.getBlockState(floor);
        // Walking only: no fluids, unsupported falls, doors, breaking, or bridging.
        return footState.getCollisionShape(client.level,feet).isEmpty()
            && headState.getCollisionShape(client.level,head).isEmpty()
            && !floorState.getCollisionShape(client.level,floor).isEmpty()
            && footState.getFluidState().isEmpty() && headState.getFluidState().isEmpty()
            && floorState.getFluidState().isEmpty()
            && !BuiltInRegistries.BLOCK.getKey(floorState.getBlock()).toString().matches(".*(magma|campfire|cactus|fire).*");
    }
    public List<PathSearch.Edge> neighbors(Position p) {
        var edges=new ArrayList<PathSearch.Edge>();
        for (int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            Position n=p.add(d[0],0,d[1]);
            if (n.distance(position())<=48 && traversable(n)) edges.add(new PathSearch.Edge(n,1));
        }
        return edges;
    }
    public void moveToward(Position target) {
        if (!traversable(target)) { releaseInputs(); return; }
        double dx=target.x()+0.5-client.player.getX(),dz=target.z()+0.5-client.player.getZ();
        client.player.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));
        client.options.keyUp.setDown(true);
    }
    public void releaseInputs() {
        client.options.keyUp.setDown(false); client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false); client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false); client.options.keySprint.setDown(false);
        client.options.keyAttack.setDown(false); client.options.keyUse.setDown(false);
        if (client.gameMode!=null) client.gameMode.stopDestroyBlock();
    }
    public void beginScan(String item) { scanOrigin=position(); scanItem=item; scanIndex=0; }
    public Optional<Position> scan(int budget) {
        for (int i=0;i<budget && scanIndex<VOLUME;i++,scanIndex++) {
            int x=scanIndex%WIDTH-8,z=(scanIndex/WIDTH)%WIDTH-8,y=scanIndex/(WIDTH*WIDTH)-4;
            Position p=scanOrigin.add(x,y,z);
            if (sourcePresent(p,scanItem)) { scanIndex++; return Optional.of(p); }
        }
        return Optional.empty();
    }
    public boolean scanComplete() { return scanIndex>=VOLUME; }
    public boolean sourcePresent(Position p,String item) {
        return client.level.hasChunkAt(block(p)) && BuiltInRegistries.ITEM.getKey(
            client.level.getBlockState(block(p)).getBlock().asItem()).toString().equals(item);
    }
    public boolean canReach(Position p) {
        return client.player.getEyePosition().distanceTo(Vec3.atCenterOf(block(p)))<=4;
    }
    public void mine(Position p) {
        var state=client.level.getBlockState(block(p));
        if (state.requiresCorrectToolForDrops() && !client.player.getMainHandItem().isCorrectToolForDrops(state))
            throw new IllegalStateException("Select a suitable mining tool in the hotbar");
        if (state.getDestroySpeed(client.level,block(p))<0 || block(p).equals(client.player.blockPosition().below()))
            throw new IllegalStateException("Refusing unbreakable block or supporting floor");
        client.player.lookAt(EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(block(p)));
        // Use the ordinary game-mode controller; never spoof packets or bypass server validation.
        if (client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(block(p)))
            client.gameMode.continueDestroyBlock(block(p),hit.getDirection());
    }
    public Position approach(Position source) {
        var candidates=new ArrayList<Position>();
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
            Position p=new Position(source.x()+x,position().y(),source.z()+z);
            if (traversable(p) && p.distance(source)<=3) candidates.add(p);
        }
        return candidates.stream().min(Comparator.comparingDouble(p -> p.distance(position())))
            .orElseThrow(() -> new IllegalStateException("No safe observed source approach"));
    }
}
