package com.liy.ancientdragon.animation.pose;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Shared deterministic expansion of compact pose state into procedural bone rotations. */
public final class DragonPoseMath {
    private static final String[] BODY_BONES = {"root", "pelvis", "spine_01", "chest"};
    private static final double[] BODY_WEIGHTS = {0.06D, 0.24D, 0.34D, 0.36D};
    private static final String[] TAIL_BONES = {
        "tail_01", "tail_02", "tail_03", "tail_04", "tail_05", "tail_06", "tail_07", "tail_08"
    };
    private static final double[] TAIL_WEIGHTS = {0.06D, 0.08D, 0.10D, 0.12D, 0.14D, 0.16D, 0.17D, 0.17D};
    private static final String[] NECK_BONES = {"neck_01", "neck_02", "neck_03", "neck_04", "head"};
    private static final double[] NECK_YAW_WEIGHTS = {0.18D, 0.22D, 0.24D, 0.22D, 0.14D};

    private DragonPoseMath() {
    }

    public static Map<String, BoneRotation> solve(DragonPoseState pose, String animationKey) {
        if (pose == null || animationKey == null) {
            throw new NullPointerException("pose and animationKey");
        }
        if (!allowsProceduralPose(animationKey)) {
            return Map.of();
        }
        double bodyWeight = bodyMask(animationKey);
        double tailWeight = tailMask(animationKey);
        LinkedHashMap<String, BoneRotation> rotations = new LinkedHashMap<>();

        addChain(rotations, BODY_BONES, BODY_WEIGHTS, pose.torso(), bodyWeight);
        addChain(rotations, TAIL_BONES, TAIL_WEIGHTS, pose.tail(), tailWeight);

        double bank = bankRadians(pose.orientation());
        double wingDeflection = Math.clamp(bank * 0.24D, Math.toRadians(-10.0D), Math.toRadians(10.0D));
        add(rotations, "wing_main_01.L", new BoneRotation(wingDeflection * 0.22D, 0.0D, -wingDeflection));
        add(rotations, "wing_main_01.R", new BoneRotation(-wingDeflection * 0.22D, 0.0D, -wingDeflection));
        add(rotations, "wing_secondary_01.L", new BoneRotation(wingDeflection * 0.35D, 0.0D, -wingDeflection * 0.65D));
        add(rotations, "wing_secondary_01.R", new BoneRotation(-wingDeflection * 0.35D, 0.0D, -wingDeflection * 0.65D));
        add(rotations, "thigh.L", new BoneRotation(-pose.torso().pitchRadians() * 0.08D, 0.0D, bank * 0.04D));
        add(rotations, "thigh.R", new BoneRotation(-pose.torso().pitchRadians() * 0.08D, 0.0D, bank * 0.04D));

        return Map.copyOf(rotations);
    }

    /** Head/neck pose used by server collision FK; eye-only lead is intentionally client-side. */
    public static Map<String, BoneRotation> solveCollision(DragonPoseState pose, String animationKey) {
        return solve(pose, animationKey);
    }

    public static BoneRotation eyeRotation(double targetYaw, double targetPitch, double solvedHeadYaw, double solvedHeadPitch) {
        return new BoneRotation(
                Math.clamp(targetPitch - solvedHeadPitch, Math.toRadians(-10.0D), Math.toRadians(10.0D)),
                Math.clamp(targetYaw - solvedHeadYaw, Math.toRadians(-18.0D), Math.toRadians(18.0D)),
                0.0D);
    }

    public static int neckBoneCount() {
        return NECK_BONES.length;
    }

    public static int tailBoneCount() {
        return TAIL_BONES.length;
    }

    public static String tailBoneName(int index) {
        return TAIL_BONES[index];
    }

    public static BoneRotation tailRotation(int index, DragonPoseState.Curve curve, String animationKey) {
        if (index < 0 || index >= TAIL_BONES.length) {
            throw new IndexOutOfBoundsException(index);
        }
        double weight = TAIL_WEIGHTS[index] * tailMask(animationKey);
        return new BoneRotation(
                curve.pitchRadians() * weight,
                curve.yawRadians() * weight,
                curve.rollRadians() * weight);
    }

    public static String neckBoneName(int index) {
        return NECK_BONES[index];
    }

    public static double neckWeight(int index) {
        return NECK_YAW_WEIGHTS[index];
    }

    public static double attentionBlendWeight(String animationKey) {
        return attentionMask(animationKey);
    }

