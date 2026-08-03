package com.liy.ancientdragon.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.InflaterInputStream;

/** Lazy reader for the tiled block-state snapshot extracted from the live Sacred Mountain. */
public final class SacredMountainStructureAsset {
    public static final String DEFAULT_ASSET_ID = "ancient_dragon:sacred_mountain_live_v1";
    private static final String DEFAULT_BASE =
            "/data/ancient_dragon/structures/sacred_mountain_live_v1/";
    private static final int MAGIC = 0x4144534D;
    private static final int FORMAT_VERSION = 1;
    private static final int COLUMN_COUNT = 16 * 16;
    private static final String AIR = "minecraft:air";

    private final String resourceBase;
    private final Manifest manifest;
    private final Map<Long, TileEntry> entries;
    private final List<TileCoordinate> tileCoordinates;

    private SacredMountainStructureAsset(
            String resourceBase,
            Manifest manifest,
            Map<Long, TileEntry> entries,
            List<TileCoordinate> tileCoordinates) {
        this.resourceBase = resourceBase;
        this.manifest = manifest;
        this.entries = Map.copyOf(entries);
        this.tileCoordinates = List.copyOf(tileCoordinates);
    }

    public static SacredMountainStructureAsset loadDefault() {
        return load(DEFAULT_BASE);
    }

    static SacredMountainStructureAsset load(String resourceBase) {
        String normalizedBase = resourceBase.endsWith("/") ? resourceBase : resourceBase + "/";
        try (InputStream stream = SacredMountainStructureAsset.class.getResourceAsStream(
                normalizedBase + "manifest.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing Sacred Mountain structure manifest at " + normalizedBase);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Manifest manifest = decodeManifest(root);
            Map<Long, TileEntry> entries = new HashMap<>();
            List<TileCoordinate> tileCoordinates = new ArrayList<>();
            for (var element : root.getAsJsonArray("tiles")) {
                JsonObject object = element.getAsJsonObject();
                TileEntry entry = new TileEntry(
                        object.get("x").getAsInt(),
                        object.get("z").getAsInt(),
                        object.get("path").getAsString(),
                        object.get("sha256").getAsString(),
                        object.get("rawSha256").getAsString(),
                        object.get("nonAirBlocks").getAsLong());
                if (entry.x() < 0 || entry.x() >= manifest.tilesX()
                        || entry.z() < 0 || entry.z() >= manifest.tilesZ()) {
                    throw new IllegalStateException("Sacred Mountain tile lies outside the declared grid: " + entry);
                }
                if (entries.put(tileKey(entry.x(), entry.z()), entry) != null) {
                    throw new IllegalStateException("Duplicate Sacred Mountain tile " + entry.x() + "," + entry.z());
                }
                tileCoordinates.add(new TileCoordinate(entry.x(), entry.z()));
            }
            if (entries.size() != manifest.nonEmptyTiles()) {
                throw new IllegalStateException("Sacred Mountain tile count does not match its manifest");
            }
            return new SacredMountainStructureAsset(normalizedBase, manifest, entries, tileCoordinates);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Sacred Mountain structure manifest", exception);
        }
    }

    public Manifest manifest() {
        return manifest;
    }

    public List<TileCoordinate> tileCoordinates() {
        return tileCoordinates;
    }

