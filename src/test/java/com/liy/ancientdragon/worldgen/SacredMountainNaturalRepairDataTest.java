package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.liy.ancientdragon.worldgen.SacredMountainNaturalRepairData.Status;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SacredMountainNaturalRepairDataTest {
    private static final String HASH =
            "afa7f74628eb524eeb34018a88c9289aad0f6c7042593b731ef7ec66d9558a4e";

    @Test
    void repairProgressSurvivesAsAStableRestartCursor() {
        SacredMountainNaturalRepairData data = new SacredMountainNaturalRepairData();
        BlockPos anchor = new BlockPos(-5_984, -61, -1_456);

        data.begin(HASH, anchor, 5, 2);
        var started = data.job().orElseThrow();
        assertEquals(anchor, started.anchor());
        assertEquals(1, started.quarterTurns());
        assertEquals(2, started.repairVersion());
        assertEquals(Status.RUNNING, started.status());

        data.advance(417, 302, 8_765_432L);
        var advanced = data.job().orElseThrow();
        assertEquals(417, advanced.nextTileIndex());
        assertEquals(302, advanced.repairedTiles());
        assertEquals(8_765_432L, advanced.changedBlocks());

        data.complete();
        assertEquals(Status.COMPLETE, data.job().orElseThrow().status());
    }
}
