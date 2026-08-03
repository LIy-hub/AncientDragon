package com.liy.ancientdragon.worldgen;

import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Column;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Manifest;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Run;
import com.liy.ancientdragon.worldgen.SacredMountainStructureAsset.Tile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A single persisted piece whose large bounding box is streamed one destination chunk at a time.
 * Source columns are inverse-rotated back into the frozen 16x16 ADSM tiles.
 */
public final class SacredMountainStructurePiece extends StructurePiece {
    private static final String AIR = "minecraft:air";
    static final int MOUNTAIN_ENTRANCE_X = -145;
    static final int MOUNTAIN_ENTRANCE_FEET_Y = 138;
    static final int MOUNTAIN_ENTRANCE_Z = 58;
    static final float MOUNTAIN_ENTRANCE_YAW = (float) Math.toDegrees(Math.atan2(-24.0D, -13.0D));
    private static final int BLOCK_UPDATE_FLAGS = 2;
    private static final Map<StateKey, BlockState> STATE_CACHE = new ConcurrentHashMap<>();
    private static volatile SacredMountainStructureAsset cachedAsset;

    private final BlockPos anchor;
    private final int quarterTurns;

    SacredMountainStructurePiece(BlockPos anchor, int quarterTurns, BoundingBox sectorBounds) {
        super(AncientDragonWorldgen.SACRED_MOUNTAIN_PIECE_TYPE, 0,
                sectorBounds);
        this.anchor = anchor.immutable();
        this.quarterTurns = Math.floorMod(quarterTurns, 4);
    }

