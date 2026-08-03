package com.liy.ancientdragon.boss;

/** Pure vertical-motion policy for an Ancient Dragon corpse after death. */
public final class AncientDragonCorpseFallPhysics {
    public static final double GRAVITY_PER_TICK = 0.04D;
    public static final double TERMINAL_FALL_SPEED = -0.72D;

    private AncientDragonCorpseFallPhysics() {
    }

    /** Drops a stationary or falling corpse by one tick without preserving a flight velocity. */
    public static double nextVerticalVelocity(double currentVerticalVelocity) {
        if (!Double.isFinite(currentVerticalVelocity)) {
            currentVerticalVelocity = 0.0D;
        }
        return Math.max(
                TERMINAL_FALL_SPEED,
                Math.min(0.0D, currentVerticalVelocity) - GRAVITY_PER_TICK);
    }
}
