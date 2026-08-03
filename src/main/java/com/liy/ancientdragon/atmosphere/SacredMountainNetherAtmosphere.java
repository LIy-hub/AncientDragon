package com.liy.ancientdragon.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Pure Sacred Mountain particle-zone tuning shared by the server-side field and regression tests.
 *
 * <p>This is deliberately derived from the mountain's bounding box instead of world coordinates:
 * rotated or naturally placed mountains therefore keep the same broad visual rhythm without
 * creating another world-generation rule.</p>
 */
public final class SacredMountainNetherAtmosphere {
    private SacredMountainNetherAtmosphere() {
    }

    public static ZoneState stateFor(BoundingBox mountainBounds, BlockPos position) {
        double relativeX = normalized(position.getX(), mountainBounds.minX(), mountainBounds.maxX());
        double relativeY = normalized(position.getY(), mountainBounds.minY(), mountainBounds.maxY());
        double relativeZ = normalized(position.getZ(), mountainBounds.minZ(), mountainBounds.maxZ());
        SacredMountainNetherZone zone = zoneForRelative(relativeX, relativeY, relativeZ);
        return new ZoneState(zone, intensityForRelative(relativeX, relativeY, relativeZ));
    }

    /**
     * Assigns stable, readable regions rather than rolling a Nether biome every tick. The summit is
     * the quiet Soul Sand Valley, outer approaches carry Nether Wastes haze, and the two opposing
     * flanks become the fungal forests around a basaltic heart.
     */
    public static SacredMountainNetherZone zoneForRelative(double relativeX, double relativeY, double relativeZ) {
        double radialDistance = Math.hypot(relativeX, relativeZ);
        if (relativeY >= 0.46D) {
            return SacredMountainNetherZone.SOUL_SAND_VALLEY;
        }
        if (radialDistance >= 0.92D) {
            return SacredMountainNetherZone.NETHER_WASTES;
        }
        if (relativeX <= -0.16D && relativeZ <= 0.64D) {
            return SacredMountainNetherZone.WARPED_FOREST;
        }
        if (relativeX >= 0.16D && relativeZ >= -0.64D) {
            return SacredMountainNetherZone.CRIMSON_FOREST;
        }
        return SacredMountainNetherZone.BASALT_DELTAS;
    }

    /** Keeps the field visibly supernatural everywhere while allowing the crest and heart to breathe more densely. */
    public static float intensityForRelative(double relativeX, double relativeY, double relativeZ) {
        double radialDistance = Math.min(1.0D, Math.hypot(relativeX, relativeZ));
        double crest = Math.max(0.0D, Math.min(1.0D, relativeY));
        double shelter = 1.0D - radialDistance;
        return (float) Math.clamp(0.64D + (crest * 0.18D) + (shelter * 0.14D), 0.64D, 0.96D);
    }

    private static double normalized(int coordinate, int minimum, int maximum) {
        double center = (minimum + maximum) * 0.5D;
        double halfSpan = Math.max(1.0D, ((maximum - minimum) + 1.0D) * 0.5D);
        return Math.clamp((coordinate - center) / halfSpan, -1.5D, 1.5D);
    }

    public record ZoneState(SacredMountainNetherZone zone, float intensity) {
        public ZoneState {
            if (zone == null) {
                throw new IllegalArgumentException("zone cannot be null");
            }
            intensity = Math.clamp(intensity, 0.0F, 1.0F);
        }
    }
}
