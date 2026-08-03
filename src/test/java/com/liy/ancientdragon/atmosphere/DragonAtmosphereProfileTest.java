package com.liy.ancientdragon.atmosphere;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonAtmosphereProfileTest {
    @Test
    void stormFogKeepsTheCloseCombatReadabilityWindow() {
        DragonAtmosphereProfile storm = DragonAtmosphereProfile.forEncounter(
                DragonAtmospherePhase.STORM, DragonAtmosphereState.ACTIVE, 96.0D, 1.0F);

        assertTrue(storm.active());
        assertTrue(storm.fogStart() >= 12.0F);
        assertTrue(storm.fogEnd() > storm.fogStart());
        assertTrue(storm.fogEnd() < 100.0F);
    }

    @Test
    void atmosphereFadesOutAtTheConfiguredLocalRange() {
        DragonAtmosphereProfile near = DragonAtmosphereProfile.forEncounter(
                DragonAtmospherePhase.SOLAR, DragonAtmosphereState.ACTIVE, 110.0D, 1.0F);
        DragonAtmosphereProfile far = DragonAtmosphereProfile.forEncounter(
                DragonAtmospherePhase.SOLAR,
                DragonAtmosphereState.ACTIVE,
                DragonAtmosphereProfile.EFFECT_RANGE_BLOCKS,
                1.0F);

        assertTrue(near.active());
        assertFalse(far.active());
    }

    @Test
    void dormantMountainKeepsLightAmbientFogWhileCorpseClearsIt() {
        assertFalse(DragonAtmosphereProfile.forEncounter(
                        DragonAtmospherePhase.DEATH, DragonAtmosphereState.CORPSE, 20.0D, 1.0F)
                .active());
        DragonAtmosphereProfile dormant = DragonAtmosphereProfile.forEncounter(
                DragonAtmospherePhase.MOUNTAIN, DragonAtmosphereState.DORMANT, 20.0D, 1.0F);
        assertTrue(dormant.active());
        assertTrue(dormant.fogStart() >= 22.0F);
        assertTrue(dormant.fogEnd() >= 84.0F);
        assertTrue(dormant.colorWeight() > 0.25F);
    }

    @Test
    void breathingFogAlternatesBetweenConcealmentAndRevealWithoutSharpSteps() {
        float minimum = Float.MAX_VALUE;
        float maximum = Float.MIN_VALUE;
        float previous = DragonAtmosphereProfile.breathingMultiplier(0L);
        for (long tick = 1; tick <= 2_000; tick++) {
            float current = DragonAtmosphereProfile.breathingMultiplier(tick);
            minimum = Math.min(minimum, current);
            maximum = Math.max(maximum, current);
            assertTrue(Math.abs(current - previous) < 0.01F);
            previous = current;
        }
        assertTrue(minimum >= 0.74F && minimum < 0.82F, "minimum=" + minimum);
        assertTrue(maximum > 0.96F && maximum <= 1.0F, "maximum=" + maximum);

        DragonAtmosphereProfile thin = DragonAtmosphereProfile.forEncounter(
                DragonAtmospherePhase.MOUNTAIN, DragonAtmosphereState.ACTIVE, 40.0D, minimum);
        DragonAtmosphereProfile dense = DragonAtmosphereProfile.forEncounter(
                DragonAtmospherePhase.MOUNTAIN, DragonAtmosphereState.ACTIVE, 40.0D, maximum);
        assertTrue(thin.fogEnd() - dense.fogEnd() >= 35.0F,
                "reveal gap=" + (thin.fogEnd() - dense.fogEnd()));
    }
}
