package com.liy.ancientdragon.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.liy.ancientdragon.animation.pose.DragonQuaternion;
import com.liy.ancientdragon.boss.AncientDragonBossState;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Replays the parent-root return path against the frozen, player-approved mountain asset. */
final class SacredMountainReturnFlightSafetyTest {
    private static final Vec3 REST = new Vec3(150.0D, 144.0D, 0.0D);
    private static final double ROOT_WIDTH = AncientDragonScale.blocks(2.0D);
    private static final double ROOT_HEIGHT = AncientDragonScale.blocks(2.0D);

    @Test
    void northNotchReturnKeepsTheControllingRootClearUntilTheProtectedRoostHandoff() {
        AssetOccupancy occupancy = new AssetOccupancy(SacredMountainStructureAsset.loadDefault());
        DragonFlightController controller = new DragonFlightController();
        Vec3 position = SacredMountainFlightPath.worldPosition(REST, SacredMountainFlightPath.ASSEMBLY_INDEX);
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);

        int approachIndex = 0;
        for (int tick = 0; tick < 20_000 && approachIndex < SacredMountainFlightPath.approachSize(); tick++) {
            SacredMountainFlightPath.ApproachWaypoint waypoint =
                    SacredMountainFlightPath.approachWaypoint(approachIndex);
            Vec3 destination = SacredMountainFlightPath.approachWorldPosition(REST, approachIndex);
            position = position.add(controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            destination,
                            waypoint.maximumSpeed(),
                            0.0D,
                            DragonFlightController.FlightEnvelope.CRUISE)).movement());
            if (approachIndex < SacredMountainFlightPath.approachSize() - 1) {
                assertClearRoot(occupancy, position, "approach " + waypoint.id() + " tick=" + tick);
            }
            double arrivalRadius = approachIndex == SacredMountainFlightPath.approachSize() - 1
                    ? 4.0D
                    : SacredMountainFlightPath.APPROACH_ARRIVAL_RADIUS;
            if (position.distanceTo(destination) <= arrivalRadius) {
                approachIndex++;
            }
        }
        assertEquals(SacredMountainFlightPath.approachSize(), approachIndex);

        for (int tick = 0; tick < AncientDragonEntity.LAND_DURATION_TICKS; tick++) {
            position = position.add(controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            REST, 0.28D, 0.0D, DragonFlightController.FlightEnvelope.ATTACK)).movement());
        }
        assertTrue(position.distanceTo(REST) <= 2.5D,
                "landing handoff error=" + position.distanceTo(REST));
        assertTrue(DragonTransitPolicy.bypassesTerrainCollision(AncientDragonBossState.RETURNING));
        assertTrue(DragonTransitPolicy.bypassesTerrainCollision(AncientDragonBossState.APPROACHING));
        assertTrue(DragonTransitPolicy.bypassesTerrainCollision(AncientDragonBossState.LANDING));
        assertTrue(!DragonTransitPolicy.bypassesTerrainCollision(AncientDragonBossState.CRUISING));
        assertTrue(!DragonTransitPolicy.bypassesTerrainCollision(AncientDragonBossState.ATTACKING));
    }

    @Test
    void permanentCorpseStopsBlockingPlayersWithoutChangingLiveDragonCollision() {
        assertTrue(DragonTransitPolicy.blocksPlayers(AncientDragonBossState.GROUND_COMBAT));
        assertTrue(DragonTransitPolicy.blocksPlayers(AncientDragonBossState.DYING));
        assertTrue(!DragonTransitPolicy.blocksPlayers(AncientDragonBossState.CORPSE));
    }

    @Test
    void lowPatrolRootRemainsClearAndInsideTheMarkedFlightBox() {
        AssetOccupancy occupancy = new AssetOccupancy(SacredMountainStructureAsset.loadDefault());
        DragonFlightController controller = new DragonFlightController();
        Vec3 position = SacredMountainFlightPath.worldPosition(REST, SacredMountainFlightPath.ASSEMBLY_INDEX);
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);
        int waypointIndex = SacredMountainFlightPath.nextIndex(SacredMountainFlightPath.ASSEMBLY_INDEX);
        int arrivals = 0;

        for (int tick = 0; tick < 20_000 && arrivals < SacredMountainFlightPath.size(); tick++) {
            SacredMountainFlightPath.Waypoint waypoint = SacredMountainFlightPath.waypoint(waypointIndex);
            Vec3 destination = SacredMountainFlightPath.worldPosition(REST, waypointIndex);
            position = position.add(controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            destination,
                            waypoint.maximumSpeed(),
                            0.0D,
                            DragonFlightController.FlightEnvelope.CRUISE)).movement());
            assertTrue(SacredMountainFlightPath.isInsideFlightBounds(position, REST, 0),
                    "patrol root escaped at tick=" + tick + " position=" + format(position));
            assertClearRoot(occupancy, position, "patrol " + waypoint.id() + " tick=" + tick);
            if (position.distanceTo(destination) <= SacredMountainFlightPath.ARRIVAL_RADIUS) {
                waypointIndex = SacredMountainFlightPath.nextIndex(waypointIndex);
                arrivals++;
            }
        }
        assertEquals(SacredMountainFlightPath.size(), arrivals);
    }

    @Test
    void combatDescentEndsAtTheWhitePlatformAnchorWithoutTerrainIntersection() {
        AssetOccupancy occupancy = new AssetOccupancy(SacredMountainStructureAsset.loadDefault());
        DragonFlightController controller = new DragonFlightController();
        Vec3 position = SacredMountainFlightPath.worldPosition(REST, SacredMountainFlightPath.ASSEMBLY_INDEX);
        controller.reset(DragonQuaternion.fromMinecraftYaw(90.0F), Vec3.ZERO);
        int approachIndex = 0;

        for (int tick = 0; tick < 20_000
                && approachIndex < SacredMountainFlightPath.combatApproachSize(); tick++) {
            SacredMountainFlightPath.ApproachWaypoint waypoint =
                    SacredMountainFlightPath.combatApproachWaypoint(approachIndex, 0);
            Vec3 destination = SacredMountainFlightPath.combatApproachWorldPosition(REST, approachIndex, 0);
            position = position.add(controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            destination,
                            waypoint.maximumSpeed(),
                            0.0D,
                            DragonFlightController.FlightEnvelope.CRUISE)).movement());
            assertTrue(SacredMountainFlightPath.isInsideFlightBounds(position, REST, 0));
            assertClearRoot(occupancy, position, "combat approach " + waypoint.id() + " tick=" + tick);
            double arrivalRadius = approachIndex == SacredMountainFlightPath.combatApproachSize() - 1
                    ? 4.0D
                    : SacredMountainFlightPath.APPROACH_ARRIVAL_RADIUS;
            if (position.distanceTo(destination) <= arrivalRadius) {
                approachIndex++;
            }
        }
        assertEquals(SacredMountainFlightPath.combatApproachSize(), approachIndex);

        Vec3 platform = SacredMountainFlightPath.combatPlatformWorldPosition(REST, 0);
        controller.reset(controller.orientation(), Vec3.ZERO);
        for (int tick = 0; tick < AncientDragonEntity.LAND_DURATION_TICKS; tick++) {
            position = position.add(controller.tick(
                    position,
                    DragonFlightController.FlightIntent.route(
                            platform, 0.36D, 0.0D, DragonFlightController.FlightEnvelope.ATTACK)).movement());
            assertClearRoot(occupancy, position, "combat landing tick=" + tick);
        }
        assertTrue(position.distanceTo(platform) <= 2.5D,
                "platform landing handoff error=" + position.distanceTo(platform));
        assertTrue(SacredMountainFlightPath.isInsideCombatPlatform(position, REST, 0));
    }

    @Test
    void whitePlatformClearsGroundCombatDeathAndCorpseCollisionParts() {
        AssetOccupancy occupancy = new AssetOccupancy(SacredMountainStructureAsset.loadDefault());
        Vec3 platform = SacredMountainFlightPath.combatPlatformWorldPosition(REST, 0);
        assertClearFrame(occupancy, platform, AncientDragonCollisionRig.COMBAT_IDLE, 0, true,
                "ancient_dragon:combat_idle");
        assertClearFrame(occupancy, platform, AncientDragonCollisionRig.COMBAT_IDLE, 80, true,
                "ancient_dragon:combat_idle");
        for (int tick = 0; tick <= 241; tick += 40) {
            assertClearFrame(occupancy, platform, AncientDragonCollisionRig.DEATH, tick, false,
                    "ancient_dragon:death_landmark");
        }
        assertClearFrame(occupancy, platform, AncientDragonCollisionRig.CORPSE, 0, true,
                "ancient_dragon:corpse_static");
    }

    private static void assertClearFrame(
            AssetOccupancy occupancy,
            Vec3 root,
            String clip,
            int tick,
            boolean loop,
            String animationKey) {
        AncientDragonCollisionRig rig = AncientDragonCollisionRig.instance();
        AncientDragonCollisionRig.SolvedFrame solved = rig.solve(
                rig.sample(clip, tick, loop),
                com.liy.ancientdragon.animation.pose.DragonPoseState.IDENTITY,
                animationKey);
        DragonQuaternion facing = DragonQuaternion.fromMinecraftYaw(270.0F);
        for (AncientDragonPartKind kind : AncientDragonPartKind.values()) {
            Vec3 center = root.add(facing.rotate(solved.position(kind)));
            AncientDragonPartKind.ProjectedDimensions dimensions = kind.project(
                    facing.multiply(solved.rotation(kind)));
            String collision = occupancy.firstSolidInBox(
                    center.x - dimensions.xSize() * 0.5D,
                    center.y - dimensions.ySize() * 0.5D,
                    center.z - dimensions.zSize() * 0.5D,
                    center.x + dimensions.xSize() * 0.5D,
                    center.y + dimensions.ySize() * 0.5D,
                    center.z + dimensions.zSize() * 0.5D);
            assertTrue(collision == null, () -> clip + " tick=" + tick + " part="
                    + kind.serializedName() + " collided at " + collision);
        }
    }

    private static void assertClearRoot(AssetOccupancy occupancy, Vec3 position, String phase) {
        String collision = occupancy.firstSolidInBox(
                position.x - ROOT_WIDTH * 0.5D,
                position.y,
                position.z - ROOT_WIDTH * 0.5D,
                position.x + ROOT_WIDTH * 0.5D,
                position.y + ROOT_HEIGHT,
                position.z + ROOT_WIDTH * 0.5D);
        assertTrue(collision == null, () -> phase + " root=" + format(position) + " collided at " + collision);
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
            if (tile == null || localY < tile.minimumLocalY() || localY >= tile.minimumLocalY() + tile.height()) {
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
