package com.liy.ancientdragon.boss;

/** Pure timing policy for the post-harvest Ancient Dragon corpse dissipation. */
public final class AncientDragonCorpseDissipation {
    /** Ten seconds gives nearby players time to see the remains leave without delaying the encounter. */
    public static final int DURATION_TICKS = 20 * 10;

    private AncientDragonCorpseDissipation() {
    }

    /** Advances a started dissipation timer without exceeding its terminal tick. */
    public static int advance(int elapsedTicks) {
        return Math.min(DURATION_TICKS, Math.max(0, elapsedTicks) + 1);
    }

    public static boolean isActive(int elapsedTicks) {
        return elapsedTicks >= 0 && elapsedTicks < DURATION_TICKS;
    }

    public static boolean isComplete(int elapsedTicks) {
        return elapsedTicks >= DURATION_TICKS;
    }

    /** Returns a stable 0–1 progress value used only for presentation effects. */
    public static float progress(int elapsedTicks) {
        if (elapsedTicks < 0) {
            return 0.0F;
        }
        return Math.clamp((float) elapsedTicks / (float) DURATION_TICKS, 0.0F, 1.0F);
    }

    /** Ash slowly thickens as the corpse's remaining form breaks apart. */
    public static int ashParticles(int elapsedTicks) {
        return 3 + (int) Math.floor(progress(elapsedTicks) * 9.0F);
    }

    /** Soul fire remains sparse so the effect reads as dissipation rather than another attack. */
    public static int soulFlameParticles(int elapsedTicks) {
        return 1 + (int) Math.floor(progress(elapsedTicks) * 4.0F);
    }
}
