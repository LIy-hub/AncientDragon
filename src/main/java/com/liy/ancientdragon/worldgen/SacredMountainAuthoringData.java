package com.liy.ancientdragon.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-owned authoring manifest and restart-safe progress for one Sacred Mountain draft. */
public final class SacredMountainAuthoringData extends SavedData {
    public static final Codec<SacredMountainAuthoringData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Geometry.CODEC.optionalFieldOf("manifest").forGetter(data -> data.geometry),
            Status.CODEC.optionalFieldOf("status", Status.IDLE).forGetter(data -> data.status),
            Progress.CODEC.optionalFieldOf("progress", Progress.ZERO).forGetter(data -> data.progress),
            Codec.LONG.optionalFieldOf("approved_preview_seed").forGetter(data -> data.approvedPreviewSeed()),
            Codec.INT.optionalFieldOf("approved_preview_shape_version", 2)
                    .forGetter(data -> data.approvedPreview
                            .map(PreviewApproval::shapeVersion)
                            .orElse(SacredMountainShape.VERSION))
    ).apply(instance, SacredMountainAuthoringData::new));

    public static final SavedDataType<SacredMountainAuthoringData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("ancient_dragon", "sacred_mountain_authoring"),
            SacredMountainAuthoringData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private Optional<Geometry> geometry;
    private Status status;
    private Progress progress;
    private Optional<PreviewApproval> approvedPreview;

    public SacredMountainAuthoringData() {
        this(Optional.empty(), Status.IDLE, Progress.ZERO, Optional.empty(), SacredMountainShape.VERSION);
    }

    private SacredMountainAuthoringData(
            Optional<Geometry> geometry,
            Status status,
            Progress progress,
            Optional<Long> approvedPreviewSeed,
            int approvedPreviewShapeVersion) {
        this.geometry = geometry == null ? Optional.empty() : geometry;
        this.status = status == null ? Status.IDLE : status;
        this.progress = progress == null ? Progress.ZERO : progress;
        this.approvedPreview = approvedPreviewSeed == null
                ? Optional.empty()
                : approvedPreviewSeed.map(seed -> new PreviewApproval(approvedPreviewShapeVersion, seed));
        normalizeLoadedState();
    }

    public static SacredMountainAuthoringData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Geometry> geometry() {
        return geometry;
    }

    public Status status() {
        return status;
    }

    public Progress progress() {
        return progress;
    }

    public Optional<Long> approvedPreviewSeed() {
        return approvedPreview.map(PreviewApproval::seed);
    }

    public Optional<PreviewApproval> approvedPreview() {
        return approvedPreview;
    }

    public boolean isIdle() {
        return status == Status.IDLE && geometry.isEmpty();
    }

    public void begin(Geometry newGeometry) {
        if (!isIdle()) {
            throw new IllegalStateException("A Sacred Mountain authoring manifest already exists");
        }
        geometry = Optional.of(newGeometry);
        status = Status.PREPARING;
        progress = Progress.ZERO;
        setDirty();
    }

    public void beginPlacement() {
        requireGeometry();
        if (status != Status.PREPARING && status != Status.PAUSED) {
            throw new IllegalStateException("Cannot begin placement from " + status.serializedName());
        }
        status = Status.BUILDING;
        setDirty();
    }

    public void updateProgress(Progress newProgress) {
        requireGeometry();
        progress = newProgress;
        setDirty();
    }

    public void pause() {
        requireGeometry();
        if (status != Status.PREPARING && status != Status.BUILDING) {
            throw new IllegalStateException("Cannot pause from " + status.serializedName());
        }
        status = Status.PAUSED;
        setDirty();
    }

    public void resume() {
        requireGeometry();
        if (status != Status.PAUSED) {
            throw new IllegalStateException("Cannot resume from " + status.serializedName());
        }
        status = Status.PREPARING;
        setDirty();
    }

    public void completeDraft() {
        requireGeometry();
        if (status != Status.BUILDING) {
            throw new IllegalStateException("Cannot complete draft from " + status.serializedName());
        }
        status = Status.DRAFT;
        setDirty();
    }

    public void beginClear() {
        Geometry current = requireGeometry();
        if (status == Status.FINALIZED || status == Status.CLEARING) {
            throw new IllegalStateException("Cannot clear from " + status.serializedName());
        }
        status = Status.CLEARING;
        progress = Progress.ZERO;
        geometry = Optional.of(current);
        setDirty();
    }

    public void approveCompletedPreview() {
        Geometry current = requireGeometry();
        if (current.mode() != Mode.PREVIEW || status != Status.DRAFT) {
            throw new IllegalStateException("Only a completed preview can approve a full build");
        }
        approvedPreview = Optional.of(new PreviewApproval(current.shapeVersion(), current.seed()));
        setDirty();
    }

    public void clearComplete() {
        geometry = Optional.empty();
        status = Status.IDLE;
        progress = Progress.ZERO;
        setDirty();
    }

    public void finalizeDraft() {
        Geometry current = requireGeometry();
        if (current.mode() != Mode.FULL || status != Status.DRAFT) {
            throw new IllegalStateException("Only a completed full draft can be finalized");
        }
        status = Status.FINALIZED;
        setDirty();
    }

    public double progressFraction(int totalChunks) {
        if (totalChunks <= 0 || status == Status.IDLE) {
            return status == Status.IDLE ? 0.0D : 1.0D;
        }
        double completed = progress.chunkIndex()
                + (progress.columnIndex() / 256.0D)
                + (progress.relativeY() / (256.0D * SacredMountainShape.MAX_RELATIVE_HEIGHT));
        return Math.clamp(completed / totalChunks, 0.0D, 1.0D);
    }

    private Geometry requireGeometry() {
        return geometry.orElseThrow(() -> new IllegalStateException("Missing Sacred Mountain manifest"));
    }

    private void normalizeLoadedState() {
        if (geometry.isEmpty()) {
            status = Status.IDLE;
            progress = Progress.ZERO;
        } else if (status == Status.IDLE) {
            status = Status.PAUSED;
        }
    }

    public enum Mode {
        PREVIEW(4, "preview"),
        FULL(1, "full");

        public static final Codec<Mode> CODEC = Codec.STRING.xmap(Mode::parse, Mode::serializedName);

        private final int scale;
        private final String serializedName;

        Mode(int scale, String serializedName) {
            this.scale = scale;
            this.serializedName = serializedName;
        }

        public int scale() {
            return scale;
        }

        public String serializedName() {
            return serializedName;
        }

        static Mode parse(String value) {
            for (Mode mode : values()) {
                if (mode.serializedName.equals(value)) {
                    return mode;
                }
            }
            return FULL;
        }
    }

    public enum Status {
        IDLE("idle"),
        PREPARING("preparing"),
        BUILDING("building"),
        PAUSED("paused"),
        DRAFT("draft"),
        CLEARING("clearing"),
        FINALIZED("finalized");

        public static final Codec<Status> CODEC = Codec.STRING.xmap(Status::parse, Status::serializedName);

        private final String serializedName;

        Status(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        static Status parse(String value) {
            for (Status status : values()) {
                if (status.serializedName.equals(value)) {
                    return status;
                }
            }
            return IDLE;
        }
    }

    public record Bounds(BlockPos minimum, BlockPos maximum) {
        public static final Codec<Bounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("minimum").forGetter(Bounds::minimum),
                BlockPos.CODEC.fieldOf("maximum").forGetter(Bounds::maximum)
        ).apply(instance, Bounds::new));

        public Bounds {
            if (minimum == null || maximum == null
                    || minimum.getX() > maximum.getX()
                    || minimum.getY() > maximum.getY()
                    || minimum.getZ() > maximum.getZ()) {
                throw new IllegalArgumentException("Invalid authoring bounds");
            }
        }
    }

    public record Geometry(
            String buildId,
            String dimension,
            BlockPos center,
            long seed,
            int shapeVersion,
            Mode mode,
            List<BlockPos> routeNodes,
            BlockPos dragonAnchor,
            Bounds cropBounds,
            int quarterTurns) {
        public static final Codec<Geometry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("build_id").forGetter(Geometry::buildId),
                Codec.STRING.fieldOf("dimension").forGetter(Geometry::dimension),
                BlockPos.CODEC.fieldOf("center").forGetter(Geometry::center),
                Codec.LONG.fieldOf("seed").forGetter(Geometry::seed),
                Codec.INT.optionalFieldOf("shape_version", 2).forGetter(Geometry::shapeVersion),
                Mode.CODEC.fieldOf("mode").forGetter(Geometry::mode),
                BlockPos.CODEC.listOf().fieldOf("route_nodes").forGetter(Geometry::routeNodes),
                BlockPos.CODEC.fieldOf("dragon_anchor").forGetter(Geometry::dragonAnchor),
                Bounds.CODEC.fieldOf("crop_bounds").forGetter(Geometry::cropBounds),
                Codec.INT.optionalFieldOf("quarter_turns", 0).forGetter(Geometry::quarterTurns)
        ).apply(instance, Geometry::new));

        public Geometry {
            if (buildId == null || buildId.isBlank() || dimension == null || dimension.isBlank() || center == null
                    || shapeVersion < 1 || mode == null || routeNodes == null
                    || dragonAnchor == null || cropBounds == null) {
                throw new IllegalArgumentException("Incomplete Sacred Mountain manifest");
            }
            routeNodes = List.copyOf(routeNodes);
            quarterTurns = Math.floorMod(quarterTurns, 4);
        }

        public String shortDescription() {
            return String.format(
                    Locale.ROOT,
                    "id=%s shape=v%d mode=%s center=%d %d %d seed=%d",
                    buildId, shapeVersion, mode.serializedName(), center.getX(), center.getY(), center.getZ(), seed);
        }
    }

    public record PreviewApproval(int shapeVersion, long seed) {
        public PreviewApproval {
            if (shapeVersion < 1) {
                throw new IllegalArgumentException("Invalid Sacred Mountain preview shape version");
            }
        }
    }

    public record Progress(int chunkIndex, int columnIndex, int relativeY) {
        public static final Progress ZERO = new Progress(0, 0, 1);
        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("chunk_index", 0).forGetter(Progress::chunkIndex),
                Codec.INT.optionalFieldOf("column_index", 0).forGetter(Progress::columnIndex),
                Codec.INT.optionalFieldOf("relative_y", 1).forGetter(Progress::relativeY)
        ).apply(instance, Progress::new));

        public Progress {
            if (chunkIndex < 0 || columnIndex < 0 || columnIndex > 256 || relativeY < 1) {
                throw new IllegalArgumentException("Invalid Sacred Mountain build progress");
            }
        }
    }
}
