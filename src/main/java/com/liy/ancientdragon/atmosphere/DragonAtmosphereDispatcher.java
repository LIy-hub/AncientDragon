package com.liy.ancientdragon.atmosphere;

import com.liy.ancientdragon.boss.AncientDragonBossState;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Sends visual-only dragon atmosphere events to nearby modded clients. */
public final class DragonAtmosphereDispatcher {
    public static final double EFFECT_RANGE_BLOCKS = DragonAtmosphereProfile.EFFECT_RANGE_BLOCKS;

    private DragonAtmosphereDispatcher() {
    }

    public static void snapshot(AncientDragonEntity dragon, ServerLevel level) {
        Vec3 atmosphereOrigin = dragon.encounterOrigin();
        DragonAtmosphereSnapshotPayload payload = new DragonAtmosphereSnapshotPayload(
                dragon.getId(),
                atmosphereOrigin.x,
                atmosphereOrigin.y,
                atmosphereOrigin.z,
                phaseFor(dragon),
                stateFor(dragon));
        sendNearby(level, atmosphereOrigin, payload);
    }

    public static void cue(AncientDragonEntity dragon, ServerLevel level, DragonAtmosphereCue cue, Vec3 position) {
        sendNearby(level, position, new DragonAtmosphereCuePayload(
                dragon.getId(), cue, position.x, position.y, position.z));
    }

    private static void sendNearby(ServerLevel level, Vec3 origin, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.around(level, origin, EFFECT_RANGE_BLOCKS)) {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static DragonAtmospherePhase phaseFor(AncientDragonEntity dragon) {
        AncientDragonBossState state = dragon.bossState();
        if (state == AncientDragonBossState.DEATH_RETURNING
                || state == AncientDragonBossState.DYING
                || state == AncientDragonBossState.CORPSE) {
            return DragonAtmospherePhase.DEATH;
        }
        return switch (dragon.encounterPhase()) {
            case MOUNTAIN -> DragonAtmospherePhase.MOUNTAIN;
            case STORM -> DragonAtmospherePhase.STORM;
            case SOLAR -> DragonAtmospherePhase.SOLAR;
        };
    }

    private static DragonAtmosphereState stateFor(AncientDragonEntity dragon) {
        return switch (dragon.bossState()) {
            case DORMANT -> DragonAtmosphereState.DORMANT;
            case AWAKENING -> DragonAtmosphereState.AWAKENING;
            case DEATH_RETURNING, DYING -> DragonAtmosphereState.DYING;
            case CORPSE -> DragonAtmosphereState.CORPSE;
            default -> DragonAtmosphereState.ACTIVE;
        };
    }
}
