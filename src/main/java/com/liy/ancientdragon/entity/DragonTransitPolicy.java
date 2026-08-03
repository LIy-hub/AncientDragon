package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.boss.AncientDragonBossState;

/** Collision policy for scripted movement through the authored Sacred Mountain. */
final class DragonTransitPolicy {
    private DragonTransitPolicy() {
    }

    static boolean bypassesTerrainCollision(AncientDragonBossState state) {
        return state == AncientDragonBossState.RETURNING
                || state == AncientDragonBossState.APPROACHING
                || state == AncientDragonBossState.LANDING
                || state == AncientDragonBossState.COMBAT_RETURNING
                || state == AncientDragonBossState.COMBAT_APPROACHING
                || state == AncientDragonBossState.COMBAT_LANDING
                || state == AncientDragonBossState.PERCH_APPROACH
                || state == AncientDragonBossState.DEATH_RETURNING;
    }

    static boolean blocksPlayers(AncientDragonBossState state) {
        return state != AncientDragonBossState.CORPSE;
    }
}
