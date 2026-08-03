package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import net.minecraft.world.phys.Vec3;

/** Acceleration-limited, quaternion-based authoritative flight controller. */
final class DragonFlightController {
    static final int MANEUVER_COOLDOWN_TICKS = 240;
    static final double MINIMUM_ROLL_SPEED = 0.30D;

    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final double SPRING_FREQUENCY_HERTZ = 1.6D;
    private static final double SPRING_DAMPING = 0.95D;
    private static final double MAXIMUM_ACCELERATION_PER_TICK = 0.045D;
    private static final double MAXIMUM_DECELERATION_PER_TICK = 0.065D;
    private static final double HEADING_RESPONSE = 1.35D;
    private static final double BANK_FROM_YAW_RATE = 0.55D;
    private static final double MAXIMUM_BANK_STEP = Math.toRadians(1.0D);

    private static final double ATTENTION_ACTIVATION_YAW = Math.toRadians(75.0D);
    private static final double ATTENTION_ASSIST_GAIN = 0.20D;
    private static final double MAXIMUM_ATTENTION_HEADING_OFFSET = Math.toRadians(12.0D);
    private static final double MAXIMUM_ATTENTION_STEP = Math.toRadians(0.20D);
    private static final double ATTENTION_RELEASE_STEP = Math.toRadians(0.40D);
    private static final double MAXIMUM_FACING_STEP = Math.toRadians(0.50D);
    private static final double FACING_RELEASE_STEP = Math.toRadians(0.75D);
    private static final int MANEUVER_RECOVERY_TICKS = 20;

    private DragonQuaternion orientation = DragonQuaternion.IDENTITY;
    private DragonQuaternion maneuverStartOrientation = DragonQuaternion.IDENTITY;
    private Vec3 angularVelocity = Vec3.ZERO;
    private Vec3 previousMovement = Vec3.ZERO;
    private Vec3 retainedHorizontalDirection = new Vec3(0.0D, 0.0D, 1.0D);
    private double attentionHeadingOffsetRadians;
    private double facingHeadingOffsetRadians;
    private double bankRadians;
    private double speed;
    private Curve torsoCurve = Curve.ZERO;
    private Curve tailCurve = Curve.ZERO;
    private Maneuver maneuver = Maneuver.NONE;
    private int maneuverTick;
    private int maneuverCooldown;
    private int maneuverRecoveryTicks;

