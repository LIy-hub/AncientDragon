package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SacredMountainSoulFireServiceTest {
    @Test
    void emitsAtTheConfiguredFixedInterval() {
        assertTrue(SacredMountainSoulFireService.shouldEmitOn(0));
        assertTrue(SacredMountainSoulFireService.shouldEmitOn(40));
        assertFalse(SacredMountainSoulFireService.shouldEmitOn(39));
    }

    @Test
    void onlyUsesDryAirInsideTheMountainBesideABlock() {
        assertTrue(SacredMountainSoulFireService.canOccupy(true, true, true, true));
        assertFalse(SacredMountainSoulFireService.canOccupy(false, true, true, true));
        assertFalse(SacredMountainSoulFireService.canOccupy(true, false, true, true));
        assertFalse(SacredMountainSoulFireService.canOccupy(true, true, false, true));
        assertFalse(SacredMountainSoulFireService.canOccupy(true, true, true, false));
    }
}
