package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.entity.AncientDragonPartKind;
import org.junit.jupiter.api.Test;

final class AncientDragonStabilityTest {
    @Test
    void onlyAcceptedWingDamageCanTriggerOneForcedLandingPerCycle() {
        AncientDragonStability stability = new AncientDragonStability();
        stability.resetForAirCycle(1);
        assertFalse(stability.applyAcceptedDamage(AncientDragonPartKind.HEAD, 100.0F));
        for (int hit = 0; hit < 5; hit++) {
            assertFalse(stability.applyAcceptedDamage(AncientDragonPartKind.PRIMARY_WING_L_TIP, 20.0F));
        }
        assertTrue(stability.applyAcceptedDamage(AncientDragonPartKind.PRIMARY_WING_L_TIP, 20.0F));
        assertFalse(stability.applyAcceptedDamage(AncientDragonPartKind.PRIMARY_WING_L_TIP, 100.0F));
    }

    @Test
    void joiningPlayersIncreaseThePoolWithoutErasingDamageFraction() {
        AncientDragonStability stability = new AncientDragonStability();
        stability.resetForAirCycle(1);
        stability.applyAcceptedDamage(AncientDragonPartKind.PRIMARY_WING_L_TIP, 20.0F);
        stability.rescaleMaximum(4);
        org.junit.jupiter.api.Assertions.assertEquals(190.0F, stability.maximum());
        org.junit.jupiter.api.Assertions.assertEquals(155.8F, stability.current(), 0.01F);
    }
}
