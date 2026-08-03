package com.liy.ancientdragon.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;

/** Owns the hostile-free thunderstorm that follows the loaded natural Sacred Mountain region. */
public final class SacredMountainEnvironmentService {
    private static final int WEATHER_SYNC_INTERVAL_TICKS = 20;
    private static final int HOSTILE_SWEEP_INTERVAL_TICKS = 20;
    private static final int STORM_HOLD_TICKS = 20 * 60;
    private static final int MINIMUM_LIGHTNING_DELAY_TICKS = 120;
    private static final int LIGHTNING_DELAY_VARIANCE_TICKS = 120;

    private static WeatherSnapshot originalWeather;
    private static long nextLightningTick = Long.MAX_VALUE;

    private SacredMountainEnvironmentService() {
    }

    public static void register() {
        ServerEntityEvents.ALLOW_LOAD.register(SacredMountainEnvironmentService::allowEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(SacredMountainEnvironmentService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(SacredMountainEnvironmentService::restoreBeforeStop);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearState());
    }

    private static boolean allowEntityLoad(
            Entity entity,
            ServerLevel level,
            EntitySpawnReason spawnReason,
            boolean loadedFromDisk) {
        return !shouldSuppressHostile(entity instanceof Enemy,
                SacredMountainRegion.contains(level, entity.blockPosition()));
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        List<ServerPlayer> mountainPlayers = level.getPlayers(player ->
                SacredMountainRegion.contains(level, player.blockPosition()));
        if (mountainPlayers.isEmpty()) {
            restoreWeather(level);
            nextLightningTick = Long.MAX_VALUE;
        } else {
            if (originalWeather == null) {
                originalWeather = WeatherSnapshot.capture(level);
                nextLightningTick = server.getTickCount() + MINIMUM_LIGHTNING_DELAY_TICKS;
            }
            forceThunderstorm(level);
            if (server.getTickCount() % WEATHER_SYNC_INTERVAL_TICKS == 0) {
                syncWeather(level, true, 1.0F, 1.0F);
            }
            if (server.getTickCount() >= nextLightningTick) {
                spawnVisualLightning(level, mountainPlayers);
                nextLightningTick = server.getTickCount()
                        + MINIMUM_LIGHTNING_DELAY_TICKS
                        + level.getRandom().nextInt(LIGHTNING_DELAY_VARIANCE_TICKS + 1);
            }
        }
        if (server.getTickCount() % HOSTILE_SWEEP_INTERVAL_TICKS == 0) {
            removeHostilesAlreadyInside(level);
        }
    }

    private static void forceThunderstorm(ServerLevel level) {
        WeatherData weather = level.getWeatherData();
        weather.setClearWeatherTime(0);
        weather.setRainTime(STORM_HOLD_TICKS);
        weather.setThunderTime(STORM_HOLD_TICKS);
        weather.setRaining(true);
        weather.setThundering(true);
        level.setRainLevel(1.0F);
        level.setThunderLevel(1.0F);
    }

    private static void restoreWeather(ServerLevel level) {
        if (originalWeather == null) {
            return;
        }
        originalWeather.apply(level);
        syncWeather(
                level,
                originalWeather.raining(),
                originalWeather.rainLevel(),
                originalWeather.thunderLevel());
        originalWeather = null;
    }

    private static void syncWeather(ServerLevel level, boolean raining, float rainLevel, float thunderLevel) {
        ClientboundGameEventPacket state = new ClientboundGameEventPacket(
                raining ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING,
                0.0F);
        ClientboundGameEventPacket rain = new ClientboundGameEventPacket(
                ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rainLevel);
        ClientboundGameEventPacket thunder = new ClientboundGameEventPacket(
                ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunderLevel);
        for (ServerPlayer player : level.players()) {
            player.connection.send(state);
            player.connection.send(rain);
            player.connection.send(thunder);
        }
    }

    private static void spawnVisualLightning(ServerLevel level, List<ServerPlayer> mountainPlayers) {
        ServerPlayer player = mountainPlayers.get(level.getRandom().nextInt(mountainPlayers.size()));
        int x = player.getBlockX() + level.getRandom().nextInt(97) - 48;
        int z = player.getBlockZ() + level.getRandom().nextInt(97) - 48;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos target = new BlockPos(x, y, z);
        if (!SacredMountainRegion.contains(level, target)) {
            target = player.blockPosition();
        }
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (lightning == null) {
            return;
        }
        lightning.setVisualOnly(true);
        lightning.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        level.addFreshEntity(lightning);
    }

    private static void removeHostilesAlreadyInside(ServerLevel level) {
        List<Entity> removal = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Enemy
                    && SacredMountainRegion.contains(level, entity.blockPosition())) {
                removal.add(entity);
            }
        }
        removal.forEach(Entity::discard);
    }

    private static void restoreBeforeStop(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level != null) {
            restoreWeather(level);
        }
    }

    private static void clearState() {
        originalWeather = null;
        nextLightningTick = Long.MAX_VALUE;
    }

    static boolean shouldSuppressHostile(boolean hostile, boolean insideMountain) {
        return hostile && insideMountain;
    }

    private record WeatherSnapshot(
            int clearWeatherTime,
            boolean raining,
            int rainTime,
            boolean thundering,
            int thunderTime,
            float rainLevel,
            float thunderLevel) {

        static WeatherSnapshot capture(ServerLevel level) {
            WeatherData weather = level.getWeatherData();
            return new WeatherSnapshot(
                    weather.getClearWeatherTime(),
                    weather.isRaining(),
                    weather.getRainTime(),
                    weather.isThundering(),
                    weather.getThunderTime(),
                    level.getRainLevel(1.0F),
                    level.getThunderLevel(1.0F));
        }

        void apply(ServerLevel level) {
            WeatherData weather = level.getWeatherData();
            weather.setClearWeatherTime(clearWeatherTime);
            weather.setRaining(raining);
            weather.setRainTime(rainTime);
            weather.setThundering(thundering);
            weather.setThunderTime(thunderTime);
            level.setRainLevel(rainLevel);
            level.setThunderLevel(thunderLevel);
        }
    }
}