    public Optional<Tile> loadTile(int tileX, int tileZ) {
        if (tileX < 0 || tileX >= manifest.tilesX() || tileZ < 0 || tileZ >= manifest.tilesZ()) {
            throw new IndexOutOfBoundsException("Sacred Mountain tile " + tileX + "," + tileZ);
        }
        TileEntry entry = entries.get(tileKey(tileX, tileZ));
        if (entry == null) {
            return Optional.empty();
        }
        String resource = resourceBase + entry.path();
        try (InputStream stream = SacredMountainStructureAsset.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Sacred Mountain tile resource " + resource);
            }
            byte[] compressed = stream.readAllBytes();
            requireHash(compressed, entry.sha256(), "compressed tile " + entry.x() + "," + entry.z());
            byte[] raw;
            try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed));
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                inflater.transferTo(output);
                raw = output.toByteArray();
            }
            requireHash(raw, entry.rawSha256(), "raw tile " + entry.x() + "," + entry.z());
            Tile tile = decodeTile(raw, entry);
            if (tile.nonAirBlocks() != entry.nonAirBlocks()) {
                throw new IllegalStateException("Sacred Mountain tile non-air count does not match its manifest");
            }
            return Optional.of(tile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Sacred Mountain tile " + entry.x() + "," + entry.z(),
                    exception);
        }
    }

    private static Manifest decodeManifest(JsonObject root) {
        int formatVersion = root.get("formatVersion").getAsInt();
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported Sacred Mountain structure format " + formatVersion);
        }
        String assetId = root.get("assetId").getAsString();
        if (!DEFAULT_ASSET_ID.equals(assetId)) {
            throw new IllegalStateException("Unexpected Sacred Mountain asset id " + assetId);
        }
        int[] anchor = intVector(root, "anchor");
        int[] dragonRest = intVector(root, "dragonRest");
        int[][] localBounds = bounds(root, "localBounds");
        int[] size = intVector(root, "size");
        int[] tileGrid = intVector(root, "tileGrid");
        int tileSize = root.get("tileSize").getAsInt();
        if (anchor.length != 3 || dragonRest.length != 3 || size.length != 3 || tileGrid.length != 2 || tileSize != 16
                || localBounds[0].length != 3 || localBounds[1].length != 3
                || size[0] != tileGrid[0] * tileSize || size[2] != tileGrid[1] * tileSize
                || size[1] <= 0) {
            throw new IllegalStateException("Sacred Mountain structure geometry is inconsistent");
        }
        String structureHash = root.get("structureSha256").getAsString();
        if (!structureHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Sacred Mountain structure hash is malformed");
        }
        return new Manifest(
                assetId,
                root.get("shapeVersion").getAsInt(),
                root.get("buildId").getAsString(),
                root.get("seed").getAsLong(),
                root.get("sourceDataVersion").getAsInt(),
                anchor[0],
                anchor[1],
                anchor[2],
                dragonRest[0],
                dragonRest[1],
                dragonRest[2],
                root.get("dragonYaw").getAsFloat(),
                localBounds[0][0],
                localBounds[0][1],
                localBounds[0][2],
                localBounds[1][0],
                localBounds[1][1],
                localBounds[1][2],
                size[0],
                size[1],
                size[2],
                tileSize,
                tileGrid[0],
                tileGrid[1],
                root.getAsJsonArray("tiles").size(),
                root.get("nonAirBlocks").getAsLong(),
                root.get("highestLocalY").getAsInt(),
                structureHash,
                decodeBlockEntities(root.getAsJsonArray("blockEntities")));
    }

    private static List<BlockEntityMarker> decodeBlockEntities(JsonArray array) {
        List<BlockEntityMarker> blockEntities = new ArrayList<>(array.size());
        for (var element : array) {
            JsonObject object = element.getAsJsonObject();
            int[] position = intVector(object, "position");
            if (position.length != 3) {
                throw new IllegalStateException("Sacred Mountain block-entity position is incomplete");
            }
            blockEntities.add(new BlockEntityMarker(
                    object.get("id").getAsString(), position[0], position[1], position[2]));
        }
        return List.copyOf(blockEntities);
    }

    private static int[] intVector(JsonObject root, String member) {
        return intVector(root.getAsJsonArray(member));
    }

    private static int[] intVector(JsonArray array) {
        int[] values = new int[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).getAsInt();
        }
        return values;
    }

    private static int[][] bounds(JsonObject root, String member) {
        JsonArray array = root.getAsJsonArray(member);
        if (array.size() != 2) {
            throw new IllegalStateException("Sacred Mountain bounds require two corners");
        }
        return new int[][] {
                intVector(array.get(0).getAsJsonArray()),
                intVector(array.get(1).getAsJsonArray())
        };
    }

    private static Tile decodeTile(byte[] raw, TileEntry entry) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != FORMAT_VERSION) {
                throw new IllegalStateException("Sacred Mountain tile has an invalid header");
            }
            int tileX = input.readUnsignedShort();
            int tileZ = input.readUnsignedShort();
            int minimumLocalY = input.readShort();
            int height = input.readUnsignedShort();
            if (tileX != entry.x() || tileZ != entry.z() || height <= 0) {
                throw new IllegalStateException("Sacred Mountain tile metadata does not match its manifest");
            }
            int paletteSize = readVarInt(input);
            if (paletteSize <= 0 || paletteSize > 65536) {
                throw new IllegalStateException("Sacred Mountain tile palette is invalid");
            }
            List<String> palette = new ArrayList<>(paletteSize);
            for (int index = 0; index < paletteSize; index++) {
                palette.add(readUtf8(input));
            }
            if (!AIR.equals(palette.getFirst())) {
                throw new IllegalStateException("Sacred Mountain tile palette must begin with air");
            }
            List<Column> columns = new ArrayList<>(COLUMN_COUNT);
            long nonAirBlocks = 0L;
            for (int columnIndex = 0; columnIndex < COLUMN_COUNT; columnIndex++) {
                int span = readVarInt(input);
                int runCount = readVarInt(input);
                if (span < 0 || span > height || runCount < 0 || runCount > span) {
                    throw new IllegalStateException("Sacred Mountain tile column metadata is invalid");
                }
                List<Run> runs = new ArrayList<>(runCount);
                int consumed = 0;
                for (int runIndex = 0; runIndex < runCount; runIndex++) {
                    int length = readVarInt(input);
                    int paletteIndex = readVarInt(input);
                    if (length <= 0 || paletteIndex < 0 || paletteIndex >= palette.size()) {
                        throw new IllegalStateException("Sacred Mountain tile run is invalid");
                    }
                    runs.add(new Run(length, paletteIndex));
                    consumed += length;
                    if (paletteIndex != 0) {
                        nonAirBlocks += length;
                    }
                }
                if (consumed != span) {
                    throw new IllegalStateException("Sacred Mountain tile runs do not fill their column");
                }
                columns.add(new Column(span, List.copyOf(runs)));
            }
            if (input.read() != -1) {
                throw new IllegalStateException("Sacred Mountain tile contains trailing data");
            }
            return new Tile(tileX, tileZ, minimumLocalY, height, List.copyOf(palette), List.copyOf(columns),
                    nonAirBlocks);
        }
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int current = input.readUnsignedByte();
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
        }
        throw new IllegalStateException("Sacred Mountain varint is too long");
    }

    private static String readUtf8(DataInputStream input) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > 32768) {
            throw new IllegalStateException("Sacred Mountain string length is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IllegalStateException("Sacred Mountain string is truncated");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireHash(byte[] bytes, String expected, String label) {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected)) {
                throw new IllegalStateException("Sacred Mountain " + label + " failed SHA-256 validation");
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static long tileKey(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
    }

    public record Manifest(
            String assetId,
            int shapeVersion,
            String buildId,
            long seed,
            int sourceDataVersion,
            int sourceAnchorX,
            int sourceAnchorY,
            int sourceAnchorZ,
            int dragonRestX,
            int dragonRestY,
            int dragonRestZ,
            float dragonYaw,
            int minimumX,
            int minimumY,
            int minimumZ,
            int maximumX,
            int maximumY,
            int maximumZ,
            int width,
            int height,
            int depth,
            int tileSize,
            int tilesX,
            int tilesZ,
            int nonEmptyTiles,
            long nonAirBlocks,
            int highestLocalY,
            String structureSha256,
            List<BlockEntityMarker> blockEntities) {
    }

    public record BlockEntityMarker(String id, int x, int y, int z) {
    }

    public record TileCoordinate(int x, int z) {
    }

    public record Tile(
            int x,
            int z,
            int minimumLocalY,
            int height,
            List<String> palette,
            List<Column> columns,
            long nonAirBlocks) {
        public String stateAt(int localX, int localY, int localZ) {
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                throw new IndexOutOfBoundsException("Tile coordinate " + localX + "," + localZ);
            }
            int yIndex = localY - minimumLocalY;
            if (yIndex < 0 || yIndex >= height) {
                throw new IndexOutOfBoundsException("Tile Y " + localY);
            }
            Column column = columns.get(localZ * 16 + localX);
            if (yIndex >= column.span()) {
                return AIR;
            }
            int cursor = 0;
            for (Run run : column.runs()) {
                cursor += run.length();
                if (yIndex < cursor) {
                    return palette.get(run.paletteIndex());
                }
            }
            throw new IllegalStateException("Sacred Mountain column ended before its declared span");
        }
    }

    public record Column(int span, List<Run> runs) {
    }

    public record Run(int length, int paletteIndex) {
    }

    private record TileEntry(
            int x,
            int z,
            String path,
            String sha256,
            String rawSha256,
            long nonAirBlocks) {
    }
}
