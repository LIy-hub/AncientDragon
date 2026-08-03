package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Bounds;
import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Geometry;
import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Mode;
import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Progress;
import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Status;
import com.liy.ancientdragon.worldgen.SacredMountainShape.RouteNode;
import com.liy.ancientdragon.worldgen.SacredMountainShape.SurfaceMaterial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ChunkPos;

/** Restart-safe, chunk-major builder for preview and full Sacred Mountain authoring drafts. */
public final class SacredMountainBuildService {
    private static final long TICK_TIME_BUDGET_NANOS = 30_000_000L;
    private static final int BLOCK_CHANGE_BUDGET = 262_144;
    private static final int CHUNK_COMPLETION_BUDGET = 2;
    private static final int PRECOMPUTE_AHEAD = Math.max(
            2,
            Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
    private static final Set<Heightmap.Types> HEIGHTMAP_TYPES = EnumSet.of(
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.WORLD_SURFACE);
    private static final SurfaceMaterial[] SURFACE_MATERIALS = SurfaceMaterial.values();
    private static CompletableFuture<SacredMountainShape> pendingShape;
    private static long pendingSeed;
    private static SacredMountainShape cachedShape;
    private static long cachedSeed;
    private static String cachedChunkBuildId;
    private static List<ChunkPos> cachedChunks = List.of();
    private static String preparedChunkBuildId;
    private static final Map<Integer, CompletableFuture<PreparedChunk>> PREPARED_CHUNKS = new HashMap<>();
    private static final Set<Long> BULK_DIRTY_CHUNKS = new HashSet<>();
    private static int serviceTicks;
    private static int lastReportedDecile = -1;

    private SacredMountainBuildService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SacredMountainBuildService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearRuntimeCaches());
    }

    public static StartResult startPreview(ServerLevel level, BlockPos requestedCenter, long seed) {
        return start(level, requestedCenter, seed, Mode.PREVIEW);
    }

    public static StartResult startBuild(ServerLevel level, BlockPos requestedCenter, long seed) {
        return start(level, requestedCenter, seed, Mode.FULL);
    }

    public static ActionResult clearPreview(ServerLevel level) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        Optional<Geometry> geometry = data.geometry();
        if (geometry.isEmpty() || geometry.get().mode() != Mode.PREVIEW) {
            return new ActionResult(false, "No Sacred Mountain preview exists");
        }
        if (data.status() == Status.FINALIZED || data.status() == Status.CLEARING) {
            return new ActionResult(false, "Preview cannot be cleared from " + data.status().serializedName());
        }
        boolean approved = data.status() == Status.DRAFT;
        if (approved) {
            data.approveCompletedPreview();
        }
        data.beginClear();
        resetRuntimeProgress();
        return new ActionResult(true, approved
                ? "Sacred Mountain preview approved; clearing started"
                : "Incomplete Sacred Mountain preview clearing started without approval");
    }

    public static ActionResult pause(ServerLevel level) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        if (data.geometry().isEmpty()
                || (data.status() != Status.PREPARING && data.status() != Status.BUILDING)) {
            return new ActionResult(false, "No running Sacred Mountain task can be paused");
        }
        data.pause();
        return new ActionResult(true, "Sacred Mountain task paused at " + progressText(data));
    }

    public static ActionResult resume(ServerLevel level) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        if (data.geometry().isEmpty() || data.status() != Status.PAUSED) {
            return new ActionResult(false, "No paused Sacred Mountain task exists");
        }
        Geometry geometry = data.geometry().orElseThrow();
        if (geometry.shapeVersion() != SacredMountainShape.VERSION) {
            return new ActionResult(
                    false,
                    "Legacy Sacred Mountain v" + geometry.shapeVersion()
                            + " cannot resume with shape v" + SacredMountainShape.VERSION
                            + "; use protected reset instead");
        }
        data.resume();
        scheduleShape(geometry.seed());
        return new ActionResult(true, "Sacred Mountain task resumed");
    }

    public static ActionResult reset(ServerLevel level, String buildId) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        Optional<Geometry> geometry = data.geometry();
        if (geometry.isEmpty() || geometry.get().mode() != Mode.FULL) {
            return new ActionResult(false, "No full Sacred Mountain draft exists");
        }
        if (!geometry.get().buildId().equals(buildId)) {
            return new ActionResult(false, "Build id does not match the active authoring manifest");
        }
        if (data.status() == Status.FINALIZED) {
            return new ActionResult(false, "Finalized Sacred Mountain drafts cannot be reset");
        }
        if (data.status() == Status.CLEARING) {
            return new ActionResult(false, "Sacred Mountain reset is already running");
        }
        if (data.status() != Status.DRAFT) {
            return new ActionResult(false, "Only a completed DRAFT Sacred Mountain can be reset");
        }
        data.beginClear();
        resetRuntimeProgress();
        return new ActionResult(true, "Protected reset started for " + buildId);
    }

    public static ActionResult finalizeDraft(ServerLevel level) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        Optional<Geometry> geometry = data.geometry();
        if (geometry.isEmpty() || geometry.get().mode() != Mode.FULL || data.status() != Status.DRAFT) {
            return new ActionResult(false, "Only a completed full Sacred Mountain draft can be finalized");
        }
        data.finalizeDraft();
        return new ActionResult(
                true,
                "Finalized " + geometry.get().buildId()
                        + "; automated reset is now permanently disabled for this draft");
    }

    public static String status(ServerLevel level) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        if (data.geometry().isEmpty()) {
            return "No Sacred Mountain authoring manifest exists; approved_preview=" + approvalText(data);
        }
        Geometry geometry = data.geometry().orElseThrow();
        return geometry.shortDescription()
                + " status=" + data.status().serializedName()
                + " " + progressText(data)
                + " approved_preview=" + approvalText(data)
                + " anchor=" + format(geometry.dragonAnchor())
                + " crop=" + format(geometry.cropBounds().minimum())
                + ".." + format(geometry.cropBounds().maximum());
    }

    static List<ChunkPos> chunksFor(Geometry geometry) {
        Bounds bounds = geometry.cropBounds();
        int minimumChunkX = bounds.minimum().getX() >> 4;
        int maximumChunkX = bounds.maximum().getX() >> 4;
        int minimumChunkZ = bounds.minimum().getZ() >> 4;
        int maximumChunkZ = bounds.maximum().getZ() >> 4;
        int centerChunkX = geometry.center().getX() >> 4;
        int centerChunkZ = geometry.center().getZ() >> 4;
        List<ChunkPos> chunks = new ArrayList<>();
        for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        chunks.sort(Comparator
                .comparingLong((ChunkPos position) -> squaredDistance(
                        position.x(), position.z(), centerChunkX, centerChunkZ))
                .thenComparingInt(ChunkPos::z)
                .thenComparingInt(ChunkPos::x));
        return List.copyOf(chunks);
    }

    private static StartResult start(ServerLevel level, BlockPos requestedCenter, long seed, Mode mode) {
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(level);
        if (!data.isIdle()) {
            return new StartResult(
                    false,
                    "Clear or reset the existing Sacred Mountain manifest before starting another draft");
        }
        if (mode == Mode.FULL && !data.approvedPreview()
                .filter(approved -> approved.shapeVersion() == SacredMountainShape.VERSION
                        && approved.seed() == seed)
                .isPresent()) {
            return new StartResult(
                    false,
                    "Complete and clear a Sacred Mountain v" + SacredMountainShape.VERSION
                            + " preview with seed " + seed
                            + " before starting the full draft");
        }
        int centerX = requestedCenter.getX();
        int centerZ = requestedCenter.getZ();
        int baseY = level.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ) - 1;
        int scale = mode.scale();
        Optional<String> invalidSite = validateFlatAuthoringSite(level, centerX, baseY, centerZ, scale);
        if (invalidSite.isPresent()) {
            return new StartResult(false, invalidSite.get());
        }
        int maximumY = baseY + ceilDiv(SacredMountainShape.MAX_RELATIVE_HEIGHT, scale);
        int clearanceTop = baseY + ceilDiv(
                SacredMountainShape.NEST_HEIGHT + SacredMountainShape.NEST_CLEARANCE_HEIGHT, scale);
        if (Math.max(maximumY, clearanceTop) >= level.getMaxY() - 8) {
            return new StartResult(
                    false,
                    "Authoring surface is too high: the dragon clearance would exceed build height");
        }

        BlockPos center = new BlockPos(centerX, baseY, centerZ);
        Bounds cropBounds = bounds(center, scale);
        List<BlockPos> routeNodes = new ArrayList<>();
        for (RouteNode node : SacredMountainShape.fixedRouteNodes()) {
            routeNodes.add(new BlockPos(
                    centerX + Math.floorDiv(node.x(), scale),
                    baseY + ceilDiv(node.height(), scale),
                    centerZ + Math.floorDiv(node.z(), scale)));
        }
        BlockPos anchor = new BlockPos(
                centerX + Math.floorDiv(SacredMountainShape.NEST_CENTER_X, scale),
                baseY + ceilDiv(SacredMountainShape.NEST_HEIGHT, scale)
                        + (mode == Mode.FULL ? SacredMountainShape.DRAGON_ANCHOR_LIFT : 1),
                centerZ + Math.floorDiv(SacredMountainShape.NEST_CENTER_Z, scale));
        Geometry geometry = new Geometry(
                UUID.randomUUID().toString(),
                level.dimension().identifier().toString(),
                center,
                seed,
                SacredMountainShape.VERSION,
                mode,
                routeNodes,
                anchor,
                cropBounds,
                0);
        data.begin(geometry);
        resetRuntimeProgress();
        scheduleShape(seed);
        return new StartResult(true, "Sacred Mountain " + mode.serializedName()
                + " queued: " + geometry.shortDescription()
                + " anchor=" + format(anchor));
    }

    private static void tick(MinecraftServer server) {
        serviceTicks++;
        SacredMountainAuthoringData data = SacredMountainAuthoringData.get(server.overworld());
        Optional<Geometry> optionalGeometry = data.geometry();
        if (optionalGeometry.isEmpty()) {
            return;
        }
        Geometry geometry = optionalGeometry.get();
        if (data.status() == Status.PAUSED
                || data.status() == Status.DRAFT
                || data.status() == Status.FINALIZED) {
            return;
        }
        ServerLevel level = findLevel(server, geometry.dimension());
        if (level == null) {
            if (data.status() == Status.BUILDING || data.status() == Status.PREPARING) {
                data.pause();
            }
            return;
        }
        if (data.status() == Status.CLEARING) {
            processClear(server, level, data, geometry);
            return;
        }
        if (geometry.shapeVersion() != SacredMountainShape.VERSION) {
            data.pause();
            notifyAll(server, "Legacy Sacred Mountain v" + geometry.shapeVersion()
                    + " task paused; shape v" + SacredMountainShape.VERSION
                    + " requires protected reset and a new preview");
            return;
        }

        SacredMountainShape shape;
        try {
            shape = preparedShape(geometry.seed());
        } catch (RuntimeException exception) {
            data.pause();
            pendingShape = null;
            notifyAll(server, "Sacred Mountain shape preparation failed; task paused: "
                    + exception.getMessage());
            return;
        }
        if (shape == null) {
            return;
        }
        if (data.status() == Status.PREPARING) {
            data.beginPlacement();
        }
        if (data.status() == Status.BUILDING) {
            try {
                processBuild(server, level, data, geometry, shape);
            } catch (RuntimeException exception) {
                data.pause();
                PREPARED_CHUNKS.clear();
                notifyAll(server, "Sacred Mountain chunk preparation failed; task paused: "
                        + exception.getMessage());
            }
        }
    }

    private static void processBuild(
            MinecraftServer server,
            ServerLevel level,
            SacredMountainAuthoringData data,
            Geometry geometry,
            SacredMountainShape shape) {
        List<ChunkPos> chunks = chunksForCached(geometry);
        Progress progress = data.progress();
        int chunkIndex = progress.chunkIndex();
        int columnIndex = progress.columnIndex();
        int relativeY = progress.relativeY();
        int changed = 0;
        int examined = 0;
        int completedThisTick = 0;
        boolean budgetExpired = false;
        long deadline = System.nanoTime() + TICK_TIME_BUDGET_NANOS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Set<LevelChunk> touchedChunks = new LinkedHashSet<>();
        Set<LevelChunk> completedChunks = new LinkedHashSet<>();

        prepareChunksAhead(geometry, shape, chunks, chunkIndex);

        while (chunkIndex < chunks.size()
                && changed < BLOCK_CHANGE_BUDGET
                && completedThisTick < CHUNK_COMPLETION_BUDGET
                && !budgetExpired) {
            ChunkPos chunk = chunks.get(chunkIndex);
            if (columnIndex >= 256) {
                if (BULK_DIRTY_CHUNKS.remove(chunk.pack())) {
                    completedChunks.add(level.getChunk(chunk.x(), chunk.z()));
                }
                PREPARED_CHUNKS.remove(chunkIndex);
                chunkIndex++;
                columnIndex = 0;
                relativeY = 1;
                completedThisTick++;
                prepareChunksAhead(geometry, shape, chunks, chunkIndex);
                continue;
            }
            CompletableFuture<PreparedChunk> preparedFuture = PREPARED_CHUNKS.get(chunkIndex);
            if (preparedFuture == null || !preparedFuture.isDone()) {
                break;
            }
            PreparedChunk prepared = preparedFuture.join();
            if (prepared.chunkIndex() != chunkIndex) {
                throw new IllegalStateException("Prepared Sacred Mountain chunk index mismatch");
            }
            int worldX = chunk.getMinBlockX() + (columnIndex & 15);
            int worldZ = chunk.getMinBlockZ() + ((columnIndex >>> 4) & 15);
            if (!containsHorizontal(geometry.cropBounds(), worldX, worldZ)) {
                columnIndex++;
                relativeY = 1;
                continue;
            }
            int maximumY = prepared.maximumY(columnIndex);
            if (maximumY <= 0) {
                columnIndex++;
                relativeY = 1;
                continue;
            }
            LevelChunk levelChunk = level.getChunk(chunk.x(), chunk.z());

            while (relativeY <= maximumY
                    && changed < BLOCK_CHANGE_BUDGET) {
                if ((examined++ & 511) == 0 && System.nanoTime() >= deadline) {
                    budgetExpired = true;
                    break;
                }
                int paletteCode = prepared.paletteCode(columnIndex, relativeY);
                if (paletteCode != 0) {
                    BlockState desired = stateFor(geometry.mode(), paletteCode);
                    cursor.set(worldX, geometry.center().getY() + relativeY, worldZ);
                    if (setBlockDirect(levelChunk, cursor, desired)) {
                        changed++;
                        touchedChunks.add(levelChunk);
                        BULK_DIRTY_CHUNKS.add(chunk.pack());
                    }
                }
                relativeY++;
            }
            if (relativeY > maximumY) {
                columnIndex++;
                relativeY = 1;
            }
        }

        for (LevelChunk touchedChunk : touchedChunks) {
            if (!completedChunks.contains(touchedChunk)) {
                refreshChunkMetadata(touchedChunk);
            }
        }
        for (LevelChunk completedChunk : completedChunks) {
            finishChunk(level, completedChunk);
        }

        data.updateProgress(new Progress(chunkIndex, columnIndex, relativeY));
        if (chunkIndex >= chunks.size()) {
            data.completeDraft();
            notifyAll(server, "Sacred Mountain draft complete: " + geometry.shortDescription()
                    + " anchor=" + format(geometry.dragonAnchor()));
            lastReportedDecile = -1;
            return;
        }
        reportProgress(server, data, chunks.size());
    }

    private static void processClear(
            MinecraftServer server,
            ServerLevel level,
            SacredMountainAuthoringData data,
            Geometry geometry) {
        List<ChunkPos> chunks = chunksForCached(geometry);
        Progress progress = data.progress();
        int chunkIndex = progress.chunkIndex();
        int columnIndex = progress.columnIndex();
        int relativeY = progress.relativeY();
        int maximumY = geometry.cropBounds().maximum().getY() - geometry.center().getY();
        int changed = 0;
        int examined = 0;
        int completedThisTick = 0;
        boolean budgetExpired = false;
        long deadline = System.nanoTime() + TICK_TIME_BUDGET_NANOS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Set<LevelChunk> touchedChunks = new LinkedHashSet<>();
        Set<LevelChunk> completedChunks = new LinkedHashSet<>();

        while (chunkIndex < chunks.size()
                && changed < BLOCK_CHANGE_BUDGET
                && completedThisTick < CHUNK_COMPLETION_BUDGET
                && !budgetExpired) {
            ChunkPos chunk = chunks.get(chunkIndex);
            if (columnIndex >= 256) {
                if (BULK_DIRTY_CHUNKS.remove(chunk.pack())) {
                    completedChunks.add(level.getChunk(chunk.x(), chunk.z()));
                }
                chunkIndex++;
                columnIndex = 0;
                relativeY = 1;
                completedThisTick++;
                continue;
            }
            int worldX = chunk.getMinBlockX() + (columnIndex & 15);
            int worldZ = chunk.getMinBlockZ() + ((columnIndex >>> 4) & 15);
            if (!containsHorizontal(geometry.cropBounds(), worldX, worldZ)) {
                columnIndex++;
                relativeY = 1;
                continue;
            }
            LevelChunk levelChunk = level.getChunk(chunk.x(), chunk.z());
            while (relativeY <= maximumY
                    && changed < BLOCK_CHANGE_BUDGET) {
                if ((examined++ & 511) == 0 && System.nanoTime() >= deadline) {
                    budgetExpired = true;
                    break;
                }
                cursor.set(worldX, geometry.center().getY() + relativeY, worldZ);
                if (setBlockDirect(levelChunk, cursor, Blocks.AIR.defaultBlockState())) {
                    changed++;
                    touchedChunks.add(levelChunk);
                    BULK_DIRTY_CHUNKS.add(chunk.pack());
                }
                relativeY++;
            }
            if (relativeY > maximumY) {
                columnIndex++;
                relativeY = 1;
            }
        }

        for (LevelChunk touchedChunk : touchedChunks) {
            if (!completedChunks.contains(touchedChunk)) {
                refreshChunkMetadata(touchedChunk);
            }
        }
        for (LevelChunk completedChunk : completedChunks) {
            finishChunk(level, completedChunk);
        }

        data.updateProgress(new Progress(chunkIndex, columnIndex, relativeY));
        if (chunkIndex >= chunks.size()) {
            String clearedId = geometry.buildId();
            data.clearComplete();
            clearRuntimeCaches();
            notifyAll(server, "Sacred Mountain authoring volume cleared: " + clearedId);
            return;
        }
        reportProgress(server, data, chunks.size());
    }

    private static void prepareChunksAhead(
            Geometry geometry,
            SacredMountainShape shape,
            List<ChunkPos> chunks,
            int currentChunkIndex) {
        if (!geometry.buildId().equals(preparedChunkBuildId)) {
            preparedChunkBuildId = geometry.buildId();
            PREPARED_CHUNKS.clear();
        }
        int end = Math.min(chunks.size(), currentChunkIndex + PRECOMPUTE_AHEAD);
        for (int index = currentChunkIndex; index < end; index++) {
            int preparedIndex = index;
            PREPARED_CHUNKS.computeIfAbsent(
                    preparedIndex,
                    ignored -> CompletableFuture.supplyAsync(
                            () -> prepareChunk(geometry, shape, chunks.get(preparedIndex), preparedIndex)));
        }
    }

    static PreparedChunk prepareChunk(
            Geometry geometry,
            SacredMountainShape shape,
            ChunkPos chunk,
            int chunkIndex) {
        int scale = geometry.mode().scale();
        int maximumRelativeY = geometry.cropBounds().maximum().getY() - geometry.center().getY();
        int stride = maximumRelativeY + 1;
        short[] maximumYByColumn = new short[256];
        byte[] palette = new byte[256 * stride];

        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            int worldX = chunk.getMinBlockX() + (columnIndex & 15);
            int worldZ = chunk.getMinBlockZ() + ((columnIndex >>> 4) & 15);
            if (!containsHorizontal(geometry.cropBounds(), worldX, worldZ)) {
                continue;
            }
            int localX = (worldX - geometry.center().getX()) * scale;
            int localZ = (worldZ - geometry.center().getZ()) * scale;
            int maximumY = Math.min(
                    maximumRelativeY,
                    ceilDiv(shape.maximumSolidHeightAt(localX, localZ), scale));
            maximumYByColumn[columnIndex] = (short) maximumY;
            int columnOffset = columnIndex * stride;
            int guaranteedStoneY = Math.min(
                    maximumY,
                    Math.floorDiv(shape.guaranteedStoneHeightAt(localX, localZ), scale));
            int guaranteedPaletteCode = geometry.mode() == Mode.PREVIEW
                    ? 1
                    : SurfaceMaterial.STONE.ordinal() + 1;
            for (int relativeY = 1; relativeY <= guaranteedStoneY; relativeY++) {
                palette[columnOffset + relativeY] = (byte) guaranteedPaletteCode;
            }
            for (int relativeY = guaranteedStoneY + 1; relativeY <= maximumY; relativeY++) {
                int sourceY = Math.min(SacredMountainShape.MAX_RELATIVE_HEIGHT, relativeY * scale);
                if (!shape.isSolid(localX, sourceY, localZ)) {
                    continue;
                }
                int paletteCode = geometry.mode() == Mode.PREVIEW
                        ? previewPaletteCode(shape, localX, sourceY, localZ, scale)
                        : shape.materialAtKnownSolid(localX, sourceY, localZ).ordinal() + 1;
                palette[columnOffset + relativeY] = (byte) paletteCode;
            }
        }
        return new PreparedChunk(chunkIndex, maximumRelativeY, maximumYByColumn, palette);
    }

    private static int previewPaletteCode(
            SacredMountainShape shape, int localX, int sourceY, int localZ, int scale) {
        boolean surface = !shape.isSolid(localX, sourceY + scale, localZ);
        if (!surface) {
            return 1;
        }
        if (Math.abs(localX - SacredMountainShape.NEST_CENTER_X) <= scale * 2
                && Math.abs(localZ - SacredMountainShape.NEST_CENTER_Z) <= scale * 2) {
            return 5;
        }
        if (shape.isTakeoffMarker(localX, localZ)) {
            return 5;
        }
        if (shape.isDragonClearanceFootprint(localX, localZ)) {
            return 4;
        }
        if (shape.isRoute(localX, localZ)) {
            return 3;
        }
        return shape.surfaceSlope(localX, localZ) >= 5
                ? 2
                : 1;
    }

    private static BlockState stateFor(Mode mode, int paletteCode) {
        if (mode == Mode.PREVIEW) {
            return switch (paletteCode) {
                case 1 -> Blocks.GRAY_CONCRETE.defaultBlockState();
                case 2 -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                case 3 -> Blocks.CYAN_CONCRETE.defaultBlockState();
                case 4 -> Blocks.RED_CONCRETE.defaultBlockState();
                case 5 -> Blocks.YELLOW_CONCRETE.defaultBlockState();
                default -> throw new IllegalArgumentException("Unknown preview palette code " + paletteCode);
            };
        }
        if (paletteCode < 1 || paletteCode > SURFACE_MATERIALS.length) {
            throw new IllegalArgumentException("Unknown full palette code " + paletteCode);
        }
        return fullState(SURFACE_MATERIALS[paletteCode - 1]);
    }

    private static boolean setBlockDirect(LevelChunk chunk, BlockPos position, BlockState desired) {
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
        return true;
    }

    private static void refreshChunkMetadata(LevelChunk chunk) {
        Heightmap.primeHeightmaps(chunk, HEIGHTMAP_TYPES);
        chunk.initializeLightSources();
        chunk.setLightCorrect(false);
        chunk.markUnsaved();
    }

    private static void finishChunk(ServerLevel level, LevelChunk chunk) {
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
        List<ServerPlayer> trackingPlayers = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
        if (trackingPlayers.isEmpty()) {
            return;
        }
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunk,
                level.getChunkSource().getLightEngine(),
                null,
                null);
        for (ServerPlayer player : trackingPlayers) {
            player.connection.send(packet);
        }
    }

    private static BlockState fullState(SurfaceMaterial material) {
        return switch (material) {
            case STONE -> Blocks.STONE.defaultBlockState();
            case ANDESITE -> Blocks.ANDESITE.defaultBlockState();
            case TUFF -> Blocks.TUFF.defaultBlockState();
            case DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState();
            case CALCITE -> Blocks.CALCITE.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case BASALT -> Blocks.BASALT.defaultBlockState();
            case BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
        };
    }

    private static Optional<String> validateFlatAuthoringSite(
            ServerLevel level, int centerX, int baseY, int centerZ, int scale) {
        int radius = ceilDiv(SacredMountainShape.OUTER_RADIUS, scale) + 8;
        for (int sampleZ = -2; sampleZ <= 2; sampleZ++) {
            for (int sampleX = -2; sampleX <= 2; sampleX++) {
                int x = centerX + ((radius * sampleX) / 2);
                int z = centerZ + ((radius * sampleZ) / 2);
                int sampledY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (Math.abs(sampledY - baseY) > 1) {
                    return Optional.of(String.format(
                            Locale.ROOT,
                            "Authoring site is not superflat near %d %d: expected Y=%d, found Y=%d",
                            x, z, baseY, sampledY));
                }
            }
        }
        return Optional.empty();
    }

    private static Bounds bounds(BlockPos center, int scale) {
        int minimumOffset = Math.floorDiv(SacredMountainShape.MIN_COORDINATE, scale);
        int maximumOffset = Math.floorDiv(SacredMountainShape.MAX_COORDINATE, scale);
        int maximumY = center.getY() + ceilDiv(SacredMountainShape.MAX_RELATIVE_HEIGHT, scale);
        return new Bounds(
                new BlockPos(center.getX() + minimumOffset, center.getY() + 1, center.getZ() + minimumOffset),
                new BlockPos(center.getX() + maximumOffset, maximumY, center.getZ() + maximumOffset));
    }

    private static SacredMountainShape preparedShape(long seed) {
        if (cachedShape != null && cachedSeed == seed) {
            return cachedShape;
        }
        scheduleShape(seed);
        if (pendingShape != null && pendingSeed == seed && pendingShape.isDone()) {
            cachedShape = pendingShape.join();
            cachedSeed = seed;
            pendingShape = null;
            return cachedShape;
        }
        return null;
    }

    private static void scheduleShape(long seed) {
        if ((cachedShape != null && cachedSeed == seed)
                || (pendingShape != null && pendingSeed == seed)) {
            return;
        }
        pendingSeed = seed;
        pendingShape = CompletableFuture.supplyAsync(() -> new SacredMountainShape(seed));
    }

    private static List<ChunkPos> chunksForCached(Geometry geometry) {
        if (!geometry.buildId().equals(cachedChunkBuildId)) {
            cachedChunkBuildId = geometry.buildId();
            cachedChunks = chunksFor(geometry);
        }
        return cachedChunks;
    }

    private static void reportProgress(
            MinecraftServer server, SacredMountainAuthoringData data, int totalChunks) {
        int decile = (int) Math.floor(data.progressFraction(totalChunks) * 10.0D);
        if (decile > lastReportedDecile && serviceTicks % 20 == 0) {
            lastReportedDecile = decile;
            notifyAll(server, "Sacred Mountain " + data.status().serializedName()
                    + " " + progressText(data));
        }
    }

    private static String progressText(SacredMountainAuthoringData data) {
        Optional<Geometry> geometry = data.geometry();
        if (geometry.isEmpty()) {
            return "progress=0.0%";
        }
        int totalChunks = chunksForCached(geometry.get()).size();
        return String.format(Locale.ROOT, "progress=%.1f%% chunk=%d/%d",
                data.progressFraction(totalChunks) * 100.0D,
                Math.min(data.progress().chunkIndex(), totalChunks),
                totalChunks);
    }

    private static boolean containsHorizontal(Bounds bounds, int x, int z) {
        return x >= bounds.minimum().getX() && x <= bounds.maximum().getX()
                && z >= bounds.minimum().getZ() && z <= bounds.maximum().getZ();
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static long squaredDistance(int x, int z, int centerX, int centerZ) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return (dx * dx) + (dz * dz);
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }

    private static String format(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static String approvalText(SacredMountainAuthoringData data) {
        return data.approvedPreview()
                .map(approval -> "v" + approval.shapeVersion() + ":" + approval.seed())
                .orElse("none");
    }

    private static void notifyAll(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private static void resetRuntimeProgress() {
        cachedChunkBuildId = null;
        cachedChunks = List.of();
        preparedChunkBuildId = null;
        PREPARED_CHUNKS.clear();
        BULK_DIRTY_CHUNKS.clear();
        lastReportedDecile = -1;
    }

    private static void clearRuntimeCaches() {
        pendingShape = null;
        cachedShape = null;
        cachedChunkBuildId = null;
        cachedChunks = List.of();
        preparedChunkBuildId = null;
        PREPARED_CHUNKS.clear();
        BULK_DIRTY_CHUNKS.clear();
        lastReportedDecile = -1;
    }

    record PreparedChunk(
            int chunkIndex,
            int maximumRelativeY,
            short[] maximumYByColumn,
            byte[] palette) {
        int maximumY(int columnIndex) {
            return maximumYByColumn[columnIndex];
        }

        int paletteCode(int columnIndex, int relativeY) {
            int stride = maximumRelativeY + 1;
            return Byte.toUnsignedInt(palette[(columnIndex * stride) + relativeY]);
        }
    }

    public record StartResult(boolean started, String message) {
    }

    public record ActionResult(boolean succeeded, String message) {
    }
}
