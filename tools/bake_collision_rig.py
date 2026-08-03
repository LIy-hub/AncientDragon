#!/usr/bin/env python3
"""Bake server-authoritative multipart collider anchors from the shipped strict GLB."""

from __future__ import annotations

import argparse
import bisect
import json
import math
import struct
from pathlib import Path


PARTS = (
    ("head", "head", (0.0, 0.35, 0.0)),
    ("neck_front", "neck_04", (0.0, 0.08, 0.0)),
    ("neck_base", "neck_02", (0.0, 0.04, 0.0)),
    ("chest", "chest", (0.0, 0.0, 0.0)),
    ("pelvis", "pelvis", (0.0, 0.0, 0.0)),
    ("hind_thigh_l", "thigh.L", (0.0, 0.18, 0.0)),
    ("hind_foot_l", "foot.L", (0.0, 0.16, 0.0)),
    ("hind_thigh_r", "thigh.R", (0.0, 0.18, 0.0)),
    ("hind_foot_r", "foot.R", (0.0, 0.16, 0.0)),
    ("primary_wing_l_inner", "wing_main_02.L", (0.0, 0.25, 0.0)),
    ("primary_wing_l_mid", "wing_main_vein_A_03.L", (0.0, 0.0, 0.0)),
    ("primary_wing_l_tip", "wing_main_vein_C_04.L", (0.0, 0.0, 0.0)),
    ("primary_wing_r_inner", "wing_main_02.R", (0.0, 0.25, 0.0)),
    ("primary_wing_r_mid", "wing_main_vein_A_03.R", (0.0, 0.0, 0.0)),
    ("primary_wing_r_tip", "wing_main_vein_C_04.R", (0.0, 0.0, 0.0)),
    ("tail_1", "tail_02", (0.0, 0.0, 0.0)),
    ("tail_2", "tail_05", (0.0, 0.0, 0.0)),
    ("tail_tip", "tail_08", (0.0, 0.20, 0.0)),
)

CLIPS = (
    "DRG_Dormant_Hold",
    "DRG_Awaken_Intro",
    "DRG_Takeoff",
    "DRG_Fly_Cruise_Loop",
    "DRG_Fly_Descend",
    "DRG_Land",
    "DRG_Combat_Idle",
    "DRG_Combat_Stand",
    "DRG_Ground_Stalk",
    "DRG_Bite_Heavy",
    "DRG_Wing_Slam",
    "DRG_Roar_Storm",
    "DRG_Solar_Breath",
    "DRG_Hit_React",
    "DRG_Dive_Strike",
    "DRG_Aerial_Tail_Sweep",
    "DRG_Aerial_Storm_Burst",
    "DRG_Aerial_Solar_Breath",
    "DRG_Death_Landmark",
    "DRG_Corpse_Static",
)


def matrix_multiply(left, right):
    return [[sum(left[row][index] * right[index][column] for index in range(4))
             for column in range(4)] for row in range(4)]


def normalized_quaternion(value):
    length = math.sqrt(sum(component * component for component in value))
    if not length > 1.0e-12:
        raise ValueError("zero quaternion")
    return tuple(component / length for component in value)


def slerp(left, right, amount):
    left = normalized_quaternion(left)
    right = normalized_quaternion(right)
    cosine = sum(a * b for a, b in zip(left, right))
    if cosine < 0.0:
        right = tuple(-component for component in right)
        cosine = -cosine
    if cosine > 0.9995:
        return normalized_quaternion(tuple(a + amount * (b - a) for a, b in zip(left, right)))
    theta = math.acos(max(-1.0, min(1.0, cosine)))
    sine = math.sin(theta)
    left_weight = math.sin((1.0 - amount) * theta) / sine
    right_weight = math.sin(amount * theta) / sine
    return normalized_quaternion(tuple(
        left_weight * a + right_weight * b for a, b in zip(left, right)))


def local_matrix(translation, rotation, scale):
    x, y, z, w = normalized_quaternion(rotation)
    sx, sy, sz = scale
    tx, ty, tz = translation
    return [
        [(1.0 - 2.0 * (y * y + z * z)) * sx, 2.0 * (x * y - z * w) * sy,
         2.0 * (x * z + y * w) * sz, tx],
        [2.0 * (x * y + z * w) * sx, (1.0 - 2.0 * (x * x + z * z)) * sy,
         2.0 * (y * z - x * w) * sz, ty],
        [2.0 * (x * z - y * w) * sx, 2.0 * (y * z + x * w) * sy,
         (1.0 - 2.0 * (x * x + y * y)) * sz, tz],
        [0.0, 0.0, 0.0, 1.0],
    ]


def transform_point(matrix, point):
    x, y, z = point
    return (
        matrix[0][0] * x + matrix[0][1] * y + matrix[0][2] * z + matrix[0][3],
        matrix[1][0] * x + matrix[1][1] * y + matrix[1][2] * z + matrix[1][3],
        matrix[2][0] * x + matrix[2][1] * y + matrix[2][2] * z + matrix[2][3],
    )


