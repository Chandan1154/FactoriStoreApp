#!/usr/bin/env python3
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


DEMOS = {
    "zolara": ("ZO", "#293E72", "#F5C45E"),
    "gkp-vastra": ("GV", "#702D4D", "#F1C7A0"),
}


def create_icons(output_dir: Path) -> None:
    for slug, (monogram, background, accent) in DEMOS.items():
        image = Image.new("RGBA", (512, 512), background)
        draw = ImageDraw.Draw(image)
        draw.ellipse((48, 48, 464, 464), fill=accent)
        draw.ellipse((64, 64, 448, 448), fill=background)
        font = ImageFont.load_default(size=180)
        bounds = draw.textbbox((0, 0), monogram, font=font)
        text_width = bounds[2] - bounds[0]
        text_height = bounds[3] - bounds[1]
        draw.text(
            ((512 - text_width) / 2, (512 - text_height) / 2 - bounds[1]),
            monogram,
            fill=accent,
            font=font,
        )
        destination = output_dir / f"{slug}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination, format="PNG", optimize=True)
        print(destination)


def main() -> None:
    parser = argparse.ArgumentParser(description="Create distinct internal PoC monogram icons.")
    parser.add_argument("--output-dir", type=Path, default=Path("proof-of-concept/icons"))
    args = parser.parse_args()
    create_icons(args.output_dir)


if __name__ == "__main__":
    main()
