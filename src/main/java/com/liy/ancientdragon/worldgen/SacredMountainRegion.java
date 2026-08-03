package com.liy.ancientdragon.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.util.Optional;

/** Shared loaded-region test for the natural Sacred Mountain and its immediate atmosphere shell. */
final class SacredMountainRegion {
    static final int HORIZONTAL_MARGIN = 48;
    static final int VERTICAL_MARGIN = 64;

    private SacredMountainRegion() {
    }

    static boolean contains(ServerLevel level, BlockPos position) {
        return containingBounds(level, position).isPresent();
    }

    static boolean containsCore(ServerLevel level, BlockPos position) {
        return containingBounds(level, position)
                .map(bounds -> containsCore(bounds, position))
                .orElse(false);
    }

    static Optional<BoundingBox> containingBounds(ServerLevel level, BlockPos position) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return Optional.empty();
        }
        var structure = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValue(AncientDragonWorldgen.SACRED_MOUNTAIN_KEY);
        if (structure == null) {
            return Optional.empty();
        }
        ChunkPos chunk = ChunkPos.containing(position);
        for (StructureStart start : level.structureManager()
                .startsForStructure(chunk, candidate -> candidate == structure)) {
            if (!start.isValid()) {
                continue;
            }
            for (var rawPiece : start.getPieces()) {
                if (rawPiece instanceof SacredMountainStructurePiece mountainPiece
                        && contains(mountainPiece.getBoundingBox(), position)) {
                    return Optional.of(mountainPiece.fullMountainBounds());
                }
            }
        }
        // Migrated one-start mountains do not gain structure references in every repaired chunk.
        return SacredMountainNaturalRepairService.completedLegacyPiece(level)
                .filter(piece -> contains(piece.getBoundingBox(), position))
                .map(SacredMountainStructurePiece::fullMountainBounds);
    }

    static boolean contains(BoundingBox mountainBounds, BlockPos position) {
        return mountainBounds.inflatedBy(HORIZONTAL_MARGIN, VERTICAL_MARGIN, HORIZONTAL_MARGIN)
                .isInside(position);
    }

    static boolean containsCore(BoundingBox mountainBounds, BlockPos position) {
        return mountainBounds.isInside(position);
    }
}
