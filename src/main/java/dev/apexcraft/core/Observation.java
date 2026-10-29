package dev.apexcraft.core;

import java.util.Map;

public record Observation(long version, Position position, double health, int hunger,
                          Map<String,Integer> inventory) {
    public Observation {
        if (!Double.isFinite(health) || health < 0 || health > 20 || hunger < 0 || hunger > 20)
            throw new IllegalArgumentException("Invalid health/hunger");
        inventory = Map.copyOf(inventory);
        if (inventory.values().stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("Negative inventory");
    }
}
