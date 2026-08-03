package com.liy.ancientdragon.flight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AncientDragonFlightManagerTest {
    @Test
    void only_a_survival_player_wearing_the_sky_wing_receives_creative_flight() {
        assertTrue(SkyWingFlightRules.grantsCreativeFlight(false, false, true));
        assertFalse(SkyWingFlightRules.grantsCreativeFlight(false, false, false));
        assertFalse(SkyWingFlightRules.grantsCreativeFlight(true, false, true));
        assertFalse(SkyWingFlightRules.grantsCreativeFlight(false, true, true));
    }
}
