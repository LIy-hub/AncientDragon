package com.liy.ancientdragon.animation.pose;

import com.liy.ancientdragon.animation.pose.DragonPoseState.Curve;

/** Server-owned temporal expansion for the specified neck delays and elastic tail response. */
public final class DragonPoseDynamics {
    private static final double[] NECK_RESPONSES = {0.069D, 0.087D, 0.112D, 0.154D, 0.268D};
    private static final double[] TAIL_FREQUENCIES_HERTZ = {2.80D, 2.45D, 2.15D, 1.85D, 1.58D, 1.34D, 1.12D, 0.92D};
    private static final double[] TAIL_DAMPING = {1.00D, 0.96D, 0.92D, 0.87D, 0.82D, 0.76D, 0.69D, 0.62D};
    private static final int TAIL_SPRING_SUBSTEPS = 4;
    private static final double TAIL_SUBSTEP_SECONDS = 1.0D / (20.0D * TAIL_SPRING_SUBSTEPS);
    private static final double MAXIMUM_TAIL_PITCH = Math.toRadians(35.0D);
    private static final double MAXIMUM_TAIL_YAW = Math.toRadians(150.0D);
    private static final double MAXIMUM_TAIL_ROLL = Math.toRadians(18.0D);
    private static final double MAXIMUM_TAIL_PITCH_SPEED = Math.toRadians(180.0D);
    private static final double MAXIMUM_TAIL_YAW_SPEED = Math.toRadians(360.0D);
    private static final double MAXIMUM_TAIL_ROLL_SPEED = Math.toRadians(120.0D);

    private final double[] neckYaw = new double[DragonPoseMath.neckBoneCount()];
    private final double[] neckPitch = new double[DragonPoseMath.neckBoneCount()];
    private final Curve[] tail = new Curve[DragonPoseMath.tailBoneCount()];
    private final double[] tailPitchVelocity = new double[DragonPoseMath.tailBoneCount()];
    private final double[] tailYawVelocity = new double[DragonPoseMath.tailBoneCount()];
    private final double[] tailRollVelocity = new double[DragonPoseMath.tailBoneCount()];
    private boolean initialized;

    public DragonPoseDynamics() {
        java.util.Arrays.fill(tail, Curve.ZERO);
    }

    public void advance(DragonPoseState target) {
        if (!initialized) {
            reset(DragonStagedPose.matching(target));
            return;
        }
        for (int index = 0; index < neckYaw.length; index++) {
            neckYaw[index] += (target.attentionYawRadians() - neckYaw[index]) * NECK_RESPONSES[index];
            neckPitch[index] += (target.attentionPitchRadians() - neckPitch[index]) * NECK_RESPONSES[index];
        }
        Curve boundedTarget = clampTail(target.tail());
        for (int index = 0; index < tail.length; index++) {
            double frequency = TAIL_FREQUENCIES_HERTZ[index];
            double damping = TAIL_DAMPING[index];
            AxisState pitch = springAxis(
                    tail[index].pitchRadians(), tailPitchVelocity[index], boundedTarget.pitchRadians(),
                    frequency, damping, MAXIMUM_TAIL_PITCH, MAXIMUM_TAIL_PITCH_SPEED);
            AxisState yaw = springAxis(
                    tail[index].yawRadians(), tailYawVelocity[index], boundedTarget.yawRadians(),
                    frequency, damping, MAXIMUM_TAIL_YAW, MAXIMUM_TAIL_YAW_SPEED);
            AxisState roll = springAxis(
                    tail[index].rollRadians(), tailRollVelocity[index], boundedTarget.rollRadians(),
                    frequency, damping, MAXIMUM_TAIL_ROLL, MAXIMUM_TAIL_ROLL_SPEED);
            tail[index] = new Curve(pitch.position(), yaw.position(), roll.position());
            tailPitchVelocity[index] = pitch.velocity();
            tailYawVelocity[index] = yaw.velocity();
            tailRollVelocity[index] = roll.velocity();
        }
    }

    public void reset(DragonStagedPose restored) {
        for (int index = 0; index < neckYaw.length; index++) {
            neckYaw[index] = restored.neckYaw(index);
            neckPitch[index] = restored.neckPitch(index);
        }
        for (int index = 0; index < tail.length; index++) {
            tail[index] = clampTail(restored.tail(index));
            tailPitchVelocity[index] = 0.0D;
            tailYawVelocity[index] = 0.0D;
            tailRollVelocity[index] = 0.0D;
        }
        initialized = true;
    }

    public DragonStagedPose snapshot() {
        return new DragonStagedPose(neckYaw, neckPitch, tail);
    }

    private static AxisState springAxis(
            double position,
            double velocity,
            double target,
            double frequencyHertz,
            double damping,
            double maximumAngle,
            double maximumSpeed) {
        double angularFrequency = Math.PI * 2.0D * frequencyHertz;
        for (int substep = 0; substep < TAIL_SPRING_SUBSTEPS; substep++) {
            double acceleration = (target - position) * angularFrequency * angularFrequency
                    - velocity * (2.0D * damping * angularFrequency);
            velocity = Math.clamp(
                    velocity + acceleration * TAIL_SUBSTEP_SECONDS,
                    -maximumSpeed,
                    maximumSpeed);
            position += velocity * TAIL_SUBSTEP_SECONDS;
            if (position < -maximumAngle) {
                position = -maximumAngle;
                velocity = Math.max(0.0D, velocity);
            } else if (position > maximumAngle) {
                position = maximumAngle;
                velocity = Math.min(0.0D, velocity);
            }
        }
        return new AxisState(position, velocity);
    }

    private static Curve clampTail(Curve curve) {
        return new Curve(
                Math.clamp(curve.pitchRadians(), -MAXIMUM_TAIL_PITCH, MAXIMUM_TAIL_PITCH),
                Math.clamp(curve.yawRadians(), -MAXIMUM_TAIL_YAW, MAXIMUM_TAIL_YAW),
                Math.clamp(curve.rollRadians(), -MAXIMUM_TAIL_ROLL, MAXIMUM_TAIL_ROLL));
    }

    private record AxisState(double position, double velocity) {
    }
}
