package com.liy.ancientdragon.mixin;

import com.liy.ancientdragon.item.AncientDragonItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native pre-component elytra renderer otherwise accepts only Items.ELYTRA. */
@Mixin(ElytraLayer.class)
abstract class LegacyElytraLayerMixin extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {
    @Shadow @Final private ElytraModel<LivingEntity> elytraModel;
    @Unique private static final ResourceLocation ANCIENT_DRAGON_WINGS = ResourceLocation.fromNamespaceAndPath(
            "ancient_dragon", "textures/entity/equipment/wings/sky_wing.png");
    protected LegacyElytraLayerMixin(RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> parent) { super(parent); }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void ancientDragon$renderWings(PoseStack poses, MultiBufferSource buffers, int light, LivingEntity entity,
            float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch, CallbackInfo callback) {
        var stack = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!AncientDragonItems.grantsSkyWingFlight(stack)) return;
        poses.pushPose();
        poses.translate(0.0F, 0.0F, 0.125F);
        getParentModel().copyPropertiesTo(elytraModel);
        elytraModel.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
        var vertices = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(ANCIENT_DRAGON_WINGS), stack.hasFoil());
        elytraModel.renderToBuffer(poses, vertices, light, OverlayTexture.NO_OVERLAY);
        poses.popPose();
        callback.cancel();
    }
}
