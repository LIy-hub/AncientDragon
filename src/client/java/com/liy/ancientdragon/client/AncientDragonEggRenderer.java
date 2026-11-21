package com.liy.ancientdragon.client;

import com.liy.ancientdragon.block.AncientDragonEggBlock;
import com.liy.ancientdragon.block.AncientDragonEggBlockEntity;
import com.liy.blendlib.api.BlendModelKey;
import com.liy.blendlib.fabric.client.blockentity.BlendBlockEntityRenderState;
import com.liy.blendlib.fabric.client.blockentity.BlendBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

final class AncientDragonEggRenderer implements BlockEntityRenderer<AncientDragonEggBlockEntity, BlendBlockEntityRenderState> {
    private final BlendBlockEntityRenderer<AncientDragonEggBlockEntity> intact;
    private final BlendBlockEntityRenderer<AncientDragonEggBlockEntity> cracked;

    AncientDragonEggRenderer(BlockEntityRendererProvider.Context context) {
        intact = create(context, "ancient_dragon:block/dragon_egg");
        cracked = create(context, "ancient_dragon:block/dragon_egg_cracked");
    }

    private static BlendBlockEntityRenderer<AncientDragonEggBlockEntity> create(
            BlockEntityRendererProvider.Context context, String key) {
        return BlendBlockEntityRenderer.<AncientDragonEggBlockEntity>builder(context, BlendModelKey.parse(key))
                .staticRestPose().build();
    }

    @Override
    public BlendBlockEntityRenderState createRenderState() {
        return intact.createRenderState();
    }

    @Override
    public void extractRenderState(AncientDragonEggBlockEntity egg, BlendBlockEntityRenderState state,
                                   float partialTick, Vec3 camera, CrumblingOverlay overlay) {
        var renderer = egg.getBlockState().getValue(AncientDragonEggBlock.CRACKED) ? cracked : intact;
        renderer.extractRenderState(egg, state, partialTick, camera, overlay);
    }

    @Override
    public void submit(BlendBlockEntityRenderState state, PoseStack pose,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        intact.submit(state, pose, collector, camera);
    }
}
