package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData.Bounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Restart-safe state for one protected placement of the extracted Sacred Mountain asset. */
public final class SacredMountainPlacementData extends SavedData {
    public static final Codec<SacredMountainPlacementData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Job.CODEC.optionalFieldOf("job").forGetter(data -> data.job)
    ).apply(instance, SacredMountainPlacementData::new));

    public static final SavedDataType<SacredMountainPlacementData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("ancient_dragon", "sacred_mountain_placement"),
            SacredMountainPlacementData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private Optional<Job> job;

    public SacredMountainPlacementData() {
        this(Optional.empty());
    }

    private SacredMountainPlacementData(Optional<Job> job) {
        this.job = job == null ? Optional.empty() : job;
        normalizeLoadedState();
    }

    public static SacredMountainPlacementData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Job> job() {
        return job;
    }

    public void begin(Job newJob) {
        if (job.isPresent()) {
            throw new IllegalStateException("A Sacred Mountain placement job already exists");
        }
        job = Optional.of(newJob);
        setDirty();
    }

    public void updateProgress(Progress progress, long checkedBlocks, long placedBlocks) {
        Job current = requireJob();
        job = Optional.of(current.withProgress(progress, checkedBlocks, placedBlocks));
        setDirty();
    }

    public void markReady() {
        Job current = requireJob();
        if (current.status() != Status.PREFLIGHT) {
            throw new IllegalStateException("Cannot ready placement from " + current.status().serializedName());
        }
        job = Optional.of(current.withState(Status.READY, Phase.PREFLIGHT, current.progress(), ""));
        setDirty();
    }

    public void beginPlacement(String placementId) {
        Job current = requireMatching(placementId);
        if (current.status() != Status.READY) {
            throw new IllegalStateException("Cannot place from " + current.status().serializedName());
        }
        job = Optional.of(current.withState(Status.PLACING, Phase.PLACEMENT, Progress.ZERO, ""));
        setDirty();
    }

    public void pause(String issue) {
        Job current = requireJob();
        if (current.status() != Status.PREFLIGHT && current.status() != Status.PLACING) {
            throw new IllegalStateException("Cannot pause placement from " + current.status().serializedName());
        }
        job = Optional.of(current.withState(Status.PAUSED, current.phase(), current.progress(), issue));
        setDirty();
    }

    public void resume() {
        Job current = requireJob();
        if (current.status() != Status.PAUSED) {
            throw new IllegalStateException("Cannot resume placement from " + current.status().serializedName());
        }
        Status resumed = current.phase() == Phase.PREFLIGHT ? Status.PREFLIGHT : Status.PLACING;
        job = Optional.of(current.withState(resumed, current.phase(), current.progress(), ""));
        setDirty();
    }

    public void fail(String issue) {
        Job current = requireJob();
        job = Optional.of(current.withState(Status.FAILED, current.phase(), current.progress(), issue));
        setDirty();
    }

    public void complete(UUID dragonUuid) {
        Job current = requireJob();
        if (current.status() != Status.PLACING) {
            throw new IllegalStateException("Cannot complete placement from " + current.status().serializedName());
        }
        job = Optional.of(current.withState(Status.COMPLETE, Phase.PLACEMENT, current.progress(), "")
                .withDragonUuid(dragonUuid));
        setDirty();
    }

    public void attachDragonToCompletedPlacement(UUID dragonUuid) {
        Job current = requireJob();
        if (current.status() != Status.COMPLETE || current.dragonUuid().isPresent()) {
            throw new IllegalStateException("Completed placement cannot accept a dragon UUID");
        }
        job = Optional.of(current.withDragonUuid(dragonUuid));
        setDirty();
    }

    public void discard(String placementId) {
        Job current = requireMatching(placementId);
        if (current.phase() == Phase.PLACEMENT || current.status() == Status.COMPLETE) {
            throw new IllegalStateException("A started or completed placement cannot be discarded automatically");
        }
        job = Optional.empty();
        setDirty();
    }

    private Job requireJob() {
        return job.orElseThrow(() -> new IllegalStateException("Missing Sacred Mountain placement job"));
    }

    private Job requireMatching(String placementId) {
        Job current = requireJob();
        if (!current.placementId().equals(placementId)) {
            throw new IllegalArgumentException("Placement id does not match the active job");
        }
        return current;
    }

    private void normalizeLoadedState() {
        job = job.map(current -> {
            if (current.status() == Status.PREFLIGHT || current.status() == Status.PLACING) {
                return current.withState(Status.PAUSED, current.phase(), current.progress(),
                        "server restarted; resume explicitly");
            }
            return current;
        });
    }

    public enum Status {
        PREFLIGHT("preflight"),
        READY("ready"),
        PLACING("placing"),
        PAUSED("paused"),
        FAILED("failed"),
        COMPLETE("complete");

        public static final Codec<Status> CODEC = Codec.STRING.xmap(Status::parse, Status::serializedName);
        private final String serializedName;

        Status(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        private static Status parse(String value) {
            for (Status status : values()) {
                if (status.serializedName.equals(value)) {
                    return status;
                }
            }
            return PAUSED;
        }
    }

    public enum Phase {
        PREFLIGHT("preflight"),
        PLACEMENT("placement");

        public static final Codec<Phase> CODEC = Codec.STRING.xmap(Phase::parse, Phase::serializedName);
        private final String serializedName;

        Phase(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        private static Phase parse(String value) {
            return "placement".equals(value) ? PLACEMENT : PREFLIGHT;
        }
    }

    public record Progress(int tileIndex, int columnIndex, int runIndex, int runOffset) {
        public static final Progress ZERO = new Progress(0, 0, 0, 0);
        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("tile_index", 0).forGetter(Progress::tileIndex),
                Codec.INT.optionalFieldOf("column_index", 0).forGetter(Progress::columnIndex),
                Codec.INT.optionalFieldOf("run_index", 0).forGetter(Progress::runIndex),
                Codec.INT.optionalFieldOf("run_offset", 0).forGetter(Progress::runOffset)
        ).apply(instance, Progress::new));

        public Progress {
            if (tileIndex < 0 || columnIndex < 0 || columnIndex > 256
                    || runIndex < 0 || runOffset < 0) {
                throw new IllegalArgumentException("Invalid Sacred Mountain placement progress");
            }
        }
    }

    public record Job(
            String placementId,
            String assetId,
            String structureSha256,
            String dimension,
            BlockPos anchor,
            int quarterTurns,
            Bounds bounds,
            BlockPos dragonRest,
            float dragonYaw,
            Status status,
            Phase phase,
            Progress progress,
            String issue,
            long checkedBlocks,
            long placedBlocks,
            String dragonUuidText) {
        public static final Codec<Job> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("placement_id").forGetter(Job::placementId),
                Codec.STRING.fieldOf("asset_id").forGetter(Job::assetId),
                Codec.STRING.fieldOf("structure_sha256").forGetter(Job::structureSha256),
                Codec.STRING.fieldOf("dimension").forGetter(Job::dimension),
                BlockPos.CODEC.fieldOf("anchor").forGetter(Job::anchor),
                Codec.INT.optionalFieldOf("quarter_turns", 0).forGetter(Job::quarterTurns),
                Bounds.CODEC.fieldOf("bounds").forGetter(Job::bounds),
                BlockPos.CODEC.fieldOf("dragon_rest").forGetter(Job::dragonRest),
                Codec.FLOAT.fieldOf("dragon_yaw").forGetter(Job::dragonYaw),
                Status.CODEC.fieldOf("status").forGetter(Job::status),
                Phase.CODEC.fieldOf("phase").forGetter(Job::phase),
                Progress.CODEC.optionalFieldOf("progress", Progress.ZERO).forGetter(Job::progress),
                Codec.STRING.optionalFieldOf("issue", "").forGetter(Job::issue),
                Codec.LONG.optionalFieldOf("checked_blocks", 0L).forGetter(Job::checkedBlocks),
                Codec.LONG.optionalFieldOf("placed_blocks", 0L).forGetter(Job::placedBlocks),
                Codec.STRING.optionalFieldOf("dragon_uuid", "").forGetter(Job::dragonUuidText)
        ).apply(instance, Job::new));

        public Job {
            if (placementId == null || placementId.isBlank() || assetId == null || assetId.isBlank()
                    || structureSha256 == null || !structureSha256.matches("[0-9a-f]{64}")
                    || dimension == null || dimension.isBlank() || anchor == null || bounds == null
                    || dragonRest == null || !Float.isFinite(dragonYaw) || status == null || phase == null
                    || progress == null || issue == null || checkedBlocks < 0L || placedBlocks < 0L
                    || dragonUuidText == null || !validOptionalUuid(dragonUuidText)) {
                throw new IllegalArgumentException("Incomplete Sacred Mountain placement job");
            }
            quarterTurns = Math.floorMod(quarterTurns, 4);
        }

        public Optional<UUID> dragonUuid() {
            return dragonUuidText.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(dragonUuidText));
        }

        Job withProgress(Progress newProgress, long newCheckedBlocks, long newPlacedBlocks) {
            return new Job(placementId, assetId, structureSha256, dimension, anchor, quarterTurns, bounds,
                    dragonRest, dragonYaw, status, phase, newProgress, issue, newCheckedBlocks, newPlacedBlocks,
                    dragonUuidText);
        }

        Job withState(Status newStatus, Phase newPhase, Progress newProgress, String newIssue) {
            return new Job(placementId, assetId, structureSha256, dimension, anchor, quarterTurns, bounds,
                    dragonRest, dragonYaw, newStatus, newPhase, newProgress, newIssue,
                    checkedBlocks, placedBlocks, dragonUuidText);
        }

        Job withDragonUuid(UUID dragonUuid) {
            return new Job(placementId, assetId, structureSha256, dimension, anchor, quarterTurns, bounds,
                    dragonRest, dragonYaw, status, phase, progress, issue,
                    checkedBlocks, placedBlocks, dragonUuid.toString());
        }

        private static boolean validOptionalUuid(String value) {
            if (value.isBlank()) {
                return true;
            }
            try {
                UUID.fromString(value);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
