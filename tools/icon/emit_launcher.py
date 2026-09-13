#!/usr/bin/env python3
"""emit_launcher.py — the 白い熊 black/yellow traced launcher icon of this fork.

One geometry model per app (yellow #FFFF00 stroke-only line art on #000000, redrawn from the
upstream launcher vector) is emitted as: a 512-viewBox SVG under design/, the Android adaptive
foreground (VectorDrawable, viewport 512), and the legacy raster mipmaps that upstream ships.
Run from the repo root:  python3 tools/icon/emit_launcher.py [--preview DIR]
Requires rsvg-convert and ImageMagick (`magick`).

Approved by 白い熊 on 2026-09-13 (previews shiroikuma-*-icon_2026-09-13_13-56-38.png).
"""
import argparse
import math
import os
import subprocess
import sys

INK = "#FFFF00"
PAPER = "#000000"
S = 512 / 108.0                   # upstream adaptive vectors use a 108 dp viewport
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

# ----------------------------------------------------------------------------------------------
# element model: ("path", d, w) | ("rect", x, y, w, h, rx, sw) | ("circle", cx, cy, r, sw, fill)
#                ("ellipse", cx, cy, rx, ry, rotation, sw) | ("group", {scale, pivot}, [children])


def sc(pts):
    """closed polygon from (x, y) points given in the 108 viewport"""
    return "M" + " L".join(f"{x*S:.1f},{y*S:.1f}" for x, y in pts) + " Z"


def rect_path(x, y, w, h, rx):
    if rx <= 0:
        return f"M{x},{y} h{w} v{h} h-{w} Z"
    return (f"M{x+rx},{y} h{w-2*rx} a{rx},{rx} 0 0 1 {rx},{rx} v{h-2*rx} a{rx},{rx} 0 0 1 -{rx},{rx} "
            f"h-{w-2*rx} a{rx},{rx} 0 0 1 -{rx},-{rx} v-{h-2*rx} a{rx},{rx} 0 0 1 {rx},-{rx} Z")


def circle_path(cx, cy, r):
    return f"M{cx-r:.1f},{cy:.1f} a{r},{r} 0 1 0 {2*r},0 a{r},{r} 0 1 0 -{2*r},0 Z"


def ellipse_path(cx, cy, rx, ry):
    return f"M{cx-rx:.1f},{cy:.1f} a{rx},{ry} 0 1 0 {2*rx},0 a{rx},{ry} 0 1 0 -{2*rx},0 Z"


# --- SVG ---------------------------------------------------------------------------------------

def svg_el(el, ink):
    kind = el[0]
    if kind == "path":
        _, d, w = el
        return (f'<path d="{d}" fill="none" stroke="{ink}" stroke-width="{w}" '
                f'stroke-linejoin="round" stroke-linecap="round"/>')
    if kind == "rect":
        _, x, y, w, h, rx, sw = el
        return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" ry="{rx}" fill="none" stroke="{ink}" stroke-width="{sw}"/>'
    if kind == "circle":
        _, cx, cy, r, sw, fill = el
        if fill:
            return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"/>'
        return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{ink}" stroke-width="{sw}"/>'
    if kind == "ellipse":
        _, cx, cy, rx, ry, rot, sw = el
        return (f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" transform="rotate({rot} {cx} {cy})" '
                f'fill="none" stroke="{ink}" stroke-width="{sw}"/>')
    if kind == "group":
        _, t, children = el
        px, py = t.get("pivot", (0, 0))
        s = t["scale"]
        tr = f"translate({px},{py}) scale({s}) translate({-px},{-py})"
        return f'<g transform="{tr}">' + "\n".join(svg_el(c, ink) for c in children) + "</g>"
    raise ValueError(kind)


def svg_doc(model, ink=INK, paper=PAPER):
    body = "\n".join(svg_el(e, ink) for e in model)
    bg = f'<rect width="512" height="512" fill="{paper}"/>\n' if paper else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">\n{bg}{body}\n</svg>\n'


