package dev.apexcraft.core;
import java.util.Optional;

public interface ResourceDriver extends MovementDriver {
    void beginScan(String item);
    Optional<Position> scan(int budget);
    boolean scanComplete();
    boolean sourcePresent(Position position,String item);
    boolean canReach(Position position);
    void mine(Position position);
    Position approach(Position position);
}
