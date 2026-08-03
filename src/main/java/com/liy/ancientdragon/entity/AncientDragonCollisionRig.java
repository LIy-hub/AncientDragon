package com.liy.ancientdragon.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liy.ancientdragon.animation.pose.DragonPoseMath;
import com.liy.ancientdragon.animation.pose.DragonPoseMath.BoneRotation;
import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.animation.pose.DragonRigPoseSolver;
import com.liy.ancientdragon.animation.pose.DragonStagedPose;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Immutable format-v2 local-TRS collision skeleton baked from the shipped GLB. */
final class AncientDragonCollisionRig {
    static final String DORMANT = "DRG_Dormant_Hold";
    static final String AWAKEN = "DRG_Awaken_Intro";
    static final String TAKEOFF = "DRG_Takeoff";
    static final String CRUISE = "DRG_Fly_Cruise_Loop";
    static final String DESCEND = "DRG_Fly_Descend";
    static final String LAND = "DRG_Land";
    static final String COMBAT_IDLE = "DRG_Combat_Idle";
    static final String COMBAT_STAND = "DRG_Combat_Stand";
    static final String GROUND_STALK = "DRG_Ground_Stalk";
    static final String BITE = "DRG_Bite_Heavy";
    static final String WING_SLAM = "DRG_Wing_Slam";
    static final String ROAR_STORM = "DRG_Roar_Storm";
    static final String GROUND_SOLAR = "DRG_Solar_Breath";
    static final String HIT_REACT = "DRG_Hit_React";
    static final String DIVE = "DRG_Dive_Strike";
    static final String TAIL_SWEEP = "DRG_Aerial_Tail_Sweep";
    static final String STORM = "DRG_Aerial_Storm_Burst";
    static final String SOLAR = "DRG_Aerial_Solar_Breath";
    static final String DEATH = "DRG_Death_Landmark";
    static final String CORPSE = "DRG_Corpse_Static";

    private static final String RESOURCE =
            "/assets/ancient_dragon/collision/ancient_dragon_collision_rig.json";
    private static final AncientDragonCollisionRig INSTANCE = loadDefault();

    private final String[] boneNames;
    private final int[] boneParents;
    private final int[] partBones;
    private final Vec3[] partOffsets;
    private final int headBone;
    private final int[] neckBones;
    private final double unitsToBlocks;
    private final Map<String, Clip> clips;

    private AncientDragonCollisionRig(
            String[] boneNames,
            int[] boneParents,
            int[] partBones,
            Vec3[] partOffsets,
            double unitsToBlocks,
            Map<String, Clip> clips) {
        this.boneNames = boneNames;
        this.boneParents = boneParents;
        this.partBones = partBones;
        this.partOffsets = partOffsets;
        this.unitsToBlocks = unitsToBlocks;
        this.clips = Map.copyOf(clips);
        int headIndex = -1;
        int[] resolvedNeckBones = new int[DragonPoseMath.neckBoneCount()];
        java.util.Arrays.fill(resolvedNeckBones, -1);
        for (int index = 0; index < boneNames.length; index++) {
            if (boneNames[index].equals("head")) {
                headIndex = index;
            }
            for (int neckIndex = 0; neckIndex < resolvedNeckBones.length; neckIndex++) {
                if (boneNames[index].equals(DragonPoseMath.neckBoneName(neckIndex))) {
                    resolvedNeckBones[neckIndex] = index;
                }
            }
        }
        if (headIndex < 0 || java.util.Arrays.stream(resolvedNeckBones).anyMatch(index -> index < 0)) {
            throw new IllegalStateException("Collision rig has an incomplete head/neck chain");
        }
        headBone = headIndex;
        neckBones = resolvedNeckBones;
    }

    static AncientDragonCollisionRig instance() {
        return INSTANCE;
    }

