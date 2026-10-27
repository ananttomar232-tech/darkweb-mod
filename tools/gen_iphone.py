#!/usr/bin/env python3
"""
gen_iphone.py - procedural, supersampled texture painter for the iPhone Duo module.

Writes
  assets/darkweb_duo/textures/entity/iphone/*.png   (3D model textures: atlas, glass, back panels, screens)
  assets/darkweb_duo/textures/gui/iphone/*.png      (app icons, wallpapers, logo, touch-id glyph)
  src/main/java/com/darkwebduo/client/render/PhoneUv.java   (atlas regions + layout constants shared with the renderer)
  tools/lang/iphone.json

Everything is ORIGINAL artwork (no Apple logo / icons / wallpapers).  Design reference: the iPhone Duo spec sheet
(5.4in outer + 7.6in inner display, titanium frame, Ceramic Shield, dual 48MP rear cameras, Touch ID side button,
Star White / Night Sky colours).
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources/assets/darkweb_duo"
ENT = RES / "textures/entity/iphone"
GUI = RES / "textures/gui/iphone"
JAVA = ROOT / "src/main/java/com/darkwebduo/client/render/PhoneUv.java"
LANG = ROOT / "tools/lang/iphone.json"
PREVIEW = ROOT / "tools/preview"
FONTS = "/usr/share/fonts/opentype/inter/"
SS = 4

COLORWAYS = ["star", "night"]


def font(name, size):
    return ImageFont.truetype(FONTS + name + ".otf", int(size))


# ------------------------------------------------------------------------------------------------ numpy helpers
def fbm(W, H, seed, octaves=5, base=3, persistence=0.5):
    rng = np.random.default_rng(seed)
    out = np.zeros((H, W), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = base * (2 ** o)
        gh, gw = max(2, n), max(2, int(round(n * W / float(H))))
        g = (rng.random((gh, gw)) * 255).astype(np.uint8)
        layer = np.asarray(Image.fromarray(g).resize((W, H), Image.BICUBIC), np.float32) / 255.0
        out += layer * amp
        tot += amp
        amp *= persistence
    out /= tot
    lo, hi = float(out.min()), float(out.max())
    return (out - lo) / max(1e-6, hi - lo)


def ramp(t, stops):
    t = np.clip(t, 0.0, 1.0)
    out = np.zeros(t.shape + (3,), np.float32)
    ps = [s[0] for s in stops]
    for c in range(3):
        out[..., c] = np.interp(t, ps, [s[1][c] for s in stops])
    return out


def to_rgba(rgb, alpha=None):
    a = np.full(rgb.shape[:2], 255.0, np.float32) if alpha is None else alpha
    arr = np.dstack([np.clip(rgb, 0, 255), np.clip(a, 0, 255)]).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def grid(W, H):
    x = np.linspace(0, 1, W, dtype=np.float32)[None, :] * np.ones((H, 1), np.float32)
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None] * np.ones((1, W), np.float32)
    return x, y


def rr_mask(W, H, box, r, ss=SS):
    m = Image.new("L", (W * ss, H * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle([box[0] * ss, box[1] * ss, box[2] * ss - 1, box[3] * ss - 1], r * ss, fill=255)
    return m.resize((W, H), Image.LANCZOS)


def fill_rr(img, box, r, c0, c1=None, ss=SS):
    W, H = img.size
    m = rr_mask(W, H, box, r, ss)
    if c1 is None:
        c1 = c0
    arr = np.zeros((H, W, 4), np.float32)
    ys = np.clip((np.arange(H) - box[1]) / max(1.0, (box[3] - box[1])), 0, 1)[:, None]
    for k in range(4):
        arr[:, :, k] = c0[k] * (1 - ys) + c1[k] * ys
    arr[:, :, 3] *= np.asarray(m, np.float32) / 255.0
    img.alpha_composite(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA"))


def stroke_rr(img, box, r, w, color, ss=SS):
    W, H = img.size
    outer = rr_mask(W, H, box, r, ss)
    inner = rr_mask(W, H, (box[0] + w, box[1] + w, box[2] - w, box[3] - w), max(0.5, r - w), ss)
    ring = np.clip(np.asarray(outer, np.float32) - np.asarray(inner, np.float32), 0, 255) / 255.0
    arr = np.zeros((H, W, 4), np.float32)
    arr[:, :, 0], arr[:, :, 1], arr[:, :, 2] = color[0], color[1], color[2]
    arr[:, :, 3] = ring * color[3]
    img.alpha_composite(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA"))


def txt(img, xy, s, fnt, fill, anchor="la", shadow=None):
    d = ImageDraw.Draw(img)
    if shadow is not None:
        d.text((xy[0] + shadow[0], xy[1] + shadow[1]), s, font=fnt, fill=shadow[2], anchor=anchor)
    d.text(xy, s, font=fnt, fill=fill, anchor=anchor)


def glow_layer(size, draw_fn, radius, strength=1.0):
    """renders draw_fn(ImageDraw) onto a layer, blurs it and returns an RGBA layer (for bloom)"""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    g = layer.filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        a = np.asarray(g, np.float32)
        a[..., 3] = np.clip(a[..., 3] * strength, 0, 255)
        g = Image.fromarray(a.astype(np.uint8), "RGBA")
    return g


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


# ------------------------------------------------------------------------------------------------ materials
TITANIUM = {
    "star": ((138, 136, 133), (247, 245, 240)),
    "night": ((22, 26, 36), (150, 160, 182)),
}


def titanium_strip(W, H, colorway, seed, polish=1.0):
    lo, hi = TITANIUM[colorway]
    rng = np.random.default_rng(seed)
    v = ((np.arange(H, dtype=np.float32) + 0.5) / H)[:, None]
    u = ((np.arange(W, dtype=np.float32) + 0.5) / W)[None, :]
    prof = (0.50 + 0.30 * np.exp(-((v - 0.14) / 0.07) ** 2) + 0.24 * np.exp(-((v - 0.86) / 0.07) ** 2)
            + 0.10 * np.sin(v * math.pi) - 0.10 * np.exp(-((v - 0.5) / 0.12) ** 2))
    rows = rng.normal(0, 1, (H, 1)).astype(np.float32)
    rows = np.asarray(Image.fromarray(((rows - rows.min()) / (np.ptp(rows) + 1e-6) * 255).astype(np.uint8)).resize((1, H), Image.BICUBIC), np.float32) / 255.0
    drift = fbm(W, H, seed + 3, 3, 2)
    shade = prof * polish + (rows - 0.5) * 0.045 + (drift - 0.5) * 0.10
    shade = np.clip(shade, 0, 1)
    # long horizontal specular streak (polished titanium catches the light unevenly along the frame)
    streak = 0.25 * np.exp(-((u - 0.28) / 0.10) ** 2) + 0.22 * np.exp(-((u - 0.74) / 0.07) ** 2)
    shade = np.clip(shade + streak * (0.4 + 0.6 * prof), 0, 1)
    rgb = np.zeros((H, W, 3), np.float32)
    for c in range(3):
        rgb[..., c] = lo[c] + (hi[c] - lo[c]) * shade
    return to_rgba(rgb)


def brushed(W, H, colorway, seed):
    lo, hi = TITANIUM[colorway]
    rng = np.random.default_rng(seed)
    n = fbm(W, H, seed, 3, 2)
    rows = rng.normal(0, 1, (H, 1)).astype(np.float32)
    cols = np.zeros((1, W), np.float32)
    shade = 0.55 + 0.06 * rows + 0.25 * (n - 0.5)
    v = ((np.arange(H, dtype=np.float32) + 0.5) / H)[:, None]
    shade = shade + 0.22 * np.exp(-((v - 0.25) / 0.12) ** 2) - 0.12 * np.exp(-((v - 0.9) / 0.1) ** 2)
    shade = np.clip(shade + cols, 0, 1)
    rgb = np.zeros((H, W, 3), np.float32)
    for c in range(3):
        rgb[..., c] = lo[c] + (hi[c] - lo[c]) * shade
    return to_rgba(rgb)


def carbon(W, H, seed=5):
    x = np.arange(W)[None, :]
    y = np.arange(H)[:, None]
    cell = 4
    tw = ((x // cell + y // cell) % 2).astype(np.float32)
    diag = np.sin((x + y) * 0.55) * 0.5 + 0.5
    fine = np.sin(x * 1.6) * np.sin(y * 1.6)
    base = 0.16 + 0.12 * tw + 0.08 * diag * (0.6 + 0.4 * tw) + 0.03 * fine
    sheen = np.exp(-(((x + y * 0.4) / (W + 0.4 * H) - 0.45) / 0.18) ** 2) * 0.10
    v = np.clip(base + sheen, 0, 1)
    rgb = np.dstack([v * 255 * 0.95, v * 255 * 0.97, v * 255 * 1.05]).astype(np.float32)
    return to_rgba(rgb)


def gradient(W, H, c0, c1, angle=90.0):
    x, y = grid(W, H)
    a = math.radians(angle)
    t = np.clip(x * math.cos(a) + y * math.sin(a), 0, 1)
    t = (t - t.min()) / max(1e-6, (t.max() - t.min()))
    return ramp(t, [(0, c0), (1, c1)])


# ------------------------------------------------------------------------------------------------ camera lenses
def paint_lens(size, rings, tint, seed):
    """top view of a rear camera lens: titanium barrel ring, black gasket, coated glass stack"""
    S = 4
    N = size * S
    cx = cy = N / 2.0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = (xx - cx) / (N / 2.0), (yy - cy) / (N / 2.0)
    r = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    rgb = np.zeros((N, N, 3), np.float32)
    a = np.ones((N, N), np.float32) * 255

    # barrel ring (brushed circular titanium with a bevel)
    ring = (r > 0.80) & (r <= 1.0)
    bevel = 0.5 + 0.5 * np.cos((r - 0.80) / 0.20 * math.pi * 1.4 + 0.6)
    spec = np.exp(-((ang + 2.4) / 0.55) ** 2) * 0.55 + np.exp(-((ang - 0.75) / 0.45) ** 2) * 0.35
    metal = np.clip(0.32 + 0.30 * bevel + spec, 0, 1)
    for c, base in enumerate((150, 154, 162)):
        rgb[..., c] = np.where(ring, metal * (base + 100), rgb[..., c])
    # gasket
    gask = (r > 0.72) & (r <= 0.80)
    for c in range(3):
        rgb[..., c] = np.where(gask, 10 + 6 * np.sin(r * 90), rgb[..., c])
    # coated glass: dark base + radial thin-film bands
    glass = r <= 0.72
    rn = np.clip(r / 0.72, 0, 1)
    base = 12 + 20 * (1 - rn) ** 2
    film = np.zeros((N, N, 3), np.float32)
    for k, rad in enumerate(rings):
        band = np.exp(-((rn - rad) / 0.035) ** 2)
        col = np.array([[0.20, 0.65, 0.45], [0.60, 0.25, 0.85], [0.20, 0.35, 0.95], [0.85, 0.35, 0.55]][k % 4], np.float32)
        film += band[..., None] * col[None, None, :] * 170
    # inner element disc
    core = np.exp(-(rn / 0.30) ** 2)
    film += core[..., None] * (np.array(tint, np.float32)[None, None, :] / 255.0) * 120
    rim_hi = np.exp(-((rn - 0.93) / 0.05) ** 2)
    film += rim_hi[..., None] * np.array([120, 130, 160], np.float32)[None, None, :]
    # anisotropic reflection streaks
    streak = np.exp(-(((dx * 0.8 + dy * 0.6) - 0.20) / 0.07) ** 2) * (1 - rn) * 0.9
    film += streak[..., None] * np.array([140, 150, 190], np.float32)[None, None, :]
    # aperture pupil
    pup = np.exp(-(rn / 0.085) ** 2)
    film *= (1 - 0.92 * pup)[..., None]
    for c in range(3):
        rgb[..., c] = np.where(glass, base + film[..., c], rgb[..., c])
    # specular crescent (top-left)
    cres = np.exp(-(((dx + 0.26) ** 2 + (dy + 0.30) ** 2) / 0.012))
    for c in range(3):
        rgb[..., c] = np.where(glass, rgb[..., c] + cres * 210, rgb[..., c])
    a = np.where(r <= 1.0, 255, 0).astype(np.float32)
    img = to_rgba(rgb, a).resize((size, size), Image.LANCZOS)
    return img


def paint_plateau(W, H, colorway, seed):
    x, y = grid(W, H)
    n = fbm(W, H, seed, 4, 3)
    if colorway == "star":
        c0, c1 = (232, 230, 226), (196, 196, 198)
    else:
        c0, c1 = (26, 32, 52), (8, 10, 18)
    base = gradient(W, H, c0, c1, 70)
    base += (n[..., None] - 0.5) * 8
    # bevel darkening at the edge, bright rim line
    ex = np.minimum(x, 1 - x) * W
    ey = np.minimum(y, 1 - y) * H
    e = np.minimum(ex, ey)
    bev = np.clip(e / 6.0, 0, 1)
    rim = np.exp(-((e - 2.0) / 1.2) ** 2)
    base = base * (0.55 + 0.45 * bev)[..., None] + rim[..., None] * (60 if colorway == "star" else 70)
    # glossy sweep
    sweep = np.exp(-(((x * 0.7 + y * 0.45) - 0.38) / 0.09) ** 2) * (0.35 if colorway == "star" else 0.28)
    base += sweep[..., None] * 160
    return to_rgba(base)


def paint_flash(S=64):
    N = S * 4
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    dx, dy = (xx - N / 2) / (N / 2), (yy - N / 2) / (N / 2)
    r = np.sqrt(dx * dx + dy * dy)
    rgb = np.zeros((N, N, 3), np.float32)
    ring = (r > 0.82) & (r <= 1.0)
    for c, v in enumerate((120, 120, 126)):
        rgb[..., c] = np.where(ring, v + 70 * np.cos(r * 9), rgb[..., c])
    inner = r <= 0.82
    warm = np.clip(1 - r / 0.82, 0, 1)
    dots = (np.sin(dx * 38) * np.sin(dy * 38) > 0.25).astype(np.float32)
    base = np.array([250, 240, 214], np.float32)
    for c in range(3):
        rgb[..., c] = np.where(inner, base[c] * (0.55 + 0.35 * warm) * (0.88 + 0.12 * dots), rgb[..., c])
    return to_rgba(rgb, np.where(r <= 1.0, 255, 0)).resize((S, S), Image.LANCZOS)


def paint_port(W, H):
    img = Image.new("RGBA", (W, H), (6, 6, 8, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 1, W - 2, H - 2], radius=H // 2, outline=(70, 72, 80, 255), width=1)
    d.rounded_rectangle([W * 0.18, H * 0.38, W * 0.82, H * 0.62], radius=H // 4, fill=(120, 126, 138, 255))
    return img


def paint_grille(W, H):
    img = Image.new("RGBA", (W, H), (150, 148, 146, 255))
    d = ImageDraw.Draw(img)
    cols, rows = 6, 2
    for i in range(cols):
        for j in range(rows):
            cx = (i + 0.5) * W / cols
            cy = (j + 0.5) * H / rows
            d.ellipse([cx - 2.2, cy - 2.2, cx + 2.2, cy + 2.2], fill=(10, 10, 12, 255))
    return img


# ------------------------------------------------------------------------------------------------ atlas
REG = {}


def build_atlas(cw):
    A = Image.new("RGBA", (512, 512), (0, 0, 0, 255))

    def put(name, img, x, y):
        A.paste(img, (x, y))
        REG[name] = (x, y, x + img.size[0], y + img.size[1])

    put("FRAME", titanium_strip(512, 48, cw, 101), 0, 0)
    put("BRUSHED", brushed(128, 48, cw, 102), 0, 48)
    put("HINGE_TI", titanium_strip(128, 48, cw, 103, 0.9), 128, 48)
    put("CARBON", carbon(128, 48), 256, 48)
    put("PORT", paint_port(64, 48), 384, 48)
    put("GRILLE", paint_grille(64, 48), 448, 48)
    put("PLATEAU", paint_plateau(256, 112, cw, 104), 0, 96)
    put("LENS_MAIN", paint_lens(128, [0.18, 0.40, 0.62, 0.80], (130, 90, 210), 1), 256, 96)
    put("LENS_WIDE", paint_lens(128, [0.22, 0.46, 0.70], (70, 120, 220), 2), 384, 96)
    put("FLASH", paint_flash(64), 0, 224)
    put("MIC", Image.new("RGBA", (16, 16), (4, 4, 6, 255)), 64, 224)
    put("TOUCHID", touchid_top(32, 128, cw), 128, 224)
    return A


def touchid_top(W, H, cw):
    """east face of the Touch ID side button (portrait W x H): brushed titanium with a sapphire sensor window"""
    img = brushed(W, H, cw, 109)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([W * 0.10, H * 0.04, W * 0.90, H * 0.96], radius=W * 0.40,
                        fill=(36, 38, 46, 255) if cw == "night" else (205, 206, 210, 255),
                        outline=(255, 255, 255, 90), width=1)
    d.rounded_rectangle([W * 0.26, H * 0.12, W * 0.74, H * 0.88], radius=W * 0.26,
                        fill=(10, 12, 18, 255) if cw == "night" else (236, 238, 242, 255))
    return img


# ------------------------------------------------------------------------------------------------ panels
BACK_PLATEAU = (0.060, 0.040, 0.620, 0.200)      # u0 v0 u1 v1 on the back of the camera panel
LENS_MAIN_C = (0.205, 0.120, 0.082)              # centre u, v, radius (fraction of panel width)
LENS_WIDE_C = (0.405, 0.120, 0.066)
FLASH_C = (0.540, 0.092, 0.022)
MIC_C = (0.560, 0.158, 0.010)
EMBLEM_C = (0.500, 0.560, 0.26)                   # centre u, v, size (fraction of width)


def duo_emblem(draw, cx, cy, R, fill, width):
    """two interlocking rings - the original 'Duo' mark (not any real brand's logo)"""
    off = R * 0.52
    for sx in (-1, 1):
        draw.ellipse([cx + sx * off - R, cy - R, cx + sx * off + R, cy + R], outline=fill, width=width)


def paint_back(cw, W=512, H=768):
    seed = 400 + (0 if cw == "star" else 7)
    n = fbm(W, H, seed, 5, 3)
    n2 = fbm(W, H, seed + 1, 3, 12)
    if cw == "star":
        base = gradient(W, H, (238, 235, 229), (206, 205, 204), 62)
        base += (n[..., None] - 0.5) * 12
        grain = (np.random.default_rng(seed).random((H, W)) - 0.5) * 7
    else:
        base = gradient(W, H, (18, 24, 44), (6, 8, 16), 62)
        base += (n[..., None] - 0.5) * 10
        grain = (np.random.default_rng(seed).random((H, W)) - 0.5) * 6
    base += grain[..., None]
    x, y = grid(W, H)
    # satin sheen bands
    sheen = np.exp(-(((x * 0.55 + y * 0.9) - 0.55) / 0.12) ** 2) * (0.20 if cw == "star" else 0.16)
    sheen += np.exp(-(((x * 0.55 + y * 0.9) - 1.0) / 0.05) ** 2) * (0.10 if cw == "star" else 0.10)
    base += sheen[..., None] * (120 if cw == "star" else 140)
    if cw == "night":
        # faint star-dust speckle: the 'night sky'
        rng = np.random.default_rng(seed + 11)
        sp = (rng.random((H, W)) > 0.9985).astype(np.float32)
        sp = np.asarray(Image.fromarray((sp * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255.0
        base += sp[..., None] * np.array([150, 170, 255], np.float32) * 1.6
        base += (n2[..., None] - 0.5) * 6
    img = to_rgba(base)
    # contact shadow under the camera plateau
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([BACK_PLATEAU[0] * W - 2, BACK_PLATEAU[1] * H + 3, BACK_PLATEAU[2] * W + 4, BACK_PLATEAU[3] * H + 9],
                                         radius=26, fill=(0, 0, 0, 120 if cw == "star" else 200))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(9)))
    # engraved emblem + wordmark
    em = Image.new("RGBA", (W * 2, H * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(em)
    cx, cy, R = EMBLEM_C[0] * W * 2, EMBLEM_C[1] * H * 2, EMBLEM_C[2] * W * 2 * 0.5
    col_hi = (255, 255, 255, 120) if cw == "star" else (150, 175, 230, 90)
    col_lo = (60, 60, 64, 90) if cw == "star" else (0, 0, 0, 160)
    duo_emblem(d, cx + 2, cy + 3, R, col_lo, 10)
    duo_emblem(d, cx, cy, R, col_hi, 8)
    d.text((W, int(H * 2 * 0.90)), "iPhone Duo", font=font("Inter-Medium", 34), fill=col_hi, anchor="mm")
    em = em.resize((W, H), Image.LANCZOS)
    img.alpha_composite(em)
    return img


def paint_inner_glass(side, W=512, H=768):
    """front glass of one inner panel: black glass with a very thin chamfered rim and a soft reflection"""
    rng = np.random.default_rng(300 + side)
    x, y = grid(W, H)
    base = np.full((H, W, 3), 3.0, np.float32)
    base += gradient(W, H, (6, 6, 8), (0, 0, 0), 80)
    e = np.minimum(np.minimum(x, 1 - x) * W, np.minimum(y, 1 - y) * H)
    rim = np.exp(-((e - 3.0) / 1.6) ** 2) * 38
    base += rim[..., None]
    sweep = np.exp(-(((x * 0.6 + y * 0.8) - 0.50) / 0.07) ** 2) * 10
    base += sweep[..., None]
    base += ((rng.random((H, W)) - 0.5) * 2.5)[..., None]
    return to_rgba(base)


def paint_outer_glass(W=512, H=768):
    """Ceramic Shield outer panel with the cover-display cut-out and a punch-hole camera"""
    rng = np.random.default_rng(310)
    x, y = grid(W, H)
    base = np.full((H, W, 3), 4.0, np.float32)
    e = np.minimum(np.minimum(x, 1 - x) * W, np.minimum(y, 1 - y) * H)
    base += (np.exp(-((e - 3.0) / 1.6) ** 2) * 40)[..., None]
    base += (np.exp(-(((x * 0.55 + y * 0.9) - 0.62) / 0.06) ** 2) * 12)[..., None]
    base += ((rng.random((H, W)) - 0.5) * 2.5)[..., None]
    img = to_rgba(base)
    d = ImageDraw.Draw(img)
    cx, cy = W // 2, int(H * 0.034) + 6
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=(2, 2, 4, 255), outline=(30, 32, 40, 255), width=2)
    d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=(18, 22, 54, 255))
    d.ellipse([cx - 2, cy - 3, cx, cy - 1], fill=(120, 130, 220, 255))
    return img


# ------------------------------------------------------------------------------------------------ wallpapers
def finish_wall(rgb, W, H, seed, vignette=0.28, grain=3.0):
    x, y = grid(W, H)
    v = 1 - vignette * ((x - 0.5) ** 2 + (y - 0.5) ** 2) * 2.2
    rgb = rgb * np.clip(v, 0.3, 1)[..., None]
    rgb += ((np.random.default_rng(seed).random((H, W)) - 0.5) * grain)[..., None]
    return to_rgba(rgb)


def wall_aurora(W=1024, H=768, seed=11):
    x, y = grid(W, H)
    n1 = fbm(W, H, seed, 5, 2)
    n2 = fbm(W, H, seed + 1, 4, 3)
    rgb = gradient(W, H, (6, 10, 34), (20, 12, 52), 90).astype(np.float32)
    ribbons = [
        (0.30, 0.10, 3.0, 0.00, 0.060, (40, 220, 200)),
        (0.46, 0.13, 2.2, 1.30, 0.075, (90, 120, 255)),
        (0.60, 0.11, 2.8, 2.20, 0.065, (190, 90, 255)),
        (0.74, 0.09, 3.6, 0.70, 0.055, (255, 110, 170)),
    ]
    acc = np.zeros((H, W, 3), np.float32)
    for (yc, amp, fr, ph, wd, col) in ribbons:
        curve = yc + amp * np.sin(x * fr * math.pi + ph + n1 * 3.0) + 0.05 * (n2 - 0.5)
        d = (y - curve) / wd
        core = np.exp(-d * d)
        halo = np.exp(-np.abs(d) * 0.55) * 0.38
        curtain = np.clip(1 - np.clip((y - curve) / 0.30, 0, 1), 0, 1) * (y > curve)
        inten = core * 1.0 + halo * 0.55 + curtain * 0.35 * np.exp(-np.abs(d) * 0.1)
        acc += inten[..., None] * np.array(col, np.float32)[None, None, :]
    rgb = rgb + acc * 0.62
    # stars
    rng = np.random.default_rng(seed + 9)
    st = (rng.random((H, W)) > 0.9975).astype(np.float32) * (1 - y) ** 1.5
    st = np.asarray(Image.fromarray((st * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7)), np.float32) / 255.0
    rgb += st[..., None] * 380
    img = to_rgba(np.clip(rgb, 0, 255))
    bloom = img.filter(ImageFilter.GaussianBlur(22))
    arr = np.asarray(img, np.float32)
    arr[..., :3] = np.clip(arr[..., :3] + np.asarray(bloom, np.float32)[..., :3] * 0.35, 0, 255)
    return finish_wall(arr[..., :3], W, H, seed + 2)


def wall_ember(W=1024, H=768, seed=21):
    x, y = grid(W, H)
    n = fbm(W, H, seed, 4, 3)
    sky = ramp(y / 0.62, [(0.0, (22, 14, 58)), (0.35, (112, 44, 112)), (0.62, (226, 98, 96)), (0.85, (255, 170, 98)), (1.0, (255, 214, 140))])
    rgb = sky.copy()
    # sun
    sx, sy = 0.68, 0.50
    dd = np.sqrt(((x - sx) * 1.33) ** 2 + (y - sy) ** 2)
    rgb += (np.exp(-(dd / 0.28) ** 2) * 95)[..., None] * np.array([1.0, 0.6, 0.35], np.float32)[None, None, :]
    disk = np.clip((0.085 - dd) / 0.004, 0, 1)
    rgb = rgb * (1 - disk[..., None]) + disk[..., None] * np.array([255, 238, 190], np.float32)[None, None, :]
    layers = [
        (0.58, 0.040, 2.2, 0.2, (140, 66, 118), 0.55),
        (0.66, 0.050, 1.6, 1.0, (112, 48, 104), 0.70),
        (0.76, 0.055, 1.3, 2.1, (80, 34, 84), 0.84),
        (0.88, 0.060, 1.0, 3.3, (44, 20, 60), 1.00),
    ]
    for (yb, amp, fr, ph, col, op) in layers:
        ridge = yb + amp * np.sin(x * fr * math.pi * 2 + ph + n * 2.4) + 0.5 * amp * np.sin(x * fr * math.pi * 5 + ph * 2) * (n - 0.3)
        m = np.clip((y - ridge) / 0.004, 0, 1)
        shade = ramp(np.clip((y - ridge) / 0.22, 0, 1), [(0, np.array(col, np.float32) * 1.25), (1, np.array(col, np.float32) * 0.62)])
        lit = np.exp(-((y - ridge) / 0.006) ** 2) * 0.55
        shade = shade + lit[..., None] * np.array([255, 190, 120], np.float32)[None, None, :] * 0.5
        rgb = rgb * (1 - m[..., None] * op) + shade * (m[..., None] * op)
    # distant glints
    return finish_wall(np.clip(rgb, 0, 255), W, H, seed + 3, 0.30)


def wall_mono(W=1024, H=768, seed=31):
    """liquid-metal ripples radiating from an off-centre drop"""
    x, y = grid(W, H)
    n1 = fbm(W, H, seed, 3, 2)
    r = np.sqrt(((x - 0.64) * 1.333) ** 2 + (y - 0.44) ** 2)
    h = np.sin(r * 46.0 - 1.2 + 1.4 * (n1 - 0.5)) * np.exp(-r * 2.1) + 0.5 * np.sin(r * 17.0 + 2.0) * np.exp(-r * 1.2)
    h = np.asarray(Image.fromarray(np.clip((h * 0.5 + 0.5) * 255, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), np.float32) / 255.0
    gy, gx = np.gradient(h)
    light = np.clip(0.5 + (-gx * 0.8 - gy * 0.6) * 38, 0, 1)
    spec = np.clip(light - 0.72, 0, 1) * 3.4
    v = 0.16 + 0.42 * light + 0.60 * spec + 0.10 * (1 - r)
    rgb = np.dstack([v * 226, v * 234, v * 255]).astype(np.float32)
    return finish_wall(np.clip(rgb, 0, 255), W, H, seed + 4, 0.38, 1.5)


WALLS = {"aurora": wall_aurora, "ember": wall_ember, "mono": wall_mono}


# ------------------------------------------------------------------------------------------------ icons
def icon_canvas(top, bot):
    N = 128 * SS
    arr = np.zeros((N, N, 4), np.float32)
    g = np.linspace(0, 1, N, dtype=np.float32)[:, None]
    for c in range(3):
        arr[:, :, c] = top[c] * (1 - g) + bot[c] * g
    arr[:, :, 3] = 255
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def icon_finish(img):
    N = img.size[0]
    d = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dd = ImageDraw.Draw(d)
    dd.ellipse([-N * 0.15, -N * 0.62, N * 1.15, N * 0.40], fill=(255, 255, 255, 34))
    img.alpha_composite(d.filter(ImageFilter.GaussianBlur(N * 0.01)))
    stroke_rr(img, (0, 0, N, N), N * 0.225, N * 0.012, (255, 255, 255, 46), ss=1)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], radius=int(N * 0.225), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out.resize((128, 128), Image.LANCZOS)


def S_(v):
    return v * SS


def W_(v):
    return max(1, int(round(v * SS)))


def icon_clock():
    img = icon_canvas((26, 30, 46), (8, 10, 18))
    d = ImageDraw.Draw(img)
    cx = cy = S_(64)
    R = S_(50)
    for i in range(18):
        t = i / 17.0
        c = int(250 - 36 * t)
        d.ellipse([cx - R + i * 0.5, cy - R + i * 0.5, cx + R - i * 0.5, cy + R - i * 0.5], fill=(c, c, int(c * 0.99), 255))
    for k in range(60):
        a = math.radians(k * 6 - 90)
        major = k % 5 == 0
        r0 = R * (0.80 if major else 0.88)
        r1 = R * 0.94
        d.line([cx + math.cos(a) * r0, cy + math.sin(a) * r0, cx + math.cos(a) * r1, cy + math.sin(a) * r1],
               fill=(40, 40, 46, 255), width=int(S_(2.2 if major else 1.0)))
    for ang, ln, w, col in ((math.radians(-60), 0.52, 4.6, (22, 22, 26, 255)), (math.radians(60), 0.74, 3.6, (22, 22, 26, 255)),
                            (math.radians(165), 0.82, 1.6, (255, 120, 20, 255))):
        d.line([cx - math.cos(ang) * R * 0.14 * (1 if col[0] > 200 else 0), cy - math.sin(ang) * R * 0.14 * (1 if col[0] > 200 else 0),
                cx + math.cos(ang) * R * ln, cy + math.sin(ang) * R * ln], fill=col, width=int(S_(w)))
    d.ellipse([cx - S_(4.5), cy - S_(4.5), cx + S_(4.5), cy + S_(4.5)], fill=(255, 120, 20, 255))
    d.ellipse([cx - S_(1.8), cy - S_(1.8), cx + S_(1.8), cy + S_(1.8)], fill=(30, 30, 34, 255))
    return icon_finish(img)


def icon_calc():
    img = icon_canvas((74, 76, 84), (30, 31, 36))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([S_(16), S_(14), S_(112), S_(40)], radius=S_(7), fill=(18, 20, 24, 255))
    d.text((S_(104), S_(27)), "128", font=font("Inter-Medium", S_(16)), fill=(240, 244, 255, 255), anchor="rm")
    cols = [(166, 168, 176), (166, 168, 176), (166, 168, 176), (255, 150, 20)]
    for r in range(4):
        for c in range(4):
            x0 = S_(16) + c * S_(25)
            y0 = S_(48) + r * S_(17.5)
            if c == 3:
                fillc = cols[3]
            elif r == 0:
                fillc = cols[0]
            else:
                fillc = (70, 72, 80)
            d.rounded_rectangle([x0, y0, x0 + S_(21), y0 + S_(14)], radius=S_(5), fill=fillc + (255,))
    return icon_finish(img)


def icon_notes():
    img = icon_canvas((252, 252, 250), (232, 232, 228))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, S_(128), S_(38)], fill=(255, 205, 20, 255))
    for i, y in enumerate((60, 78, 96, 114)):
        d.line([S_(16), S_(y), S_(112), S_(y)], fill=(200, 204, 214, 255), width=W_(1.4))
    d.line([S_(16), S_(18), S_(70), S_(18)], fill=(120, 80, 0, 255), width=W_(4.2))
    d.line([S_(16), S_(30), S_(46), S_(30)], fill=(160, 112, 0, 255), width=W_(2.6))
    d.line([S_(16), S_(56), S_(86), S_(56)], fill=(70, 74, 86, 255), width=W_(3))
    d.line([S_(16), S_(74), S_(66), S_(74)], fill=(120, 126, 140, 255), width=W_(3))
    return icon_finish(img)


