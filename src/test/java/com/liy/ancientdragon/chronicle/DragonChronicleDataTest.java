package com.liy.ancientdragon.chronicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DragonChronicleDataTest {
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000099");

    @Test
    void eachChapterUnlocksOnceAndStaysIndependent() {
        DragonChronicleData data = new DragonChronicleData();

        assertTrue(data.unlock(PLAYER, DragonChronicleEvent.GATE_OPENED));
        assertFalse(data.unlock(PLAYER, DragonChronicleEvent.GATE_OPENED));
        assertTrue(data.unlock(PLAYER, DragonChronicleEvent.DRAGON_AWAKENED));

        assertTrue(data.isUnlocked(PLAYER, DragonChronicleEvent.GATE_OPENED));
        assertTrue(data.isUnlocked(PLAYER, DragonChronicleEvent.DRAGON_AWAKENED));
        assertFalse(data.isUnlocked(PLAYER, DragonChronicleEvent.SLAIN_BY_DRAGON));
        assertEquals(
                List.of(DragonChronicleEvent.GATE_OPENED, DragonChronicleEvent.DRAGON_AWAKENED),
                data.unlockedEvents(PLAYER));
    }

    @Test
    void deathChapterWaitsForSafeDeliveryAndCannotBeDuplicated() {
        DragonChronicleData data = new DragonChronicleData();

        assertTrue(data.unlockAndQueue(PLAYER, DragonChronicleEvent.SLAIN_BY_DRAGON));
        assertFalse(data.unlockAndQueue(PLAYER, DragonChronicleEvent.SLAIN_BY_DRAGON));
        assertTrue(data.isUnlocked(PLAYER, DragonChronicleEvent.SLAIN_BY_DRAGON));
        assertTrue(data.isPending(PLAYER, DragonChronicleEvent.SLAIN_BY_DRAGON));
        assertEquals(List.of(DragonChronicleEvent.SLAIN_BY_DRAGON), data.takePending(PLAYER));
        assertFalse(data.isPending(PLAYER, DragonChronicleEvent.SLAIN_BY_DRAGON));
        assertEquals(List.of(), data.takePending(PLAYER));
    }

    @Test
    void pendingAndUnlockedProgressSurviveCodecRoundTrip() {
        DragonChronicleData data = new DragonChronicleData();
        data.unlock(PLAYER, DragonChronicleEvent.GATE_OPENED);
        data.unlockAndQueue(PLAYER, DragonChronicleEvent.DRAGON_FALLEN);

        var json = DragonChronicleData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        DragonChronicleData loaded = DragonChronicleData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertTrue(loaded.isUnlocked(PLAYER, DragonChronicleEvent.GATE_OPENED));
        assertTrue(loaded.isUnlocked(PLAYER, DragonChronicleEvent.DRAGON_FALLEN));
        assertTrue(loaded.isPending(PLAYER, DragonChronicleEvent.DRAGON_FALLEN));
        assertFalse(loaded.isPending(PLAYER, DragonChronicleEvent.GATE_OPENED));
    }
}
