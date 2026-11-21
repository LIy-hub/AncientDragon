"""Prepare strict BlendLib assets from the checked-in hatchling and egg GLB exports.

Diet textures use NumPy and Pillow at authoring time.
"""
import json
import shutil
import struct
from copy import deepcopy
from pathlib import Path

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
AUTHORING = ROOT / "art/blender/hatchling"
ASSETS = ROOT / "src/main/resources/assets/ancient_dragon"
TEXTURES = ASSETS / "textures/blendlib"

# Scale and wing colors in sRGB. The original atlas supplies the tonal detail and the pale belly.
DIET_PALETTES = {
    "scarlet": ((199, 75, 86), (254, 158, 117)),
    "verdant": ((69, 135, 98), (150, 211, 112)),
    "violet": ((114, 98, 171), (178, 156, 238)),
    "amber": ((182, 119, 55), (243, 199, 107)),
}
PRISMATIC_PALETTE = (
    (229, 105, 135), (234, 137, 88), (242, 193, 95), (79, 189, 144),
    (83, 171, 224), (123, 113, 211), (204, 116, 202),
)


def read_glb(path):
    stream = path.read_bytes()
    if struct.unpack_from("<4sII", stream) != (b"glTF", 2, len(stream)):
        raise ValueError(f"Invalid GLB header in {path}")
    json_length, kind = struct.unpack_from("<I4s", stream, 12)
    bin_offset = 20 + json_length
    bin_length, bin_kind = struct.unpack_from("<I4s", stream, bin_offset)
    if (kind, bin_kind) != (b"JSON", b"BIN\0") or bin_offset + 8 + bin_length != len(stream):
        raise ValueError(f"Expected an embedded GLB in {path}")
    return json.loads(stream[20:bin_offset]), memoryview(stream)[bin_offset + 8:]


def write_glb(path, model, data):
    header_json = json.dumps(model, separators=(",", ":")).encode()
    header_json += b" " * (-len(header_json) % 4)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"".join((
        struct.pack("<4sIII4s", b"glTF", 2, 28 + len(header_json) + len(data), len(header_json), b"JSON"),
        header_json, struct.pack("<I4s", len(data), b"BIN\0"), data,
    )))


def prepare(source, target, profile, states=None):
    model, data = read_glb(AUTHORING / f"{source}.glb")
    views = model["bufferViews"]
    images = model.pop("images")
    image_views = {image["bufferView"] for image in images}
    textures = model.pop("textures")
    model.pop("samplers", None)
    model.pop("extensionsUsed", None)
    model.pop("extensionsRequired", None)
    model.pop("extensions", None)

    materials = {}
    for material in model["materials"]:
        name = material["name"]
        texture_index = material["pbrMetallicRoughness"]["baseColorTexture"]["index"]
        image = images[textures[texture_index]["source"]]
        if image["mimeType"] != "image/png":
            raise ValueError(f"Expected embedded PNG for {source}/{name}")
        view = views[image["bufferView"]]
        start = view.get("byteOffset", 0)
        texture_name = f"{target.replace('/', '_')}__{textures[texture_index]['source']}.png"
        (TEXTURES / texture_name).write_bytes(data[start : start + view["byteLength"]])
        cutout = material.get("alphaMode") == "MASK"
        materials[name] = {
            "base_color": f"ancient_dragon:textures/blendlib/{texture_name}",
            "mode": "cutout" if cutout else "opaque",
            "emissive": False,
            "double_sided": material.get("doubleSided", False),
        }
        if cutout:
            materials[name]["cutout_threshold"] = round(material.get("alphaCutoff", 0.5), 5)
        material.clear()
        material["name"] = name

    # Copy the GLB's geometry and animation views into a compact BIN chunk. Textures are external.
    binary = bytearray()
    remapped = {}
    new_views = []
    for index, view in enumerate(views):
        if index in image_views:
            continue
        binary.extend(b"\0" * (-len(binary) % 4))
        start = view.get("byteOffset", 0)
        remapped[index] = len(new_views)
        new_views.append({**view, "byteOffset": len(binary)})
        binary.extend(data[start : start + view["byteLength"]])
    for accessor in model["accessors"]:
        accessor["bufferView"] = remapped[accessor["bufferView"]]
    model["bufferViews"] = new_views
    binary.extend(b"\0" * (-len(binary) % 4))
    model["buffers"][0]["byteLength"] = len(binary)

    if profile == "rigid_v1":
        # A block entity's origin is the lower corner of its block, while these GLBs are centered.
        for index in model["scenes"][model["scene"]]["nodes"]:
            node = model["nodes"][index]
            x, y, z = node.get("translation", [0, 0, 0])
            node["translation"] = [x + 0.5, y, z + 0.5]
    write_glb(ASSETS / f"models3d/{target}.glb", model, binary)
    descriptor = {
        "format_version": 1,
        "profile": f"blendlib:{profile}",
        "mesh": f"ancient_dragon:models3d/{target}.glb",
        "units_per_block": 1.0,
        "materials": materials,
    }
    if states:
        descriptor["animation"] = {"initial_state": "ancient_dragon:hatchling_perch", "states": states}
    path = ASSETS / f"blend_models/{target}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(descriptor, indent=2) + "\n", encoding="utf-8")



