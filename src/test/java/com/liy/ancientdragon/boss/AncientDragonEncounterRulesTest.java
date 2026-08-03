package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Attack;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import com.liy.ancientdragon.entity.AncientDragonPartKind;
import org.junit.jupiter.api.Test;

final class AncientDragonEncounterRulesTest {
    @Test
    void healthScalingLocksToTheApprovedOneThroughEightPlayerCurve() {
        assertEquals(1, AncientDragonEncounterRules.normalizedParticipants(0));
        assertEquals(600.0F, AncientDragonEncounterRules.maxHealth(1));
        assertEquals(1_140.0F, AncientDragonEncounterRules.maxHealth(4));
        assertEquals(1_860.0F, AncientDragonEncounterRules.maxHealth(99));
        assertEquals(0.5F, AncientDragonEncounterRules.damageMultiplier(1), 0.0001F);
        assertEquals(0.5525F, AncientDragonEncounterRules.damageMultiplier(4), 0.0001F);
        assertEquals(0.6F, AncientDragonEncounterRules.damageMultiplier(8), 0.0001F);
        assertEquals(4, AncientDragonEncounterRules.simultaneousPressureTargets(8));
        assertEquals(60, AncientDragonEncounterRules.attackCooldownTicks(1));
        assertEquals(32, AncientDragonEncounterRules.attackCooldownTicks(8));
        assertEquals(4, AncientDragonEncounterRules.AIR_ATTACKS_PER_CYCLE);
    }

    @Test
    void healthBandsPreserveTheMountainStormSolarOrder() {
        assertEquals(Phase.MOUNTAIN, AncientDragonEncounterRules.phaseFor(410.0F, 600.0F));
        assertEquals(Phase.STORM, AncientDragonEncounterRules.phaseFor(300.0F, 600.0F));
        assertEquals(Phase.SOLAR, AncientDragonEncounterRules.phaseFor(200.0F, 600.0F));
        assertEquals(500, Phase.MOUNTAIN.groundWindowTicks());
        assertEquals(440, Phase.STORM.groundWindowTicks());
        assertEquals(360, Phase.SOLAR.groundWindowTicks());
    }

    @Test
    void attackTimingsMatchTheExportedGlbClips() {
        assertEquals(101, Attack.DIVE_STRIKE.durationTicks());
        assertEquals(81, Attack.TAIL_SWEEP.durationTicks());
        assertEquals(101, Attack.STORM_BURST.durationTicks());
        assertEquals(121, Attack.SOLAR_BREATH.durationTicks());
        assertEquals(101, Attack.LIGHTNING_CHAIN.durationTicks());
        assertEquals(81, Attack.WIND_BLADE.durationTicks());
        assertEquals(101, Attack.STORM_CAGE.durationTicks());
        assertEquals(49, Attack.BITE_HEAVY.durationTicks());
        assertEquals(81, Attack.WING_SLAM.durationTicks());
        assertEquals(81, Attack.RIFT_CLAW.durationTicks());
        assertEquals(81, Attack.GROUND_STORM_BURST.durationTicks());
        assertEquals(101, Attack.GROUND_SOLAR_BREATH.durationTicks());
    }

    @Test
    void arenaAndDisengageConstantsMatchTheLockedDesign() {
        assertEquals(256.0D, AncientDragonEncounterRules.AWAKEN_RADIUS_BLOCKS);
        assertEquals(96.0D, AncientDragonEncounterRules.ARENA_HORIZONTAL_BUFFER_BLOCKS);
        assertEquals(192.0D, AncientDragonEncounterRules.ARENA_UPPER_BUFFER_BLOCKS);
        assertEquals(600, AncientDragonEncounterRules.RETURN_TO_ROOST_DELAY_TICKS);
        assertEquals(400, AncientDragonEncounterRules.RECENT_THREAT_WINDOW_TICKS);
        assertEquals(100, AncientDragonEncounterRules.TARGET_LOCK_TICKS);
    }

    @Test
    void wingStabilityUsesDedicatedWeightsAndPerHitCap() {
        assertEquals(100.0F, AncientDragonEncounterRules.stabilityMaximum(1));
        assertEquals(190.0F, AncientDragonEncounterRules.stabilityMaximum(4));
        assertEquals(310.0F, AncientDragonEncounterRules.stabilityMaximum(8));
        assertEquals(0.0F, AncientDragonEncounterRules.stabilityDamage(AncientDragonPartKind.HEAD, 20.0F));
        assertEquals(11.0F, AncientDragonEncounterRules.stabilityDamage(
                AncientDragonPartKind.PRIMARY_WING_L_INNER, 10.0F));
        assertEquals(13.5F, AncientDragonEncounterRules.stabilityDamage(
                AncientDragonPartKind.PRIMARY_WING_L_MID, 10.0F));
        assertEquals(16.0F, AncientDragonEncounterRules.stabilityDamage(
                AncientDragonPartKind.PRIMARY_WING_L_TIP, 10.0F));
        assertTrue(AncientDragonEncounterRules.stabilityDamage(
                AncientDragonPartKind.PRIMARY_WING_L_TIP, 100.0F)
                <= AncientDragonEncounterRules.STABILITY_DAMAGE_PER_HIT_CAP);
    }
}
