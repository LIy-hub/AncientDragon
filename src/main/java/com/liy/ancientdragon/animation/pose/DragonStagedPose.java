package com.liy.ancientdragon.animation.pose;

import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;

/**
 * Compact, quantizable authoritative result of the temporal neck and tail followers.
 *
 * <p>Five packed neck pairs and eight packed tail triples are synchronized through vanilla
 * entity data. Both client rendering and server collision FK therefore consume the exact same
 * delayed joint inputs instead of independently simulating latency-sensitive springs.</p>
 */
public final class DragonStagedPose {
    public static final int TAIL_ENCODING_VERSION = 2;

    private static final double NECK_PACK_SCALE = 10_000.0D;
    private static final double TAIL_PITCH_ROLL_PACK_SCALE = 400.0D;
    private static final double TAIL_YAW_PACK_SCALE = 750.0D;
    private static final int TAIL_10_BIT_MINIMUM = -512;
    private static final int TAIL_10_BIT_MAXIMUM = 511;
    private static final int TAIL_YAW_MINIMUM = -2048;
    private static final int TAIL_YAW_MAXIMUM = 2047;

    private final double[] neckYaw;
    private final double[] neckPitch;
    private final Curve[] tail;

    public DragonStagedPose(double[] neckYaw, double[] neckPitch, Curve[] tail) {
        if (neckYaw == null || neckPitch == null || tail == null
                || neckYaw.length != DragonPoseMath.neckBoneCount()
                || neckPitch.length != neckYaw.length
                || tail.length != DragonPoseMath.tailBoneCount()) {
            throw new IllegalArgumentException("Staged pose arrays do not match the frozen dragon rig");
        }
        this.neckYaw = neckYaw.clone();
        this.neckPitch = neckPitch.clone();
        this.tail = tail.clone();
        for (int index = 0; index < this.neckYaw.length; index++) {
            if (!Double.isFinite(this.neckYaw[index]) || !Double.isFinite(this.neckPitch[index])) {
                throw new IllegalArgumentException("Staged neck angles must be finite");
            }
        }
        for (Curve curve : this.tail) {
            if (curve == null) {
                throw new IllegalArgumentException("Staged tail curves must not be null");
            }
        }
    }

    public static DragonStagedPose matching(DragonPoseState pose) {
        double[] yaw = new double[DragonPoseMath.neckBoneCount()];
        double[] pitch = new double[yaw.length];
        Curve[] tail = new Curve[DragonPoseMath.tailBoneCount()];
        for (int index = 0; index < yaw.length; index++) {
            yaw[index] = pose.attentionYawRadians();
            pitch[index] = pose.attentionPitchRadians();
        }
        java.util.Arrays.fill(tail, pose.tail());
        return new DragonStagedPose(yaw, pitch, tail);
    }

    public double neckYaw(int index) {
        return neckYaw[index];
    }

    public double neckPitch(int index) {
        return neckPitch[index];
    }

    public Curve tail(int index) {
        return tail[index];
    }

    public DragonStagedPose lerp(DragonStagedPose target, double amount) {
        double clamped = Math.clamp(amount, 0.0D, 1.0D);
        double[] yaw = new double[neckYaw.length];
        double[] pitch = new double[neckPitch.length];
        Curve[] curves = new Curve[tail.length];
        for (int index = 0; index < yaw.length; index++) {
            yaw[index] = neckYaw[index] + (target.neckYaw[index] - neckYaw[index]) * clamped;
            pitch[index] = neckPitch[index] + (target.neckPitch[index] - neckPitch[index]) * clamped;
        }
        for (int index = 0; index < curves.length; index++) {
            curves[index] = tail[index].lerp(target.tail[index], clamped);
        }
        return new DragonStagedPose(yaw, pitch, curves);
    }

    public int packedNeck(int index) {
        int yaw = Math.clamp((int) Math.round(neckYaw[index] * NECK_PACK_SCALE), Short.MIN_VALUE, Short.MAX_VALUE);
        int pitch = Math.clamp((int) Math.round(neckPitch[index] * NECK_PACK_SCALE), Short.MIN_VALUE, Short.MAX_VALUE);
        return (yaw & 0xffff) | ((pitch & 0xffff) << 16);
    }

