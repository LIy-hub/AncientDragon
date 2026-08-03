package com.liy.ancientdragon.client.inertia;

import com.liy.ancientdragon.animation.inertia.AncientDragonInertiaProfile;
import com.liy.ancientdragon.animation.inertia.AncientDragonInertiaSimulation;
import com.liy.ancientdragon.animation.inertia.AncientDragonInertiaSimulation.BoneRotation;
import com.liy.ancientdragon.animation.inertia.AncientDragonInertiaSimulation.KinematicFrame;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.blendlib.fabric.client.entity.BlendEntityPoseContext;
import com.liy.blendlib.fabric.client.entity.BlendEntityRotation;
import com.liy.blendlib.fabric.client.entity.BlendEntityRotationPose;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Owns client-only procedural inertia state for every currently loaded Ancient Dragon. */
public final class AncientDragonInertiaSystem {
    private static final double MINIMUM_ROTATION_SQUARED = 1.0e-18;

    private final Map<AncientDragonEntity, EntityState> states = new IdentityHashMap<>();

    /** Registers lifecycle cleanup so renderer-owned state cannot outlive a client entity/world. */
    public void registerLifecycleHooks() {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AncientDragonEntity dragon) {
                states.remove(dragon);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> states.clear());
    }

    /** Applies one bounded rotation-only secondary-motion layer over BlendLib's sampled pose. */
    public BlendEntityRotationPose modify(
            AncientDragonEntity entity,
            BlendEntityPoseContext context,
            BlendEntityRotationPose basePose) {
        EntityState state = states.computeIfAbsent(entity, ignored -> new EntityState());
        if (state.generation != context.generation()) {
            state.prepare(context);
        }

        Map<String, BoneRotation> rotations = state.simulation.sample(
                new KinematicFrame(
                        context.extractionRequest().clientGameTick(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        entity.getYRot(),
                        context.animationKey().value()),
                context.extractionRequest().partialTick());

        LinkedHashMap<Integer, BlendEntityRotation> rotationOverrides = null;
        for (AncientDragonInertiaProfile.Bone bone : AncientDragonInertiaProfile.bones()) {
            BoneRotation rotation = rotations.get(bone.name());
            if (rotation == null || rotation.magnitudeSquared() <= MINIMUM_ROTATION_SQUARED) {
                continue;
            }
            if (rotationOverrides == null) {
                rotationOverrides = new LinkedHashMap<>();
            }
            int nodeIndex = state.nodeIndices.get(bone.name());
            BlendEntityRotation delta = eulerDelta(rotation);
            rotationOverrides.put(nodeIndex, multiply(delta, basePose.rotation(nodeIndex)));
        }
        return rotationOverrides == null ? basePose : basePose.withRotations(rotationOverrides);
    }

    private static BlendEntityRotation eulerDelta(BoneRotation rotation) {
        BlendEntityRotation yaw = axisAngle(0.0, 1.0, 0.0, rotation.yawRadians());
        BlendEntityRotation pitch = axisAngle(1.0, 0.0, 0.0, rotation.pitchRadians());
        BlendEntityRotation roll = axisAngle(0.0, 0.0, 1.0, rotation.rollRadians());
        return multiply(multiply(yaw, pitch), roll);
    }

    private static BlendEntityRotation axisAngle(double x, double y, double z, double radians) {
        double halfAngle = radians * 0.5;
        double sine = Math.sin(halfAngle);
        return BlendEntityRotation.normalized(
                (float) (x * sine),
                (float) (y * sine),
                (float) (z * sine),
                (float) Math.cos(halfAngle));
    }

    private static BlendEntityRotation multiply(BlendEntityRotation left, BlendEntityRotation right) {
        return BlendEntityRotation.normalized(
                left.w() * right.x() + left.x() * right.w() + left.y() * right.z() - left.z() * right.y(),
                left.w() * right.y() - left.x() * right.z() + left.y() * right.w() + left.z() * right.x(),
                left.w() * right.z() + left.x() * right.y() - left.y() * right.x() + left.z() * right.w(),
                left.w() * right.w() - left.x() * right.x() - left.y() * right.y() - left.z() * right.z());
    }

    private static final class EntityState {
        private final AncientDragonInertiaSimulation simulation = new AncientDragonInertiaSimulation();
        private final Map<String, Integer> nodeIndices = new LinkedHashMap<>();
        private long generation = -1L;

        private void prepare(BlendEntityPoseContext context) {
            nodeIndices.clear();
            for (AncientDragonInertiaProfile.Bone bone : AncientDragonInertiaProfile.bones()) {
                nodeIndices.put(bone.name(), context.rig().requireNodeIndex(bone.name()));
            }
            simulation.reset();
            generation = context.generation();
        }
    }
}
