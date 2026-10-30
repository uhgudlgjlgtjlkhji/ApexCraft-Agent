package dev.apexcraft.core;
import java.util.Map;

public interface ObjectiveDriver {
    Map<String,Integer> planningInventory(Observation world);
    Action collect(String item,int total);
    Action craft(String item);
    Action smelt(int amount);
    void releaseInputs();
    void decision(String message);
}