# --- Android VectorDrawable --------------------------------------------------------------------

def vd_el(el, ink, indent="  "):
    kind = el[0]
    stroke = (f'android:fillColor="#00000000" android:strokeColor="{ink}" android:strokeLineCap="round" '
              f'android:strokeLineJoin="round"')
    if kind == "path":
        _, d, w = el
        return f'{indent}<path android:pathData="{d}" {stroke} android:strokeWidth="{w}"/>'
    if kind == "rect":
        _, x, y, w, h, rx, sw = el
        return f'{indent}<path android:pathData="{rect_path(x, y, w, h, rx)}" {stroke} android:strokeWidth="{sw}"/>'
    if kind == "circle":
        _, cx, cy, r, sw, fill = el
        if fill:
            return f'{indent}<path android:pathData="{circle_path(cx, cy, r)}" android:fillColor="{fill}"/>'
        return f'{indent}<path android:pathData="{circle_path(cx, cy, r)}" {stroke} android:strokeWidth="{sw}"/>'
    if kind == "ellipse":
        _, cx, cy, rx, ry, rot, sw = el
        return (f'{indent}<group android:rotation="{rot}" android:pivotX="{cx}" android:pivotY="{cy}">\n'
                f'{indent}  <path android:pathData="{ellipse_path(cx, cy, rx, ry)}" {stroke} android:strokeWidth="{sw}"/>\n'
                f'{indent}</group>')
    if kind == "group":
        _, t, children = el
        px, py = t.get("pivot", (0, 0))
        s = t["scale"]
        inner = "\n".join(vd_el(c, ink, indent + "  ") for c in children)
        return (f'{indent}<group android:scaleX="{s}" android:scaleY="{s}" android:pivotX="{px}" android:pivotY="{py}">\n'
                f'{inner}\n{indent}</group>')
    raise ValueError(kind)


def vector_drawable(model, ink=INK, size_dp=108, header=""):
    ink_a = "#FF" + ink[1:]
    body = "\n".join(vd_el(e, ink_a) for e in model)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n{header}'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="512"\n    android:viewportHeight="512">\n{body}\n</vector>\n')


# --- raster helpers ----------------------------------------------------------------------------

def render(svg_text, out_png, size, tmpdir):
    tmp = os.path.join(tmpdir, "render.svg")
    with open(tmp, "w") as f:
        f.write(svg_text)
    os.makedirs(os.path.dirname(out_png) or ".", exist_ok=True)
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), tmp, "-o", out_png], check=True)


def round_mask(src_png, out_png):
    size = subprocess.run(["magick", "identify", "-format", "%w", src_png], capture_output=True, text=True, check=True).stdout
    n = int(size)
    subprocess.run(["magick", src_png, "(", "-size", f"{n}x{n}", "xc:none", "-fill", "white",
                    "-draw", f"circle {n/2-0.5},{n/2-0.5} {n/2-0.5},0", ")",
                    "-alpha", "set", "-compose", "DstIn", "-composite", out_png], check=True)


def to_webp(png, out):
    subprocess.run(["magick", png, "-define", "webp:lossless=true", out], check=True)


# ----------------------------------------------------------------------------------------------
# geometry, one model per app (all coordinates in the 512 canvas; sc() maps the 108 viewport)

CHEVRON = sc([(26, 30), (37, 30), (56, 54), (37, 78), (26, 78), (45, 54)])
UNDERSCORE = sc([(56, 68), (82, 68), (82, 77), (56, 77)])

