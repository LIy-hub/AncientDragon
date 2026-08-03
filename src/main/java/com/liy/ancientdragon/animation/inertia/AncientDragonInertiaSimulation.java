package com.liy.ancientdragon.animation.inertia;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic client-presentation simulation driven by observed entity translation and yaw.
 *
 * <p>The simulation advances at Minecraft's fixed 20 Hz tick rate and interpolates its previous
 * and current spring states for render partial ticks. It owns no Minecraft or rendering objects,
 * which keeps the physics math directly testable.</p>
 */
public final class AncientDragonInertiaSimulation {
    private static final double TICK_SECONDS = 1.0 / 20.0;
    private static final int SPRING_SUBSTEPS = 6;
    private static final long MAX_CATCH_UP_TICKS = 4L;
    private static final double TELEPORT_BLOCKS_PER_TICK = 4.0;
    private static final double MAX_HORIZONTAL_SPEED = 1.2;
    private static final double MAX_VERTICAL_SPEED = 0.9;
    private static final double MAX_HORIZONTAL_ACCELERATION = 0.35;
    private static final double MAX_VERTICAL_ACCELERATION = 0.30;
    private static final double MAX_YAW_DEGREES_PER_TICK = 12.0;

    private final Map<String, BoneSpring> springs = new LinkedHashMap<>();

    private boolean initialized;
    private long lastGameTick;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double lastVelocityX;
    private double lastVelocityY;
    private double lastVelocityZ;
    private double lastYawDegrees;
    private String lastAnimationKey;

    public AncientDragonInertiaSimulation() {
        for (AncientDragonInertiaProfile.Bone bone : AncientDragonInertiaProfile.bones()) {
            springs.put(bone.name(), new BoneSpring(bone));
        }
    }

    /** Advances at most once for a game tick, then returns an interpolated immutable rotation map. */
    public Map<String, BoneRotation> sample(KinematicFrame frame, float partialTick) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        if (!Float.isFinite(partialTick) || partialTick < 0.0F) {
            throw new IllegalArgumentException("partialTick must be finite and non-negative");
        }
        if (!initialized) {
            initialize(frame);
        } else if (frame.gameTick() < lastGameTick) {
            initialize(frame);
        } else {
            resetGroupsWhoseMaskChanged(lastAnimationKey, frame.animationKey());
            lastAnimationKey = frame.animationKey();
            if (frame.gameTick() > lastGameTick) {
                advance(frame);
            }
        }

