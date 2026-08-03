package com.liy.ancientdragon.animation.pose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class DragonQuaternionTest {
    private static final double EPSILON = 1.0e-7D;

    @Test
    void canonicalYawAndPitchRoundTripThroughForwardVector() {
        DragonQuaternion yaw = DragonQuaternion.fromMinecraftYaw(90.0F);
        assertVector(new Vec3(-1.0D, 0.0D, 0.0D), yaw.forward());
        assertEquals(90.0F, yaw.minecraftYawDegrees(), 1.0e-4F);

        DragonQuaternion climb = DragonQuaternion.lookRotation(
                new Vec3(0.0D, 1.0D, 1.0D).normalize(), new Vec3(0.0D, 1.0D, 0.0D));
        assertEquals(-45.0F, climb.minecraftPitchDegrees(), 1.0e-4F);
    }

    @Test
    void slerpAndFullRollStayFiniteAndReversible() {
        DragonQuaternion start = DragonQuaternion.lookRotation(
                new Vec3(0.2D, 0.4D, 0.8D), new Vec3(0.0D, 1.0D, 0.0D));
        DragonQuaternion rolled = start;
        for (int index = 0; index < 60; index++) {
            rolled = rolled.multiply(DragonQuaternion.axisAngle(
                    new Vec3(0.0D, 0.0D, 1.0D), Math.PI * 2.0D / 60.0D));
        }
        assertTrue(start.angularDistance(rolled) < 1.0e-6D);
        assertEquals(1.0D, start.slerp(rolled, 0.5D).forward().length(), EPSILON);
    }

    @Test
    void compactCurvesExpandToSpecifiedVisibleBodyAndTailTotals() {
        DragonPoseState pose = new DragonPoseState(
                DragonQuaternion.IDENTITY,
                new DragonPoseState.Curve(0.0D, Math.toRadians(64.0D), 0.0D),
                new DragonPoseState.Curve(0.0D, Math.toRadians(-140.0D), 0.0D),
                Math.toRadians(135.0D),
                0.0D,
                DragonPoseState.AttentionMode.THREAT,
                42,
                DragonPoseState.Maneuver.NONE);
        Map<String, DragonPoseMath.BoneRotation> rotations =
                DragonPoseMath.solve(pose, "ancient_dragon:fly_cruise");
        double bodyTotal = rotations.get("root").yawRadians()
                + rotations.get("pelvis").yawRadians()
                + rotations.get("spine_01").yawRadians()
                + rotations.get("chest").yawRadians();
        double tailTotal = 0.0D;
        for (int index = 1; index <= 8; index++) {
            tailTotal += rotations.get("tail_%02d".formatted(index)).yawRadians();
        }
        assertEquals(Math.toRadians(64.0D), bodyTotal, EPSILON);
        assertEquals(Math.toRadians(-140.0D), tailTotal, EPSILON);
        assertTrue(Math.abs(rotations.get("root").yawRadians())
                < Math.abs(rotations.get("pelvis").yawRadians()));
        assertTrue(Math.abs(rotations.get("pelvis").yawRadians())
                        + Math.abs(rotations.get("spine_01").yawRadians())
                > Math.abs(bodyTotal) * 0.5D);
        assertFalse(rotations.containsKey("neck_01"),
                "Head/neck attention must be solved by the rig-aware quaternion solver");
    }

    @Test
    void diveStrikeKeepsTheFullRequestedAbdominalPitchCurve() {
        DragonPoseState pose = new DragonPoseState(
                DragonQuaternion.IDENTITY,
                new DragonPoseState.Curve(Math.toRadians(56.0D), 0.0D, 0.0D),
                DragonPoseState.Curve.ZERO,
                0.0D,
                0.0D,
                DragonPoseState.AttentionMode.ATTACK_FOCUS,
                42,
                DragonPoseState.Maneuver.NONE);
        Map<String, DragonPoseMath.BoneRotation> rotations =
                DragonPoseMath.solve(pose, "ancient_dragon:dive_strike");
        double pitchTotal = rotations.get("root").pitchRadians()
                + rotations.get("pelvis").pitchRadians()
                + rotations.get("spine_01").pitchRadians()
                + rotations.get("chest").pitchRadians();
        assertEquals(Math.toRadians(56.0D), pitchTotal, EPSILON);
    }

    @Test
    void rootBankDirectlyProducesAsymmetricWingLoadingAndSmallLegSway() {
        DragonPoseState banked = new DragonPoseState(
                DragonQuaternion.axisAngle(new Vec3(0.0D, 0.0D, 1.0D), Math.toRadians(65.0D)),
                DragonPoseState.Curve.ZERO,
                DragonPoseState.Curve.ZERO,
                0.0D,
                0.0D,
                DragonPoseState.AttentionMode.SCAN,
                -1,
                DragonPoseState.Maneuver.NONE);
        Map<String, DragonPoseMath.BoneRotation> rotations =
                DragonPoseMath.solve(banked, "ancient_dragon:fly_cruise");
        DragonPoseMath.BoneRotation left = rotations.get("wing_main_01.L");
        DragonPoseMath.BoneRotation right = rotations.get("wing_main_01.R");
        assertTrue(left.pitchRadians() * right.pitchRadians() < 0.0D);
        assertEquals(Math.toRadians(-10.0D), left.rollRadians(), EPSILON);
        assertEquals(Math.toRadians(-10.0D), right.rollRadians(), EPSILON);
        assertTrue(Math.abs(rotations.get("thigh.L").rollRadians()) <= Math.toRadians(3.0D));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
