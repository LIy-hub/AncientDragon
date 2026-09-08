package com.liy.ancientdragon.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.liy.ancientdragon.boss.AncientDragonCorpseRewardData;
import com.liy.ancientdragon.boss.AncientDragonEncounterData;
import com.liy.ancientdragon.chronicle.DragonChronicleData;
import com.liy.ancientdragon.chronicle.DragonChronicleEvent;
import com.liy.ancientdragon.worldgen.SacredMountainAuthoringData;
import com.liy.ancientdragon.worldgen.SacredMountainNaturalRepairData;
import com.liy.ancientdragon.worldgen.SacredMountainPlacementData;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.Test;

class LegacySavedDataTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    @Test void allSixNativeFactoriesCanReloadTheirOwnNbt() {
        for (var type : List.of(AncientDragonEncounterData.TYPE, AncientDragonCorpseRewardData.TYPE,
                DragonChronicleData.TYPE, SacredMountainAuthoringData.TYPE,
                SacredMountainPlacementData.TYPE, SacredMountainNaturalRepairData.TYPE)) roundTrip(type);
    }
    private static <T extends SavedData> void roundTrip(LegacySavedDataType<T> type) {
        T value = type.factory().constructor().get();
        var tag = type.save(value, REGISTRIES);
        T restored = type.factory().deserializer().apply(tag, REGISTRIES);
        assertEquals(tag, type.save(restored, REGISTRIES));
        assertFalse(type.id().contains(":"));
    }
    @Test void pendingRewardsAndChronicleNotificationsSurviveNativeFactoryReloadExactlyOnce() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var rewards = new AncientDragonCorpseRewardData();
        rewards.queueExperience(player, 900); rewards.queueLoot(player, "bone", 10);
        var loaded = AncientDragonCorpseRewardData.TYPE.factory().deserializer().apply(
                AncientDragonCorpseRewardData.TYPE.save(rewards, REGISTRIES), REGISTRIES);
        assertEquals(900, loaded.take(player).orElseThrow().experience());
        assertTrue(loaded.take(player).isEmpty());
        var chronicle = new DragonChronicleData();
        DragonChronicleEvent event = DragonChronicleEvent.values()[0];
        chronicle.unlockAndQueue(player, event);
        var reloaded = DragonChronicleData.TYPE.factory().deserializer().apply(
                DragonChronicleData.TYPE.save(chronicle, REGISTRIES), REGISTRIES);
        assertEquals(List.of(event), reloaded.takePending(player));
        assertTrue(reloaded.takePending(player).isEmpty());
        assertTrue(reloaded.isUnlocked(player, event));
    }
    @Test void partialNaturalRepairProgressSurvivesFactoryReload() {
        var repair = new SacredMountainNaturalRepairData();
        repair.begin("0".repeat(64), new BlockPos(12, 80, 34), 1, 3);
        repair.advance(17, 12, 123456L);
        var restored = SacredMountainNaturalRepairData.TYPE.factory().deserializer().apply(
                SacredMountainNaturalRepairData.TYPE.save(repair, REGISTRIES), REGISTRIES);
        assertEquals(repair.job(), restored.job());
    }
}
