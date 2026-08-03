package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonPoseDynamics;
import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.worldgen.SacredMountainShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AncientDragonClearanceEnvelopeTest {
    @Test
    void authoredNestContainsEveryDormantAwakenAndTakeoffCollisionFrame() {
        AncientDragonClearanceEnvelope.Bounds bounds = AncientDragonClearanceEnvelope.bounds();
        assertTrue(bounds.eastFacingWidth() <= SacredMountainShape.NEST_CLEARANCE_WIDTH,
                "east-facing animation width exceeds nest clearance: " + bounds.eastFacingWidth());
        assertTrue(bounds.eastFacingDepth() <= SacredMountainShape.NEST_CLEARANCE_DEPTH,
                "east-facing animation depth exceeds nest clearance: " + bounds.eastFacingDepth());
        assertTrue(bounds.height() <= SacredMountainShape.NEST_CLEARANCE_HEIGHT,
                "animation height exceeds nest clearance: " + bounds.height());
        assertTrue(bounds.minimumZ() >= -SacredMountainShape.NEST_CLEARANCE_WIDTH / 2.0D
                        && bounds.maximumZ() <= SacredMountainShape.NEST_CLEARANCE_WIDTH / 2.0D
                        && -bounds.maximumX() >= -SacredMountainShape.NEST_CLEARANCE_DEPTH / 2.0D
                        && -bounds.minimumX() <= SacredMountainShape.NEST_CLEARANCE_DEPTH / 2.0D,
                "east-facing animation is offset outside the centered clearance: " + bounds);
        assertTrue(bounds.minimumY() + SacredMountainShape.DRAGON_ANCHOR_LIFT >= 1.0D,
                "animation descends through nest floor: minimumY=" + bounds.minimumY()
                        + " lift=" + SacredMountainShape.DRAGON_ANCHOR_LIFT);
    }

    @Test
    void everyAnimatedCollisionPartClearsTheFinalMountainVoxels() {
        SacredMountainShape shape = new SacredMountainShape(SacredMountainShape.DEFAULT_SEED);
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        DragonQuaternion east = DragonQuaternion.fromMinecraftYaw(-90.0F);
        DragonPoseState groundedPose = pose(east, Curve.ZERO, Curve.ZERO);
        DragonPoseDynamics dynamics = new DragonPoseDynamics();
        dynamics.reset(com.liy.ancientdragon.animation.pose.DragonStagedPose.matching(groundedPose));

        LiftRequirement requirement = requiredLiftForClip(
                shape,
                rig,
                AncientDragonCollisionRig.DORMANT,
                2,
                true,
                "ancient_dragon:dormant_hold",
                groundedPose,
                dynamics,
                Vec3.ZERO);
        requirement = requirement.max(requiredLiftForClip(
                shape,
                rig,
                AncientDragonCollisionRig.AWAKEN,
                AncientDragonEntity.AWAKEN_DURATION_TICKS,
                false,
                "ancient_dragon:awaken_intro",
                groundedPose,
                dynamics,
                Vec3.ZERO));

        requirement = requirement.max(requiredLiftForClip(
                shape,
                rig,
                AncientDragonCollisionRig.TAKEOFF,
                AncientDragonEntity.TAKEOFF_DURATION_TICKS,
                false,
                "ancient_dragon:takeoff",
                groundedPose,
                dynamics,
                Vec3.ZERO));
        assertTrue(
                SacredMountainShape.DRAGON_ANCHOR_LIFT >= requirement.lift(),
                "configured dragon anchor lift=" + SacredMountainShape.DRAGON_ANCHOR_LIFT
                        + " but voxel-tested animations require " + requirement.lift()
                        + "; worst=" + requirement.detail());
    }

    private static LiftRequirement requiredLiftForClip(
            SacredMountainShape shape,
            AncientDragonCollisionRig rig,
            String clip,
            int durationTicks,
            boolean loop,
            String animationKey,
            DragonPoseState pose,
            DragonPoseDynamics dynamics,
            Vec3 rootOffset) {
        LiftRequirement requirement = LiftRequirement.NONE;
        for (int tick = 0; tick <= durationTicks; tick++) {
            dynamics.advance(pose);
            requirement = requirement.max(requiredLiftForFrame(
                    shape, rig, clip, tick, loop, animationKey, pose, dynamics, rootOffset));
        }
        return requirement;
    }

    private static LiftRequirement requiredLiftForFrame(
            SacredMountainShape shape,
            AncientDragonCollisionRig rig,
            String clip,
            int tick,
            boolean loop,
            String animationKey,
            DragonPoseState pose,
            DragonPoseDynamics dynamics,
            Vec3 rootOffset) {
        AncientDragonCollisionRig.SolvedFrame solved = rig.solve(
                rig.sample(clip, tick, loop), pose, dynamics.snapshot(), animationKey);
        Vec3 anchor = new Vec3(
                SacredMountainShape.NEST_CENTER_X,
                SacredMountainShape.NEST_HEIGHT + SacredMountainShape.DRAGON_ANCHOR_LIFT,
                SacredMountainShape.NEST_CENTER_Z).add(rootOffset);
        LiftRequirement requirement = LiftRequirement.NONE;
        for (AncientDragonPartKind kind : AncientDragonPartKind.values()) {
            Vec3 center = anchor.add(pose.orientation().rotate(solved.position(kind)));
            DragonQuaternion rotation = pose.orientation().multiply(solved.rotation(kind));
            AncientDragonPartKind.ProjectedDimensions dimensions = kind.project(rotation);
            double halfX = dimensions.xSize() * 0.5D;
            double halfHeight = dimensions.ySize() * 0.5D;
            double halfZ = dimensions.zSize() * 0.5D;
            int minimumX = (int) Math.floor(center.x - halfX + 1.0e-7D);
            int maximumX = (int) Math.floor(center.x + halfX - 1.0e-7D);
            int minimumZ = (int) Math.floor(center.z - halfZ + 1.0e-7D);
            int maximumZ = (int) Math.floor(center.z + halfZ - 1.0e-7D);
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    int topSolid = shape.topSolidHeightAt(x, z);
                    int candidate = (int) Math.ceil(
                            SacredMountainShape.DRAGON_ANCHOR_LIFT
                                    + topSolid + 1.0D - (center.y - halfHeight));
                    requirement = requirement.max(new LiftRequirement(
                            candidate,
                            clip + " tick=" + tick + " part=" + kind.serializedName()
                                    + " column=" + x + "," + z + " top=" + topSolid
                                    + " partMinY=" + (center.y - halfHeight)));
                }
            }
        }
        return requirement;
    }

    private static DragonPoseState pose(
            DragonQuaternion orientation, Curve torso, Curve tail) {
        return new DragonPoseState(
                orientation,
                torso,
                tail,
                0.0D,
                0.0D,
                AttentionMode.SCAN,
                -1,
                Maneuver.NONE);
    }

    private record LiftRequirement(int lift, String detail) {
        private static final LiftRequirement NONE = new LiftRequirement(0, "none");

        private LiftRequirement max(LiftRequirement other) {
            return other.lift > lift ? other : this;
        }
    }
}