def icon_camera():
    img = icon_canvas((112, 118, 130), (36, 38, 46))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([S_(12), S_(34), S_(116), S_(104)], radius=S_(14), fill=(26, 28, 34, 255), outline=(150, 156, 170, 255), width=W_(1.4))
    d.rounded_rectangle([S_(40), S_(26), S_(64), S_(40)], radius=S_(5), fill=(26, 28, 34, 255), outline=(150, 156, 170, 255), width=W_(1.2))
    cx, cy = S_(64), S_(70)
    for i in range(22):
        t = i / 21.0
        r = S_(30) * (1 - t)
        c = (int(40 + 30 * t), int(44 + 36 * t), int(70 + 70 * t))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c + (255,))
    for r, col in ((S_(30), (210, 214, 224)), (S_(24), (10, 12, 16)), (S_(14), (28, 32, 80)), (S_(7), (90, 70, 200))):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col + (255,), width=W_(1.3))
    d.ellipse([cx - S_(7), cy - S_(14), cx - S_(1), cy - S_(8)], fill=(255, 255, 255, 200))
    d.ellipse([S_(94), S_(42), S_(104), S_(52)], fill=(255, 190, 40, 255))
    return icon_finish(img)


def icon_maps():
    img = icon_canvas((238, 234, 222), (224, 220, 208))
    d = ImageDraw.Draw(img)
    d.polygon([(0, S_(78)), (S_(52), S_(60)), (S_(128), S_(96)), (S_(128), S_(128)), (0, S_(128))], fill=(120, 180, 240, 255))
    d.ellipse([S_(60), S_(-20), S_(150), S_(46)], fill=(150, 210, 140, 255))
    d.ellipse([S_(-20), S_(8), S_(46), S_(52)], fill=(166, 218, 150, 255))
    d.line([S_(-4), S_(52), S_(60), S_(40), S_(132), S_(70)], fill=(255, 255, 255, 255), width=W_(8))
    d.line([S_(36), S_(-4), S_(52), S_(60), S_(72), S_(132)], fill=(255, 226, 120, 255), width=W_(7))
    d.line([S_(-4), S_(52), S_(60), S_(40), S_(132), S_(70)], fill=(250, 250, 250, 255), width=W_(5))
    d.line([S_(20), S_(112), S_(52), S_(82), S_(84), S_(66), S_(104), S_(36)], fill=(40, 120, 255, 255), width=W_(5))
    px, py = S_(104), S_(34)
    d.ellipse([px - S_(12), py - S_(24), px + S_(12), py], fill=(240, 56, 56, 255))
    d.polygon([(px - S_(9), py - S_(8)), (px + S_(9), py - S_(8)), (px, py + S_(10))], fill=(240, 56, 56, 255))
    d.ellipse([px - S_(4.5), py - S_(16.5), px + S_(4.5), py - S_(7.5)], fill=(255, 255, 255, 255))
    return icon_finish(img)


