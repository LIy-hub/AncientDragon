package com.liy.ancientdragon.client;

import com.liy.ancientdragon.entity.AncientDragonHatchling;
import com.liy.ancientdragon.entity.HatchlingVariant;
import com.liy.blendlib.api.BlendModelKey;
import com.liy.blendlib.fabric.client.entity.BlendEntityRenderer;
import com.liy.blendlib.fabric.client.entity.BlendEntityRenderState;
import com.liy.blendlib.fabric.client.entity.BlendEntityRotation;
import com.liy.blendlib.fabric.client.entity.BlendEntitySnapshotRequest;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** Selects a prepared BlendLib material preset using the server-synced hatchling variant. */
final class AncientDragonHatchlingRenderer extends EntityRenderer<AncientDragonHatchling, BlendEntityRenderState> {
    private final Map<HatchlingVariant, BlendEntityRenderer<AncientDragonHatchling>> variants =
            new EnumMap<>(HatchlingVariant.class);

    AncientDragonHatchlingRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.33F;
        for (HatchlingVariant variant : HatchlingVariant.values()) {
            String model = variant == HatchlingVariant.AZURE ? "hatchling" : "hatchling/" + variant.id();
            variants.put(variant, BlendEntityRenderer
                    .<AncientDragonHatchling>builder(context, BlendModelKey.parse("ancient_dragon:entity/" + model))
                    .synchronizedSkinnedAnimation((entity, request) ->
                            entity.onGround() ? AncientDragonHatchling.PERCH : AncientDragonHatchling.FLIGHT)
                    .rootRotation(AncientDragonHatchlingRenderer::rotation)
                    .shadowRadius(shadowRadius)
                    .build());
        }
    }

    @Override
    public boolean shouldRender(AncientDragonHatchling entity, Frustum frustum, double x, double y, double z) {
        return variants.get(entity.getVariant()).shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public BlendEntityRenderState createRenderState() {
        return new BlendEntityRenderState();
    }

    @Override
    public void extractRenderState(AncientDragonHatchling entity, BlendEntityRenderState state, float partialTick) {
        variants.get(entity.getVariant()).extractRenderState(entity, state, partialTick);
    }

    @Override
    public void submit(BlendEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        // The captured snapshot already contains the selected model and its material bindings.
        variants.get(HatchlingVariant.AZURE).submit(state, pose, collector, camera);
    }

    private static BlendEntityRotation rotation(AncientDragonHatchling hatchling, BlendEntitySnapshotRequest request) {
        float partial = request.partialTick();
        float yaw = Mth.rotLerp(partial, hatchling.yRotO, hatchling.getYRot());
        float pitch = Mth.clamp(Mth.lerp(partial, hatchling.xRotO, hatchling.getXRot()), -25.0F, 23.0F)
                * hatchling.renderAirborneBlend(partial);
        var orientation = new Quaternionf().rotationY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(hatchling.renderBank(partial)));
        return BlendEntityRotation.normalized(orientation.x, orientation.y, orientation.z, orientation.w);
    }
}
