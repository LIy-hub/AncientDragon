package com.liy.ancientdragon.entity;

/** Shared world-size multiplier applied on top of the previously shipped dragon scale. */
public final class AncientDragonScale {
    public static final float MULTIPLIER = 1.6F;

    private AncientDragonScale() {
    }

    public static float blocks(float previousBlocks) {
        return previousBlocks * MULTIPLIER;
    }

    public static double blocks(double previousBlocks) {
        return previousBlocks * MULTIPLIER;
    }
}
