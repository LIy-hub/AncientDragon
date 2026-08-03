package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AncientDragonCorpseDissipationTest {
    @Test
    void dissipationRunsForTenSecondsBeforeTheCorpseCanBeRemoved() {
        assertEquals(200, AncientDragonCorpseDissipation.DURATION_TICKS);
        assertFalse(AncientDragonCorpseDissipation.isActive(-1));
        assertFalse(AncientDragonCorpseDissipation.isComplete(199));
        assertTrue(AncientDragonCorpseDissipation.isActive(199));
        assertTrue(AncientDragonCorpseDissipation.isComplete(200));
        assertFalse(AncientDragonCorpseDissipation.isActive(200));
    }

    @Test
    void progressAndEffectsIncreaseMonotonicallyWithoutOverflowingTheDuration() {
        int elapsed = 0;
        for (int tick = 0; tick < AncientDragonCorpseDissipation.DURATION_TICKS + 20; tick++) {
            int next = AncientDragonCorpseDissipation.advance(elapsed);
            assertTrue(next >= elapsed);
            assertTrue(next <= AncientDragonCorpseDissipation.DURATION_TICKS);
            elapsed = next;
        }

        assertEquals(0.0F, AncientDragonCorpseDissipation.progress(-1));
        assertEquals(0.5F, AncientDragonCorpseDissipation.progress(100));
        assertEquals(1.0F, AncientDragonCorpseDissipation.progress(200));
        assertTrue(AncientDragonCorpseDissipation.ashParticles(200)
                > AncientDragonCorpseDissipation.ashParticles(0));
        assertTrue(AncientDragonCorpseDissipation.soulFlameParticles(200)
                > AncientDragonCorpseDissipation.soulFlameParticles(0));
    }
}
