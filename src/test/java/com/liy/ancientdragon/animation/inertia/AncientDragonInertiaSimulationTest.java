package com.liy.ancientdragon.animation.inertia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.inertia.AncientDragonInertiaSimulation.BoneRotation;
import com.liy.ancientdragon.animation.inertia.AncientDragonInertiaSimulation.KinematicFrame;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AncientDragonInertiaSimulationTest {
    @Test
    void startsAtRestAndProducesBoundedWholeRigResponse() {
        AncientDragonInertiaSimulation simulation = new AncientDragonInertiaSimulation();
        assertAllZero(simulation.sample(frame(0, 0.0, 0.0, 0.0, 0.0, "ancient_dragon:fly_cruise"), 1.0F));

        Map<String, BoneRotation> response = simulation.sample(
                frame(1, 0.0, 0.16, 0.42, 6.0, "ancient_dragon:fly_cruise"), 1.0F);

        assertEquals(81, response.size());
        assertTrue(response.get("root").magnitudeSquared() > 0.0);
        assertTrue(response.get("tail_08").magnitudeSquared() > 0.0);
        assertTrue(response.get("wing_main_vein_C_04.L").magnitudeSquared() > 0.0);
        assertTrue(response.get("toe.R").magnitudeSquared() > 0.0);

        for (var profile : AncientDragonInertiaProfile.bones()) {
            BoneRotation rotation = response.get(profile.name());
            assertTrue(Math.abs(rotation.pitchRadians()) <= profile.maximumRadians() + 1.0e-12);
            assertTrue(Math.abs(rotation.yawRadians()) <= profile.maximumRadians() + 1.0e-12);
            assertTrue(Math.abs(rotation.rollRadians()) <= profile.maximumRadians() + 1.0e-12);
        }
    }

    @Test
    void sustainedTurnProducesSegmentedBodyBendHeadStabilizationAndTailDrag() {
        AncientDragonInertiaSimulation simulation = new AncientDragonInertiaSimulation();
        simulation.sample(frame(0, 0.0, 0.0, 0.0, 0.0, "ancient_dragon:fly_cruise"), 1.0F);

        Map<String, BoneRotation> response = Map.of();
        for (int tick = 1; tick <= 16; tick++) {
            response = simulation.sample(
                    frame(tick, 0.0, 0.0, 0.0, tick * 6.0, "ancient_dragon:fly_cruise"),
                    1.0F);
        }

        double rootYaw = response.get("root").yawRadians();
        double pelvisYaw = response.get("pelvis").yawRadians();
        double spineYaw = response.get("spine_01").yawRadians();
        double chestYaw = response.get("chest").yawRadians();
        assertTrue(rootYaw < 0.0);
        assertTrue(pelvisYaw < rootYaw);
        assertTrue(spineYaw < pelvisYaw);
        assertTrue(chestYaw < spineYaw);
        assertTrue(Math.abs(rootYaw + pelvisYaw + spineYaw + chestYaw) > Math.toRadians(2.0));

        assertTrue(response.get("neck_04").yawRadians() < 0.0);
        assertTrue(response.get("head").yawRadians() > 0.0);
        assertTrue(Math.abs(response.get("tail_08").yawRadians())
                > Math.abs(response.get("tail_01").yawRadians()));
    }

    @Test
    void tailAttackHardMasksTailWhileRetainingSmallNonGameplayResponseElsewhere() {
        AncientDragonInertiaSimulation simulation = movingSimulation();
        Map<String, BoneRotation> response = simulation.sample(
                frame(3, 0.24, 0.22, 1.20, 18.0, "ancient_dragon:aerial_tail_sweep"), 1.0F);

        assertEquals(BoneRotation.ZERO, response.get("tail_01"));
        assertEquals(BoneRotation.ZERO, response.get("tail_08"));
        assertTrue(response.get("wing_main_vein_C_04.L").magnitudeSquared() > 0.0);
        assertTrue(response.get("root").magnitudeSquared() > 0.0);
    }

    @Test
    void dormantAndDeathStatesSuppressEveryProceduralRotation() {
        AncientDragonInertiaSimulation simulation = movingSimulation();
        assertAllZero(simulation.sample(
                frame(3, 0.24, 0.22, 1.20, 18.0, "ancient_dragon:dormant_hold"), 1.0F));
        assertAllZero(simulation.sample(
                frame(4, 0.42, 0.20, 1.55, 24.0, "ancient_dragon:death_landmark"), 1.0F));
    }

    @Test
    void teleportAndLongExtractionGapResetStoredMomentum() {
        AncientDragonInertiaSimulation simulation = movingSimulation();
        assertAllZero(simulation.sample(
                frame(3, 100.0, 80.0, -100.0, 160.0, "ancient_dragon:fly_cruise"), 1.0F));

        simulation = movingSimulation();
        assertAllZero(simulation.sample(
                frame(12, 1.0, 0.0, 1.0, 45.0, "ancient_dragon:fly_cruise"), 1.0F));
    }

    @Test
    void partialTickInterpolatesWithoutAdvancingTheSimulationTwice() {
        AncientDragonInertiaSimulation simulation = new AncientDragonInertiaSimulation();
        simulation.sample(frame(0, 0.0, 0.0, 0.0, 0.0, "ancient_dragon:fly_cruise"), 1.0F);
        Map<String, BoneRotation> atTickStart = simulation.sample(
                frame(1, 0.0, 0.12, 0.42, 6.0, "ancient_dragon:fly_cruise"), 0.0F);
        Map<String, BoneRotation> atTickEnd = simulation.sample(
                frame(1, 0.0, 0.12, 0.42, 6.0, "ancient_dragon:fly_cruise"), 1.0F);
        Map<String, BoneRotation> repeated = simulation.sample(
                frame(1, 0.0, 0.12, 0.42, 6.0, "ancient_dragon:fly_cruise"), 1.0F);

        assertTrue(atTickEnd.get("tail_08").magnitudeSquared()
                > atTickStart.get("tail_08").magnitudeSquared());
        assertEquals(atTickEnd, repeated);
    }

    @Test
    void leavingAnAttackMaskCannotRevealMomentumAccumulatedBehindTheMask() {
        AncientDragonInertiaSimulation simulation = movingSimulation();
        simulation.sample(
                frame(3, 0.24, 0.22, 1.20, 18.0, "ancient_dragon:aerial_tail_sweep"), 1.0F);
        simulation.sample(
                frame(4, 0.44, 0.30, 1.58, 24.0, "ancient_dragon:aerial_tail_sweep"), 1.0F);
        Map<String, BoneRotation> maskedTail = simulation.sample(
                frame(5, 0.70, 0.36, 1.90, 30.0, "ancient_dragon:aerial_tail_sweep"), 1.0F);
        assertEquals(BoneRotation.ZERO, maskedTail.get("tail_01"));
        assertEquals(BoneRotation.ZERO, maskedTail.get("tail_08"));
        Map<String, BoneRotation> tailSweepExit = simulation.sample(
                frame(5, 0.70, 0.36, 1.90, 30.0, "ancient_dragon:fly_cruise"), 1.0F);
        assertEquals(BoneRotation.ZERO, tailSweepExit.get("root"));
        assertEquals(BoneRotation.ZERO, tailSweepExit.get("tail_01"));
        assertEquals(BoneRotation.ZERO, tailSweepExit.get("tail_08"));
        assertEquals(BoneRotation.ZERO, tailSweepExit.get("wing_main_vein_C_04.L"));

        simulation = movingSimulation();
        simulation.sample(
                frame(3, 0.24, 0.22, 1.20, 18.0, "ancient_dragon:bite_heavy"), 1.0F);
        Map<String, BoneRotation> biteExit = simulation.sample(
                frame(3, 0.24, 0.22, 1.20, 18.0, "ancient_dragon:combat_idle"), 1.0F);
        assertEquals(BoneRotation.ZERO, biteExit.get("neck_04"));
        assertEquals(BoneRotation.ZERO, biteExit.get("head"));
    }

    private static AncientDragonInertiaSimulation movingSimulation() {
        AncientDragonInertiaSimulation simulation = new AncientDragonInertiaSimulation();
        simulation.sample(frame(0, 0.0, 0.0, 0.0, 0.0, "ancient_dragon:fly_cruise"), 1.0F);
        simulation.sample(frame(1, 0.0, 0.12, 0.42, 6.0, "ancient_dragon:fly_cruise"), 1.0F);
        simulation.sample(frame(2, 0.10, 0.20, 0.82, 12.0, "ancient_dragon:fly_cruise"), 1.0F);
        return simulation;
    }

    private static KinematicFrame frame(
            long tick, double x, double y, double z, double yaw, String animation) {
        return new KinematicFrame(tick, x, y, z, yaw, animation);
    }

    private static void assertAllZero(Map<String, BoneRotation> rotations) {
        rotations.forEach((name, rotation) -> assertEquals(BoneRotation.ZERO, rotation, name));
    }
}
