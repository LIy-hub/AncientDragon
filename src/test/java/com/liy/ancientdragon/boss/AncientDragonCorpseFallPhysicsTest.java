package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AncientDragonCorpseGravityTest {
    @Test
    void corpseAcceleratesDownwardUntilItsSafeTerminalFallSpeed() {
        assertEquals(-0.04D, AncientDragonCorpseFallPhysics.nextVerticalVelocity(0.0D), 0.000001D);
        assertEquals(-0.24D, AncientDragonCorpseFallPhysics.nextVerticalVelocity(-0.20D), 0.000001D);
        assertEquals(-0.72D, AncientDragonCorpseFallPhysics.nextVerticalVelocity(-0.70D), 0.000001D);
        assertEquals(-0.72D, AncientDragonCorpseFallPhysics.nextVerticalVelocity(-4.0D), 0.000001D);
    }

    @Test
    void corpseNeverKeepsAnUpwardOrInvalidFlightVelocity() {
        assertEquals(-0.04D, AncientDragonCorpseFallPhysics.nextVerticalVelocity(0.3D), 0.000001D);
        assertEquals(-0.04D, AncientDragonCorpseFallPhysics.nextVerticalVelocity(Double.NaN), 0.000001D);
    }
}
