package com.liy.ancientdragon.block;

import com.liy.ancientdragon.chronicle.DragonChronicleEvent;
import com.liy.ancientdragon.chronicle.DragonChronicleService;
import com.liy.ancientdragon.worldgen.SacredMountainTravelService;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Echo-shard activation ritual for the exact vanilla Ancient City centre frame. */
public final class AncientCityGatewayActivation {
    private AncientCityGatewayActivation() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!player.getItemInHand(hand).is(Items.ECHO_SHARD)
                    || !level.getBlockState(hitResult.getBlockPos()).is(Blocks.REINFORCED_DEEPSLATE)) {
                return InteractionResult.PASS;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.SUCCESS;
            }
            var inspection = AncientCityPortalFrame.inspect(serverLevel, hitResult.getBlockPos());
            if (inspection.match().isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                        "message.ancient_dragon.gateway_invalid_frame",
                        inspection.connectedBlocks(),
                        inspection.width(),
                        inspection.height(),
                        inspection.depth()));
                return InteractionResult.FAIL;
            }
            AncientCityPortalFrame.Match match = inspection.match().orElseThrow();
            if (match.isActive(serverLevel)) {
                player.sendSystemMessage(Component.translatable("message.ancient_dragon.gateway_already_active"));
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!match.canFill(serverLevel)) {
                player.sendSystemMessage(Component.translatable("message.ancient_dragon.gateway_obstructed"));
                return InteractionResult.FAIL;
            }

            SacredMountainTravelService.Preparation preparation =
                    SacredMountainTravelService.prepareDestination(serverLevel);
            if (!preparation.succeeded()) {
                player.sendSystemMessage(Component.translatable(
                        "message.ancient_dragon.gateway_destination_failed",
                        Component.translatable(preparation.failure().orElseThrow().translationKey())));
                return InteractionResult.FAIL;
            }

            var portalState = AncientDragonBlocks.ANCIENT_CITY_GATEWAY.defaultBlockState()
                    .setValue(AncientCityGatewayBlock.AXIS, match.horizontalAxis());
            for (var position : match.interior()) {
                serverLevel.setBlock(position, portalState, Block.UPDATE_ALL);
            }
            if (!player.hasInfiniteMaterials()) {
                player.getItemInHand(hand).consume(1, player);
            }
            Vec3 center = match.center();
            serverLevel.playSound(
                    null, center.x, center.y, center.z,
                    SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.5F, 0.65F);
            serverLevel.playSound(
                    null, center.x, center.y, center.z,
                    SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.8F, 0.7F);
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SCULK_CHARGE_POP,
                    center.x, center.y, center.z,
                    80, 5.0D, 2.5D, 0.2D, 0.03D);
            if (player instanceof ServerPlayer serverPlayer) {
                DragonChronicleService.grant(serverPlayer, DragonChronicleEvent.GATE_OPENED);
            }
            player.sendSystemMessage(Component.translatable("message.ancient_dragon.gateway_activated"));
            return InteractionResult.SUCCESS_SERVER;
        });
    }
}
