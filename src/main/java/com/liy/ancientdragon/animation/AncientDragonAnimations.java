package com.liy.ancientdragon.animation;

import com.liy.blendlib.api.BlendAnimationKey;

/** Server-safe semantic animation vocabulary; no model or rendering state lives here. */
public final class AncientDragonAnimations {
    public static final BlendAnimationKey DORMANT_HOLD =
            BlendAnimationKey.parse("ancient_dragon:dormant_hold");
    public static final BlendAnimationKey AWAKEN_INTRO =
            BlendAnimationKey.parse("ancient_dragon:awaken_intro");
    public static final BlendAnimationKey TAKEOFF =
            BlendAnimationKey.parse("ancient_dragon:takeoff");
    public static final BlendAnimationKey COMBAT_IDLE =
            BlendAnimationKey.parse("ancient_dragon:combat_idle");
    public static final BlendAnimationKey COMBAT_STAND =
            BlendAnimationKey.parse("ancient_dragon:combat_stand");
    public static final BlendAnimationKey GROUND_STALK =
            BlendAnimationKey.parse("ancient_dragon:ground_stalk");
    public static final BlendAnimationKey FLY_CRUISE =
            BlendAnimationKey.parse("ancient_dragon:fly_cruise");
    public static final BlendAnimationKey FLY_DESCEND =
            BlendAnimationKey.parse("ancient_dragon:fly_descend");
    public static final BlendAnimationKey LAND =
            BlendAnimationKey.parse("ancient_dragon:land");
    public static final BlendAnimationKey DIVE_STRIKE =
            BlendAnimationKey.parse("ancient_dragon:dive_strike");
    public static final BlendAnimationKey AERIAL_TAIL_SWEEP =
            BlendAnimationKey.parse("ancient_dragon:aerial_tail_sweep");
    public static final BlendAnimationKey AERIAL_STORM_BURST =
            BlendAnimationKey.parse("ancient_dragon:aerial_storm_burst");
    public static final BlendAnimationKey AERIAL_SOLAR_BREATH =
            BlendAnimationKey.parse("ancient_dragon:aerial_solar_breath");
    public static final BlendAnimationKey ROAR_STORM =
            BlendAnimationKey.parse("ancient_dragon:roar_storm");
    public static final BlendAnimationKey BITE_HEAVY =
            BlendAnimationKey.parse("ancient_dragon:bite_heavy");
    public static final BlendAnimationKey WING_SLAM =
            BlendAnimationKey.parse("ancient_dragon:wing_slam");
    public static final BlendAnimationKey SOLAR_BREATH =
            BlendAnimationKey.parse("ancient_dragon:solar_breath");
    public static final BlendAnimationKey HIT_REACT =
            BlendAnimationKey.parse("ancient_dragon:hit_react");
    public static final BlendAnimationKey DEATH_LANDMARK =
            BlendAnimationKey.parse("ancient_dragon:death_landmark");
    public static final BlendAnimationKey CORPSE_STATIC =
            BlendAnimationKey.parse("ancient_dragon:corpse_static");

    private AncientDragonAnimations() {
    }
}
