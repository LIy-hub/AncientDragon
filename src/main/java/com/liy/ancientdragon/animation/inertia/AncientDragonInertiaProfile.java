package com.liy.ancientdragon.animation.inertia;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Frozen, model-specific secondary-motion profile for the v011 Ancient Dragon rig.
 *
 * <p>The 81 listed joints cover every structural bone. Facial joints and {@code solar_core}
 * intentionally receive only their parents' motion so authored expressions remain undisturbed.</p>
 */
public final class AncientDragonInertiaProfile {
    private static final List<Bone> BONES = buildBones();

    private AncientDragonInertiaProfile() {
    }

    public static List<Bone> bones() {
        return BONES;
    }

    private static List<Bone> buildBones() {
        List<Bone> bones = new ArrayList<>();

        // Turn lag grows from the root toward the chest. Different spring frequencies make the
        // curve travel through the torso instead of rotating the whole creature as one rigid mass.
        bones.add(bone("root", Group.BODY, 0.16, 0.10, 0.08, 1.85, 0.96, 0.35));
        bones.add(bone("pelvis", Group.BODY, 0.18, 0.25, 0.12, 1.65, 0.90, 0.70));
        bones.add(bone("spine_01", Group.BODY, 0.24, 0.35, 0.16, 1.48, 0.85, 1.10));
        bones.add(bone("chest", Group.BODY, 0.30, 0.45, 0.20, 1.32, 0.80, 1.40));

        bones.add(bone("neck_01", Group.NECK, 0.18, 0.20, 0.10, 1.75, 0.90, 0.60));
        bones.add(bone("neck_02", Group.NECK, 0.24, 0.26, 0.13, 1.60, 0.86, 0.80));
        bones.add(bone("neck_03", Group.NECK, 0.30, 0.32, 0.16, 1.45, 0.82, 1.00));
        bones.add(bone("neck_04", Group.NECK, 0.36, 0.38, 0.19, 1.30, 0.78, 1.20));
        // The head counters part of the accumulated neck lag, giving it the stability of a
        // massive animal tracking a target rather than the looseness of a rope endpoint.
        bones.add(bone("head", Group.NECK, -0.32, -0.50, -0.28, 2.10, 0.98, 1.50));

        for (int index = 1; index <= 8; index++) {
            double depth = index - 1.0;
            bones.add(bone(
                    "tail_%02d".formatted(index),
                    Group.TAIL,
                    0.15 + depth * 0.030,
                    0.16 + depth * 0.035,
                    0.08 + depth * 0.015,
                    1.45 - depth * 0.09,
                    0.86 - depth * 0.03,
                    0.25 + depth * 0.08));
        }

        addWing(bones, "wing_main", Group.PRIMARY_WING, "L", 1.0);
        addWing(bones, "wing_main", Group.PRIMARY_WING, "R", -1.0);
        addWing(bones, "wing_secondary", Group.SECONDARY_WING, "L", 0.82);
        addWing(bones, "wing_secondary", Group.SECONDARY_WING, "R", -0.82);

        addLeg(bones, "L", 1.0);
        addLeg(bones, "R", -1.0);

        Set<String> uniqueNames = new HashSet<>();
        for (Bone bone : bones) {
            if (!uniqueNames.add(bone.name())) {
                throw new IllegalStateException("Duplicate inertia bone profile: " + bone.name());
            }
        }
        return List.copyOf(bones);
    }

    private static void addWing(
            List<Bone> bones, String prefix, Group group, String sideName, double mirrorAndStrength) {
        double strength = Math.abs(mirrorAndStrength);
        double mirror = Math.copySign(1.0, mirrorAndStrength);
        bones.add(bone(prefix + "_01." + sideName, group,
                0.12 * strength, 0.10 * mirror, 0.14 * mirror, 3.0, 1.02, 0.12 * strength));
        bones.add(bone(prefix + "_02." + sideName, group,
                0.20 * strength, 0.16 * mirror, 0.22 * mirror, 2.7, 0.98, 0.18 * strength));
        bones.add(bone(prefix + "_03." + sideName, group,
                0.28 * strength, 0.22 * mirror, 0.32 * mirror, 2.4, 0.94, 0.24 * strength));

        addWingVein(bones, prefix, group, sideName, "A", 3, strength, mirror, 0.92);
        addWingVein(bones, prefix, group, sideName, "B", 4, strength, mirror, 1.00);
        addWingVein(bones, prefix, group, sideName, "C", 4, strength, mirror, 1.08);
    }

    private static void addWingVein(
            List<Bone> bones,
            String prefix,
            Group group,
            String sideName,
            String vein,
            int segments,
            double strength,
            double mirror,
            double veinStrength) {
        for (int segment = 1; segment <= segments; segment++) {
            double depth = segment - 1.0;
            bones.add(bone(
                    prefix + "_vein_" + vein + "_%02d.".formatted(segment) + sideName,
                    group,
                    (0.14 + depth * 0.07) * strength * veinStrength,
                    (0.11 + depth * 0.055) * mirror * veinStrength,
                    (0.16 + depth * 0.080) * mirror * veinStrength,
                    2.45 - depth * 0.18,
                    0.94 - depth * 0.035,
                    (0.13 + depth * 0.070) * strength));
        }
    }

    private static void addLeg(List<Bone> bones, String sideName, double mirror) {
        bones.add(bone("thigh." + sideName, Group.LEG,
                0.24, 0.08 * mirror, 0.10 * mirror, 2.0, 0.92, 0.20));
        bones.add(bone("shin." + sideName, Group.LEG,
                0.34, 0.10 * mirror, 0.12 * mirror, 1.8, 0.88, 0.30));
        bones.add(bone("foot." + sideName, Group.LEG,
                0.46, 0.12 * mirror, 0.14 * mirror, 1.6, 0.84, 0.42));
        bones.add(bone("toe." + sideName, Group.LEG,
                0.54, 0.14 * mirror, 0.16 * mirror, 1.5, 0.82, 0.50));
    }

    private static Bone bone(
            String name,
            Group group,
            double pitchResponse,
            double yawResponse,
            double rollResponse,
            double frequencyHertz,
            double dampingRatio,
            double maximumDegrees) {
        return new Bone(
                name,
                group,
                pitchResponse,
                yawResponse,
                rollResponse,
                frequencyHertz,
                dampingRatio,
                Math.toRadians(maximumDegrees));
    }

    public enum Group {
        BODY,
        NECK,
        TAIL,
        PRIMARY_WING,
        SECONDARY_WING,
        LEG
    }

    /** One local-rotation response; translations and scales are never modified. */
    public record Bone(
            String name,
            Group group,
            double pitchResponse,
            double yawResponse,
            double rollResponse,
            double frequencyHertz,
            double dampingRatio,
            double maximumRadians) {
        public Bone {
            if (name == null || name.isBlank() || group == null) {
                throw new IllegalArgumentException("Inertia bone name and group are required");
            }
            if (!Double.isFinite(pitchResponse)
                    || !Double.isFinite(yawResponse)
                    || !Double.isFinite(rollResponse)
                    || !Double.isFinite(frequencyHertz)
                    || !Double.isFinite(dampingRatio)
                    || !Double.isFinite(maximumRadians)
                    || frequencyHertz <= 0.0
                    || dampingRatio <= 0.0
                    || maximumRadians <= 0.0) {
                throw new IllegalArgumentException("Inertia bone parameters must be finite and positive where required");
            }
        }
    }
}
