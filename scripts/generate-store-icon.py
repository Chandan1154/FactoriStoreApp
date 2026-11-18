#!/usr/bin/env python3
import argparse
from pathlib import Path

from PIL import Image, ImageOps


LEGACY_DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
ADAPTIVE_DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def render_icon(source: Image.Image, side: int, safe_fraction: float) -> Image.Image:
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    icon_side = round(side * safe_fraction)
    icon = ImageOps.contain(source, (icon_side, icon_side), Image.Resampling.LANCZOS)
    canvas.alpha_composite(icon, ((side - icon.width) // 2, (side - icon.height) // 2))
    return canvas


def generate(icon_path: Path, output_dir: Path) -> None:
    if not icon_path.is_file():
        raise ValueError(f"Store icon does not exist: {icon_path}")
    if icon_path.stat().st_size > 10 * 1024 * 1024:
        raise ValueError("Store icon must be 10 MiB or smaller.")

    Image.MAX_IMAGE_PIXELS = 20_000_000
    with Image.open(icon_path) as opened:
        if opened.format not in {"PNG", "JPEG", "WEBP"}:
            raise ValueError("Store icon must be PNG, JPEG, or WebP.")
        if opened.width * opened.height > 20_000_000:
            raise ValueError("Store icon dimensions must not exceed 20 megapixels.")
        source = opened.convert("RGBA")

    if source.getbbox() is None:
        raise ValueError("Store icon is empty or fully transparent.")

    for density, side in LEGACY_DENSITIES.items():
        destination = output_dir / density / "store_icon.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        render_icon(source, side, 0.82).save(destination, format="PNG", optimize=True)

    for density, side in ADAPTIVE_DENSITIES.items():
        destination = output_dir / density / "store_icon_foreground.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        render_icon(source, side, 0.58).save(destination, format="PNG", optimize=True)

    adaptive_dir = output_dir / "mipmap-anydpi-v26"
    adaptive_dir.mkdir(parents=True, exist_ok=True)
    (adaptive_dir / "store_icon.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/store_icon_background" />\n'
        '    <foreground android:drawable="@mipmap/store_icon_foreground" />\n'
        '</adaptive-icon>\n',
        encoding="utf-8",
    )
    values_dir = output_dir / "values"
    values_dir.mkdir(parents=True, exist_ok=True)
    (values_dir / "store_icon.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<resources><color name="store_icon_background">#FFFFFFFF</color></resources>\n',
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Android launcher icon resources.")
    parser.add_argument("--icon", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    generate(args.icon, args.output_dir)


if __name__ == "__main__":
    main()
