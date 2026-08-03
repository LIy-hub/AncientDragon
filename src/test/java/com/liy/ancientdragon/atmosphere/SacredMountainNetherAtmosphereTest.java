package com.liy.ancientdragon.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class SacredMountainNetherAtmosphereTest {
    private static final BoundingBox MOUNTAIN = new BoundingBox(0, 0, 0, 100, 100, 100);

    @Test
    void mountainPositionsResolveToAllFiveStableNetherMemories() {
        assertEquals(SacredMountainNetherZone.SOUL_SAND_VALLEY,
                SacredMountainNetherAtmosphere.stateFor(MOUNTAIN, new BlockPos(50, 99, 50)).zone());
        assertEquals(SacredMountainNetherZone.NETHER_WASTES,
                SacredMountainNetherAtmosphere.stateFor(MOUNTAIN, new BlockPos(135, 50, 50)).zone());
        assertEquals(SacredMountainNetherZone.WARPED_FOREST,
                SacredMountainNetherAtmosphere.stateFor(MOUNTAIN, new BlockPos(20, 50, 50)).zone());
        assertEquals(SacredMountainNetherZone.CRIMSON_FOREST,
                SacredMountainNetherAtmosphere.stateFor(MOUNTAIN, new BlockPos(80, 50, 50)).zone());
        assertEquals(SacredMountainNetherZone.BASALT_DELTAS,
                SacredMountainNetherAtmosphere.stateFor(MOUNTAIN, new BlockPos(50, 50, 50)).zone());
    }

    @Test
    void particleFieldIntensityStaysBoundedAndFavorsTheCrest() {
        float crestIntensity = SacredMountainNetherAtmosphere
                .stateFor(MOUNTAIN, new BlockPos(50, 99, 50))
                .intensity();
        float outerIntensity = SacredMountainNetherAtmosphere
                .stateFor(MOUNTAIN, new BlockPos(135, 50, 50))
                .intensity();
        assertTrue(crestIntensity >= 0.64F && crestIntensity <= 0.96F);
        assertTrue(outerIntensity >= 0.64F && outerIntensity <= 0.96F);
        assertTrue(crestIntensity > outerIntensity);
    }
}
