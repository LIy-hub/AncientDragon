package com.liy.ancientdragon.atmosphere;

/**
 * Pure presentation tuning shared by the client controller and regression tests.
 *
 * <p>The mountain uses a broad distance veil rather than blindness. Close terrain and attack
 * telegraphs stay readable, while the dragon crosses in and out of the fog as its distance changes.
 * A slow client-side breathing multiplier opens and closes the long view without abrupt flashes.</p>
 */
public record DragonAtmosphereProfile(
        boolean active,
        float fogStart,
        float fogEnd,
        float red,
        float green,
        float blue,
        float colorWeight) {
    public static final double EFFECT_RANGE_BLOCKS = 640.0D;
    private static final DragonAtmosphereProfile NONE =
            new DragonAtmosphereProfile(false, Float.MAX_VALUE, Float.MAX_VALUE, 0.0F, 0.0F, 0.0F, 0.0F);

    public static DragonAtmosphereProfile forEncounter(
            DragonAtmospherePhase phase,
            DragonAtmosphereState state,
            double playerDistance,
            float fogIntensity) {
        if (state == DragonAtmosphereState.CORPSE
                || playerDistance >= EFFECT_RANGE_BLOCKS
                || fogIntensity <= 0.0F) {
            return NONE;
        }

        float rangeFade = (float) Math.clamp(
                (EFFECT_RANGE_BLOCKS - playerDistance) / (EFFECT_RANGE_BLOCKS - 96.0D), 0.0D, 1.0D);
        float strength = rangeFade * Math.clamp(fogIntensity, 0.0F, 1.0F);
        if (strength <= 0.01F) {
            return NONE;
        }

        float start = 18.0F;
        float end = 70.0F;
        float red = 0.38F;
        float green = 0.41F;
        float blue = 0.43F;
        float colorWeight = 0.38F;
        switch (phase) {
            case STORM -> {
                start = 12.0F;
                end = 42.0F;
                red = 0.30F;
                green = 0.38F;
                blue = 0.47F;
                colorWeight = 0.54F;
            }
            case SOLAR -> {
                start = 18.0F;
                end = 64.0F;
                red = 0.52F;
                green = 0.34F;
                blue = 0.17F;
                colorWeight = 0.40F;
            }
            case DEATH -> {
                start = 16.0F;
                end = 58.0F;
                red = 0.31F;
                green = 0.30F;
                blue = 0.29F;
                colorWeight = 0.38F;
            }
            case DORMANT, MOUNTAIN -> {
                start = 18.0F;
                end = 70.0F;
                red = 0.38F;
                green = 0.41F;
                blue = 0.43F;
                colorWeight = 0.38F;
            }
        }
        if (state == DragonAtmosphereState.DORMANT) {
            start = 24.0F;
            end = 88.0F;
            red = 0.34F;
            green = 0.39F;
            blue = 0.43F;
            colorWeight = 0.32F;
        }
        if (state == DragonAtmosphereState.AWAKENING) {
            end = Math.max(end, 82.0F);
            colorWeight = Math.min(colorWeight, 0.18F);
        }
        return new DragonAtmosphereProfile(
                true,
                lerp(192.0F, start, strength),
                lerp(256.0F, end, strength),
                red,
                green,
                blue,
                colorWeight * strength);
    }

    /** Slow, bounded fog movement: dense enough to conceal at the crest, thin enough to reveal again. */
    public static float breathingMultiplier(long clientTicks) {
        double primary = 0.5D + 0.5D * Math.sin(clientTicks * 0.016D);
        double secondary = 0.5D + 0.5D * Math.sin(clientTicks * 0.0067D + 1.4D);
        return (float) (0.74D + primary * 0.18D + secondary * 0.08D);
    }

    private static float lerp(float start, float end, float amount) {
        return start + ((end - start) * amount);
    }
}
