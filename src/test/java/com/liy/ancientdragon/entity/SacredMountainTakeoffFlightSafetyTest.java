package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonPoseDynamics;
import com.liy.ancientdragon.animation.pose.DragonPoseState;
import com.liy.ancientdragon.animation.pose.DragonPoseState.AttentionMode;
import com.liy.ancientdragon.animation.pose.DragonPoseState.Maneuver;
import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Replays the protected takeoff climb against the frozen, player-approved mountain asset. */
final class SacredMountainTakeoffFlightSafetyTest {
    private static final Vec3 REST = new Vec3(150.0D, 144.0D, 0.0D);
    private static final double ROOT_WIDTH = AncientDragonScale.blocks(2.0D);
    private static final double ROOT_HEIGHT = AncientDragonScale.blocks(2.0D);

    @Test
    void rootCannotBeBlockedAndCruiseWingsOnlyOpenInClearSky() {
        AssetOccupancy occupancy = new AssetOccupancy(SacredMountainStructureAsset.loadDefault());
        DragonFlightController controller = new DragonFlightController();
        DragonPoseDynamics dynamics = new DragonPoseDynamics();
        Vec3 position = REST;
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);
        dynamics.reset(com.liy.ancientdragon.animation.pose.DragonStagedPose.matching(pose(controller)));

        int stateTick = 0;
        while (stateTick < 20_000 && !SacredMountainFlightPath.takeoffClearForCruise(
                position, REST, stateTick > AncientDragonEntity.TAKEOFF_DURATION_TICKS)) {
            stateTick++;
            boolean animationComplete = stateTick > AncientDragonEntity.TAKEOFF_DURATION_TICKS;
            DragonFlightController.FlightStep step = controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            SacredMountainFlightPath.takeoffWorldPosition(REST),
                            SacredMountainFlightPath.takeoffMaximumSpeed(animationComplete),
                            0.0D,
                            DragonFlightController.FlightEnvelope.TAKEOFF));
            position = position.add(step.movement());
            dynamics.advance(pose(controller));
            assertClearRoot(occupancy, position, ROOT_WIDTH, ROOT_HEIGHT,
                    "takeoff root tick=" + stateTick);
        }

        assertTrue(stateTick > AncientDragonEntity.TAKEOFF_DURATION_TICKS,
                "cruise must not open at the old fixed 81-tick boundary");
        assertTrue(SacredMountainFlightPath.takeoffClearForCruise(position, REST, true),
                "protected climb did not reach the high assembly gate");

        DragonPoseState pose = pose(controller);
        AncientDragonCollisionRig.SolvedFrame cruise = AncientDragonCollisionRig.instance().solve(
                AncientDragonCollisionRig.instance().sample(AncientDragonCollisionRig.CRUISE, 1, true),
                pose,
                dynamics.snapshot(),
                "ancient_dragon:fly_cruise");
        for (AncientDragonPartKind part : AncientDragonPartKind.values()) {
            Vec3 center = position.add(pose.orientation().rotate(cruise.position(part)));
            AncientDragonPartKind.ProjectedDimensions dimensions = part.project(
                    pose.orientation().multiply(cruise.rotation(part)));
            assertClearCentered(
                    occupancy, center, dimensions.xSize(), dimensions.ySize(), dimensions.zSize(),
                    "cruise handoff part=" + part.serializedName());
        }
    }

    private static DragonPoseState pose(DragonFlightController controller) {
        return new DragonPoseState(
                controller.orientation(),
                controller.torsoCurve(),
                controller.tailCurve(),
                0.0D,
                0.0D,
                AttentionMode.CLEARED,
                -1,
                Maneuver.NONE);
    }

    private static void assertClearRoot(
            AssetOccupancy occupancy, Vec3 center, double width, double height, String phase) {
        String collision = occupancy.firstSolidInBox(
                center.x - width * 0.5D,
                center.y,
                center.z - width * 0.5D,
                center.x + width * 0.5D,
                center.y + height,
                center.z + width * 0.5D);
        assertTrue(collision == null, () -> phase + " at " + format(center) + " collided with " + collision);
    }

    private static void assertClearCentered(
            AssetOccupancy occupancy,
            Vec3 center,
            double width,
            double height,
            double depth,
            String phase) {
        String collision = occupancy.firstSolidInBox(
                center.x - width * 0.5D,
                center.y - height * 0.5D,
                center.z - depth * 0.5D,
                center.x + width * 0.5D,
                center.y + height * 0.5D,
                center.z + depth * 0.5D);
        assertTrue(collision == null, () -> phase + " at " + format(center) + " collided with " + collision);
    }

    private static String format(Vec3 position) {
        return String.format(java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)",
                position.x, position.y, position.z);
    }

    private static final class AssetOccupancy {
        private final SacredMountainStructureAsset.Manifest manifest;
        private final Map<Long, SacredMountainStructureAsset.Tile> tiles = new HashMap<>();

        private AssetOccupancy(SacredMountainStructureAsset asset) {
            manifest = asset.manifest();
            for (SacredMountainStructureAsset.TileCoordinate coordinate : asset.tileCoordinates()) {
                asset.loadTile(coordinate.x(), coordinate.z()).ifPresent(tile ->
                        tiles.put(key(tile.x(), tile.z()), tile));
            }
        }

        private String firstSolidInBox(
                double minimumX, double minimumY, double minimumZ,
                double maximumX, double maximumY, double maximumZ) {
            for (int worldX = (int) Math.floor(minimumX); worldX <= (int) Math.floor(maximumX); worldX++) {
                for (int worldZ = (int) Math.floor(minimumZ); worldZ <= (int) Math.floor(maximumZ); worldZ++) {
                    for (int worldY = (int) Math.floor(minimumY); worldY <= (int) Math.floor(maximumY); worldY++) {
                        if (solid(worldX, worldY, worldZ)) {
                            return worldX + "," + worldY + "," + worldZ;
                        }
                    }
                }
            }
            return null;
        }

        private boolean solid(int worldX, int worldY, int worldZ) {
            int localX = worldX - manifest.sourceAnchorX();
            int localY = worldY - manifest.sourceAnchorY();
            int localZ = worldZ - manifest.sourceAnchorZ();
            if (localX < manifest.minimumX() || localX > manifest.maximumX()
                    || localY < manifest.minimumY() || localY > manifest.maximumY()
                    || localZ < manifest.minimumZ() || localZ > manifest.maximumZ()) {
                return false;
            }
            int tileX = Math.floorDiv(localX - manifest.minimumX(), manifest.tileSize());
            int tileZ = Math.floorDiv(localZ - manifest.minimumZ(), manifest.tileSize());
            SacredMountainStructureAsset.Tile tile = tiles.get(key(tileX, tileZ));
            if (tile == null || localY < tile.minimumLocalY()
                    || localY >= tile.minimumLocalY() + tile.height()) {
                return false;
            }
            return !"minecraft:air".equals(tile.stateAt(
                    Math.floorMod(localX - manifest.minimumX(), manifest.tileSize()),
                    localY,
                    Math.floorMod(localZ - manifest.minimumZ(), manifest.tileSize())));
        }

        private static long key(int x, int z) {
            return ((long) x << 32) ^ Integer.toUnsignedLong(z);
        }
    }
}
