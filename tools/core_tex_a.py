#!/usr/bin/env python3
"""core_tex_a - textures for the giant monitor, keyboard and mouse (all supersampled / procedural)."""
import math

import numpy as np
from PIL import Image, ImageFilter

import core_paint as P
from core_paint import Layer, hexc

CYAN = (25, 230, 255, 255)
MAG = (255, 44, 214, 255)
GOLD = (255, 196, 58, 255)
RED = (255, 40, 62, 255)


def _finish(arr, *layers, glow=None):
    """composite layers over arr; glow = list of (layer_index, radius, gain)"""
    out = arr
    for i, L in enumerate(layers):
        img = L.done() if isinstance(L, Layer) else L
        out = P.composite(out, img)
        if glow:
            for gi, rad, gain in glow:
                if gi == i:
                    out = P.add_glow(out, img, rad, gain)
    return P.to_image(out)


# ---------------------------------------------------------------------------------------------------- monitor
def monitor_bezel():
    W = 64
    a = P.metal(W, W, 11, (44, 47, 56), contrast=0.07, brush=0.5, grain=0.03)
    a = P.bevel(a, 2, 0.22, 0.34)
    L = Layer(W, W)
    L.rect(2, 2, W - 2, 3, fill=(0, 0, 0, 90))
    L.rect(2, W - 3, W - 2, W - 2, fill=(0, 0, 0, 90))
    return _finish(a, L)


def monitor_glass():
    W = 64
    x, y = P.grid(W, W)
    base = P.vgrad(W, W, (9, 13, 22), (3, 5, 9))
    sheen = np.clip(1.0 - np.abs((x + y * 0.6) - 0.62) * 5.0, 0, 1) ** 2 * 26
    base = base + sheen[..., None] * np.array([0.5, 0.8, 1.0], np.float32)[None, None, :]
    base = base + (P.white_noise(W, W, 3)[..., None] - 0.5) * 5.0
    base = P.vignette(base, 0.4, 2.4)
    return P.to_image(np.clip(base, 0, 255))


def _casing_base(seed, base=(60, 65, 76)):
    W = 64
    a = P.metal(W, W, seed, base, contrast=0.09, brush=0.65, grain=0.03)
    return a


def monitor_casing_a():
    W = 64
    a = _casing_base(21)
    a = P.scratches(a, 5, 9, 0.12)
    a = P.bevel(a, 2, 0.20, 0.38, inset=False)
    L = Layer(W, W)
    # recessed inner plate
    L.rrect(7, 7, W - 7, W - 7, 2.5, outline=(10, 12, 16, 220), width=1.2)
    L.rrect(8.2, 8.2, W - 8.2, W - 8.2, 2.0, outline=(255, 255, 255, 38), width=0.8)
    for cx, cy in ((4.2, 4.2), (W - 4.2, 4.2), (4.2, W - 4.2), (W - 4.2, W - 4.2)):
        P.screw(L, cx, cy, 1.9)
    # engraved stencil text
    L.text((W / 2, 31), "DWB-9000", 6.0, (14, 16, 20, 200), anchor="mm")
    L.text((W / 2, 38.5), "CLASS  V  /  SECURE", 3.2, (14, 16, 20, 170), anchor="mm")
    L.line([(14, 44), (W - 14, 44)], (14, 16, 20, 150), 0.7)
    P.barcode(L, 18, 47, W - 18, 53, 4, (14, 16, 20, 190))
    # cyan status pip
    L.circle(W - 11, 14, 1.4, fill=CYAN)
    return _finish(a, L, glow=[(0, 2.6, 0.18)])


