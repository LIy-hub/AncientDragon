package com.liy.ancientdragon.boss;

import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Bounds;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData.Job;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** The placed structure crop plus the approved horizontal and upper-air combat buffers. */
public record AncientDragonArenaVolume(
        double minimumX,
        double minimumY,
        double minimumZ,
        double maximumX,
        double maximumY,
        double maximumZ) {

    private static final double FALLBACK_HORIZONTAL_RADIUS = 640.0D;
    private static final double FALLBACK_LOWER_DEPTH = 256.0D;
    private static final double FALLBACK_UPPER_HEIGHT = 320.0D;

    public AncientDragonArenaVolume {
        if (!Double.isFinite(minimumX) || !Double.isFinite(minimumY) || !Double.isFinite(minimumZ)
                || !Double.isFinite(maximumX) || !Double.isFinite(maximumY) || !Double.isFinite(maximumZ)
                || minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Invalid Ancient Dragon arena volume");
        }
    }

    public static AncientDragonArenaVolume resolve(ServerLevel level, UUID dragonUuid, Vec3 encounterOrigin) {
        Optional<Job> placement = SacredMountainPlacementData.get(level).job().filter(job ->
                job.dimension().equals(level.dimension().identifier().toString())
                        && job.dragonUuid().map(dragonUuid::equals).orElse(false));
        return placement.map(Job::bounds)
                .map(AncientDragonArenaVolume::fromStructureBounds)
                .orElseGet(() -> fallback(encounterOrigin));
    }

    public static AncientDragonArenaVolume fromStructureBounds(Bounds bounds) {
        BlockPos minimum = bounds.minimum();
        BlockPos maximum = bounds.maximum();
        double horizontal = AncientDragonEncounterRules.ARENA_HORIZONTAL_BUFFER_BLOCKS;
        return new AncientDragonArenaVolume(
                minimum.getX() - horizontal,
                minimum.getY(),
                minimum.getZ() - horizontal,
                maximum.getX() + 1.0D + horizontal,
                maximum.getY() + 1.0D + AncientDragonEncounterRules.ARENA_UPPER_BUFFER_BLOCKS,
                maximum.getZ() + 1.0D + horizontal);
    }

    public static AncientDragonArenaVolume fallback(Vec3 origin) {
        return new AncientDragonArenaVolume(
                origin.x - FALLBACK_HORIZONTAL_RADIUS,
                origin.y - FALLBACK_LOWER_DEPTH,
                origin.z - FALLBACK_HORIZONTAL_RADIUS,
                origin.x + FALLBACK_HORIZONTAL_RADIUS,
                origin.y + FALLBACK_UPPER_HEIGHT,
                origin.z + FALLBACK_HORIZONTAL_RADIUS);
    }

    public boolean contains(Vec3 position) {
        return position.x >= minimumX && position.x <= maximumX
                && position.y >= minimumY && position.y <= maximumY
                && position.z >= minimumZ && position.z <= maximumZ;
    }
}