    FlightStep tick(Vec3 position, FlightIntent intent) {
        if (!finite(position) || intent == null) {
            throw new IllegalArgumentException("Flight input must be finite and complete");
        }
        if (maneuverCooldown > 0) {
            maneuverCooldown--;
        }
        if (maneuverRecoveryTicks > 0) {
            maneuverRecoveryTicks--;
        }

        Vec3 offset = intent.destination().subtract(position);
        Vec3 desiredDirection = offset.lengthSqr() > 1.0e-8D
                ? limitPitch(offset.normalize(), intent.envelope().maximumPitchRadians)
                : orientation.forward();

        boolean suppressGuidanceOffsets = maneuver != Maneuver.NONE || maneuverRecoveryTicks > 0;
        double attentionTarget = suppressGuidanceOffsets
                ? 0.0D
                : attentionHeadingTarget(intent.attentionAssistYawRadians());
        attentionHeadingOffsetRadians = approach(
                attentionHeadingOffsetRadians,
                attentionTarget,
                attentionTarget == 0.0D ? ATTENTION_RELEASE_STEP : MAXIMUM_ATTENTION_STEP);

        double facingTarget = suppressGuidanceOffsets ? 0.0D : facingHeadingTarget(position, desiredDirection, intent);
        facingHeadingOffsetRadians = approach(
                facingHeadingOffsetRadians,
                facingTarget,
                facingTarget == 0.0D ? FACING_RELEASE_STEP : MAXIMUM_FACING_STEP);

        double totalHeadingOffset = Math.clamp(
                attentionHeadingOffsetRadians + facingHeadingOffsetRadians,
                -intent.envelope().maximumGuidanceOffsetRadians,
                intent.envelope().maximumGuidanceOffsetRadians);
        if (Math.abs(totalHeadingOffset) > 1.0e-8D) {
            desiredDirection = DragonQuaternion.axisAngle(
                            new Vec3(0.0D, 1.0D, 0.0D), totalHeadingOffset)
                    .rotate(desiredDirection)
                    .normalize();
        }

        DragonQuaternion previousOrientation = orientation;
        Vec3 previousForward = previousOrientation.forward();
        double commandedYawRate = 0.0D;
        if (maneuver == Maneuver.NONE) {
            double headingError = signedHorizontalAngle(previousForward, desiredDirection);
            commandedYawRate = Math.clamp(
                    headingError * HEADING_RESPONSE,
                    -intent.envelope().maximumAngularSpeedRadians,
                    intent.envelope().maximumAngularSpeedRadians);
            double bankTarget = Math.clamp(
                    -commandedYawRate * BANK_FROM_YAW_RATE,
                    -intent.envelope().maximumBankRadians,
                    intent.envelope().maximumBankRadians);
            bankRadians = approach(bankRadians, bankTarget, MAXIMUM_BANK_STEP);

            DragonQuaternion target = DragonQuaternion.lookRotation(desiredDirection, new Vec3(0.0D, 1.0D, 0.0D))
                    .multiply(DragonQuaternion.axisAngle(new Vec3(0.0D, 0.0D, 1.0D), bankRadians));
            integrateOrientationSpring(
                    target,
                    intent.envelope().maximumAngularSpeedRadians,
                    intent.envelope().maximumAngularAccelerationRadians);
            DragonQuaternion bankLimited = clampBank(orientation, intent.envelope().maximumBankRadians);
            orientation = limitAngularStep(
                    previousOrientation,
                    bankLimited,
                    intent.envelope().maximumAngularSpeedRadians * TICK_SECONDS);
        } else {
            bankRadians = approach(bankRadians, 0.0D, MAXIMUM_BANK_STEP);
            advanceManeuver();
        }

        double distance = Math.sqrt(offset.lengthSqr());
        double brakingLimitedSpeed = Math.sqrt(2.0D * MAXIMUM_DECELERATION_PER_TICK * distance);
        double targetSpeed = Math.min(intent.maximumSpeed(), brakingLimitedSpeed);
        double rate = targetSpeed >= speed ? MAXIMUM_ACCELERATION_PER_TICK : MAXIMUM_DECELERATION_PER_TICK;
        speed = approach(speed, targetSpeed, rate);
        Vec3 movement = orientation.forward().scale(speed);

        Vec3 localAcceleration = orientation.conjugate().rotate(movement.subtract(previousMovement));
        previousMovement = movement;
        double turnInfluence = maneuver == Maneuver.NONE
                ? commandedYawRate / intent.envelope().maximumAngularSpeedRadians
                : 0.0D;
        double pathPitchBend = desiredDirection.y < 0.0D
                ? -desiredDirection.y * Math.toRadians(56.0D)
                : -desiredDirection.y * Math.toRadians(28.0D);
        Curve torsoTarget = new Curve(
                Math.clamp(
                        pathPitchBend
                                - localAcceleration.y * Math.toRadians(28.0D)
                                + localAcceleration.z * Math.toRadians(9.0D),
                        Math.toRadians(-28.0D),
                        Math.toRadians(56.0D)),
                Math.toRadians(-64.0D) * turnInfluence,
                bankRadians * 0.12D + Math.clamp(
                        -localAcceleration.x * Math.toRadians(18.0D),
                        Math.toRadians(-3.0D),
                        Math.toRadians(3.0D)));
        Curve tailTarget = new Curve(
                Math.clamp(
                        -torsoTarget.pitchRadians() * 1.15D,
                        Math.toRadians(-30.0D),
                        Math.toRadians(30.0D)),
                Math.toRadians(140.0D) * turnInfluence,
                Math.clamp(
                        -bankRadians * 0.18D,
                        Math.toRadians(-10.0D),
                        Math.toRadians(10.0D)));
        torsoCurve = torsoCurve.lerp(torsoTarget, 0.24D);
        tailCurve = tailTarget;
        return new FlightStep(movement, orientation, torsoCurve, tailCurve, maneuver, maneuverTick, maneuverCooldown);
    }

