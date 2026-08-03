package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SacredMountainPlacementDataTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preflightConfirmationAndPlacementProtectionFollowTheTwoStepLifecycle() {
        SacredMountainPlacementData data = new SacredMountainPlacementData();
        data.begin(job());
        data.updateProgress(new SacredMountainPlacementData.Progress(9, 87, 3, 4), 12_345L, 0L);
        data.markReady();

        assertEquals(SacredMountainPlacementData.Status.READY, data.job().orElseThrow().status());
        assertEquals(new SacredMountainPlacementData.Progress(9, 87, 3, 4),
                data.job().orElseThrow().progress());

        data.beginPlacement("placement-test");
        assertEquals(SacredMountainPlacementData.Status.PLACING, data.job().orElseThrow().status());
        assertEquals(SacredMountainPlacementData.Progress.ZERO, data.job().orElseThrow().progress());
        assertThrows(IllegalStateException.class, () -> data.discard("placement-test"));

        UUID dragonUuid = UUID.fromString("59f8ce2a-a6d0-4898-a48c-8d80bb85e345");
        data.complete(dragonUuid);
        assertEquals(SacredMountainPlacementData.Status.COMPLETE, data.job().orElseThrow().status());
        assertEquals(dragonUuid, data.job().orElseThrow().dragonUuid().orElseThrow());
    }

    @Test
    void codecRoundTripPausesAnInterruptedTaskWithoutLosingItsCursor() {
        SacredMountainPlacementData data = new SacredMountainPlacementData();
        data.begin(job());
        SacredMountainPlacementData.Progress progress =
                new SacredMountainPlacementData.Progress(18, 255, 5, 19);
        data.updateProgress(progress, 90_000L, 0L);

        var json = SacredMountainPlacementData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        SacredMountainPlacementData loaded = SacredMountainPlacementData.CODEC
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();

        SacredMountainPlacementData.Job loadedJob = loaded.job().orElseThrow();
        assertEquals(SacredMountainPlacementData.Status.PAUSED, loadedJob.status());
        assertEquals(SacredMountainPlacementData.Phase.PREFLIGHT, loadedJob.phase());
        assertEquals(progress, loadedJob.progress());
        assertEquals(90_000L, loadedJob.checkedBlocks());
        assertEquals("server restarted; resume explicitly", loadedJob.issue());
    }

    @Test
    void aDiscardedUnstartedPreflightLeavesNoActiveJob() {
        SacredMountainPlacementData data = new SacredMountainPlacementData();
        data.begin(job());
        data.discard("placement-test");
        assertEquals(java.util.Optional.empty(), data.job());
    }

    @Test
    void legacyCompletedPlacementCanAttachOneMissingDragonButNeverReplaceIt() {
        SacredMountainPlacementData original = new SacredMountainPlacementData();
        original.begin(job());
        original.markReady();
        original.beginPlacement("placement-test");
        original.complete(UUID.fromString("1f357e94-f902-4de4-884b-75b914ce864e"));
        JsonObject legacy = SacredMountainPlacementData.CODEC
                .encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow()
                .getAsJsonObject();
        legacy.getAsJsonObject("job").remove("dragon_uuid");

        SacredMountainPlacementData loaded = SacredMountainPlacementData.CODEC
                .parse(JsonOps.INSTANCE, legacy)
                .getOrThrow();
        assertEquals(java.util.Optional.empty(), loaded.job().orElseThrow().dragonUuid());

        UUID attached = UUID.fromString("cab5e01d-2351-4389-bf35-ae89a683ad8e");
        loaded.attachDragonToCompletedPlacement(attached);
        assertEquals(attached, loaded.job().orElseThrow().dragonUuid().orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> loaded.attachDragonToCompletedPlacement(UUID.randomUUID()));
    }

    private static SacredMountainPlacementData.Job job() {
        return new SacredMountainPlacementData.Job(
                "placement-test",
                SacredMountainStructureAsset.DEFAULT_ASSET_ID,
                "afa7f74628eb524eeb34018a88c9289aad0f6c7042593b731ef7ec66d9558a4e",
                "minecraft:overworld",
                new BlockPos(512, -61, 1024),
                1,
                new SacredMountainAuthoringData.Bounds(
                        new BlockPos(128, -60, 640),
                        new BlockPos(895, 249, 1407)),
                new BlockPos(510, 144, 1163),
                180.0F,
                SacredMountainPlacementData.Status.PREFLIGHT,
                SacredMountainPlacementData.Phase.PREFLIGHT,
                SacredMountainPlacementData.Progress.ZERO,
                "",
                0L,
                0L,
                "");
    }
}
