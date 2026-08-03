package com.liy.ancientdragon.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

final class AncientCityPortalFrameTest {
    @Test
    void recognizesTheReinforcedDeepslateLoadedFromEveryVanillaCityCentreTemplate() throws IOException {
        for (int variant = 1; variant <= 3; variant++) {
            String path = "/data/minecraft/structure/ancient_city/city_center/city_center_"
                    + variant + ".nbt";
            var stream = AncientCityPortalFrameTest.class.getResourceAsStream(path);
            if (stream == null) {
                throw new AssertionError("Missing vanilla structure resource " + path);
            }
            Set<BlockPos> reinforced = new HashSet<>();
            try (stream) {
                var root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                var palette = root.getListOrEmpty("palette");
                Set<Integer> reinforcedStates = new HashSet<>();
                for (int index = 0; index < palette.size(); index++) {
                    if ("minecraft:reinforced_deepslate".equals(
                            palette.getCompoundOrEmpty(index).getStringOr("Name", ""))) {
                        reinforcedStates.add(index);
                    }
                }
                var blocks = root.getListOrEmpty("blocks");
                for (int index = 0; index < blocks.size(); index++) {
                    var block = blocks.getCompoundOrEmpty(index);
                    if (!reinforcedStates.contains(block.getIntOr("state", -1))) {
                        continue;
                    }
                    var position = block.getListOrEmpty("pos");
                    reinforced.add(new BlockPos(
                            position.getIntOr(0, Integer.MIN_VALUE),
                            position.getIntOr(1, Integer.MIN_VALUE),
                            position.getIntOr(2, Integer.MIN_VALUE)));
                }
            }
            assertEquals(AncientCityPortalFrame.GAPPED_FRAME_BLOCK_COUNT, reinforced.size(), path);
            assertTrue(AncientCityPortalFrame.validate(reinforced).isPresent(), path);
        }
    }

    @Test
    void acceptsEveryOfficialOrientationWithAGapOrTheGeneratedFullTopEdge() {
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            var match = new AncientCityPortalFrame.Match(axis, new BlockPos(120, -35, -240));
            for (int topGap : new int[] {10, 11, -1}) {
                var blocks = AncientCityPortalFrame.expectedFrameBlocks(match, topGap);
                int expectedCount = topGap < 0
                        ? AncientCityPortalFrame.FULL_FRAME_BLOCK_COUNT
                        : AncientCityPortalFrame.GAPPED_FRAME_BLOCK_COUNT;
                assertEquals(expectedCount, blocks.size());
                assertEquals(match, AncientCityPortalFrame.validate(blocks).orElseThrow());
            }
        }
    }

    @Test
    void rejectsAnAlmostOfficialFrameWithOneDisplacedBlock() {
        var match = new AncientCityPortalFrame.Match(Direction.Axis.X, BlockPos.ZERO);
        var changed = new HashSet<>(AncientCityPortalFrame.expectedFrameBlocks(match, 10));
        changed.remove(match.position(3, 0));
        changed.add(match.position(3, 1));
        assertFalse(AncientCityPortalFrame.validate(changed).isPresent());
    }

    @Test
    void interiorIsExactlyTwentyBySixAndDoesNotOverlapTheFrame() {
        var match = new AncientCityPortalFrame.Match(Direction.Axis.Z, new BlockPos(-20, 4, 70));
        var interior = match.interior();
        var frame = AncientCityPortalFrame.expectedFrameBlocks(match, 11);

        assertEquals(AncientCityPortalFrame.PORTAL_BLOCK_COUNT, interior.size());
        assertEquals(interior.size(), interior.stream().distinct().count());
        assertTrue(interior.stream().noneMatch(frame::contains));
        assertEquals(match.position(1, 1), interior.getFirst());
        assertEquals(match.position(20, 6), interior.getLast());
    }
}
