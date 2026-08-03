package com.liy.ancientdragon.animation.pose;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Rig-aware model-space swing/twist solver for the rest-basis-flipped head/neck chain. */
public final class DragonRigPoseSolver {
    private static final Vec3 MODEL_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 HEAD_LOCAL_FORWARD = new Vec3(0.0D, 1.0D, 0.0D);

    private DragonRigPoseSolver() {
    }

    /**
     * Applies fractional powers of one model-space swing/twist target across the five neck joints.
     * Arbitrary authored local bases therefore cannot cancel the intended gaze.
     */
    public static Map<Integer, DragonQuaternion> solveNeck(
            Map<Integer, DragonQuaternion> inputLocals,
            Map<Integer, Integer> parents,
            int[] neckIndices,
            int headIndex,
            DragonStagedPose staged,
            String animationKey) {
        if (inputLocals == null || parents == null || neckIndices == null || staged == null || animationKey == null
                || neckIndices.length != DragonPoseMath.neckBoneCount()) {
            throw new IllegalArgumentException("Rig-aware neck solve inputs are incomplete");
        }
        LinkedHashMap<Integer, DragonQuaternion> locals = new LinkedHashMap<>(inputLocals);
        Map<Integer, DragonQuaternion> baseWorld = worldRotations(locals, parents);
        DragonQuaternion baseHeadWorld = requireRotation(baseWorld, headIndex);
        Vec3 baseHeadForward = baseHeadWorld.rotate(HEAD_LOCAL_FORWARD).normalize();
        double mask = DragonPoseMath.attentionBlendWeight(animationKey);
        if (mask <= 0.0D) {
            return Map.copyOf(locals);
        }

        for (int chainIndex = 0; chainIndex < neckIndices.length; chainIndex++) {
            int nodeIndex = neckIndices[chainIndex];
            double pitch = DragonPoseMath.constrainedAttentionPitch(staged.neckPitch(chainIndex));
            double yaw = DragonPoseMath.constrainedAttentionYaw(staged.neckYaw(chainIndex), pitch);
            Vec3 desired = desiredDirection(baseHeadForward, yaw, pitch);
            DragonQuaternion swing = shortestArc(baseHeadForward, desired);
            double totalTwist = Math.clamp(yaw * 0.10D, Math.toRadians(-30.0D), Math.toRadians(30.0D));
            DragonQuaternion twist = Math.abs(totalTwist) < 1.0e-10D
                    ? DragonQuaternion.IDENTITY
                    : DragonQuaternion.axisAngle(desired, totalTwist);
            DragonQuaternion completeDelta = twist.multiply(swing);
            DragonQuaternion fractionalDelta = DragonQuaternion.IDENTITY.slerp(
                    completeDelta, DragonPoseMath.neckWeight(chainIndex) * mask);

            Map<Integer, DragonQuaternion> currentWorld = worldRotations(locals, parents);
            Integer parentIndex = parents.get(nodeIndex);
            DragonQuaternion parentWorld = parentIndex == null
                    ? DragonQuaternion.IDENTITY
                    : requireRotation(currentWorld, parentIndex);
            DragonQuaternion baseLocal = requireRotation(locals, nodeIndex);
            DragonQuaternion adjustedLocal = parentWorld.conjugate()
                    .multiply(fractionalDelta)
                    .multiply(parentWorld)
                    .multiply(baseLocal);
            locals.put(nodeIndex, adjustedLocal);
        }
        return Map.copyOf(locals);
    }

    public static Vec3 headForward(
            Map<Integer, DragonQuaternion> locals,
            Map<Integer, Integer> parents,
            int headIndex) {
        return requireRotation(worldRotations(locals, parents), headIndex)
                .rotate(HEAD_LOCAL_FORWARD)
                .normalize();
    }

    private static Vec3 desiredDirection(Vec3 baseForward, double yaw, double pitch) {
        DragonQuaternion yawRotation = DragonQuaternion.axisAngle(MODEL_UP, yaw);
        Vec3 yawed = yawRotation.rotate(baseForward).normalize();
        Vec3 right = MODEL_UP.cross(yawed);
        if (right.lengthSqr() < 1.0e-10D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        return DragonQuaternion.axisAngle(right, -pitch).rotate(yawed).normalize();
    }

    private static DragonQuaternion shortestArc(Vec3 from, Vec3 to) {
        Vec3 normalizedFrom = from.normalize();
        Vec3 normalizedTo = to.normalize();
        double dot = Math.clamp(normalizedFrom.dot(normalizedTo), -1.0D, 1.0D);
        if (dot > 0.999999D) {
            return DragonQuaternion.IDENTITY;
        }
        Vec3 axis = normalizedFrom.cross(normalizedTo);
        if (axis.lengthSqr() < 1.0e-12D) {
            axis = Math.abs(normalizedFrom.y) < 0.9D
                    ? normalizedFrom.cross(MODEL_UP)
                    : normalizedFrom.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        return DragonQuaternion.axisAngle(axis.normalize(), Math.acos(dot));
    }

    private static Map<Integer, DragonQuaternion> worldRotations(
            Map<Integer, DragonQuaternion> locals,
            Map<Integer, Integer> parents) {
        Map<Integer, DragonQuaternion> world = new HashMap<>();
        Map<Integer, Byte> visit = new HashMap<>();
        for (int nodeIndex : locals.keySet()) {
            resolveWorld(nodeIndex, locals, parents, world, visit);
        }
        return world;
    }

    private static DragonQuaternion resolveWorld(
            int nodeIndex,
            Map<Integer, DragonQuaternion> locals,
            Map<Integer, Integer> parents,
            Map<Integer, DragonQuaternion> world,
            Map<Integer, Byte> visit) {
        DragonQuaternion existing = world.get(nodeIndex);
        if (existing != null) {
            return existing;
        }
        if (visit.put(nodeIndex, (byte) 1) != null) {
            throw new IllegalArgumentException("Rig hierarchy contains a cycle at node " + nodeIndex);
        }
        DragonQuaternion local = requireRotation(locals, nodeIndex);
        Integer parent = parents.get(nodeIndex);
        DragonQuaternion result = parent == null
                ? local
                : resolveWorld(parent, locals, parents, world, visit).multiply(local);
        visit.remove(nodeIndex);
        world.put(nodeIndex, result);
        return result;
    }

    private static DragonQuaternion requireRotation(Map<Integer, DragonQuaternion> rotations, int nodeIndex) {
        DragonQuaternion rotation = rotations.get(nodeIndex);
        if (rotation == null) {
            throw new IllegalArgumentException("Rig pose is missing node " + nodeIndex);
        }
        return rotation;
    }
}
