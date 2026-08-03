#!/usr/bin/env python3
"""Crop a transparent generated source into a compact Minecraft item texture."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--size", type=int, default=32)
    parser.add_argument("--padding", type=int, default=2)
    args = parser.parse_args()

    source = Image.open(args.input).convert("RGBA")
    alpha = source.getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError(f"generated texture has no opaque subject: {args.input}")

    subject = source.crop(bounds)
    available = args.size - args.padding * 2
    scale = min(available / subject.width, available / subject.height)
    resized = subject.resize(
        (max(1, round(subject.width * scale)), max(1, round(subject.height * scale))),
        Image.Resampling.LANCZOS,
    )
    pixels = resized.load()
    for y in range(resized.height):
        for x in range(resized.width):
            red, green, blue, opacity = pixels[x, y]
            pixels[x, y] = (red, green, blue, 0 if opacity < 48 else opacity)

    output = Image.new("RGBA", (args.size, args.size), (0, 0, 0, 0))
    output.alpha_composite(
        resized,
        ((args.size - resized.width) // 2, (args.size - resized.height) // 2),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    output.save(args.output, optimize=True)
    print(
        f"ITEM_TEXTURE_READY input={args.input.name} output={args.output} "
        f"subject={resized.width}x{resized.height} canvas={args.size}x{args.size}"
    )


if __name__ == "__main__":
    main()