    FlightStep stop() {
        speed = approach(speed, 0.0D, MAXIMUM_DECELERATION_PER_TICK);
        previousMovement = Vec3.ZERO;
        attentionHeadingOffsetRadians = approach(
                attentionHeadingOffsetRadians, 0.0D, ATTENTION_RELEASE_STEP);
        facingHeadingOffsetRadians = approach(facingHeadingOffsetRadians, 0.0D, FACING_RELEASE_STEP);
        bankRadians = approach(bankRadians, 0.0D, MAXIMUM_BANK_STEP);
        torsoCurve = torsoCurve.lerp(Curve.ZERO, 0.22D);
        tailCurve = tailCurve.lerp(Curve.ZERO, 0.10D);
        if (maneuver != Maneuver.NONE) {
            cancelManeuver();
        }
        return new FlightStep(Vec3.ZERO, orientation, torsoCurve, tailCurve, maneuver, maneuverTick, maneuverCooldown);
    }

    boolean startManeuver(Maneuver requested) {
        if (requested == null || requested == Maneuver.NONE || maneuver != Maneuver.NONE || maneuverCooldown > 0) {
            return false;
        }
        maneuver = requested;
        maneuverTick = 0;
        maneuverStartOrientation = orientation;
        maneuverRecoveryTicks = 0;
        angularVelocity = Vec3.ZERO;
        attentionHeadingOffsetRadians = 0.0D;
        facingHeadingOffsetRadians = 0.0D;
        return true;
    }

    void cancelManeuver() {
        if (maneuver != Maneuver.NONE) {
            maneuverCooldown = MANEUVER_COOLDOWN_TICKS;
            maneuverRecoveryTicks = MANEUVER_RECOVERY_TICKS;
        }
        maneuver = Maneuver.NONE;
        maneuverTick = 0;
        angularVelocity = Vec3.ZERO;
    }

    void reset(DragonQuaternion newOrientation, Vec3 velocity) {
        orientation = newOrientation == null ? DragonQuaternion.IDENTITY : newOrientation;
        maneuverStartOrientation = orientation;
        speed = velocity == null ? 0.0D : Math.max(0.0D, velocity.dot(orientation.forward()));
        angularVelocity = Vec3.ZERO;
        previousMovement = velocity == null ? Vec3.ZERO : velocity;
        Vec3 initialHorizontal = new Vec3(orientation.forward().x, 0.0D, orientation.forward().z);
        retainedHorizontalDirection = initialHorizontal.lengthSqr() > 1.0e-10D
                ? initialHorizontal.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
        attentionHeadingOffsetRadians = 0.0D;
        facingHeadingOffsetRadians = 0.0D;
        bankRadians = bankRadians(orientation);
        torsoCurve = Curve.ZERO;
        tailCurve = Curve.ZERO;
        maneuver = Maneuver.NONE;
        maneuverTick = 0;
        maneuverRecoveryTicks = 0;
    }

    void restore(DragonQuaternion savedOrientation, Vec3 savedVelocity, Curve savedTorso, Curve savedTail, int cooldown) {
        reset(savedOrientation, savedVelocity);
        torsoCurve = savedTorso == null ? Curve.ZERO : savedTorso;
        tailCurve = savedTail == null ? Curve.ZERO : savedTail;
        maneuverCooldown = Math.max(0, cooldown);
    }

    DragonQuaternion orientation() {
        return orientation;
    }

    Vec3 velocity() {
        return orientation.forward().scale(speed);
    }

    double speed() {
        return speed;
    }

    double attentionHeadingOffsetRadians() {
        return attentionHeadingOffsetRadians;
    }

    double facingHeadingOffsetRadians() {
        return facingHeadingOffsetRadians;
    }

    Curve torsoCurve() {
        return torsoCurve;
    }