    SacredMountainStructurePiece(CompoundTag tag) {
        super(AncientDragonWorldgen.SACRED_MOUNTAIN_PIECE_TYPE, tag);
        this.anchor = new BlockPos(
                tag.getIntOr("AnchorX", 0),
                tag.getIntOr("AnchorY", 0),
                tag.getIntOr("AnchorZ", 0));
        this.quarterTurns = Math.floorMod(tag.getIntOr("QuarterTurns", 0), 4);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("AnchorX", anchor.getX());
        tag.putInt("AnchorY", anchor.getY());
        tag.putInt("AnchorZ", anchor.getZ());
        tag.putInt("QuarterTurns", quarterTurns);
        tag.putString("AssetSha256", asset().manifest().structureSha256());
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox generationBounds,
            ChunkPos chunkPos,
            BlockPos pivot) {
        SacredMountainStructureAsset structureAsset = asset();
        Manifest manifest = structureAsset.manifest();
        int minimumX = Math.max(generationBounds.minX(), boundingBox.minX());
        int maximumX = Math.min(generationBounds.maxX(), boundingBox.maxX());
        int minimumZ = Math.max(generationBounds.minZ(), boundingBox.minZ());
        int maximumZ = Math.min(generationBounds.maxZ(), boundingBox.maxZ());
        if (minimumX > maximumX || minimumZ > maximumZ) {
            return;
        }

        Map<Long, Optional<Tile>> tiles = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int worldZ = minimumZ; worldZ <= maximumZ; worldZ++) {
            for (int worldX = minimumX; worldX <= maximumX; worldX++) {
                SourceColumn source = inverseHorizontal(anchor, worldX, worldZ, quarterTurns);
                if (source.x() < manifest.minimumX() || source.x() > manifest.maximumX()
                        || source.z() < manifest.minimumZ() || source.z() > manifest.maximumZ()) {
                    continue;
                }
                int offsetX = source.x() - manifest.minimumX();
                int offsetZ = source.z() - manifest.minimumZ();
                int tileX = Math.floorDiv(offsetX, manifest.tileSize());
                int tileZ = Math.floorDiv(offsetZ, manifest.tileSize());
                long tileKey = ((long) tileX << 32) ^ Integer.toUnsignedLong(tileZ);
                Optional<Tile> optionalTile = tiles.computeIfAbsent(
                        tileKey, ignored -> structureAsset.loadTile(tileX, tileZ));
                if (optionalTile.isEmpty()) {
                    continue; // Empty source tiles deliberately preserve the surrounding ocean floor.
                }
                Tile tile = optionalTile.orElseThrow();
                int withinTileX = Math.floorMod(offsetX, manifest.tileSize());
                int withinTileZ = Math.floorMod(offsetZ, manifest.tileSize());
                Column column = tile.columns().get(withinTileZ * manifest.tileSize() + withinTileX);
                placeColumn(
                        level,
                        generationBounds,
                        cursor,
                        tile,
                        column,
                        worldX,
                        worldZ,
                        source.x(),
                        source.z(),
                        chunkGenerator.getSeaLevel(),
                        manifest.seed());
            }
        }
    }

    private void placeColumn(
            WorldGenLevel level,
            BoundingBox generationBounds,
            BlockPos.MutableBlockPos cursor,
            Tile tile,
            Column column,
            int worldX,
            int worldZ,
            int localX,
            int localZ,
            int seaLevel,
            long seed) {
        int localY = tile.minimumLocalY();
        for (Run run : column.runs()) {
            String serialized = tile.palette().get(run.paletteIndex());
            BlockState desired = parseState(level, serialized, quarterTurns);
            for (int offset = 0; offset < run.length(); offset++) {
                int worldY = anchor.getY() + localY + offset;
                cursor.set(worldX, worldY, worldZ);
                if (generationBounds.isInside(cursor) && !level.getBlockState(cursor).equals(desired)) {
                    // Explicit air runs reproduce authored caves and clear vanilla terrain. Space above a
                    // column's encoded span remains untouched, giving the sparse outer tiles a natural merge.
                    level.setBlock(cursor, desired, BLOCK_UPDATE_FLAGS);
                }
            }
            localY += run.length();
        }

        SacredMountainNaturalTerrain.topSurface(tile, column).ifPresent(surface -> {
            int targetSurfaceY = SacredMountainNaturalTerrain.targetSurfaceY(
                    seaLevel, localX, localZ, seed);
            int authoredSurfaceY = anchor.getY() + surface.localY();
            if (targetSurfaceY <= authoredSurfaceY) {
                return;
            }
            BlockState cap = SacredMountainNaturalTerrain.capState(
                    parseState(level, surface.serializedState(), quarterTurns));
            BlockState fill = SacredMountainNaturalTerrain.fillState();
            for (int worldY = authoredSurfaceY + 1; worldY <= targetSurfaceY; worldY++) {
                cursor.set(worldX, worldY, worldZ);
                if (!generationBounds.isInside(cursor)) {
                    continue;
                }
                BlockState desired = worldY == targetSurfaceY ? cap : fill;
                if (!level.getBlockState(cursor).equals(desired)) {
                    level.setBlock(cursor, desired, BLOCK_UPDATE_FLAGS);
                }
            }
        });
    }

    private static BlockState parseState(WorldGenLevel level, String serialized, int quarterTurns) {
        if (AIR.equals(serialized)) {
            return Blocks.AIR.defaultBlockState();
        }
        StateKey key = new StateKey(serialized, Math.floorMod(quarterTurns, 4));
        return STATE_CACHE.computeIfAbsent(key, ignored -> {
            try {
                BlockState parsed = BlockStateParser.parseForBlock(
                        level.registryAccess().lookupOrThrow(Registries.BLOCK), serialized, false).blockState();
                return parsed.rotate(rotation(key.quarterTurns()));
            } catch (CommandSyntaxException exception) {
                throw new IllegalStateException("Invalid Sacred Mountain block state " + serialized, exception);
            }
        });
    }

    private static Rotation rotation(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> Rotation.NONE;
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new AssertionError();
        };
    }

    static BoundingBox boundsFor(BlockPos anchor, Manifest manifest, int quarterTurns) {
        var bounds = SacredMountainPlacementService.transformedBounds(anchor, manifest, quarterTurns);
        return BoundingBox.fromCorners(bounds.minimum(), bounds.maximum());
    }

    static SourceColumn inverseHorizontal(BlockPos anchor, int worldX, int worldZ, int quarterTurns) {
        int transformedX = worldX - anchor.getX();
        int transformedZ = worldZ - anchor.getZ();
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new SourceColumn(transformedX, transformedZ);
            case 1 -> new SourceColumn(transformedZ, -transformedX - 1);
            case 2 -> new SourceColumn(-transformedX - 1, -transformedZ - 1);
            case 3 -> new SourceColumn(-transformedZ - 1, transformedX);
            default -> throw new AssertionError();
        };
    }

    BlockPos dragonRest() {
        Manifest manifest = asset().manifest();
        return SacredMountainPlacementService.transform(
                anchor,
                manifest.dragonRestX(),
                manifest.dragonRestY(),
                manifest.dragonRestZ(),
                quarterTurns);
    }

    BlockPos mountainEntrance() {
        return mountainEntrance(anchor, quarterTurns);
    }

    float mountainEntranceYaw() {
        return normalizeYaw(MOUNTAIN_ENTRANCE_YAW + quarterTurns * 90.0F);
    }

    static BlockPos mountainEntrance(BlockPos anchor, int quarterTurns) {
        return SacredMountainPlacementService.transform(
                anchor,
                MOUNTAIN_ENTRANCE_X,
                MOUNTAIN_ENTRANCE_FEET_Y,
                MOUNTAIN_ENTRANCE_Z,
                quarterTurns);
    }

    static float mountainEntranceYaw(int quarterTurns) {
        return normalizeYaw(MOUNTAIN_ENTRANCE_YAW + Math.floorMod(quarterTurns, 4) * 90.0F);
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    float dragonYaw() {
        float yaw = asset().manifest().dragonYaw() + quarterTurns * 90.0F;
        yaw %= 360.0F;
        return yaw < 0.0F ? yaw + 360.0F : yaw;
    }

    String naturalPlacementId() {
        String hash = asset().manifest().structureSha256();
        ChunkPos canonicalStart = ChunkPos.containing(anchor);
        return "natural/" + canonicalStart.x() + "/" + canonicalStart.z() + "/" + hash.substring(0, 16);
    }

    boolean isLegacyMonolithic() {
        return boundingBox.getXSpan() > 16 * 16 || boundingBox.getZSpan() > 16 * 16;
    }

    /**
     * Returns the canonical full-mountain bounds even when natural generation stores this instance
     * as one of the nine streamed sectors. Runtime atmosphere and encounter checks must not reset
     * their local coordinate system at a sector seam.
     */
    BoundingBox fullMountainBounds() {
        return isLegacyMonolithic()
                ? boundingBox
                : boundsFor(anchor, asset().manifest(), quarterTurns);
    }

    BlockPos canonicalAnchor() {
        return anchor;
    }

    int quarterTurns() {
        return quarterTurns;
    }

    private static SacredMountainStructureAsset asset() {
        SacredMountainStructureAsset result = cachedAsset;
        if (result == null) {
            synchronized (SacredMountainStructurePiece.class) {
                result = cachedAsset;
                if (result == null) {
                    result = SacredMountainStructureAsset.loadDefault();
                    cachedAsset = result;
                }
            }
        }
        return result;
    }

    static record SourceColumn(int x, int z) {
    }

    private record StateKey(String serialized, int quarterTurns) {
    }
}
