package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.block.AncientDragonBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Maintains a sparse layer of permanent, floating soul fire around players exploring the Sacred Mountain. */
public final class SacredMountainSoulFireService {
    private static final int EMISSION_INTERVAL_TICKS = 40;
    private static final int SPAWN_ATTEMPTS = 32;
    private static final int HORIZONTAL_RADIUS = 18;
    private static final int VERTICAL_RADIUS = 9;
    private static final int MINIMUM_HORIZONTAL_DISTANCE = 6;
    private static final int NEARBY_HORIZONTAL_RADIUS = 10;
    private static final int NEARBY_VERTICAL_RADIUS = 6;
    private static final int MAX_NEARBY_FLAMES = 6;

    private SacredMountainSoulFireService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!shouldEmitOn(server.getTickCount())) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level() instanceof ServerLevel level
                        && SacredMountainRegion.containsCore(level, player.blockPosition())) {
                    emitNear(level, player);
                }
            }
        });
    }

    static boolean shouldEmitOn(int serverTick) {
        return serverTick % EMISSION_INTERVAL_TICKS == 0;
    }

    static boolean canOccupy(boolean isAir, boolean fluidEmpty, boolean withinMountain, boolean touchesBlock) {
        return isAir && fluidEmpty && withinMountain && touchesBlock;
    }

    private static void emitNear(ServerLevel level, ServerPlayer player) {
        BlockPos origin = player.blockPosition();
        if (countNearby(level, origin) >= MAX_NEARBY_FLAMES) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int xOffset = random.nextInt(HORIZONTAL_RADIUS * 2 + 1) - HORIZONTAL_RADIUS;
            int zOffset = random.nextInt(HORIZONTAL_RADIUS * 2 + 1) - HORIZONTAL_RADIUS;
            if (xOffset * xOffset + zOffset * zOffset
                    < MINIMUM_HORIZONTAL_DISTANCE * MINIMUM_HORIZONTAL_DISTANCE) {
                continue;
            }
            BlockPos candidate = origin.offset(
                    xOffset,
                    random.nextInt(VERTICAL_RADIUS * 2 + 1) - VERTICAL_RADIUS,
                    zOffset);
            if (!level.isInWorldBounds(candidate) || !SacredMountainRegion.containsCore(level, candidate)) {
                continue;
            }
            BlockState state = level.getBlockState(candidate);
            if (!canOccupy(
                    state.isAir(),
                    level.getFluidState(candidate).isEmpty(),
                    true,
                    touchesMountainSide(level, candidate))) {
                continue;
            }
            level.setBlock(candidate, AncientDragonBlocks.ETERNAL_SOUL_FIRE.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
    }

    private static boolean touchesMountainSide(ServerLevel level, BlockPos position) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(position.relative(direction));
            if (!neighbor.isAir() && neighbor.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int countNearby(ServerLevel level, BlockPos origin) {
        int count = 0;
        for (int yOffset = -NEARBY_VERTICAL_RADIUS; yOffset <= NEARBY_VERTICAL_RADIUS; yOffset++) {
            for (int xOffset = -NEARBY_HORIZONTAL_RADIUS; xOffset <= NEARBY_HORIZONTAL_RADIUS; xOffset++) {
                for (int zOffset = -NEARBY_HORIZONTAL_RADIUS; zOffset <= NEARBY_HORIZONTAL_RADIUS; zOffset++) {
                    if (level.getBlockState(origin.offset(xOffset, yOffset, zOffset))
                            .is(AncientDragonBlocks.ETERNAL_SOUL_FIRE)) {
                        count++;
                        if (count >= MAX_NEARBY_FLAMES) {
                            return count;
                        }
                    }
                }
            }
        }
        return count;
    }
}
