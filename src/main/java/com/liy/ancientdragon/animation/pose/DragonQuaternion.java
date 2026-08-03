package com.liy.ancientdragon.animation.pose;

import net.minecraft.world.phys.Vec3;

/**
 * Small immutable double-precision quaternion used by the authoritative dragon pose.
 *
 * <p>The canonical model basis is right handed: +X right, +Y up and +Z forward. A quaternion
 * maps vectors in that model basis into Minecraft world space.</p>
 */
public record DragonQuaternion(double x, double y, double z, double w) {
    private static final double MINIMUM_NORM_SQUARED = 1.0e-20D;
    public static final DragonQuaternion IDENTITY = new DragonQuaternion(0.0D, 0.0D, 0.0D, 1.0D);

    public DragonQuaternion {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)) {
            throw new IllegalArgumentException("Quaternion components must be finite");
        }
        double normSquared = x * x + y * y + z * z + w * w;
        if (normSquared < MINIMUM_NORM_SQUARED) {
            throw new IllegalArgumentException("Quaternion must have a non-zero norm");
        }
        double inverseNorm = 1.0D / Math.sqrt(normSquared);
        x *= inverseNorm;
        y *= inverseNorm;
        z *= inverseNorm;
        w *= inverseNorm;
    }

    public static DragonQuaternion axisAngle(Vec3 axis, double radians) {
        if (!Double.isFinite(radians) || axis == null || axis.lengthSqr() < MINIMUM_NORM_SQUARED) {
            throw new IllegalArgumentException("Axis-angle values must be finite and non-zero");
        }
        Vec3 normalized = axis.normalize();
        double halfAngle = radians * 0.5D;
        double sine = Math.sin(halfAngle);
        return new DragonQuaternion(
                normalized.x * sine,
                normalized.y * sine,
                normalized.z * sine,
                Math.cos(halfAngle));
    }

    public static DragonQuaternion fromMinecraftYaw(float yawDegrees) {
        if (!Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite");
        }
        return axisAngle(new Vec3(0.0D, 1.0D, 0.0D), Math.toRadians(-yawDegrees));
    }

    /** Builds an orientation whose local +Z follows {@code forward} and whose local +Y is upright. */
    public static DragonQuaternion lookRotation(Vec3 forward, Vec3 preferredUp) {
        if (forward == null || preferredUp == null || forward.lengthSqr() < MINIMUM_NORM_SQUARED) {
            throw new IllegalArgumentException("Look rotation requires a non-zero forward vector");
        }
        Vec3 normalizedForward = forward.normalize();
        Vec3 up = preferredUp.lengthSqr() < MINIMUM_NORM_SQUARED
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : preferredUp.normalize();
        Vec3 right = up.cross(normalizedForward);
        if (right.lengthSqr() < 1.0e-10D) {
            Vec3 fallback = Math.abs(normalizedForward.y) < 0.9D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
            right = fallback.cross(normalizedForward);
        }
        right = right.normalize();
        Vec3 correctedUp = normalizedForward.cross(right).normalize();
        return fromRotationMatrix(
                right.x, correctedUp.x, normalizedForward.x,
                right.y, correctedUp.y, normalizedForward.y,
                right.z, correctedUp.z, normalizedForward.z);
    }

    private static DragonQuaternion fromRotationMatrix(
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22) {
        double trace = m00 + m11 + m22;
        if (trace > 0.0D) {
            double scale = Math.sqrt(trace + 1.0D) * 2.0D;
            return new DragonQuaternion(
                    (m21 - m12) / scale,
                    (m02 - m20) / scale,
                    (m10 - m01) / scale,
                    0.25D * scale);
        }
        if (m00 > m11 && m00 > m22) {
            double scale = Math.sqrt(1.0D + m00 - m11 - m22) * 2.0D;
            return new DragonQuaternion(
                    0.25D * scale,
                    (m01 + m10) / scale,
                    (m02 + m20) / scale,
                    (m21 - m12) / scale);
        }
        if (m11 > m22) {
            double scale = Math.sqrt(1.0D + m11 - m00 - m22) * 2.0D;
            return new DragonQuaternion(
                    (m01 + m10) / scale,
                    0.25D * scale,
                    (m12 + m21) / scale,
                    (m02 - m20) / scale);
        }
        double scale = Math.sqrt(1.0D + m22 - m00 - m11) * 2.0D;
        return new DragonQuaternion(
                (m02 + m20) / scale,
                (m12 + m21) / scale,
                0.25D * scale,
                (m10 - m01) / scale);
    }

    /** Returns {@code this * other}; {@code other} is applied to a vector first. */
    public DragonQuaternion multiply(DragonQuaternion other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return new DragonQuaternion(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public DragonQuaternion conjugate() {
        return new DragonQuaternion(-x, -y, -z, w);
    }

    public Vec3 rotate(Vec3 vector) {
        if (vector == null) {
            throw new NullPointerException("vector");
        }
        Vec3 quaternionVector = new Vec3(x, y, z);
        Vec3 twiceCross = quaternionVector.cross(vector).scale(2.0D);
        return vector.add(twiceCross.scale(w)).add(quaternionVector.cross(twiceCross));
    }

    public Vec3 forward() {
        return rotate(new Vec3(0.0D, 0.0D, 1.0D));
    }

    public Vec3 up() {
        return rotate(new Vec3(0.0D, 1.0D, 0.0D));
    }

    public Vec3 right() {
        return rotate(new Vec3(1.0D, 0.0D, 0.0D));
    }

    public double dot(DragonQuaternion other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    public DragonQuaternion slerp(DragonQuaternion target, double amount) {
        if (target == null || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("Slerp target and amount must be finite");
        }
        double clamped = Math.clamp(amount, 0.0D, 1.0D);
        double targetX = target.x;
        double targetY = target.y;
        double targetZ = target.z;
        double targetW = target.w;
        double cosine = dot(target);
        if (cosine < 0.0D) {
            cosine = -cosine;
            targetX = -targetX;
            targetY = -targetY;
            targetZ = -targetZ;
            targetW = -targetW;
        }
        if (cosine > 0.9995D) {
            return new DragonQuaternion(
                    x + (targetX - x) * clamped,
                    y + (targetY - y) * clamped,
                    z + (targetZ - z) * clamped,
                    w + (targetW - w) * clamped);
        }
        double angle = Math.acos(Math.clamp(cosine, -1.0D, 1.0D));
        double sine = Math.sin(angle);
        double sourceWeight = Math.sin((1.0D - clamped) * angle) / sine;
        double targetWeight = Math.sin(clamped * angle) / sine;
        return new DragonQuaternion(
                x * sourceWeight + targetX * targetWeight,
                y * sourceWeight + targetY * targetWeight,
                z * sourceWeight + targetZ * targetWeight,
                w * sourceWeight + targetW * targetWeight);
    }

    public double angularDistance(DragonQuaternion other) {
        return 2.0D * Math.acos(Math.clamp(Math.abs(dot(other)), -1.0D, 1.0D));
    }

    public float minecraftYawDegrees() {
        Vec3 direction = forward();
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    public float minecraftPitchDegrees() {
        return (float) -Math.toDegrees(Math.asin(Math.clamp(forward().y, -1.0D, 1.0D)));
    }
}