    Frame sample(String clipName, int tick, boolean loop) {
        Clip clip = clips.get(clipName);
        if (clip == null) {
            throw new IllegalArgumentException("Unknown Ancient Dragon collision clip: " + clipName);
        }
        int frameIndex = loop
                ? Math.floorMod(tick, clip.durationTicks())
                : Math.clamp(tick, 0, clip.durationTicks());
        return clip.frames()[frameIndex];
    }

    SolvedFrame solve(Frame frame, DragonPoseState pose, String animationKey) {
        return solve(frame, pose, DragonStagedPose.matching(pose), animationKey);
    }

    SolvedFrame solve(
            Frame frame,
            DragonPoseState pose,
            DragonStagedPose staged,
            String animationKey) {
        if (frame == null || pose == null || staged == null || animationKey == null) {
            throw new NullPointerException("Collision solve inputs");
        }
        DragonPoseState rigidTarget = new DragonPoseState(
                pose.orientation(),
                pose.torso(),
                DragonPoseState.Curve.ZERO,
                0.0D,
                0.0D,
                pose.attentionMode(),
                pose.attentionTargetEntityId(),
                pose.maneuver());
        Map<String, BoneRotation> procedural = new LinkedHashMap<>(
                DragonPoseMath.solveCollision(rigidTarget, animationKey));
        for (int index = 0; index < DragonPoseMath.tailBoneCount(); index++) {
            procedural.merge(
                    DragonPoseMath.tailBoneName(index),
                    DragonPoseMath.tailRotation(index, staged.tail(index), animationKey),
                    BoneRotation::plus);
        }
        Map<Integer, DragonQuaternion> localRotations = new LinkedHashMap<>();
        Map<Integer, Integer> parents = new LinkedHashMap<>();
        for (int index = 0; index < boneNames.length; index++) {
            LocalTransform local = frame.localTransforms()[index];
            BoneRotation boneRotation = procedural.get(boneNames[index]);
            localRotations.put(index, boneRotation == null
                    ? local.rotation()
                    : local.rotation().multiply(quaternion(boneRotation)));
            if (boneParents[index] >= 0) {
                parents.put(index, boneParents[index]);
            }
        }
        localRotations = DragonRigPoseSolver.solveNeck(
                localRotations, parents, neckBones, headBone, staged, animationKey);
        Vec3[] worldTranslations = new Vec3[boneNames.length];
        DragonQuaternion[] worldRotations = new DragonQuaternion[boneNames.length];
        Vec3[] worldScales = new Vec3[boneNames.length];
        for (int index = 0; index < boneNames.length; index++) {
            LocalTransform local = frame.localTransforms()[index];
            DragonQuaternion localRotation = localRotations.get(index);
            int parent = boneParents[index];
            if (parent < 0) {
                worldTranslations[index] = local.translation();
                worldRotations[index] = localRotation;
                worldScales[index] = local.scale();
            } else {
                Vec3 scaledTranslation = multiply(local.translation(), worldScales[parent]);
                worldTranslations[index] = worldTranslations[parent].add(
                        worldRotations[parent].rotate(scaledTranslation));
                worldRotations[index] = worldRotations[parent].multiply(localRotation);
                worldScales[index] = multiply(worldScales[parent], local.scale());
            }
        }

        Vec3[] positions = new Vec3[AncientDragonPartKind.values().length];
        DragonQuaternion[] rotations = new DragonQuaternion[positions.length];
        for (int partIndex = 0; partIndex < positions.length; partIndex++) {
            int bone = partBones[partIndex];
            Vec3 modelPosition = worldTranslations[bone].add(worldRotations[bone].rotate(
                    multiply(partOffsets[partIndex], worldScales[bone])));
            positions[partIndex] = modelPosition.scale(unitsToBlocks);
            rotations[partIndex] = worldRotations[bone];
        }
        Vec3 headForward = worldRotations[headBone].rotate(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        return new SolvedFrame(positions, rotations, headForward);
    }

    int clipCount() {
        return clips.size();
    }

    int boneCount() {
        return boneNames.length;
    }

    private static DragonQuaternion quaternion(BoneRotation rotation) {
        DragonQuaternion yaw = DragonQuaternion.axisAngle(
                new Vec3(0.0D, 0.0D, 1.0D), rotation.yawRadians());
        DragonQuaternion pitch = DragonQuaternion.axisAngle(
                new Vec3(1.0D, 0.0D, 0.0D), rotation.pitchRadians());
        DragonQuaternion roll = DragonQuaternion.axisAngle(
                new Vec3(0.0D, 1.0D, 0.0D), rotation.rollRadians());
        return yaw.multiply(pitch).multiply(roll);
    }

    static Vec3 rotateByMinecraftYaw(Vec3 modelVector, float yawDegrees) {
        return DragonQuaternion.fromMinecraftYaw(yawDegrees).rotate(modelVector);
    }

    private static Vec3 multiply(Vec3 left, Vec3 right) {
        return new Vec3(left.x * right.x, left.y * right.y, left.z * right.z);
    }

    private static AncientDragonCollisionRig loadDefault() {
        try (InputStream stream = AncientDragonCollisionRig.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Ancient Dragon collision rig " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (root.get("format_version").getAsInt() != 2) {
                throw new IllegalStateException("Unsupported Ancient Dragon collision rig format");
            }
            validatePartOrder(root.getAsJsonArray("part_order"));
            double unitsToBlocks = root.get("units_to_blocks").getAsDouble();
            if (!Double.isFinite(unitsToBlocks) || unitsToBlocks <= 0.0D) {
                throw new IllegalStateException("Collision rig has invalid units_to_blocks");
            }

            JsonArray boneOrderArray = root.getAsJsonArray("bone_order");
            JsonArray parentArray = root.getAsJsonArray("bone_parents");
            if (boneOrderArray.isEmpty() || parentArray.size() != boneOrderArray.size()) {
                throw new IllegalStateException("Collision rig bone metadata is incomplete");
            }
            String[] boneNames = new String[boneOrderArray.size()];
            int[] parents = new int[boneNames.length];
            for (int index = 0; index < boneNames.length; index++) {
                boneNames[index] = boneOrderArray.get(index).getAsString();
                parents[index] = parentArray.get(index).getAsInt();
                if (boneNames[index].isBlank() || parents[index] >= index || parents[index] < -1) {
                    throw new IllegalStateException("Collision rig bone order is not parent-first");
                }
            }

            JsonArray partBoneArray = root.getAsJsonArray("part_bones");
            JsonArray partOffsetArray = root.getAsJsonArray("part_offsets");
            int partCount = AncientDragonPartKind.values().length;
            if (partBoneArray.size() != partCount || partOffsetArray.size() != partCount) {
                throw new IllegalStateException("Collision rig part anchors are incomplete");
            }
            int[] partBones = new int[partCount];
            Vec3[] partOffsets = new Vec3[partCount];
            for (int index = 0; index < partCount; index++) {
                partBones[index] = partBoneArray.get(index).getAsInt();
                if (partBones[index] < 0 || partBones[index] >= boneNames.length) {
                    throw new IllegalStateException("Collision rig part references an invalid bone");
                }
                partOffsets[index] = finiteVector(partOffsetArray.get(index).getAsJsonArray(), 0, "part offset");
            }

            JsonObject clipObjects = root.getAsJsonObject("clips");
            Map<String, Clip> clips = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : clipObjects.entrySet()) {
                clips.put(entry.getKey(), decodeClip(entry.getKey(), entry.getValue().getAsJsonObject(), boneNames.length));
            }
            for (String required : new String[] {
                    DORMANT, AWAKEN, TAKEOFF, CRUISE, DESCEND, LAND,
                    COMBAT_IDLE, COMBAT_STAND, GROUND_STALK, BITE, WING_SLAM,
                    ROAR_STORM, GROUND_SOLAR, HIT_REACT,
                    DIVE, TAIL_SWEEP, STORM, SOLAR, DEATH, CORPSE}) {
                if (!clips.containsKey(required)) {
                    throw new IllegalStateException("Collision rig is missing required clip " + required);
                }
            }
            return new AncientDragonCollisionRig(
                    boneNames, parents, partBones, partOffsets, unitsToBlocks, clips);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Ancient Dragon collision rig", exception);
        }
    }

    private static void validatePartOrder(JsonArray partOrder) {
        AncientDragonPartKind[] kinds = AncientDragonPartKind.values();
        if (partOrder.size() != kinds.length) {
            throw new IllegalStateException(
                    "Collision rig part count " + partOrder.size() + " does not match " + kinds.length);
        }
        for (int index = 0; index < kinds.length; index++) {
            String actual = partOrder.get(index).getAsString();
            if (!kinds[index].serializedName().equals(actual)) {
                throw new IllegalStateException(
                        "Collision rig part " + index + " is " + actual + ", expected "
                                + kinds[index].serializedName());
            }
        }
    }

    private static Clip decodeClip(String name, JsonObject object, int boneCount) {
        int durationTicks = object.get("duration_ticks").getAsInt();
        if (durationTicks <= 0) {
            throw new IllegalStateException("Collision clip " + name + " has invalid duration");
        }
        JsonArray framesArray = object.getAsJsonArray("local_trs");
        if (framesArray.size() != durationTicks + 1) {
            throw new IllegalStateException("Collision clip " + name + " frame count does not match duration");
        }
        Frame[] frames = new Frame[framesArray.size()];
        for (int frameIndex = 0; frameIndex < frames.length; frameIndex++) {
            JsonArray flat = framesArray.get(frameIndex).getAsJsonArray();
            if (flat.size() != boneCount * 10) {
                throw new IllegalStateException("Collision clip " + name + " has an invalid local-TRS frame");
            }
            LocalTransform[] transforms = new LocalTransform[boneCount];
            for (int bone = 0; bone < boneCount; bone++) {
                int offset = bone * 10;
                Vec3 translation = finiteVector(flat, offset, name + " translation");
                DragonQuaternion rotation = new DragonQuaternion(
                        flat.get(offset + 3).getAsDouble(),
                        flat.get(offset + 4).getAsDouble(),
                        flat.get(offset + 5).getAsDouble(),
                        flat.get(offset + 6).getAsDouble());
                Vec3 scale = finiteVector(flat, offset + 7, name + " scale");
                transforms[bone] = new LocalTransform(translation, rotation, scale);
            }
            frames[frameIndex] = new Frame(transforms);
        }
        return new Clip(durationTicks, frames);
    }

    private static Vec3 finiteVector(JsonArray values, int offset, String label) {
        Vec3 result = new Vec3(
                values.get(offset).getAsDouble(),
                values.get(offset + 1).getAsDouble(),
                values.get(offset + 2).getAsDouble());
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z)) {
            throw new IllegalStateException(label + " is non-finite");
        }
        return result;
    }

    record Frame(LocalTransform[] localTransforms) {
        Frame {
            if (localTransforms.length == 0) {
                throw new IllegalArgumentException("Collision frame has no bones");
            }
        }
    }

    record SolvedFrame(Vec3[] positions, DragonQuaternion[] rotations, Vec3 headForward) {
        SolvedFrame {
            if (positions.length != AncientDragonPartKind.values().length
                    || rotations.length != positions.length
                    || headForward.lengthSqr() < 0.99D) {
                throw new IllegalArgumentException("Solved collision frame is incomplete");
            }
        }

        Vec3 position(AncientDragonPartKind kind) {
            return positions[kind.ordinal()];
        }

        DragonQuaternion rotation(AncientDragonPartKind kind) {
            return rotations[kind.ordinal()];
        }
    }

    private record LocalTransform(Vec3 translation, DragonQuaternion rotation, Vec3 scale) {
    }

    private record Clip(int durationTicks, Frame[] frames) {
    }
}
