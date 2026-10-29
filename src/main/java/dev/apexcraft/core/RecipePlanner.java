package dev.apexcraft.core;

import java.util.*;

/** Recipe output batches, retained tool/station prerequisites, and inventory reservations. */
public final class RecipePlanner {
    public record Recipe(int output,Map<String,Integer> ingredients,Map<String,Integer> requirements,String kind) {
        public Recipe {
            ingredients=Map.copyOf(ingredients); requirements=Map.copyOf(requirements);
            if (output<1 || !Set.of("craft","smelt").contains(kind)
                || ingredients.values().stream().anyMatch(n -> n<1)
                || requirements.values().stream().anyMatch(n -> n<1)) throw new IllegalArgumentException("Invalid recipe");
        }
    }
    public record Step(String kind,String item,int count) {}
    private final Map<String,Recipe> recipes;
    private final Map<String,Map<String,Integer>> sources;
    public RecipePlanner(Map<String,Recipe> recipes,Map<String,Map<String,Integer>> sources) {
        this.recipes=Map.copyOf(recipes); this.sources=Map.copyOf(sources);
    }
    public List<Step> resolve(Map<String,Integer> required,Map<String,Integer> inventory) {
        var stock=new HashMap<>(inventory); var steps=new ArrayList<Step>(); var visiting=new HashSet<String>();
        // Preserve caller order for deterministic strategy selection.
        for (var entry:required.entrySet()) {
            if (entry.getValue()<0) throw new IllegalArgumentException("Negative quantity");
            obtain(entry.getKey(),entry.getValue(),stock,steps,visiting);
            stock.merge(entry.getKey(),-entry.getValue(),Math::addExact);
        }
        return List.copyOf(steps);
    }
    private void obtain(String item,int amount,Map<String,Integer> stock,List<Step> steps,Set<String> visiting) {
        int deficit=amount-stock.getOrDefault(item,0);
        if (deficit<=0) return;
        if (!visiting.add(item)) throw new IllegalArgumentException("Dependency cycle: "+item);
        Recipe recipe=recipes.get(item);
        if (recipe!=null) {
            int batches=Math.ceilDiv(deficit,recipe.output());
            for (var e:recipe.requirements().entrySet()) obtain(e.getKey(),e.getValue(),stock,steps,visiting);
            for (var e:recipe.ingredients().entrySet()) {
                int quantity=Math.multiplyExact(e.getValue(),batches);
                obtain(e.getKey(),quantity,stock,steps,visiting);
                stock.merge(e.getKey(),-quantity,Math::addExact);
            }
            stock.merge(item,Math.multiplyExact(batches,recipe.output()),Math::addExact);
            steps.add(new Step(recipe.kind(),item,batches));
        } else if (sources.containsKey(item)) {
            for (var e:sources.get(item).entrySet()) obtain(e.getKey(),e.getValue(),stock,steps,visiting);
            stock.merge(item,deficit,Math::addExact); steps.add(new Step("collect",item,deficit));
        } else throw new IllegalArgumentException("No acquisition strategy: "+item);
        visiting.remove(item);
    }
}