    public int packedTail(int index) {
        Curve curve = tail[index];
        int pitch = quantizeTail10(curve.pitchRadians());
        int yaw = Math.clamp(
                (int) Math.round(curve.yawRadians() * TAIL_YAW_PACK_SCALE),
                TAIL_YAW_MINIMUM,
                TAIL_YAW_MAXIMUM);
        int roll = quantizeTail10(curve.rollRadians());
        return (pitch & 0x3ff) | ((yaw & 0xfff) << 10) | ((roll & 0x3ff) << 22);
    }

    /** Version-one NBT compatibility for saves written with three signed ten-bit axes. */
    public int packedTailLegacyV1(int index) {
        Curve curve = tail[index];
        int pitch = quantizeTail10(curve.pitchRadians());
        int yaw = quantizeTail10(curve.yawRadians());
        int roll = quantizeTail10(curve.rollRadians());
        return (pitch & 0x3ff) | ((yaw & 0x3ff) << 10) | ((roll & 0x3ff) << 20);
    }

    public static DragonStagedPose unpack(int[] packedNeck, int[] packedTail) {
        if (packedNeck == null || packedTail == null
                || packedNeck.length != DragonPoseMath.neckBoneCount()
                || packedTail.length != DragonPoseMath.tailBoneCount()) {
            throw new IllegalArgumentException("Packed staged pose has the wrong joint count");
        }
        double[] yaw = new double[packedNeck.length];
        double[] pitch = new double[packedNeck.length];
        Curve[] curves = new Curve[packedTail.length];
        for (int index = 0; index < packedNeck.length; index++) {
            yaw[index] = (short) (packedNeck[index] & 0xffff) / NECK_PACK_SCALE;
            pitch[index] = (short) ((packedNeck[index] >>> 16) & 0xffff) / NECK_PACK_SCALE;
        }
        for (int index = 0; index < packedTail.length; index++) {
            int packed = packedTail[index];
            curves[index] = new Curve(
                    signExtend10(packed) / TAIL_PITCH_ROLL_PACK_SCALE,
                    signExtend12(packed >>> 10) / TAIL_YAW_PACK_SCALE,
                    signExtend10(packed >>> 22) / TAIL_PITCH_ROLL_PACK_SCALE);
        }
        return new DragonStagedPose(yaw, pitch, curves);
    }

    public static DragonStagedPose unpackLegacyV1(int[] packedNeck, int[] packedTail) {
        if (packedNeck == null || packedTail == null
                || packedNeck.length != DragonPoseMath.neckBoneCount()
                || packedTail.length != DragonPoseMath.tailBoneCount()) {
            throw new IllegalArgumentException("Packed staged pose has the wrong joint count");
        }
        double[] yaw = new double[packedNeck.length];
        double[] pitch = new double[packedNeck.length];
        Curve[] curves = new Curve[packedTail.length];
        for (int index = 0; index < packedNeck.length; index++) {
            yaw[index] = (short) (packedNeck[index] & 0xffff) / NECK_PACK_SCALE;
            pitch[index] = (short) ((packedNeck[index] >>> 16) & 0xffff) / NECK_PACK_SCALE;
        }
        for (int index = 0; index < packedTail.length; index++) {
            int packed = packedTail[index];
            curves[index] = new Curve(
                    signExtend10(packed) / TAIL_PITCH_ROLL_PACK_SCALE,
                    signExtend10(packed >>> 10) / TAIL_PITCH_ROLL_PACK_SCALE,
                    signExtend10(packed >>> 20) / TAIL_PITCH_ROLL_PACK_SCALE);
        }
        return new DragonStagedPose(yaw, pitch, curves);
    }

    private static int quantizeTail10(double radians) {
        return Math.clamp(
                (int) Math.round(radians * TAIL_PITCH_ROLL_PACK_SCALE),
                TAIL_10_BIT_MINIMUM,
                TAIL_10_BIT_MAXIMUM);
    }

    private static int signExtend10(int value) {
        return (value << 22) >> 22;
    }

    private static int signExtend12(int value) {
        return (value << 20) >> 20;
    }
}
