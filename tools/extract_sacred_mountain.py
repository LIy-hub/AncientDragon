#!/usr/bin/env python3
"""Extract the authored Sacred Mountain from an Anvil save into tiled ADSM resources.

The source save is read-only. Each 16x16 tile stores vertical block-state runs from the
asset's minimum Y through the highest non-air block in each column. Air above the stored
span is implicit, which keeps the 768x768 authoring volume compact while retaining caves.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import math
import shutil
import struct
import time
import zlib
from collections import Counter, OrderedDict
from dataclasses import dataclass
from pathlib import Path

import nbtlib


AIR = "minecraft:air"
MAGIC = b"ADSM"
FORMAT_VERSION = 1
TILE_SIZE = 16


def stable_read(path: Path, attempts: int = 4) -> bytes:
    """Read a live region file only when size and mtime remain stable across the read."""
    for attempt in range(attempts):
        before = path.stat()
        data = path.read_bytes()
        after = path.stat()
        if before.st_size == after.st_size == len(data) and before.st_mtime_ns == after.st_mtime_ns:
            return data
        if attempt + 1 < attempts:
            time.sleep(0.1)
    raise RuntimeError(f"Region changed repeatedly while being read: {path}")


def decompress_chunk(compression: int, payload: bytes) -> bytes:
    if compression == 1:
        return gzip.decompress(payload)
    if compression == 2:
        return zlib.decompress(payload)
    if compression == 3:
        return payload
    raise ValueError(f"Unsupported Anvil compression type {compression}")


class RegionReader:
    def __init__(self, path: Path):
        self.path = path
        self.data = stable_read(path)
        if len(self.data) < 8192:
            raise ValueError(f"Truncated Anvil region: {path}")

    def chunk(self, chunk_x: int, chunk_z: int):
        local_x = chunk_x & 31
        local_z = chunk_z & 31
        index = local_x + local_z * 32
        location = struct.unpack_from(">I", self.data, index * 4)[0]
        sector = location >> 8
        sector_count = location & 0xFF
        if sector == 0 or sector_count == 0:
            return None
        start = sector * 4096
        if start + 5 > len(self.data):
            raise ValueError(f"Chunk {chunk_x},{chunk_z} points outside {self.path}")
        length = struct.unpack_from(">I", self.data, start)[0]
        if length <= 1 or length > sector_count * 4096 - 4:
            raise ValueError(f"Chunk {chunk_x},{chunk_z} has invalid length {length}")
        compression = self.data[start + 4]
        payload = self.data[start + 5 : start + 4 + length]
        return nbtlib.File.parse(io.BytesIO(decompress_chunk(compression, payload)))


def canonical_state(palette_entry) -> str:
    name = str(palette_entry.get("Name", AIR))
    properties = palette_entry.get("Properties")
    if not properties:
        return name
    encoded = ",".join(f"{key}={properties[key]}" for key in sorted(properties))
    return f"{name}[{encoded}]"


@dataclass(frozen=True)
class Section:
    palette: tuple[str, ...]
    packed: tuple[int, ...]
    bits: int
    values_per_long: int
    mask: int

    @classmethod
    def decode(cls, section_tag):
        block_states = section_tag.get("block_states")
        if block_states is None:
            return None
        palette = tuple(canonical_state(entry) for entry in block_states.get("palette", ()))
        if not palette:
            return None
        bits = max(4, (len(palette) - 1).bit_length())
        values_per_long = 64 // bits
        mask = (1 << bits) - 1
        packed = tuple(int(value) & 0xFFFFFFFFFFFFFFFF for value in block_states.get("data", ()))
        return cls(palette, packed, bits, values_per_long, mask)

    def state(self, local_x: int, local_y: int, local_z: int) -> str:
        if len(self.palette) == 1:
            return self.palette[0]
        index = (local_y << 8) | (local_z << 4) | local_x
        long_index = index // self.values_per_long
        shift = (index % self.values_per_long) * self.bits
        palette_index = (self.packed[long_index] >> shift) & self.mask
        if palette_index >= len(self.palette):
            raise ValueError(f"Palette index {palette_index} exceeds {len(self.palette)} states")
        return self.palette[palette_index]


@dataclass
class Chunk:
    data_version: int
    sections: dict[int, Section]
    block_entities: list

    def state(self, world_x: int, world_y: int, world_z: int) -> str:
        section = self.sections.get(world_y // 16)
        if section is None:
            return AIR
        return section.state(world_x & 15, world_y & 15, world_z & 15)

    def highest_non_air(self, world_x: int, world_z: int, minimum_y: int, maximum_y: int) -> int | None:
        local_x = world_x & 15
        local_z = world_z & 15
        for section_y in range(maximum_y // 16, minimum_y // 16 - 1, -1):
            section = self.sections.get(section_y)
            if section is None or all(state == AIR for state in section.palette):
                continue
            low = max(minimum_y, section_y * 16)
            high = min(maximum_y, section_y * 16 + 15)
            for world_y in range(high, low - 1, -1):
                if section.state(local_x, world_y & 15, local_z) != AIR:
                    return world_y
        return None


class WorldReader:
    def __init__(self, region_directory: Path, cache_size: int = 128):
        self.region_directory = region_directory
        self.regions: dict[tuple[int, int], RegionReader] = {}
        self.chunks: OrderedDict[tuple[int, int], Chunk | None] = OrderedDict()
        self.cache_size = cache_size
        self.data_versions = Counter()

    def _region(self, region_x: int, region_z: int) -> RegionReader | None:
        key = (region_x, region_z)
        if key in self.regions:
            return self.regions[key]
        path = self.region_directory / f"r.{region_x}.{region_z}.mca"
        if not path.is_file():
            return None
        reader = RegionReader(path)
        self.regions[key] = reader
        return reader

    def chunk(self, chunk_x: int, chunk_z: int) -> Chunk | None:
        key = (chunk_x, chunk_z)
        cached = self.chunks.pop(key, None) if key in self.chunks else None
        if cached is not None:
            self.chunks[key] = cached
            return cached
        region = self._region(chunk_x // 32, chunk_z // 32)
        root = None if region is None else region.chunk(chunk_x, chunk_z)
        if root is None:
            chunk = None
        else:
            sections = {}
            for section_tag in root.get("sections", ()):
                decoded = Section.decode(section_tag)
                if decoded is not None:
                    sections[int(section_tag["Y"])] = decoded
            data_version = int(root.get("DataVersion", 0))
            self.data_versions[data_version] += 1
            chunk = Chunk(data_version, sections, list(root.get("block_entities", ())))
        self.chunks[key] = chunk
        while len(self.chunks) > self.cache_size:
            self.chunks.popitem(last=False)
        return chunk


def write_varint(buffer: bytearray, value: int) -> None:
    if value < 0:
        raise ValueError("ADSM varints cannot be negative")
    while value & ~0x7F:
        buffer.append((value & 0x7F) | 0x80)
        value >>= 7
    buffer.append(value)


def write_utf8(buffer: bytearray, value: str) -> None:
    encoded = value.encode("utf-8")
    write_varint(buffer, len(encoded))
    buffer.extend(encoded)


@dataclass
class TileResult:
    raw: bytes
    palette_size: int
    non_air_blocks: int
    highest_local_y: int | None
    block_counts: Counter


def extract_tile(
    world: WorldReader,
    world_min_x: int,
    world_min_z: int,
    tile_x: int,
    tile_z: int,
    minimum_y: int,
    maximum_y: int,
    anchor_y: int,
) -> TileResult:
    palette = [AIR]
    palette_indices = {AIR: 0}
    columns: list[tuple[int, list[tuple[int, int]]]] = []
    non_air_blocks = 0
    highest_local_y = None
    block_counts = Counter()

    for local_z in range(TILE_SIZE):
        world_z = world_min_z + tile_z * TILE_SIZE + local_z
        for local_x in range(TILE_SIZE):
            world_x = world_min_x + tile_x * TILE_SIZE + local_x
            chunk = world.chunk(world_x // 16, world_z // 16)
            top = None if chunk is None else chunk.highest_non_air(world_x, world_z, minimum_y, maximum_y)
            if top is None:
                columns.append((0, []))
                continue
            span = top - minimum_y + 1
            runs: list[tuple[int, int]] = []
            previous_index = None
            run_length = 0
            for world_y in range(minimum_y, top + 1):
                state = chunk.state(world_x, world_y, world_z)
                state_index = palette_indices.get(state)
                if state_index is None:
                    state_index = len(palette)
                    palette_indices[state] = state_index
                    palette.append(state)
                if state != AIR:
                    non_air_blocks += 1
                    block_counts[state] += 1
                if state_index == previous_index:
                    run_length += 1
                else:
                    if previous_index is not None:
                        runs.append((run_length, previous_index))
                    previous_index = state_index
                    run_length = 1
            if previous_index is not None:
                runs.append((run_length, previous_index))
            columns.append((span, runs))
            highest_local_y = max(highest_local_y or (top - anchor_y), top - anchor_y)

    raw = bytearray()
    raw.extend(MAGIC)
    raw.append(FORMAT_VERSION)
    raw.extend(struct.pack(">HHhH", tile_x, tile_z, minimum_y - anchor_y, maximum_y - minimum_y + 1))
    write_varint(raw, len(palette))
    for state in palette:
        write_utf8(raw, state)
    for span, runs in columns:
        write_varint(raw, span)
        write_varint(raw, len(runs))
        for run_length, palette_index in runs:
            write_varint(raw, run_length)
            write_varint(raw, palette_index)
    return TileResult(bytes(raw), len(palette), non_air_blocks, highest_local_y, block_counts)


def prepare_output(output: Path, asset_id: str) -> None:
    if output.exists():
        manifest = output / "manifest.json"
        if not manifest.is_file():
            raise RuntimeError(f"Refusing to replace unrecognized directory: {output}")
        existing = json.loads(manifest.read_text(encoding="utf-8"))
        if existing.get("assetId") != asset_id:
            raise RuntimeError(f"Refusing to replace a different asset: {output}")
        shutil.rmtree(output)
    (output / "chunks").mkdir(parents=True)


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--save", type=Path, required=True)
    parser.add_argument("--dimension", default="dimensions/minecraft/overworld")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--asset-id", default="ancient_dragon:sacred_mountain_live_v1")
    parser.add_argument("--build-id", required=True)
    parser.add_argument("--shape-version", type=int, required=True)
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--anchor", type=int, nargs=3, metavar=("X", "Y", "Z"), required=True)
    parser.add_argument("--dragon-rest", type=int, nargs=3, metavar=("X", "Y", "Z"), required=True)
    parser.add_argument("--dragon-yaw", type=float, required=True)
    parser.add_argument("--bounds", type=int, nargs=6, metavar=("MIN_X", "MIN_Y", "MIN_Z", "MAX_X", "MAX_Y", "MAX_Z"), required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    min_x, min_y, min_z, max_x, max_y, max_z = args.bounds
    anchor_x, anchor_y, anchor_z = args.anchor
    dragon_x, dragon_y, dragon_z = args.dragon_rest
    width = max_x - min_x + 1
    depth = max_z - min_z + 1
    height = max_y - min_y + 1
    if width <= 0 or depth <= 0 or height <= 0 or width % TILE_SIZE or depth % TILE_SIZE:
        raise ValueError("Bounds must be positive and horizontally divisible into 16x16 tiles")
    if (anchor_x + (min_x - anchor_x), anchor_z + (min_z - anchor_z)) != (min_x, min_z):
        raise AssertionError("Invalid anchor arithmetic")

    region_directory = args.save / Path(args.dimension) / "region"
    if not region_directory.is_dir():
        raise FileNotFoundError(f"Missing dimension region directory: {region_directory}")
    prepare_output(args.output, args.asset_id)
    args.report.parent.mkdir(parents=True, exist_ok=True)

    world = WorldReader(region_directory)
    tiles_x = width // TILE_SIZE
    tiles_z = depth // TILE_SIZE
    structure_hash = hashlib.sha256()
    tile_entries = []
    global_counts = Counter()
    total_non_air = 0
    highest_local_y = None

    for tile_z in range(tiles_z):
        for tile_x in range(tiles_x):
            result = extract_tile(world, min_x, min_z, tile_x, tile_z, min_y, max_y, anchor_y)
            structure_hash.update(struct.pack(">HHI", tile_x, tile_z, len(result.raw)))
            structure_hash.update(result.raw)
            global_counts.update(result.block_counts)
            total_non_air += result.non_air_blocks
            if result.highest_local_y is not None:
                highest_local_y = max(highest_local_y or result.highest_local_y, result.highest_local_y)
            if result.non_air_blocks == 0:
                continue
            compressed = zlib.compress(result.raw, level=9)
            relative_path = f"chunks/{tile_x}.{tile_z}.adsm"
            destination = args.output / relative_path
            destination.write_bytes(compressed)
            tile_entries.append({
                "x": tile_x,
                "z": tile_z,
                "path": relative_path,
                "sha256": hashlib.sha256(compressed).hexdigest(),
                "rawSha256": hashlib.sha256(result.raw).hexdigest(),
                "compressedBytes": len(compressed),
                "rawBytes": len(result.raw),
                "paletteSize": result.palette_size,
                "nonAirBlocks": result.non_air_blocks,
                "highestLocalY": result.highest_local_y,
            })
        print(f"EXTRACT_ROW {tile_z + 1}/{tiles_z} non_air={total_non_air} tiles={len(tile_entries)}", flush=True)

    block_entities = []
    minimum_chunk_x, maximum_chunk_x = min_x // 16, max_x // 16
    minimum_chunk_z, maximum_chunk_z = min_z // 16, max_z // 16
    seen_block_entities = set()
    for chunk_z in range(minimum_chunk_z, maximum_chunk_z + 1):
        for chunk_x in range(minimum_chunk_x, maximum_chunk_x + 1):
            chunk = world.chunk(chunk_x, chunk_z)
            if chunk is None:
                continue
            for tag in chunk.block_entities:
                x, y, z = int(tag.get("x", 0)), int(tag.get("y", 0)), int(tag.get("z", 0))
                key = (x, y, z)
                if key in seen_block_entities or not (min_x <= x <= max_x and min_y <= y <= max_y and min_z <= z <= max_z):
                    continue
                seen_block_entities.add(key)
                block_entities.append({
                    "id": str(tag.get("id", "unknown")),
                    "position": [x - anchor_x, y - anchor_y, z - anchor_z],
                    "policy": "state_only",
                })
    block_entities.sort(key=lambda entry: tuple(entry["position"]))

    data_version = world.data_versions.most_common(1)[0][0] if world.data_versions else 0
    manifest = {
        "formatVersion": FORMAT_VERSION,
        "assetId": args.asset_id,
        "shapeVersion": args.shape_version,
        "buildId": args.build_id,
        "seed": args.seed,
        "sourceDataVersion": data_version,
        "sourceDimension": "minecraft:overworld",
        "anchor": [anchor_x, anchor_y, anchor_z],
        "dragonRest": [dragon_x - anchor_x, dragon_y - anchor_y, dragon_z - anchor_z],
        "dragonYaw": args.dragon_yaw,
        "sourceBounds": [[min_x, min_y, min_z], [max_x, max_y, max_z]],
        "localBounds": [[min_x - anchor_x, min_y - anchor_y, min_z - anchor_z], [max_x - anchor_x, max_y - anchor_y, max_z - anchor_z]],
        "size": [width, height, depth],
        "tileSize": TILE_SIZE,
        "tileGrid": [tiles_x, tiles_z],
        "airPolicy": "implicit_and_preserved",
        "entityPolicy": "excluded",
        "blockEntityPolicy": "state_only_indexed",
        "structureSha256": structure_hash.hexdigest(),
        "nonAirBlocks": total_non_air,
        "highestLocalY": highest_local_y,
        "tiles": tile_entries,
        "blockEntities": block_entities,
    }
    (args.output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report = {
        **manifest,
        "sourceSave": str(args.save.resolve()),
        "output": str(args.output.resolve()),
        "regionFilesRead": sorted(str(reader.path.resolve()) for reader in world.regions.values()),
        "dataVersionCounts": dict(sorted(world.data_versions.items())),
        "uniqueBlockStates": len(global_counts),
        "topBlockStates": global_counts.most_common(32),
        "compressedChunkBytes": sum(entry["compressedBytes"] for entry in tile_entries),
        "rawChunkBytes": sum(entry["rawBytes"] for entry in tile_entries),
    }
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        "SACRED_MOUNTAIN_EXTRACTED "
        f"size={width}x{height}x{depth} tiles={len(tile_entries)}/{tiles_x * tiles_z} "
        f"non_air={total_non_air} states={len(global_counts)} sha256={structure_hash.hexdigest()}"
    )


if __name__ == "__main__":
    main()
