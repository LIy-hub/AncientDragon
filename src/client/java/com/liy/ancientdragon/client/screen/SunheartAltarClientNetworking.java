package com.liy.ancientdragon.client.screen;

import com.liy.ancientdragon.block.SunheartAltarOpenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-only receiver that turns the server's approved altar-open request into a screen. */
public final class SunheartAltarClientNetworking {
    private SunheartAltarClientNetworking() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SunheartAltarOpenPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().player != null) {
                        context.client().setScreen(new SunheartAltarScreen(payload.altarPos(), payload.hand()));
                    }
                }));
    }
}