def monitor_casing_b():
    W = 64
    a = _casing_base(33, (56, 61, 72))
    a = P.scratches(a, 9, 7, 0.1)
    a = P.bevel(a, 2, 0.20, 0.38)
    L = Layer(W, W)
    L.rrect(7, 7, W - 7, W - 7, 2.5, outline=(10, 12, 16, 220), width=1.2)
    # louvres
    for i in range(7):
        y = 12 + i * 3.1
        L.rrect(14, y, W - 14, y + 1.5, 0.7, fill=(8, 9, 12, 255))
        L.rect(14.5, y + 1.4, W - 14.5, y + 1.9, fill=(255, 255, 255, 40))
    # neon trace groove
    L.line([(10, 44), (26, 44), (30, 49), (46, 49), (50, 44), (W - 10, 44)], (6, 8, 10, 255), 2.2)
    L.line([(10, 44), (26, 44), (30, 49), (46, 49), (50, 44), (W - 10, 44)], CYAN, 0.8)
    P.screw(L, 4.2, 4.2, 1.9)
    P.screw(L, W - 4.2, 4.2, 1.9)
    P.screw(L, 4.2, W - 4.2, 1.9)
    P.screw(L, W - 4.2, W - 4.2, 1.9)
    L.text((W / 2, 56), "THERMAL  EXHAUST", 3.0, (14, 16, 20, 170), anchor="mm")
    return _finish(a, L, glow=[(0, 2.4, 0.5)])


def monitor_casing_vent():
    W = 64
    a = _casing_base(41, (52, 57, 68))
    a = P.bevel(a, 2, 0.2, 0.38)
    L = Layer(W, W)
    L.rrect(6, 6, W - 6, W - 6, 3.0, fill=(5, 6, 9, 255), outline=(150, 156, 170, 255), width=1.0)
    glow = Layer(W, W)
    n = 11
    for i in range(n):
        y = 9 + i * 4.3
        t = i / (n - 1)
        col = (int(255 * (1 - t) + 25 * t), int(44 * (1 - t) + 230 * t), int(214 * (1 - t) + 255 * t), 255)
        L.rrect(10, y, W - 10, y + 2.4, 1.0, fill=(2, 2, 4, 255))
        glow.rrect(11.5, y + 0.6, W - 11.5, y + 1.8, 0.6, fill=col[:3] + (200,))
    # fan blades behind the slots hint
    return _finish(a, L, glow, glow=[(1, 2.8, 0.9)])


def monitor_casing_edge():
    W = 64
    a = P.metal(W, W, 55, (50, 54, 64), contrast=0.06, brush=0.4, grain=0.03)
    a = P.bevel(a, 3, 0.25, 0.4)
    L = Layer(W, W)
    L.rect(4, 31, W - 4, 33, fill=(4, 5, 8, 255))
    L.rect(5, 31.7, W - 5, 32.5, fill=CYAN[:3] + (170,))
    for cx in (9, 55):
        P.screw(L, cx, 14, 2.0)
        P.screw(L, cx, 50, 2.0)
    return _finish(a, L, glow=[(0, 2.0, 0.35)])


def monitor_back_logo():
    W = 64
    a = _casing_base(66, (54, 58, 70))
    a = P.bevel(a, 2, 0.2, 0.38)
    L = Layer(W, W)
    L.rrect(7, 7, W - 7, W - 7, 3, outline=(10, 12, 16, 220), width=1.2)
    cx, cy = 32, 28
    # dark emblem plate
    L.circle(cx, cy, 19.5, fill=(6, 8, 12, 255), outline=(150, 156, 170, 200), width=1.0)
    g = Layer(W, W)
    g.circle(cx, cy, 17, outline=CYAN, width=1.3)
    g.circle(cx, cy, 13.5, outline=MAG[:3] + (150,), width=0.8)
    # hexagon web
    pts = [(cx + 10.5 * math.cos(math.radians(60 * k + 30)), cy + 10.5 * math.sin(math.radians(60 * k + 30))) for k in range(6)]
    g.poly(pts, outline=CYAN[:3] + (230,))
    for p in pts:
        g.line([(cx, cy), p], CYAN[:3] + (90,), 0.6)
        g.circle(p[0], p[1], 1.0, fill=CYAN)
    g.text((cx, cy + 0.5), "D", 14.0, MAG, anchor="mm")
    # tick marks around ring
    for k in range(36):
        a0 = math.radians(k * 10)
        r0, r1 = 17.2, 18.8 if k % 3 == 0 else 18.0
        g.line([(cx + r0 * math.cos(a0), cy + r0 * math.sin(a0)), (cx + r1 * math.cos(a0), cy + r1 * math.sin(a0))], CYAN[:3] + (200,), 0.5)
    g.text((W / 2, 53), "DARKWEB", 7.5, CYAN, anchor="mm")
    g.text((W / 2, 59), "S E C U R E   T E R M I N A L", 2.6, (200, 220, 230, 200), anchor="mm")
    return _finish(a, L, g, glow=[(1, 2.6, 0.95)])