        double interpolation = clamp(partialTick, 0.0, 1.0);
        Map<String, BoneRotation> rotations = new LinkedHashMap<>(springs.size());
        for (BoneSpring spring : springs.values()) {
            rotations.put(spring.profile.name(), spring.sample(interpolation));
        }
        return Map.copyOf(rotations);
    }

    public void reset() {
        initialized = false;
        for (BoneSpring spring : springs.values()) {
            spring.reset();
        }
    }

    private void initialize(KinematicFrame frame) {
        initialized = true;
        lastGameTick = frame.gameTick();
        lastX = frame.x();
        lastY = frame.y();
        lastZ = frame.z();
        lastVelocityX = 0.0;
        lastVelocityY = 0.0;
        lastVelocityZ = 0.0;
        lastYawDegrees = frame.yawDegrees();
        lastAnimationKey = frame.animationKey();
        for (BoneSpring spring : springs.values()) {
            spring.reset();
        }
    }

    private void advance(KinematicFrame frame) {
        long elapsedTicks = frame.gameTick() - lastGameTick;
        double dx = frame.x() - lastX;
        double dy = frame.y() - lastY;
        double dz = frame.z() - lastZ;
        double distancePerTick = Math.sqrt(dx * dx + dy * dy + dz * dz) / elapsedTicks;
        if (elapsedTicks > MAX_CATCH_UP_TICKS || distancePerTick > TELEPORT_BLOCKS_PER_TICK) {
            initialize(frame);
            return;
        }

        double velocityX = clamp(dx / elapsedTicks, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        double velocityY = clamp(dy / elapsedTicks, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        double velocityZ = clamp(dz / elapsedTicks, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
        double accelerationX = clamp(
                (velocityX - lastVelocityX) / elapsedTicks,
                -MAX_HORIZONTAL_ACCELERATION,
                MAX_HORIZONTAL_ACCELERATION);
        double accelerationY = clamp(
                (velocityY - lastVelocityY) / elapsedTicks,
                -MAX_VERTICAL_ACCELERATION,
                MAX_VERTICAL_ACCELERATION);
        double accelerationZ = clamp(
                (velocityZ - lastVelocityZ) / elapsedTicks,
                -MAX_HORIZONTAL_ACCELERATION,
                MAX_HORIZONTAL_ACCELERATION);

        double yawRadians = Math.toRadians(frame.yawDegrees());
        double cosine = Math.cos(yawRadians);
        double sine = Math.sin(yawRadians);
        double localVelocityX = cosine * velocityX + sine * velocityZ;
        double localAccelerationX = cosine * accelerationX + sine * accelerationZ;
        double localAccelerationZ = -sine * accelerationX + cosine * accelerationZ;
        double yawPerTick = clamp(
                wrapDegrees(frame.yawDegrees() - lastYawDegrees) / elapsedTicks,
                -MAX_YAW_DEGREES_PER_TICK,
                MAX_YAW_DEGREES_PER_TICK);

        MotionSignal signal = new MotionSignal(
                Math.toRadians(2.4) * clamp(-localAccelerationZ / MAX_HORIZONTAL_ACCELERATION, -1.0, 1.0)
                        + Math.toRadians(1.8) * clamp(-accelerationY / MAX_VERTICAL_ACCELERATION, -1.0, 1.0)
                        + Math.toRadians(1.2) * clamp(-velocityY / MAX_VERTICAL_SPEED, -1.0, 1.0),
                Math.toRadians(5.4) * clamp(-yawPerTick / 6.0, -1.0, 1.0)
                        + Math.toRadians(2.6) * clamp(-localAccelerationX / MAX_HORIZONTAL_ACCELERATION, -1.0, 1.0)
                        + Math.toRadians(1.4) * clamp(-localVelocityX / MAX_HORIZONTAL_SPEED, -1.0, 1.0),
                Math.toRadians(6.0) * clamp(yawPerTick / 6.0, -1.0, 1.0)
                        + Math.toRadians(2.4) * clamp(localAccelerationX / MAX_HORIZONTAL_ACCELERATION, -1.0, 1.0));

        double animationWeight = animationWeight(frame.animationKey());
        for (long tick = 0; tick < elapsedTicks; tick++) {
            for (BoneSpring spring : springs.values()) {
                double maskWeight = animationWeight * groupWeight(frame.animationKey(), spring.profile.group());
                if (maskWeight == 0.0) {
                    spring.reset();
                } else {
                    spring.advance(signal, maskWeight);
                }
            }
        }

        lastGameTick = frame.gameTick();
        lastX = frame.x();
        lastY = frame.y();
        lastZ = frame.z();
        lastVelocityX = velocityX;
        lastVelocityY = velocityY;
        lastVelocityZ = velocityZ;
        lastYawDegrees = frame.yawDegrees();
    }

    private void resetGroupsWhoseMaskChanged(String previousAnimation, String nextAnimation) {
        if (previousAnimation.equals(nextAnimation)) {
            return;
        }
        double previousAnimationWeight = animationWeight(previousAnimation);
        double nextAnimationWeight = animationWeight(nextAnimation);
        for (BoneSpring spring : springs.values()) {
            double previousWeight = previousAnimationWeight * groupWeight(previousAnimation, spring.profile.group());
            double nextWeight = nextAnimationWeight * groupWeight(nextAnimation, spring.profile.group());
            if (previousWeight != nextWeight) {
                spring.reset();
            }
        }
    }

    private static double animationWeight(String animationKey) {
        return switch (animationKey) {
            case "ancient_dragon:dormant_hold", "ancient_dragon:death_landmark", "ancient_dragon:corpse_static" -> 0.0;
            default -> 1.0;
        };
    }

    private static double groupWeight(String animationKey, AncientDragonInertiaProfile.Group group) {
        return switch (animationKey) {
            case "ancient_dragon:aerial_tail_sweep" -> switch (group) {
                case TAIL -> 0.0;
                case BODY, NECK -> 0.18;
                case PRIMARY_WING, SECONDARY_WING -> 0.32;
                case LEG -> 0.45;
            };
            case "ancient_dragon:dive_strike" -> switch (group) {
                case BODY, NECK -> 0.15;
                case TAIL -> 0.20;
                case PRIMARY_WING, SECONDARY_WING -> 0.25;
                case LEG -> 0.35;
            };
            case "ancient_dragon:aerial_storm_burst", "ancient_dragon:aerial_solar_breath",
                    "ancient_dragon:solar_breath", "ancient_dragon:roar_storm" -> switch (group) {
                case BODY, NECK -> 0.22;
                case TAIL -> 0.25;
                case PRIMARY_WING, SECONDARY_WING -> 0.38;
                case LEG -> 0.45;
            };
            case "ancient_dragon:bite_heavy" -> group == AncientDragonInertiaProfile.Group.NECK ? 0.0 : 0.45;
            default -> 1.0;
        };
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record KinematicFrame(
            long gameTick,
            double x,
            double y,
            double z,
            double yawDegrees,
            String animationKey) {
        public KinematicFrame {
            if (gameTick < 0L
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || !Double.isFinite(yawDegrees)
                    || animationKey == null
                    || animationKey.isBlank()) {
                throw new IllegalArgumentException("Kinematic frame values must be finite and complete");
            }
        }
    }

    public record BoneRotation(double pitchRadians, double yawRadians, double rollRadians) {
        public static final BoneRotation ZERO = new BoneRotation(0.0, 0.0, 0.0);

        public BoneRotation {
            if (!Double.isFinite(pitchRadians)
                    || !Double.isFinite(yawRadians)
                    || !Double.isFinite(rollRadians)) {
                throw new IllegalArgumentException("Bone rotation must be finite");
            }
        }

        public double magnitudeSquared() {
            return pitchRadians * pitchRadians + yawRadians * yawRadians + rollRadians * rollRadians;
        }
    }

    private record MotionSignal(double pitchRadians, double yawRadians, double rollRadians) {
    }

    private static final class BoneSpring {
        private final AncientDragonInertiaProfile.Bone profile;
        private final ScalarSpring pitch = new ScalarSpring();
        private final ScalarSpring yaw = new ScalarSpring();
        private final ScalarSpring roll = new ScalarSpring();

        private BoneSpring(AncientDragonInertiaProfile.Bone profile) {
            this.profile = profile;
        }

        private void advance(MotionSignal signal, double maskWeight) {
            pitch.advance(clamp(
                    signal.pitchRadians * profile.pitchResponse() * maskWeight,
                    -profile.maximumRadians(),
                    profile.maximumRadians()), profile);
            yaw.advance(clamp(
                    signal.yawRadians * profile.yawResponse() * maskWeight,
                    -profile.maximumRadians(),
                    profile.maximumRadians()), profile);
            roll.advance(clamp(
                    signal.rollRadians * profile.rollResponse() * maskWeight,
                    -profile.maximumRadians(),
                    profile.maximumRadians()), profile);
        }

        private BoneRotation sample(double amount) {
            return new BoneRotation(pitch.sample(amount), yaw.sample(amount), roll.sample(amount));
        }

        private void reset() {
            pitch.reset();
            yaw.reset();
            roll.reset();
        }
    }

    private static final class ScalarSpring {
        private double previous;
        private double current;
        private double velocity;

        private void advance(double target, AncientDragonInertiaProfile.Bone profile) {
            previous = current;
            double angularFrequency = Math.PI * 2.0 * profile.frequencyHertz();
            double stepSeconds = TICK_SECONDS / SPRING_SUBSTEPS;
            for (int substep = 0; substep < SPRING_SUBSTEPS; substep++) {
                double acceleration = angularFrequency * angularFrequency * (target - current)
                        - 2.0 * profile.dampingRatio() * angularFrequency * velocity;
                velocity += acceleration * stepSeconds;
                current += velocity * stepSeconds;
                double clamped = clamp(current, -profile.maximumRadians(), profile.maximumRadians());
                if (clamped != current) {
                    current = clamped;
                    velocity = 0.0;
                }
            }
        }

        private double sample(double amount) {
            return previous + (current - previous) * amount;
        }

        private void reset() {
            previous = 0.0;
            current = 0.0;
            velocity = 0.0;
        }
    }
}
