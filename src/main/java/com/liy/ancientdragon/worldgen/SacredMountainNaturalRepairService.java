package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.worldgen.SacredMountainNaturalRepairData.Job;
import com.liy.ancientdragon.worldgen.SacredMountainNaturalRepairData.Status;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Column;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Run;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Tile;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.TileCoordinate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** One-time, restart-safe repair for saves affected by the legacy ±8-chunk structure crop. */
public final class SacredMountainNaturalRepairService {
    private static final int CURRENT_REPAIR_VERSION = 2;
    private static SacredMountainStructureAsset cachedAsset;
    private static int lastReportedDecile = -1;
    private static boolean completedRepairDragonAttempted;

    private SacredMountainNaturalRepairService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SacredMountainNaturalRepairService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            cachedAsset = null;
            lastReportedDecile = -1;
            completedRepairDragonAttempted = false;
        });
    }

    public static SacredMountainPlacementService.ActionResult start(ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return failure("Natural Sacred Mountain repair can only run in the Overworld");
        }
        SacredMountainNaturalRepairData data = SacredMountainNaturalRepairData.get(level);
        Optional<Job> existing = data.job();
        if (existing.isPresent() && existing.orElseThrow().status() == Status.RUNNING) {
            return failure("Natural Sacred Mountain repair is already running; " + status(level));
        }
        if (existing.isPresent()
                && existing.orElseThrow().status() == Status.COMPLETE
                && existing.orElseThrow().repairVersion() >= CURRENT_REPAIR_VERSION) {
            return failure("Natural Sacred Mountain repair is already complete; " + status(level));
        }

        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var holder = registry.get(AncientDragonWorldgen.SACRED_MOUNTAIN_KEY).orElse(null);
        if (holder == null) {
            return failure("Natural Sacred Mountain is not registered in this world");
        }
        var generatorState = level.getChunkSource().getGeneratorState();
        Optional<ChunkPos> selected = SacredMountainPlacement.findSelectedChunk(
                generatorState.getLevelSeed(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                generatorState.randomState(),
                level.getSeaLevel(),
                holder.value().biomes()::contains);
        if (selected.isEmpty()) {
            return failure("No deep-ocean Sacred Mountain candidate exists in this world");
        }

        ChunkPos canonicalStart = selected.orElseThrow();
        LevelChunk startChunk = level.getChunk(canonicalStart.x(), canonicalStart.z());
        StructureStart start = startChunk.getStartForStructure(holder.value());
        if (start == null || !start.isValid()) {
            return failure("The selected natural Sacred Mountain has not generated yet");
        }
        SacredMountainStructurePiece legacyPiece = null;
        for (var rawPiece : start.getPieces()) {
            if (rawPiece instanceof SacredMountainStructurePiece piece && piece.isLegacyMonolithic()) {
                legacyPiece = piece;
                break;
            }
        }
        if (legacyPiece == null) {
            return failure("This Sacred Mountain already uses the complete 3x3 sector layout; no repair is needed");
        }

        SacredMountainStructureAsset asset = asset();
        data.begin(
                asset.manifest().structureSha256(),
                legacyPiece.canonicalAnchor(),
                legacyPiece.quarterTurns(),
                CURRENT_REPAIR_VERSION);
        lastReportedDecile = -1;
        return success("Started legacy natural Sacred Mountain repair at "
                + canonicalStart.x() + "," + canonicalStart.z()
                + "; frozen tiles and the emerged shoreline foundation will be restored over subsequent server ticks");
    }

    public static String status(ServerLevel level) {
        Optional<Job> optional = SacredMountainNaturalRepairData.get(level).job();
        if (optional.isEmpty()) {
            return "No natural Sacred Mountain repair job exists";
        }
        Job job = optional.orElseThrow();
        int totalTiles = asset().tileCoordinates().size();
        double progress = totalTiles == 0 ? 100.0D : 100.0D * job.nextTileIndex() / totalTiles;
        String issue = job.issue().isBlank() ? "none" : job.issue();
        return String.format(Locale.ROOT,
                "natural repair version=%d status=%s progress=%.1f%% tile=%d/%d repaired_tiles=%d changed_blocks=%d issue=%s",
                job.repairVersion(), job.status().serializedName(), progress, job.nextTileIndex(), totalTiles,
                job.repairedTiles(), job.changedBlocks(), issue);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        SacredMountainNaturalRepairData data = SacredMountainNaturalRepairData.get(level);
        Optional<Job> optional = data.job();
        if (optional.isEmpty()) {
            return;
        }
        Job job = optional.orElseThrow();
        if (job.status() == Status.COMPLETE) {
            bootstrapCompletedRepair(server, level);
            return;
        }
        if (job.status() != Status.RUNNING) {
            return;
        }
        try {
            SacredMountainStructureAsset asset = asset();
            if (!asset.manifest().structureSha256().equals(job.structureSha256())) {
                throw new IllegalStateException("packaged structure hash differs from the repair job");
            }
            processOneTile(server, level, data, job, asset);
        } catch (RuntimeException exception) {
            data.fail(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            broadcast(server, "Natural Sacred Mountain repair failed: " + exception.getMessage());
        }
    }

    private static void processOneTile(
            MinecraftServer server,
            ServerLevel level,
            SacredMountainNaturalRepairData data,
            Job job,
            SacredMountainStructureAsset asset) {
        List<TileCoordinate> coordinates = asset.tileCoordinates();
        int tileIndex = job.nextTileIndex();
        int repairedTiles = job.repairedTiles();
        long changedBlocks = job.changedBlocks();

        while (tileIndex < coordinates.size()) {
            TileCoordinate coordinate = coordinates.get(tileIndex++);
            ChunkPos destination = destinationChunk(job, asset.manifest(), coordinate);
            ChunkPos canonical = ChunkPos.containing(job.anchor());
            int deltaX = destination.x() - canonical.x();
            int deltaZ = destination.z() - canonical.z();
            if (job.repairVersion() < CURRENT_REPAIR_VERSION
                    && Math.abs(deltaX) <= 8 && Math.abs(deltaZ) <= 8) {
                continue; // The legacy centre start generated this 17x17-chunk area correctly.
            }

            Tile tile = asset.loadTile(coordinate.x(), coordinate.z()).orElseThrow();
            LevelChunk chunk = level.getChunk(destination.x(), destination.z());
            changedBlocks += placeTile(level, chunk, job, asset.manifest(), coordinate, tile);
            repairedTiles++;
            SacredMountainPlacementService.finishChunk(level, chunk);
            data.advance(tileIndex, repairedTiles, changedBlocks);
            reportProgress(server, tileIndex, coordinates.size());
            return;
        }

        data.advance(tileIndex, repairedTiles, changedBlocks);
        data.complete();
        lastReportedDecile = -1;
        broadcast(server, "Natural Sacred Mountain repair complete: processed " + repairedTiles
                + " tiles and changed " + changedBlocks + " blocks");
        bootstrapCompletedRepair(server, level);
    }

    static Optional<SacredMountainStructurePiece> completedLegacyPiece(ServerLevel level) {
        Optional<Job> optional = SacredMountainNaturalRepairData.get(level).job();
        if (optional.isEmpty() || optional.orElseThrow().status() != Status.COMPLETE) {
            return Optional.empty();
        }
        Job job = optional.orElseThrow();
        SacredMountainStructureAsset structureAsset = asset();
        if (!structureAsset.manifest().structureSha256().equals(job.structureSha256())) {
            return Optional.empty();
        }
        return Optional.of(new SacredMountainStructurePiece(
                job.anchor(),
                job.quarterTurns(),
                SacredMountainStructurePiece.boundsFor(
                        job.anchor(), structureAsset.manifest(), job.quarterTurns())));
    }

    private static void bootstrapCompletedRepair(MinecraftServer server, ServerLevel level) {
        if (completedRepairDragonAttempted) {
            return;
        }
        completedRepairDragonAttempted = true;
        SacredMountainStructurePiece piece = completedLegacyPiece(level).orElse(null);
        if (piece == null) {
            return;
        }
        if (SacredMountainNaturalDragonSpawner.ensureNaturalDragon(level, piece)) {
            broadcast(server, "Bound the repaired natural Sacred Mountain to its formal Ancient Dragon");
        } else {
            broadcast(server, "Could not bind the repaired natural Sacred Mountain dragon; check the server log");
        }
    }

    private static long placeTile(
            ServerLevel level,
            LevelChunk chunk,
            Job job,
            SacredMountainStructureAsset.Manifest manifest,
            TileCoordinate coordinate,
            Tile tile) {
        long changed = 0L;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int columnIndex = 0; columnIndex < tile.columns().size(); columnIndex++) {
            Column column = tile.columns().get(columnIndex);
            int localX = manifest.minimumX() + coordinate.x() * manifest.tileSize() + (columnIndex & 15);
            int localZ = manifest.minimumZ() + coordinate.z() * manifest.tileSize() + ((columnIndex >>> 4) & 15);
            BlockPos horizontal = SacredMountainPlacementService.transform(
                    job.anchor(), localX, 0, localZ, job.quarterTurns());
            int localY = tile.minimumLocalY();
            for (Run run : column.runs()) {
                var desired = SacredMountainPlacementService.parseState(
                        level, tile.palette().get(run.paletteIndex()), job.quarterTurns());
                for (int offset = 0; offset < run.length(); offset++) {
                    cursor.set(horizontal.getX(), job.anchor().getY() + localY + offset, horizontal.getZ());
                    if (SacredMountainPlacementService.setBlock(level, chunk, cursor, desired)) {
                        changed++;
                    }
                }
                localY += run.length();
            }
            var surface = SacredMountainNaturalTerrain.topSurface(tile, column).orElse(null);
            if (surface == null) {
                continue;
            }
            int targetSurfaceY = SacredMountainNaturalTerrain.targetSurfaceY(
                    level.getSeaLevel(), localX, localZ, manifest.seed());
            int authoredSurfaceY = job.anchor().getY() + surface.localY();
            if (targetSurfaceY <= authoredSurfaceY) {
                continue;
            }
            var cap = SacredMountainNaturalTerrain.capState(SacredMountainPlacementService.parseState(
                    level, surface.serializedState(), job.quarterTurns()));
            var fill = SacredMountainNaturalTerrain.fillState();
            for (int worldY = authoredSurfaceY + 1; worldY <= targetSurfaceY; worldY++) {
                cursor.set(horizontal.getX(), worldY, horizontal.getZ());
                var desired = worldY == targetSurfaceY ? cap : fill;
                if (SacredMountainPlacementService.setBlock(level, chunk, cursor, desired)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private static ChunkPos destinationChunk(
            Job job,
            SacredMountainStructureAsset.Manifest manifest,
            TileCoordinate coordinate) {
        int localX = manifest.minimumX() + coordinate.x() * manifest.tileSize();
        int localZ = manifest.minimumZ() + coordinate.z() * manifest.tileSize();
        BlockPos destination = SacredMountainPlacementService.transform(
                job.anchor(), localX, manifest.minimumY(), localZ, job.quarterTurns());
        return ChunkPos.containing(destination);
    }

    private static void reportProgress(MinecraftServer server, int tileIndex, int totalTiles) {
        int decile = totalTiles == 0 ? 10 : tileIndex * 10 / totalTiles;
        if (decile > lastReportedDecile) {
            lastReportedDecile = decile;
            broadcast(server, "Natural Sacred Mountain repair " + (decile * 10) + "%");
        }
    }

    private static SacredMountainStructureAsset asset() {
        if (cachedAsset == null) {
            cachedAsset = SacredMountainStructureAsset.loadDefault();
        }
        return cachedAsset;
    }

    private static SacredMountainPlacementService.ActionResult success(String message) {
        return new SacredMountainPlacementService.ActionResult(true, message);
    }

    private static SacredMountainPlacementService.ActionResult failure(String message) {
        return new SacredMountainPlacementService.ActionResult(false, message);
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }
}
