package com.liy.ancientdragon.client;

import com.liy.ancientdragon.animation.AncientDragonAnimations;
import com.liy.ancientdragon.client.atmosphere.AncientDragonAtmosphereController;
import com.liy.ancientdragon.client.pose.AncientDragonPoseSystem;
import com.liy.ancientdragon.client.screen.SunheartAltarScreen;
import com.liy.ancientdragon.entity.AncientDragonEntities;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.ancientdragon.entity.AncientDragonScale;
import com.liy.ancientdragon.inventory.AncientDragonMenus;
import com.liy.blendlib.api.BlendModelKey;
import com.liy.blendlib.fabric.client.entity.BlendEntityRenderer;
import com.liy.blendlib.fabric.client.entity.BlendEntityRenderers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** Client-only binding between the server host and the strict BlendLib v011 asset. */
public final class AncientDragonClient implements ClientModInitializer {
    private static final BlendModelKey DRAGON_MODEL =
            BlendModelKey.parse("ancient_dragon:entity/ancient_dragon");
    private static final AncientDragonPoseSystem POSE = new AncientDragonPoseSystem();

    @Override
    public void onInitializeClient() {
        AncientDragonAtmosphereController.initialize();
        MenuScreens.register(AncientDragonMenus.SUNHEART_ALTAR, SunheartAltarScreen::new);
        POSE.registerLifecycleHooks();
        BlendEntityRenderers.register(
                AncientDragonEntities.ANCIENT_DRAGON,
                context -> BlendEntityRenderer.<AncientDragonEntity>builder(context, DRAGON_MODEL)
                        .synchronizedSkinnedAnimation((entity, request) -> AncientDragonAnimations.COMBAT_IDLE)
                        .rootRotation(POSE::rootRotation)
                        .poseModifier(POSE::modify)
                        .shadowRadius(AncientDragonScale.blocks(5.0F))
                        .build());
        EntityRendererRegistry.register(AncientDragonEntities.ANCIENT_DRAGON_PART, NoopRenderer::new);
    }
}
