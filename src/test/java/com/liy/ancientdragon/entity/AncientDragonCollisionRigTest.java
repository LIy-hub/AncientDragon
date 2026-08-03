package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AncientDragonCollisionRigTest {
    @Test
    void loadsEveryRequiredClipAndPart() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();

        assertEquals(20, rig.clipCount());
        assertTrue(rig.boneCount() >= 40);
        assertEquals(18, AncientDragonPartKind.values().length);

        AncientDragonCollisionRig.Frame cruise = rig.sample(AncientDragonCollisionRig.CRUISE, 0, true);
        AncientDragonCollisionRig.SolvedFrame solved = rig.solve(
                cruise, DragonPoseState.IDENTITY, "ancient_dragon:fly_cruise");
        assertTrue(solved.position(AncientDragonPartKind.HEAD).z > 32.0D);
        assertTrue(solved.position(AncientDragonPartKind.TAIL_TIP).z < -48.0D);
        assertEquals(1.0D, solved.headForward().length(), 0.002D);
    }

    @Test
    void minecraftYawRotatesCanonicalForwardWithTheHost() {
        Vec3 positiveNinety = AncientDragonCollisionRig.rotateByMinecraftYaw(new Vec3(0.0D, 0.0D, 1.0D), 90.0F);
        Vec3 negativeNinety = AncientDragonCollisionRig.rotateByMinecraftYaw(new Vec3(0.0D, 0.0D, 1.0D), -90.0F);

        assertEquals(-1.0D, positiveNinety.x, 0.000001D);
        assertEquals(0.0D, positiveNinety.z, 0.000001D);
        assertEquals(1.0D, negativeNinety.x, 0.000001D);
        assertEquals(0.0D, negativeNinety.z, 0.000001D);
    }

    @Test
    void headIsTheStrongestTargetAndWingTipsRemainDamageable() {
        assertTrue(AncientDragonPartKind.HEAD.damageMultiplier()
                > AncientDragonPartKind.PRIMARY_WING_L_TIP.damageMultiplier());
        assertTrue(AncientDragonPartKind.HEAD.isHead());
        assertTrue(AncientDragonPartKind.TAIL_TIP.isTail());
        assertEquals(1.6F, AncientDragonScale.MULTIPLIER);
        assertEquals(32.0F, AncientDragonPartKind.PRIMARY_WING_L_TIP.width());
        assertEquals(8.0F, AncientDragonPartKind.PRIMARY_WING_L_TIP.height());
        assertEquals(4.0F, AncientDragonPartKind.PRIMARY_WING_L_TIP.depth());
    }

    @Test
    void fullPoseFkMovesHeadAndEveryAnchorRemainsFiniteAndReversible() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.Frame frame = rig.sample(AncientDragonCollisionRig.CRUISE, 29, true);
        DragonPoseState pose = new DragonPoseState(
                DragonQuaternion.axisAngle(new Vec3(0.0D, 0.0D, 1.0D), Math.toRadians(137.0D))
                        .multiply(DragonQuaternion.axisAngle(
                                new Vec3(1.0D, 0.0D, 0.0D), Math.toRadians(61.0D))),
                new Curve(Math.toRadians(56.0D), Math.toRadians(64.0D), Math.toRadians(8.0D)),
                new Curve(Math.toRadians(-30.0D), Math.toRadians(-140.0D), Math.toRadians(-6.0D)),
                Math.toRadians(112.0D),
                Math.toRadians(24.0D),
                AttentionMode.THREAT,
                7,
                Maneuver.ROLL_LEFT);
        AncientDragonCollisionRig.SolvedFrame solved = rig.solve(frame, pose, "ancient_dragon:fly_cruise");
        for (AncientDragonPartKind kind : AncientDragonPartKind.values()) {
            Vec3 model = solved.position(kind);
            Vec3 transformed = pose.orientation().rotate(model);
            Vec3 recovered = pose.orientation().conjugate().rotate(transformed);
            assertTrue(Double.isFinite(transformed.x) && Double.isFinite(transformed.y) && Double.isFinite(transformed.z));
            assertEquals(model.x, recovered.x, 1.0e-6D);
            assertEquals(model.y, recovered.y, 1.0e-6D);
            assertEquals(model.z, recovered.z, 1.0e-6D);
        }
        AncientDragonCollisionRig.SolvedFrame neutral = rig.solve(
                frame, DragonPoseState.IDENTITY, "ancient_dragon:fly_cruise");
        assertTrue(neutral.headForward().dot(solved.headForward()) < 0.75D);
    }

    @Test
    void rigAwareNeckSwingReachesBothEdgesOfTheTwoHundredSeventyDegreeField() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.Frame frame = rig.sample(AncientDragonCollisionRig.CRUISE, 0, true);
        AncientDragonCollisionRig.SolvedFrame neutral = rig.solve(
                frame, DragonPoseState.IDENTITY, "ancient_dragon:fly_cruise");
        for (double yawDegrees : new double[] {-135.0D, 135.0D}) {
            DragonPoseState gaze = new DragonPoseState(
                    DragonQuaternion.IDENTITY,
                    Curve.ZERO,
                    Curve.ZERO,
                    Math.toRadians(yawDegrees),
                    0.0D,
                    AttentionMode.THREAT,
                    7,
                    Maneuver.NONE);
            AncientDragonCollisionRig.SolvedFrame solved = rig.solve(
                    frame, gaze, "ancient_dragon:fly_cruise");
            Vec3 expected = DragonQuaternion.axisAngle(
                    new Vec3(0.0D, 1.0D, 0.0D), Math.toRadians(yawDegrees))
                    .rotate(neutral.headForward())
                    .normalize();
            double errorDegrees = Math.toDegrees(Math.acos(Math.clamp(
                    expected.dot(solved.headForward()), -1.0D, 1.0D)));
            assertTrue(errorDegrees <= 2.0D, "yaw=" + yawDegrees + " error=" + errorDegrees);
        }
    }

    @Test
    void rigAwareNeckSwingReachesTheDownwardAndUpwardPitchLimits() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.Frame frame = rig.sample(AncientDragonCollisionRig.CRUISE, 0, true);
        Vec3 neutralForward = rig.solve(
                        frame, DragonPoseState.IDENTITY, "ancient_dragon:fly_cruise")
                .headForward();
        Vec3 modelUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = modelUp.cross(neutralForward).normalize();
        for (double pitchDegrees : new double[] {-110.0D, 80.0D}) {
            DragonPoseState gaze = new DragonPoseState(
                    DragonQuaternion.IDENTITY,
                    Curve.ZERO,
                    Curve.ZERO,
                    0.0D,
                    Math.toRadians(pitchDegrees),
                    AttentionMode.THREAT,
                    7,
                    Maneuver.NONE);
            Vec3 actual = rig.solve(frame, gaze, "ancient_dragon:fly_cruise").headForward();
            Vec3 expected = DragonQuaternion.axisAngle(right, Math.toRadians(-pitchDegrees))
                    .rotate(neutralForward)
                    .normalize();
            double errorDegrees = Math.toDegrees(Math.acos(Math.clamp(
                    expected.dot(actual), -1.0D, 1.0D)));
            assertTrue(errorDegrees <= 2.0D,
                    "pitch=" + pitchDegrees + " error=" + errorDegrees);
        }
    }

    @Test
    void tailCounterCurveChangesTheActualFkSilhouetteInsteadOfOnlyTwistingScalars() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.Frame frame = rig.sample(AncientDragonCollisionRig.CRUISE, 0, true);
        DragonPoseState straight = new DragonPoseState(
                DragonQuaternion.IDENTITY,
                new Curve(0.0D, Math.toRadians(64.0D), 0.0D),
                Curve.ZERO,
                0.0D,
                0.0D,
                AttentionMode.SCAN,
                -1,
                Maneuver.NONE);
        DragonPoseState curved = new DragonPoseState(
                DragonQuaternion.IDENTITY,
                straight.torso(),
                new Curve(0.0D, Math.toRadians(-140.0D), 0.0D),
                0.0D,
                0.0D,
                AttentionMode.SCAN,
                -1,
                Maneuver.NONE);
        Vec3 straightTip = rig.solve(frame, straight, "ancient_dragon:fly_cruise")
                .position(AncientDragonPartKind.TAIL_TIP);
        Vec3 curvedTip = rig.solve(frame, curved, "ancient_dragon:fly_cruise")
                .position(AncientDragonPartKind.TAIL_TIP);
        assertTrue(straightTip.distanceTo(curvedTip) > 20.0D,
                "tail silhouette displacement=" + straightTip.distanceTo(curvedTip));
    }

    @Test
    void saturatedTorsoCurveVisiblyBendsTheActualFkBodyChain() {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.Frame frame = rig.sample(AncientDragonCollisionRig.CRUISE, 0, true);
        AncientDragonCollisionRig.SolvedFrame neutral = rig.solve(
                frame, DragonPoseState.IDENTITY, "ancient_dragon:fly_cruise");
        DragonPoseState bentPose = new DragonPoseState(
                DragonQuaternion.IDENTITY,
                new Curve(0.0D, Math.toRadians(64.0D), 0.0D),
                Curve.ZERO,
                0.0D,
                0.0D,
                AttentionMode.SCAN,
                -1,
                Maneuver.NONE);
        AncientDragonCollisionRig.SolvedFrame bent = rig.solve(
                frame, bentPose, "ancient_dragon:fly_cruise");
        double headDisplacement = neutral.position(AncientDragonPartKind.HEAD)
                .distanceTo(bent.position(AncientDragonPartKind.HEAD));
        double chestDisplacement = neutral.position(AncientDragonPartKind.CHEST)
                .distanceTo(bent.position(AncientDragonPartKind.CHEST));
        double headDirectionChange = Math.toDegrees(Math.acos(Math.clamp(
                neutral.headForward().dot(bent.headForward()), -1.0D, 1.0D)));
        assertTrue(headDisplacement > 2.0D, "head displacement=" + headDisplacement);
        assertTrue(chestDisplacement > 0.25D, "chest displacement=" + chestDisplacement);
        assertTrue(headDirectionChange > 40.0D, "head direction change=" + headDirectionChange);
    }

    @Test
    void projectedDynamicAabbConservativelyContainsAllRotatedCorners() {
        AncientDragonPartKind kind = AncientDragonPartKind.PRIMARY_WING_L_TIP;
        DragonQuaternion rotation = DragonQuaternion.axisAngle(
                new Vec3(1.0D, 1.0D, 0.4D), Math.toRadians(73.0D));
        AncientDragonPartKind.ProjectedDimensions projected = kind.project(rotation);
        double halfWidth = kind.width() * 0.5D;
        double halfHeight = kind.height() * 0.5D;
        double halfDepth = kind.depth() * 0.5D;
        for (int xSign : new int[] {-1, 1}) {
            for (int ySign : new int[] {-1, 1}) {
                for (int zSign : new int[] {-1, 1}) {
                    Vec3 corner = rotation.rotate(new Vec3(
                            xSign * halfWidth, ySign * halfHeight, zSign * halfDepth));
                    assertTrue(Math.abs(corner.x) <= projected.xSize() * 0.5D + 1.0e-5D);
                    assertTrue(Math.abs(corner.y) <= projected.ySize() * 0.5D + 1.0e-5D);
                    assertTrue(Math.abs(corner.z) <= projected.zSize() * 0.5D + 1.0e-5D);
                }
            }
        }
    }

    @Test
    void neutralWingTipKeepsItsThinDepthInsteadOfBecomingAHorizontalSquare() {
        AncientDragonPartKind kind = AncientDragonPartKind.PRIMARY_WING_L_TIP;
        AncientDragonPartKind.ProjectedDimensions projected = kind.project(DragonQuaternion.IDENTITY);

        assertEquals(kind.width(), projected.xSize(), 0.0001F);
        assertEquals(kind.height(), projected.ySize(), 0.0001F);
        assertEquals(kind.depth(), projected.zSize(), 0.0001F);
        assertTrue(projected.zSize() < projected.xSize() * 0.2F);
    }
}