def monitor_back_ports():
    W = 64
    a = _casing_base(77, (52, 57, 68))
    a = P.bevel(a, 2, 0.2, 0.38)
    L = Layer(W, W)
    L.rrect(5, 8, W - 5, W - 8, 3, fill=(8, 9, 13, 255), outline=(150, 156, 170, 255), width=0.9)
    # power inlet
    L.rrect(9, 13, 25, 27, 1.5, fill=(2, 2, 3, 255), outline=(190, 194, 205, 255), width=0.7)
    for px in (13, 17, 21):
        L.rect(px - 0.7, 17, px + 0.7, 23, fill=(180, 160, 70, 255))
    L.text((17, 31), "PWR", 3.4, (200, 205, 215, 230), anchor="mm")
    # HDMI x2
    for i, x in enumerate((30, 44)):
        L.poly([(x, 14), (x + 12, 14), (x + 12, 20), (x + 10, 22.5), (x + 2, 22.5), (x, 20)], fill=(2, 2, 3, 255), outline=(190, 194, 205, 255))
        L.rect(x + 2, 17, x + 10, 18.4, fill=(210, 200, 120, 255))
        L.text((x + 6, 28), "HDMI " + str(i + 1), 3.0, (200, 205, 215, 230), anchor="mm")
    # ethernet + USB-C row
    L.rrect(9, 36, 23, 48, 1, fill=(2, 2, 3, 255), outline=(190, 194, 205, 255), width=0.7)
    for k in range(8):
        L.rect(10.5 + k * 1.5, 38, 11.2 + k * 1.5, 41, fill=(210, 200, 120, 255))
    L.text((16, 52), "NET", 3.4, (200, 205, 215, 230), anchor="mm")
    for i, x in enumerate((30, 42)):
        L.rrect(x, 38, x + 8, 43, 2.5, fill=(2, 2, 3, 255), outline=(190, 194, 205, 255), width=0.7)
        L.rect(x + 1.5, 40, x + 6.5, 41, fill=(160, 165, 180, 255))
        L.text((x + 4, 48), "USB-C", 2.6, (200, 205, 215, 230), anchor="mm")
    g = Layer(W, W)
    for i, (x, col) in enumerate(((54, CYAN), (54, (0, 0, 0, 0)))):
        pass
    g.circle(54, 40, 1.5, fill=(60, 255, 120, 255))
    g.circle(54, 46, 1.5, fill=MAG)
    g.circle(54, 17, 1.5, fill=CYAN)
    g.circle(54, 23, 1.5, fill=RED)
    P.screw(L, 7.5, 11, 1.4)
    P.screw(L, W - 7.5, W - 11, 1.4)
    return _finish(a, L, g, glow=[(1, 2.2, 0.9)])


