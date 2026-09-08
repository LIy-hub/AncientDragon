package com.liy.ancientdragon.compat;

/** Only version-absent decorative flora is translated at placement; authored tile bytes stay intact. */
public final class LegacyBlockPalette {
    private LegacyBlockPalette() { }
    public static String resolve(String serialized) {
        return "minecraft:firefly_bush".equals(serialized) ? "minecraft:fern" : serialized;
    }
}
