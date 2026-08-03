package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.atmosphere.SacredMountainNetherAtmosphere;
import com.liy.ancientdragon.atmosphere.SacredMountainNetherAtmosphere.ZoneState;
import com.liy.ancientdragon.worldgen.SacredMountainParticlePalette.ParticleKind;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Sends the Sacred Mountain's player-local Nether-memory particle field without changing fog. */
public final class SacredMountainSculkAtmosphere {
    private static final int EMISSION_INTERVAL_TICKS = 4;
    private static final int PARTICLES_PER_EMISSION = 6;

    private SacredMountainSculkAtmosphere() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % EMISSION_INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level() instanceof ServerLevel level) {
                    SacredMountainRegion.containingBounds(level, player.blockPosition()).ifPresent(bounds ->
                            emitAround(level, player,
                                    SacredMountainNetherAtmosphere.stateFor(bounds, player.blockPosition())));
                }
            }
        });
    }

    static boolean isWithinAtmosphere(BoundingBox mountainBounds, BlockPos position) {
        return SacredMountainRegion.contains(mountainBounds, position);
    }

    private static void emitAround(ServerLevel level, ServerPlayer player, ZoneState atmosphere) {
        RandomSource random = player.getRandom();
        int particleCount = PARTICLES_PER_EMISSION + (random.nextFloat() < atmosphere.intensity() ? 1 : 0);
        for (int index = 0; index < particleCount; index++) {
            double angle = random.nextDouble() * Math.TAU;
            double distance = 3.0D + random.nextDouble() * 19.0D;
            double x = player.getX() + Math.cos(angle) * distance;
            double y = player.getY() - 4.0D + random.nextDouble() * 11.0D;
            double z = player.getZ() + Math.sin(angle) * distance;
            ParticleKind kind = SacredMountainParticlePalette.pick(
                    atmosphere.zone(), random.nextInt(100), random.nextInt());
            float drift = 0.008F + (atmosphere.intensity() * 0.018F);
            level.sendParticles(
                    player, particleType(kind), false, false,
                    x, y, z, 1, 0.0D, drift, 0.0D, 0.0D);
        }
    }

    private static SimpleParticleType particleType(ParticleKind kind) {
        return switch (kind) {
            case ASH -> ParticleTypes.ASH;
            case CAMPFIRE_COSY_SMOKE -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case CAMPFIRE_SIGNAL_SMOKE -> ParticleTypes.CAMPFIRE_SIGNAL_SMOKE;
            case CHERRY_LEAVES -> ParticleTypes.CHERRY_LEAVES;
            case CLOUD -> ParticleTypes.CLOUD;
            case COMPOSTER -> ParticleTypes.COMPOSTER;
            case COPPER_FIRE_FLAME -> ParticleTypes.COPPER_FIRE_FLAME;
            case CRIMSON_SPORE -> ParticleTypes.CRIMSON_SPORE;
            case CRIT -> ParticleTypes.CRIT;
            case DRIPPING_DRIPSTONE_LAVA -> ParticleTypes.DRIPPING_DRIPSTONE_LAVA;
            case DRIPPING_DRIPSTONE_WATER -> ParticleTypes.DRIPPING_DRIPSTONE_WATER;
            case DRIPPING_HONEY -> ParticleTypes.DRIPPING_HONEY;
            case DRIPPING_LAVA -> ParticleTypes.DRIPPING_LAVA;
            case DRIPPING_OBSIDIAN_TEAR -> ParticleTypes.DRIPPING_OBSIDIAN_TEAR;
            case DRIPPING_WATER -> ParticleTypes.DRIPPING_WATER;
            case DUST_PLUME -> ParticleTypes.DUST_PLUME;
            case ELECTRIC_SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case ENCHANT -> ParticleTypes.ENCHANT;
            case ENCHANTED_HIT -> ParticleTypes.ENCHANTED_HIT;
            case END_ROD -> ParticleTypes.END_ROD;
            case FALLING_DRIPSTONE_LAVA -> ParticleTypes.FALLING_DRIPSTONE_LAVA;
            case FALLING_DRIPSTONE_WATER -> ParticleTypes.FALLING_DRIPSTONE_WATER;
            case FALLING_HONEY -> ParticleTypes.FALLING_HONEY;
            case FALLING_LAVA -> ParticleTypes.FALLING_LAVA;
            case FALLING_NECTAR -> ParticleTypes.FALLING_NECTAR;
            case FALLING_OBSIDIAN_TEAR -> ParticleTypes.FALLING_OBSIDIAN_TEAR;
            case FALLING_SPORE_BLOSSOM -> ParticleTypes.FALLING_SPORE_BLOSSOM;
            case FALLING_WATER -> ParticleTypes.FALLING_WATER;
            case FIREWORK -> ParticleTypes.FIREWORK;
            case FIREFLY -> ParticleTypes.FIREFLY;
            case FLAME -> ParticleTypes.FLAME;
            case GLOW -> ParticleTypes.GLOW;
            case GLOW_SQUID_INK -> ParticleTypes.GLOW_SQUID_INK;
            case GUST -> ParticleTypes.GUST;
            case GUST_EMITTER_LARGE -> ParticleTypes.GUST_EMITTER_LARGE;
            case GUST_EMITTER_SMALL -> ParticleTypes.GUST_EMITTER_SMALL;
            case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
            case LANDING_HONEY -> ParticleTypes.LANDING_HONEY;
            case LANDING_LAVA -> ParticleTypes.LANDING_LAVA;
            case LANDING_OBSIDIAN_TEAR -> ParticleTypes.LANDING_OBSIDIAN_TEAR;
            case LAVA -> ParticleTypes.LAVA;
            case MYCELIUM -> ParticleTypes.MYCELIUM;
            case NAUTILUS -> ParticleTypes.NAUTILUS;
            case PALE_OAK_LEAVES -> ParticleTypes.PALE_OAK_LEAVES;
            case PORTAL -> ParticleTypes.PORTAL;
            case POOF -> ParticleTypes.POOF;
            case REVERSE_PORTAL -> ParticleTypes.REVERSE_PORTAL;
            case RAIN -> ParticleTypes.RAIN;
            case SCRAPE -> ParticleTypes.SCRAPE;
            case SCULK_CHARGE_POP -> ParticleTypes.SCULK_CHARGE_POP;
            case SCULK_SOUL -> ParticleTypes.SCULK_SOUL;
            case SMALL_FLAME -> ParticleTypes.SMALL_FLAME;
            case SMALL_GUST -> ParticleTypes.SMALL_GUST;
            case SMOKE -> ParticleTypes.SMOKE;
            case SNOWFLAKE -> ParticleTypes.SNOWFLAKE;
            case SOUL -> ParticleTypes.SOUL;
            case SOUL_FIRE_FLAME -> ParticleTypes.SOUL_FIRE_FLAME;
            case SPORE_BLOSSOM_AIR -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case TOTEM_OF_UNDYING -> ParticleTypes.TOTEM_OF_UNDYING;
            case WARPED_SPORE -> ParticleTypes.WARPED_SPORE;
            case WAX_OFF -> ParticleTypes.WAX_OFF;
            case WAX_ON -> ParticleTypes.WAX_ON;
            case WHITE_ASH -> ParticleTypes.WHITE_ASH;
            case WHITE_SMOKE -> ParticleTypes.WHITE_SMOKE;
        };
    }
}