def monitor_screen_item():
    W = 64
    x, y = P.grid(W, W)
    base = P.vgrad(W, W, (3, 12, 26), (1, 4, 12))
    rng = np.random.default_rng(7)
    L = Layer(W, W)
    # matrix rain columns
    for cx in range(3, W - 2, 3):
        ln = int(8 + rng.random() * 30)
        y0 = rng.random() * W - 10
        for k in range(ln):
            t = 1.0 - k / ln
            yy = y0 + k * 1.7
            if 3 < yy < W - 3:
                col = (int(40 * t), int(255 * t), int(200 * t), int(40 + 150 * t))
                L.rect(cx, yy, cx + 1.4, yy + 1.1, fill=col)
    # window
    G = Layer(W, W)
    G.rrect(14, 14, 50, 46, 2, fill=(4, 9, 20, 235), outline=CYAN, width=0.9)
    G.rect(14.5, 14.5, 49.5, 19, fill=CYAN[:3] + (90,))
    for i in range(5):
        G.rect(18, 22 + i * 4.2, 18 + 10 + (i * 7) % 18, 23.4 + i * 4.2, fill=MAG[:3] + (230,) if i % 2 else CYAN[:3] + (210,))
    G.line([(30, 42), (34, 36), (38, 40), (42, 30), (47, 33)], (255, 196, 58, 255), 0.9)
    arr = P.composite(base, L.done())
    arr = P.composite(arr, G.done())
    arr = P.add_glow(arr, G.done(), 2.2, 0.8)
    # scanlines & sheen
    sc = (np.sin(np.arange(W) * math.pi * 1.0)[:, None] * 0.5 + 0.5) * 0.18
    arr = arr * (1 - sc[..., None])
    sheen = np.clip(1.0 - np.abs((x + y * 0.8) - 0.5) * 4.0, 0, 1) ** 2 * 40
    arr = arr + sheen[..., None]
    arr = P.bevel(arr, 2, 0.35, 0.5)
    return P.to_image(np.clip(arr, 0, 255))


# ---------------------------------------------------------------------------------------------------- keyboard
def keyboard_base(lit=False):
    W = 128
    a = P.metal(W, W, 81, (23, 25, 31), contrast=0.08, brush=0.5, grain=0.03)
    L = Layer(W, W)
    # deck occupies z 3..13 -> v 24..104 ; underglow bed (visible between the 3D keys)
    glow = Layer(W, W)
    stops = [CYAN, (120, 90, 255, 255), MAG, (255, 90, 120, 255), GOLD]
    k = 0
    xs = 4
    while xs < 124:
        t = (xs - 4) / 120.0
        seg = t * (len(stops) - 1)
        i = min(len(stops) - 2, int(seg))
        c = P.mixc(stops[i], stops[i + 1], seg - i)
        glow.rect(xs, 42, xs + 4, 101, fill=c[:3] + ((150 if lit else 70),))
        xs += 4
    # palm rest
    L.rrect(4, 25, 124, 40, 4, fill=(16, 17, 22, 255), outline=(60, 64, 76, 255), width=1.0)
    for x in range(10, 118, 5):
        L.rect(x, 28, x + 2, 37, fill=(24, 26, 32, 255))
    tt = P.rot_text(W, W, "DARKWEB  K-666", 5.0, 64, 33, (120, 126, 140, 255))
    L.rrect(4, 41.5, 124, 102.5, 3, outline=(70, 75, 90, 255), width=1.0)
    # top status LEDs
    st = Layer(W, W)
    for i, c in enumerate((CYAN, MAG, (60, 255, 120, 255))):
        st.circle(104 + i * 6, 31, 1.6, fill=c if (lit or i == 0) else c[:3] + (90,))
    return _finish(a, glow, L, tt, st, glow=[(0, 2.0, 0.8 if lit else 0.45), (3, 2.4, 0.8)])


def keyboard_side():
    W = 64
    a = P.metal(W, W, 91, (34, 37, 46), contrast=0.07, brush=0.5, grain=0.03)
    a = P.bevel(a, 2, 0.22, 0.4)
    L = Layer(W, W)
    L.rect(0, 50, W, 54, fill=(5, 6, 9, 255))
    L.rect(0, 51.2, W, 52.4, fill=CYAN[:3] + (190,))
    # rubber feet
    L.rrect(4, 58, 18, 63, 1.5, fill=(10, 10, 12, 255))
    L.rrect(46, 58, 60, 63, 1.5, fill=(10, 10, 12, 255))
    # vent dashes
    for i in range(6):
        L.rrect(10 + i * 8, 14, 15 + i * 8, 16.5, 1.0, fill=(4, 5, 7, 255))
    return _finish(a, L, glow=[(0, 1.8, 0.4)])