    Curve tailCurve() {
        return tailCurve;
    }

    Maneuver maneuver() {
        return maneuver;
    }

    int maneuverTick() {
        return maneuverTick;
    }

    int maneuverCooldown() {
        return maneuverCooldown;
    }

    private static double attentionHeadingTarget(double attentionAssistYawRadians) {
        if (Math.abs(attentionAssistYawRadians) <= ATTENTION_ACTIVATION_YAW) {
            return 0.0D;
        }
        double excess = Math.abs(attentionAssistYawRadians) - ATTENTION_ACTIVATION_YAW;
        return Math.copySign(
                Math.min(MAXIMUM_ATTENTION_HEADING_OFFSET, excess * ATTENTION_ASSIST_GAIN),
                attentionAssistYawRadians);
    }

    private static double facingHeadingTarget(Vec3 position, Vec3 pathDirection, FlightIntent intent) {
        if (intent.facingTarget() == null || intent.maximumFacingBiasRadians() <= 0.0D) {
            return 0.0D;
        }
        Vec3 facingOffset = intent.facingTarget().subtract(position);
        if (facingOffset.lengthSqr() <= 1.0e-8D) {
            return 0.0D;
        }
        double error = signedHorizontalAngle(pathDirection, facingOffset.normalize());
        return Math.clamp(error, -intent.maximumFacingBiasRadians(), intent.maximumFacingBiasRadians());
    }

    private void integrateOrientationSpring(
            DragonQuaternion target,
            double maximumAngularSpeed,
            double maximumAngularAcceleration) {
        DragonQuaternion error = target.multiply(orientation.conjugate());
        double sign = error.w() < 0.0D ? -1.0D : 1.0D;
        double halfAngle = Math.acos(Math.clamp(error.w() * sign, -1.0D, 1.0D));
        double sine = Math.sin(halfAngle);
        Vec3 rotationError = sine < 1.0e-8D
                ? Vec3.ZERO
                : new Vec3(error.x() * sign, error.y() * sign, error.z() * sign)
                        .scale((halfAngle * 2.0D) / sine);
        double frequency = Math.PI * 2.0D * SPRING_FREQUENCY_HERTZ;
        Vec3 acceleration = rotationError.scale(frequency * frequency)
                .subtract(angularVelocity.scale(2.0D * SPRING_DAMPING * frequency));
        acceleration = limitLength(acceleration, maximumAngularAcceleration);
        angularVelocity = limitLength(
                angularVelocity.add(acceleration.scale(TICK_SECONDS)), maximumAngularSpeed);
        double angularSpeed = angularVelocity.length();
        double deltaAngle = angularSpeed * TICK_SECONDS;
        if (deltaAngle > 1.0e-9D) {
            orientation = DragonQuaternion.axisAngle(
                            angularVelocity.scale(1.0D / angularSpeed), deltaAngle)
                    .multiply(orientation);
        }
    }

