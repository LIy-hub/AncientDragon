package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SacredMountainEnvironmentServiceTest {
    @Test
    void onlyHostilesInsideTheMountainAreSuppressed() {
        assertTrue(SacredMountainEnvironmentService.shouldSuppressHostile(true, true));
        assertFalse(SacredMountainEnvironmentService.shouldSuppressHostile(true, false));
        assertFalse(SacredMountainEnvironmentService.shouldSuppressHostile(false, true));
    }
}
