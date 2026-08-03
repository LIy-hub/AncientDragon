package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.atmosphere.SacredMountainNetherZone;
import java.util.List;
import java.util.Set;

/**
 * Curated vanilla particle palette for the Sacred Mountain's divine field.
 *
 * <p>It intentionally includes most particles that read as weather, ash, magic, growth, fire, or
 * distant ritual light, but excludes particles that lie about combat state (hearts, damage, potion
 * effects, fishing, item debris, explosions, and warning icons).</p>
 */
public final class SacredMountainParticlePalette {
    private static final List<ParticleKind> NETHER_WASTES = List.of(
            ParticleKind.ASH, ParticleKind.WHITE_ASH, ParticleKind.SMOKE, ParticleKind.WHITE_SMOKE,
            ParticleKind.SMALL_FLAME, ParticleKind.FLAME, ParticleKind.LAVA, ParticleKind.DRIPPING_LAVA,
            ParticleKind.FALLING_LAVA, ParticleKind.FALLING_DRIPSTONE_LAVA, ParticleKind.LANDING_LAVA,
            ParticleKind.DUST_PLUME, ParticleKind.CAMPFIRE_COSY_SMOKE, ParticleKind.CAMPFIRE_SIGNAL_SMOKE);
    private static final List<ParticleKind> SOUL_SAND_VALLEY = List.of(
            ParticleKind.SOUL, ParticleKind.SOUL_FIRE_FLAME, ParticleKind.SCULK_SOUL,
            ParticleKind.SCULK_CHARGE_POP, ParticleKind.ASH, ParticleKind.WHITE_ASH,
            ParticleKind.SNOWFLAKE, ParticleKind.SMALL_GUST, ParticleKind.DRIPPING_OBSIDIAN_TEAR,
            ParticleKind.FALLING_OBSIDIAN_TEAR, ParticleKind.LANDING_OBSIDIAN_TEAR, ParticleKind.SOUL);
    private static final List<ParticleKind> CRIMSON_FOREST = List.of(
            ParticleKind.CRIMSON_SPORE, ParticleKind.SPORE_BLOSSOM_AIR, ParticleKind.FALLING_SPORE_BLOSSOM,
            ParticleKind.FLAME, ParticleKind.SMALL_FLAME, ParticleKind.LAVA, ParticleKind.COMPOSTER,
            ParticleKind.WAX_ON, ParticleKind.GLOW, ParticleKind.MYCELIUM, ParticleKind.DRIPPING_HONEY,
            ParticleKind.FALLING_HONEY, ParticleKind.LANDING_HONEY, ParticleKind.FALLING_NECTAR);
    private static final List<ParticleKind> WARPED_FOREST = List.of(
            ParticleKind.WARPED_SPORE, ParticleKind.PORTAL, ParticleKind.REVERSE_PORTAL,
            ParticleKind.SCULK_SOUL, ParticleKind.SCULK_CHARGE_POP, ParticleKind.GLOW,
            ParticleKind.END_ROD, ParticleKind.GLOW_SQUID_INK, ParticleKind.FIREFLY,
            ParticleKind.ELECTRIC_SPARK, ParticleKind.NAUTILUS, ParticleKind.CLOUD);
    private static final List<ParticleKind> BASALT_DELTAS = List.of(
            ParticleKind.ASH, ParticleKind.WHITE_ASH, ParticleKind.SMOKE, ParticleKind.LARGE_SMOKE,
            ParticleKind.CAMPFIRE_COSY_SMOKE, ParticleKind.CAMPFIRE_SIGNAL_SMOKE, ParticleKind.DUST_PLUME,
            ParticleKind.ELECTRIC_SPARK, ParticleKind.SCRAPE, ParticleKind.LAVA, ParticleKind.DRIPPING_LAVA,
            ParticleKind.COPPER_FIRE_FLAME, ParticleKind.DRIPPING_DRIPSTONE_LAVA,
            ParticleKind.FALLING_DRIPSTONE_LAVA);
    private static final List<ParticleKind> DIVINE_FIELD = List.of(
            ParticleKind.ASH, ParticleKind.WHITE_ASH, ParticleKind.SOUL, ParticleKind.SCULK_SOUL,
            ParticleKind.SCULK_CHARGE_POP, ParticleKind.WARPED_SPORE, ParticleKind.CRIMSON_SPORE,
            ParticleKind.PORTAL, ParticleKind.REVERSE_PORTAL, ParticleKind.ENCHANT, ParticleKind.END_ROD,
            ParticleKind.GLOW, ParticleKind.WAX_ON, ParticleKind.WAX_OFF, ParticleKind.ELECTRIC_SPARK,
            ParticleKind.MYCELIUM, ParticleKind.SPORE_BLOSSOM_AIR, ParticleKind.FALLING_SPORE_BLOSSOM,
            ParticleKind.SNOWFLAKE, ParticleKind.CHERRY_LEAVES, ParticleKind.PALE_OAK_LEAVES,
            ParticleKind.FIREFLY, ParticleKind.POOF, ParticleKind.GUST, ParticleKind.SMALL_GUST,
            ParticleKind.COMPOSTER, ParticleKind.SCRAPE, ParticleKind.CRIT, ParticleKind.ENCHANTED_HIT,
            ParticleKind.COPPER_FIRE_FLAME, ParticleKind.GLOW_SQUID_INK, ParticleKind.DUST_PLUME,
            ParticleKind.CLOUD, ParticleKind.RAIN, ParticleKind.DRIPPING_WATER, ParticleKind.FALLING_WATER,
            ParticleKind.DRIPPING_DRIPSTONE_WATER, ParticleKind.FALLING_DRIPSTONE_WATER,
            ParticleKind.DRIPPING_OBSIDIAN_TEAR, ParticleKind.FALLING_OBSIDIAN_TEAR,
            ParticleKind.LANDING_OBSIDIAN_TEAR, ParticleKind.DRIPPING_HONEY, ParticleKind.FALLING_HONEY,
            ParticleKind.LANDING_HONEY, ParticleKind.FALLING_NECTAR, ParticleKind.NAUTILUS);
    private static final List<ParticleKind> RARE_MANIFESTATIONS = List.of(
            ParticleKind.FIREWORK, ParticleKind.TOTEM_OF_UNDYING, ParticleKind.GUST_EMITTER_SMALL,
            ParticleKind.GUST_EMITTER_LARGE, ParticleKind.END_ROD, ParticleKind.REVERSE_PORTAL,
            ParticleKind.SCULK_CHARGE_POP, ParticleKind.GLOW);
    private static final Set<ParticleKind> ALL_PARTICLES = Set.of(ParticleKind.values());