def icon_music():
    img = icon_canvas((255, 100, 128), (214, 24, 66))
    d = ImageDraw.Draw(img)
    d.ellipse([S_(34), S_(76), S_(66), S_(100)], fill=(255, 255, 255, 255))
    d.ellipse([S_(70), S_(66), S_(102), S_(90)], fill=(255, 255, 255, 255))
    d.rectangle([S_(61), S_(30), S_(67), S_(88)], fill=(255, 255, 255, 255))
    d.rectangle([S_(97), S_(22), S_(103), S_(78)], fill=(255, 255, 255, 255))
    d.polygon([(S_(61), S_(30)), (S_(103), S_(18)), (S_(103), S_(36)), (S_(61), S_(48))], fill=(255, 255, 255, 255))
    return icon_finish(img)


def icon_weather():
    img = icon_canvas((92, 176, 255), (28, 104, 226))
    d = ImageDraw.Draw(img)
    sx, sy = S_(48), S_(50)
    for k in range(12):
        a = math.radians(k * 30)
        d.line([sx + math.cos(a) * S_(26), sy + math.sin(a) * S_(26), sx + math.cos(a) * S_(36), sy + math.sin(a) * S_(36)],
               fill=(255, 226, 90, 255), width=W_(3.4))
    for i in range(14):
        t = i / 13.0
        r = S_(21) * (1 - 0.35 * t)
        d.ellipse([sx - r, sy - r, sx + r, sy + r], fill=(255, int(236 - 30 * t), int(110 - 40 * t), 255))
    cloud = Image.new("RGBA", img.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(cloud)
    for (x0, y0, x1, y1) in ((S_(28), S_(80), S_(66), S_(112)), (S_(48), S_(64), S_(92), S_(112)), (S_(74), S_(76), S_(112), S_(112)),
                             (S_(36), S_(90), S_(104), S_(112))):
        cd.ellipse([x0, y0, x1, y1], fill=(255, 255, 255, 255))
    cd.rounded_rectangle([S_(36), S_(92), S_(104), S_(112)], radius=S_(10), fill=(255, 255, 255, 255))
    sh = cloud.filter(ImageFilter.GaussianBlur(S_(3)))
    sh2 = Image.new("RGBA", img.size, (14, 50, 120, 0))
    arr = np.asarray(sh, np.float32).copy()
    arr[..., :3] = (14, 50, 120)
    arr[..., 3] *= 0.45
    img.alpha_composite(Image.fromarray(arr.astype(np.uint8), "RGBA").transform(img.size, Image.AFFINE, (1, 0, 0, 0, 1, -S_(3))))
    img.alpha_composite(cloud)
    return icon_finish(img)


def icon_settings():
    img = icon_canvas((168, 172, 182), (96, 100, 112))
    d = ImageDraw.Draw(img)
    cx = cy = S_(64)
    for k in range(8):
        a = math.radians(k * 45)
        px, py = cx + math.cos(a) * S_(38), cy + math.sin(a) * S_(38)
        pts = []
        for da, rr in ((-13, 0), (13, 0)):
            pass
        ca, sa = math.cos(a), math.sin(a)
        pw, ph = S_(9.5), S_(10)
        d.polygon([(px - sa * pw + ca * ph, py + ca * pw + sa * ph), (px + sa * pw + ca * ph, py - ca * pw + sa * ph),
                   (px + sa * pw * 0.8 - ca * ph, py - ca * pw * 0.8 - sa * ph), (px - sa * pw * 0.8 - ca * ph, py + ca * pw * 0.8 - sa * ph)],
                  fill=(246, 247, 250, 255))
    for i in range(14):
        t = i / 13.0
        r = S_(33) * (1 - 0.02 * t)
        c = int(252 - 40 * t)
        d.ellipse([cx - r + i * 0.4, cy - r + i * 0.4, cx + r - i * 0.4, cy + r - i * 0.4], fill=(c, c, min(255, c + 3), 255))
    d.ellipse([cx - S_(15), cy - S_(15), cx + S_(15), cy + S_(15)], fill=(110, 114, 126, 255))
    d.ellipse([cx - S_(11), cy - S_(11), cx + S_(11), cy + S_(11)], fill=(150, 154, 166, 255))
    return icon_finish(img)


def icon_photos():
    img = icon_canvas((255, 255, 255), (236, 238, 244))
    cols = [(255, 72, 72), (255, 150, 40), (255, 214, 40), (110, 214, 80), (40, 200, 190), (56, 140, 255), (150, 90, 255), (255, 80, 170)]
    cx = cy = S_(64)
    for k, col in enumerate(cols):
        petal = Image.new("RGBA", img.size, (0, 0, 0, 0))
        pd = ImageDraw.Draw(petal)
        a = math.radians(k * 45 - 90)
        px, py = cx + math.cos(a) * S_(21), cy + math.sin(a) * S_(21)
        pd.ellipse([px - S_(20), py - S_(20), px + S_(20), py + S_(20)], fill=col + (150,))
        img.alpha_composite(petal)
    return icon_finish(img)


ICONS = {
    "clock": icon_clock, "calc": icon_calc, "notes": icon_notes, "camera": icon_camera, "maps": icon_maps,
    "music": icon_music, "weather": icon_weather, "settings": icon_settings, "photos": icon_photos,
}
ICON_LABELS = {"clock": "Clock", "calc": "Calculator", "notes": "Notes", "camera": "Camera", "maps": "Maps",
               "music": "Music", "weather": "Weather", "settings": "Settings", "photos": "Photos"}


def paint_logo(N=256):
    S = 4
    M = N * S
    img = Image.new("RGBA", (M, M), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    R = M * 0.27
    off = R * 0.58
    cx, cy = M / 2, M / 2
    lens = Image.new("L", (M, M), 0)
    ld = ImageDraw.Draw(lens)
    a = Image.new("L", (M, M), 0)
    b = Image.new("L", (M, M), 0)
    ImageDraw.Draw(a).ellipse([cx - off - R, cy - R, cx - off + R, cy + R], fill=255)
    ImageDraw.Draw(b).ellipse([cx + off - R, cy - R, cx + off + R, cy + R], fill=255)
    inter = Image.fromarray(np.minimum(np.asarray(a), np.asarray(b)))
    base = Image.new("RGBA", (M, M), (255, 255, 255, 0))
    arr = np.zeros((M, M, 4), np.uint8)
    arr[..., :3] = 255
    arr[..., 3] = (np.asarray(inter, np.float32) * 0.9).astype(np.uint8)
    img.alpha_composite(Image.fromarray(arr, "RGBA"))
    w = int(M * 0.026)
    d.ellipse([cx - off - R, cy - R, cx - off + R, cy + R], outline=(255, 255, 255, 255), width=w)
    d.ellipse([cx + off - R, cy - R, cx + off + R, cy + R], outline=(214, 232, 255, 255), width=w)
    return img.resize((N, N), Image.LANCZOS)


def paint_touchid(N=128):
    S = 4
    M = N * S
    img = Image.new("RGBA", (M, M), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = M / 2, M * 0.52
    w = int(M * 0.032)
    arcs = [(0.14, 200, 340), (0.14, 20, 160), (0.23, 205, 335), (0.23, 25, 150), (0.32, 210, 330), (0.32, 40, 140),
            (0.41, 215, 325), (0.41, 55, 125), (0.05, 0, 360)]
    for (rr, a0, a1) in arcs:
        R = M * rr
        d.arc([cx - R, cy - R * 1.15, cx + R, cy + R * 1.15], a0, a1, fill=(255, 255, 255, 255), width=w)
    d.line([cx, cy - M * 0.22, cx, cy + M * 0.25], fill=(255, 255, 255, 255), width=w)
    for off in (-0.095, 0.095):
        d.line([cx + M * off, cy - M * 0.14, cx + M * off, cy + M * 0.18], fill=(255, 255, 255, 255), width=w)
    return img.resize((N, N), Image.LANCZOS)


# ------------------------------------------------------------------------------------------------ pane layouts (logical 240x360)
LW, LH = 240.0, 360.0
ICON_SZ = 46.0
GRID_X0 = 14.0
GRID_GAP = (LW - 2 * GRID_X0 - 4 * ICON_SZ) / 3.0
GRID_Y0 = 130.0
GRID_ROW = 72.0
DOCK_Y = 360.0 - 12.0 - 66.0
HOME_GRID = ["clock", "calc", "notes", "maps", "weather"]
HOME_DOCK = ["photos", "camera", "music", "settings"]


def icon_pos(slot):
    r, c = divmod(slot, 4)
    return GRID_X0 + c * (ICON_SZ + GRID_GAP), GRID_Y0 + r * GRID_ROW


def dock_pos(slot):
    return 14.0 + 10.0 + slot * ((LW - 28.0 - 20.0 - ICON_SZ) / 3.0), DOCK_Y + 10.0


def status_icons(img, s, white=(255, 255, 255, 235)):
    d = ImageDraw.Draw(img)
    W = img.size[0]
    # signal bars
    x0 = W - 78 * s
    for i in range(4):
        h = (3 + i * 2.2) * s
        d.rounded_rectangle([x0 + i * 4.6 * s, 14 * s - h, x0 + i * 4.6 * s + 3.0 * s, 14 * s], radius=0.8 * s, fill=white)
    # wifi
    cx, cy = W - 54 * s, 14 * s
    for r, w in ((9.0, 1.9), (5.8, 1.9), (2.6, 1.9)):
        d.arc([cx - r * s, cy - r * s - 1.5 * s, cx + r * s, cy + r * s - 1.5 * s], 225, 315, fill=white, width=max(1, int(w * s)))
    d.ellipse([cx - 1.2 * s, cy - 1.4 * s, cx + 1.2 * s, cy + 1.0 * s], fill=white)
    # battery
    bx = W - 40 * s
    d.rounded_rectangle([bx, 6.4 * s, bx + 22 * s, 15.0 * s], radius=3 * s, outline=(255, 255, 255, 150), width=max(1, int(1 * s)))
    d.rounded_rectangle([bx + 1.8 * s, 8.2 * s, bx + 17.5 * s, 13.2 * s], radius=1.8 * s, fill=white)
    d.rounded_rectangle([bx + 23 * s, 9.2 * s, bx + 24.6 * s, 12.2 * s], radius=0.7 * s, fill=(255, 255, 255, 150))


def crop_wall(wall, w, h, shift=0.0):
    ww, wh = wall.size
    target = w / float(h)
    if ww / float(wh) > target:
        cw = int(wh * target)
        x0 = int((ww - cw) / 2 + shift * (ww - cw) / 2)
        box = (x0, 0, x0 + cw, wh)
    else:
        ch = int(ww / target)
        box = (0, (wh - ch) // 2, ww, (wh - ch) // 2 + ch)
    return wall.crop(box).resize((w, h), Image.LANCZOS)


def pane_home(W, H, wall, icons, fonts_scale=None, shift=0.0):
    s = W / LW
    img = crop_wall(wall, W, H, shift).convert("RGBA")
    # soft scrim so white labels stay readable
    scrim = np.zeros((H, W, 4), np.float32)
    scrim[..., 3] = np.linspace(0, 70, H)[:, None]
    img.alpha_composite(Image.fromarray(scrim.astype(np.uint8), "RGBA"))
    status_icons(img, s)
    # widget card
    box = (14 * s, 34 * s, 226 * s, 112 * s)
    fill_rr(img, box, 20 * s, (255, 255, 255, 72), (255, 255, 255, 34))
    stroke_rr(img, box, 20 * s, max(1.0, 1.0 * s), (255, 255, 255, 70))
    # little static sun + cloud emblem right inside the card
    d = ImageDraw.Draw(img)
    ex, ey = 190 * s, 60 * s
    for k in range(10):
        a = math.radians(k * 36)
        d.line([ex + math.cos(a) * 10 * s, ey + math.sin(a) * 10 * s, ex + math.cos(a) * 15 * s, ey + math.sin(a) * 15 * s], fill=(255, 226, 120, 230), width=max(1, int(1.6 * s)))
    d.ellipse([ex - 8 * s, ey - 8 * s, ex + 8 * s, ey + 8 * s], fill=(255, 218, 96, 255))
    fill_rr(img, (ex - 14 * s, ey + 2 * s, ex + 16 * s, ey + 16 * s), 7 * s, (255, 255, 255, 235))
    fill_rr(img, (ex - 6 * s, ey - 4 * s, ex + 8 * s, ey + 10 * s), 7 * s, (255, 255, 255, 240))
    lab = font("Inter-Medium", 9.2 * s)
    for slot, name in enumerate(HOME_GRID):
        x, y = icon_pos(slot)
        ic = icons[name].resize((int(ICON_SZ * s), int(ICON_SZ * s)), Image.LANCZOS)
        sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle([x * s, (y + 3) * s, (x + ICON_SZ) * s, (y + ICON_SZ + 3) * s], radius=ICON_SZ * 0.225 * s, fill=(0, 0, 0, 90))
        img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(3 * s)))
        img.alpha_composite(ic, (int(x * s), int(y * s)))
        txt(img, ((x + ICON_SZ / 2) * s, (y + ICON_SZ + 9) * s), ICON_LABELS[name], lab, (255, 255, 255, 255), "mm", shadow=(0, 1, (0, 0, 0, 150)))
    # dock
    dbox = (10 * s, DOCK_Y * s, (LW - 10) * s, (DOCK_Y + 66) * s)
    fill_rr(img, dbox, 22 * s, (255, 255, 255, 70), (255, 255, 255, 40))
    stroke_rr(img, dbox, 22 * s, max(1.0, 1.0 * s), (255, 255, 255, 60))
    for slot, name in enumerate(HOME_DOCK):
        x, y = dock_pos(slot)
        ic = icons[name].resize((int(ICON_SZ * s), int(ICON_SZ * s)), Image.LANCZOS)
        img.alpha_composite(ic, (int(x * s), int(y * s)))
    # page dots + home indicator
    d = ImageDraw.Draw(img)
    for i in range(3):
        cx = (LW / 2 + (i - 1) * 9) * s
        d.ellipse([cx - 2.0 * s, (DOCK_Y - 9) * s - 2.0 * s, cx + 2.0 * s, (DOCK_Y - 9) * s + 2.0 * s], fill=(255, 255, 255, 235 if i == 0 else 110))
    fill_rr(img, (88 * s, 351 * s, 152 * s, 354.4 * s), 1.7 * s, (255, 255, 255, 235))
    return img


def pane_lock(W, H, wall, shift=0.0):
    s = W / LW
    img = crop_wall(wall, W, H, shift).convert("RGBA")
    scrim = np.zeros((H, W, 4), np.float32)
    scrim[..., 3] = 40 + 30 * np.abs(np.linspace(-1, 1, H))[:, None]
    img.alpha_composite(Image.fromarray(scrim.astype(np.uint8), "RGBA"))
    status_icons(img, s)
    d = ImageDraw.Draw(img)
    # padlock glyph
    cx, cy = LW / 2 * s, 46 * s
    d.arc([cx - 6 * s, cy - 13 * s, cx + 6 * s, cy + 3 * s], 180, 360, fill=(255, 255, 255, 235), width=max(1, int(2 * s)))
    fill_rr(img, (cx - 9 * s, cy - 3 * s, cx + 9 * s, cy + 11 * s), 3.4 * s, (255, 255, 255, 235))
    d.ellipse([cx - 1.6 * s, cy + 1.6 * s, cx + 1.6 * s, cy + 4.8 * s], fill=(40, 44, 60, 255))
    # bottom shortcut buttons
    for bx in (34, 206):
        cxx, cyy = bx * s, 318 * s
        r = 17 * s
        fill_rr(img, (cxx - r, cyy - r, cxx + r, cyy + r), r, (20, 22, 30, 110), (30, 32, 40, 150))
        stroke_rr(img, (cxx - r, cyy - r, cxx + r, cyy + r), r, max(1.0, 0.9 * s), (255, 255, 255, 50))
    # torch + camera glyphs
    tx, ty = 34 * s, 318 * s
    d.polygon([(tx - 6 * s, ty - 8 * s), (tx + 6 * s, ty - 8 * s), (tx + 3.5 * s, ty - 2 * s), (tx - 3.5 * s, ty - 2 * s)], fill=(255, 255, 255, 240))
    fill_rr(img, (tx - 3.5 * s, ty - 2 * s, tx + 3.5 * s, ty + 9 * s), 2 * s, (255, 255, 255, 240))
    kx, ky = 206 * s, 318 * s
    fill_rr(img, (kx - 9 * s, ky - 5.5 * s, kx + 9 * s, ky + 7 * s), 3 * s, (255, 255, 255, 240))
    fill_rr(img, (kx - 3.5 * s, ky - 8 * s, kx + 3.5 * s, ky - 4 * s), 1.6 * s, (255, 255, 255, 240))
    d.ellipse([kx - 4.2 * s, ky - 3.2 * s, kx + 4.2 * s, ky + 5.2 * s], fill=(30, 34, 50, 255))
    d.ellipse([kx - 2.4 * s, ky - 1.4 * s, kx + 2.4 * s, ky + 3.4 * s], fill=(110, 120, 220, 255))
    # hint pill
    hint = font("Inter-Medium", 8.4 * s)
    fill_rr(img, (62 * s, 300 * s, 178 * s, 322 * s), 11 * s, (255, 255, 255, 60), (255, 255, 255, 36))
    txt(img, (LW / 2 * s, 311 * s), "Touch ID to unlock", hint, (255, 255, 255, 235), "mm")
    fill_rr(img, (88 * s, 351 * s, 152 * s, 354.4 * s), 1.7 * s, (255, 255, 255, 235))
    return img


def paint_album(kind, N):
    """generative cover art for the two boss themes (original artwork)"""
    x, y = grid(N, N)
    rr = np.sqrt((x - 0.5) ** 2 + (y - 0.5) ** 2)
    n = fbm(N, N, 70 + kind, 4, 3)
    if kind == 1:
        art = ramp(np.clip(0.55 * rr * 2 + 0.35 * n + 0.1 * np.sin(rr * 40), 0, 1),
                   [(0, (255, 226, 150)), (0.25, (255, 120, 80)), (0.5, (190, 40, 120)), (0.75, (80, 20, 120)), (1, (14, 8, 40))])
    else:
        art = ramp(np.clip(0.9 * rr * 1.6 + 0.30 * n, 0, 1),
                   [(0, (255, 248, 214)), (0.18, (255, 206, 96)), (0.42, (120, 70, 190)), (0.72, (28, 22, 88)), (1, (6, 6, 28))])
        beams = np.zeros_like(rr)
        ang = np.arctan2(y - 0.5, x - 0.5)
        beams = np.clip(np.sin(ang * 12.0) * 0.5 + 0.5, 0, 1) ** 6 * np.exp(-rr * 3.0)
        art = art + beams[..., None] * np.array([255, 220, 150], np.float32) * 0.55
    img = to_rgba(np.clip(art, 0, 255))
    d = ImageDraw.Draw(img)
    c = N / 2.0
    s_ = N / 184.0
    for k, rad in enumerate((0.40, 0.30, 0.20)):
        d.ellipse([c - c * rad * 2, c - c * rad * 2, c + c * rad * 2, c + c * rad * 2], outline=(255, 255, 255, 70 - 14 * k), width=max(1, int(1.4 * s_)))
    if kind == 1:
        off = c * 0.12
        d.ellipse([c - off * 2.6 - c * 0.22, c - c * 0.22, c - off * 2.6 + c * 0.22, c + c * 0.22], outline=(255, 255, 255, 230), width=max(2, int(3 * s_)))
        d.ellipse([c + off * 2.6 - c * 0.22, c - c * 0.22, c + off * 2.6 + c * 0.22, c + c * 0.22], outline=(255, 255, 255, 230), width=max(2, int(3 * s_)))
    else:
        d.text((c, c * 1.02), "\u03a9", font=font("Inter-Light", int(N * 0.52)), fill=(255, 250, 230, 245), anchor="mm")
    return img


def pane_music(W, H):
    s = W / LW
    x, y = grid(W, H)
    rgb = gradient(W, H, (54, 12, 44), (14, 8, 24), 90)
    glow = np.exp(-(((x - 0.5) * 1.0) ** 2 + ((y - 0.36)) ** 2) / 0.07)
    rgb += glow[..., None] * np.array([150, 40, 110], np.float32)
    img = to_rgba(np.clip(rgb, 0, 255))
    status_icons(img, s)
    d = ImageDraw.Draw(img)
    # album art
    ax0, ay0, asz = 28.0, 56.0, 184.0
    art_img = paint_album(1, int(asz * s))
    m = rr_mask(art_img.size[0], art_img.size[1], (0, 0, art_img.size[0], art_img.size[1]), 14 * s)
    art_img.putalpha(m)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([ax0 * s, (ay0 + 8) * s, (ax0 + asz) * s, (ay0 + asz + 8) * s], radius=14 * s, fill=(0, 0, 0, 140))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10 * s)))
    img.alpha_composite(art_img, (int(ax0 * s), int(ay0 * s)))
    # text
    txt(img, (28 * s, 262 * s), "Cipher Protocol", font("Inter-SemiBold", 14.5 * s), (255, 255, 255, 255), "lm")
    txt(img, (28 * s, 279 * s), "Dark Web Duo  -  Boss Theme", font("Inter-Medium", 9.4 * s), (255, 255, 255, 150), "lm")
    # progress
    fill_rr(img, (28 * s, 296 * s, 212 * s, 299.4 * s), 1.7 * s, (255, 255, 255, 60))
    fill_rr(img, (28 * s, 296 * s, 28 * s + 184 * s * 0.38, 299.4 * s), 1.7 * s, (255, 255, 255, 240))
    d = ImageDraw.Draw(img)
    d.ellipse([28 * s + 184 * s * 0.38 - 3.4 * s, 297.7 * s - 3.4 * s, 28 * s + 184 * s * 0.38 + 3.4 * s, 297.7 * s + 3.4 * s], fill=(255, 255, 255, 255))
    f = font("Inter-Medium", 7.6 * s)
    txt(img, (28 * s, 307 * s), "0:38", f, (255, 255, 255, 140), "lm")
    txt(img, (212 * s, 307 * s), "-1:42", f, (255, 255, 255, 140), "rm")
    # transport controls
    mx, my = LW / 2 * s, 331 * s
    d.rectangle([mx - 7 * s, my - 10 * s, mx - 2.4 * s, my + 10 * s], fill=(255, 255, 255, 255))
    d.rectangle([mx + 2.4 * s, my - 10 * s, mx + 7 * s, my + 10 * s], fill=(255, 255, 255, 255))
    for sx in (-1, 1):
        px = mx + sx * 46 * s
        tri = [(px - sx * (-8) * s, my), (px + sx * 3 * s, my - 9 * s), (px + sx * 3 * s, my + 9 * s)]
        d.polygon([(px + sx * 9 * s, my), (px - sx * 4 * s, my - 9 * s), (px - sx * 4 * s, my + 9 * s)], fill=(255, 255, 255, 235))
        d.polygon([(px + sx * 15 * s, my), (px + sx * 2 * s, my - 9 * s), (px + sx * 2 * s, my + 9 * s)], fill=(255, 255, 255, 235))
    fill_rr(img, (88 * s, 351 * s, 152 * s, 354.4 * s), 1.7 * s, (255, 255, 255, 235))
    return img


