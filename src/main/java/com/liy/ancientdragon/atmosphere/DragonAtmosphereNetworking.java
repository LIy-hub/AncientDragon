package com.liy.ancientdragon.atmosphere;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the small clientbound protocol used by the atmosphere presentation layer. */
public final class DragonAtmosphereNetworking {
    private DragonAtmosphereNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(DragonAtmosphereSnapshotPayload.TYPE, DragonAtmosphereSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DragonAtmosphereCuePayload.TYPE, DragonAtmosphereCuePayload.CODEC);
    }
}
