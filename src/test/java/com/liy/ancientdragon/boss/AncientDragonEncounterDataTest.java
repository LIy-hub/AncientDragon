package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.liy.ancientdragon.boss.AncientDragonEncounterData.Stage;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AncientDragonEncounterDataTest {
    private static final UUID DRAGON = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void theNestPaysOutOnceToAVictoryParticipantEvenAcrossSaveReload() {
        AncientDragonEncounterData data = new AncientDragonEncounterData();
        data.bindPlacement("mountain-1", "minecraft:overworld", DRAGON);
        assertFalse(data.canClaimEgg(PLAYER));
        data.updateStage(DRAGON, Stage.DYING, List.of(PLAYER));
        assertFalse(data.canClaimEgg(PLAYER));
        data.updateStage(DRAGON, Stage.CORPSE, List.of(PLAYER));

        var codec = AncientDragonEncounterData.CODEC;
        var oldSave = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
        assertFalse(oldSave.has("egg_claimed"));
        var loaded = codec.parse(JsonOps.INSTANCE, oldSave).getOrThrow();
        assertTrue(loaded.canClaimEgg(PLAYER));
        assertFalse(loaded.canClaimEgg(DRAGON));
        loaded.markEggClaimed();

        var claimedSave = codec.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow();
        var reloaded = codec.parse(JsonOps.INSTANCE, claimedSave).getOrThrow();
        assertTrue(reloaded.eggClaimed());
        assertFalse(reloaded.canClaimEgg(PLAYER));
    }

    @Test
    void corpseIsAnIrreversibleWorldUniqueStage() {
        AncientDragonEncounterData data = new AncientDragonEncounterData();
        data.bindPlacement("mountain-1", "minecraft:overworld", DRAGON);
        data.updateStage(DRAGON, Stage.ACTIVE, List.of());
        data.updateStage(DRAGON, Stage.DYING, List.of(PLAYER));
        data.updateStage(DRAGON, Stage.CORPSE, List.of(PLAYER));

        AncientDragonEncounterData.Encounter encounter = data.encounter().orElseThrow();
        assertEquals(Stage.CORPSE, encounter.stage());
        assertEquals(List.of(PLAYER.toString()), encounter.eligiblePlayers());
        assertThrows(IllegalStateException.class,
                () -> data.updateStage(DRAGON, Stage.DORMANT, List.of()));
        assertThrows(IllegalStateException.class,
                () -> data.bindPlacement("mountain-2", "minecraft:overworld", DRAGON));
        assertThrows(IllegalStateException.class,
                () -> data.bindPlacement(
                        "mountain-1",
                        "minecraft:overworld",
                        UUID.fromString("10000000-0000-0000-0000-000000000002")));
    }
}
