package com.liy.ancientdragon.client;

import com.liy.ancientdragon.animation.AncientDragonAnimations;
import com.liy.ancientdragon.block.AncientDragonBlocks;
import com.liy.ancientdragon.client.atmosphere.AncientDragonAtmosphereController;
import com.liy.ancientdragon.client.pose.AncientDragonPoseSystem;
import com.liy.ancientdragon.client.screen.SunheartAltarScreen;
import com.liy.ancientdragon.entity.AncientDragonEntities;
import com.liy.ancientdragon.entity.AncientDragonEntity;
import com.liy.ancientdragon.entity.AncientDragonScale;
import com.liy.ancientdragon.entity.AncientDragonHatchling;
import com.liy.ancientdragon.inventory.AncientDragonMenus;
import com.liy.blendlib.api.BlendModelKey;
import com.liy.blendlib.fabric.client.entity.BlendEntityRenderer;
import com.liy.blendlib.fabric.client.entity.BlendEntityRotation;
import com.liy.blendlib.fabric.client.entity.BlendEntitySnapshotRequest;
import com.liy.blendlib.fabric.client.entity.BlendEntityRenderers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** Client-only binding between the server host and the strict BlendLib v011 asset. */
public final class AncientDragonClient implements ClientModInitializer {
    private static final BlendModelKey DRAGON_MODEL =
            BlendModelKey.parse("ancient_dragon:entity/ancient_dragon");
    private static final BlendModelKey HATCHLING_MODEL = BlendModelKey.parse("ancient_dragon:entity/hatchling");
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
        BlendEntityRenderers.register(AncientDragonEntities.HATCHLING,
                context -> BlendEntityRenderer.<AncientDragonHatchling>builder(context, HATCHLING_MODEL)
                        .synchronizedSkinnedAnimation((entity, request) ->
                                entity.onGround() ? AncientDragonHatchling.PERCH : AncientDragonHatchling.FLIGHT)
                        .rootRotation(AncientDragonClient::hatchlingRotation)
                        .shadowRadius(0.33F)
                        .build());
        BlockEntityRendererRegistry.register(AncientDragonBlocks.DRAGON_EGG_ENTITY, AncientDragonEggRenderer::new);
    }

    private static BlendEntityRotation hatchlingRotation(AncientDragonHatchling hatchling, BlendEntitySnapshotRequest request) {
        float partial = request.partialTick();
        float yaw = Mth.rotLerp(partial, hatchling.yRotO, hatchling.getYRot());
        float pitch = Mth.clamp(Mth.lerp(partial, hatchling.xRotO, hatchling.getXRot()), -25.0F, 23.0F)
                * hatchling.renderAirborneBlend(partial);
        var rotation = new Quaternionf().rotationY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(hatchling.renderBank(partial)));
        return BlendEntityRotation.normalized(rotation.x, rotation.y, rotation.z, rotation.w);
    }
}
