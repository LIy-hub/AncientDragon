package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.entity.DragonFlightController.FlightEnvelope;
import com.liy.ancientdragon.entity.DragonFlightController.FlightIntent;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class DragonAttentionControllerTest {
    @Test
    void usesPriorityAndTwentyFivePercentHysteresisAfterMinimumHold() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.Candidate threat = candidate(1, AttentionMode.THREAT, 300.0D, true);
        DragonAttentionController.Candidate observer = candidate(2, AttentionMode.OBSERVER, 200.0D, true);
        DragonAttentionController.AttentionStep step = tick(controller, 0L, List.of(threat, observer));
        assertEquals(1, step.targetEntityId());
        for (int tick = 1; tick <= 35; tick++) {
            step = tick(controller, tick, List.of(
                    candidate(1, AttentionMode.THREAT, 300.0D, true),
                    candidate(3, AttentionMode.RECENT_ATTACKER, 374.0D, true)));
        }
        assertEquals(1, step.targetEntityId());
        step = tick(controller, 36L, List.of(
                candidate(1, AttentionMode.THREAT, 300.0D, true),
                candidate(3, AttentionMode.RECENT_ATTACKER, 376.0D, true)));
        assertEquals(3, step.targetEntityId());
    }

    @Test
    void retainsLastVisiblePointForThirtyTicksThenSearches() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.AttentionStep step = tick(
                controller, 0L, List.of(candidate(9, AttentionMode.OBSERVER, 200.0D, true)));
        for (int tick = 1; tick <= 30; tick++) {
            step = tick(controller, tick, List.of(candidate(9, AttentionMode.OBSERVER, 200.0D, false)));
            assertEquals(9, step.targetEntityId());
        }
        step = tick(controller, 31L, List.of());
        assertEquals(-1, step.targetEntityId());
        assertEquals(AttentionMode.SCAN, step.mode());

        for (int tick = 32; tick < 40; tick++) {
            step = tick(controller, tick, List.of(candidate(9, AttentionMode.OBSERVER, 200.0D, false)));
            assertEquals(-1, step.targetEntityId());
        }
        step = tick(controller, 40L, List.of(candidate(9, AttentionMode.OBSERVER, 200.0D, true)));
        assertEquals(9, step.targetEntityId());
    }

    @Test
    void clearOverrideStaysClearedWhileCandidatesRemainAvailable() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.Candidate observer =
                candidate(9, AttentionMode.OBSERVER, 200.0D, true);
        for (int tick = 0; tick < 4; tick++) {
            DragonAttentionController.AttentionStep step = controller.tick(
                    tick,
                    123L,
                    Vec3.ZERO,
                    DragonQuaternion.IDENTITY,
                    List.of(observer),
                    DragonAttentionController.AttentionOverride.CLEAR);
            assertEquals(-1, step.targetEntityId());
            assertEquals(AttentionMode.CLEARED, step.mode());
        }
    }

    @Test
    void hiddenRecentAttackerCannotBeAcquiredThroughWalls() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.AttentionStep step = tick(
                controller,
                0L,
                List.of(candidate(7, AttentionMode.RECENT_ATTACKER, 700.0D, false)));
        assertEquals(-1, step.targetEntityId());
        assertEquals(AttentionMode.SCAN, step.mode());
    }

    @Test
    void switchHysteresisCannotFreezeAnOccludedTargetPastLastVisibleMemory() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.AttentionStep step = tick(
                controller,
                0L,
                List.of(candidate(1, AttentionMode.OBSERVER, 200.0D, true)));
        assertEquals(1, step.targetEntityId());
        for (int tick = 1; tick <= 30; tick++) {
            step = tick(controller, tick, List.of(
                    candidate(1, AttentionMode.OBSERVER, 200.0D, false),
                    candidate(2, AttentionMode.OBSERVER, 210.0D, true)));
            assertEquals(1, step.targetEntityId());
        }
        step = tick(controller, 31L, List.of(
                candidate(1, AttentionMode.OBSERVER, 200.0D, false),
                candidate(2, AttentionMode.OBSERVER, 210.0D, true)));
        assertEquals(-1, step.targetEntityId());
        assertEquals(AttentionMode.SCAN, step.mode());
        step = tick(controller, 32L, List.of(
                candidate(1, AttentionMode.OBSERVER, 200.0D, false),
                candidate(2, AttentionMode.OBSERVER, 210.0D, true)));
        assertEquals(2, step.targetEntityId());
    }

    @Test
    void clampsRearAttentionToEllipticalTwoHundredSeventyDegreeField() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.AttentionStep step = null;
        DragonAttentionController.Candidate rear = new DragonAttentionController.Candidate(
                5, new Vec3(-0.1D, 0.0D, -10.0D), AttentionMode.ATTACK_FOCUS, 500.0D, true);
        for (int tick = 0; tick < 50; tick++) {
            step = tick(controller, tick, List.of(rear));
        }
        assertTrue(Math.abs(Math.toDegrees(step.yawRadians())) <= 135.0001D);
        assertTrue(Math.abs(Math.toDegrees(step.yawRadians())) > 120.0D);
        assertTrue(Math.abs(step.bodyAssistYawRadians()) > 0.1D);
    }

    @Test
    void sustainedRearAttentionRequestsOnlyABoundedRouteCorrection() {
        DragonAttentionController attention = new DragonAttentionController();
        DragonFlightController flight = new DragonFlightController();
        DragonAttentionController.Candidate rear = new DragonAttentionController.Candidate(
                5, new Vec3(0.0D, 0.0D, -20.0D), AttentionMode.ATTACK_FOCUS, 1000.0D, true);
        double maximumYaw = 0.0D;
        DragonAttentionController.AttentionStep attentionStep = null;
        for (int tick = 0; tick < 240; tick++) {
            attentionStep = attention.tick(
                    tick,
                    123L,
                    Vec3.ZERO,
                    flight.orientation(),
                    List.of(rear),
                    DragonAttentionController.AttentionOverride.NONE);
            maximumYaw = Math.max(maximumYaw, Math.abs(Math.toDegrees(attentionStep.yawRadians())));
            flight.tick(
                    Vec3.ZERO,
                    FlightIntent.route(
                            new Vec3(0.0D, 0.0D, 100.0D),
                            0.0D,
                            attentionStep.bodyAssistYawRadians(),
                            FlightEnvelope.CRUISE));
        }
        assertTrue(maximumYaw > 120.0D, "maximumYaw=" + maximumYaw);
        assertTrue(Math.abs(Math.toDegrees(attentionStep.yawRadians())) > 120.0D,
                "finalYaw=" + Math.toDegrees(attentionStep.yawRadians())
                        + " headingOffset=" + Math.toDegrees(flight.attentionHeadingOffsetRadians())
                        + " forward=" + flight.orientation().forward());
        assertEquals(12.0D, Math.abs(Math.toDegrees(flight.attentionHeadingOffsetRadians())), 1.0e-6D);
    }

    @Test
    void lateralPlayerAttentionSettlesWithoutWindingUpTheWholeBody() {
        DragonAttentionController attention = new DragonAttentionController();
        DragonFlightController flight = new DragonFlightController();
        DragonAttentionController.Candidate player = new DragonAttentionController.Candidate(
                5, new Vec3(100.0D, 0.0D, 0.0D), AttentionMode.THREAT, 400.0D, true);
        double minimumSettledHeading = Double.POSITIVE_INFINITY;
        double maximumSettledHeading = Double.NEGATIVE_INFINITY;
        DragonAttentionController.AttentionStep attentionStep = null;

        for (int tick = 0; tick < 500; tick++) {
            attentionStep = attention.tick(
                    tick,
                    123L,
                    Vec3.ZERO,
                    flight.orientation(),
                    List.of(player),
                    DragonAttentionController.AttentionOverride.NONE);
            flight.tick(
                    Vec3.ZERO,
                    FlightIntent.route(
                            new Vec3(0.0D, 0.0D, 100.0D),
                            0.0D,
                            attentionStep.bodyAssistYawRadians(),
                            FlightEnvelope.CRUISE));
            if (tick >= 400) {
                Vec3 forward = flight.orientation().forward();
                double heading = Math.toDegrees(Math.atan2(forward.x, forward.z));
                minimumSettledHeading = Math.min(minimumSettledHeading, heading);
                maximumSettledHeading = Math.max(maximumSettledHeading, heading);
            }
        }

        double finalNeckYaw = Math.abs(Math.toDegrees(attentionStep.yawRadians()));
        assertTrue(finalNeckYaw >= 84.0D && finalNeckYaw <= 92.0D, "neckYaw=" + finalNeckYaw);
        assertTrue(maximumSettledHeading - minimumSettledHeading < 2.0D,
                "settledHeadingRange=" + (maximumSettledHeading - minimumSettledHeading));
        assertTrue(maximumSettledHeading >= 2.0D && maximumSettledHeading < 6.0D,
                "heading=" + maximumSettledHeading);
    }

    @Test
    void bodyTurnAssistReleasesOnlyAfterTargetStaysInsideTheInnerThreshold() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.Candidate side = new DragonAttentionController.Candidate(
                5, new Vec3(100.0D, 0.0D, 0.0D), AttentionMode.THREAT, 400.0D, true);
        DragonAttentionController.AttentionStep step = null;
        for (int tick = 0; tick < 30; tick++) {
            step = tick(controller, tick, List.of(side));
        }
        assertTrue(Math.abs(step.bodyAssistYawRadians()) > 0.1D);

        DragonAttentionController.Candidate front = new DragonAttentionController.Candidate(
                5, new Vec3(0.0D, 0.0D, 100.0D), AttentionMode.THREAT, 400.0D, true);
        for (int tick = 30; tick < 45; tick++) {
            step = tick(controller, tick, List.of(front));
        }
        assertTrue(Math.abs(step.bodyAssistYawRadians()) > 0.01D);
        for (int tick = 45; tick < 75; tick++) {
            step = tick(controller, tick, List.of(front));
        }
        assertEquals(0.0D, step.bodyAssistYawRadians(), 1.0e-12D);
    }

    @Test
    void emptyAttentionScansTheGroundWithAConsistentlyLoweredHead() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.AttentionStep step = null;
        for (int tick = 0; tick < 240; tick++) {
            step = tick(controller, tick, List.of());
            assertEquals(AttentionMode.SCAN, step.mode());
            assertEquals(-1, step.targetEntityId());
            assertTrue(step.lookPosition().y < -20.0D, "lookPosition=" + step.lookPosition());
            if (tick >= 40) {
                double pitchDegrees = Math.toDegrees(step.pitchRadians());
                assertTrue(pitchDegrees >= -80.01D && pitchDegrees <= -55.99D,
                        "pitch=" + pitchDegrees);
            }
        }
    }

    @Test
    void poseStateKeepsTheExpandedDownwardAndUpwardPitchLimits() {
        DragonAttentionController controller = new DragonAttentionController();
        DragonAttentionController.Candidate below = new DragonAttentionController.Candidate(
                8, new Vec3(0.0D, -100.0D, 0.01D), AttentionMode.ATTACK_FOCUS, 1000.0D, true);
        DragonAttentionController.AttentionStep step = null;
        for (int tick = 0; tick < 30; tick++) {
            step = tick(controller, tick, List.of(below));
        }
        assertTrue(Math.toDegrees(step.pitchRadians()) <= -89.0D);

        DragonPoseState downward =
                new DragonPoseState(
                        DragonQuaternion.IDENTITY,
                        Curve.ZERO,
                        Curve.ZERO,
                        0.0D,
                        Math.toRadians(-180.0D),
                        AttentionMode.THREAT,
                        8,
                        Maneuver.NONE);
        DragonPoseState upward =
                new DragonPoseState(
                        DragonQuaternion.IDENTITY,
                        Curve.ZERO,
                        Curve.ZERO,
                        0.0D,
                        Math.toRadians(180.0D),
                        AttentionMode.THREAT,
                        8,
                        Maneuver.NONE);
        assertEquals(-110.0D, Math.toDegrees(downward.attentionPitchRadians()), 1.0e-9D);
        assertEquals(80.0D, Math.toDegrees(upward.attentionPitchRadians()), 1.0e-9D);
    }

    private static DragonAttentionController.AttentionStep tick(
            DragonAttentionController controller,
            long tick,
            List<DragonAttentionController.Candidate> candidates) {
        return controller.tick(
                tick,
                123L,
                Vec3.ZERO,
                DragonQuaternion.IDENTITY,
                candidates,
                DragonAttentionController.AttentionOverride.NONE);
    }

    private static DragonAttentionController.Candidate candidate(
            int id, AttentionMode mode, double score, boolean visible) {
        return new DragonAttentionController.Candidate(id, new Vec3(10.0D, 2.0D, 10.0D), mode, score, visible);
    }
}
