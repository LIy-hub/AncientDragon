package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.ancientdragon.boss.AncientDragonEncounterData;
import com.liy.ancientdragon.boss.AncientDragonEncounterData.Stage;
import com.liy.ancientdragon.boss.AncientDragonBossState;
import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Bounds;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData.Job;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData.Phase;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData.Progress;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData.Status;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Column;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Run;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Tile;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.TileCoordinate;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/** Protected preflight and restart-safe placement for the extracted live Sacred Mountain asset. */
public final class SacredMountainPlacementService {
    private static final long TICK_TIME_BUDGET_NANOS = 30_000_000L;
    private static final int BLOCK_BUDGET = 262_144;
    private static final int TILE_COMPLETION_BUDGET = 2;
    private static final String AIR = "minecraft:air";
    private static final Set<Heightmap.Types> HEIGHTMAP_TYPES = EnumSet.of(
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.WORLD_SURFACE);
    private static final Map<String, BlockState> STATE_CACHE = new HashMap<>();
    private static SacredMountainStructureAsset cachedAsset;
    private static int cachedTileIndex = -1;
    private static Tile cachedTile;
    private static int serviceTicks;
    private static int lastReportedDecile = -1;

    private SacredMountainPlacementService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SacredMountainPlacementService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearRuntimeCaches());
    }

    public static ActionResult startPreflight(ServerLevel level, BlockPos requestedPosition, int quarterTurns) {
        SacredMountainPlacementData data = SacredMountainPlacementData.get(level);
        if (data.job().isPresent()) {
            return new ActionResult(false,
                    "A Sacred Mountain placement job already exists; inspect or discard it first");
        }
        SacredMountainStructureAsset asset;
        try {
            asset = asset();
        } catch (RuntimeException exception) {
            return new ActionResult(false, "Sacred Mountain asset failed validation: " + exception.getMessage());
        }
        SacredMountainStructureAsset.Manifest manifest = asset.manifest();
        int normalizedTurns = Math.floorMod(quarterTurns, 4);
        int anchorX = Math.floorDiv(requestedPosition.getX(), 16) * 16;
        int anchorZ = Math.floorDiv(requestedPosition.getZ(), 16) * 16;
        int anchorY = level.getHeight(Heightmap.Types.WORLD_SURFACE, anchorX, anchorZ) - 1;
        BlockPos anchor = new BlockPos(anchorX, anchorY, anchorZ);
        Bounds bounds = transformedBounds(anchor, manifest, normalizedTurns);
        if (bounds.minimum().getY() < level.getMinY() || bounds.maximum().getY() >= level.getMaxY()) {
            return new ActionResult(false, "Sacred Mountain placement exceeds the dimension build height: "
                    + format(bounds.minimum()) + ".." + format(bounds.maximum()));
        }
        BlockPos dragonRest = transform(anchor, manifest.dragonRestX(), manifest.dragonRestY(),
                manifest.dragonRestZ(), normalizedTurns);
        float dragonYaw = normalizeYaw(manifest.dragonYaw() + normalizedTurns * 90.0F);
        Job job = new Job(
                UUID.randomUUID().toString(),
                manifest.assetId(),
                manifest.structureSha256(),
                level.dimension().identifier().toString(),
                anchor,
                normalizedTurns,
                bounds,
                dragonRest,
                dragonYaw,
                Status.PREFLIGHT,
                Phase.PREFLIGHT,
                Progress.ZERO,
                "",
                0L,
                0L,
                "");
        data.begin(job);
        resetRuntimeProgress();
        return new ActionResult(true, "Sacred Mountain preflight started: id=" + job.placementId()
                + " anchor=" + format(anchor)
                + " rotation=" + normalizedTurns
                + " bounds=" + format(bounds.minimum()) + ".." + format(bounds.maximum())
                + " dragon_rest=" + format(dragonRest) + " yaw=" + dragonYaw);
    }

    public static ActionResult confirmPlacement(ServerLevel level, String placementId) {
        SacredMountainPlacementData data = SacredMountainPlacementData.get(level);
        Optional<Job> optional = data.job();
        if (optional.isEmpty() || !optional.get().placementId().equals(placementId)) {
            return new ActionResult(false, "Placement id does not match the active Sacred Mountain job");
        }
        if (optional.get().status() != Status.READY) {
            return new ActionResult(false, "Sacred Mountain placement is not ready; current status="
                    + optional.get().status().serializedName());
        }
        data.beginPlacement(placementId);
        resetRuntimeProgress();
        return new ActionResult(true, "Sacred Mountain placement confirmed: " + placementId);
    }

    public static ActionResult pause(ServerLevel level) {
        SacredMountainPlacementData data = SacredMountainPlacementData.get(level);
        Optional<Job> optional = data.job();
        if (optional.isEmpty()
                || (optional.get().status() != Status.PREFLIGHT && optional.get().status() != Status.PLACING)) {
            return new ActionResult(false, "No running Sacred Mountain placement task can be paused");
        }
        data.pause("paused by administrator");
        return new ActionResult(true, "Sacred Mountain placement paused at " + progressText(data));
    }

    public static ActionResult resume(ServerLevel level) {
        SacredMountainPlacementData data = SacredMountainPlacementData.get(level);
        Optional<Job> optional = data.job();
        if (optional.isEmpty() || optional.get().status() != Status.PAUSED) {
            return new ActionResult(false, "No paused Sacred Mountain placement task exists");
        }
        Job job = optional.get();
        SacredMountainStructureAsset asset;
        try {
            asset = asset();
        } catch (RuntimeException exception) {
            return new ActionResult(false, "Sacred Mountain asset failed validation: " + exception.getMessage());
        }
        if (!asset.manifest().assetId().equals(job.assetId())
                || !asset.manifest().structureSha256().equals(job.structureSha256())) {
            return new ActionResult(false, "The packaged Sacred Mountain asset no longer matches this job");
        }
        data.resume();
        resetRuntimeProgress();
        return new ActionResult(true, "Sacred Mountain placement resumed: phase=" + job.phase().serializedName());
    }

    public static ActionResult discard(ServerLevel level, String placementId) {
        SacredMountainPlacementData data = SacredMountainPlacementData.get(level);
        try {
            data.discard(placementId);
            clearRuntimeCaches();
            return new ActionResult(true, "Discarded unstarted Sacred Mountain placement " + placementId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new ActionResult(false, exception.getMessage());
        }
    }

    public static String status(ServerLevel level) {
        SacredMountainPlacementData data = SacredMountainPlacementData.get(level);
        Optional<Job> optional = data.job();
        if (optional.isEmpty()) {
            return "No Sacred Mountain placement job exists";
        }
        Job job = optional.get();
        String issue = job.issue().isBlank() ? "none" : job.issue();
        return String.format(
                Locale.ROOT,
                "id=%s status=%s phase=%s %s anchor=%s rotation=%d bounds=%s..%s dragon=%s dragon_rest=%s yaw=%.1f checked=%d placed=%d issue=%s",
                job.placementId(),
                job.status().serializedName(),
                job.phase().serializedName(),
                progressText(data),
                format(job.anchor()),
                job.quarterTurns(),
                format(job.bounds().minimum()),
                format(job.bounds().maximum()),
                job.dragonUuid().map(UUID::toString).orElse("pending"),
                format(job.dragonRest()),
                job.dragonYaw(),
                job.checkedBlocks(),
                job.placedBlocks(),
                issue);
    }

    private static void tick(MinecraftServer server) {
        serviceTicks++;
        SacredMountainPlacementData data = SacredMountainPlacementData.get(server.overworld());
        Optional<Job> optional = data.job();
        if (optional.isEmpty()) {
            return;
        }
        Job job = optional.get();
        if (job.status() == Status.COMPLETE) {
            if (job.dragonUuid().isEmpty() && serviceTicks % 20 == 0) {
                attachMissingDragon(server, data, job);
            } else if (job.dragonUuid().isPresent() && serviceTicks % 200 == 0) {
                ServerLevel level = findLevel(server, job.dimension());
                if (level != null) {
                    try {
                        AncientDragonEncounterData.get(level).bindPlacement(
                                job.placementId(), job.dimension(), job.dragonUuid().orElseThrow());
                        synchronizeEncounterStage(level, job.dragonUuid().orElseThrow());
                    } catch (RuntimeException exception) {
                        notifyAll(server, "Sacred Mountain encounter binding mismatch: " + exception.getMessage());
                    }
                }
            }
            return;
        }
        if (job.status() != Status.PREFLIGHT && job.status() != Status.PLACING) {
            return;
        }
        ServerLevel level = findLevel(server, job.dimension());
        if (level == null) {
            data.pause("target dimension is unavailable");
            return;
        }
        SacredMountainStructureAsset asset;
        try {
            asset = asset();
            if (!asset.manifest().assetId().equals(job.assetId())
                    || !asset.manifest().structureSha256().equals(job.structureSha256())) {
                throw new IllegalStateException("packaged asset hash differs from the placement manifest");
            }
            process(server, level, data, job, asset);
        } catch (RuntimeException exception) {
            String issue = "placement processing failed: " + exception.getMessage();
            if (job.status() == Status.PLACING) {
                data.pause(issue);
                notifyAll(server, "Sacred Mountain placement paused: " + exception.getMessage());
            } else {
                data.fail(issue);
                notifyAll(server, "Sacred Mountain preflight failed: " + exception.getMessage());
            }
        }
    }

    private static void process(
            MinecraftServer server,
            ServerLevel level,
            SacredMountainPlacementData data,
            Job job,
            SacredMountainStructureAsset asset) {
        List<TileCoordinate> coordinates = asset.tileCoordinates();
        Progress progress = job.progress();
        int tileIndex = progress.tileIndex();
        int columnIndex = progress.columnIndex();
        int runIndex = progress.runIndex();
        int runOffset = progress.runOffset();
        long checkedBlocks = job.checkedBlocks();
        long placedBlocks = job.placedBlocks();
        int examined = 0;
        int changed = 0;
        int completedTiles = 0;
        boolean budgetExpired = false;
        long deadline = System.nanoTime() + TICK_TIME_BUDGET_NANOS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Set<LevelChunk> completedChunks = new java.util.LinkedHashSet<>();

        while (tileIndex < coordinates.size()
                && examined < BLOCK_BUDGET
                && changed < BLOCK_BUDGET
                && completedTiles < TILE_COMPLETION_BUDGET
                && !budgetExpired) {
            TileCoordinate coordinate = coordinates.get(tileIndex);
            Tile tile = tile(asset, coordinate, tileIndex);
            if (columnIndex >= tile.columns().size()) {
                if (job.phase() == Phase.PLACEMENT) {
                    LevelChunk completed = destinationChunk(level, job, asset.manifest(), coordinate);
                    // Always finalize a completed source tile so lighting survives pause/restart.
                    completedChunks.add(completed);
                }
                tileIndex++;
                columnIndex = 0;
                runIndex = 0;
                runOffset = 0;
                completedTiles++;
                continue;
            }

            Column column = tile.columns().get(columnIndex);
            if (runIndex >= column.runs().size()) {
                columnIndex++;
                runIndex = 0;
                runOffset = 0;
                continue;
            }
            int withinTileX = columnIndex & 15;
            int withinTileZ = (columnIndex >>> 4) & 15;
            int localX = asset.manifest().minimumX() + coordinate.x() * 16 + withinTileX;
            int localZ = asset.manifest().minimumZ() + coordinate.z() * 16 + withinTileZ;

            if (job.phase() == Phase.PREFLIGHT && runIndex == 0 && runOffset == 0
                    && hasNonAir(column, tile)) {
                BlockPos supportPosition = transform(job.anchor(), localX, 0, localZ, job.quarterTurns());
                cursor.set(supportPosition);
                BlockState support = level.getBlockState(cursor);
                checkedBlocks++;
                examined++;
                if (support.isAir() || !support.getFluidState().isEmpty()
                        || !support.isCollisionShapeFullBlock(level, cursor)) {
                    Progress stopped = new Progress(tileIndex, columnIndex, runIndex, runOffset);
                    data.updateProgress(stopped, checkedBlocks, placedBlocks);
                    data.fail("unsupported footprint at " + format(cursor) + " state=" + support);
                    notifyAll(server, "Sacred Mountain preflight failed: unsupported footprint at "
                            + format(cursor));
                    return;
                }
            }

            Run run = column.runs().get(runIndex);
            String serializedState = tile.palette().get(run.paletteIndex());
            if (job.phase() == Phase.PLACEMENT && AIR.equals(serializedState)) {
                runIndex++;
                runOffset = 0;
                continue;
            }
            int runStart = runStart(column, runIndex);
            int localY = tile.minimumLocalY() + runStart + runOffset;
            BlockPos target = transform(job.anchor(), localX, localY, localZ, job.quarterTurns());
            cursor.set(target);

            if ((examined++ & 511) == 0 && System.nanoTime() >= deadline) {
                budgetExpired = true;
                continue;
            }
            BlockState existing = level.getBlockState(cursor);
            if (job.phase() == Phase.PREFLIGHT) {
                checkedBlocks++;
                if (AIR.equals(serializedState)) {
                    if (!existing.isAir()) {
                        Progress stopped = new Progress(tileIndex, columnIndex, runIndex, runOffset);
                        data.updateProgress(stopped, checkedBlocks, placedBlocks);
                        data.fail("air-volume conflict at " + format(cursor) + " state=" + existing);
                        notifyAll(server, "Sacred Mountain preflight failed: air-volume conflict at "
                                + format(cursor));
                        return;
                    }
                } else {
                    BlockState desired = parseState(level, serializedState, job.quarterTurns());
                    if (!existing.isAir() && !existing.equals(desired)) {
                        Progress stopped = new Progress(tileIndex, columnIndex, runIndex, runOffset);
                        data.updateProgress(stopped, checkedBlocks, placedBlocks);
                        data.fail("block conflict at " + format(cursor) + " existing=" + existing
                                + " desired=" + desired);
                        notifyAll(server, "Sacred Mountain preflight failed: block conflict at "
                                + format(cursor));
                        return;
                    }
                }
            } else {
                BlockState desired = parseState(level, serializedState, job.quarterTurns());
                if (!existing.isAir() && !existing.equals(desired)) {
                    Progress stopped = new Progress(tileIndex, columnIndex, runIndex, runOffset);
                    data.updateProgress(stopped, checkedBlocks, placedBlocks);
                    data.pause("placement conflict at " + format(cursor) + " existing=" + existing
                            + " desired=" + desired);
                    notifyAll(server, "Sacred Mountain placement paused by a new conflict at "
                            + format(cursor));
                    return;
                }
                if (!existing.equals(desired)) {
                    LevelChunk chunk = level.getChunk(cursor.getX() >> 4, cursor.getZ() >> 4);
                    if (setBlock(level, chunk, cursor, desired)) {
                        changed++;
                        placedBlocks++;
                    }
                }
            }

            runOffset++;
            if (runOffset >= run.length()) {
                runIndex++;
                runOffset = 0;
            }
        }

        for (LevelChunk completed : completedChunks) {
            finishChunk(level, completed);
        }

        Progress updated = new Progress(tileIndex, columnIndex, runIndex, runOffset);
        data.updateProgress(updated, checkedBlocks, placedBlocks);
        if (tileIndex >= coordinates.size()) {
            if (job.phase() == Phase.PREFLIGHT) {
                data.markReady();
                notifyAll(server, "Sacred Mountain preflight passed: id=" + job.placementId()
                        + "; confirm with /ancientdragon structure place " + job.placementId() + " confirm");
            } else {
                UUID dragonUuid = spawnOrAdoptDragon(level, job);
                AncientDragonEncounterData.get(level).bindPlacement(job.placementId(), job.dimension(), dragonUuid);
                data.complete(dragonUuid);
                notifyAll(server, "Sacred Mountain placement complete: id=" + job.placementId()
                        + " dragon=" + dragonUuid
                        + " dragon_rest=" + format(job.dragonRest()) + " yaw=" + job.dragonYaw());
            }
            lastReportedDecile = -1;
            return;
        }
        reportProgress(server, data, coordinates.size());
    }

    private static Tile tile(SacredMountainStructureAsset asset, TileCoordinate coordinate, int tileIndex) {
        if (cachedTile != null && cachedTileIndex == tileIndex) {
            return cachedTile;
        }
        cachedTile = asset.loadTile(coordinate.x(), coordinate.z()).orElseThrow(
                () -> new IllegalStateException("Manifest references an empty tile " + coordinate));
        cachedTileIndex = tileIndex;
        return cachedTile;
    }

    private static void attachMissingDragon(
            MinecraftServer server, SacredMountainPlacementData data, Job job) {
        ServerLevel level = findLevel(server, job.dimension());
        if (level == null) {
            return;
        }
        try {
            var boundEncounter = AncientDragonEncounterData.get(level).encounter()
                    .filter(encounter -> encounter.placementId().equals(job.placementId()));
            if (boundEncounter.isPresent()) {
                UUID recordedUuid = boundEncounter.get().dragonUuid();
                data.attachDragonToCompletedPlacement(recordedUuid);
                notifyAll(server, "Restored Sacred Mountain dragon binding " + recordedUuid
                        + " without creating a duplicate entity");
                return;
            }
            UUID dragonUuid = spawnOrAdoptDragon(level, job);
            AncientDragonEncounterData.get(level).bindPlacement(job.placementId(), job.dimension(), dragonUuid);
            data.attachDragonToCompletedPlacement(dragonUuid);
            notifyAll(server, "Attached missing Ancient Dragon " + dragonUuid
                    + " to completed Sacred Mountain " + job.placementId());
        } catch (RuntimeException exception) {
            notifyAll(server, "Could not attach the Sacred Mountain dragon: " + exception.getMessage());
        }
    }

    private static UUID spawnOrAdoptDragon(ServerLevel level, Job job) {
        level.getChunk(job.dragonRest().getX() >> 4, job.dragonRest().getZ() >> 4);
        return AncientDragonSpawnService.spawnOrAdopt(
                level, job.dragonRest(), job.dragonYaw(), job.quarterTurns());
    }

    private static void synchronizeEncounterStage(ServerLevel level, UUID dragonUuid) {
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof AncientDragonEntity dragon) || !dragon.getUUID().equals(dragonUuid)) {
                continue;
            }
            AncientDragonBossState state = dragon.bossState();
            Stage stage = switch (state) {
                case DORMANT -> Stage.DORMANT;
                case DEATH_RETURNING, DYING -> Stage.DYING;
                case CORPSE -> Stage.CORPSE;
                default -> Stage.ACTIVE;
            };
            AncientDragonEncounterData.get(level).updateStage(dragonUuid, stage, null);
            return;
        }
    }

    private static boolean hasNonAir(Column column, Tile tile) {
        for (Run run : column.runs()) {
            if (!AIR.equals(tile.palette().get(run.paletteIndex()))) {
                return true;
            }
        }
        return false;
    }

    private static int runStart(Column column, int runIndex) {
        int start = 0;
        for (int index = 0; index < runIndex; index++) {
            start += column.runs().get(index).length();
        }
        return start;
    }

    static BlockState parseState(ServerLevel level, String serialized, int quarterTurns) {
        int normalizedTurns = Math.floorMod(quarterTurns, 4);
        String cacheKey = normalizedTurns + "\u0000" + serialized;
        BlockState cached = STATE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            BlockState state = BlockStateParser.parseForBlock(
                    level.registryAccess().lookupOrThrow(Registries.BLOCK), serialized, false).blockState();
            state = state.rotate(rotation(normalizedTurns));
            STATE_CACHE.put(cacheKey, state);
            return state;
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("Invalid extracted block state " + serialized, exception);
        }
    }

    private static Rotation rotation(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new AssertionError();
        };
    }

    static boolean setBlock(
            ServerLevel level, LevelChunk chunk, BlockPos position, BlockState desired) {
        if (desired.hasBlockEntity() || !desired.getFluidState().isEmpty()) {
            return level.setBlock(position, desired, 18);
        }
        int sectionIndex = chunk.getSectionIndex(position.getY());
        LevelChunkSection section = chunk.getSection(sectionIndex);
        int localX = position.getX() & 15;
        int localY = position.getY() & 15;
        int localZ = position.getZ() & 15;
        BlockState previous = section.getBlockState(localX, localY, localZ);
        if (previous.equals(desired)) {
            return false;
        }
        if (previous.hasBlockEntity()) {
            chunk.removeBlockEntity(position);
        }
        section.setBlockState(localX, localY, localZ, desired, false);
        chunk.markUnsaved();
        return true;
    }

    static BlockPos transform(BlockPos anchor, int localX, int localY, int localZ, int quarterTurns) {
        int rotatedX;
        int rotatedZ;
        switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> {
                rotatedX = localX;
                rotatedZ = localZ;
            }
            case 1 -> {
                rotatedX = -localZ - 1;
                rotatedZ = localX;
            }
            case 2 -> {
                rotatedX = -localX - 1;
                rotatedZ = -localZ - 1;
            }
            case 3 -> {
                rotatedX = localZ;
                rotatedZ = -localX - 1;
            }
            default -> throw new AssertionError();
        }
        return anchor.offset(rotatedX, localY, rotatedZ);
    }

    static Bounds transformedBounds(
            BlockPos anchor, SacredMountainStructureAsset.Manifest manifest, int quarterTurns) {
        BlockPos[] corners = {
                transform(anchor, manifest.minimumX(), manifest.minimumY(), manifest.minimumZ(), quarterTurns),
                transform(anchor, manifest.minimumX(), manifest.minimumY(), manifest.maximumZ(), quarterTurns),
                transform(anchor, manifest.maximumX(), manifest.maximumY(), manifest.minimumZ(), quarterTurns),
                transform(anchor, manifest.maximumX(), manifest.maximumY(), manifest.maximumZ(), quarterTurns)
        };
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (BlockPos corner : corners) {
            minimumX = Math.min(minimumX, corner.getX());
            minimumY = Math.min(minimumY, corner.getY());
            minimumZ = Math.min(minimumZ, corner.getZ());
            maximumX = Math.max(maximumX, corner.getX());
            maximumY = Math.max(maximumY, corner.getY());
            maximumZ = Math.max(maximumZ, corner.getZ());
        }
        return new Bounds(new BlockPos(minimumX, minimumY, minimumZ),
                new BlockPos(maximumX, maximumY, maximumZ));
    }

    private static LevelChunk destinationChunk(
            ServerLevel level,
            Job job,
            SacredMountainStructureAsset.Manifest manifest,
            TileCoordinate coordinate) {
        int localX = manifest.minimumX() + coordinate.x() * 16;
        int localZ = manifest.minimumZ() + coordinate.z() * 16;
        BlockPos destination = transform(job.anchor(), localX, manifest.minimumY(), localZ, job.quarterTurns());
        return level.getChunk(destination.getX() >> 4, destination.getZ() >> 4);
    }

    private static void refreshChunkMetadata(LevelChunk chunk) {
        Heightmap.primeHeightmaps(chunk, HEIGHTMAP_TYPES);
        chunk.initializeLightSources();
        chunk.setLightCorrect(false);
        chunk.markUnsaved();
    }

    static void finishChunk(ServerLevel level, LevelChunk chunk) {
        refreshChunkMetadata(chunk);
        var lightEngine = level.getChunkSource().getLightEngine();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            int sectionY = chunk.getSectionYFromSectionIndex(index);
            lightEngine.updateSectionStatus(
                    SectionPos.of(chunk.getPos(), sectionY),
                    sections[index].hasOnlyAir());
        }
        lightEngine.lightChunk(chunk, false).thenRunAsync(
                () -> sendFullChunk(level, chunk),
                level.getServer());
    }

    private static void sendFullChunk(ServerLevel level, LevelChunk chunk) {
        List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (players.isEmpty()) {
            return;
        }
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunk, level.getChunkSource().getLightEngine(), null, null);
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }

    private static void reportProgress(
            MinecraftServer server, SacredMountainPlacementData data, int totalTiles) {
        Optional<Job> optional = data.job();
        if (optional.isEmpty()) {
            return;
        }
        int decile = (int) Math.floor(progressFraction(optional.get(), totalTiles) * 10.0D);
        if (decile > lastReportedDecile && serviceTicks % 20 == 0) {
            lastReportedDecile = decile;
            notifyAll(server, "Sacred Mountain placement " + optional.get().status().serializedName()
                    + " " + progressText(data));
        }
    }

    private static String progressText(SacredMountainPlacementData data) {
        Optional<Job> optional = data.job();
        if (optional.isEmpty()) {
            return "progress=0.0%";
        }
        int totalTiles = asset().tileCoordinates().size();
        Job job = optional.get();
        return String.format(
                Locale.ROOT,
                "progress=%.1f%% tile=%d/%d",
                progressFraction(job, totalTiles) * 100.0D,
                Math.min(job.progress().tileIndex(), totalTiles),
                totalTiles);
    }

    static double progressFraction(Job job, int totalTiles) {
        if (totalTiles <= 0) {
            return 1.0D;
        }
        double completed = job.progress().tileIndex() + job.progress().columnIndex() / 256.0D;
        return Math.clamp(completed / totalTiles, 0.0D, 1.0D);
    }

    private static SacredMountainStructureAsset asset() {
        if (cachedAsset == null) {
            cachedAsset = SacredMountainStructureAsset.loadDefault();
        }
        return cachedAsset;
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static String format(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static void notifyAll(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void resetRuntimeProgress() {
        cachedTileIndex = -1;
        cachedTile = null;
        STATE_CACHE.clear();
        lastReportedDecile = -1;
    }

    private static void clearRuntimeCaches() {
        cachedAsset = null;
        resetRuntimeProgress();
    }

    public record ActionResult(boolean succeeded, String message) {
    }
}
