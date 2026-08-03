package com.liy.ancientdragon.client.pose;

import com.liy.ancientdragon.animation.pose.DragonPoseMath;
import com.liy.ancientdragon.animation.pose.DragonPoseMath.BoneRotation;
import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.animation.pose.DragonRigPoseSolver;
import com.liy.ancientdragon.animation.pose.DragonStagedPose;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.blendlib.fabric.client.entity.BlendEntityPoseContext;
import com.liy.blendlib.fabric.client.entity.BlendEntityRotation;
import com.liy.blendlib.fabric.client.entity.BlendEntityRotationPose;
import com.liy.blendlib.fabric.client.entity.BlendEntitySnapshotRequest;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Interpolates the authoritative compact pose and applies staged procedural bone motion. */
public final class AncientDragonPoseSystem {
    private static final double MINIMUM_ROTATION_SQUARED = 1.0e-18D;
    private static final String[] PROCEDURAL_BONES = {
        "root", "pelvis", "spine_01", "chest",
        "tail_01", "tail_02", "tail_03", "tail_04", "tail_05", "tail_06", "tail_07", "tail_08",
        "neck_01", "neck_02", "neck_03", "neck_04", "head",
        "wing_main_01.L", "wing_main_01.R", "wing_secondary_01.L", "wing_secondary_01.R",
        "thigh.L", "thigh.R", "eye.L", "eye.R"
    };
    private static final double EYE_RESPONSE = 0.465D;

    private final Map<AncientDragonEntity, EntityState> states = new IdentityHashMap<>();