    public static double constrainedAttentionYaw(double yaw, double pitch) {
        return ellipticalYaw(yaw, constrainedAttentionPitch(pitch));
    }

    public static double constrainedAttentionPitch(double pitch) {
        return Math.clamp(pitch, Math.toRadians(-110.0D), Math.toRadians(80.0D));
    }

    private static void addChain(
            Map<String, BoneRotation> rotations,
            String[] bones,
            double[] weights,
            DragonPoseState.Curve curve,
            double mask) {
        for (int index = 0; index < bones.length; index++) {
            double weight = weights[index] * mask;
            add(rotations, bones[index], new BoneRotation(
                    curve.pitchRadians() * weight,
                    curve.yawRadians() * weight,
                    curve.rollRadians() * weight));
        }
    }

    private static void add(Map<String, BoneRotation> rotations, String bone, BoneRotation addition) {
        if (addition.magnitudeSquared() < 1.0e-18D) {
            return;
        }
        rotations.merge(bone, addition, BoneRotation::plus);
    }

    private static double ellipticalYaw(double yaw, double pitch) {
        double verticalLimit = pitch < 0.0D ? Math.toRadians(110.0D) : Math.toRadians(80.0D);
        double verticalFraction = Math.clamp(Math.abs(pitch) / verticalLimit, 0.0D, 1.0D);
        double yawLimit = Math.toRadians(135.0D) * Math.sqrt(Math.max(0.0D, 1.0D - verticalFraction * verticalFraction * 0.42D));
        return Math.clamp(yaw, -yawLimit, yawLimit);
    }

    private static boolean allowsProceduralPose(String animationKey) {
        return !animationKey.equals("ancient_dragon:dormant_hold")
                && !animationKey.equals("ancient_dragon:death_landmark")
                && !animationKey.equals("ancient_dragon:corpse_static");
    }

    private static double bodyMask(String animationKey) {
        return switch (animationKey) {
            case "ancient_dragon:bite_heavy" -> 0.42D;
            case "ancient_dragon:dive_strike" -> 1.0D;
            case "ancient_dragon:aerial_tail_sweep" -> 0.32D;
            default -> 1.0D;
        };
    }

    private static double tailMask(String animationKey) {
        return switch (animationKey) {
            case "ancient_dragon:aerial_tail_sweep" -> 0.0D;
            case "ancient_dragon:dive_strike" -> 0.55D;
            default -> 1.0D;
        };
    }

    private static double attentionMask(String animationKey) {
        return switch (animationKey) {
            case "ancient_dragon:bite_heavy" -> 0.0D;
            case "ancient_dragon:aerial_tail_sweep" -> 0.28D;
            default -> 1.0D;
        };
    }

    private static double bankRadians(DragonQuaternion orientation) {
        Vec3 forward = orientation.forward();
        Vec3 referenceUp = new Vec3(0.0D, 1.0D, 0.0D).subtract(forward.scale(forward.y));
        if (referenceUp.lengthSqr() < 1.0e-10D) {
            return 0.0D;
        }
        referenceUp = referenceUp.normalize();
        Vec3 actualUp = orientation.up().subtract(forward.scale(orientation.up().dot(forward)));
        if (actualUp.lengthSqr() < 1.0e-10D) {
            return 0.0D;
        }
        actualUp = actualUp.normalize();
        return Math.atan2(forward.dot(referenceUp.cross(actualUp)), referenceUp.dot(actualUp));
    }

    /** Local anatomical rotation: pitch about +X, lateral yaw about +Z, axial twist about +Y. */
    public record BoneRotation(double pitchRadians, double yawRadians, double rollRadians) {
        public static final BoneRotation ZERO = new BoneRotation(0.0D, 0.0D, 0.0D);

        public BoneRotation {
            if (!Double.isFinite(pitchRadians) || !Double.isFinite(yawRadians) || !Double.isFinite(rollRadians)) {
                throw new IllegalArgumentException("Bone rotation must be finite");
            }
        }

        public BoneRotation plus(BoneRotation other) {
            return new BoneRotation(
                    pitchRadians + other.pitchRadians,
                    yawRadians + other.yawRadians,
                    rollRadians + other.rollRadians);
        }

        public double magnitudeSquared() {
            return pitchRadians * pitchRadians + yawRadians * yawRadians + rollRadians * rollRadians;
        }
    }
}
