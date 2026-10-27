#!/usr/bin/env python3
"""
core_paint - the small supersampled procedural painter used by gen_core_assets.py

* Layer: an RGBA canvas drawn at SSx resolution (anti-aliased shapes/text) and downsampled with LANCZOS
* materials: brushed metal, carbon twill, foam, painted steel; all return float32 HxWx3 arrays in 0..255
* helpers: bevel, glow (bloom), vignette, scratches, composite

Everything is deterministic (seeded numpy), no external assets except an optional TrueType font.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SS = 4

_FONT_CANDIDATES = [
    "/usr/share/fonts/opentype/inter/Inter-Bold.otf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    "/Library/Fonts/Arial Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
]


def get_font(size):
    for p in _FONT_CANDIDATES:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, int(max(4, size)))
            except Exception:
                continue
    try:
        return ImageFont.load_default(int(max(6, size)))
    except TypeError:
        return ImageFont.load_default()


# ------------------------------------------------------------------------------------------------ colour helpers
def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mixc(c0, c1, t):
    return tuple(int(round(c0[i] * (1 - t) + c1[i] * t)) for i in range(min(len(c0), len(c1))))


def scale_c(c, k):
    return tuple(int(max(0, min(255, c[i] * k))) for i in range(3)) + ((c[3],) if len(c) > 3 else ())


# ------------------------------------------------------------------------------------------------ noise
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


def white_noise(W, H, seed):
    return np.random.default_rng(seed).random((H, W)).astype(np.float32)


def grid(W, H):
    x = np.linspace(0, 1, W, dtype=np.float32)[None, :] * np.ones((H, 1), np.float32)
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None] * np.ones((1, W), np.float32)
    return x, y


# ------------------------------------------------------------------------------------------------ materials
def flat(W, H, c):
    out = np.zeros((H, W, 3), np.float32)
    out[..., 0], out[..., 1], out[..., 2] = c[0], c[1], c[2]
    return out


def metal(W, H, seed, base, contrast=0.10, brush=0.55, horizontal=True, grain=0.035):
    """brushed metal: long streaks + low frequency mottling + fine grain"""
    rng = np.random.default_rng(seed)
    low = fbm(W, H, seed + 1, 4, 2)
    if horizontal:
        streak = np.repeat(rng.random((H, 1)).astype(np.float32), W, axis=1)
        streak = (streak + np.asarray(Image.fromarray((streak * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)), np.float32) / 255.0) * 0.5
    else:
        streak = np.repeat(rng.random((1, W)).astype(np.float32), H, axis=0)
        streak = (streak + np.asarray(Image.fromarray((streak * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)), np.float32) / 255.0) * 0.5
    gr = white_noise(W, H, seed + 2) - 0.5
    v = 1.0 + contrast * (low - 0.5) * 2.0 + contrast * brush * (streak - 0.5) * 2.0 + grain * gr * 2.0
    out = np.zeros((H, W, 3), np.float32)
    for k in range(3):
        out[..., k] = base[k] * v
    return np.clip(out, 0, 255)


def carbon(W, H, seed, base=(22, 24, 28), weave=6):
    """2x2 twill carbon weave with a soft sheen"""
    x = np.arange(W)[None, :] * np.ones((H, 1))
    y = np.arange(H)[:, None] * np.ones((1, W))
    cell = weave
    u = (x % cell) / cell
    v = (y % cell) / cell
    a = ((x // cell + y // cell) % 2)
    tw = np.where(a == 0, u, v)
    shade = 0.82 + 0.34 * tw
    hi = np.sin((x + y) / cell * math.pi) * 0.06
    n = white_noise(W, H, seed) * 0.05
    out = np.zeros((H, W, 3), np.float32)
    for k in range(3):
        out[..., k] = base[k] * (shade + hi + n)
    return np.clip(out, 0, 255).astype(np.float32)


def foam(W, H, seed, base=(18, 20, 26)):
    n1 = fbm(W, H, seed, 5, 6)
    n2 = white_noise(W, H, seed + 3)
    v = 0.75 + 0.5 * n1 + 0.12 * (n2 - 0.5)
    out = np.zeros((H, W, 3), np.float32)
    for k in range(3):
        out[..., k] = base[k] * v
    return np.clip(out, 0, 255)


def vgrad(W, H, top, bottom):
    t = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
    return np.clip(np.array(top, np.float32)[None, None, :3] * (1 - t) + np.array(bottom, np.float32)[None, None, :3] * t, 0, 255) * np.ones((1, W, 1), np.float32)


def hgrad(W, H, left, right):
    t = np.linspace(0, 1, W, dtype=np.float32)[None, :, None]
    return np.clip(np.array(left, np.float32)[None, None, :3] * (1 - t) + np.array(right, np.float32)[None, None, :3] * t, 0, 255) * np.ones((H, 1, 1), np.float32)


def diag_grad(W, H, c0, c1, power=1.0):
    x, y = grid(W, H)
    t = np.clip((x + y) / 2.0, 0, 1) ** power
    return np.clip(np.array(c0, np.float32)[None, None, :3] * (1 - t[..., None]) + np.array(c1, np.float32)[None, None, :3] * t[..., None], 0, 255)


# ------------------------------------------------------------------------------------------------ shading
def bevel(arr, w=2, light=0.30, dark=0.38, inset=False):
    """adds a 1..w px highlight on the top/left edges and a shadow on the bottom/right edges (swap with inset)"""
    H, W = arr.shape[:2]
    out = arr.copy()
    for i in range(w):
        k = (1.0 - i / float(w))
        hi = light * k
        lo = dark * k
        if inset:
            hi, lo = lo * 0.8, hi * 1.2
        out[i, i:W - i] = out[i, i:W - i] * (1 - hi) + 255 * hi
        out[i:H - i, i] = out[i:H - i, i] * (1 - hi) + 255 * hi
        out[H - 1 - i, i:W - i] = out[H - 1 - i, i:W - i] * (1 - lo)
        out[i:H - i, W - 1 - i] = out[i:H - i, W - 1 - i] * (1 - lo)
    return np.clip(out, 0, 255)


def vignette(arr, strength=0.35, power=2.2):
    H, W = arr.shape[:2]
    x, y = grid(W, H)
    d = np.sqrt((x - 0.5) ** 2 + (y - 0.5) ** 2) / 0.7071
    v = 1.0 - strength * np.clip(d, 0, 1) ** power
    return np.clip(arr * v[..., None], 0, 255)


def scratches(arr, seed, count=14, strength=0.10):
    H, W = arr.shape[:2]
    rng = np.random.default_rng(seed)
    lay = Image.new("L", (W * 2, H * 2), 0)
    d = ImageDraw.Draw(lay)
    for _ in range(count):
        x0, y0 = rng.random() * W * 2, rng.random() * H * 2
        ang = rng.normal(0, 0.35) + (math.pi * 0.25 if rng.random() < 0.3 else 0)
        ln = (0.15 + rng.random() * 0.4) * W * 2
        d.line([x0, y0, x0 + math.cos(ang) * ln, y0 + math.sin(ang) * ln], fill=int(40 + rng.random() * 120), width=1)
    m = np.asarray(lay.resize((W, H), Image.LANCZOS), np.float32) / 255.0
    return np.clip(arr * (1 - strength * 0.4 * m[..., None]) + 255 * strength * m[..., None] * 0.35, 0, 255)


def to_image(arr, alpha=None):
    H, W = arr.shape[:2]
    a = np.full((H, W), 255.0, np.float32) if alpha is None else alpha
    return Image.fromarray(np.dstack([np.clip(arr, 0, 255), np.clip(a, 0, 255)]).astype(np.uint8), "RGBA")


def composite(base_arr, layer_img):
    """alpha composites an RGBA PIL layer over a float array and returns the float array"""
    base = to_image(base_arr)
    base.alpha_composite(layer_img)
    return np.asarray(base, np.float32)[..., :3]


def add_glow(arr, layer_img, radius, gain=1.0):
    """bloom: blurred copy of the layer is ADDED to the array (emissive details)"""
    g = layer_img.filter(ImageFilter.GaussianBlur(radius))
    a = np.asarray(g, np.float32)
    add = a[..., :3] * (a[..., 3:4] / 255.0) * gain
    return np.clip(arr + add, 0, 255)


# ------------------------------------------------------------------------------------------------ the supersampled layer
class Layer:
    """anti-aliased drawing canvas: coordinates are in *output* pixels (floats allowed)"""

    def __init__(self, W, H, ss=SS):
        self.W, self.H, self.ss = W, H, ss
        self.img = Image.new("RGBA", (W * ss, H * ss), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def _s(self, v):
        return v * self.ss

    def rect(self, x0, y0, x1, y1, fill=None, outline=None, width=1):
        s = self._s
        self.d.rectangle([s(x0), s(y0), s(x1) - 1, s(y1) - 1], fill=fill, outline=outline, width=int(width * self.ss) if outline else 0)

    def rrect(self, x0, y0, x1, y1, r, fill=None, outline=None, width=1):
        s = self._s
        self.d.rounded_rectangle([s(x0), s(y0), s(x1) - 1, s(y1) - 1], s(r), fill=fill, outline=outline, width=int(width * self.ss) if outline else 0)

    def ellipse(self, x0, y0, x1, y1, fill=None, outline=None, width=1):
        s = self._s
        self.d.ellipse([s(x0), s(y0), s(x1) - 1, s(y1) - 1], fill=fill, outline=outline, width=int(width * self.ss) if outline else 0)

    def circle(self, cx, cy, r, fill=None, outline=None, width=1):
        self.ellipse(cx - r, cy - r, cx + r, cy + r, fill, outline, width)

    def line(self, pts, fill, width=1.0):
        s = self._s
        flat_pts = []
        for p in pts:
            flat_pts += [s(p[0]), s(p[1])]
        self.d.line(flat_pts, fill=fill, width=max(1, int(round(width * self.ss))), joint="curve")

    def poly(self, pts, fill=None, outline=None):
        s = self._s
        self.d.polygon([(s(p[0]), s(p[1])) for p in pts], fill=fill, outline=outline)

    def arc(self, box, a0, a1, fill, width=1.0):
        s = self._s
        self.d.arc([s(box[0]), s(box[1]), s(box[2]) - 1, s(box[3]) - 1], a0, a1, fill=fill, width=max(1, int(round(width * self.ss))))

    def text(self, xy, s_, size, fill, anchor="la"):
        self.d.text((self._s(xy[0]), self._s(xy[1])), s_, font=get_font(size * self.ss), fill=fill, anchor=anchor)

    def text_width(self, s_, size):
        f = get_font(size * self.ss)
        try:
            return f.getlength(s_) / self.ss
        except AttributeError:
            return f.getsize(s_)[0] / self.ss

    def done(self):
        return self.img.resize((self.W, self.H), Image.LANCZOS)


def rot_text(W, H, s_, size, cx, cy, fill, ss=SS):
    """RGBA canvas (W x H) with `s_` centred on (cx, cy) but rotated 180 degrees: for top faces that are read from the NORTH
    (vanilla maps the top of an 'up' texture to the north, so it reads upright from the south)"""
    L = Layer(W, H, ss)
    L.text((W - cx, H - cy), s_, size, fill, anchor="mm")
    return L.done().rotate(180)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


# ------------------------------------------------------------------------------------------------ small motifs
def screw(L, cx, cy, r, base=(120, 124, 132, 255), slot=(30, 32, 38, 255)):
    L.circle(cx + 0.25, cy + 0.35, r + 0.3, fill=(0, 0, 0, 110))
    L.circle(cx, cy, r, fill=base)
    L.circle(cx - r * 0.25, cy - r * 0.25, r * 0.55, fill=(255, 255, 255, 70))
    L.line([(cx - r * 0.7, cy - r * 0.2), (cx + r * 0.7, cy + r * 0.2)], slot, max(0.5, r * 0.28))
    L.line([(cx - r * 0.2, cy + r * 0.7), (cx + r * 0.2, cy - r * 0.7)], slot, max(0.5, r * 0.28))


def barcode(L, x0, y0, x1, y1, seed, color=(15, 15, 18, 255)):
    rng = np.random.default_rng(seed)
    x = x0
    while x < x1 - 0.6:
        w = 0.4 + rng.integers(0, 3) * 0.35
        if rng.random() < 0.62:
            L.rect(x, y0, min(x1, x + w), y1, fill=color)
        x += w + 0.25 + rng.integers(0, 2) * 0.2


def circuit(L, W, H, seed, color, count=14, width=0.7, node=1.1):
    """right-angle PCB-style traces with solder nodes"""
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x, y = rng.integers(2, W - 2), rng.integers(2, H - 2)
        pts = [(x, y)]
        horiz = rng.random() < 0.5
        for _s in range(rng.integers(2, 5)):
            ln = rng.integers(4, max(6, W // 3)) * (1 if rng.random() < 0.5 else -1)
            if horiz:
                x = int(np.clip(x + ln, 1, W - 2))
            else:
                y = int(np.clip(y + ln, 1, H - 2))
            pts.append((x, y))
            horiz = not horiz
        L.line(pts, color, width)
        L.circle(pts[0][0], pts[0][1], node, fill=color)
        L.circle(pts[-1][0], pts[-1][1], node * 0.8, fill=color)
