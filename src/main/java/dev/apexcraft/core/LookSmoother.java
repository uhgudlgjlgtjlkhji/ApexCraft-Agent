package dev.apexcraft.core;

/** Bounded visible turning for comfortable camera motion, with no randomized jitter. */
public final class LookSmoother {
    private LookSmoother() {}
    public static float step(float current,float target,float maxDegrees,boolean yaw) {
        if (!Float.isFinite(current) || !Float.isFinite(target) || !Float.isFinite(maxDegrees) || maxDegrees<=0)
            throw new IllegalArgumentException("Invalid rotation");
        float difference=target-current;
        if (yaw) difference=(difference%360+540)%360-180;
        return current+Math.max(-maxDegrees,Math.min(maxDegrees,difference*0.5f));
    }
}
