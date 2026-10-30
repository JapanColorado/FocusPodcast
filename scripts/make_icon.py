"""Generates the Yagi launcher, monochrome and notification icons plus PNG fallbacks.

Usage: python3 scripts/make_icon.py . [--preview <dir>]

Needs the system python3 with PyGObject and librsvg (python3-gi, gir1.2-rsvg-2.0), not the pixi env.
"""
import math
import os
import sys

import gi

gi.require_version("Rsvg", "2.0")
from gi.repository import Rsvg  # noqa: E402

BG = "#5E35B1"  # deep purple 600, next to the app's default accent (deep purple 500)
FG = "#FFFFFF"
ROTATION = -30.0
LAUNCHER_SCALE = 0.85

# Side view of a Yagi antenna on a 108 x 108 canvas: reflector, driven element and three directors
# along a boom, with three waves leaving the front.
ELEMENTS = [(26, 35, 73), (35.5, 38.5, 69.5), (45, 41.5, 66.5), (54, 44, 64), (62, 46.5, 61.5)]
ELEMENT_HALF_WIDTH = 2.25
BOOM = (26, 52.5, 63, 55.5)
WAVE_CENTER = (64, 54)
WAVES = [(7.5, 1.0), (13.5, 0.8), (19.5, 0.6)]
WAVE_ANGLE = math.radians(40)
WAVE_STROKE = 3.5


def element_path(cx, top, bottom):
    r = ELEMENT_HALF_WIDTH
    return (f"M{cx - r:g},{top + r:g} A{r:g},{r:g} 0 0 1 {cx + r:g},{top + r:g} "
            f"V{bottom - r:g} A{r:g},{r:g} 0 0 1 {cx - r:g},{bottom - r:g} Z")


def wave_path(radius):
    cx, cy = WAVE_CENTER
    x = cx + radius * math.cos(WAVE_ANGLE)
    dy = radius * math.sin(WAVE_ANGLE)
    return f"M{x:.2f},{cy - dy:.2f} A{radius:g},{radius:g} 0 0 1 {x:.2f},{cy + dy:.2f}"


def fill_paths():
    x1, y1, x2, y2 = BOOM
    paths = [f"M{x1:g},{y1:g} H{x2:g} V{y2:g} H{x1:g} Z"]
    paths += [element_path(*e) for e in ELEMENTS]
    return paths


def outline_points():
    """Points on the outline of every shape, before the group transform."""
    pts = []
    for cx, top, bottom in ELEMENTS:
        for dx in (-ELEMENT_HALF_WIDTH, ELEMENT_HALF_WIDTH):
            pts += [(cx + dx, top), (cx + dx, bottom)]
    cx, cy = WAVE_CENTER
    for radius, _ in WAVES:
        for i in range(41):
            a = -WAVE_ANGLE + 2 * WAVE_ANGLE * i / 40
            for rr in (radius - WAVE_STROKE / 2, radius + WAVE_STROKE / 2):
                pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def extent(scale, translate=(0.0, 0.0)):
    """Largest distance of the transformed glyph from the canvas centre, and its bounding box."""
    rot = math.radians(ROTATION)
    xs, ys = [], []
    for x, y in outline_points():
        dx, dy = (x - 54) * scale, (y - 54) * scale
        xs.append(54 + translate[0] + dx * math.cos(rot) - dy * math.sin(rot))
        ys.append(54 + translate[1] + dx * math.sin(rot) + dy * math.cos(rot))
    radius = max(math.hypot(x - 54, y - 54) for x, y in zip(xs, ys))
    return radius, (min(xs), min(ys), max(xs), max(ys))


def centring(scale):
    """The translation that puts the glyph's bounding box in the middle of the canvas."""
    _, (x0, y0, x1, y1) = extent(scale)
    return 54 - (x0 + x1) / 2, 54 - (y0 + y1) / 2


