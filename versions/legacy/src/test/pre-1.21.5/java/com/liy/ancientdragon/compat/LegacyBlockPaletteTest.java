package com.liy.ancientdragon.compat;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyBlockPaletteTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void everyFloraReplacementRemainsARealRotatableNonAirBlock() throws Exception {
        for (String source : new String[] {"minecraft:firefly_bush", "minecraft:closed_eyeblossom", "minecraft:open_eyeblossom"}) {
            var state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, LegacyBlockPalette.resolve(source), false).blockState();
            assertFalse(state.isAir(), source);
            for (var rotation : Rotation.values()) assertFalse(state.rotate(rotation).isAir(), source);
        }
    }
}
