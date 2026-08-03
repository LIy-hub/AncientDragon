package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SacredMountainPlacementServiceTest {
    private final SacredMountainStructureAsset asset = SacredMountainStructureAsset.loadDefault();

    @Test
    void cellPreservingQuarterTurnsKeepTheFrozenBoundsAndCycleBackToTheSource() {
        BlockPos anchor = new BlockPos(512, -61, 1024);
        SacredMountainStructureAsset.Manifest manifest = asset.manifest();

        for (int turns = 0; turns < 4; turns++) {
            SacredMountainAuthoringData.Bounds bounds = SacredMountainPlacementService.transformedBounds(
                    anchor, manifest, turns);
            assertEquals(new BlockPos(128, -60, 640), bounds.minimum());
            assertEquals(new BlockPos(895, 249, 1407), bounds.maximum());
        }

        BlockPos source = new BlockPos(-137, 205, 91);
        BlockPos rotated = source;
        for (int index = 0; index < 4; index++) {
            rotated = SacredMountainPlacementService.transform(BlockPos.ZERO,
                    rotated.getX(), rotated.getY(), rotated.getZ(), 1);
        }
        assertEquals(source, rotated);
    }

    @Test
    void dragonRestPointRotatesWithTheStructure() {
        SacredMountainStructureAsset.Manifest manifest = asset.manifest();
        BlockPos source = new BlockPos(
                manifest.dragonRestX(), manifest.dragonRestY(), manifest.dragonRestZ());

        assertEquals(new BlockPos(139, 205, 1), rotate(source, 0));
        assertEquals(new BlockPos(-2, 205, 139), rotate(source, 1));
        assertEquals(new BlockPos(-140, 205, -2), rotate(source, 2));
        assertEquals(new BlockPos(1, 205, -140), rotate(source, 3));
    }

    @Test
    void everyNonEmptySourceTileStillMapsToExactlyOneChunkInEveryRotation() {
        SacredMountainStructureAsset.Manifest manifest = asset.manifest();
        BlockPos anchor = new BlockPos(160, -61, -320);

        for (int turns = 0; turns < 4; turns++) {
            for (SacredMountainStructureAsset.TileCoordinate coordinate : asset.tileCoordinates()) {
                int minimumX = manifest.minimumX() + coordinate.x() * 16;
                int minimumZ = manifest.minimumZ() + coordinate.z() * 16;
                int maximumX = minimumX + 15;
                int maximumZ = minimumZ + 15;
                BlockPos first = SacredMountainPlacementService.transform(
                        anchor, minimumX, manifest.minimumY(), minimumZ, turns);
                int chunkX = first.getX() >> 4;
                int chunkZ = first.getZ() >> 4;

                assertChunk(chunkX, chunkZ, anchor, maximumX, minimumZ, turns);
                assertChunk(chunkX, chunkZ, anchor, minimumX, maximumZ, turns);
                assertChunk(chunkX, chunkZ, anchor, maximumX, maximumZ, turns);
            }
        }
    }

    @Test
    void progressIncludesTheCurrentTileColumnsAndIsClamped() {
        SacredMountainPlacementData.Job job = jobWithProgress(
                new SacredMountainPlacementData.Progress(100, 128, 0, 0));
        assertEquals(0.1005D, SacredMountainPlacementService.progressFraction(job, 1_000), 0.000001D);
        assertEquals(1.0D, SacredMountainPlacementService.progressFraction(
                jobWithProgress(new SacredMountainPlacementData.Progress(2_000, 0, 0, 0)), 1_000));
    }

    private static BlockPos rotate(BlockPos position, int turns) {
        return SacredMountainPlacementService.transform(
                BlockPos.ZERO, position.getX(), position.getY(), position.getZ(), turns);
    }

    private static void assertChunk(
            int expectedX, int expectedZ, BlockPos anchor, int localX, int localZ, int turns) {
        BlockPos transformed = SacredMountainPlacementService.transform(anchor, localX, 1, localZ, turns);
        assertEquals(expectedX, transformed.getX() >> 4);
        assertEquals(expectedZ, transformed.getZ() >> 4);
    }

    private static SacredMountainPlacementData.Job jobWithProgress(SacredMountainPlacementData.Progress progress) {
        return new SacredMountainPlacementData.Job(
                "test", SacredMountainStructureAsset.DEFAULT_ASSET_ID,
                "afa7f74628eb524eeb34018a88c9289aad0f6c7042593b731ef7ec66d9558a4e",
                "minecraft:overworld", BlockPos.ZERO, 0,
                new SacredMountainAuthoringData.Bounds(BlockPos.ZERO, new BlockPos(1, 1, 1)),
                BlockPos.ZERO, 90.0F,
                SacredMountainPlacementData.Status.PREFLIGHT,
                SacredMountainPlacementData.Phase.PREFLIGHT,
                progress, "", 0L, 0L, "");
    }
}
