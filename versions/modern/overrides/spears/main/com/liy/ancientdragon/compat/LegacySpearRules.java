package com.liy.ancientdragon.compat;

/** Parameters of the upstream Emberbone spear, expressed without post-1.21.10 components. */
public final class LegacySpearRules {
    public static final int CONTACT_COOLDOWN = 10;
    public static final int DELAY = 8;
    public static final double MIN_RANGE = 2.0;
    public static final double SURVIVAL_RANGE = 4.5;
    public static final double CREATIVE_RANGE = 6.5;
    private LegacySpearRules() { }

    public static Outcome kinetic(int elapsed, double forwardSpeed, double targetForwardSpeed, boolean player) {
        int active = elapsed - DELAY;
        if (active < 0) return new Outcome(false, false, false, 0);
        double scale = player ? 1.0 : 0.2;
        double relative = Math.max(0, forwardSpeed - targetForwardSpeed);
        boolean dismount = active <= 50 && forwardSpeed >= 9.0 * scale;
        boolean knockback = active <= 110 && forwardSpeed >= 5.1 * scale;
        boolean damage = active <= 175 && relative >= 4.6 * scale;
        return new Outcome(damage, knockback, dismount, (int) Math.floor(relative * 1.2));
    }
    public record Outcome(boolean damage, boolean knockback, boolean dismount, int kineticDamage) { }
}
