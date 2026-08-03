package com.liy.ancientdragon.boss;

import java.util.Locale;

/** Server-authoritative lifecycle state for the first playable encounter slice. */
public enum AncientDragonBossState {
    DORMANT,
    AWAKENING,
    TAKEOFF,
    DEPARTING,
    CRUISING,
    PERCH_APPROACH,
    PERCHED,
    ATTACKING,
    COMBAT_RETURNING,
    COMBAT_APPROACHING,
    COMBAT_LANDING,
    GROUND_COMBAT,
    RETURNING,
    APPROACHING,
    LANDING,
    DEATH_RETURNING,
    DYING,
    CORPSE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AncientDragonBossState fromSerializedName(String value) {
        for (AncientDragonBossState state : values()) {
            if (state.serializedName().equals(value)) {
                return state;
            }
        }
        return DORMANT;
    }
}
