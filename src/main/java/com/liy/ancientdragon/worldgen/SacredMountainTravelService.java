package com.liy.ancientdragon.worldgen;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Resolves and validates the one safe, above-water arrival point on the natural Sacred Mountain. */
public final class SacredMountainTravelService {
    private static final int SAFE_SEARCH_RADIUS = 4;
    private static final int[] SAFE_VERTICAL_OFFSETS = {0, 1, -1, 2, -2};

    private SacredMountainTravelService() {
    }

    public static Preparation prepareDestination(ServerLevel sourceLevel) {
        if (!Level.OVERWORLD.equals(sourceLevel.dimension())) {
            return Preparation.failure(Failure.WRONG_DIMENSION);
        }
        var registry = sourceLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var holder = registry.get(AncientDragonWorldgen.SACRED_MOUNTAIN_KEY).orElse(null);
        if (holder == null) {
            return Preparation.failure(Failure.STRUCTURE_NOT_REGISTERED);
        }
        var generatorState = sourceLevel.getChunkSource().getGeneratorState();
        if (generatorState.getPlacementsForStructure(holder).stream()
                .noneMatch(SacredMountainPlacement.class::isInstance)) {
            return Preparation.failure(Failure.PLACEMENT_NOT_ACTIVE);
        }
        SacredMountainStructurePiece piece = SacredMountainNaturalRepairService
                .completedLegacyPiece(sourceLevel)
                .orElse(null);
        if (piece == null) {
            Optional<ChunkPos> selected = SacredMountainPlacement.findSelectedChunk(
                    generatorState.getLevelSeed(),
                    sourceLevel.getChunkSource().getGenerator().getBiomeSource(),
                    generatorState.randomState(),
                    sourceLevel.getSeaLevel(),
                    holder.value().biomes()::contains);
            if (selected.isEmpty()) {
                return Preparation.failure(Failure.NO_DEEP_OCEAN_CANDIDATE);
            }

            ChunkPos startPosition = selected.orElseThrow();
            LevelChunk startChunk = sourceLevel.getChunk(startPosition.x(), startPosition.z());
            StructureStart start = startChunk.getStartForStructure(holder.value());
            if (start == null || !start.isValid()) {
                return Preparation.failure(Failure.GENERATED_WITHOUT_MOUNTAIN);
            }
            for (var candidate : start.getPieces()) {
                if (candidate instanceof SacredMountainStructurePiece sacredMountainPiece) {
                    piece = sacredMountainPiece;
                    break;
                }
            }
        }
        if (piece == null) {
            return Preparation.failure(Failure.MISSING_MOUNTAIN_PIECE);
        }
        if (!SacredMountainNaturalDragonSpawner.ensureNaturalDragon(sourceLevel, piece)) {
            return Preparation.failure(Failure.DRAGON_UNAVAILABLE);
        }

        BlockPos intendedFeet = piece.mountainEntrance();
        sourceLevel.getChunk(intendedFeet.getX() >> 4, intendedFeet.getZ() >> 4);
        Optional<BlockPos> safeFeet = findSafeFeet(sourceLevel, intendedFeet);
        if (safeFeet.isEmpty()) {
            return Preparation.failure(Failure.ENTRANCE_OBSTRUCTED);
        }
        return Preparation.success(new Destination(sourceLevel, safeFeet.orElseThrow(), piece.mountainEntranceYaw()));
    }

    static Optional<BlockPos> findSafeFeet(ServerLevel level, BlockPos intendedFeet) {
        for (int radius = 0; radius <= SAFE_SEARCH_RADIUS; radius++) {
            for (int verticalOffset : SAFE_VERTICAL_OFFSETS) {
                for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                    for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                        if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                            continue;
                        }
                        BlockPos candidate = intendedFeet.offset(offsetX, verticalOffset, offsetZ);
                        if (isSafeFeet(level, candidate)) {
                            return Optional.of(candidate.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isSafeFeet(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir();
    }

    public record Destination(ServerLevel level, BlockPos feet, float yaw) {
        public TeleportTransition transition(Entity entity) {
            return new TeleportTransition(
                    level,
                    feet.getBottomCenter(),
                    Vec3.ZERO,
                    yaw,
                    0.0F,
                    TeleportTransition.PLAY_PORTAL_SOUND.then(teleported -> teleported.setPortalCooldown()));
        }
    }

    public enum Failure {
        WRONG_DIMENSION("message.ancient_dragon.gateway_failure.wrong_dimension"),
        STRUCTURE_NOT_REGISTERED("message.ancient_dragon.gateway_failure.structure_not_registered"),
        PLACEMENT_NOT_ACTIVE("message.ancient_dragon.gateway_failure.placement_not_active"),
        NO_DEEP_OCEAN_CANDIDATE("message.ancient_dragon.gateway_failure.no_deep_ocean_candidate"),
        GENERATED_WITHOUT_MOUNTAIN("message.ancient_dragon.gateway_failure.generated_without_mountain"),
        MISSING_MOUNTAIN_PIECE("message.ancient_dragon.gateway_failure.missing_mountain_piece"),
        DRAGON_UNAVAILABLE("message.ancient_dragon.gateway_failure.dragon_unavailable"),
        ENTRANCE_OBSTRUCTED("message.ancient_dragon.gateway_failure.entrance_obstructed");

        private final String translationKey;

        Failure(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record Preparation(Optional<Destination> destination, Optional<Failure> failure) {
        static Preparation success(Destination destination) {
            return new Preparation(Optional.of(destination), Optional.empty());
        }

        static Preparation failure(Failure failure) {
            return new Preparation(Optional.empty(), Optional.of(failure));
        }

        public boolean succeeded() {
            return destination.isPresent();
        }
    }
}
