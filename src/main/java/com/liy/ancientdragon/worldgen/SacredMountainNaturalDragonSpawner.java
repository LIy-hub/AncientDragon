package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.boss.AncientDragonEncounterData;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Spawns and binds the one formal dragon when the natural roost chunk becomes usable. */
public final class SacredMountainNaturalDragonSpawner {
    private static final System.Logger LOGGER = System.getLogger("Ancient Dragon Natural Spawner");
    private static final ConcurrentLinkedQueue<PendingCheck> PENDING_CHECKS = new ConcurrentLinkedQueue<>();

    private SacredMountainNaturalDragonSpawner() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> schedule(level, chunk));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PendingCheck pending;
            while ((pending = PENDING_CHECKS.poll()) != null) {
                ServerLevel level = server.getLevel(pending.dimension());
                if (level != null) {
                    tryBindNaturalDragon(level, pending.chunk());
                }
            }
        });
    }

    private static void schedule(ServerLevel level, LevelChunk chunk) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        // CHUNK_LOAD fires before the loading future itself has completed. A synchronous chunk
        // request from that callback can wait on its own future forever, so bind at tick end.
        PENDING_CHECKS.add(new PendingCheck(level.dimension(), chunk.getPos()));
    }

    static void tryBindNaturalDragon(ServerLevel level, ChunkPos loadedChunk) {
        Structure structure = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .getValue(AncientDragonWorldgen.SACRED_MOUNTAIN_KEY);
        if (structure == null) {
            return;
        }
        for (StructureStart start : level.structureManager()
                .startsForStructure(loadedChunk, candidate -> candidate == structure)) {
            if (!start.isValid()) {
                continue;
            }
            for (var rawPiece : start.getPieces()) {
                if (!(rawPiece instanceof SacredMountainStructurePiece piece)) {
                    continue;
                }
                BlockPosAndChunk roost = new BlockPosAndChunk(piece.dragonRest());
                if (!loadedChunk.equals(roost.chunk())) {
                    continue;
                }
                ensureNaturalDragon(level, piece);
                return;
            }
        }
    }

    /**
     * Ensures the formal dragon exists for a known natural mountain piece.
     *
     * <p>This direct path is intentionally independent of structure references in the roost
     * chunk. Legacy mountains may have had their outer blocks repaired without receiving the
     * matching vanilla structure reference, so looking the start up from that chunk alone is not
     * sufficient.</p>
     */
    static boolean ensureNaturalDragon(ServerLevel level, SacredMountainStructurePiece piece) {
        net.minecraft.core.BlockPos rest = piece.dragonRest();
        level.getChunk(rest.getX() >> 4, rest.getZ() >> 4);
        AncientDragonEncounterData data = AncientDragonEncounterData.get(level);
        String placementId = piece.naturalPlacementId();
        var existing = data.encounter();
        if (existing.isPresent()) {
            if (!existing.orElseThrow().placementId().equals(placementId)) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Natural Sacred Mountain {0} cannot bind because encounter {1} already exists",
                        placementId, existing.orElseThrow().placementId());
                return false;
            }
            AncientDragonSpawnService.configureBoundDragon(
                    level, existing.orElseThrow().dragonUuid(), rest, piece.quarterTurns());
            return true;
        }
        try {
            UUID dragonUuid = AncientDragonSpawnService.spawnOrAdopt(
                    level, rest, piece.dragonYaw(), piece.quarterTurns());
            data.bindPlacement(placementId, level.dimension().identifier().toString(), dragonUuid);
            LOGGER.log(System.Logger.Level.INFO,
                    "Bound natural Sacred Mountain {0} to Ancient Dragon {1} at {2}",
                    placementId, dragonUuid, rest);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not bind the natural Sacred Mountain dragon at " + rest, exception);
            return false;
        }
    }

    private record BlockPosAndChunk(net.minecraft.core.BlockPos position, ChunkPos chunk) {
        BlockPosAndChunk(net.minecraft.core.BlockPos position) {
            this(position, ChunkPos.containing(position));
        }
    }

    private record PendingCheck(ResourceKey<Level> dimension, ChunkPos chunk) {
    }
}
