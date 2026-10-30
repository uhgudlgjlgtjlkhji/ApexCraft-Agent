package dev.apexcraft.core;

import java.util.*;

/** Canonical inventory IDs; all crafting recipes describe real Minecraft ingredient counts. */
public final class ProgressionCatalog {
    public record Craft(String item,int output,List<String> grid,boolean table) {}
    public static final Map<String,Craft> CRAFTS=new LinkedHashMap<>();
    public static final Map<String,List<String>> SOURCES=Map.of(
        "log",List.of("oak_log","birch_log","spruce_log","jungle_log","acacia_log","dark_oak_log","mangrove_log","cherry_log"),
        "cobblestone",List.of("stone","cobblestone"),
        "coal",List.of("coal_ore","deepslate_coal_ore"),
        "raw_iron",List.of("iron_ore","deepslate_iron_ore"),
        "diamond",List.of("diamond_ore","deepslate_diamond_ore"));
    public static final RecipePlanner PLANNER;
    static {
        craft("planks",4,false,"log","","","");
        craft("stick",4,false,"planks","","planks","");
        craft("crafting_table",1,false,"planks","planks","planks","planks");
        craft("wooden_pickaxe",1,true,"planks","planks","planks","","stick","","","stick","");
        craft("stone_pickaxe",1,true,"cobblestone","cobblestone","cobblestone","","stick","","","stick","");
        craft("furnace",1,true,"cobblestone","cobblestone","cobblestone","cobblestone","","cobblestone","cobblestone","cobblestone","cobblestone");
        craft("iron_pickaxe",1,true,"iron_ingot","iron_ingot","iron_ingot","","stick","","","stick","");
        var recipes=new LinkedHashMap<String,RecipePlanner.Recipe>();
        CRAFTS.forEach((item,recipe) -> {
            var ingredients=new LinkedHashMap<String,Integer>();
            recipe.grid().stream().filter(s -> !s.isEmpty()).forEach(s -> ingredients.merge(s,1,Integer::sum));
            recipes.put(item,new RecipePlanner.Recipe(recipe.output(),ingredients,
                recipe.table()?Map.of("crafting_table",1):Map.of(),"craft"));
        });
        recipes.put("iron_ingot",new RecipePlanner.Recipe(1,Map.of("raw_iron",1),
            Map.of("furnace",1,"coal",1),"smelt"));
        var sources=new LinkedHashMap<String,Map<String,Integer>>();
        sources.put("log",Map.of()); sources.put("cobblestone",Map.of("wooden_pickaxe",1));
        sources.put("coal",Map.of("wooden_pickaxe",1)); sources.put("raw_iron",Map.of("stone_pickaxe",1));
        sources.put("diamond",Map.of("iron_pickaxe",1));
        PLANNER=new RecipePlanner(recipes,sources);
    }
    private static void craft(String item,int output,boolean table,String... grid) {
        CRAFTS.put(item,new Craft(item,output,List.of(grid),table));
    }
    public static String canonical(String id) {
        String item=id.replace("minecraft:","");
        if (item.endsWith("_log")) return "log";
        if (item.endsWith("_planks")) return "planks";
        return item;
    }
    public static Map<String,Integer> toolsSatisfied(Map<String,Integer> stock) {
        var result=new HashMap<>(stock);
        if (stock.getOrDefault("netherite_pickaxe",0)>0 || stock.getOrDefault("diamond_pickaxe",0)>0)
            result.put("iron_pickaxe",1);
        if (result.getOrDefault("iron_pickaxe",0)>0) result.put("stone_pickaxe",1);
        if (result.getOrDefault("stone_pickaxe",0)>0) result.put("wooden_pickaxe",1);
        return result;
    }
}
