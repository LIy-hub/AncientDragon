package com.liy.ancientdragon.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Seed-stable placement candidates for exactly one far-ocean Sacred Mountain.
 *
 * <p>Vanilla concentric rings give up when no preferred biome is within their short adjustment
 * radius. This placement exposes a deterministic far ring of candidates, while the structure
 * selects the first deep-ocean candidate whose complete 48x48-chunk footprint plus an eight-chunk
 * margin remains in any ocean biome. Requiring every surrounding chunk to be deep ocean rejects
 * otherwise open water whenever the ocean changes temperature, so deep ocean is required only at
 * the mountain centre while the clearance rejects actual land. Only the nine sector starts around
 * that selected candidate can create starts, so increasing the search coverage cannot create
 * duplicate mountains.</p>
 */
public final class SacredMountainPlacement extends StructurePlacement {
    public static final MapCodec<SacredMountainPlacement> CODEC =
            MapCodec.unit(SacredMountainPlacement::new);

    static final int MINIMUM_DISTANCE_CHUNKS = 264;
    static final int MAXIMUM_DISTANCE_CHUNKS = 504;
    static final int CANDIDATE_COUNT = 4_096;
    static final int MOUNTAIN_RADIUS_CHUNKS = 24;
    static final int LAND_CLEARANCE_CHUNKS = 8;
    static final int OPEN_OCEAN_RADIUS_CHUNKS = MOUNTAIN_RADIUS_CHUNKS + LAND_CLEARANCE_CHUNKS;
    private static final int COARSE_OCEAN_SAMPLE_STEP_CHUNKS = 8;
    /**
     * Vanilla only discovers structure starts within eight chunks of the chunk being decorated.
     * Nine starts, each centred on a 16x16-chunk sector, cover the complete 48x48-chunk asset
     * without exceeding that hard reference radius.
     */
    static final int[] SECTOR_START_OFFSETS = {-16, 0, 16};
    private static final int SALT = 1_701_444_195;
    private static final long SEED_MIXER = 0x9E3779B97F4A7C15L;
    private static final Map<Long, CandidatePlan> PLANS = new ConcurrentHashMap<>();

    private SacredMountainPlacement() {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, SALT, Optional.empty());
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return isPotentialStart(state.getLevelSeed(), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type() {
        return AncientDragonWorldgen.SACRED_MOUNTAIN_PLACEMENT_TYPE;
    }

    public static Optional<ChunkPos> findSelectedChunk(
            long seed,
            BiomeSource biomeSource,
            RandomState randomState,
            int seaLevel,
            Predicate<Holder<Biome>> validBiome) {
        int quartY = QuartPos.fromBlock(seaLevel);
        for (ChunkPos candidate : candidateChunks(seed)) {
            Holder<Biome> centerBiome = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(candidate.getMiddleBlockX()),
                    quartY,
                    QuartPos.fromBlock(candidate.getMiddleBlockZ()),
                    randomState.sampler());
            if (!validBiome.test(centerBiome)) {
                continue;
            }
            if (hasOpenOceanClearance(candidate, (ignoredX, ignoredZ) -> true, (chunkX, chunkZ) -> {
                Holder<Biome> biome = biomeSource.getNoiseBiome(
                        QuartPos.fromBlock((chunkX << 4) + 8),
                        quartY,
                        QuartPos.fromBlock((chunkZ << 4) + 8),
                        randomState.sampler());
                return biome.is(BiomeTags.IS_OCEAN);
            })) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static boolean hasOpenOceanClearance(
            ChunkPos candidate,
            ChunkBiomeTest deepOceanAt,
            ChunkBiomeTest oceanAt) {
        if (!deepOceanAt.test(candidate.x(), candidate.z())) {
            return false;
        }
        if (!sampleCircle(candidate, COARSE_OCEAN_SAMPLE_STEP_CHUNKS, oceanAt)) {
            return false;
        }
        return sampleCircle(candidate, 1, oceanAt);
    }

    private static boolean sampleCircle(ChunkPos candidate, int step, ChunkBiomeTest oceanAt) {
        int radiusSquared = OPEN_OCEAN_RADIUS_CHUNKS * OPEN_OCEAN_RADIUS_CHUNKS;
        for (int offsetZ = -OPEN_OCEAN_RADIUS_CHUNKS;
                offsetZ <= OPEN_OCEAN_RADIUS_CHUNKS;
                offsetZ += step) {
            for (int offsetX = -OPEN_OCEAN_RADIUS_CHUNKS;
                    offsetX <= OPEN_OCEAN_RADIUS_CHUNKS;
                    offsetX += step) {
                if (offsetX * offsetX + offsetZ * offsetZ > radiusSquared) {
                    continue;
                }
                if (!oceanAt.test(candidate.x() + offsetX, candidate.z() + offsetZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    static List<ChunkPos> candidateChunks(long seed) {
        return plan(seed).candidates();
    }

    static boolean isPotentialStart(long seed, int chunkX, int chunkZ) {
        return plan(seed).startPositions().contains(ChunkPos.pack(chunkX, chunkZ));
    }

    static boolean isSectorStart(ChunkPos selected, ChunkPos candidate) {
        int offsetX = candidate.x() - selected.x();
        int offsetZ = candidate.z() - selected.z();
        return isSectorStartOffset(offsetX) && isSectorStartOffset(offsetZ);
    }

    private static boolean isSectorStartOffset(int offset) {
        for (int candidate : SECTOR_START_OFFSETS) {
            if (candidate == offset) {
                return true;
            }
        }
        return false;
    }

    private static CandidatePlan plan(long seed) {
        return PLANS.computeIfAbsent(seed, SacredMountainPlacement::createPlan);
    }

    private static CandidatePlan createPlan(long seed) {
        RandomSource random = RandomSource.create(seed ^ (SEED_MIXER * SALT));
        List<ChunkPos> candidates = new ArrayList<>(CANDIDATE_COUNT);
        Set<Long> candidatePositions = new HashSet<>(CANDIDATE_COUNT * 2);
        double minimumSquared = (double) MINIMUM_DISTANCE_CHUNKS * MINIMUM_DISTANCE_CHUNKS;
        double maximumSquared = (double) MAXIMUM_DISTANCE_CHUNKS * MAXIMUM_DISTANCE_CHUNKS;
        while (candidates.size() < CANDIDATE_COUNT) {
            double angle = random.nextDouble() * Math.TAU;
            double radius = Math.sqrt(minimumSquared + random.nextDouble() * (maximumSquared - minimumSquared));
            int chunkX = (int) Math.round(Math.cos(angle) * radius);
            int chunkZ = (int) Math.round(Math.sin(angle) * radius);
            long packed = ChunkPos.pack(chunkX, chunkZ);
            if (candidatePositions.add(packed)) {
                candidates.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        Set<Long> startPositions = new HashSet<>(CANDIDATE_COUNT * 18);
        for (ChunkPos candidate : candidates) {
            for (int offsetZ : SECTOR_START_OFFSETS) {
                for (int offsetX : SECTOR_START_OFFSETS) {
                    startPositions.add(ChunkPos.pack(candidate.x() + offsetX, candidate.z() + offsetZ));
                }
            }
        }
        return new CandidatePlan(List.copyOf(candidates), Set.copyOf(startPositions));
    }

    private record CandidatePlan(List<ChunkPos> candidates, Set<Long> startPositions) {
    }

    @FunctionalInterface
    interface ChunkBiomeTest {
        boolean test(int chunkX, int chunkZ);
    }
}