    private void advanceManeuver() {
        Maneuver active = maneuver;
        double progress = Math.clamp((maneuverTick + 1.0D) / active.durationTicks(), 0.0D, 1.0D);
        switch (active) {
            case ROLL_LEFT, ROLL_RIGHT -> {
                double direction = active == Maneuver.ROLL_LEFT ? 1.0D : -1.0D;
                orientation = maneuverStartOrientation.multiply(DragonQuaternion.axisAngle(
                        new Vec3(0.0D, 0.0D, 1.0D), Math.PI * 2.0D * progress * direction));
            }
            case LOOP -> orientation = maneuverStartOrientation.multiply(DragonQuaternion.axisAngle(
                    new Vec3(1.0D, 0.0D, 0.0D), -Math.PI * 2.0D * progress));
            case EVADE_LEFT, EVADE_RIGHT -> {
                double direction = active == Maneuver.EVADE_LEFT ? 1.0D : -1.0D;
                double pulse = Math.sin(Math.PI * progress);
                pulse *= pulse;
                double yaw = Math.toRadians(12.0D) * pulse * direction;
                double roll = Math.toRadians(45.0D) * pulse * direction;
                orientation = DragonQuaternion.axisAngle(new Vec3(0.0D, 1.0D, 0.0D), yaw)
                        .multiply(maneuverStartOrientation)
                        .multiply(DragonQuaternion.axisAngle(new Vec3(0.0D, 0.0D, 1.0D), roll));
            }
            case PULL_UP -> {
                double pulse = Math.sin(Math.PI * progress);
                pulse *= pulse;
                orientation = maneuverStartOrientation.multiply(DragonQuaternion.axisAngle(
                        new Vec3(1.0D, 0.0D, 0.0D), -Math.toRadians(70.0D) * pulse));
            }
            case NONE -> throw new IllegalStateException("Cannot advance an empty maneuver");
        }
        maneuverTick++;
        if (maneuverTick >= active.durationTicks()) {
            orientation = maneuverStartOrientation;
            maneuver = Maneuver.NONE;
            maneuverTick = 0;
            maneuverCooldown = MANEUVER_COOLDOWN_TICKS;
            maneuverRecoveryTicks = MANEUVER_RECOVERY_TICKS;
            angularVelocity = Vec3.ZERO;
        }
    }

