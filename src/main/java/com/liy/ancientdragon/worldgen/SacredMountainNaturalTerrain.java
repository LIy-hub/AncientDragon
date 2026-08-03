package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Column;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Run;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Tile;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Natural-only shoreline pedestal that exposes the authored foothills without lifting the peaks. */
final class SacredMountainNaturalTerrain {
    private static final int BASE_SHORELINE_RADIUS = 356;
    private static final int SHORELINE_NOISE_AMPLITUDE = 10;
    private static final int MAXIMUM_SURFACE_RISE = 7;

    private SacredMountainNaturalTerrain() {
    }

    static Optional<Surface> topSurface(Tile tile, Column column) {
        int localY = tile.minimumLocalY();
        Surface surface = null;
        for (Run run : column.runs()) {
            if (run.paletteIndex() != 0) {
                surface = new Surface(localY + run.length() - 1, tile.palette().get(run.paletteIndex()));
            }
            localY += run.length();
        }
        return Optional.ofNullable(surface);
    }

    static int targetSurfaceY(int seaLevel, int localX, int localZ, long seed) {
        long noise = mix(seed ^ ((long) localX * 0x9E3779B97F4A7C15L)
                ^ ((long) localZ * 0xC2B2AE3D27D4EB4FL));
        int shorelineNoise = (int) Math.floorMod(noise, SHORELINE_NOISE_AMPLITUDE * 2L + 1L)
                - SHORELINE_NOISE_AMPLITUDE;
        double shorelineRadius = BASE_SHORELINE_RADIUS + shorelineNoise;
        double distance = Math.hypot(localX, localZ);
        if (distance >= shorelineRadius) {
            return Integer.MIN_VALUE;
        }
        int surfaceNoise = (int) Math.floorMod(noise >>> 17, 3L) - 1;
        int inlandDepth = (int) Math.floor(shorelineRadius - distance);
        int rise = Math.clamp(1 + inlandDepth / 24 + surfaceNoise, 1, MAXIMUM_SURFACE_RISE);
        return seaLevel + rise;
    }

    static BlockState fillState() {
        return Blocks.STONE.defaultBlockState();
    }

    static BlockState capState(BlockState authoredSurface) {
        if (authoredSurface.hasBlockEntity() || !authoredSurface.getFluidState().isEmpty()) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        return authoredSurface;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    record Surface(int localY, String serializedState) {
    }
}