EMACS_SWIRL = ("m44.28,77.94c0,0 2.74,0.2 6.25,-0.12 1.42,-0.13 6.84,-0.67 10.88,-1.57 0,0 4.93,-1.08 7.57,-2.07 "
               "2.76,-1.04 4.26,-1.92 4.94,-3.17 -0.03,-0.26 0.21,-1.16 -1.06,-1.71C69.6,67.91 65.83,68.17 58.36,68 "
               "50.07,67.71 47.32,66.3 45.85,65.16 44.44,64.01 45.15,60.81 51.18,57.99 54.22,56.5 66.13,53.73 66.13,53.73 "
               "62.12,51.71 54.64,48.15 53.1,47.39 51.75,46.71 49.6,45.7 49.13,44.47c-0.53,-1.18 1.25,-2.19 2.25,-2.48 "
               "3.21,-0.94 7.73,-1.53 11.86,-1.6 2.07,-0.03 2.41,-0.17 2.41,-0.17 2.86,-0.48 4.74,-2.48 3.96,-5.64 "
               "-0.7,-3.22 -4.42,-5.12 -7.94,-4.46 -3.32,0.62 -11.33,2.99 -11.33,2.99 9.89,-0.09 11.55,0.08 12.29,1.14 "
               "0.44,0.62 -0.2,1.48 -2.84,1.92 -2.87,0.48 -8.85,1.05 -8.85,1.05 -5.73,0.35 -9.76,0.37 -10.98,2.98 "
               "-0.79,1.71 0.84,3.22 1.56,4.16 3.03,3.43 7.4,5.28 10.21,6.65 1.06,0.51 4.17,1.48 4.17,1.48 "
               "-9.13,-0.51 -15.71,2.35 -19.58,5.64 -4.37,4.12 -2.44,9.03 6.51,12.06 5.29,1.79 7.91,2.63 15.79,1.9 "
               "4.64,-0.26 5.38,-0.1 5.42,0.29 0.07,0.55 -5.16,1.91 -6.59,2.33 -3.63,1.07 -13.14,3.22 -13.19,3.23z")


def emacs_mark():
    return [("group", {"scale": round(S, 4)}, [
        ("circle", 54, 54, 33, 3.4, None),
        ("path", EMACS_SWIRL, 2.7),
    ])]


def wrench():
    cx, cy, r = 418, 382, 30
    a1, a2 = math.radians(-15), math.radians(-75)
    head = (f"M{cx+r*math.cos(a1):.0f},{cy+r*math.sin(a1):.0f} A{r},{r} 0 1,1 "
            f"{cx+r*math.cos(a2):.0f},{cy+r*math.sin(a2):.0f}")
    return [("circle", cx, cy, 58, 0, PAPER), ("circle", 318, 482, 26, 0, PAPER),
            ("path", head, 13), ("path", f"M{cx-21},{cy+21} L318,482", 20)]


X0, Y0, BW = 262, 148, 136
CX, CY = X0 + BW / 2, Y0 + BW / 2

