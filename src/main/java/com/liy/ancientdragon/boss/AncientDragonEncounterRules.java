package com.liy.ancientdragon.boss;

import com.liy.ancientdragon.entity.AncientDragonPartKind;

/** Deterministic balance and timing constants for the Sacred Mountain encounter. */
public final class AncientDragonEncounterRules {
    public static final float SOLO_MAX_HEALTH = 600.0F;
    public static final float HEALTH_PER_ADDITIONAL_PARTICIPANT = 180.0F;
    public static final int MAX_PARTICIPANTS = 8;
    /** Global outgoing-damage balance factor applied after the active-roster pressure curve. */
    public static final float OUTGOING_DAMAGE_SCALE = 0.5F;

    public static final double AWAKEN_RADIUS_BLOCKS = 256.0D;
    public static final double ARENA_HORIZONTAL_BUFFER_BLOCKS = 96.0D;
    public static final double ARENA_UPPER_BUFFER_BLOCKS = 192.0D;
    public static final int RETURN_TO_ROOST_DELAY_TICKS = 600;
    public static final int RECENT_THREAT_WINDOW_TICKS = 400;
    public static final int TARGET_LOCK_TICKS = 100;
    public static final int PARTICIPATION_TIME_TICKS = 1_200;
    public static final double PARTICIPATION_DAMAGE_FRACTION = 0.01D;
    public static final int AIR_ATTACKS_PER_CYCLE = 4;

    public static final float SOLO_STABILITY = 100.0F;
    public static final float STABILITY_PER_ADDITIONAL_PARTICIPANT = 30.0F;
    public static final float MAX_STABILITY = 310.0F;
    public static final float STABILITY_DAMAGE_PER_HIT_CAP = 18.0F;

    private AncientDragonEncounterRules() {
    }

    public enum Phase {
        MOUNTAIN("mountain", 500),
        STORM("storm", 440),
        SOLAR("solar", 360);

        private final String serializedName;
        private final int groundWindowTicks;

        Phase(String serializedName, int groundWindowTicks) {
            this.serializedName = serializedName;
            this.groundWindowTicks = groundWindowTicks;
        }

        public String serializedName() {
            return serializedName;
        }

        public int groundWindowTicks() {
            return groundWindowTicks;
        }
    }

    public enum AttackDomain {
        AIR,
        GROUND
    }

    public enum Attack {
        DIVE_STRIKE(AttackDomain.AIR, 101, 28.0F),
        TAIL_SWEEP(AttackDomain.AIR, 81, 22.0F),
        STORM_BURST(AttackDomain.AIR, 101, 24.0F),
        SOLAR_BREATH(AttackDomain.AIR, 121, 7.0F),
        LIGHTNING_CHAIN(AttackDomain.AIR, 101, 10.0F),
        WIND_BLADE(AttackDomain.AIR, 81, 18.0F),
        STORM_CAGE(AttackDomain.AIR, 101, 14.0F),
        BITE_HEAVY(AttackDomain.GROUND, 49, 30.0F),
        WING_SLAM(AttackDomain.GROUND, 81, 18.0F),
        RIFT_CLAW(AttackDomain.GROUND, 81, 22.0F),
        GROUND_STORM_BURST(AttackDomain.GROUND, 81, 24.0F),
        GROUND_SOLAR_BREATH(AttackDomain.GROUND, 101, 8.0F);

        private final AttackDomain domain;
        private final int durationTicks;
        private final float baseDamage;

        Attack(AttackDomain domain, int durationTicks, float baseDamage) {
            this.domain = domain;
            this.durationTicks = durationTicks;
            this.baseDamage = baseDamage;
        }

        public AttackDomain domain() {
            return domain;
        }

        public int durationTicks() {
            return durationTicks;
        }

        /** Damage before player mitigation and the small active-roster pressure multiplier. */
        public float baseDamage() {
            return baseDamage;
        }
    }

    public static int normalizedParticipants(int participants) {
        return Math.clamp(participants, 1, MAX_PARTICIPANTS);
    }

    public static float maxHealth(int participants) {
        int normalized = normalizedParticipants(participants);
        return SOLO_MAX_HEALTH + ((normalized - 1) * HEALTH_PER_ADDITIONAL_PARTICIPANT);
    }

    /** Current active players affect pressure on top of the global half-strength damage balance. */
    public static float damageMultiplier(int participants) {
        int normalized = normalizedParticipants(participants);
        return OUTGOING_DAMAGE_SCALE * Math.min(1.2F, 1.0F + ((normalized - 1) * 0.035F));
    }

    public static int simultaneousPressureTargets(int participants) {
        return Math.min(normalizedParticipants(participants), 4);
    }

    public static int attackCooldownTicks(int participants) {
        int normalized = normalizedParticipants(participants);
        return Math.max(32, 60 - ((normalized - 1) * 4));
    }

    public static float stabilityMaximum(int participants) {
        int normalized = normalizedParticipants(participants);
        return Math.min(MAX_STABILITY, SOLO_STABILITY + ((normalized - 1) * STABILITY_PER_ADDITIONAL_PARTICIPANT));
    }

    public static float stabilityDamage(AncientDragonPartKind part, float acceptedDamage) {
        if (part == null || !part.isWing() || !(acceptedDamage > 0.0F)) {
            return 0.0F;
        }
        return Math.min(STABILITY_DAMAGE_PER_HIT_CAP, acceptedDamage * part.stabilityMultiplier());
    }

    public static Phase phaseFor(float health, float maximumHealth) {
        if (!(maximumHealth > 0.0F)) {
            return Phase.MOUNTAIN;
        }
        float fraction = Math.clamp(health / maximumHealth, 0.0F, 1.0F);
        if (fraction > 0.68F) {
            return Phase.MOUNTAIN;
        }
        if (fraction > 0.34F) {
            return Phase.STORM;
        }
        return Phase.SOLAR;
    }
}
