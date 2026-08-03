package com.liy.ancientdragon.animation.pose;

/** Compact server-owned state synchronized through vanilla entity data. */
public record DragonPoseState(
        DragonQuaternion orientation,
        Curve torso,
        Curve tail,
        double attentionYawRadians,
        double attentionPitchRadians,
        AttentionMode attentionMode,
        int attentionTargetEntityId,
        Maneuver maneuver) {
    public static final DragonPoseState IDENTITY = new DragonPoseState(
            DragonQuaternion.IDENTITY,
            Curve.ZERO,
            Curve.ZERO,
            0.0D,
            0.0D,
            AttentionMode.SCAN,
            -1,
            Maneuver.NONE);

    public DragonPoseState {
        if (orientation == null || torso == null || tail == null || attentionMode == null || maneuver == null) {
            throw new NullPointerException("Dragon pose fields must not be null");
        }
        if (!Double.isFinite(attentionYawRadians) || !Double.isFinite(attentionPitchRadians)) {
            throw new IllegalArgumentException("Attention angles must be finite");
        }
        attentionYawRadians = Math.clamp(attentionYawRadians, Math.toRadians(-135.0D), Math.toRadians(135.0D));
        attentionPitchRadians = Math.clamp(attentionPitchRadians, Math.toRadians(-110.0D), Math.toRadians(80.0D));
    }

    public record Curve(double pitchRadians, double yawRadians, double rollRadians) {
        public static final Curve ZERO = new Curve(0.0D, 0.0D, 0.0D);

        public Curve {
            if (!Double.isFinite(pitchRadians) || !Double.isFinite(yawRadians) || !Double.isFinite(rollRadians)) {
                throw new IllegalArgumentException("Curve angles must be finite");
            }
        }

        public Curve lerp(Curve target, double amount) {
            double clamped = Math.clamp(amount, 0.0D, 1.0D);
            return new Curve(
                    pitchRadians + (target.pitchRadians - pitchRadians) * clamped,
                    yawRadians + (target.yawRadians - yawRadians) * clamped,
                    rollRadians + (target.rollRadians - rollRadians) * clamped);
        }
    }

    public enum AttentionMode {
        ATTACK_FOCUS,
        RECENT_ATTACKER,
        THREAT,
        OBSERVER,
        SCAN,
        OVERRIDE_NEAREST,
        OVERRIDE_SCAN,
        CLEARED;

        public static AttentionMode byNetworkId(int value) {
            AttentionMode[] values = values();
            return value >= 0 && value < values.length ? values[value] : SCAN;
        }
    }

    public enum Maneuver {
        NONE(0),
        ROLL_LEFT(60),
        ROLL_RIGHT(60),
        LOOP(72),
        EVADE_LEFT(28),
        EVADE_RIGHT(28),
        PULL_UP(36);

        private final int durationTicks;

        Maneuver(int durationTicks) {
            this.durationTicks = durationTicks;
        }

        public int durationTicks() {
            return durationTicks;
        }

        public boolean isRolling() {
            return this == ROLL_LEFT || this == ROLL_RIGHT || this == LOOP;
        }

        public boolean isFullRotation() {
            return this == ROLL_LEFT || this == ROLL_RIGHT || this == LOOP;
        }

        public boolean isLateral() {
            return this == ROLL_LEFT || this == ROLL_RIGHT || this == EVADE_LEFT || this == EVADE_RIGHT;
        }

        public boolean suppressesAttentionBodyAssist() {
            return this != NONE;
        }

        public static Maneuver byNetworkId(int value) {
            Maneuver[] values = values();
            return value >= 0 && value < values.length ? values[value] : NONE;
        }
    }
}