MODELS = {
    # Termux: the hollow >_ inside a rounded-square frame (upstream's rounded screen/border), kept
    # inside the adaptive icon's 72 dp visible zone (85..427 of 512) so launcher masks do not eat it.
    "termux": [("rect", 88, 88, 336, 336, 60, 14),
               ("group", {"scale": 0.86, "pivot": (256, 256)}, [("path", CHEVRON, 14), ("path", UNDERSCORE, 14)])],
    # Termux:API: upstream draws the same glyph on a black disc → disc outline + glyph
    "termux-api": [("path", f"M{18*S:.1f},{54*S:.1f} A{36*S:.1f},{36*S:.1f} 0 1,1 {90*S:.1f},{54*S:.1f} "
                            f"A{36*S:.1f},{36*S:.1f} 0 1,1 {18*S:.1f},{54*S:.1f} Z", 14),
                   ("path", sc([(30, 36), (39, 36), (53, 54), (39, 72), (30, 72), (44, 54)]), 12),
                   ("path", sc([(55, 63), (74, 63), (74, 70), (55, 70)]), 12)],
    # Termux:X11: chevron, the X box with its orbit, underscore (upstream ic_launcher-web.png layout)
    "termux-x11": [("path", sc([(20, 28), (31, 28), (49, 54), (31, 80), (20, 80), (38, 54)]), 14),
                   ("rect", X0, Y0, BW, BW, 0, 12),
                   ("path", f"M{X0+26},{Y0+24} L{X0+BW-26},{Y0+BW-24}", 24),
                   ("path", f"M{X0+BW-26},{Y0+24} L{X0+26},{Y0+BW-24}", 12),
                   ("ellipse", int(CX), int(CY + 4), int(BW * 0.50), int(BW * 0.20), -14, 7),
                   ("rect", X0 + 10, Y0 + BW + 42, BW - 10, 42, 0, 14)],
    # Termux:GUI: chevron, the open window frame with its dot, underscore (upstream 512 playstore layout)
    "termux-gui": [("path", sc([(22, 24), (32, 24), (50, 54), (32, 84), (22, 84), (40, 54)]), 14),
                   ("path", "M342,156 L284,156 L284,285 L384,285 L384,212", 24),
                   ("circle", 405, 140, 24, 12, None),
                   ("rect", 270, 350, 162, 42, 0, 14)],
    # GNU Emacs: the swirl-E mark inside the disc; the UI-page entry adds a wrench
    "emacs": emacs_mark(),
    "emacs-ui": emacs_mark() + wrench(),
}

HEADER = ("<!-- 白い熊 fork launcher art: yellow line-art on black, redrawn from upstream's launcher vector.\n"
          "     Generated by tools/icon/emit_launcher.py — edit the model there, not this file. -->\n")


