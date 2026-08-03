package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SacredMountainAuthoringDataTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void authoringLifecycleResumesAndPermanentlyProtectsFinalizedDraft() {
        SacredMountainAuthoringData data = new SacredMountainAuthoringData();
        SacredMountainAuthoringData.Geometry geometry = geometry(SacredMountainAuthoringData.Mode.FULL);
        data.begin(geometry);
        assertEquals(SacredMountainAuthoringData.Status.PREPARING, data.status());
        data.beginPlacement();
        data.updateProgress(new SacredMountainAuthoringData.Progress(12, 42, 73));
        data.pause();
        assertEquals(SacredMountainAuthoringData.Status.PAUSED, data.status());
        data.resume();
        data.beginPlacement();
        assertEquals(new SacredMountainAuthoringData.Progress(12, 42, 73), data.progress());
        data.completeDraft();
        data.finalizeDraft();
        assertEquals(SacredMountainAuthoringData.Status.FINALIZED, data.status());
        assertThrows(IllegalStateException.class, data::beginClear);
    }

    @Test
    void clearReturnsManifestToPristineIdleState() {
        SacredMountainAuthoringData data = new SacredMountainAuthoringData();
        data.begin(geometry(SacredMountainAuthoringData.Mode.PREVIEW));
        data.beginPlacement();
        data.completeDraft();
        data.approveCompletedPreview();
        data.beginClear();
        data.updateProgress(new SacredMountainAuthoringData.Progress(4, 17, 9));
        data.clearComplete();
        assertTrue(data.isIdle());
        assertTrue(data.geometry().isEmpty());
        assertEquals(SacredMountainAuthoringData.Progress.ZERO, data.progress());
        assertEquals(SacredMountainShape.DEFAULT_SEED, data.approvedPreviewSeed().orElseThrow());
    }

    @Test
    void chunkTraversalIsDeterministicAndCenterFirst() {
        var geometry = geometry(SacredMountainAuthoringData.Mode.FULL);
        var first = SacredMountainBuildService.chunksFor(geometry);
        var second = SacredMountainBuildService.chunksFor(geometry);
        assertEquals(first, second);
        assertEquals(2_304, first.size());
        assertEquals(0, first.getFirst().x());
        assertEquals(0, first.getFirst().z());
        assertEquals(first.size(), first.stream().distinct().count());

        var preview = SacredMountainBuildService.chunksFor(geometry(SacredMountainAuthoringData.Mode.PREVIEW));
        assertEquals(144, preview.size());
    }

    @Test
    void backgroundChunkPreparationIsDeterministicAndDense() {
        var geometry = geometry(SacredMountainAuthoringData.Mode.FULL);
        var chunk = SacredMountainBuildService.chunksFor(geometry).getFirst();
        var shape = new SacredMountainShape(SacredMountainShape.DEFAULT_SEED);
        var first = SacredMountainBuildService.prepareChunk(geometry, shape, chunk, 0);
        var second = SacredMountainBuildService.prepareChunk(geometry, shape, chunk, 0);

        assertEquals(0, first.chunkIndex());
        assertArrayEquals(first.maximumYByColumn(), second.maximumYByColumn());
        assertArrayEquals(first.palette(), second.palette());
        long solidBlocks = 0;
        for (byte paletteCode : first.palette()) {
            if (paletteCode != 0) {
                solidBlocks++;
            }
        }
        assertTrue(solidBlocks > 20_000, "central prepared chunk unexpectedly lost its mountain mass");
    }

    @Test
    void savedDataCodecRoundTripsManifestProgressAndPreviewApproval() {
        SacredMountainAuthoringData data = new SacredMountainAuthoringData();
        data.begin(geometry(SacredMountainAuthoringData.Mode.PREVIEW));
        data.beginPlacement();
        data.completeDraft();
        data.approveCompletedPreview();
        data.beginClear();
        data.updateProgress(new SacredMountainAuthoringData.Progress(8, 77, 12));
        var json = SacredMountainAuthoringData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        SacredMountainAuthoringData loaded = SacredMountainAuthoringData.CODEC
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();
        assertEquals(data.geometry(), loaded.geometry());
        assertEquals(data.status(), loaded.status());
        assertEquals(data.progress(), loaded.progress());
        assertEquals(data.approvedPreviewSeed(), loaded.approvedPreviewSeed());
        assertEquals(data.approvedPreview(), loaded.approvedPreview());
        assertEquals(SacredMountainShape.VERSION,
                loaded.geometry().orElseThrow().shapeVersion());
        assertEquals(SacredMountainShape.VERSION,
                loaded.approvedPreview().orElseThrow().shapeVersion());
    }

    @Test
    void legacyManifestAndPreviewApprovalDefaultToShapeV2() {
        SacredMountainAuthoringData data = new SacredMountainAuthoringData();
        data.begin(geometry(SacredMountainAuthoringData.Mode.PREVIEW));
        data.beginPlacement();
        data.completeDraft();
        data.approveCompletedPreview();
        JsonObject legacy = SacredMountainAuthoringData.CODEC
                .encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow()
                .getAsJsonObject();
        legacy.getAsJsonObject("manifest").remove("shape_version");
        legacy.remove("approved_preview_shape_version");

        SacredMountainAuthoringData loaded = SacredMountainAuthoringData.CODEC
                .parse(JsonOps.INSTANCE, legacy)
                .getOrThrow();
        assertEquals(2, loaded.geometry().orElseThrow().shapeVersion());
        assertEquals(
                new SacredMountainAuthoringData.PreviewApproval(
                        2, SacredMountainShape.DEFAULT_SEED),
                loaded.approvedPreview().orElseThrow());
        assertTrue(loaded.geometry().orElseThrow().shortDescription().contains("shape=v2"));
    }

    private static SacredMountainAuthoringData.Geometry geometry(SacredMountainAuthoringData.Mode mode) {
        int scale = mode.scale();
        int minimum = Math.floorDiv(SacredMountainShape.MIN_COORDINATE, scale);
        int maximum = Math.floorDiv(SacredMountainShape.MAX_COORDINATE, scale);
        var bounds = new SacredMountainAuthoringData.Bounds(
                new BlockPos(minimum, 1, minimum),
                new BlockPos(maximum, SacredMountainShape.MAX_RELATIVE_HEIGHT / scale, maximum));
        return new SacredMountainAuthoringData.Geometry(
                "test-build",
                "minecraft:overworld",
                BlockPos.ZERO,
                SacredMountainShape.DEFAULT_SEED,
                SacredMountainShape.VERSION,
                mode,
                List.of(new BlockPos(-10, 5, 10), new BlockPos(0, 20, 0)),
                new BlockPos(38 / scale, SacredMountainShape.NEST_HEIGHT / scale, 0),
                bounds,
                0);
    }
}
