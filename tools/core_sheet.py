#!/usr/bin/env python3
"""contact sheet of generated textures (for eyeballing); usage: core_sheet.py out.png tex1.png tex2.png ..."""
import sys
from pathlib import Path

from PIL import Image, ImageDraw


def sheet(paths, out, cell=128, cols=6):
    paths = [Path(p) for p in paths]
    rows = (len(paths) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (cell + 8) + 8, rows * (cell + 22) + 8), (58, 58, 64, 255))
    d = ImageDraw.Draw(img)
    for i, p in enumerate(paths):
        t = Image.open(p).convert("RGBA")
        k = cell / max(t.size)
        t = t.resize((max(1, int(t.width * k)), max(1, int(t.height * k))), Image.NEAREST if k > 1 else Image.LANCZOS)
        x = 8 + (i % cols) * (cell + 8)
        y = 8 + (i // cols) * (cell + 22)
        img.alpha_composite(t, (x, y))
        d.text((x, y + cell + 4), p.stem[:24], fill=(230, 230, 235, 255))
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


if __name__ == "__main__":
    sheet(sys.argv[2:], sys.argv[1])
