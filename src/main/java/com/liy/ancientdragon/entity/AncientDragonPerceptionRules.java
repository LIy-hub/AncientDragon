package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.boss.AncientDragonBossState;

/** Small pure policy for who the dragon can notice and when its rig may visibly track them. */
final class AncientDragonPerceptionRules {
    private AncientDragonPerceptionRules() {
    }

    static boolean canParticipate(boolean alive, boolean spectator) {
        return alive && !spectator;
    }

    static boolean sensesThreat(boolean activeParticipant, boolean lineOfSight) {
        // The dragon knows every combatant inside its own arena even through peaks, rain and clouds.
        return activeParticipant || lineOfSight;
    }

    static boolean tracksTargetPose(AncientDragonBossState state) {
        return state != AncientDragonBossState.DORMANT
                && state != AncientDragonBossState.DYING
                && state != AncientDragonBossState.CORPSE;
    }
}
