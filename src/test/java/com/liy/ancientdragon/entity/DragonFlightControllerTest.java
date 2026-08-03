package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.entity.DragonFlightController.FlightEnvelope;
import com.liy.ancientdragon.entity.DragonFlightController.FlightIntent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class DragonFlightControllerTest {
    @Test
    void acceleratesOnlyAlongTheCurrentThreeDimensionalForwardAxis() {
        DragonFlightController controller = new DragonFlightController();
        DragonFlightController.FlightStep step = null;
        for (int tick = 0; tick < 100; tick++) {
            step = controller.tick(Vec3.ZERO, route(
                    new Vec3(20.0D, 20.0D, 40.0D), 0.42D, FlightEnvelope.CRUISE));
            if (step.movement().lengthSqr() > 1.0e-10D) {
                assertTrue(step.movement().normalize().dot(step.orientation().forward()) > 0.999999D);
            }
        }
        assertTrue(step.orientation().forward().y > 0.15D, "forward=" + step.orientation().forward());
        assertEquals(0.42D, controller.speed(), 1.0e-9D);
    }

    @Test
    void verticalTakeoffPreservesExistingHeadingAtThePitchLimit() {
        DragonFlightController controller = new DragonFlightController();
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);
        for (int tick = 0; tick < 100; tick++) {
            controller.tick(Vec3.ZERO, route(
                    new Vec3(0.0D, 100.0D, 0.0D), 0.32D, FlightEnvelope.TAKEOFF));
        }
        Vec3 forward = controller.orientation().forward();
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z).normalize();
        assertTrue(horizontal.dot(new Vec3(-1.0D, 0.0D, 0.0D)) > 0.98D, "forward=" + forward);
        assertTrue(forward.y > 0.96D);
    }

    @Test
    void saturatedCruiseTurnStaysInsideTheNormalEnvelopeAndKeepsAVisibleCurve() {
        DragonFlightController controller = new DragonFlightController();
        DragonQuaternion previous = controller.orientation();
        double maximumTorso = 0.0D;
        double maximumTail = 0.0D;
        double maximumBank = 0.0D;
        double maximumStep = 0.0D;
        boolean opposing = false;
        for (int tick = 0; tick < 80; tick++) {
            DragonFlightController.FlightStep step = controller.tick(
                    Vec3.ZERO, route(new Vec3(80.0D, 0.0D, 0.0D), 0.42D, FlightEnvelope.CRUISE));
            maximumTorso = Math.max(maximumTorso, Math.abs(Math.toDegrees(step.torsoCurve().yawRadians())));
            maximumTail = Math.max(maximumTail, Math.abs(Math.toDegrees(step.tailCurve().yawRadians())));
            maximumBank = Math.max(maximumBank, Math.abs(bankDegrees(step.orientation())));
            maximumStep = Math.max(maximumStep, Math.toDegrees(previous.angularDistance(step.orientation())));
            opposing |= Math.signum(step.torsoCurve().yawRadians()) != Math.signum(step.tailCurve().yawRadians());
            previous = step.orientation();
        }
        assertTrue(maximumTorso >= 50.0D && maximumTorso <= 64.01D, "torso=" + maximumTorso);
        assertTrue(maximumTail >= 130.0D && maximumTail <= 140.01D, "tail=" + maximumTail);
        assertTrue(maximumBank >= 12.0D && maximumBank <= 35.01D, "bank=" + maximumBank);
        assertTrue(maximumStep <= 3.0001D, "step=" + maximumStep);
        assertTrue(opposing);
    }

    @Test
    void downwardAttackBendsTheAbdomenWithoutRelaxingTheRootEnvelope() {
        DragonFlightController controller = new DragonFlightController();
        DragonQuaternion previous = controller.orientation();
        double maximumTorsoPitch = 0.0D;
        double maximumRootStep = 0.0D;
        for (int tick = 0; tick < 100; tick++) {
            DragonFlightController.FlightStep step = controller.tick(
                    Vec3.ZERO,
                    route(new Vec3(0.0D, -100.0D, 20.0D), 0.58D, FlightEnvelope.DIVE));
            maximumTorsoPitch = Math.max(
                    maximumTorsoPitch, Math.toDegrees(step.torsoCurve().pitchRadians()));
            maximumRootStep = Math.max(
                    maximumRootStep, Math.toDegrees(previous.angularDistance(step.orientation())));
            previous = step.orientation();
        }
        assertTrue(maximumTorsoPitch >= 50.0D && maximumTorsoPitch <= 56.01D,
                "torsoPitch=" + maximumTorsoPitch);
        assertTrue(maximumRootStep <= 4.5001D, "rootStep=" + maximumRootStep);
        assertTrue(controller.orientation().forward().y < -0.90D);
    }

    @Test
    void attentionAssistIsAnAbsoluteBoundedOffsetInsteadOfAnAccumulator() {
        DragonFlightController controller = new DragonFlightController();
        double maximumBank = 0.0D;
        for (int tick = 0; tick < 500; tick++) {
            DragonFlightController.FlightStep step = controller.tick(
                    Vec3.ZERO,
                    FlightIntent.route(
                            new Vec3(0.0D, 0.0D, 100.0D),
                            0.0D,
                            Math.toRadians(135.0D),
                            FlightEnvelope.CRUISE));
            maximumBank = Math.max(maximumBank, Math.abs(bankDegrees(step.orientation())));
        }
        double heading = headingDegrees(controller.orientation());
        assertEquals(12.0D, Math.toDegrees(controller.attentionHeadingOffsetRadians()), 1.0e-6D);
        assertTrue(heading >= 10.0D && heading <= 14.0D, "heading=" + heading);
        assertTrue(maximumBank <= 35.01D, "bank=" + maximumBank);
    }

    @Test
    void attackFacingBiasTurnsInTheRequestedDirectionWithoutUsingTheNeckComfortFormula() {
        DragonFlightController controller = new DragonFlightController();
        double maximumBank = 0.0D;
        FlightIntent intent = FlightIntent.facing(
                new Vec3(0.0D, 0.0D, 100.0D),
                0.34D,
                new Vec3(100.0D, 0.0D, 0.0D),
                Math.toRadians(8.0D),
                FlightEnvelope.ATTACK);
        for (int tick = 0; tick < 121; tick++) {
            DragonFlightController.FlightStep step = controller.tick(Vec3.ZERO, intent);
            maximumBank = Math.max(maximumBank, Math.abs(bankDegrees(step.orientation())));
        }
        double heading = headingDegrees(controller.orientation());
        assertEquals(0.0D, controller.attentionHeadingOffsetRadians(), 1.0e-12D);
        assertEquals(8.0D, Math.toDegrees(controller.facingHeadingOffsetRadians()), 1.0e-6D);
        assertTrue(heading >= 6.0D && heading <= 10.0D, "heading=" + heading);
        assertTrue(maximumBank <= 20.0D, "bank=" + maximumBank);
    }

    @Test
    void alternatingRearAttentionCannotFlipOrWhipTheNormalRoot() {
        DragonFlightController controller = new DragonFlightController();
        DragonQuaternion previous = controller.orientation();
        double minimumUprightDot = 1.0D;
        double maximumStep = 0.0D;
        for (int tick = 0; tick < 800; tick++) {
            double attentionYaw = Math.toRadians(((tick / 40) & 1) == 0 ? 135.0D : -135.0D);
            DragonFlightController.FlightStep step = controller.tick(
                    Vec3.ZERO,
                    FlightIntent.route(
                            new Vec3(0.0D, 0.0D, 100.0D),
                            0.42D,
                            attentionYaw,
                            FlightEnvelope.CRUISE));
            minimumUprightDot = Math.min(minimumUprightDot, step.orientation().up().y);
            maximumStep = Math.max(maximumStep, Math.toDegrees(previous.angularDistance(step.orientation())));
            assertEquals(Maneuver.NONE, step.maneuver());
            previous = step.orientation();
        }
        assertTrue(minimumUprightDot >= Math.cos(Math.toRadians(35.01D)), "upright=" + minimumUprightDot);
        assertTrue(maximumStep <= 3.0001D, "step=" + maximumStep);
    }

    @Test
    void barrelRollRunsSixtyTicksThenEnforcesCooldown() {
        DragonFlightController controller = movingController();
        DragonQuaternion before = controller.orientation();
        assertTrue(controller.startManeuver(Maneuver.ROLL_LEFT));
        DragonQuaternion middle = null;
        for (int tick = 0; tick < 60; tick++) {
            controller.tick(Vec3.ZERO, route(new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
            if (tick == 29) {
                middle = controller.orientation();
            }
        }
        assertEquals(Maneuver.NONE, controller.maneuver());
        assertEquals(DragonFlightController.MANEUVER_COOLDOWN_TICKS, controller.maneuverCooldown());
        assertFalse(controller.startManeuver(Maneuver.ROLL_RIGHT));
        assertTrue(before.forward().dot(controller.orientation().forward()) > 0.999999D);
        assertTrue(before.up().dot(middle.up()) < -0.999D);
        assertTrue(before.up().dot(controller.orientation().up()) > 0.999999D);

        for (int tick = 0; tick < DragonFlightController.MANEUVER_COOLDOWN_TICKS - 1; tick++) {
            controller.tick(Vec3.ZERO, route(new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
        }
        assertEquals(1, controller.maneuverCooldown());
        assertFalse(controller.startManeuver(Maneuver.ROLL_RIGHT));
        controller.tick(Vec3.ZERO, route(new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
        assertEquals(0, controller.maneuverCooldown());
        assertTrue(controller.startManeuver(Maneuver.ROLL_RIGHT));
    }

    @Test
    void loopRunsSeventyTwoTicksThroughAnInvertedFlightPath() {
        DragonFlightController controller = movingController();
        Vec3 initialForward = controller.orientation().forward();
        assertTrue(controller.startManeuver(Maneuver.LOOP));
        Vec3 midpointForward = Vec3.ZERO;
        for (int tick = 0; tick < 72; tick++) {
            controller.tick(Vec3.ZERO, route(new Vec3(0.0D, 0.0D, 100.0D), 0.58D, FlightEnvelope.DIVE));
            if (tick == 35) {
                midpointForward = controller.orientation().forward();
            }
        }
        assertTrue(initialForward.dot(midpointForward) < -0.999D);
        assertTrue(initialForward.dot(controller.orientation().forward()) > 0.999999D);
        assertEquals(Maneuver.NONE, controller.maneuver());
    }

    @Test
    void automaticEvasionIsAContainedBankAndNeverAnInversion() {
        DragonFlightController controller = movingController();
        DragonQuaternion start = controller.orientation();
        assertTrue(controller.startManeuver(Maneuver.EVADE_LEFT));
        double minimumUp = 1.0D;
        double maximumBank = 0.0D;
        for (int tick = 0; tick < Maneuver.EVADE_LEFT.durationTicks(); tick++) {
            controller.tick(Vec3.ZERO, route(new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
            minimumUp = Math.min(minimumUp, controller.orientation().up().y);
            maximumBank = Math.max(maximumBank, Math.abs(bankDegrees(controller.orientation())));
        }
        assertTrue(minimumUp > 0.68D, "minimumUp=" + minimumUp);
        assertTrue(maximumBank >= 42.0D && maximumBank <= 46.0D, "bank=" + maximumBank);
        assertTrue(start.angularDistance(controller.orientation()) < 1.0e-9D);
    }

    @Test
    void diveRecoveryPullsUpWithoutCompletingAFlip() {
        DragonFlightController controller = movingController();
        DragonQuaternion start = controller.orientation();
        assertTrue(controller.startManeuver(Maneuver.PULL_UP));
        double maximumClimb = -1.0D;
        double minimumUp = 1.0D;
        for (int tick = 0; tick < Maneuver.PULL_UP.durationTicks(); tick++) {
            controller.tick(Vec3.ZERO, route(new Vec3(0.0D, 20.0D, 100.0D), 0.58D, FlightEnvelope.DIVE));
            maximumClimb = Math.max(maximumClimb, controller.orientation().forward().y);
            minimumUp = Math.min(minimumUp, controller.orientation().up().y);
        }
        assertTrue(maximumClimb > 0.90D, "climb=" + maximumClimb);
        assertTrue(minimumUp > 0.30D, "minimumUp=" + minimumUp);
        assertTrue(start.angularDistance(controller.orientation()) < 1.0e-9D);
    }

    @Test
    void restoreKeepsQuaternionVelocityAndCurvesWithoutResumingManeuver() {
        DragonFlightController source = new DragonFlightController();
        for (int tick = 0; tick < 35; tick++) {
            source.tick(Vec3.ZERO, route(
                    new Vec3(-60.0D, -25.0D, 30.0D), 0.42D, FlightEnvelope.CRUISE));
        }
        DragonFlightController restored = new DragonFlightController();
        restored.restore(
                source.orientation(),
                source.velocity(),
                source.torsoCurve(),
                source.tailCurve(),
                123);
        assertTrue(source.orientation().angularDistance(restored.orientation()) < 1.0e-9D);
        assertEquals(source.speed(), restored.speed(), 1.0e-9D);
        assertEquals(source.torsoCurve(), restored.torsoCurve());
        assertEquals(source.tailCurve(), restored.tailCurve());
        assertEquals(Maneuver.NONE, restored.maneuver());
        assertEquals(123, restored.maneuverCooldown());
    }

    @Test
    void teleportChangesTheRouteWithoutSnappingOrDecouplingPoseAndVelocity() {
        DragonFlightController controller = new DragonFlightController();
        for (int tick = 0; tick < 45; tick++) {
            controller.tick(Vec3.ZERO, route(
                    new Vec3(80.0D, 12.0D, 10.0D), 0.42D, FlightEnvelope.CRUISE));
        }
        DragonQuaternion beforeTeleport = controller.orientation();
        Vec3 teleportedPosition = new Vec3(1200.0D, 240.0D, -900.0D);
        DragonFlightController.FlightStep afterTeleport = controller.tick(
                teleportedPosition, route(Vec3.ZERO, 0.42D, FlightEnvelope.CRUISE));
        assertTrue(Math.toDegrees(beforeTeleport.angularDistance(afterTeleport.orientation())) <= 3.0001D);
        assertTrue(afterTeleport.movement().normalize().dot(afterTeleport.orientation().forward()) > 0.999999D);
    }

    @Test
    void reachingAThenRetargetingFromWaypointNeverBypassesLinearAccelerationLimits() {
        DragonFlightController controller = new DragonFlightController();
        for (int tick = 0; tick < 20; tick++) {
            controller.tick(Vec3.ZERO, route(
                    new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
        }
        double previousCommandedSpeed = controller.speed();
        assertEquals(0.42D, previousCommandedSpeed, 1.0e-9D);

        for (int tick = 0; tick < 8; tick++) {
            DragonFlightController.FlightStep braking = controller.tick(
                    Vec3.ZERO, route(Vec3.ZERO, 0.42D, FlightEnvelope.CRUISE));
            double commandedSpeed = braking.movement().length();
            assertTrue(previousCommandedSpeed - commandedSpeed <= 0.0650001D,
                    "deceleration=" + (previousCommandedSpeed - commandedSpeed));
            assertEquals(controller.speed(), commandedSpeed, 1.0e-9D);
            previousCommandedSpeed = commandedSpeed;
        }
        assertEquals(0.0D, previousCommandedSpeed, 1.0e-9D);

        DragonFlightController.FlightStep retargeted = controller.tick(
                Vec3.ZERO,
                route(new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
        assertEquals(0.045D, retargeted.movement().length(), 1.0e-9D);
        assertEquals(controller.speed(), retargeted.movement().length(), 1.0e-9D);
        assertTrue(retargeted.movement().normalize().dot(retargeted.orientation().forward()) > 0.999999D);
    }

    private static DragonFlightController movingController() {
        DragonFlightController controller = new DragonFlightController();
        for (int tick = 0; tick < 12; tick++) {
            controller.tick(Vec3.ZERO, route(
                    new Vec3(0.0D, 0.0D, 100.0D), 0.42D, FlightEnvelope.CRUISE));
        }
        return controller;
    }

    private static FlightIntent route(Vec3 destination, double speed, FlightEnvelope envelope) {
        return FlightIntent.route(destination, speed, 0.0D, envelope);
    }

    private static double headingDegrees(DragonQuaternion orientation) {
        Vec3 forward = orientation.forward();
        return Math.toDegrees(Math.atan2(forward.x, forward.z));
    }

    private static double bankDegrees(DragonQuaternion orientation) {
        Vec3 forward = orientation.forward();
        Vec3 referenceUp = new Vec3(0.0D, 1.0D, 0.0D).subtract(forward.scale(forward.y));
        if (referenceUp.lengthSqr() < 1.0e-8D) {
            return 0.0D;
        }
        referenceUp = referenceUp.normalize();
        Vec3 actualUp = orientation.up().subtract(forward.scale(orientation.up().dot(forward))).normalize();
        return Math.toDegrees(Math.atan2(forward.dot(referenceUp.cross(actualUp)), referenceUp.dot(actualUp)));
    }
}