def accessor_array(model, binary, index, dtype, dimensions):
    accessor = model["accessors"][index]
    view = model["bufferViews"][accessor["bufferView"]]
    item_size = np.dtype(dtype).itemsize
    return np.ndarray(
        shape=(accessor["count"], dimensions), dtype=dtype, buffer=binary,
        offset=view.get("byteOffset", 0) + accessor.get("byteOffset", 0),
        strides=(view.get("byteStride", item_size * dimensions), item_size),
    )


def hatchling_surface(model, binary, width, height):
    """Bake rest-pose wing span and nose-to-tail coordinates into the existing UV atlas."""
    surface = np.full((height, width, 2), np.nan, dtype=np.float32)
    for mesh in model["meshes"]:
        for primitive in mesh["primitives"]:
            if model["materials"][primitive["material"]]["name"] != "body":
                continue
            attributes = primitive["attributes"]
            uv = accessor_array(model, binary, attributes["TEXCOORD_0"], "<f4", 2) * (width, height) - 0.5
            xyz = accessor_array(model, binary, attributes["POSITION"], "<f4", 3)
            index = primitive["indices"]
            index_type = {5121: "u1", 5123: "<u2", 5125: "<u4"}[model["accessors"][index]["componentType"]]
            indices = accessor_array(model, binary, index, index_type, 1).reshape(-1, 3)
            for triangle in indices:
                a, b, c = uv[triangle]
                lo = np.maximum(np.ceil(np.minimum(np.minimum(a, b), c)).astype(int), (0, 0))
                hi = np.minimum(np.floor(np.maximum(np.maximum(a, b), c)).astype(int), (width - 1, height - 1))
                if np.any(lo > hi):
                    continue
                ab = b - a
                ac = c - a
                area = ab[0] * ac[1] - ab[1] * ac[0]
                if abs(area) < 1e-8:
                    continue
                x = np.arange(lo[0], hi[0] + 1)[None, :] - a[0]
                y = np.arange(lo[1], hi[1] + 1)[:, None] - a[1]
                u = (x * ac[1] - y * ac[0]) / area
                v = (ab[0] * y - ab[1] * x) / area
                inside = (u >= 0) & (v >= 0) & (u + v <= 1)
                # Both sides of the mesh can share UVs, so the palette is symmetric across the spine.
                points = xyz[triangle][:, (0, 2)].copy()
                points[:, 0] = np.abs(points[:, 0])
                coords = points[0] + u[..., None] * (points[1] - points[0]) + v[..., None] * (points[2] - points[0])
                surface[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1][inside] = coords[inside]

    # Fill edge padding so sampling around an island does not create fringe colors.
    adjacent = (
        (surface[1:], surface[:-1]), (surface[:-1], surface[1:]),
        (surface[:, 1:], surface[:, :-1]), (surface[:, :-1], surface[:, 1:]),
    )
    for _ in range(8):
        filled = np.isfinite(surface[..., 0])
        masks = ((filled[1:], filled[:-1]), (filled[:-1], filled[1:]),
                 (filled[:, 1:], filled[:, :-1]), (filled[:, :-1], filled[:, 1:]))
        for (destination, source), (target_has_data, source_has_data) in zip(adjacent, masks):
            fill = ~target_has_data & source_has_data
            destination[fill] = source[fill]
    surface[~np.isfinite(surface[..., 0])] = (0.0, 0.78)
    return surface


