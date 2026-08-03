package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AncientDragonAttackGeometryTest {
    @Test
    void tailUsesTheApprovedContactWindow() {
        assertFalse(AncientDragonAttackGeometry.tailContactActive(15));
        assertTrue(AncientDragonAttackGeometry.tailContactActive(16));
        assertTrue(AncientDragonAttackGeometry.tailContactActive(64));
        assertFalse(AncientDragonAttackGeometry.tailContactActive(65));
    }

    @Test
    void stormTelegraphAdvancesFromMarkToStrike() {
        assertEquals(0.0D, AncientDragonAttackGeometry.stormTelegraphProgress(32));
        assertEquals(0.5D, AncientDragonAttackGeometry.stormTelegraphProgress(45));
        assertEquals(1.0D, AncientDragonAttackGeometry.stormTelegraphProgress(58));
        assertTrue(AncientDragonAttackGeometry.stormTargetTracksPlayer(32));
        assertTrue(AncientDragonAttackGeometry.stormTargetTracksPlayer(48));
        assertFalse(AncientDragonAttackGeometry.stormTargetTracksPlayer(49));
        assertEquals(5.0D, AncientDragonAttackGeometry.STORM_STRIKE_RADIUS_BLOCKS);
    }

    @Test
    void targetPredictionLeadsMovementWithoutAllowingExtremeJumps() {
        Vec3 position = new Vec3(10.0D, 20.0D, 30.0D);
        assertEquals(
                new Vec3(14.0D, 20.0D, 30.0D),
                AncientDragonAttackGeometry.leadTarget(position, new Vec3(0.5D, 0.0D, 0.0D), 8, 12.0D));
        assertEquals(
                new Vec3(22.0D, 20.0D, 30.0D),
                AncientDragonAttackGeometry.leadTarget(position, new Vec3(10.0D, 0.0D, 0.0D), 8, 12.0D));
    }

    @Test
    void lightningChainTracksThenLocksBeforeEachStrike() {
        assertTrue(AncientDragonAttackGeometry.lightningChainTracksTarget(32));
        assertFalse(AncientDragonAttackGeometry.lightningChainTracksTarget(33));
        assertTrue(AncientDragonAttackGeometry.lightningChainTelegraphActive(22));
        assertEquals(0.0D, AncientDragonAttackGeometry.lightningChainTelegraphProgress(22));
        assertEquals(1.0D, AncientDragonAttackGeometry.lightningChainTelegraphProgress(40));
        assertTrue(AncientDragonAttackGeometry.lightningChainStrikes(40));
        assertTrue(AncientDragonAttackGeometry.lightningChainStrikes(62));
        assertTrue(AncientDragonAttackGeometry.lightningChainStrikes(84));
    }

    @Test
    void spreadAndCageGeometryRemainSymmetric() {
        var spread = AncientDragonAttackGeometry.spreadDirections(
                new Vec3(0.0D, 0.0D, 1.0D), Math.toRadians(15.0D));
        assertEquals(3, spread.size());
        assertEquals(0.0D, spread.get(0).x + spread.get(2).x, 1.0e-9D);
        assertEquals(spread.get(0).z, spread.get(2).z, 1.0e-9D);

        var ring = AncientDragonAttackGeometry.ringPositions(Vec3.ZERO, 13.0D, 8);
        assertEquals(8, ring.size());
        for (Vec3 point : ring) {
            assertEquals(13.0D, point.horizontalDistance(), 1.0e-9D);
        }
    }

    @Test
    void solarSweepCrossesTheLockedCenterAndKeepsItsElevation() {
        Vec3 pivot = new Vec3(0.0D, 100.0D, 0.0D);
        Vec3 center = new Vec3(0.0D, 12.0D, 100.0D);
        Vec3 start = AncientDragonAttackGeometry.solarSweepTarget(pivot, center, 54);
        Vec3 middle = AncientDragonAttackGeometry.solarSweepTarget(pivot, center, 74);
        Vec3 end = AncientDragonAttackGeometry.solarSweepTarget(pivot, center, 94);

        assertTrue(start.x > 0.0D);
        assertEquals(center, middle);
        assertTrue(end.x < 0.0D);
        assertEquals(center.y, start.y);
        assertEquals(center.y, end.y);
    }

    @Test
    void solarBeamLengthIsBoundedButCanReachAcrossTheArenaFloor() {
        Vec3 start = Vec3.ZERO;
        assertEquals(4.5D, AncientDragonAttackGeometry.SOLAR_BEAM_RADIUS_BLOCKS);
        assertEquals(90.0D, AncientDragonAttackGeometry.solarBeamLength(start, new Vec3(20.0D, 0.0D, 0.0D)));
        assertEquals(138.0D, AncientDragonAttackGeometry.solarBeamLength(start, new Vec3(120.0D, 0.0D, 0.0D)));
        assertEquals(280.0D, AncientDragonAttackGeometry.solarBeamLength(start, new Vec3(400.0D, 0.0D, 0.0D)));
    }

    @Test
    void sweptContactCatchesAPlayerBetweenTwoTailFrames() {
        assertTrue(AncientDragonAttackGeometry.sweptContact(
                new Vec3(5.0D, 0.0D, 0.0D),
                Vec3.ZERO,
                new Vec3(10.0D, 0.0D, 0.0D),
                1.0D));
        assertFalse(AncientDragonAttackGeometry.sweptContact(
                new Vec3(5.0D, 2.0D, 0.0D),
                Vec3.ZERO,
                new Vec3(10.0D, 0.0D, 0.0D),
                1.0D));
    }

    @Test
    void wingShockwaveHasSeparateHorizontalAndVerticalLimits() {
        Vec3 origin = new Vec3(0.0D, 10.0D, 0.0D);
        assertTrue(AncientDragonAttackGeometry.groundShockwaveContact(
                origin, new Vec3(40.0D, 20.0D, 0.0D), 42.0D, 18.0D));
        assertFalse(AncientDragonAttackGeometry.groundShockwaveContact(
                origin, new Vec3(43.0D, 20.0D, 0.0D), 42.0D, 18.0D));
        assertFalse(AncientDragonAttackGeometry.groundShockwaveContact(
                origin, new Vec3(40.0D, 29.0D, 0.0D), 42.0D, 18.0D));
    }
}
