package com.liy.ancientdragon.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SacredMountainGatewayTest {
    private final SacredMountainStructureAsset asset = SacredMountainStructureAsset.loadDefault();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void entranceIsTheFirstAuthoredRoutePointWithSafeAboveWaterHeadroom() {
        assertState(-145, 137, 58, "minecraft:basalt[axis=y]");
        assertState(-145, 138, 58, "minecraft:air");
        assertState(-145, 139, 58, "minecraft:air");
        assertEquals(77, SacredMountainStructure.naturalAnchorY(-64)
                + SacredMountainStructurePiece.MOUNTAIN_ENTRANCE_FEET_Y);
    }

    @Test
    void entranceAndRouteFacingRotateWithTheNaturalStructure() {
        BlockPos anchor = new BlockPos(1_000, -61, 2_000);
        BlockPos[] expected = {
                anchor.offset(-145, 138, 58),
                anchor.offset(-59, 138, -145),
                anchor.offset(144, 138, -59),
                anchor.offset(58, 138, 144)
        };
        for (int turns = 0; turns < 4; turns++) {
            BlockPos entrance = SacredMountainStructurePiece.mountainEntrance(anchor, turns);
            BlockPos nextRoutePoint = SacredMountainPlacementService.transform(
                    anchor, -121, 138, 45, turns);
            float routeYaw = yawToward(entrance, nextRoutePoint);

            assertEquals(expected[turns], entrance);
            assertEquals(routeYaw, SacredMountainStructurePiece.mountainEntranceYaw(turns), 0.0001F);
        }
    }

    private static float yawToward(BlockPos from, BlockPos to) {
        float yaw = (float) Math.toDegrees(Math.atan2(
                -(to.getX() - from.getX()), to.getZ() - from.getZ()));
        yaw %= 360.0F;
        return yaw < 0.0F ? yaw + 360.0F : yaw;
    }

    private void assertState(int localX, int localY, int localZ, String expected) {
        var manifest = asset.manifest();
        int shiftedX = localX - manifest.minimumX();
        int shiftedZ = localZ - manifest.minimumZ();
        int tileX = Math.floorDiv(shiftedX, manifest.tileSize());
        int tileZ = Math.floorDiv(shiftedZ, manifest.tileSize());
        int withinTileX = Math.floorMod(shiftedX, manifest.tileSize());
        int withinTileZ = Math.floorMod(shiftedZ, manifest.tileSize());
        var tile = asset.loadTile(tileX, tileZ).orElseThrow();
        assertEquals(expected, tile.stateAt(withinTileX, localY, withinTileZ));
    }
}
