package com.liy.ancientdragon.chronicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DragonChronicleTextTest {
    @Test
    void allFourChroniclesHaveShortBooksAndDistinctPersistenceBits() {
        assertEquals(4, DragonChronicleEvent.values().length);
        Set<Integer> bits = new HashSet<>();
        for (DragonChronicleEvent event : DragonChronicleEvent.values()) {
            assertTrue(bits.add(event.bit()));
            assertFalse(event.bookTitle().isBlank());
            assertEquals(2, event.pages().size());
            assertEquals(2, event.pageKeys().size());
        }
        assertEquals(0b1111, DragonChronicleEvent.allBits());
    }

    @Test
    void everyChronicleMessageAndPageIsLocalizedInChineseAndEnglish() {
        JsonObject chinese = language("zh_cn");
        JsonObject english = language("en_us");
        for (DragonChronicleEvent event : DragonChronicleEvent.values()) {
            assertHasText(chinese, event.noticeKey());
            assertHasText(english, event.noticeKey());
            for (String key : event.pageKeys()) {
                assertHasText(chinese, key);
                assertHasText(english, key);
            }
        }
        assertHasText(chinese, "message.ancient_dragon.chronicle.slain_by_dragon_death");
        assertHasText(english, "message.ancient_dragon.chronicle.slain_by_dragon_death");
        assertHasText(chinese, "command.ancient_dragon.chronicle.player_only");
        assertHasText(english, "command.ancient_dragon.chronicle.player_only");
    }

    private static JsonObject language(String locale) {
        String path = "/assets/ancient_dragon/lang/" + locale + ".json";
        try (InputStream stream = DragonChronicleTextTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing language resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read language resource " + path, exception);
        }
    }

    private static void assertHasText(JsonObject language, String key) {
        assertTrue(language.has(key), () -> "Missing translation key " + key);
        assertFalse(language.get(key).getAsString().isBlank(), () -> "Blank translation key " + key);
    }
}
