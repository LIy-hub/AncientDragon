package com.liy.ancientdragon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The portable world relic containing the unique Ancient Dragon heart. It turns the finite
 * personal corpse shares into permanent mountain, sky and storm inheritances.
 */
public final class SunheartAltarBlock extends Block {
    public SunheartAltarBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        openScreen(level, pos, player);
        return level instanceof ServerLevel ? InteractionResult.SUCCESS_SERVER : InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        openScreen(level, pos, player);
        return level instanceof ServerLevel ? InteractionResult.SUCCESS_SERVER : InteractionResult.SUCCESS;
    }

    private static void openScreen(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new SunheartAltarMenu(
                            containerId, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.ancient_dragon.sunheart_altar")));
        }
    }
}
