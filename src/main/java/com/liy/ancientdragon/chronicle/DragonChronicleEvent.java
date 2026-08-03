package com.liy.ancientdragon.chronicle;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The small, non-mechanical story beats attached to the one Ancient Dragon encounter.
 *
 * <p>The bit values are persisted by {@link DragonChronicleData}; never change an existing
 * value after release.</p>
 */
public enum DragonChronicleEvent {
    GATE_OPENED(
            1,
            "gate_opened",
            "余烬档案 I：门前",
            "message.ancient_dragon.chronicle.gate_opened",
            List.of(
                    "book.ancient_dragon.chronicle.gate_opened.1",
                    "book.ancient_dragon.chronicle.gate_opened.2")),
    DRAGON_AWAKENED(
            1 << 1,
            "dragon_awakened",
            "余烬档案 II：山醒",
            "message.ancient_dragon.chronicle.dragon_awakened",
            List.of(
                    "book.ancient_dragon.chronicle.dragon_awakened.1",
                    "book.ancient_dragon.chronicle.dragon_awakened.2")),
    SLAIN_BY_DRAGON(
            1 << 2,
            "slain_by_dragon",
            "余烬档案 III：风暴中",
            "message.ancient_dragon.chronicle.slain_by_dragon",
            List.of(
                    "book.ancient_dragon.chronicle.slain_by_dragon.1",
                    "book.ancient_dragon.chronicle.slain_by_dragon.2")),
    DRAGON_FALLEN(
            1 << 3,
            "dragon_fallen",
            "余烬档案 IV：日落",
            "message.ancient_dragon.chronicle.dragon_fallen",
            List.of(
                    "book.ancient_dragon.chronicle.dragon_fallen.1",
                    "book.ancient_dragon.chronicle.dragon_fallen.2"));

    private final int bit;
    private final String serializedName;
    private final String bookTitle;
    private final String noticeKey;
    private final List<String> pageKeys;

    DragonChronicleEvent(
            int bit,
            String serializedName,
            String bookTitle,
            String noticeKey,
            List<String> pageKeys) {
        this.bit = bit;
        this.serializedName = serializedName;
        this.bookTitle = bookTitle;
        this.noticeKey = noticeKey;
        this.pageKeys = List.copyOf(pageKeys);
    }

    public int bit() {
        return bit;
    }

    public String serializedName() {
        return serializedName;
    }

    public String bookTitle() {
        return bookTitle;
    }

    public String noticeKey() {
        return noticeKey;
    }

    public List<Component> pages() {
        return pageKeys.stream().<Component>map(Component::translatable).toList();
    }

    List<String> pageKeys() {
        return pageKeys;
    }

    public static int allBits() {
        int bits = 0;
        for (DragonChronicleEvent event : values()) {
            bits |= event.bit;
        }
        return bits;
    }
}
