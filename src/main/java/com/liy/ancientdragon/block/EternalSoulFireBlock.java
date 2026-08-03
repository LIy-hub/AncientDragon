package com.liy.ancientdragon.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A soul-fire block that is safe to place in unsupported mountain air and cannot extinguish naturally. */
public final class EternalSoulFireBlock extends SoulFireBlock {
    public EternalSoulFireBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SoulFireBlock> codec() {
        return SoulFireBlock.CODEC;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        return permitsUnsupportedPlacement();
    }

    static boolean permitsUnsupportedPlacement() {
        return true;
    }
}