def keycap(kind, lit=False):
    W = 32
    if kind == "dark":
        top, bot, edge, leg = (50, 54, 64), (24, 26, 33), (14, 15, 20), CYAN
    elif kind == "light":
        top, bot, edge, leg = (210, 216, 228), (150, 156, 172), (96, 100, 114), (30, 34, 44, 255)
    else:
        top, bot, edge, leg = (30, 190, 215), (12, 96, 128), (8, 60, 84), (255, 255, 255, 255)
    a = P.vgrad(W, W, top, bot)
    # concave dish
    x, y = P.grid(W, W)
    d = np.sqrt((x - 0.5) ** 2 + (y - 0.46) ** 2)
    a = a * (0.80 + 0.35 * np.clip(d * 1.6, 0, 1))[..., None]
    if lit:
        a = a * 1.0 + np.array([30, 40, 50], np.float32)[None, None, :] * (1 - d[..., None] * 1.4)
    a = P.bevel(a, 2, 0.25, 0.45)
    a = a + (P.white_noise(W, W, 5)[..., None] - 0.5) * 6
    L = Layer(W, W)
    L.rect(0, 0, W, 1, fill=edge[:3] + (255,))
    L.rect(0, W - 1, W, W, fill=edge[:3] + (255,))
    L.rect(0, 0, 1, W, fill=edge[:3] + (255,))
    L.rect(W - 1, 0, W, W, fill=edge[:3] + (255,))
    lc = leg if (lit or kind != "dark") else leg[:3] + (150,)
    L.poly([(W / 2, W / 2 - 6), (W / 2 + 4.5, W / 2 - 1.5), (W / 2, W / 2 + 3), (W / 2 - 4.5, W / 2 - 1.5)], fill=lc)
    return _finish(a, L, glow=[(0, 1.8, 0.9)] if lit else None)


# ---------------------------------------------------------------------------------------------------- mouse
def _mouse_shell(lit=False):
    W = 64
    x, y = P.grid(W, W)
    # top-view: x in 3..13 px -> 12..52 ; z in 1..15 px -> 4..60  (nose = +z = larger v)
    cx = 32.0
    base = P.vgrad(W, W, (34, 27, 66), (58, 44, 104))
    base = base * (0.9 + 0.2 * P.fbm(W, W, 5, 3, 3))[..., None]
    # gloss band
    gl = np.clip(1.0 - np.abs((x - 0.40) * 5.5), 0, 1) ** 2 * 85
    base = base + gl[..., None] * np.array([0.8, 0.7, 1.0], np.float32)[None, None, :]
    L = Layer(W, W)
    # button split + wheel slot (nose end)
    L.rect(cx - 0.5, 36, cx + 0.5, 60, fill=(2, 2, 6, 255))
    L.line([(12, 36), (cx - 0.5, 36)], (2, 2, 6, 255), 1.0)
    L.line([(cx + 0.5, 36), (52, 36)], (2, 2, 6, 255), 1.0)
    L.rrect(cx - 3, 38.5, cx + 3, 49.5, 2.5, fill=(2, 2, 6, 255))
    # side neon strips
    G = Layer(W, W)
    on = 255 if lit else 160
    G.line([(14.5, 20), (13.2, 32), (13.6, 44)], MAG[:3] + (on,), 1.0)
    G.line([(49.5, 20), (50.8, 32), (50.4, 44)], CYAN[:3] + (on,), 1.0)
    # logo on the back
    G.circle(cx, 16, 4.2, outline=CYAN[:3] + (on,), width=1.0)
    logo = P.rot_text(W, W, "D", 6.0, cx, 16.4, MAG[:3] + (on,))
    if lit:
        G.rrect(15, 38, cx - 1.2, 58, 3, fill=CYAN[:3] + (90,))
        G.rrect(cx + 1.2, 38, 49, 58, 3, fill=MAG[:3] + (90,))
    return _finish(base, L, G, logo, glow=[(1, 1.8, 0.9 if lit else 0.6), (2, 1.6, 0.8)])


def mouse_shell():
    return _mouse_shell(False)


def mouse_shell_lit():
    return _mouse_shell(True)


def mouse_side():
    W = 64
    a = P.vgrad(W, W, (46, 36, 82), (12, 9, 24))
    a = a + (P.white_noise(W, W, 9)[..., None] - 0.5) * 6
    L = Layer(W, W)
    # fine seam & sparkle
    for k in range(0, W, 8):
        L.rect(k, 0, k + 0.5, W, fill=(0, 0, 0, 36))
    return _finish(a, L)