def colorize(source, pigment):
    rgb = source[..., :3].astype(np.float32) / 255.0
    light = rgb @ np.array([0.24, 0.58, 0.18], dtype=np.float32)
    colored = np.clip(pigment + (light[..., None] - 0.53) * 0.94, 0, 1)
    # Cool pixels are scales or membrane. The warm belly and edge markings keep their original colors.
    strength = np.clip((rgb[..., 2] - rgb[..., 0] - 0.025) / 0.15, 0, 1)[..., None]
    result = source.copy()
    result[..., :3] = np.round((rgb + (colored - rgb) * strength) * 255.0).astype(np.uint8)
    return result


def prismatic_pigment(phase):
    channels = np.asarray(PRISMATIC_PALETTE, dtype=np.float32).T / 255.0
    stops = np.linspace(0.0, 1.0, channels.shape[1])
    return np.stack([np.interp(phase, stops, channel) for channel in channels], axis=-1)


def prepare_hatchling_variants():
    model, binary = read_glb(ASSETS / "models3d/entity/hatchling.glb")
    descriptor = ASSETS / "blend_models/entity/hatchling.json"
    base = json.loads(descriptor.read_text(encoding="utf-8"))
    with Image.open(TEXTURES / "entity_hatchling__0.png") as image:
        body = np.asarray(image.convert("RGBA"))
    with Image.open(TEXTURES / "entity_hatchling__1.png") as image:
        eyelids = np.asarray(image.convert("RGB"))

    surface = hatchling_surface(model, binary, body.shape[1], body.shape[0])
    span, length = surface[..., 0], surface[..., 1]
    wing = np.clip((span - 0.23) / 0.72, 0, 1)
    wing = wing * wing * (3.0 - 2.0 * wing)
    for name, (scales, membranes) in DIET_PALETTES.items():
        scales, membranes = np.asarray((scales, membranes), dtype=np.float32) / 255.0
        pigment = scales + (membranes - scales) * wing[..., None]
        write_variant(descriptor, base, name, colorize(body, pigment), colorize(eyelids, scales))

    # The spectrum runs down the spine and out along both wings.
    spine_phase = np.clip(0.045 + (0.78 - length) * 0.39, 0.0, 1.0)
    wing_phase = np.clip(0.20 + (span - 0.24) * 0.64, 0.0, 1.0)
    pigment = prismatic_pigment(spine_phase + (wing_phase - spine_phase) * wing)
    write_variant(descriptor, base, "prismatic", colorize(body, pigment), colorize(eyelids, prismatic_pigment(0.05)))


def write_variant(base_path, base, name, body, eyelid):
    descriptor = deepcopy(base)
    for material, pixels in (("body", body), ("eyelid", eyelid)):
        texture = f"hatchling_{name}_{material}.png"
        Image.fromarray(pixels).save(TEXTURES / texture, optimize=True)
        descriptor["materials"][material]["base_color"] = f"ancient_dragon:textures/blendlib/{texture}"
    path = base_path.with_suffix("") / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(descriptor, indent=2) + "\n", encoding="utf-8")


def main():
    prepare("ancient_dragon_egg", "block/dragon_egg", "rigid_v1")
    prepare("ancient_dragon_egg_cracked", "block/dragon_egg_cracked", "rigid_v1")
    prepare(
        "ancient_dragon_hatchling", "entity/hatchling", "skinned_v1",
        {
            "ancient_dragon:hatchling_perch": {
                "clip": "Hatchling_Perch_Idle", "loop": True, "speed": 1.0, "blend_seconds": 0.15,
            },
            "ancient_dragon:hatchling_flight": {
                "clip": "Hatchling_Fly_Loop", "loop": True, "speed": 1.0, "blend_seconds": 0.15,
            },
            "ancient_dragon:hatchling_glide": {
                "clip": "Hatchling_Glide", "loop": True, "speed": 1.0, "blend_seconds": 0.28,
            },
            "ancient_dragon:hatchling_greet": {
                "clip": "Hatchling_Greet", "loop": False, "speed": 1.0, "blend_seconds": 0.15,
                "next": "ancient_dragon:hatchling_perch",
            },
        },
    )
    prepare_hatchling_variants()
    shutil.copyfile(AUTHORING / "ancient_dragon_egg_item.png",
                    ASSETS / "textures/item/dragon_egg.png")


if __name__ == "__main__":
    main()
