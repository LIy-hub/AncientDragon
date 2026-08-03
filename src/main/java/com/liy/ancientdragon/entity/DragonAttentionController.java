package com.liy.ancientdragon.entity;

import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative attention selector with target holding, hysteresis and last-seen memory. */
final class DragonAttentionController {
    static final int MINIMUM_HOLD_TICKS = 30;
    static final int LAST_VISIBLE_HOLD_TICKS = 30;
    static final double SWITCH_SCORE_MULTIPLIER = 1.25D;
    private static final double MAXIMUM_YAW = Math.toRadians(135.0D);
    private static final double MINIMUM_PITCH = Math.toRadians(-110.0D);
    private static final double MAXIMUM_PITCH = Math.toRadians(80.0D);
    private static final double BODY_ASSIST_ACTIVATION_YAW = Math.toRadians(75.0D);
    private static final double BODY_ASSIST_RELEASE_YAW = Math.toRadians(55.0D);
    private static final int BODY_ASSIST_ACTIVATION_TICKS = 12;
    private static final int BODY_ASSIST_RELEASE_TICKS = 20;

    private int targetEntityId = -1;
    private AttentionMode mode = AttentionMode.SCAN;
    private double score;
    private Vec3 lastVisiblePosition = Vec3.ZERO;
    private int heldTicks;
    private int invisibleTicks;
    private int suppressedEntityId = -1;
    private int excessiveYawTicks;
    private int comfortableYawTicks;
    private int bodyAssistTargetEntityId = -1;
    private boolean bodyTurnAssistActive;
    private double yaw;
    private double pitch;

    AttentionStep tick(
            long gameTick,
            long deterministicSeed,
            Vec3 eyePosition,
            DragonQuaternion bodyOrientation,
            List<Candidate> candidates,
            AttentionOverride override) {
        int previousTargetEntityId = targetEntityId;
        Candidate current = candidates.stream()
                .filter(candidate -> candidate.entityId() == targetEntityId)
                .max(Comparator.comparing(Candidate::visible)
                        .thenComparingDouble(Candidate::score))
                .orElse(null);
        Candidate selected = selectCandidate(candidates, override);
        updateSelection(selected, current);
        if (targetEntityId != previousTargetEntityId) {
            excessiveYawTicks = 0;
            comfortableYawTicks = 0;
            bodyTurnAssistActive = false;
            bodyAssistTargetEntityId = targetEntityId;
        }

        Vec3 lookPosition;
        if (mode == AttentionMode.CLEARED) {
            lookPosition = eyePosition.add(bodyOrientation.forward().scale(24.0D));
        } else if (targetEntityId >= 0 || selected != null) {
            lookPosition = lastVisiblePosition;
        } else {
            double seedPhase = (deterministicSeed & 0xffffL) * 0.00031D;
            double scanYaw = Math.sin(gameTick * 0.021D + seedPhase) * Math.toRadians(92.0D)
                    + Math.sin(gameTick * 0.007D + seedPhase * 0.37D) * Math.toRadians(24.0D);
            double scanPitch = Math.toRadians(-68.0D)
                    + Math.sin(gameTick * 0.013D + seedPhase * 1.7D) * Math.toRadians(12.0D);
            DragonQuaternion scan = bodyOrientation
                    .multiply(DragonQuaternion.axisAngle(new Vec3(0.0D, 1.0D, 0.0D), scanYaw))
                    .multiply(DragonQuaternion.axisAngle(new Vec3(1.0D, 0.0D, 0.0D), -scanPitch));
            lookPosition = eyePosition.add(scan.forward().scale(30.0D));
        }

        Vec3 localDirection = bodyOrientation.conjugate().rotate(lookPosition.subtract(eyePosition));
        double targetYaw = Math.atan2(localDirection.x, localDirection.z);
        double horizontal = Math.sqrt(localDirection.x * localDirection.x + localDirection.z * localDirection.z);
        double targetPitch = Math.atan2(localDirection.y, Math.max(horizontal, 1.0e-8D));
        targetPitch = Math.clamp(targetPitch, MINIMUM_PITCH, MAXIMUM_PITCH);
        targetYaw = clampEllipticalYaw(targetYaw, targetPitch, yaw);

        yaw = approachAngle(yaw, targetYaw, 0.27D, Math.toRadians(16.0D));
        pitch = approachAngle(pitch, targetPitch, 0.27D, Math.toRadians(12.0D));
        if (targetEntityId < 0 || targetEntityId != bodyAssistTargetEntityId) {
            excessiveYawTicks = 0;
            comfortableYawTicks = 0;
            bodyTurnAssistActive = false;
            bodyAssistTargetEntityId = targetEntityId;
        } else if (!bodyTurnAssistActive && Math.abs(yaw) > BODY_ASSIST_ACTIVATION_YAW) {
            excessiveYawTicks++;
            if (excessiveYawTicks >= BODY_ASSIST_ACTIVATION_TICKS) {
                bodyTurnAssistActive = true;
                comfortableYawTicks = 0;
            }
        } else if (!bodyTurnAssistActive) {
            excessiveYawTicks = 0;
        } else if (Math.abs(yaw) < BODY_ASSIST_RELEASE_YAW) {
            comfortableYawTicks++;
            if (comfortableYawTicks >= BODY_ASSIST_RELEASE_TICKS) {
                excessiveYawTicks = 0;
                comfortableYawTicks = 0;
                bodyTurnAssistActive = false;
            }
        } else {
            comfortableYawTicks = 0;
        }
        // A wide activation/release hysteresis keeps the assist from chattering as an orbiting
        // target crosses the comfort boundary. It releases only after the neck has remained well
        // inside that boundary, so a passing target cannot make the whole body alternate sides.
        double bodyAssistYaw = bodyTurnAssistActive ? yaw : 0.0D;
        heldTicks++;
        return new AttentionStep(yaw, pitch, mode, targetEntityId, lookPosition, bodyAssistYaw);
    }