    private SacredMountainParticlePalette() {
    }

    /** 58% native biome memory, 35% cross-biome divinity, 7% rare manifestation. */
    public static ParticleKind pick(SacredMountainNetherZone zone, int lane, int selection) {
        List<ParticleKind> candidates = lane < 58
                ? particlesForZone(zone)
                : lane < 93 ? DIVINE_FIELD : RARE_MANIFESTATIONS;
        return candidates.get(Math.floorMod(selection, candidates.size()));
    }

    public static List<ParticleKind> particlesForZone(SacredMountainNetherZone zone) {
        return switch (zone) {
            case NETHER_WASTES -> NETHER_WASTES;
            case SOUL_SAND_VALLEY -> SOUL_SAND_VALLEY;
            case CRIMSON_FOREST -> CRIMSON_FOREST;
            case WARPED_FOREST -> WARPED_FOREST;
            case BASALT_DELTAS -> BASALT_DELTAS;
        };
    }

    public static List<ParticleKind> divineParticles() {
        return DIVINE_FIELD;
    }

    public static List<ParticleKind> rareManifestations() {
        return RARE_MANIFESTATIONS;
    }

    public static Set<ParticleKind> allParticleKinds() {
        return ALL_PARTICLES;
    }

    public enum ParticleKind {
        ASH,
        CAMPFIRE_COSY_SMOKE,
        CAMPFIRE_SIGNAL_SMOKE,
        CHERRY_LEAVES,
        CLOUD,
        COMPOSTER,
        COPPER_FIRE_FLAME,
        CRIMSON_SPORE,
        CRIT,
        DRIPPING_DRIPSTONE_LAVA,
        DRIPPING_DRIPSTONE_WATER,
        DRIPPING_HONEY,
        DRIPPING_LAVA,
        DRIPPING_OBSIDIAN_TEAR,
        DRIPPING_WATER,
        DUST_PLUME,
        ELECTRIC_SPARK,
        ENCHANT,
        ENCHANTED_HIT,
        END_ROD,
        FALLING_DRIPSTONE_LAVA,
        FALLING_DRIPSTONE_WATER,
        FALLING_HONEY,
        FALLING_LAVA,
        FALLING_NECTAR,
        FALLING_OBSIDIAN_TEAR,
        FALLING_SPORE_BLOSSOM,
        FALLING_WATER,
        FIREWORK,
        FIREFLY,
        FLAME,
        GLOW,
        GLOW_SQUID_INK,
        GUST,
        GUST_EMITTER_LARGE,
        GUST_EMITTER_SMALL,
        LARGE_SMOKE,
        LANDING_HONEY,
        LANDING_LAVA,
        LANDING_OBSIDIAN_TEAR,
        LAVA,
        MYCELIUM,
        NAUTILUS,
        PALE_OAK_LEAVES,
        PORTAL,
        POOF,
        REVERSE_PORTAL,
        RAIN,
        SCRAPE,
        SCULK_CHARGE_POP,
        SCULK_SOUL,
        SMALL_FLAME,
        SMALL_GUST,
        SMOKE,
        SNOWFLAKE,
        SOUL,
        SOUL_FIRE_FLAME,
        SPORE_BLOSSOM_AIR,
        TOTEM_OF_UNDYING,
        WARPED_SPORE,
        WAX_OFF,
        WAX_ON,
        WHITE_ASH,
        WHITE_SMOKE
    }
}
