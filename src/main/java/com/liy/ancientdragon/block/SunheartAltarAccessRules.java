package com.liy.ancientdragon.block;

/** Server-side guardrail for a GUI request that targets a placed Sunheart Altar. */
public final class SunheartAltarAccessRules {
    public static final double MAXIMUM_USE_DISTANCE_SQUARED = 64.0D;

    private SunheartAltarAccessRules() {
    }

    public static boolean mayUse(boolean isSunheartAltar, double distanceSquared) {
        return isSunheartAltar && distanceSquared <= MAXIMUM_USE_DISTANCE_SQUARED;
    }
}
