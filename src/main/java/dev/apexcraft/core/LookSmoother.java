package dev.apexcraft.core;

/** Bounded visible turning for comfortable camera motion, with no randomized jitter. */
public final class LookSmoother {
    private LookSmoother() {}
    public static float difference(float current,float target,boolean yaw) {
        if (!Float.isFinite(current) || !Float.isFinite(target)) throw new IllegalArgumentException("Invalid rotation");
        double difference=(double)target-current;
        if (yaw) difference=(difference%360+540)%360-180;
        return (float)difference;
    }
    public static float step(float current,float target,float maxDegrees,boolean yaw) {
        if (!Float.isFinite(maxDegrees) || maxDegrees<=0) throw new IllegalArgumentException("Invalid rotation speed");
        float difference=difference(current,target,yaw);
        return current+Math.max(-maxDegrees,Math.min(maxDegrees,difference*0.5f));
    }
}
