package com.liy.ancientdragon.compat;

/** Non-colliding native flora substitutions retain the authored vegetation positions on older games. */
public final class LegacyBlockPalette {
    private LegacyBlockPalette() { }
    public static String resolve(String serialized) {
        return switch (serialized) {
            case "minecraft:firefly_bush" -> "minecraft:fern";
            case "minecraft:closed_eyeblossom" -> "minecraft:azure_bluet";
            case "minecraft:open_eyeblossom" -> "minecraft:poppy";
            default -> serialized;
        };
    }
}
