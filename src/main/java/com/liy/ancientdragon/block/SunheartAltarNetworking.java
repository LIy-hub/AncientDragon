package com.liy.ancientdragon.block;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Registers the small bidirectional protocol used by the Sunheart Altar screen. */
public final class SunheartAltarNetworking {
    private SunheartAltarNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(SunheartAltarOpenPayload.TYPE, SunheartAltarOpenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SunheartAltarForgePayload.TYPE, SunheartAltarForgePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SunheartAltarForgePayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> SunheartAltarRituals.forge(context.player(), payload.altarPos(), payload.hand())));
    }

    public static void open(ServerPlayer player, BlockPos altarPos, InteractionHand hand) {
        if (ServerPlayNetworking.canSend(player, SunheartAltarOpenPayload.TYPE)) {
            ServerPlayNetworking.send(player, new SunheartAltarOpenPayload(altarPos, hand));
        }
    }
}
