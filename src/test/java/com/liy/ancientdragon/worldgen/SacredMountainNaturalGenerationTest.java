package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SacredMountainNaturalGenerationTest {
    private final SacredMountainStructureAsset asset = SacredMountainStructureAsset.loadDefault();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultOverworldAnchorPreservesTheFrozenVerticalPlacement() {
        assertEquals(-61, SacredMountainStructure.naturalAnchorY(-64));
        assertEquals(249, -61 + asset.manifest().maximumY());
    }

    @Test
    void inverseMappingRestoresEveryCornerForEveryQuarterTurn() {
        BlockPos anchor = new BlockPos(2_048, -61, -4_096);
        var manifest = asset.manifest();
        int[][] samples = {
                {manifest.minimumX(), manifest.minimumZ()},
                {manifest.minimumX(), manifest.maximumZ()},
                {manifest.maximumX(), manifest.minimumZ()},
                {manifest.maximumX(), manifest.maximumZ()},
                {manifest.dragonRestX(), manifest.dragonRestZ()}
        };

        for (int turns = 0; turns < 4; turns++) {
            for (int[] sample : samples) {
                BlockPos world = SacredMountainPlacementService.transform(
                        anchor, sample[0], 0, sample[1], turns);
                var restored = SacredMountainStructurePiece.inverseHorizontal(
                        anchor, world.getX(), world.getZ(), turns);
                assertEquals(sample[0], restored.x());
                assertEquals(sample[1], restored.z());
            }
        }
    }

    @Test
    void everySourceTileStillMapsToOneDestinationChunkAndBack() {
        BlockPos anchor = new BlockPos(2_048, -61, -4_096);
        var manifest = asset.manifest();
        for (int turns = 0; turns < 4; turns++) {
            for (int tileZ = 0; tileZ < manifest.tilesZ(); tileZ++) {
                for (int tileX = 0; tileX < manifest.tilesX(); tileX++) {
                    int sourceX = manifest.minimumX() + tileX * manifest.tileSize();
                    int sourceZ = manifest.minimumZ() + tileZ * manifest.tileSize();
                    BlockPos first = SacredMountainPlacementService.transform(anchor, sourceX, 0, sourceZ, turns);
                    ChunkPos destination = ChunkPos.containing(first);

                    for (int cornerX : new int[] {0, 15}) {
                        for (int cornerZ : new int[] {0, 15}) {
                            BlockPos world = SacredMountainPlacementService.transform(
                                    anchor, sourceX + cornerX, 0, sourceZ + cornerZ, turns);
                            assertEquals(destination, ChunkPos.containing(world));
                            var restored = SacredMountainStructurePiece.inverseHorizontal(
                                    anchor, world.getX(), world.getZ(), turns);
                            assertEquals(sourceX + cornerX, restored.x());
                            assertEquals(sourceZ + cornerZ, restored.z());
                        }
                    }
                }
            }
        }
    }

    @Test
    void nineSectorStartsCoverTheFullAssetWithinVanillasEightChunkReferenceRadius() {
        long seed = 9_842_211L;
        ChunkPos canonical = SacredMountainPlacement.candidateChunks(seed).getFirst();
        var covered = new HashSet<ChunkPos>();

        for (int offsetZ : SacredMountainPlacement.SECTOR_START_OFFSETS) {
            for (int offsetX : SacredMountainPlacement.SECTOR_START_OFFSETS) {
                ChunkPos sectorStart = new ChunkPos(canonical.x() + offsetX, canonical.z() + offsetZ);
                assertTrue(SacredMountainPlacement.isSectorStart(canonical, sectorStart));
                assertTrue(SacredMountainPlacement.isPotentialStart(seed, sectorStart.x(), sectorStart.z()));
                var bounds = SacredMountainStructure.sectorBounds(sectorStart, -61, 320);
                assertEquals(16 * 16, bounds.getXSpan());
                assertEquals(16 * 16, bounds.getZSpan());

                for (int chunkZ = sectorStart.z() - 8; chunkZ <= sectorStart.z() + 7; chunkZ++) {
                    for (int chunkX = sectorStart.x() - 8; chunkX <= sectorStart.x() + 7; chunkX++) {
                        assertTrue(Math.abs(chunkX - sectorStart.x()) <= 8);
                        assertTrue(Math.abs(chunkZ - sectorStart.z()) <= 8);
                        covered.add(new ChunkPos(chunkX, chunkZ));
                    }
                }
            }
        }

        assertEquals(48 * 48, covered.size());
        assertTrue(covered.contains(new ChunkPos(canonical.x() - 24, canonical.z() - 24)));
        assertTrue(covered.contains(new ChunkPos(canonical.x() + 23, canonical.z() + 23)));
        assertFalse(covered.contains(new ChunkPos(canonical.x() - 25, canonical.z())));
        assertFalse(covered.contains(new ChunkPos(canonical.x() + 24, canonical.z())));
    }

    @Test
    void everySectorReusesTheLegacyCenterStartRotation() {
        long seed = 9_842_211L;
        ChunkPos canonical = new ChunkPos(-374, -91);
        WorldgenRandom legacyCenterRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        legacyCenterRandom.setLargeFeatureSeed(seed, canonical.x(), canonical.z());

        assertEquals(legacyCenterRandom.nextInt(4), SacredMountainStructure.quarterTurnsFor(seed, canonical));
    }

    @Test
    void dragonRoostAlwaysHasASectorReferenceEvenWhenTheCenterCannotSeeIt() {
        ChunkPos canonical = new ChunkPos(-379, -95);
        BlockPos anchor = new BlockPos(canonical.getMinBlockX(), -61, canonical.getMinBlockZ());
        boolean foundLegacyBlindSpot = false;

        for (int turns = 0; turns < 4; turns++) {
            BlockPos rest = SacredMountainPlacementService.transform(
                    anchor,
                    asset.manifest().dragonRestX(),
                    asset.manifest().dragonRestY(),
                    asset.manifest().dragonRestZ(),
                    turns);
            ChunkPos roostChunk = ChunkPos.containing(rest);
            int centerDeltaX = Math.abs(roostChunk.x() - canonical.x());
            int centerDeltaZ = Math.abs(roostChunk.z() - canonical.z());
            foundLegacyBlindSpot |= centerDeltaX > 8 || centerDeltaZ > 8;

            boolean referencedBySector = false;
            for (int offsetZ : SacredMountainPlacement.SECTOR_START_OFFSETS) {
                for (int offsetX : SacredMountainPlacement.SECTOR_START_OFFSETS) {
                    ChunkPos sector = new ChunkPos(canonical.x() + offsetX, canonical.z() + offsetZ);
                    if (Math.abs(roostChunk.x() - sector.x()) <= 8
                            && Math.abs(roostChunk.z() - sector.z()) <= 8) {
                        referencedBySector = true;
                    }
                }
            }
            assertTrue(referencedBySector, "rotation " + turns + " left the roost unreferenced");
        }

        assertTrue(foundLegacyBlindSpot,
                "the regression fixture must include a roost outside the legacy center reference radius");
    }

    @Test
    void naturalStructureSetUsesTheUniqueDeepOceanPlacement() {
        JsonObject root = resourceJson(
                "/data/ancient_dragon/worldgen/structure_set/sacred_mountain.json");
        JsonObject placement = root.getAsJsonObject("placement");
        assertEquals("ancient_dragon:sacred_mountain", placement.get("type").getAsString());

        JsonObject tag = resourceJson(
                "/data/ancient_dragon/tags/worldgen/biome/has_structure/sacred_mountain.json");
        assertTrue(tag.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> "#minecraft:is_deep_ocean".equals(value.getAsString())));
    }

    @Test
    void uniquePlacementCandidatesAreDistinctAndStayInTheFarExplorationBand() {
        var candidates = SacredMountainPlacement.candidateChunks(9_842_211L);
        assertEquals(SacredMountainPlacement.CANDIDATE_COUNT, candidates.size());
        assertEquals(candidates.size(), candidates.stream().distinct().count());
        assertFalse(candidates.isEmpty());
        for (ChunkPos candidate : candidates) {
            double distance = Math.hypot(candidate.x(), candidate.z());
            assertTrue(distance >= SacredMountainPlacement.MINIMUM_DISTANCE_CHUNKS - 1.0D);
            assertTrue(distance <= SacredMountainPlacement.MAXIMUM_DISTANCE_CHUNKS + 1.0D);
        }
    }

    @Test
    void selectedCandidateRequiresADeepCenterAndEightExtraChunksWithoutLand() {
        ChunkPos candidate = new ChunkPos(120, -240);
        var sampled = new HashSet<ChunkPos>();
        AtomicInteger calls = new AtomicInteger();

        assertTrue(SacredMountainPlacement.hasOpenOceanClearance(
                candidate,
                (chunkX, chunkZ) -> chunkX == candidate.x() && chunkZ == candidate.z(),
                (chunkX, chunkZ) -> {
            calls.incrementAndGet();
            sampled.add(new ChunkPos(chunkX, chunkZ));
            return true;
        }));
        int radius = SacredMountainPlacement.OPEN_OCEAN_RADIUS_CHUNKS;
        int expectedSamples = 0;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + z * z <= radius * radius) {
                    expectedSamples++;
                }
            }
        }
        assertEquals(expectedSamples, sampled.size());
        assertTrue(calls.get() > sampled.size()); // The cheap coarse pass runs before the exact pass.
        assertTrue(sampled.contains(new ChunkPos(candidate.x() - 32, candidate.z())));
        assertTrue(sampled.contains(new ChunkPos(candidate.x() + 32, candidate.z())));
        assertFalse(sampled.contains(new ChunkPos(candidate.x() + 32, candidate.z() + 32)));

        assertFalse(SacredMountainPlacement.hasOpenOceanClearance(
                candidate, (chunkX, chunkZ) -> false, (chunkX, chunkZ) -> true));
        assertFalse(SacredMountainPlacement.hasOpenOceanClearance(
                candidate,
                (chunkX, chunkZ) -> true,
                (chunkX, chunkZ) -> chunkX != candidate.x() + 1 || chunkZ != candidate.z() + 1));
    }

    @Test
    void naturalShorelineExposesLowFoothillsWithoutRaisingTheFrozenPeaks() {
        int centerSurface = SacredMountainNaturalTerrain.targetSurfaceY(63, 0, 0, asset.manifest().seed());
        assertTrue(centerSurface >= 64 && centerSurface <= 70);
        assertEquals(Integer.MIN_VALUE,
                SacredMountainNaturalTerrain.targetSurfaceY(63, 383, 0, asset.manifest().seed()));
        assertEquals(249, SacredMountainStructure.naturalAnchorY(-64) + asset.manifest().maximumY());
    }

    private static JsonObject resourceJson(String path) {
        var stream = SacredMountainNaturalGenerationTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new AssertionError("Missing test resource " + path);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read test resource " + path, exception);
        }
    }
}