def emit(app, root, preview, tmpdir):
    model = MODELS[app]
    design = os.path.join(root, "design")
    os.makedirs(design, exist_ok=True)
    full = svg_doc(model)                       # black paper, yellow ink — legacy/preview art
    fg = svg_doc(model, paper=None)             # transparent — adaptive foreground rasters
    mono = svg_doc(model, ink="#FFFFFF", paper=None)
    with open(os.path.join(design, f"shiroikuma-{app}-icon.svg"), "w") as f:
        f.write(full)
    if preview:
        render(full, os.path.join(preview, f"shiroikuma-{app}-icon.png"), 512, tmpdir)

    def mipmaps(dirpat, base, ext="png", round_name=None):
        for dname, mult in DENSITIES:
            d = dirpat.format(dname)
            os.makedirs(d, exist_ok=True)
            png = os.path.join(tmpdir, f"{base}-{dname}.png")
            render(full, png, int(48 * mult), tmpdir)
            out = os.path.join(d, f"{base}.{ext}")
            to_webp(png, out) if ext == "webp" else subprocess.run(["cp", png, out], check=True)
            if round_name:
                rpng = os.path.join(tmpdir, f"{base}-{dname}-round.png")
                round_mask(png, rpng)
                rout = os.path.join(d, f"{round_name}.{ext}")
                to_webp(rpng, rout) if ext == "webp" else subprocess.run(["cp", rpng, rout], check=True)

    if app == "termux":
        res = os.path.join(root, "app/src/main/res")
        with open(os.path.join(res, "drawable/ic_foreground.xml"), "w") as f:
            f.write(vector_drawable(model, header=HEADER))
        mipmaps(os.path.join(res, "mipmap-{}"), "ic_launcher", "png", "ic_launcher_round")
        render(full, os.path.join(root, "fastlane/metadata/android/en-US/images/icon.png"), 512, tmpdir)
    elif app == "termux-api":
        res = os.path.join(root, "app/src/main/res")
        with open(os.path.join(res, "drawable/ic_foreground.xml"), "w") as f:
            f.write(vector_drawable(model, header=HEADER))
        # pre-26 launcher icon: the same art on a black disc (upstream's shape), 48 dp
        legacy = [("circle", 256, 256, 256, 0, PAPER)] + model
        with open(os.path.join(res, "drawable/ic_launcher.xml"), "w") as f:
            f.write(vector_drawable(legacy, size_dp=48, header=HEADER))
    elif app == "termux-x11":
        res = os.path.join(root, "lorie/src/main/res")
        for dname, mult in DENSITIES:
            d = os.path.join(res, f"mipmap-{dname}")
            n = int(108 * mult)
            render(svg_doc([], paper=PAPER), os.path.join(d, "lorie_ic_launcher_background.png"), n, tmpdir)
            render(fg, os.path.join(d, "lorie_ic_launcher_foreground.png"), n, tmpdir)
            render(mono, os.path.join(d, "lorie_ic_launcher_monochrome.png"), n, tmpdir)
        mipmaps(os.path.join(res, "mipmap-{}"), "lorie_ic_launcher", "png", "lorie_ic_launcher_round")
        render(full, os.path.join(root, "lorie/src/main/ic_launcher-web.png"), 512, tmpdir)
    elif app == "termux-gui":
        res = os.path.join(root, "app/src/main/res")
        with open(os.path.join(res, "drawable/ic_launcher_foreground.xml"), "w") as f:
            f.write(vector_drawable(model, header=HEADER))
        mipmaps(os.path.join(res, "mipmap-{}"), "ic_launcher", "webp", "ic_launcher_round")
        render(full, os.path.join(root, "app/src/main/ic_launcher-playstore.png"), 512, tmpdir)
    elif app == "emacs":
        res = os.path.join(root, "java/res")
        os.makedirs(os.path.join(res, "mipmap-v26"), exist_ok=True)
        for name, m in (("shiroikuma", MODELS["emacs"]), ("shiroikuma_ui", MODELS["emacs-ui"])):
            with open(os.path.join(res, f"drawable/{name}_foreground.xml"), "w") as f:
                f.write(vector_drawable(m, header=HEADER))
            with open(os.path.join(res, f"mipmap-v26/{name}_icon.xml"), "w") as f:
                f.write('<?xml version="1.0" encoding="utf-8"?>\n' + HEADER +
                        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                        '  <background android:drawable="@drawable/shiroikuma_background"/>\n'
                        f'  <foreground android:drawable="@drawable/{name}_foreground"/>\n'
                        f'  <monochrome android:drawable="@drawable/{name}_foreground"/>\n'
                        '</adaptive-icon>\n')
            render(svg_doc(m), os.path.join(res, f"mipmap/{name}_icon.png"), 192, tmpdir)
        with open(os.path.join(res, "drawable/shiroikuma_background.xml"), "w") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n' + HEADER +
                    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                    '    android:width="108dp" android:height="108dp"\n'
                    '    android:viewportWidth="108" android:viewportHeight="108">\n'
                    '  <path android:fillColor="#FF000000" android:pathData="M0,0h108v108h-108z"/>\n</vector>\n')
        # DocumentsProvider root icon (upstream: drawable/emacs.png, 128 px) and the monochrome
        # notification glyph (24 dp, tinted by the system)
        render(full, os.path.join(res, "drawable/shiroikuma.png"), 128, tmpdir)
        with open(os.path.join(res, "drawable/shiroikuma_notification.xml"), "w") as f:
            f.write(vector_drawable(MODELS["emacs"], ink="#FFFFFF", size_dp=24, header=HEADER))
        with open(os.path.join(design, "shiroikuma-emacs-ui-icon.svg"), "w") as f:
            f.write(svg_doc(MODELS["emacs-ui"]))
    else:
        raise SystemExit(f"unknown app {app}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--app", required=True, choices=["termux", "termux-api", "termux-x11", "termux-gui", "emacs"])
    ap.add_argument("--root", default=".")
    ap.add_argument("--preview", default=None, help="directory for a 512 px preview PNG")
    a = ap.parse_args()
    import tempfile
    with tempfile.TemporaryDirectory() as tmpdir:
        emit(a.app, a.root, a.preview, tmpdir)
    print("ok")


if __name__ == "__main__":
    main()