    private Vec3 limitPitch(Vec3 direction, double maximumPitchRadians) {
        double pitch = Math.asin(Math.clamp(direction.y, -1.0D, 1.0D));
        double limitedPitch = Math.clamp(pitch, -maximumPitchRadians, maximumPitchRadians);
        double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontalLength > 1.0e-6D) {
            retainedHorizontalDirection = new Vec3(
                    direction.x / horizontalLength, 0.0D, direction.z / horizontalLength);
        }
        return retainedHorizontalDirection.scale(Math.cos(limitedPitch))
                .add(0.0D, Math.sin(limitedPitch), 0.0D)
                .normalize();
    }

    private static double signedHorizontalAngle(Vec3 from, Vec3 to) {
        Vec3 fromHorizontal = new Vec3(from.x, 0.0D, from.z);
        Vec3 toHorizontal = new Vec3(to.x, 0.0D, to.z);
        if (fromHorizontal.lengthSqr() < 1.0e-10D || toHorizontal.lengthSqr() < 1.0e-10D) {
            return 0.0D;
        }
        fromHorizontal = fromHorizontal.normalize();
        toHorizontal = toHorizontal.normalize();
        double crossY = fromHorizontal.z * toHorizontal.x - fromHorizontal.x * toHorizontal.z;
        return Math.atan2(crossY, fromHorizontal.dot(toHorizontal));
    }

    private static Vec3 limitLength(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum ? vector.scale(maximum / length) : vector;
    }

    private static DragonQuaternion clampBank(DragonQuaternion source, double maximumBank) {
        Vec3 forward = source.forward();
        Vec3 referenceUp = new Vec3(0.0D, 1.0D, 0.0D).subtract(forward.scale(forward.y));
        if (referenceUp.lengthSqr() < 1.0e-8D) {
            return source;
        }
        referenceUp = referenceUp.normalize();
        Vec3 actualUp = source.up().subtract(forward.scale(source.up().dot(forward)));
        if (actualUp.lengthSqr() < 1.0e-8D) {
            return source;
        }
        actualUp = actualUp.normalize();
        double bank = Math.atan2(forward.dot(referenceUp.cross(actualUp)), referenceUp.dot(actualUp));
        double clamped = Math.clamp(bank, -maximumBank, maximumBank);
        return DragonQuaternion.lookRotation(forward, new Vec3(0.0D, 1.0D, 0.0D))
                .multiply(DragonQuaternion.axisAngle(new Vec3(0.0D, 0.0D, 1.0D), clamped));
    }

    private static double bankRadians(DragonQuaternion source) {
        Vec3 forward = source.forward();
        Vec3 referenceUp = new Vec3(0.0D, 1.0D, 0.0D).subtract(forward.scale(forward.y));
        Vec3 actualUp = source.up().subtract(forward.scale(source.up().dot(forward)));
        if (referenceUp.lengthSqr() < 1.0e-8D || actualUp.lengthSqr() < 1.0e-8D) {
            return 0.0D;
        }
        referenceUp = referenceUp.normalize();
        actualUp = actualUp.normalize();
        return Math.atan2(forward.dot(referenceUp.cross(actualUp)), referenceUp.dot(actualUp));
    }

    private static DragonQuaternion limitAngularStep(
            DragonQuaternion previous,
            DragonQuaternion candidate,
            double maximumStep) {
        // The spring already limits the quaternion step. This second guard catches any additional
        // correction introduced by the bank projection while preserving its shortest-path result.
        double distance = previous.angularDistance(candidate);
        return distance > maximumStep ? previous.slerp(candidate, maximumStep / distance) : candidate;
    }

    private static double approach(double current, double target, double maximumDelta) {
        if (current < target) {
            return Math.min(current + maximumDelta, target);
        }
        return Math.max(current - maximumDelta, target);
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    enum FlightEnvelope {
        CRUISE(55.0D, 35.0D, 60.0D, 120.0D, 20.0D),
        TAKEOFF(80.0D, 20.0D, 75.0D, 150.0D, 12.0D),
        ATTACK(65.0D, 40.0D, 75.0D, 150.0D, 20.0D),
        DIVE(80.0D, 50.0D, 90.0D, 180.0D, 20.0D);

        private final double maximumPitchRadians;
        private final double maximumBankRadians;
        private final double maximumAngularSpeedRadians;
        private final double maximumAngularAccelerationRadians;
        private final double maximumGuidanceOffsetRadians;

        FlightEnvelope(
                double maximumPitchDegrees,
                double maximumBankDegrees,
                double maximumAngularSpeedDegrees,
                double maximumAngularAccelerationDegrees,
                double maximumGuidanceOffsetDegrees) {
            maximumPitchRadians = Math.toRadians(maximumPitchDegrees);
            maximumBankRadians = Math.toRadians(maximumBankDegrees);
            maximumAngularSpeedRadians = Math.toRadians(maximumAngularSpeedDegrees);
            maximumAngularAccelerationRadians = Math.toRadians(maximumAngularAccelerationDegrees);
            maximumGuidanceOffsetRadians = Math.toRadians(maximumGuidanceOffsetDegrees);
        }
    }

    record FlightIntent(
            Vec3 destination,
            double maximumSpeed,
            double attentionAssistYawRadians,
            Vec3 facingTarget,
            double maximumFacingBiasRadians,
            FlightEnvelope envelope) {
        FlightIntent {
            if (!finite(destination)
                    || !Double.isFinite(maximumSpeed)
                    || maximumSpeed < 0.0D
                    || !Double.isFinite(attentionAssistYawRadians)
                    || (facingTarget != null && !finite(facingTarget))
                    || !Double.isFinite(maximumFacingBiasRadians)
                    || maximumFacingBiasRadians < 0.0D
                    || envelope == null) {
                throw new IllegalArgumentException("Flight intent must be finite and complete");
            }
            if (facingTarget == null && maximumFacingBiasRadians != 0.0D) {
                throw new IllegalArgumentException("Facing bias requires a facing target");
            }
        }

        static FlightIntent route(
                Vec3 destination,
                double maximumSpeed,
                double attentionAssistYawRadians,
                FlightEnvelope envelope) {
            return new FlightIntent(destination, maximumSpeed, attentionAssistYawRadians, null, 0.0D, envelope);
        }

        static FlightIntent facing(
                Vec3 destination,
                double maximumSpeed,
                Vec3 facingTarget,
                double maximumFacingBiasRadians,
                FlightEnvelope envelope) {
            return new FlightIntent(destination, maximumSpeed, 0.0D, facingTarget, maximumFacingBiasRadians, envelope);
        }
    }

    record FlightStep(
            Vec3 movement,
            DragonQuaternion orientation,
            Curve torsoCurve,
            Curve tailCurve,
            Maneuver maneuver,
            int maneuverTick,
            int maneuverCooldown) {
    }
}
