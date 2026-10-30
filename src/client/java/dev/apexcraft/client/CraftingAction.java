package dev.apexcraft.client;

import dev.apexcraft.core.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import java.util.*;

/** Real, rate-limited container clicks. Inventory observations, never clicks, prove success. */
public final class CraftingAction implements Action {
    private record Click(int slot,int button,ContainerInput kind,boolean carriedOnly) {}
    private final MinecraftDriver driver;
    private final String item;
    private final int amount;
    private final boolean smelting;
    private Cancellation token;
    private AbstractContainerMenu menu;
    private final ArrayDeque<Click> clicks=new ArrayDeque<>();
    private int ticks,phase,target,menuTicks,placementTicks;
    private boolean done;
    private Action travel;
    public CraftingAction(MinecraftDriver driver,String item,int amount) {
        this.driver=driver;this.item=item;this.amount=amount;smelting=item.equals("iron_ingot");
        if (!smelting && !ProgressionCatalog.CRAFTS.containsKey(item)) throw new IllegalArgumentException("Unknown recipe "+item);
    }
    public void start(Cancellation token) {
        this.token=token;token.check();driver.releaseInputs();menu=null;ticks=phase=menuTicks=placementTicks=0;done=false;clicks.clear();target=-1;
    }
    public void tick(Observation world) {
        token.check();ticks++;
        if (target<0) target=world.inventory().getOrDefault(item,0)+(smelting?amount:ProgressionCatalog.CRAFTS.get(item).output());
        if (verify(world)) { done=true; return; }
        if (ticks>24000) throw new IllegalStateException("Workstation travel budget exceeded");
        if (menu!=null && ++menuTicks>240*amount+600)
            throw new IllegalStateException("Craft/smelt timed out; inspect ingredients, inventory space and server container state");
        var mc=driver.client;
        if (travel!=null) {
            travel.tick(world);
            if (travel.complete()) { travel.cancel();travel=null; }
            return;
        }
        if (menu==null) {
            boolean table=!smelting && ProgressionCatalog.CRAFTS.get(item).table();
            if (smelting || table) {
                var station=driver.station(smelting?"furnace":"crafting_table");
                if (station!=null && mc.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(station))>=4.2) {
                    travel=driver.nearStation(station);travel.start(token);return;
                }
                if (++placementTicks>400) throw new IllegalStateException("Workstation could not be placed or opened");
                if (smelting && mc.player.containerMenu instanceof AbstractFurnaceMenu
                    || table && mc.player.containerMenu instanceof CraftingMenu) {
                    menu=mc.player.containerMenu;driver.ownedScreen=mc.gui.screen();
                } else { driver.prepareStation(smelting?"furnace":"crafting_table",ticks);return; }
            } else {
                mc.gui.setScreen(new InventoryScreen(mc.player));driver.ownedScreen=mc.gui.screen();
                menu=mc.player.inventoryMenu;
            }
            if (!menu.getCarried().isEmpty()) throw new IllegalStateException("Cursor already holds an item; crafting will not overwrite it");
        }
        if (mc.player.containerMenu!=menu) throw new IllegalStateException("Crafting container closed or replaced");
        if (ticks%3!=0) return;
        if (!clicks.isEmpty()) {
            Click click=clicks.removeFirst();
            if (!click.carriedOnly() || !menu.getCarried().isEmpty()) driver.click(menu,click.slot(),click.button(),click.kind());
            return;
        }
        if (smelting) { smeltTick(world);return; }
        var recipe=ProgressionCatalog.CRAFTS.get(item);
        if (phase==0) {
            for (int i=1;i<=recipe.grid().size();i++)
                if (menu.getSlot(i).hasItem()) clicks.add(new Click(i,0,ContainerInput.QUICK_MOVE,false));
            phase=1;return;
        }
        if (phase==1) {
            var reserved=new HashMap<Integer,Integer>();
            for (int index=0;index<recipe.grid().size();index++) {
                String ingredient=recipe.grid().get(index);
                if (!ingredient.isEmpty()) enqueueOne(ingredient,index+1,reserved);
            }
            phase=2;return;
        }
        var output=menu.getSlot(0).getItem();
        if (!output.isEmpty()) {
            String actual=ProgressionCatalog.canonical(BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
            if (!actual.equals(item)) throw new IllegalStateException("Unexpected crafting result: "+actual);
            driver.click(menu,0,0,ContainerInput.QUICK_MOVE);
            phase=3;
        }
    }
    private void enqueueOne(String ingredient,int destination,Map<Integer,Integer> reserved) {
        int source=driver.inventorySlot(menu,ingredient,reserved);
        reserved.merge(source,1,Integer::sum);
        clicks.add(new Click(source,0,ContainerInput.PICKUP,false));
        clicks.add(new Click(destination,1,ContainerInput.PICKUP,false));
        clicks.add(new Click(source,0,ContainerInput.PICKUP,true));
    }
    private void smeltTick(Observation world) {
        var furnace=(AbstractFurnaceMenu)menu;
        if (menu.getSlot(2).hasItem()) {
            String result=ProgressionCatalog.canonical(BuiltInRegistries.ITEM.getKey(menu.getSlot(2).getItem().getItem()).toString());
            if (!result.equals("iron_ingot")) throw new IllegalStateException("Furnace contains a different output; refusing to overwrite");
            driver.click(menu,2,0,ContainerInput.QUICK_MOVE);return;
        }
        if (phase==0) {
            var input=menu.getSlot(0).getItem();
            if (!input.isEmpty() && !ProgressionCatalog.canonical(BuiltInRegistries.ITEM.getKey(input.getItem()).toString()).equals("raw_iron"))
                throw new IllegalStateException("Furnace already contains a different input");
            var reserved=new HashMap<Integer,Integer>();
            int needed=Math.max(0,target-world.inventory().getOrDefault(item,0)-input.getCount());
            for (int i=0;i<needed;i++) enqueueOne("raw_iron",0,reserved);
            if (!furnace.isLit() && !menu.getSlot(1).hasItem()) enqueueOne("coal",1,reserved);
            phase=1;
        }
    }
    @Override public String toString() { return (smelting?"smelt ":"craft ")+item+" phase="+phase+" clicks="+clicks.size()+" target="+target+" travel="+travel; }
    public boolean complete() { return done; }
    public boolean verify(Observation world) { return target>=0 && world.inventory().getOrDefault(item,0)>=target; }
    public void cancel() {
        clicks.clear();
        if (travel!=null) { travel.cancel();travel=null; }
        try { driver.closeOwnedScreen(); } finally { driver.releaseInputs(); }
    }
}
