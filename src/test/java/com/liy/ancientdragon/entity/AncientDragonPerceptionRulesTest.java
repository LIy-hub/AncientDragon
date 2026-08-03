package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.boss.AncientDragonBossState;
import org.junit.jupiter.api.Test;

final class AncientDragonPerceptionRulesTest {
    @Test
    void creativePlayersRemainValidParticipantsWhileSpectatorsDoNot() {
        assertTrue(AncientDragonPerceptionRules.canParticipate(true, false));
        assertFalse(AncientDragonPerceptionRules.canParticipate(true, true));
        assertFalse(AncientDragonPerceptionRules.canParticipate(false, false));
    }

    @Test
    void arenaThreatsRemainSensedThroughTerrainAndStorms() {
        assertTrue(AncientDragonPerceptionRules.sensesThreat(true, false));
        assertTrue(AncientDragonPerceptionRules.sensesThreat(false, true));
        assertFalse(AncientDragonPerceptionRules.sensesThreat(false, false));
    }

    @Test
    void groundedCombatKeepsVisibleHeadTracking() {
        assertTrue(AncientDragonPerceptionRules.tracksTargetPose(AncientDragonBossState.GROUND_COMBAT));
        assertTrue(AncientDragonPerceptionRules.tracksTargetPose(AncientDragonBossState.PERCHED));
        assertFalse(AncientDragonPerceptionRules.tracksTargetPose(AncientDragonBossState.DORMANT));
        assertFalse(AncientDragonPerceptionRules.tracksTargetPose(AncientDragonBossState.CORPSE));
    }
}
