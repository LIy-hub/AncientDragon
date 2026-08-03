package com.liy.ancientdragon.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.liy.ancientdragon.boss.AncientDragonEncounterData.Stage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class AncientDragonEncounterDataTest {
    private static final UUID DRAGON = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000001");

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
