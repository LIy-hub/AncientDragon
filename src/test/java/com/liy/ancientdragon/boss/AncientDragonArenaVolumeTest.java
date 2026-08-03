package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Bounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class AncientDragonArenaVolumeTest {
    @Test
    void structureBoundsReceiveOnlyTheApprovedBuffers() {
        AncientDragonArenaVolume volume = AncientDragonArenaVolume.fromStructureBounds(
                new Bounds(new BlockPos(-400, -63, -384), new BlockPos(367, 246, 383)));
        assertTrue(volume.contains(new Vec3(-496.0D, -63.0D, -480.0D)));
        assertTrue(volume.contains(new Vec3(464.0D, 439.0D, 480.0D)));
        assertFalse(volume.contains(new Vec3(-496.1D, -63.0D, 0.0D)));
        assertFalse(volume.contains(new Vec3(0.0D, 439.1D, 0.0D)));
    }

    @Test
    void naturalFallbackCoversTheEntireMountainAndItsFlightShell() {
        AncientDragonArenaVolume volume = AncientDragonArenaVolume.fallback(Vec3.ZERO);
        assertTrue(volume.contains(new Vec3(640.0D, 320.0D, -640.0D)));
        assertFalse(volume.contains(new Vec3(640.1D, 0.0D, 0.0D)));
    }
}
