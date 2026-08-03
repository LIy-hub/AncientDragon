package com.liy.ancientdragon.animation.pose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import org.junit.jupiter.api.Test;

final class DragonPoseDynamicsTest {
    @Test
    void headEndLeadsNeckBaseAndTailDelayIncreasesTowardTheTip() {
        DragonPoseState neutral = DragonPoseState.IDENTITY;
        DragonPoseState target = new DragonPoseState(
                DragonQuaternion.IDENTITY,
                Curve.ZERO,
                new Curve(0.0D, Math.toRadians(30.0D), 0.0D),
                Math.toRadians(90.0D),
                Math.toRadians(20.0D),
                AttentionMode.THREAT,
                3,
                Maneuver.NONE);
        DragonPoseDynamics dynamics = new DragonPoseDynamics();
        dynamics.advance(neutral);
        for (int tick = 0; tick < 4; tick++) {
            dynamics.advance(target);
        }
        DragonStagedPose staged = dynamics.snapshot();
        assertTrue(staged.neckYaw(4) > staged.neckYaw(3));
        assertTrue(staged.neckYaw(3) > staged.neckYaw(0));
        assertTrue(staged.tail(0).yawRadians() > staged.tail(7).yawRadians());
        assertTrue(staged.tail(7).yawRadians() > 0.0D);
    }

    @Test
    void packedVanillaEntityDataRepresentationKeepsHeadPrecisionBelowTwoDegrees() {
        double[] yaw = new double[DragonPoseMath.neckBoneCount()];
        double[] pitch = new double[yaw.length];
        Curve[] tail = new Curve[DragonPoseMath.tailBoneCount()];
        for (int index = 0; index < yaw.length; index++) {
            yaw[index] = Math.toRadians(-122.0D + index * 7.3D);
            pitch[index] = Math.toRadians(41.0D - index * 3.1D);
        }
        for (int index = 0; index < tail.length; index++) {
            tail[index] = new Curve(
                    Math.toRadians(index * 0.7D),
                    Math.toRadians(-29.0D + index * 1.8D),
                    Math.toRadians(5.0D - index * 0.4D));
        }
        DragonStagedPose original = new DragonStagedPose(yaw, pitch, tail);
        int[] packedNeck = new int[yaw.length];
        int[] packedTail = new int[tail.length];
        for (int index = 0; index < packedNeck.length; index++) {
            packedNeck[index] = original.packedNeck(index);
        }
        for (int index = 0; index < packedTail.length; index++) {
            packedTail[index] = original.packedTail(index);
        }
        DragonStagedPose decoded = DragonStagedPose.unpack(packedNeck, packedTail);
        for (int index = 0; index < yaw.length; index++) {
            assertEquals(yaw[index], decoded.neckYaw(index), Math.toRadians(0.01D));
            assertEquals(pitch[index], decoded.neckPitch(index), Math.toRadians(0.01D));
        }
        for (int index = 0; index < tail.length; index++) {
            assertEquals(tail[index].yawRadians(), decoded.tail(index).yawRadians(), Math.toRadians(0.08D));
        }
    }

    @Test
    void tailTipLagsThenReboundsWithoutExceedingItsSafetyEnvelope() {
        DragonPoseDynamics dynamics = new DragonPoseDynamics();
        dynamics.advance(DragonPoseState.IDENTITY);
        DragonPoseState bent = poseWithTailYaw(140.0D);
        for (int tick = 0; tick < 4; tick++) {
            dynamics.advance(bent);
        }
        DragonStagedPose early = dynamics.snapshot();
        assertTrue(early.tail(0).yawRadians() > early.tail(7).yawRadians());

        double maximumTipDegrees = 0.0D;
        for (int tick = 0; tick < 36; tick++) {
            dynamics.advance(bent);
            maximumTipDegrees = Math.max(
                    maximumTipDegrees,
                    Math.toDegrees(dynamics.snapshot().tail(7).yawRadians()));
        }
        assertTrue(maximumTipDegrees > 140.5D, "maximumTip=" + maximumTipDegrees);
        assertTrue(maximumTipDegrees <= 150.01D, "maximumTip=" + maximumTipDegrees);

        DragonPoseState released = poseWithTailYaw(0.0D);
        boolean crossedNeutral = false;
        for (int tick = 0; tick < 50; tick++) {
            dynamics.advance(released);
            DragonStagedPose staged = dynamics.snapshot();
            crossedNeutral |= staged.tail(7).yawRadians() < 0.0D;
            for (int index = 0; index < DragonPoseMath.tailBoneCount(); index++) {
                assertTrue(Math.abs(Math.toDegrees(staged.tail(index).yawRadians())) <= 150.01D);
            }
        }
        assertTrue(crossedNeutral, "The elastic tail tip should pass neutral once before settling");
    }

    @Test
    void twelveBitYawEncodingPreservesExtremeCurvesAndReadsLegacySaves() {
        double[] yaw = new double[DragonPoseMath.neckBoneCount()];
        double[] pitch = new double[yaw.length];
        Curve[] tail = new Curve[DragonPoseMath.tailBoneCount()];
        for (int index = 0; index < tail.length; index++) {
            tail[index] = new Curve(
                    Math.toRadians(12.0D),
                    Math.toRadians(index % 2 == 0 ? 149.0D : -149.0D),
                    Math.toRadians(-8.0D));
        }
        DragonStagedPose original = new DragonStagedPose(yaw, pitch, tail);
        int[] packedNeck = new int[yaw.length];
        int[] packedTail = new int[tail.length];
        int[] legacyTail = new int[tail.length];
        for (int index = 0; index < packedNeck.length; index++) {
            packedNeck[index] = original.packedNeck(index);
        }
        for (int index = 0; index < packedTail.length; index++) {
            packedTail[index] = original.packedTail(index);
            legacyTail[index] = original.packedTailLegacyV1(index);
        }
        DragonStagedPose decoded = DragonStagedPose.unpack(packedNeck, packedTail);
        for (int index = 0; index < tail.length; index++) {
            assertEquals(tail[index].yawRadians(), decoded.tail(index).yawRadians(), Math.toRadians(0.08D));
        }

        Curve[] legacyRepresentableTail = new Curve[tail.length];
        for (int index = 0; index < legacyRepresentableTail.length; index++) {
            legacyRepresentableTail[index] = new Curve(0.0D, Math.toRadians(-40.0D + index), 0.0D);
        }
        DragonStagedPose legacyOriginal = new DragonStagedPose(yaw, pitch, legacyRepresentableTail);
        for (int index = 0; index < legacyTail.length; index++) {
            legacyTail[index] = legacyOriginal.packedTailLegacyV1(index);
        }
        DragonStagedPose migrated = DragonStagedPose.unpackLegacyV1(packedNeck, legacyTail);
        for (int index = 0; index < legacyTail.length; index++) {
            assertEquals(
                    legacyRepresentableTail[index].yawRadians(),
                    migrated.tail(index).yawRadians(),
                    Math.toRadians(0.08D));
        }
    }

    private static DragonPoseState poseWithTailYaw(double yawDegrees) {
        return new DragonPoseState(
                DragonQuaternion.IDENTITY,
                Curve.ZERO,
                new Curve(0.0D, Math.toRadians(yawDegrees), 0.0D),
                0.0D,
                0.0D,
                AttentionMode.SCAN,
                -1,
                Maneuver.NONE);
    }
}
