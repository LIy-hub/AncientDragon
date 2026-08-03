#!/usr/bin/env python3
"""Remove enchanting-table states and their metadata from a frozen ADSM mountain asset."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import zlib
from pathlib import Path


AIR = "minecraft:air"
MAGIC = b"ADSM"
FORMAT_VERSION = 1


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    for shift in range(0, 35, 7):
        current = data[offset]
        offset += 1
        value |= (current & 0x7F) << shift
        if not current & 0x80:
            return value, offset
    raise ValueError("ADSM varint is too long")


def write_varint(output: bytearray, value: int) -> None:
    while value & ~0x7F:
        output.append((value & 0x7F) | 0x80)
        value >>= 7
    output.append(value)


def read_utf8(data: bytes, offset: int) -> tuple[str, int]:
    size, offset = read_varint(data, offset)
    return data[offset:offset + size].decode("utf-8"), offset + size


def write_utf8(output: bytearray, value: str) -> None:
    encoded = value.encode("utf-8")
    write_varint(output, len(encoded))
    output.extend(encoded)


def decode_tile(raw: bytes) -> tuple[tuple[int, int, int, int], list[str], list[tuple[int, list[tuple[int, int]]]]]:
    if raw[:4] != MAGIC or raw[4] != FORMAT_VERSION:
        raise ValueError("unexpected ADSM header")
    tile_x, tile_z, minimum_y, height = struct.unpack_from(">HHhH", raw, 5)
    offset = 13
    palette_size, offset = read_varint(raw, offset)
    palette = []
    for _ in range(palette_size):
        state, offset = read_utf8(raw, offset)
        palette.append(state)
    if not palette or palette[0] != AIR:
        raise ValueError("ADSM palette must start with air")
    columns = []
    for _ in range(16 * 16):
        span, offset = read_varint(raw, offset)
        run_count, offset = read_varint(raw, offset)
        runs = []
        for _ in range(run_count):
            length, offset = read_varint(raw, offset)
            palette_index, offset = read_varint(raw, offset)
            runs.append((length, palette_index))
        columns.append((span, runs))
    if offset != len(raw):
        raise ValueError("ADSM tile has trailing bytes")
    return (tile_x, tile_z, minimum_y, height), palette, columns


def encode_tile(
        header: tuple[int, int, int, int], palette: list[str], columns: list[tuple[int, list[tuple[int, int]]]]) -> bytes:
    tile_x, tile_z, minimum_y, height = header
    output = bytearray(MAGIC)
    output.append(FORMAT_VERSION)
    output.extend(struct.pack(">HHhH", tile_x, tile_z, minimum_y, height))
    write_varint(output, len(palette))
    for state in palette:
        write_utf8(output, state)
    for span, runs in columns:
        write_varint(output, span)
        write_varint(output, len(runs))
        for length, palette_index in runs:
            write_varint(output, length)
            write_varint(output, palette_index)
    return bytes(output)


def highest_non_air_y(minimum_y: int, columns: list[tuple[int, list[tuple[int, int]]]]) -> int | None:
    highest = None
    for span, runs in columns:
        cursor = 0
        for length, palette_index in runs:
            if palette_index != 0:
                highest = max(highest or minimum_y, minimum_y + cursor + length - 1)
            cursor += length
        if cursor != span:
            raise ValueError("ADSM runs do not fill their span")
    return highest


def remove_state(raw: bytes, state: str) -> tuple[bytes, int, int, int | None]:
    header, palette, columns = decode_tile(raw)
    targets = {index for index, value in enumerate(palette) if value == state}
    if not targets:
        return raw, 0, sum(length for _, runs in columns for length, index in runs if index), highest_non_air_y(header[2], columns)

    rewritten = []
    removed = 0
    used = {0}
    for span, runs in columns:
        compact = []
        for length, palette_index in runs:
            if palette_index in targets:
                palette_index = 0
                removed += length
            used.add(palette_index)
            if compact and compact[-1][1] == palette_index:
                compact[-1] = (compact[-1][0] + length, palette_index)
            else:
                compact.append((length, palette_index))
        rewritten.append((span, compact))

    old_to_new = {0: 0}
    compact_palette = [AIR]
    for old_index, value in enumerate(palette[1:], start=1):
        if old_index in used and old_index not in targets:
            old_to_new[old_index] = len(compact_palette)
            compact_palette.append(value)
    remapped = [
        (span, [(length, old_to_new[index]) for length, index in runs])
        for span, runs in rewritten
    ]
    non_air = sum(length for _, runs in remapped for length, index in runs if index)
    return encode_tile(header, compact_palette, remapped), removed, non_air, highest_non_air_y(header[2], remapped)


def empty_tile(tile_x: int, tile_z: int, minimum_y: int, height: int) -> bytes:
    return encode_tile((tile_x, tile_z, minimum_y, height), [AIR], [(0, []) for _ in range(16 * 16)])


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def atomic_write(path: Path, data: bytes) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(data)
    temporary.replace(path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("asset", type=Path)
    args = parser.parse_args()
    asset = args.asset.resolve()
    manifest_path = asset / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    entries = {(entry["x"], entry["z"]): entry for entry in manifest["tiles"]}
    changed = {}
    removed_total = 0

    for key, entry in entries.items():
        path = asset / entry["path"]
        compressed = path.read_bytes()
        if digest(compressed) != entry["sha256"]:
            raise ValueError(f"compressed hash mismatch for {path}")
        raw = zlib.decompress(compressed)
        if digest(raw) != entry["rawSha256"]:
            raise ValueError(f"raw hash mismatch for {path}")
        rewritten, removed, non_air, highest_y = remove_state(raw, "minecraft:enchanting_table")
        if not removed:
            continue
        compressed_rewritten = zlib.compress(rewritten, level=9)
        entry.update({
            "sha256": digest(compressed_rewritten),
            "rawSha256": digest(rewritten),
            "compressedBytes": len(compressed_rewritten),
            "rawBytes": len(rewritten),
            "paletteSize": len(decode_tile(rewritten)[1]),
            "nonAirBlocks": non_air,
            "highestLocalY": highest_y,
        })
        changed[key] = (asset / entry["path"], compressed_rewritten, rewritten)
        removed_total += removed

    if removed_total != 3:
        raise ValueError(f"expected exactly 3 enchanting tables, removed {removed_total}")
    manifest["blockEntities"] = [
        marker for marker in manifest["blockEntities"] if marker["id"] != "minecraft:enchanting_table"
    ]
    if manifest["blockEntities"]:
        raise ValueError("unexpected non-enchanting block entity markers remain")
    manifest["shapeVersion"] += 1
    manifest["nonAirBlocks"] -= removed_total

    minimum_y = manifest["localBounds"][0][1]
    height = manifest["size"][1]
    structure_hash = hashlib.sha256()
    for tile_z in range(manifest["tileGrid"][1]):
        for tile_x in range(manifest["tileGrid"][0]):
            raw = changed.get((tile_x, tile_z), (None, None, None))[2]
            if raw is None:
                entry = entries.get((tile_x, tile_z))
                raw = (zlib.decompress((asset / entry["path"]).read_bytes()) if entry
                       else empty_tile(tile_x, tile_z, minimum_y, height))
            structure_hash.update(struct.pack(">HHI", tile_x, tile_z, len(raw)))
            structure_hash.update(raw)
    manifest["structureSha256"] = structure_hash.hexdigest()

    for path, compressed, _ in changed.values():
        atomic_write(path, compressed)
    atomic_write(manifest_path, (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    print(f"Removed {removed_total} enchanting tables from {len(changed)} tiles; sha256={manifest['structureSha256']}")


if __name__ == "__main__":
    main()