    public void registerLifecycleHooks() {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AncientDragonEntity dragon) {
                states.remove(dragon);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> states.clear());
    }

    public BlendEntityRotation rootRotation(AncientDragonEntity entity, BlendEntitySnapshotRequest request) {
        EntityState state = states.computeIfAbsent(entity, ignored -> new EntityState());
        state.advance(entity, request.clientGameTick());
        DragonQuaternion orientation = state.samplePose(request.partialTick()).orientation();
        return BlendEntityRotation.normalized(
                (float) orientation.x(),
                (float) orientation.y(),
                (float) orientation.z(),
                (float) orientation.w());
    }

    public BlendEntityRotationPose modify(
            AncientDragonEntity entity,
            BlendEntityPoseContext context,
            BlendEntityRotationPose basePose) {
        EntityState state = states.computeIfAbsent(entity, ignored -> new EntityState());
        state.advance(entity, context.extractionRequest().clientGameTick());
        if (state.generation != context.generation()) {
            state.prepareRig(context);
        }
        float partialTick = context.extractionRequest().partialTick();
        DragonPoseState sampled = state.samplePose(partialTick);
        String animationKey = context.animationKey().value();
        DragonPoseState withoutAttention = new DragonPoseState(
                sampled.orientation(),
                sampled.torso(),
                Curve.ZERO,
                0.0D,
                0.0D,
                sampled.attentionMode(),
                sampled.attentionTargetEntityId(),
                sampled.maneuver());
        LinkedHashMap<String, BoneRotation> rotations = new LinkedHashMap<>(
                DragonPoseMath.solve(withoutAttention, animationKey));

        DragonStagedPose staged = state.sampleStaged(partialTick);
        for (int index = 0; index < DragonPoseMath.tailBoneCount(); index++) {
            rotations.merge(
                    DragonPoseMath.tailBoneName(index),
                    DragonPoseMath.tailRotation(index, staged.tail(index), animationKey),
                    BoneRotation::plus);
        }

        double eyeYaw = interpolate(state.previousEyeYaw, state.eyeYaw, partialTick);
        double eyePitch = interpolate(state.previousEyePitch, state.eyePitch, partialTick);
        long microTick = context.extractionRequest().clientGameTick();
        double phase = (entity.getId() * 0.61803398875D) + microTick * 0.31D;
        eyeYaw += Math.sin(phase) * Math.toRadians(0.8D);
        eyePitch += Math.sin(phase * 0.73D + 1.7D) * Math.toRadians(0.45D);
        BoneRotation eyeRotation = DragonPoseMath.eyeRotation(
                eyeYaw,
                eyePitch,
                staged.neckYaw(DragonPoseMath.neckBoneCount() - 1),
                staged.neckPitch(DragonPoseMath.neckBoneCount() - 1));
        rotations.merge("eye.L", eyeRotation, BoneRotation::plus);
        rotations.merge("eye.R", eyeRotation, BoneRotation::plus);

        LinkedHashMap<Integer, BlendEntityRotation> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, BoneRotation> entry : rotations.entrySet()) {
            BoneRotation rotation = entry.getValue();
            if (rotation.magnitudeSquared() <= MINIMUM_ROTATION_SQUARED) {
                continue;
            }
            Integer nodeIndex = state.nodeIndices.get(entry.getKey());
            if (nodeIndex == null) {
                continue;
            }
            // Structural chains advance along each node's local +Y. Post-multiplication applies
            // lateral swing and axial twist in those anatomical local axes.
            overrides.put(nodeIndex, multiply(basePose.rotation(nodeIndex), eulerDelta(rotation)));
        }
        Map<Integer, DragonQuaternion> localPose = new LinkedHashMap<>();
        for (int nodeIndex : basePose.nodeIndices()) {
            BlendEntityRotation effective = overrides.getOrDefault(nodeIndex, basePose.rotation(nodeIndex));
            localPose.put(nodeIndex, fromBlend(effective));
        }
        Map<Integer, DragonQuaternion> neckSolved = DragonRigPoseSolver.solveNeck(
                localPose,
                state.parentIndices,
                state.neckIndices,
                state.headIndex,
                staged,
                animationKey);
        for (int nodeIndex : state.neckIndices) {
            overrides.put(nodeIndex, toBlend(neckSolved.get(nodeIndex)));
        }
        return overrides.isEmpty() ? basePose : basePose.withRotations(overrides);
    }

    private static BlendEntityRotation eulerDelta(BoneRotation rotation) {
        BlendEntityRotation yaw = axisAngle(0.0D, 0.0D, 1.0D, rotation.yawRadians());
        BlendEntityRotation pitch = axisAngle(1.0D, 0.0D, 0.0D, rotation.pitchRadians());
        BlendEntityRotation roll = axisAngle(0.0D, 1.0D, 0.0D, rotation.rollRadians());
        return multiply(multiply(yaw, pitch), roll);
    }

    private static BlendEntityRotation axisAngle(double x, double y, double z, double radians) {
        double half = radians * 0.5D;
        double sine = Math.sin(half);
        return BlendEntityRotation.normalized(
                (float) (x * sine),
                (float) (y * sine),
                (float) (z * sine),
                (float) Math.cos(half));
    }

    private static BlendEntityRotation multiply(BlendEntityRotation left, BlendEntityRotation right) {
        return BlendEntityRotation.normalized(
                left.w() * right.x() + left.x() * right.w() + left.y() * right.z() - left.z() * right.y(),
                left.w() * right.y() - left.x() * right.z() + left.y() * right.w() + left.z() * right.x(),
                left.w() * right.z() + left.x() * right.y() - left.y() * right.x() + left.z() * right.w(),
                left.w() * right.w() - left.x() * right.x() - left.y() * right.y() - left.z() * right.z());
    }

    private static DragonQuaternion fromBlend(BlendEntityRotation rotation) {
        return new DragonQuaternion(rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    private static BlendEntityRotation toBlend(DragonQuaternion rotation) {
        return BlendEntityRotation.normalized(
                (float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w());
    }

    private static double interpolate(double previous, double current, double amount) {
        return previous + (current - previous) * Math.clamp(amount, 0.0D, 1.0D);
    }

    private static final class EntityState {
        private final Map<String, Integer> nodeIndices = new LinkedHashMap<>();
        private final Map<Integer, Integer> parentIndices = new LinkedHashMap<>();
        private final int[] neckIndices = new int[DragonPoseMath.neckBoneCount()];
        private int headIndex = -1;
        private DragonPoseState previousPose = DragonPoseState.IDENTITY;
        private DragonPoseState pose = DragonPoseState.IDENTITY;
        private DragonStagedPose previousStaged = DragonStagedPose.matching(DragonPoseState.IDENTITY);
        private DragonStagedPose staged = DragonStagedPose.matching(DragonPoseState.IDENTITY);
        private double previousEyeYaw;
        private double eyeYaw;
        private double previousEyePitch;
        private double eyePitch;
        private long gameTick = Long.MIN_VALUE;
        private long generation = -1L;

        private void advance(AncientDragonEntity entity, long requestedTick) {
            DragonPoseState networkPose = entity.dragonPoseState();
            DragonStagedPose networkStaged = entity.dragonStagedPose();
            if (gameTick == Long.MIN_VALUE || requestedTick < gameTick || requestedTick - gameTick > 5L) {
                reset(networkPose, networkStaged, requestedTick);
                return;
            }
            if (requestedTick == gameTick) {
                pose = networkPose;
                staged = networkStaged;
                return;
            }
            while (gameTick < requestedTick) {
                previousPose = pose;
                pose = networkPose;
                previousStaged = staged;
                staged = networkStaged;
                previousEyeYaw = eyeYaw;
                previousEyePitch = eyePitch;
                eyeYaw += (pose.attentionYawRadians() - eyeYaw) * EYE_RESPONSE;
                eyePitch += (pose.attentionPitchRadians() - eyePitch) * EYE_RESPONSE;
                gameTick++;
            }
        }

        private void reset(DragonPoseState networkPose, DragonStagedPose networkStaged, long requestedTick) {
            previousPose = networkPose;
            pose = networkPose;
            previousStaged = networkStaged;
            staged = networkStaged;
            previousEyeYaw = networkPose.attentionYawRadians();
            eyeYaw = networkPose.attentionYawRadians();
            previousEyePitch = networkPose.attentionPitchRadians();
            eyePitch = networkPose.attentionPitchRadians();
            gameTick = requestedTick;
        }

        private DragonPoseState samplePose(float partialTick) {
            double amount = Math.clamp(partialTick, 0.0F, 1.0F);
            return new DragonPoseState(
                    previousPose.orientation().slerp(pose.orientation(), amount),
                    previousPose.torso().lerp(pose.torso(), amount),
                    previousPose.tail().lerp(pose.tail(), amount),
                    interpolate(previousPose.attentionYawRadians(), pose.attentionYawRadians(), amount),
                    interpolate(previousPose.attentionPitchRadians(), pose.attentionPitchRadians(), amount),
                    pose.attentionMode(),
                    pose.attentionTargetEntityId(),
                    pose.maneuver());
        }

        private DragonStagedPose sampleStaged(float partialTick) {
            return previousStaged.lerp(staged, partialTick);
        }

        private void prepareRig(BlendEntityPoseContext context) {
            nodeIndices.clear();
            parentIndices.clear();
            for (String bone : PROCEDURAL_BONES) {
                nodeIndices.put(bone, context.rig().requireNodeIndex(bone));
            }
            for (int nodeIndex : context.rig().nodeIndices()) {
                context.rig().parentIndex(nodeIndex).ifPresent(parent -> parentIndices.put(nodeIndex, parent));
            }
            for (int index = 0; index < neckIndices.length; index++) {
                neckIndices[index] = nodeIndices.get(DragonPoseMath.neckBoneName(index));
            }
            headIndex = nodeIndices.get("head");
            generation = context.generation();
        }
    }
}
