package com.liy.ancientdragon.client.atmosphere;

import com.liy.ancientdragon.atmosphere.DragonAtmosphereCue;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereCuePayload;
import com.liy.ancientdragon.atmosphere.DragonAtmospherePhase;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereProfile;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereSnapshotPayload;
import com.liy.ancientdragon.atmosphere.DragonAtmosphereState;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Client-only, local presentation for the authoritative Ancient Dragon encounter. */
public final class AncientDragonAtmosphereController {
    private static final AncientDragonAtmosphereController INSTANCE = new AncientDragonAtmosphereController();
    private static final int SNAPSHOT_STALE_TICKS = 140;

    private final Map<Integer, EncounterVisualState> encounters = new HashMap<>();
    private final Random random = new Random();
    private DragonAtmosphereClientOptions options = DragonAtmosphereClientOptions.DEFAULT;
    private DragonAtmosphereProfile currentFog = DragonAtmosphereProfile.forEncounter(
            DragonAtmospherePhase.DORMANT, DragonAtmosphereState.DORMANT, 0.0D, 0.0F);
    private DragonAtmosphereMusicMood musicMood = DragonAtmosphereMusicMood.NONE;
    private ClientLevel observedLevel;
    private long clientTicks;
    private float cameraImpulse;

    private AncientDragonAtmosphereController() {
    }

    public static void initialize() {
        INSTANCE.initializeInternal();
    }

    public static DragonAtmosphereProfile fogProfile() {
        return INSTANCE.currentFog;
    }

    public static float cameraYawOffset() {
        return INSTANCE.cameraImpulse <= 0.001F
                ? 0.0F
                : (float) Math.sin(INSTANCE.clientTicks * 1.71D) * INSTANCE.cameraImpulse * 1.35F;
    }

    public static float cameraPitchOffset() {
        return INSTANCE.cameraImpulse <= 0.001F
                ? 0.0F
                : (float) Math.cos(INSTANCE.clientTicks * 2.13D) * INSTANCE.cameraImpulse * 0.82F;
    }

    /** Future sound playback can poll this rather than reverse-engineering boss animations. */
    public static DragonAtmosphereMusicMood musicMood() {
        return INSTANCE.musicMood;
    }

