package com.liy.ancientdragon.boss;

import com.liy.ancientdragon.entity.AncientDragonPartKind;

/** One-cycle wing stability pool. It never changes health or severs a wing. */
public final class AncientDragonStability {
    private float maximum = AncientDragonEncounterRules.SOLO_STABILITY;
    private float current = maximum;
    private boolean forcedLandingUsed;

    public void resetForAirCycle(int lockedParticipants) {
        maximum = AncientDragonEncounterRules.stabilityMaximum(lockedParticipants);
        current = maximum;
        forcedLandingUsed = false;
    }

    public void rescaleMaximum(int lockedParticipants) {
        float newMaximum = AncientDragonEncounterRules.stabilityMaximum(lockedParticipants);
        if (newMaximum <= maximum) {
            return;
        }
        float fraction = current / Math.max(maximum, 1.0F);
        maximum = newMaximum;
        current = Math.clamp(fraction * maximum, 0.0F, maximum);
    }

    /** Returns true only on the hit that newly breaks stability. */
    public boolean applyAcceptedDamage(AncientDragonPartKind part, float acceptedDamage) {
        if (forcedLandingUsed) {
            return false;
        }
        float stabilityDamage = AncientDragonEncounterRules.stabilityDamage(part, acceptedDamage);
        if (!(stabilityDamage > 0.0F)) {
            return false;
        }
        float previous = current;
        current = Math.max(0.0F, current - stabilityDamage);
        if (previous > 0.0F && current <= 0.0F) {
            forcedLandingUsed = true;
            return true;
        }
        return false;
    }

    /** Administrator-only test hook; returns true when the assignment newly breaks stability. */
    public boolean debugSetCurrent(float value) {
        float previous = current;
        current = Math.clamp(value, 0.0F, maximum);
        if (previous > 0.0F && current <= 0.0F && !forcedLandingUsed) {
            forcedLandingUsed = true;
            return true;
        }
        return false;
    }

    public void restore(float restoredCurrent, float restoredMaximum, boolean restoredForcedLandingUsed) {
        maximum = Math.clamp(restoredMaximum, 1.0F, AncientDragonEncounterRules.MAX_STABILITY);
        current = Math.clamp(restoredCurrent, 0.0F, maximum);
        forcedLandingUsed = restoredForcedLandingUsed;
    }

    public float current() {
        return current;
    }

    public float maximum() {
        return maximum;
    }

    public boolean forcedLandingUsed() {
        return forcedLandingUsed;
    }
}
