package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AncientDragonCorpseRewardDataTest {
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000177");

    @Test
    void queuedOfflineRewardMergesAndIsTakenOnce() {
        AncientDragonCorpseRewardData data = new AncientDragonCorpseRewardData();

        data.queueExperience(PLAYER, 250);
        data.queueExperience(PLAYER, 50);
        data.queueLoot(PLAYER, "scales", 12);
        data.queueLoot(PLAYER, "scales", 4);
        data.queueLoot(PLAYER, "not_a_corpse_part", 99);

        AncientDragonCorpseRewardData.PlayerRewards rewards = data.take(PLAYER).orElseThrow();
        assertEquals(300, rewards.experience());
        assertEquals(List.of(new AncientDragonCorpseRewardData.LootEntry("scales", 16)), rewards.loot());
        assertFalse(data.hasPending(PLAYER));
        assertTrue(data.take(PLAYER).isEmpty());
    }

    @Test
    void queuedOfflineRewardSurvivesCodecRoundTrip() {
        AncientDragonCorpseRewardData data = new AncientDragonCorpseRewardData();
        data.queueExperience(PLAYER, 255_532);
        data.queueLoot(PLAYER, "horns", 1);
        data.queueLoot(PLAYER, "left_membrane", 8);

        var json = AncientDragonCorpseRewardData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        AncientDragonCorpseRewardData loaded = AncientDragonCorpseRewardData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        AncientDragonCorpseRewardData.PlayerRewards rewards = loaded.take(PLAYER).orElseThrow();
        assertEquals(255_532, rewards.experience());
        assertEquals(
                List.of(
                        new AncientDragonCorpseRewardData.LootEntry("horns", 1),
                        new AncientDragonCorpseRewardData.LootEntry("left_membrane", 8)),
                rewards.loot());
    }
}