    private void initializeInternal() {
        Path configDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        options = DragonAtmosphereClientOptions.load(configDirectory);
        ClientPlayNetworking.registerGlobalReceiver(
                DragonAtmosphereSnapshotPayload.TYPE,
                (payload, context) -> context.client().execute(() -> acceptSnapshot(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                DragonAtmosphereCuePayload.TYPE,
                (payload, context) -> context.client().execute(() -> acceptCue(payload, context.client())));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void acceptSnapshot(DragonAtmosphereSnapshotPayload payload) {
        encounters.put(
                payload.dragonEntityId(),
                new EncounterVisualState(
                        new Vec3(payload.x(), payload.y(), payload.z()), payload.phase(), payload.state(), clientTicks));
    }

    private void acceptCue(DragonAtmosphereCuePayload payload, Minecraft client) {
        applyCameraImpulse(payload.cue());
        if (client.level == null || client.player == null) {
            return;
        }
        Vec3 cuePosition = new Vec3(payload.x(), payload.y(), payload.z());
        if (client.player.position().distanceToSqr(cuePosition) <= 144.0D * 144.0D) {
            spawnCueParticles(client.level, cuePosition, payload.cue());
        }
    }

    private void tick(Minecraft client) {
        clientTicks++;
        if (client.level != observedLevel) {
            observedLevel = client.level;
            encounters.clear();
            currentFog = DragonAtmosphereProfile.forEncounter(
                    DragonAtmospherePhase.DORMANT, DragonAtmosphereState.DORMANT, 0.0D, 0.0F);
            musicMood = DragonAtmosphereMusicMood.NONE;
            cameraImpulse = 0.0F;
        }
        cameraImpulse = Math.max(0.0F, cameraImpulse - 0.035F);
        if (client.level == null || client.player == null) {
            return;
        }

        encounters.entrySet().removeIf(entry -> clientTicks - entry.getValue().snapshotTick > SNAPSHOT_STALE_TICKS);
        EncounterVisualState nearest = nearestEncounter(client.player.position());
        if (nearest == null) {
            currentFog = DragonAtmosphereProfile.forEncounter(
                    DragonAtmospherePhase.DORMANT, DragonAtmosphereState.DORMANT, 0.0D, 0.0F);
            musicMood = DragonAtmosphereMusicMood.NONE;
            return;
        }

        double distance = client.player.position().distanceTo(nearest.position);
        currentFog = DragonAtmosphereProfile.forEncounter(
                nearest.phase, nearest.state, distance, 0.0F);
        musicMood = moodFor(nearest);
    }

    private EncounterVisualState nearestEncounter(Vec3 playerPosition) {
        EncounterVisualState nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (EncounterVisualState candidate : encounters.values()) {
            if (candidate.state == DragonAtmosphereState.CORPSE) {
                continue;
            }
            double distance = playerPosition.distanceToSqr(candidate.position);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private DragonAtmosphereMusicMood moodFor(EncounterVisualState state) {
        if (state.state == DragonAtmosphereState.DYING) {
            return DragonAtmosphereMusicMood.DEATH;
        }
        if (state.state == DragonAtmosphereState.AWAKENING) {
            return DragonAtmosphereMusicMood.AWAKENING;
        }
        return switch (state.phase) {
            case STORM -> DragonAtmosphereMusicMood.STORM;
            case SOLAR -> DragonAtmosphereMusicMood.SOLAR;
            case DORMANT, MOUNTAIN, DEATH -> DragonAtmosphereMusicMood.NONE;
        };
    }

    private void applyCameraImpulse(DragonAtmosphereCue cue) {
        float amount = switch (cue) {
            case AWAKEN -> 0.38F;
            case TAKEOFF -> 0.24F;
            case PHASE_STORM, PHASE_SOLAR -> 0.32F;
            case DIVE_IMPACT -> 0.58F;
            case WING_IMPACT -> 0.46F;
            case STORM_STRIKE -> 0.54F;
            case SOLAR_IGNITION -> options.reducedFlashes() ? 0.26F : 0.42F;
            case DEATH_IMPACT -> 0.72F;
            case CORPSE_SETTLE -> 0.20F;
        };
        cameraImpulse = Math.max(cameraImpulse, amount * options.cameraIntensity());
    }

    private void spawnCueParticles(ClientLevel level, Vec3 position, DragonAtmosphereCue cue) {
        int count = switch (cue) {
            case DIVE_IMPACT, DEATH_IMPACT -> 18;
            case STORM_STRIKE, SOLAR_IGNITION -> 14;
            case AWAKEN, TAKEOFF, PHASE_STORM, PHASE_SOLAR -> 10;
            case WING_IMPACT -> 12;
            case CORPSE_SETTLE -> 8;
        };
        for (int index = 0; index < count; index++) {
            double x = position.x + random.nextGaussian() * 2.0D;
            double y = position.y + random.nextDouble() * 2.5D;
            double z = position.z + random.nextGaussian() * 2.0D;
            switch (cue) {
                case STORM_STRIKE, PHASE_STORM ->
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0D, 0.08D, 0.0D);
                case SOLAR_IGNITION, PHASE_SOLAR ->
                        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 0.06D, 0.0D);
                case DIVE_IMPACT, WING_IMPACT, DEATH_IMPACT ->
                        level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.05D, 0.0D);
                case AWAKEN, TAKEOFF -> level.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0D, 0.04D, 0.0D);
                case CORPSE_SETTLE -> level.addParticle(ParticleTypes.ASH, x, y, z, 0.0D, 0.025D, 0.0D);
            }
        }
    }

    private record EncounterVisualState(
            Vec3 position, DragonAtmospherePhase phase, DragonAtmosphereState state, long snapshotTick) {
    }
}
