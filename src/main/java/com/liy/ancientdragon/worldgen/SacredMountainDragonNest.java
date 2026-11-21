package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.block.AncientDragonBlocks;
import com.liy.ancientdragon.block.AncientDragonEggBlock;
import com.liy.ancientdragon.boss.AncientDragonEncounterData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

final class SacredMountainDragonNest {
    private SacredMountainDragonNest() {
    }

    static BlockPos position(BlockPos anchor, int quarterTurns) {
        // Pearlescent froglight shelf beside the resting dragon, with three blocks of headroom.
        return SacredMountainPlacementService.transform(anchor, 132, 205, 10, quarterTurns);
    }

    static void ensureEgg(ServerLevel level, BlockPos position, String placementId) {
        if (!level.hasChunkAt(position)) {
            return;
        }
        var data = AncientDragonEncounterData.get(level);
        if (data.eggClaimed() || data.encounter().filter(
                encounter -> encounter.placementId().equals(placementId)).isEmpty()) {
            return;
        }
        BlockPos floor = position.below();
        if (level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            level.setBlock(position, AncientDragonBlocks.DRAGON_EGG.defaultBlockState()
                    .setValue(AncientDragonEggBlock.NESTED, true), Block.UPDATE_ALL);
        }
    }
}