def vector_drawable(size_dp, scale, translate=(0.0, 0.0), comment=None):
    out = ['<?xml version="1.0" encoding="utf-8"?>']
    if comment:
        out.append(f"<!-- {comment} -->")
    out += [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size_dp}dp"',
        f'    android:height="{size_dp}dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
        "    <group",
        '        android:pivotX="54"',
        '        android:pivotY="54"',
        f'        android:rotation="{ROTATION:g}"',
        f'        android:scaleX="{scale:g}"',
        f'        android:scaleY="{scale:g}"',
        f'        android:translateX="{translate[0]:g}"',
        f'        android:translateY="{translate[1]:g}">',
    ]
    for d in fill_paths():
        out += ["        <path", f'            android:fillColor="{FG}"', f'            android:pathData="{d}" />']
    for radius, alpha in WAVES:
        out += [
            "        <path",
            f'            android:pathData="{wave_path(radius)}"',
            f'            android:strokeAlpha="{alpha:g}"',
            f'            android:strokeColor="{FG}"',
            '            android:strokeLineCap="round"',
            f'            android:strokeWidth="{WAVE_STROKE:g}" />',
        ]
    out += ["    </group>", "</vector>", ""]
    return "\n".join(out)


def glyph_svg(scale, translate=(0.0, 0.0)):
    tx, ty = translate
    body = [f'<g transform="translate({54 + tx:g} {54 + ty:g}) rotate({ROTATION:g}) scale({scale:g}) translate(-54 -54)">']
    body += [f'<path fill="{FG}" d="{d}"/>' for d in fill_paths()]
    body += [f'<path fill="none" stroke="{FG}" stroke-opacity="{a:g}" stroke-width="{WAVE_STROKE:g}" '
             f'stroke-linecap="round" d="{wave_path(r)}"/>' for r, a in WAVES]
    body.append("</g>")
    return "".join(body)


def icon_svg(shape):
    """The adaptive icon's visible 72 x 72 middle (viewBox 18..90) on a square, round or full background."""
    if shape == "round":
        bg = f'<circle cx="54" cy="54" r="36" fill="{BG}"/>'
    elif shape == "square":
        bg = f'<rect x="18" y="18" width="72" height="72" rx="13" fill="{BG}"/>'
    else:
        bg = f'<rect x="18" y="18" width="72" height="72" fill="{BG}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="72" height="72">'
            f"{bg}{glyph_svg(LAUNCHER_SCALE, centring(LAUNCHER_SCALE))}</svg>")


def render(svg, size, path):
    svg = svg.replace('width="72" height="72"', f'width="{size}" height="{size}"', 1)
    pixbuf = Rsvg.Handle.new_from_data(svg.encode()).get_pixbuf()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pixbuf.savev(path, "png", [], [])


def main():
    root = sys.argv[1]
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    radius, _ = extent(LAUNCHER_SCALE, centring(LAUNCHER_SCALE))
    print(f"launcher glyph reaches {radius:.1f} from the centre (safe zone 33)")

    # The notification icon fills its canvas: fit the glyph's bounding box into 108 with a margin.
    _, (x0, y0, x1, y1) = extent(1.0)
    notif_scale = 100.0 / max(x1 - x0, y1 - y0)
    notif_translate = centring(notif_scale)

    res = os.path.join(root, "core/src/main/res")
    if preview:
        res = os.path.join(preview, "res")
    files = {
        "drawable/ic_launcher_foreground.xml": vector_drawable(108, LAUNCHER_SCALE, centring(LAUNCHER_SCALE), comment="Generated by scripts/make_icon.py"),
        "drawable/mono_icon.xml": vector_drawable(108, LAUNCHER_SCALE, centring(LAUNCHER_SCALE), comment="Generated by scripts/make_icon.py"),
        "drawable/ic_notification.xml": vector_drawable(24, notif_scale, notif_translate, comment="Generated by scripts/make_icon.py"),
    }
    for rel, text in files.items():
        path = os.path.join(res, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(text)

    for density, size in [("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        render(icon_svg("square"), size, os.path.join(res, f"mipmap-{density}/ic_launcher.png"))
        render(icon_svg("round"), size, os.path.join(res, f"mipmap-{density}/ic_launcher_round.png"))
    render(icon_svg("round"), 512, os.path.join(res, "drawable-xxxhdpi/ic_splash.png"))
    store = os.path.join(preview or root, "fastlane/metadata/android/en-US/images/icon.png")
    render(icon_svg("full"), 512, store)


if __name__ == "__main__":
    main()
