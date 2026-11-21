"""Prepare strict BlendLib assets from the checked-in hatchling and egg GLB exports."""
import json
import shutil
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AUTHORING = ROOT / "art/blender/hatchling"
ASSETS = ROOT / "src/main/resources/assets/ancient_dragon"


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
        texture_path = ASSETS / "textures/blendlib" / texture_name
        texture_path.write_bytes(data[start : start + view["byteLength"]])
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
    shutil.copyfile(AUTHORING / "ancient_dragon_egg_item.png",
                    ASSETS / "textures/item/dragon_egg.png")


if __name__ == "__main__":
    main()