def mouse_glow():
    W = 64
    a = P.hgrad(W, W, (255, 44, 214), (25, 230, 255))
    a = a * (0.8 + 0.4 * P.fbm(W, W, 21, 3, 3))[..., None]
    L = Layer(W, W)
    L.rect(0, 28, W, 36, fill=(255, 255, 255, 120))
    return _finish(np.clip(a, 0, 255), L)


def mouse_belly():
    W = 64
    a = P.metal(W, W, 33, (26, 27, 34), contrast=0.07)
    L = Layer(W, W)
    L.rrect(12, 4, 52, 60, 10, outline=(60, 62, 74, 255), width=1.0)
    for (cx, cy) in ((20, 10), (44, 10), (20, 54), (44, 54)):
        L.circle(cx, cy, 3.0, fill=(210, 212, 222, 255))
        L.circle(cx - 0.6, cy - 0.6, 1.3, fill=(255, 255, 255, 200))
    # sensor window
    L.circle(32, 32, 6.2, fill=(2, 2, 4, 255), outline=(150, 154, 168, 255), width=0.8)
    G = Layer(W, W)
    G.circle(32, 32, 3.4, fill=(255, 40, 62, 255))
    L.text((32, 49), "6400 DPI", 3.6, (150, 154, 170, 255), anchor="mm")
    return _finish(a, L, G, glow=[(1, 3.0, 0.9)])


def mouse_button():
    W = 32
    a = P.vgrad(W, W, (60, 50, 104), (20, 16, 42))
    L = Layer(W, W)
    L.rrect(3, 3, W - 3, W - 3, 5, outline=(255, 255, 255, 40), width=0.8)
    return _finish(a, L)


def mouse_wheel():
    W = 32
    a = P.vgrad(W, W, (30, 30, 38), (12, 12, 16))
    L = Layer(W, W)
    for y in range(2, W, 3):
        L.rect(0, y, W, y + 1.2, fill=(90, 92, 106, 255))
    G = Layer(W, W)
    G.rect(0, 0, 3, W, fill=CYAN)
    G.rect(W - 3, 0, W, W, fill=MAG)
    return _finish(a, L, G, glow=[(1, 1.5, 0.7)])


def mouse_cable():
    W = 16
    a = P.carbon(W, W, 4, (20, 20, 26), 2)
    L = Layer(W, W)
    L.rect(0, 0, W, 2, fill=CYAN[:3] + (160,))
    return _finish(a, L)


TEXTURES = [
    ("block/monitor_bezel", monitor_bezel),
    ("block/monitor_glass", monitor_glass),
    ("block/monitor_casing_a", monitor_casing_a),
    ("block/monitor_casing_b", monitor_casing_b),
    ("block/monitor_casing_vent", monitor_casing_vent),
    ("block/monitor_casing_edge", monitor_casing_edge),
    ("block/monitor_back_logo", monitor_back_logo),
    ("block/monitor_back_ports", monitor_back_ports),
    ("block/monitor_screen_item", monitor_screen_item),
    ("block/keyboard_base", lambda: keyboard_base(False)),
    ("block/keyboard_base_lit", lambda: keyboard_base(True)),
    ("block/keyboard_side", keyboard_side),
    ("block/keycap_dark", lambda: keycap("dark")),
    ("block/keycap_light", lambda: keycap("light")),
    ("block/keycap_accent", lambda: keycap("accent")),
    ("block/keycap_dark_lit", lambda: keycap("dark", True)),
    ("block/keycap_light_lit", lambda: keycap("light", True)),
    ("block/keycap_accent_lit", lambda: keycap("accent", True)),
    ("block/mouse_shell", mouse_shell),
    ("block/mouse_shell_lit", mouse_shell_lit),
    ("block/mouse_side", mouse_side),
    ("block/mouse_glow", mouse_glow),
    ("block/mouse_belly", mouse_belly),
    ("block/mouse_button", mouse_button),
    ("block/mouse_wheel", mouse_wheel),
    ("block/mouse_cable", mouse_cable),
]