class Glb:
    def __init__(self, path: Path):
        raw = path.read_bytes()
        if raw[:4] != b"glTF" or struct.unpack_from("<I", raw, 4)[0] != 2:
            raise ValueError(f"not GLB 2.0: {path}")
        cursor = 12
        json_document = None
        binary = b""
        while cursor + 8 <= len(raw):
            length, kind = struct.unpack_from("<II", raw, cursor)
            cursor += 8
            chunk = raw[cursor:cursor + length]
            cursor += length
            if kind == 0x4E4F534A:
                json_document = json.loads(chunk.decode("utf-8").rstrip("\x00 \t\r\n"))
            elif kind == 0x004E4942:
                binary = chunk
        if json_document is None:
            raise ValueError("GLB has no JSON chunk")
        self.document = json_document
        self.binary = binary

    def accessor(self, index: int):
        accessor = self.document["accessors"][index]
        if accessor["componentType"] != 5126:
            raise ValueError(f"animation accessor {index} is not FLOAT")
        components = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[accessor["type"]]
        view = self.document["bufferViews"][accessor["bufferView"]]
        stride = view.get("byteStride", components * 4)
        offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        values = []
        for row in range(accessor["count"]):
            values.append(struct.unpack_from("<" + "f" * components, self.binary, offset + row * stride))
        return values


def sampled_value(times, values, interpolation, time_seconds, rotation):
    if len(times) == 1 or time_seconds <= times[0][0]:
        return values[0]
    if time_seconds >= times[-1][0]:
        return values[-1]
    right = bisect.bisect_right([value[0] for value in times], time_seconds)
    left = right - 1
    if interpolation == "STEP":
        return values[left]
    span = times[right][0] - times[left][0]
    amount = 0.0 if span <= 0.0 else (time_seconds - times[left][0]) / span
    if rotation:
        return slerp(values[left], values[right], amount)
    return tuple(a + amount * (b - a) for a, b in zip(values[left], values[right]))


def bake(glb_path: Path):
    glb = Glb(glb_path)
    document = glb.document
    nodes = document["nodes"]
    node_by_name = {node.get("name"): index for index, node in enumerate(nodes)}
    missing = sorted({bone for _, bone, _ in PARTS if bone not in node_by_name})
    if missing:
        raise ValueError(f"collision bones missing from GLB: {missing}")
    parents = {child: parent for parent, node in enumerate(nodes) for child in node.get("children", [])}
    rest = [
        (
            tuple(node.get("translation", (0.0, 0.0, 0.0))),
            tuple(node.get("rotation", (0.0, 0.0, 0.0, 1.0))),
            tuple(node.get("scale", (1.0, 1.0, 1.0))),
        )
        for node in nodes
    ]
    animations = {animation["name"]: animation for animation in document["animations"]}
    required_nodes = set()
    for _, bone_name, _ in PARTS:
        node_index = node_by_name[bone_name]
        while node_index not in required_nodes:
            required_nodes.add(node_index)
            if node_index not in parents:
                break
            node_index = parents[node_index]

    ordered_nodes = []

    def append_with_parents(node_index):
        if node_index in parents:
            append_with_parents(parents[node_index])
        if node_index not in ordered_nodes:
            ordered_nodes.append(node_index)

    for node_index in sorted(required_nodes):
        append_with_parents(node_index)
    local_index = {node_index: index for index, node_index in enumerate(ordered_nodes)}
    part_bones = [local_index[node_by_name[bone_name]] for _, bone_name, _ in PARTS]

    result = {
        "format_version": 2,
        "source_glb": "ancient_dragon:models3d/entity/ancient_dragon.glb",
        "units_to_blocks": 16.0,
        "part_order": [part_id for part_id, _, _ in PARTS],
        "bone_order": [nodes[index].get("name", f"node_{index}") for index in ordered_nodes],
        "bone_parents": [local_index.get(parents.get(index), -1) for index in ordered_nodes],
        "part_bones": part_bones,
        "part_offsets": [[round(component, 6) for component in offset] for _, _, offset in PARTS],
        "clips": {},
    }

    for clip_name in CLIPS:
        animation = animations.get(clip_name)
        if animation is None:
            raise ValueError(f"required collision clip missing: {clip_name}")
        channels = []
        duration = 0.0
        for channel in animation["channels"]:
            sampler = animation["samplers"][channel["sampler"]]
            times = glb.accessor(sampler["input"])
            values = glb.accessor(sampler["output"])
            interpolation = sampler.get("interpolation", "LINEAR")
            if interpolation not in ("LINEAR", "STEP"):
                raise ValueError(f"unsupported collision interpolation {interpolation}")
            path = channel["target"]["path"]
            if path not in ("translation", "rotation", "scale"):
                continue
            channels.append((channel["target"]["node"], path, times, values, interpolation))
            duration = max(duration, times[-1][0])
        duration_ticks = max(1, round(duration * 20.0))
        local_trs_frames = []
        for tick in range(duration_ticks + 1):
            time_seconds = min(duration, tick / 20.0)
            pose = [[list(value) for value in node_rest] for node_rest in rest]
            for node_index, path, times, values, interpolation in channels:
                path_index = {"translation": 0, "rotation": 1, "scale": 2}[path]
                pose[node_index][path_index] = list(sampled_value(
                    times, values, interpolation, time_seconds, path == "rotation"))
            flat_local_trs = []
            for node_index in ordered_nodes:
                translation, rotation, scale = pose[node_index]
                flat_local_trs.extend(round(component, 6) for component in translation)
                flat_local_trs.extend(round(component, 7) for component in normalized_quaternion(rotation))
                flat_local_trs.extend(round(component, 6) for component in scale)
            local_trs_frames.append(flat_local_trs)
        result["clips"][clip_name] = {
            "duration_ticks": duration_ticks,
            "local_trs": local_trs_frames,
        }
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--glb", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    payload = bake(args.glb)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(
        f"ANCIENT_DRAGON_COLLISION_RIG_BAKED parts={len(payload['part_order'])} "
        f"clips={len(payload['clips'])} bytes={args.output.stat().st_size}")


if __name__ == "__main__":
    main()
