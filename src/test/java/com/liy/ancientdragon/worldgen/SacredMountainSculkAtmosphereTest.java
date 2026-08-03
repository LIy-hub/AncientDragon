package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class SacredMountainSculkAtmosphereTest {
    @Test
    void atmosphereCoversTheWholeMountainAndACompactArrivalMargin() {
        BoundingBox mountain = new BoundingBox(100, 1, -200, 867, 249, 567);

        assertTrue(SacredMountainSculkAtmosphere.isWithinAtmosphere(mountain, new BlockPos(500, 120, 200)));
        assertTrue(SacredMountainSculkAtmosphere.isWithinAtmosphere(mountain, new BlockPos(52, 0, -248)));
        assertTrue(SacredMountainSculkAtmosphere.isWithinAtmosphere(mountain, new BlockPos(915, 313, 615)));
        assertFalse(SacredMountainSculkAtmosphere.isWithinAtmosphere(mountain, new BlockPos(51, 0, -248)));
        assertFalse(SacredMountainSculkAtmosphere.isWithinAtmosphere(mountain, new BlockPos(500, 314, 200)));
    }

    @Test
    void coreExcludesTheAtmosphereMarginUsedForParticles() {
        BoundingBox mountain = new BoundingBox(100, 1, -200, 867, 249, 567);

        assertTrue(SacredMountainRegion.containsCore(mountain, new BlockPos(500, 120, 200)));
        assertFalse(SacredMountainRegion.containsCore(mountain, new BlockPos(99, 120, 200)));
    }
}
