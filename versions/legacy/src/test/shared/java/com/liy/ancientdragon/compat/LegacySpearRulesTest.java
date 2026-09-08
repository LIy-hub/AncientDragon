package com.liy.ancientdragon.compat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LegacySpearRulesTest {
    @Test void chargeDelayPreventsImmediateKineticDamage() {
        assertFalse(LegacySpearRules.kinetic(7, 30, 0, true).damage());
        assertTrue(LegacySpearRules.kinetic(8, 4.6, 0, true).damage());
    }
    @Test void approachingTargetsContributeToRelativeSpeed() {
        assertTrue(LegacySpearRules.kinetic(8, 0, -5, true).damage());
        assertFalse(LegacySpearRules.kinetic(8, 10, 9, true).damage());
    }
    @Test void dismountAndKnockbackWindowsExpireIndependently() {
        assertTrue(LegacySpearRules.kinetic(58, 10, 0, true).dismount());
        assertFalse(LegacySpearRules.kinetic(59, 10, 0, true).dismount());
        assertTrue(LegacySpearRules.kinetic(118, 6, 0, true).knockback());
        assertFalse(LegacySpearRules.kinetic(119, 6, 0, true).knockback());
    }
    @Test void heldSpearCannotDamageAfterMaximumWindow() {
        assertTrue(LegacySpearRules.kinetic(183, 20, 0, true).damage());
        assertFalse(LegacySpearRules.kinetic(184, 20, 0, true).damage());
    }
    @Test void kineticDamageMatchesUpstreamMultiplierAndFloor() {
        assertEquals(6, LegacySpearRules.kinetic(20, 5.4, 0, true).kineticDamage());
        assertEquals(0, LegacySpearRules.kinetic(20, 1, 3, true).kineticDamage());
    }
    @Test void nonPlayerContactUsesVanillaReducedSpeedThreshold() {
        assertFalse(LegacySpearRules.kinetic(20, 1, 0, true).damage());
        assertTrue(LegacySpearRules.kinetic(20, 1, 0, false).damage());
    }
}
