"""Renders the MockMentor app icon PNGs (iOS 1024, Play Store 512) from the master shapes.

The same shapes, in a 120-unit square, appear in artwork/mockmentor-icon.svg and in the Android
vector drawables (composeApp/src/androidMain/res/drawable*/app_icon*.xml); keep them in sync.
Run from the repository root: python3 tools/render-icons.py   (needs Pillow)
"""
import re
from PIL import Image, ImageDraw

BACKGROUND = "#3E4796"
BACK_BUBBLE = ("#8F97D8", "M24 32 Q24 25 31 25 H67 Q74 25 74 32 V56 Q74 63 67 63 H45 L33 73 V63 H31 Q24 63 24 56 Z")
FRONT_BUBBLE = ("#FFFFFF", "M46 52 Q46 45 53 45 H89 Q96 45 96 52 V78 Q96 85 89 85 H87 V95 L75 85 H53 Q46 85 46 78 Z")
CHECK = ("#F2B93B", [(60, 65), (68, 73), (82, 58)], 7)
SUPERSAMPLE = 4


def polygon(d, steps=24):
    """Points of an absolute M/L/H/V/Q/Z path, sampling quadratic curves."""
    tokens = re.findall(r"[MLHVQZ]|-?\d+(?:\.\d+)?", d)
    points, i, x, y = [], 0, 0.0, 0.0
    while i < len(tokens):
        cmd = tokens[i]; i += 1
        if cmd in "ML":
            x, y = float(tokens[i]), float(tokens[i + 1]); i += 2; points.append((x, y))
        elif cmd == "H":
            x = float(tokens[i]); i += 1; points.append((x, y))
        elif cmd == "V":
            y = float(tokens[i]); i += 1; points.append((x, y))
        elif cmd == "Q":
            cx, cy, ex, ey = (float(t) for t in tokens[i:i + 4]); i += 4
            for s in range(1, steps + 1):
                t = s / steps
                points.append(((1 - t) ** 2 * x + 2 * (1 - t) * t * cx + t * t * ex, (1 - t) ** 2 * y + 2 * (1 - t) * t * cy + t * t * ey))
            x, y = ex, ey
    return points


def render(size):
    big = size * SUPERSAMPLE
    scale = big / 120
    image = Image.new("RGB", (big, big), BACKGROUND)  # iOS icons must be opaque and full-bleed
    draw = ImageDraw.Draw(image)
    for color, d in (BACK_BUBBLE, FRONT_BUBBLE):
        draw.polygon([(px * scale, py * scale) for px, py in polygon(d)], fill=color)
    color, line, width = CHECK
    pts = [(px * scale, py * scale) for px, py in line]
    w = width * scale
    draw.line(pts, fill=color, width=round(w), joint="curve")
    for px, py in (pts[0], pts[-1]):  # round caps
        draw.ellipse((px - w / 2, py - w / 2, px + w / 2, py + w / 2), fill=color)
    return image.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    render(1024).save("iosApp/PrepDesk/Assets.xcassets/AppIcon.appiconset/AppIcon1024.png")
    render(512).convert("RGBA").save("artwork/play-store-icon-512.png")  # Play asks for a 32-bit PNG
    print("Rendered iOS 1024 px and Play Store 512 px icons.")