    void reset() {
        targetEntityId = -1;
        mode = AttentionMode.SCAN;
        score = 0.0D;
        lastVisiblePosition = Vec3.ZERO;
        heldTicks = 0;
        invisibleTicks = 0;
        suppressedEntityId = -1;
        excessiveYawTicks = 0;
        comfortableYawTicks = 0;
        bodyAssistTargetEntityId = -1;
        bodyTurnAssistActive = false;
        yaw = 0.0D;
        pitch = 0.0D;
    }

    private Candidate selectCandidate(List<Candidate> candidates, AttentionOverride override) {
        if (override == AttentionOverride.CLEAR) {
            mode = AttentionMode.CLEARED;
            targetEntityId = -1;
            score = Double.POSITIVE_INFINITY;
            return null;
        }
        if (override == AttentionOverride.SCAN) {
            mode = AttentionMode.OVERRIDE_SCAN;
            targetEntityId = -1;
            score = Double.POSITIVE_INFINITY;
            return null;
        }
        return candidates.stream()
                .filter(candidate -> candidate.visible()
                        || candidate.entityId() == targetEntityId
                        || candidate.mode() == AttentionMode.ATTACK_FOCUS)
                .filter(candidate -> candidate.mode() == AttentionMode.ATTACK_FOCUS
                        || candidate.entityId() != suppressedEntityId
                        || candidate.visible())
                .filter(candidate -> override != AttentionOverride.NEAREST || candidate.mode() == AttentionMode.OBSERVER)
                .max(Comparator.comparingDouble(Candidate::score).thenComparingInt(candidate -> -candidate.entityId()))
                .orElse(null);
    }

    private void updateSelection(Candidate selected, Candidate current) {
        if (mode == AttentionMode.CLEARED || mode == AttentionMode.OVERRIDE_SCAN) {
            if (selected == null) {
                return;
            }
            mode = AttentionMode.SCAN;
            score = 0.0D;
        }
        if (targetEntityId >= 0) {
            if (current != null && current.visible()) {
                lastVisiblePosition = current.position();
                invisibleTicks = 0;
                suppressedEntityId = -1;
            } else if (++invisibleTicks > LAST_VISIBLE_HOLD_TICKS) {
                suppressedEntityId = targetEntityId;
                targetEntityId = -1;
                mode = AttentionMode.SCAN;
                score = 0.0D;
                heldTicks = 0;
                invisibleTicks = 0;
                return;
            }
        }
        if (selected == null) {
            if (targetEntityId >= 0) {
                return;
            }
            targetEntityId = -1;
            mode = AttentionMode.SCAN;
            score = 0.0D;
            invisibleTicks = 0;
            return;
        }

        if (selected.entityId() == targetEntityId) {
            mode = selected.mode();
            score = selected.score();
            return;
        }

        boolean attackOverride = selected.mode() == AttentionMode.ATTACK_FOCUS;
        if (!attackOverride && targetEntityId >= 0
                && (heldTicks < MINIMUM_HOLD_TICKS || selected.score() <= score * SWITCH_SCORE_MULTIPLIER)) {
            return;
        }
        targetEntityId = selected.entityId();
        if (selected.visible()) {
            suppressedEntityId = -1;
        }
        mode = selected.mode();
        score = selected.score();
        lastVisiblePosition = selected.position();
        heldTicks = 0;
        invisibleTicks = selected.visible() ? 0 : 1;
    }

    private static double clampEllipticalYaw(double yaw, double pitch, double previousYaw) {
        double verticalLimit = pitch < 0.0D ? -MINIMUM_PITCH : MAXIMUM_PITCH;
        double fraction = Math.clamp(Math.abs(pitch) / verticalLimit, 0.0D, 1.0D);
        double yawLimit = MAXIMUM_YAW * Math.sqrt(Math.max(0.0D, 1.0D - fraction * fraction * 0.42D));
        if (Math.abs(yaw) <= yawLimit) {
            return yaw;
        }
        // The blind rear sector is continuous across atan2's +/-PI seam. Preserve the already
        // chosen side while the target remains there, so a tiny body turn cannot flip the neck
        // and the requested body correction to the opposite side.
        double side = Math.abs(previousYaw) > Math.toRadians(1.0D)
                ? Math.copySign(1.0D, previousYaw)
                : Math.copySign(1.0D, yaw);
        return side * yawLimit;
    }

    private static double approachAngle(double current, double target, double response, double maximumStep) {
        double error = target - current;
        double step = Math.clamp(error * response, -maximumStep, maximumStep);
        return current + step;
    }

    record Candidate(int entityId, Vec3 position, AttentionMode mode, double score, boolean visible) {
        Candidate {
            if (position == null || mode == null || !Double.isFinite(score)) {
                throw new IllegalArgumentException("Attention candidate must be complete and finite");
            }
        }
    }

    record AttentionStep(
            double yawRadians,
            double pitchRadians,
            AttentionMode mode,
            int targetEntityId,
            Vec3 lookPosition,
            double bodyAssistYawRadians) {
    }

    enum AttentionOverride {
        NONE,
        NEAREST,
        SCAN,
        CLEAR
    }
}
