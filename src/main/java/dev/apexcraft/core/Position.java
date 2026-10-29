package dev.apexcraft.core;

public record Position(int x, int y, int z) {
    public Position add(int dx, int dy, int dz) { return new Position(x+dx,y+dy,z+dz); }
    public double distance(Position other) {
        return Math.sqrt(Math.pow((double)x-other.x,2)+Math.pow((double)y-other.y,2)+Math.pow((double)z-other.z,2));
    }
}
