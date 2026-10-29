package dev.apexcraft.core;
import java.util.List;

public interface MovementDriver {
    List<PathSearch.Edge> neighbors(Position position);
    boolean traversable(Position position);
    void moveToward(Position position);
    void releaseInputs();
}
