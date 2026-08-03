package com.liy.ancientdragon.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** One seed-stable, chunk-streamed Sacred Mountain structure start. */
public final class SacredMountainStructure extends Structure {
    private static final System.Logger LOGGER = System.getLogger("Ancient Dragon Sacred Mountain");
    public static final MapCodec<SacredMountainStructure> CODEC = simpleCodec(SacredMountainStructure::new);

    public SacredMountainStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos startChunk = context.chunkPos();
        Optional<ChunkPos> selected = SacredMountainPlacement.findSelectedChunk(
                context.seed(),
                context.biomeSource(),
                context.randomState(),
                context.chunkGenerator().getSeaLevel(),
                context.validBiome());
        if (selected.isEmpty() || !SacredMountainPlacement.isSectorStart(selected.orElseThrow(), startChunk)) {
            return Optional.empty();
        }
        ChunkPos canonicalStart = selected.orElseThrow();
        if (canonicalStart.equals(startChunk)) {
            LOGGER.log(System.Logger.Level.INFO,
                    "Creating unique deep-ocean Sacred Mountain sectors around {0}", canonicalStart);
        }
        BlockPos anchor = new BlockPos(
                canonicalStart.getMinBlockX(),
                naturalAnchorY(context.heightAccessor().getMinY()),
                canonicalStart.getMinBlockZ());
        BlockPos biomeCheck = new BlockPos(
                canonicalStart.getMiddleBlockX(),
                context.chunkGenerator().getSeaLevel(),
                canonicalStart.getMiddleBlockZ());
        int quarterTurns = quarterTurnsFor(context.seed(), canonicalStart);
        BoundingBox sectorBounds = sectorBounds(startChunk, anchor.getY(), context.heightAccessor().getMaxY());
        // GenerationStub.position is also the vanilla biome-validation sample. Keep that sample
        // at the selected deep-ocean surface for every sector; sampling the satellite chunk would
        // incorrectly reject sectors whose mountain footprint reaches a neighbouring land biome.
        return Optional.of(new GenerationStub(biomeCheck,
                builder -> builder.addPiece(new SacredMountainStructurePiece(anchor, quarterTurns, sectorBounds))));
    }

    static int quarterTurnsFor(long seed, ChunkPos canonicalStart) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, canonicalStart.x(), canonicalStart.z());
        return random.nextInt(4);
    }

    static BoundingBox sectorBounds(ChunkPos sectorStart, int anchorY, int dimensionMaximumY) {
        var manifest = SacredMountainStructureAsset.loadDefault().manifest();
        int minimumChunkX = sectorStart.x() - 8;
        int minimumChunkZ = sectorStart.z() - 8;
        int maximumChunkX = sectorStart.x() + 7;
        int maximumChunkZ = sectorStart.z() + 7;
        return new BoundingBox(
                minimumChunkX << 4,
                anchorY + manifest.minimumY(),
                minimumChunkZ << 4,
                (maximumChunkX << 4) + 15,
                Math.min(dimensionMaximumY - 1,
                        anchorY + manifest.maximumY()),
                (maximumChunkZ << 4) + 15);
    }

    static int naturalAnchorY(int dimensionMinimumY) {
        // The frozen source anchor is y=-61 in a dimension whose minimum is y=-64.
        return dimensionMinimumY + 3;
    }

    @Override
    public StructureType<?> type() {
        return AncientDragonWorldgen.SACRED_MOUNTAIN_STRUCTURE_TYPE;
    }
}