def apply_corner_mask(img, r):
    W, H = img.size
    m = np.asarray(rr_mask(W, H, (0, 0, W, H), r), np.float32) / 255.0
    a = np.asarray(img, np.float32).copy()
    a[..., 3] *= m
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def paint_boot(W, H, logo):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    size = int(min(W, H) * (0.42 if W <= H else 0.30))
    lg = logo.resize((size, size), Image.LANCZOS)
    g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g.paste(lg, ((W - size) // 2, (H - size) // 2 - int(H * 0.02)), lg)
    glow = g.filter(ImageFilter.GaussianBlur(size * 0.07))
    img.alpha_composite(glow)
    img.alpha_composite(g)
    txt(img, (W // 2, int(H * 0.5 + size * 0.62)), "iPhone Duo", font("Inter-Light", size * 0.16), (255, 255, 255, 235), "mm")
    return img


def paint_crease(img, x_center, width):
    W, H = img.size
    a = np.asarray(img, np.float32).copy()
    xs = np.arange(W, dtype=np.float32)[None, :]
    k = np.exp(-((xs - x_center) / width) ** 2)
    a[..., :3] *= (1 - 0.30 * k)[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------------------------------------------ java constants
def uvf(name):
    x0, y0, x1, y1 = REG[name]
    e = 0.6
    return "{%.6fF, %.6fF, %.6fF, %.6fF}" % ((x0 + e) / 512.0, (y0 + e) / 512.0, (x1 - e) / 512.0, (y1 - e) / 512.0)


def emit_java():
    lines = ["package com.darkwebduo.client.render;", "", "/** GENERATED by tools/gen_iphone.py - do not edit by hand. Atlas regions {u0, v0, u1, v1} and back-panel layout. */",
             "public final class PhoneUv {", "    private PhoneUv() {}", ""]
    for name in ("FRAME", "BRUSHED", "HINGE_TI", "CARBON", "PORT", "GRILLE", "PLATEAU", "LENS_MAIN", "LENS_WIDE", "FLASH", "MIC", "TOUCHID"):
        lines.append("    public static final float[] %s = %s;" % (name, uvf(name)))
    lines.append("")

    def arr(n, vals):
        lines.append("    public static final float[] %s = {%s};" % (n, ", ".join("%.4fF" % v for v in vals)))

    arr("BACK_PLATEAU", BACK_PLATEAU)
    arr("LENS_MAIN_C", LENS_MAIN_C)
    arr("LENS_WIDE_C", LENS_WIDE_C)
    arr("FLASH_C", FLASH_C)
    arr("MIC_C", MIC_C)
    lines.append("")
    lines.append("    /** logical 240x360 pane layout shared with the phone UI */")
    lines.append("    public static final float PANE_W = %.1fF;" % LW)
    lines.append("    public static final float PANE_H = %.1fF;" % LH)
    lines.append("    public static final float ICON_SIZE = %.1fF;" % ICON_SZ)
    lines.append("    public static final float GRID_X0 = %.3fF;" % GRID_X0)
    lines.append("    public static final float GRID_GAP = %.3fF;" % GRID_GAP)
    lines.append("    public static final float GRID_Y0 = %.1fF;" % GRID_Y0)
    lines.append("    public static final float GRID_ROW = %.1fF;" % GRID_ROW)
    lines.append("    public static final float DOCK_Y = %.1fF;" % DOCK_Y)
    lines.append("}")
    JAVA.write_text("\n".join(lines) + "\n")


LANG_ENTRIES = {
    "block.darkweb_duo.iphone_duo": "iPhone Duo",
    "item.darkweb_duo.iphone_duo": "iPhone Duo",
    "tooltip.darkweb_duo.iphone_duo.1": "Apple's first foldable - 5.4\" outer, 7.6\" inner display",
    "tooltip.darkweb_duo.iphone_duo.2": "Titanium frame - Ceramic Shield - Touch ID",
    "tooltip.darkweb_duo.iphone_duo.3": "Right-click to use. Sneak + right-click to fold or unfold.",
    "tooltip.darkweb_duo.iphone_duo.color.0": "Star White",
    "tooltip.darkweb_duo.iphone_duo.color.1": "Night Sky",
    "message.darkweb_duo.phone_unfolded": "iPhone Duo unfolded",
    "message.darkweb_duo.phone_folded": "iPhone Duo folded",
    "message.darkweb_duo.phone_busy": "The iPhone Duo is still arriving...",
    "phone.darkweb_duo.app.clock": "Clock",
    "phone.darkweb_duo.app.calc": "Calculator",
    "phone.darkweb_duo.app.notes": "Notes",
    "phone.darkweb_duo.app.camera": "Camera",
    "phone.darkweb_duo.app.maps": "Maps",
    "phone.darkweb_duo.app.music": "Music",
    "phone.darkweb_duo.app.weather": "Weather",
    "phone.darkweb_duo.app.settings": "Settings",
    "phone.darkweb_duo.app.photos": "Photos",
    "phone.darkweb_duo.screen": "iPhone Duo",
    "phone.darkweb_duo.track.1": "Cipher Protocol",
    "phone.darkweb_duo.track.2": "Omega Ascension",
    "phone.darkweb_duo.touchid": "Touch ID to unlock",
    "phone.darkweb_duo.shot": "Photo saved",
    "key.categories.darkweb_duo": "Dark Web & iPhone Duo",
}


def main():
    ENT.mkdir(parents=True, exist_ok=True)
    GUI.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)

    # --- icons, logo, fingerprint, wallpapers
    icons = {}
    for name, fn in ICONS.items():
        icons[name] = fn()
        save(icons[name], GUI / ("icon_%s.png" % name))
    logo = paint_logo(256)
    save(logo, GUI / "logo_duo.png")
    save(paint_touchid(128), GUI / "touchid.png")
    walls = {}
    for name, fn in WALLS.items():
        walls[name] = fn()
        save(walls[name].convert("RGB").resize((512, 384), Image.LANCZOS), GUI / ("wall_%s.png" % name))

    # --- 3D atlases
    for cw in COLORWAYS:
        atlas = build_atlas(cw)
        save(atlas, ENT / ("atlas_%s.png" % cw))
        save(paint_back(cw), ENT / ("back_%s.png" % cw))
    save(paint_inner_glass(0), ENT / "inner_left.png")
    save(paint_inner_glass(1), ENT / "inner_right.png")
    save(paint_outer_glass(), ENT / "outer_glass.png")

    # --- 3D screens (rounded outer corners, hinge crease on the inner one)
    wall = walls["aurora"]
    PW, PH = 512, 768
    lock = apply_corner_mask(pane_lock(PW, PH, wall), 22 * PW / LW)
    save(lock, ENT / "screen_outer_lock.png")
    left = pane_home(PW, PH, wall, icons, shift=-0.5)
    right = pane_music(PW, PH)
    inner = Image.new("RGBA", (PW * 2, PH), (0, 0, 0, 255))
    inner.paste(left, (0, 0))
    inner.paste(right, (PW, 0))
    inner = paint_crease(inner, PW, 4.0)
    inner = apply_corner_mask(inner, 22 * PW / LW)
    save(inner, ENT / "screen_inner_home.png")
    save(apply_corner_mask(paint_boot(PW, PH, logo), 22 * PW / LW), ENT / "screen_boot_outer.png")
    save(apply_corner_mask(paint_boot(PW * 2, PH, logo), 22 * PW / LW), ENT / "screen_boot_inner.png")

    save(paint_album(1, 256), GUI / "album_1.png")
    save(paint_album(2, 256), GUI / "album_2.png")
    for f in GUI.glob("*.png"):
        (GUI / (f.name + ".mcmeta")).write_text('{"texture": {"blur": true}}\n')
    emit_java()
    LANG.write_text(json.dumps(LANG_ENTRIES, indent=2, ensure_ascii=False) + "\n")

    # --- preview sheet
    sheet = Image.new("RGBA", (2200, 1400), (40, 40, 46, 255))
    x = 10
    for nm in ("screen_outer_lock", "screen_inner_home"):
        im = Image.open(ENT / (nm + ".png")).convert("RGBA")
        im.thumbnail((700, 700))
        sheet.alpha_composite(im, (x, 10))
        x += im.size[0] + 10
    for cw in COLORWAYS:
        im = Image.open(ENT / ("back_%s.png" % cw)).convert("RGBA")
        im.thumbnail((460, 700))
        sheet.alpha_composite(im, (x, 10))
        x += im.size[0] + 10
    x = 10
    for cw in COLORWAYS:
        im = Image.open(ENT / ("atlas_%s.png" % cw)).convert("RGBA")
        sheet.alpha_composite(im, (x, 730))
        x += 522
    for name in ICONS:
        im = icons[name]
        sheet.alpha_composite(im, (x, 730))
        x += 134
        if x > 2060:
            break
    x = 10
    for nm in walls:
        im = walls[nm].copy()
        im.thumbnail((360, 360))
        sheet.alpha_composite(im, (x, 1260 - 120))
        x += 370
    sheet.convert("RGB").save(PREVIEW / "iphone_sheet.png")
    print("iPhone Duo assets written;", len(list(ENT.glob("*.png"))), "entity textures,", len(list(GUI.glob("*.png"))), "gui textures")


if __name__ == "__main__":
    main()
