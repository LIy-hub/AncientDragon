package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.boss.AncientDragonBossState;
import com.liy.ancientdragon.boss.AncientDragonEncounterRules.Phase;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SacredMountainFlightPathTest {
    private static final Vec3 APPROVED_DORMANT_ORIGIN = new Vec3(150.0D, 144.0D, 0.0D);

    @Test
    void summitAttackHoldsMatchTheThreeFrozenSummits() {
        assertEquals(3, SacredMountainFlightPath.peakHoldCount());
        assertEquals(new Vec3(-227.0D, 114.0D, -169.0D), SacredMountainFlightPath.peakHold(0).offset());
        assertEquals(new Vec3(-274.0D, 60.0D, 111.0D), SacredMountainFlightPath.peakHold(1).offset());
        assertEquals(new Vec3(-51.0D, 75.0D, 175.0D), SacredMountainFlightPath.peakHold(2).offset());

        Vec3 nearSolar = APPROVED_DORMANT_ORIGIN.add(-40.0D, 120.0D, 180.0D);
        assertEquals(
                APPROVED_DORMANT_ORIGIN.add(-51.0D, 75.0D, 175.0D),
                SacredMountainFlightPath.nearestPeakHoldWorldPosition(APPROVED_DORMANT_ORIGIN, nearSolar));
    }

    @Test
    void returnStatesRoundTripThroughPersistentNames() {
        assertEquals(AncientDragonBossState.RETURNING,
                AncientDragonBossState.fromSerializedName("returning"));
        assertEquals(AncientDragonBossState.APPROACHING,
                AncientDragonBossState.fromSerializedName("approaching"));
        assertEquals(AncientDragonBossState.LANDING,
                AncientDragonBossState.fromSerializedName("landing"));
    }

    @Test
    void preservesApprovedWorldCoordinates() {
        List<Vec3> expected = List.of(
                new Vec3(150.0D, 204.0D, 0.0D),
                new Vec3(190.0D, 206.0D, -80.0D),
                new Vec3(110.0D, 202.0D, -140.0D),
                new Vec3(10.0D, 206.0D, -165.0D),
                new Vec3(-80.0D, 204.0D, -120.0D),
                new Vec3(-120.0D, 202.0D, -40.0D),
                new Vec3(-110.0D, 200.0D, 60.0D),
                new Vec3(-60.0D, 198.0D, 130.0D),
                new Vec3(20.0D, 200.0D, 165.0D),
                new Vec3(100.0D, 202.0D, 160.0D),
                new Vec3(170.0D, 204.0D, 120.0D),
                new Vec3(220.0D, 206.0D, 50.0D),
                new Vec3(205.0D, 204.0D, -20.0D));

        assertEquals(expected.size(), SacredMountainFlightPath.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index),
                    SacredMountainFlightPath.worldPosition(APPROVED_DORMANT_ORIGIN, index));
        }
    }

    @Test
    void preservesApprovedLoopOrderAndMountainSegments() {
        List<String> ids = List.of("A", "S1", "S2", "S3", "S4", "C1", "C2", "C3", "C4",
                "T1", "T2", "T3", "T4");
        List<SacredMountainFlightPath.Segment> segments = List.of(
                SacredMountainFlightPath.Segment.ASSEMBLY,
                SacredMountainFlightPath.Segment.STORM,
                SacredMountainFlightPath.Segment.STORM,
                SacredMountainFlightPath.Segment.STORM,
                SacredMountainFlightPath.Segment.STORM,
                SacredMountainFlightPath.Segment.CROWN,
                SacredMountainFlightPath.Segment.CROWN,
                SacredMountainFlightPath.Segment.CROWN,
                SacredMountainFlightPath.Segment.CROWN,
                SacredMountainFlightPath.Segment.SOLAR,
                SacredMountainFlightPath.Segment.SOLAR,
                SacredMountainFlightPath.Segment.SOLAR,
                SacredMountainFlightPath.Segment.SOLAR);

        for (int index = 0; index < ids.size(); index++) {
            assertEquals(ids.get(index), SacredMountainFlightPath.waypoint(index).id());
            assertEquals(segments.get(index), SacredMountainFlightPath.waypoint(index).segment());
        }
        assertEquals(SacredMountainFlightPath.ASSEMBLY_INDEX,
                SacredMountainFlightPath.nextIndex(SacredMountainFlightPath.size() - 1));
    }

    @Test
    void everyFlightLandmarkAndFacingRotatesWithTheMountain() {
        for (int turns = 0; turns < 4; turns++) {
            for (int index = 0; index < SacredMountainFlightPath.size(); index++) {
                Vec3 authored = SacredMountainFlightPath.waypoint(index).offset();
                assertEquals(
                        APPROVED_DORMANT_ORIGIN.add(SacredMountainFlightPath.rotateOffset(authored, turns)),
                        SacredMountainFlightPath.worldPosition(APPROVED_DORMANT_ORIGIN, index, turns),
                        "patrol index=" + index + " turns=" + turns);
            }
            for (int index = 0; index < SacredMountainFlightPath.approachSize(); index++) {
                Vec3 authored = SacredMountainFlightPath.approachWaypoint(index).offset();
                assertEquals(
                        APPROVED_DORMANT_ORIGIN.add(SacredMountainFlightPath.rotateOffset(authored, turns)),
                        SacredMountainFlightPath.approachWorldPosition(
                                APPROVED_DORMANT_ORIGIN, index, turns),
                        "approach index=" + index + " turns=" + turns);
            }
            for (int index = 0; index < SacredMountainFlightPath.combatApproachSize(); index++) {
                Vec3 authored = SacredMountainFlightPath.combatApproachWaypoint(index, 0).offset();
                assertEquals(
                        APPROVED_DORMANT_ORIGIN.add(SacredMountainFlightPath.rotateOffset(authored, turns)),
                        SacredMountainFlightPath.combatApproachWorldPosition(
                                APPROVED_DORMANT_ORIGIN, index, turns),
                        "combat approach index=" + index + " turns=" + turns);
            }
            for (Phase phase : Phase.values()) {
                SacredMountainFlightPath.Perch authored = SacredMountainFlightPath.perchFor(phase);
                SacredMountainFlightPath.Perch rotated = SacredMountainFlightPath.perchFor(phase, turns);
                assertEquals(SacredMountainFlightPath.rotateOffset(authored.rootOffset(), turns),
                        rotated.rootOffset(), "perch root phase=" + phase + " turns=" + turns);
                assertEquals(SacredMountainFlightPath.rotateOffset(authored.skyApproachOffset(), turns),
                        rotated.skyApproachOffset(), "perch sky phase=" + phase + " turns=" + turns);
                assertEquals(SacredMountainFlightPath.rotateYaw(authored.yaw(), turns), rotated.yaw(),
                        "perch yaw phase=" + phase + " turns=" + turns);
            }
            assertEquals((90.0F + turns * 90.0F) % 360.0F,
                    SacredMountainFlightPath.rotateYaw(90.0F, turns));
            assertEquals((90.0F + turns * 90.0F) % 360.0F,
                    SacredMountainFlightPath.roostYaw(turns));
        }
    }

    @Test
    void flightControllerCanCompleteTheAuthoredLoop() {
        DragonFlightController controller = new DragonFlightController();
        Vec3 position = SacredMountainFlightPath.worldPosition(
                APPROVED_DORMANT_ORIGIN, SacredMountainFlightPath.ASSEMBLY_INDEX);
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);

        int waypointIndex = SacredMountainFlightPath.nextIndex(SacredMountainFlightPath.ASSEMBLY_INDEX);
        int arrivals = 0;
        for (int tick = 0; tick < 20_000 && arrivals < SacredMountainFlightPath.size(); tick++) {
            SacredMountainFlightPath.Waypoint waypoint = SacredMountainFlightPath.waypoint(waypointIndex);
            Vec3 destination = SacredMountainFlightPath.worldPosition(APPROVED_DORMANT_ORIGIN, waypointIndex);
            DragonFlightController.FlightStep step = controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            destination,
                            waypoint.maximumSpeed(),
                            0.0D,
                            DragonFlightController.FlightEnvelope.CRUISE));
            position = position.add(step.movement());
            if (position.distanceTo(destination) <= SacredMountainFlightPath.ARRIVAL_RADIUS) {
                waypointIndex = SacredMountainFlightPath.nextIndex(waypointIndex);
                arrivals++;
            }
        }

        assertEquals(SacredMountainFlightPath.size(), arrivals);
        assertEquals(SacredMountainFlightPath.nextIndex(SacredMountainFlightPath.ASSEMBLY_INDEX), waypointIndex);
        assertTrue(position.distanceTo(SacredMountainFlightPath.worldPosition(
                APPROVED_DORMANT_ORIGIN, SacredMountainFlightPath.ASSEMBLY_INDEX))
                <= SacredMountainFlightPath.ARRIVAL_RADIUS);
    }

    @Test
    void innerNorthApproachEndsCloseEnoughForTheRealLandingClip() {
        List<Vec3> expected = List.of(
                new Vec3(220.0D, 230.0D, -140.0D),
                new Vec3(180.0D, 220.0D, -120.0D),
                new Vec3(155.0D, 215.0D, -90.0D),
                new Vec3(150.0D, 200.0D, -60.0D),
                new Vec3(150.0D, 180.0D, -30.0D),
                new Vec3(160.0D, 165.0D, -10.0D),
                new Vec3(168.0D, 152.0D, 0.0D));
        assertEquals(expected.size(), SacredMountainFlightPath.approachSize());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index),
                    SacredMountainFlightPath.approachWorldPosition(APPROVED_DORMANT_ORIGIN, index));
        }

        DragonFlightController controller = new DragonFlightController();
        Vec3 position = SacredMountainFlightPath.worldPosition(
                APPROVED_DORMANT_ORIGIN, SacredMountainFlightPath.ASSEMBLY_INDEX);
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);
        int approachIndex = 0;
        for (int tick = 0; tick < 20_000 && approachIndex < SacredMountainFlightPath.approachSize(); tick++) {
            SacredMountainFlightPath.ApproachWaypoint waypoint =
                    SacredMountainFlightPath.approachWaypoint(approachIndex);
            Vec3 destination = SacredMountainFlightPath.approachWorldPosition(
                    APPROVED_DORMANT_ORIGIN, approachIndex);
            DragonFlightController.FlightStep step = controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            destination,
                            waypoint.maximumSpeed(),
                            0.0D,
                            DragonFlightController.FlightEnvelope.CRUISE));
            position = position.add(step.movement());
            double arrivalRadius = approachIndex == SacredMountainFlightPath.approachSize() - 1
                    ? 4.0D
                    : SacredMountainFlightPath.APPROACH_ARRIVAL_RADIUS;
            if (position.distanceTo(destination) <= arrivalRadius) {
                approachIndex++;
            }
        }
        assertEquals(SacredMountainFlightPath.approachSize(), approachIndex);

        for (int tick = 0; tick < AncientDragonEntity.LAND_DURATION_TICKS; tick++) {
            DragonFlightController.FlightStep step = controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            APPROVED_DORMANT_ORIGIN,
                            0.28D,
                            0.0D,
                            DragonFlightController.FlightEnvelope.ATTACK));
            position = position.add(step.movement());
        }
        assertTrue(position.distanceTo(APPROVED_DORMANT_ORIGIN) <= 2.5D,
                "touchdown error=" + position.distanceTo(APPROVED_DORMANT_ORIGIN));
        Vec3 horizontalHeading = new Vec3(
                controller.orientation().forward().x,
                0.0D,
                controller.orientation().forward().z).normalize();
        assertTrue(horizontalHeading.x < -0.90D,
                "touchdown heading=" + controller.orientation().forward());
    }

    @Test
    void markedFlightBoxContainsEveryAuthoredAirDestinationAtLowCruiseHeight() {
        for (int turns = 0; turns < 4; turns++) {
            assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                    SacredMountainFlightPath.takeoffWorldPosition(APPROVED_DORMANT_ORIGIN, turns),
                    APPROVED_DORMANT_ORIGIN,
                    turns));
            for (int index = 0; index < SacredMountainFlightPath.size(); index++) {
                Vec3 waypoint = SacredMountainFlightPath.worldPosition(APPROVED_DORMANT_ORIGIN, index, turns);
                assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                        waypoint, APPROVED_DORMANT_ORIGIN, turns),
                        "patrol index=" + index + " turns=" + turns);
                assertTrue(waypoint.y >= 198.0D && waypoint.y <= 206.0D,
                        "cruise Y=" + waypoint.y + " index=" + index);
            }
            for (int index = 0; index < SacredMountainFlightPath.approachSize(); index++) {
                assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                        SacredMountainFlightPath.approachWorldPosition(
                                APPROVED_DORMANT_ORIGIN, index, turns),
                        APPROVED_DORMANT_ORIGIN,
                        turns));
            }
            for (int index = 0; index < SacredMountainFlightPath.combatApproachSize(); index++) {
                assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                        SacredMountainFlightPath.combatApproachWorldPosition(
                                APPROVED_DORMANT_ORIGIN, index, turns),
                        APPROVED_DORMANT_ORIGIN,
                        turns));
            }
            for (Phase phase : Phase.values()) {
                SacredMountainFlightPath.Perch perch = SacredMountainFlightPath.perchFor(phase, turns);
                assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                        APPROVED_DORMANT_ORIGIN.add(perch.rootOffset()),
                        APPROVED_DORMANT_ORIGIN,
                        turns));
                assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                        APPROVED_DORMANT_ORIGIN.add(perch.skyApproachOffset()),
                        APPROVED_DORMANT_ORIGIN,
                        turns));
            }
        }
    }

    @Test
    void whitePlatformHasItsOwnRotatingAnchorAndDeathArea() {
        Vec3 expected = new Vec3(49.0D, 169.0D, -1.0D);
        assertEquals(expected, SacredMountainFlightPath.combatPlatformWorldPosition(
                APPROVED_DORMANT_ORIGIN, 0));
        for (int turns = 0; turns < 4; turns++) {
            Vec3 platform = SacredMountainFlightPath.combatPlatformWorldPosition(
                    APPROVED_DORMANT_ORIGIN, turns);
            assertTrue(SacredMountainFlightPath.isInsideCombatPlatform(
                    platform, APPROVED_DORMANT_ORIGIN, turns));
            assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                    platform, APPROVED_DORMANT_ORIGIN, turns));
            assertTrue(SacredMountainFlightPath.isInsideCombatPlatform(
                    SacredMountainFlightPath.deathApproachWorldPosition(
                            APPROVED_DORMANT_ORIGIN, turns),
                    APPROVED_DORMANT_ORIGIN,
                    turns));
        }
        assertTrue(!SacredMountainFlightPath.isInsideCombatPlatform(
                APPROVED_DORMANT_ORIGIN, APPROVED_DORMANT_ORIGIN, 0));
    }

    @Test
    void arbitraryAttackDestinationIsClampedToTheMarkedFlightBox() {
        Vec3 outside = APPROVED_DORMANT_ORIGIN.add(500.0D, 80.0D, -500.0D);
        Vec3 clamped = SacredMountainFlightPath.clampToFlightBounds(
                outside, APPROVED_DORMANT_ORIGIN, 0);
        assertEquals(APPROVED_DORMANT_ORIGIN.add(100.0D, 80.0D, -179.0D), clamped);
        assertTrue(SacredMountainFlightPath.isInsideFlightBounds(
                clamped, APPROVED_DORMANT_ORIGIN, 0));
    }
}
