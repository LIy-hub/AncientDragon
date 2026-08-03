package com.liy.ancientdragon.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Exact recognition of the unused reinforced-deepslate frame in every Ancient City centre. */
final class AncientCityPortalFrame {
    static final int FRAME_WIDTH = 22;
    static final int FRAME_HEIGHT = 8;
    static final int GAPPED_FRAME_BLOCK_COUNT = 55;
    static final int FULL_FRAME_BLOCK_COUNT = 56;
    static final int PORTAL_BLOCK_COUNT = 120;
    private static final int COMPONENT_SCAN_LIMIT = 128;

    private AncientCityPortalFrame() {
    }

    static Optional<Match> find(LevelReader level, BlockPos clicked) {
        return inspect(level, clicked).match();
    }

    static Inspection inspect(LevelReader level, BlockPos clicked) {
        if (!level.getBlockState(clicked).is(Blocks.REINFORCED_DEEPSLATE)) {
            return new Inspection(Optional.empty(), 0, 0, 0, 0);
        }
        Set<BlockPos> connected = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(clicked.immutable());
        while (!pending.isEmpty()) {
            BlockPos position = pending.removeFirst();
            if (!visited.add(position) || !level.getBlockState(position).is(Blocks.REINFORCED_DEEPSLATE)) {
                continue;
            }
            connected.add(position);
            if (connected.size() > COMPONENT_SCAN_LIMIT) {
                return inspection(connected);
            }
            for (Direction direction : Direction.values()) {
                pending.addLast(position.relative(direction).immutable());
            }
        }
        return inspection(connected);
    }

    static Optional<Match> validate(Set<BlockPos> blocks) {
        if (blocks.size() != GAPPED_FRAME_BLOCK_COUNT && blocks.size() != FULL_FRAME_BLOCK_COUNT) {
            return Optional.empty();
        }
        int minimumX = blocks.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maximumX = blocks.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minimumY = blocks.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maximumY = blocks.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int minimumZ = blocks.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maximumZ = blocks.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        if (maximumY - minimumY != FRAME_HEIGHT - 1) {
            return Optional.empty();
        }

        Match match;
        if (minimumZ == maximumZ && maximumX - minimumX == FRAME_WIDTH - 1) {
            match = new Match(Direction.Axis.X, new BlockPos(minimumX, minimumY, minimumZ));
        } else if (minimumX == maximumX && maximumZ - minimumZ == FRAME_WIDTH - 1) {
            match = new Match(Direction.Axis.Z, new BlockPos(minimumX, minimumY, minimumZ));
        } else {
            return Optional.empty();
        }
        // The packaged centre templates have one of the two central top blocks absent. In a
        // generated city an overlapping placement can fill that cell, producing a 56-block
        // version. Both shapes occur naturally in unmodified 26.1.2 worlds.
        for (int topGap : new int[] {10, 11, -1}) {
            if (blocks.equals(expectedFrameBlocks(match, topGap))) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    private static Inspection inspection(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return new Inspection(Optional.empty(), 0, 0, 0, 0);
        }
        int minimumX = blocks.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maximumX = blocks.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minimumY = blocks.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maximumY = blocks.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int minimumZ = blocks.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maximumZ = blocks.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        return new Inspection(
                validate(blocks),
                blocks.size(),
                maximumX - minimumX + 1,
                maximumY - minimumY + 1,
                maximumZ - minimumZ + 1);
    }

    static Set<BlockPos> expectedFrameBlocks(Match match, int topGap) {
        Set<BlockPos> result = new HashSet<>(FULL_FRAME_BLOCK_COUNT);
        for (int horizontal = 0; horizontal < FRAME_WIDTH; horizontal++) {
            result.add(match.position(horizontal, 0));
            if (horizontal != topGap) {
                result.add(match.position(horizontal, FRAME_HEIGHT - 1));
            }
        }
        for (int vertical = 1; vertical < FRAME_HEIGHT - 1; vertical++) {
            result.add(match.position(0, vertical));
            result.add(match.position(FRAME_WIDTH - 1, vertical));
        }
        return Set.copyOf(result);
    }

    record Inspection(Optional<Match> match, int connectedBlocks, int width, int height, int depth) {
    }

    record Match(Direction.Axis horizontalAxis, BlockPos lowerLeft) {
        Match {
            if (horizontalAxis == Direction.Axis.Y) {
                throw new IllegalArgumentException("An Ancient City portal cannot use the vertical axis");
            }
            lowerLeft = lowerLeft.immutable();
        }

        BlockPos position(int horizontalOffset, int verticalOffset) {
            return horizontalAxis == Direction.Axis.X
                    ? lowerLeft.offset(horizontalOffset, verticalOffset, 0)
                    : lowerLeft.offset(0, verticalOffset, horizontalOffset);
        }

        List<BlockPos> interior() {
            List<BlockPos> positions = new ArrayList<>(PORTAL_BLOCK_COUNT);
            for (int vertical = 1; vertical < FRAME_HEIGHT - 1; vertical++) {
                for (int horizontal = 1; horizontal < FRAME_WIDTH - 1; horizontal++) {
                    positions.add(position(horizontal, vertical));
                }
            }
            return List.copyOf(positions);
        }

        Vec3 center() {
            return horizontalAxis == Direction.Axis.X
                    ? new Vec3(lowerLeft.getX() + 10.5D, lowerLeft.getY() + 3.5D, lowerLeft.getZ() + 0.5D)
                    : new Vec3(lowerLeft.getX() + 0.5D, lowerLeft.getY() + 3.5D, lowerLeft.getZ() + 10.5D);
        }

        boolean isActive(LevelReader level) {
            return interior().stream().allMatch(position -> {
                BlockState state = level.getBlockState(position);
                return state.is(AncientDragonBlocks.ANCIENT_CITY_GATEWAY)
                        && state.getValue(AncientCityGatewayBlock.AXIS) == horizontalAxis;
            });
        }

        boolean canFill(LevelReader level) {
            return interior().stream().allMatch(position -> {
                BlockState state = level.getBlockState(position);
                return state.isAir() || state.is(AncientDragonBlocks.ANCIENT_CITY_GATEWAY);
            });
        }
    }
}
