package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SacredMountainStructureAssetTest {
    private final SacredMountainStructureAsset asset = SacredMountainStructureAsset.loadDefault();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void manifestFreezesTheLiveAuthoringCropAndProvenance() {
        SacredMountainStructureAsset.Manifest manifest = asset.manifest();

        assertEquals(SacredMountainStructureAsset.DEFAULT_ASSET_ID, manifest.assetId());
        assertEquals(4, manifest.shapeVersion());
        assertEquals("6e20ef30-1f9c-4498-9591-351ccf02b28d", manifest.buildId());
        assertEquals(984221L, manifest.seed());
        assertEquals(4790, manifest.sourceDataVersion());
        assertEquals(11, manifest.sourceAnchorX());
        assertEquals(-61, manifest.sourceAnchorY());
        assertEquals(-1, manifest.sourceAnchorZ());
        assertEquals(139, manifest.dragonRestX());
        assertEquals(205, manifest.dragonRestY());
        assertEquals(1, manifest.dragonRestZ());
        assertEquals(270.0F, manifest.dragonYaw());
        assertEquals(-384, manifest.minimumX());
        assertEquals(1, manifest.minimumY());
        assertEquals(-384, manifest.minimumZ());
        assertEquals(383, manifest.maximumX());
        assertEquals(310, manifest.maximumY());
        assertEquals(383, manifest.maximumZ());
        assertEquals(768, manifest.width());
        assertEquals(310, manifest.height());
        assertEquals(768, manifest.depth());
        assertEquals(48, manifest.tilesX());
        assertEquals(48, manifest.tilesZ());
        assertEquals(1372, manifest.nonEmptyTiles());
        assertEquals(30_640_378L, manifest.nonAirBlocks());
        assertEquals("99964e3f77f39518bffc244d9f55a888346e6ab7c41acca66e217756aa9c4294",
                manifest.structureSha256());
    }

    @Test
    void enchantingTablesAreAbsentFromAllThreeAuthoredSummits() {
        assertState(-162, 252, 112, "minecraft:air");
        assertState(-88, 283, -168, "minecraft:air");
        assertState(88, 244, 176, "minecraft:air");

        List<SacredMountainStructureAsset.BlockEntityMarker> markers = asset.manifest().blockEntities();
        assertTrue(markers.isEmpty());
    }

    @Test
    void omittedExteriorTilesAreCanonicalAir() {
        assertFalse(asset.loadTile(0, 0).isPresent());
        assertFalse(asset.loadTile(47, 47).isPresent());
    }

    @Test
    void everyPackagedTilePassesBothHashesAndMatchesTheFrozenBlockCount() {
        long nonAirBlocks = asset.tileCoordinates().stream()
                .mapToLong(coordinate -> asset.loadTile(coordinate.x(), coordinate.z())
                        .orElseThrow()
                        .nonAirBlocks())
                .sum();

        assertEquals(asset.manifest().nonEmptyTiles(), asset.tileCoordinates().size());
        assertEquals(asset.manifest().nonAirBlocks(), nonAirBlocks);
    }

    @Test
    void everyExtractedPaletteStateParsesAndSupportsAllPlacementRotations()
            throws CommandSyntaxException {
        Set<String> states = new HashSet<>();
        for (SacredMountainStructureAsset.TileCoordinate coordinate : asset.tileCoordinates()) {
            states.addAll(asset.loadTile(coordinate.x(), coordinate.z()).orElseThrow().palette());
        }

        assertEquals(111, states.size());
        assertTrue(states.contains("minecraft:air"));
        assertFalse(states.contains("minecraft:enchanting_table"));
        for (String serialized : states) {
            var state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, serialized, false).blockState();
            state.rotate(Rotation.NONE);
            state.rotate(Rotation.CLOCKWISE_90);
            state.rotate(Rotation.CLOCKWISE_180);
            state.rotate(Rotation.COUNTERCLOCKWISE_90);
        }
    }

    private void assertState(int localX, int localY, int localZ, String expected) {
        SacredMountainStructureAsset.Manifest manifest = asset.manifest();
        int shiftedX = localX - manifest.minimumX();
        int shiftedZ = localZ - manifest.minimumZ();
        int tileX = Math.floorDiv(shiftedX, manifest.tileSize());
        int tileZ = Math.floorDiv(shiftedZ, manifest.tileSize());
        int withinTileX = Math.floorMod(shiftedX, manifest.tileSize());
        int withinTileZ = Math.floorMod(shiftedZ, manifest.tileSize());
        SacredMountainStructureAsset.Tile tile = asset.loadTile(tileX, tileZ).orElseThrow();
        assertEquals(expected, tile.stateAt(withinTileX, localY, withinTileZ));
    }
}
