#!/usr/bin/env python3
"""core_tex_b - textures for the delivery boxes (2 tiers), keycards, keycard door, omega terminal, phone stand."""
import math

import numpy as np
from PIL import Image

import core_paint as P
from core_paint import Layer
from core_tex_a import CYAN, MAG, GOLD, RED, _finish

PAL = {
    1: dict(base=(30, 34, 44), trim=(78, 86, 104), a=CYAN, b=MAG, dark=(8, 10, 14, 255), paper=(205, 220, 230, 255), name="DARKNET EXPRESS", code="DWB-0001"),
    2: dict(base=(44, 34, 28), trim=(190, 140, 52), a=GOLD, b=RED, dark=(12, 8, 6, 255), paper=(236, 205, 140, 255), name="OMEGA  PROTOCOL", code="OMG-9999"),
}


def _col(c, a):
    return tuple(c[:3]) + (a,)


# ---------------------------------------------------------------------------------------------------- delivery boxes
def _omega_glyph(L, cx, cy, r, color, width):
    """Omega: open ring with two feet (drawn with arcs/lines so it needs no font)"""
    L.arc((cx - r, cy - r, cx + r, cy + r), 125, 415, color, width)
    L.line([(cx - r * 0.95, cy + r * 0.88), (cx - r * 0.38, cy + r * 0.88)], color, width)
    L.line([(cx + r * 0.95, cy + r * 0.88), (cx + r * 0.38, cy + r * 0.88)], color, width)


def crate_wall(tier, kind):
    pal = PAL[tier]
    W = 128
    seed = 100 * tier + (1 if kind == "front" else 2)
    canvas = P.metal(W, W, seed, pal["base"], contrast=0.08, brush=0.5, grain=0.03)
    pw, ph = 104, 72  # panel = 13 x 9 block pixels at 8 tex px each
    a = P.metal(pw, ph, seed + 3, pal["base"], contrast=0.09, brush=0.6, grain=0.035)
    a = P.scratches(a, seed, 10, 0.12)
    a = P.bevel(a, 3, 0.22, 0.42)
    L = Layer(pw, ph)
    G = Layer(pw, ph)
    ca, cb = pal["a"], pal["b"]
    # corner brackets
    for (x0, y0, sx, sy) in ((4, 4, 1, 1), (pw - 4, 4, -1, 1), (4, ph - 4, 1, -1), (pw - 4, ph - 4, -1, -1)):
        L.line([(x0, y0 + sy * 12), (x0, y0), (x0 + sx * 12, y0)], _col(pal["trim"], 255), 2.2)
        P.screw(L, x0 + sx * 4.5, y0 + sy * 4.5, 1.7)
    # hazard band
    for i in range(-4, 40):
        x = i * 6.0
        L.poly([(x, ph - 12), (x + 3, ph - 12), (x - 3 + 0, ph - 4), (x - 6, ph - 4)], fill=_col(ca, 215))
    L.rect(0, ph - 12, pw, ph - 11.2, fill=(0, 0, 0, 170))
    L.rect(0, ph - 4, pw, ph - 3.2, fill=(0, 0, 0, 170))
    # centre strap with lock plate
    sx0, sx1 = pw / 2 - 8, pw / 2 + 8
    L.rect(sx0, 0, sx1, ph - 12, fill=(12, 13, 18, 255))
    L.rect(sx0, 0, sx0 + 1.2, ph - 12, fill=(255, 255, 255, 36))
    L.rect(sx1 - 1.2, 0, sx1, ph - 12, fill=(0, 0, 0, 120))
    G.line([(sx0 + 2, 0), (sx0 + 2, ph - 12)], _col(ca, 230), 0.9)
    G.line([(sx1 - 2, 0), (sx1 - 2, ph - 12)], _col(cb, 230), 0.9)
    L.rrect(pw / 2 - 11, 20, pw / 2 + 11, 44, 4, fill=(8, 9, 12, 255), outline=_col(pal["trim"], 255), width=1.6)
    if tier == 1:
        pts = [(pw / 2 + 6.5 * math.cos(math.radians(60 * k + 30)), 32 + 6.5 * math.sin(math.radians(60 * k + 30))) for k in range(6)]
        G.poly(pts, outline=_col(ca, 255))
        G.circle(pw / 2, 32, 2.2, fill=_col(cb, 255))
    else:
        _omega_glyph(G, pw / 2, 32, 6.5, _col(ca, 255), 1.5)
        G.circle(pw / 2, 32, 1.4, fill=_col(cb, 255))
    if kind == "front":
        # shipping label
        L.rrect(6, 8, 38, 54, 2, fill=pal["paper"])
        L.rect(6, 8, 38, 15, fill=pal["dark"])
        L.text((22, 11.5), pal["name"][:9], 3.4, _col(ca, 255), anchor="mm")
        for i in range(4):
            L.rect(9, 19 + i * 3.6, 9 + 14 + (i * 5) % 12, 20.2 + i * 3.6, fill=(40, 44, 54, 255))
        P.barcode(L, 9, 37, 35, 49, 4 + tier, pal["dark"])
        L.text((22, 52), pal["code"], 2.8, (30, 34, 44, 255), anchor="mm")
        # right side: warnings and circuit
        P.circuit(G, pw, ph - 14, seed, _col(ca, 150), 9, 0.6, 1.0)
        L.rrect(66, 8, 98, 26, 2, fill=(10, 11, 15, 255), outline=_col(cb, 255), width=1.0)
        G.text((82, 14), "ENCRYPTED", 4.0, _col(cb, 255), anchor="mm")
        G.text((82, 21), "// DO NOT SCAN", 2.8, _col(ca, 255), anchor="mm")
        # qr-ish block
        rng = np.random.default_rng(seed)
        for qx in range(14):
            for qy in range(14):
                if rng.random() < 0.5:
                    L.rect(70 + qx * 1.6, 30 + qy * 1.2, 70 + qx * 1.6 + 1.5, 30 + qy * 1.2 + 1.1, fill=pal["paper"])
    else:
        # vent grille + emblem
        for i in range(7):
            L.rrect(8, 10 + i * 6, 38, 13.5 + i * 6, 1.4, fill=(3, 4, 6, 255))
            G.rect(10, 11.4 + i * 6, 36, 12.2 + i * 6, fill=_col(ca if i % 2 == 0 else cb, 120))
        if tier == 1:
            G.circle(82, 30, 12, outline=_col(ca, 230), width=1.2)
            G.circle(82, 30, 8, outline=_col(cb, 200), width=0.9)
            G.text((82, 30.6), "D", 9.0, _col(ca, 255), anchor="mm")
        else:
            _omega_glyph(G, 82, 30, 11, _col(ca, 255), 2.0)
            G.circle(82, 30, 15, outline=_col(cb, 200), width=1.0)
        G.text((82, 52), "SEALED", 4.4, _col(cb, 255), anchor="mm")
    panel = _finish(a, L, G, glow=[(1, 2.4, 0.85)])
    out = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    out.paste(panel, (12, 56))
    return out


def crate_top(tier):
    """rim of the open body (13x13 px footprint, drawn on a 64px sheet)"""
    pal = PAL[tier]
    W = 64
    a = P.metal(W, W, 150 + tier, pal["base"], contrast=0.08)
    a = P.bevel(a, 2, 0.25, 0.4)
    L = Layer(W, W)
    L.rect(7, 7, 57, 57, fill=(4, 5, 7, 255))
    L.rect(7, 7, 57, 8.2, fill=(0, 0, 0, 120))
    G = Layer(W, W)
    G.rect(6, 6, 58, 7, fill=_col(pal["a"], 190))
    G.rect(6, 57, 58, 58, fill=_col(pal["b"], 190))
    return _finish(a, L, G, glow=[(1, 1.8, 0.7)])


def crate_bottom(tier):
    pal = PAL[tier]
    W = 64
    a = P.metal(W, W, 160 + tier, tuple(int(c * 0.7) for c in pal["base"]), contrast=0.08)
    a = P.bevel(a, 2, 0.15, 0.4)
    L = Layer(W, W)
    for cx, cy in ((10, 10), (54, 10), (10, 54), (54, 54)):
        L.rrect(cx - 5, cy - 5, cx + 5, cy + 5, 2, fill=(8, 8, 10, 255), outline=_col(pal["trim"], 200), width=0.8)
    L.text((32, 32), "THIS SIDE DOWN", 4.0, _col(pal["trim"], 200), anchor="mm")
    return _finish(a, L)


def crate_inner(tier):
    pal = PAL[tier]
    W = 64
    a = P.foam(W, W, 170 + tier, (16, 18, 24) if tier == 1 else (30, 20, 16))
    a = a + P.vgrad(W, W, (0, 0, 0), _col(pal["a"], 0)[:3]) * 0.06
    L = Layer(W, W)
    P.circuit(L, W, W, 5 + tier, _col(pal["a"], 90), 11, 0.6, 1.0)
    G = Layer(W, W)
    G.rect(0, W - 6, W, W - 4, fill=_col(pal["a"], 200))
    G.rect(0, W - 3, W, W - 2, fill=_col(pal["b"], 200))
    return _finish(a, L, G, glow=[(1, 2.4, 0.8)])


def lid_top(tier):
    pal = PAL[tier]
    W = 64
    a = P.metal(W, W, 180 + tier, pal["base"], contrast=0.09, brush=0.6)
    a = P.scratches(a, 7 + tier, 9, 0.12)
    a = P.bevel(a, 3, 0.26, 0.42)
    L = Layer(W, W)
    G = Layer(W, W)
    ca, cb = pal["a"], pal["b"]
    # strap cross
    L.rect(26, 0, 38, W, fill=(10, 11, 15, 255))
    L.rect(0, 26, W, 38, fill=(10, 11, 15, 255))
    G.line([(28, 0), (28, W)], _col(ca, 235), 0.8)
    G.line([(36, 0), (36, W)], _col(cb, 235), 0.8)
    G.line([(0, 28), (W, 28)], _col(cb, 235), 0.8)
    G.line([(0, 36), (W, 36)], _col(ca, 235), 0.8)
    # lock rosette
    L.circle(32, 32, 11, fill=(6, 7, 10, 255), outline=_col(pal["trim"], 255), width=1.6)
    G.circle(32, 32, 8.5, outline=_col(ca, 255), width=1.0)
    if tier == 1:
        pts = [(32 + 5.6 * math.cos(math.radians(60 * k)), 32 + 5.6 * math.sin(math.radians(60 * k))) for k in range(6)]
        G.poly(pts, outline=_col(cb, 255))
        G.circle(32, 32, 2.0, fill=_col(ca, 255))
    else:
        _omega_glyph(G, 32, 31.5, 5.2, _col(cb, 255), 1.4)
    for cx, cy in ((6, 6), (58, 6), (6, 58), (58, 58)):
        P.screw(L, cx, cy, 2.0)
    # corner chevrons + tiny text
    for (x0, y0, sx, sy) in ((12, 12, 1, 1), (52, 12, -1, 1), (12, 52, 1, -1), (52, 52, -1, -1)):
        G.line([(x0, y0 + sy * 5), (x0, y0), (x0 + sx * 5, y0)], _col(ca, 210), 0.8)
    P.barcode(L, 10, 46, 22, 50, 7 + tier, _col(pal["trim"], 220))
    return _finish(a, L, G, glow=[(1, 2.2, 0.9)])


def lid_side(tier):
    pal = PAL[tier]
    W = 64
    a = P.foam(W, W, 190 + tier, (14, 16, 22) if tier == 1 else (28, 18, 14))
    L = Layer(W, W)
    G = Layer(W, W)
    # top 1/8 band = visible side of the lid: dark plate with neon line + bolts
    L.rect(0, 0, W, 8, fill=_col(pal["base"], 255))
    L.rect(0, 0, W, 1, fill=(255, 255, 255, 50))
    L.rect(0, 7, W, 8, fill=(0, 0, 0, 160))
    G.rect(0, 3, W, 5, fill=_col(pal["a"], 255))
    for x in range(4, W, 12):
        L.circle(x, 1.8, 0.9, fill=_col(pal["trim"], 255))
    # underside = padded lining with glow circuit
    P.circuit(L, W, W - 10, 40 + tier, _col(pal["a"], 100), 8, 0.6, 1.0)
    G.rect(0, 12, W, 13, fill=_col(pal["b"], 120))
    return _finish(a, L, G, glow=[(1, 1.6, 0.9)])


# ---------------------------------------------------------------------------------------------------- keycards (128x80, rounded corners, transparent outside)
def keycard(tier, back=False):
    W, H = 128, 80
    pal = PAL[tier]
    ca, cb = pal["a"], pal["b"]
    if tier == 1:
        base = P.diag_grad(W, H, (10, 12, 20), (30, 22, 56), 1.0)
    else:
        base = P.diag_grad(W, H, (56, 38, 12), (22, 12, 10), 0.9)
    n = P.fbm(W, H, 600 + tier + (1 if back else 0), 4, 3)
    base = base * (0.85 + 0.3 * n)[..., None]
    # holographic sweep
    x, y = P.grid(W, H)
    holo = np.clip(1.0 - np.abs((x * 0.8 + y * 0.9) - 0.55) * 3.2, 0, 1) ** 2
    h1 = np.array(ca[:3], np.float32)[None, None, :]
    h2 = np.array(cb[:3], np.float32)[None, None, :]
    mixk = np.clip((x - 0.3) * 1.6, 0, 1)[..., None]
    base = base + holo[..., None] * (h1 * (1 - mixk) + h2 * mixk) * 0.38
    base = P.bevel(base, 2, 0.28, 0.42)
    L = Layer(W, H)
    G = Layer(W, H)
    if not back:
        # chip
        L.rrect(10, 28, 32, 48, 3, fill=(210, 176, 80, 255), outline=(120, 90, 30, 255), width=0.8)
        for yy in (33, 38, 43):
            L.line([(10, yy), (32, yy)], (120, 90, 30, 255), 0.7)
        L.line([(21, 28), (21, 48)], (120, 90, 30, 255), 0.7)
        # emblem
        if tier == 1:
            G.circle(100, 24, 13, outline=_col(ca, 255), width=1.6)
            G.circle(100, 24, 9, outline=_col(cb, 220), width=1.0)
            G.text((100, 24.6), "D", 13, _col(ca, 255), anchor="mm")
        else:
            _omega_glyph(G, 100, 24, 12, _col(ca, 255), 2.4)
            G.circle(100, 24, 15.5, outline=_col(cb, 230), width=1.0)
        G.text((10, 12), "DARK WEB" if tier == 1 else "OMEGA", 9.0, _col(ca, 255), anchor="lm")
        G.text((10, 22), "ACCESS KEYCARD" if tier == 1 else "ROOT CLEARANCE", 4.6, (220, 230, 240, 230), anchor="lm")
        L.text((10, 60), "LEVEL  " + ("I" if tier == 1 else "OMEGA"), 7.0, _col(cb, 255), anchor="lm")
        L.text((10, 70), pal["code"] + "-" + "4F2A-91C7", 4.4, (200, 205, 215, 220), anchor="lm")
        P.circuit(G, W, H, 9 + tier, _col(ca, 70), 7, 0.6, 1.0)
        G.line([(60, 62), (118, 62)], _col(ca, 230), 1.2)
        G.line([(60, 66), (100, 66)], _col(cb, 230), 1.0)
    else:
        L.rect(0, 10, W, 26, fill=(6, 6, 8, 255))
        L.rect(0, 10, W, 11, fill=(255, 255, 255, 30))
        L.rect(10, 34, 92, 50, fill=(226, 228, 232, 255))
        L.text((12, 41), "AUTHORISED SIGNATURE", 3.4, (60, 64, 76, 255), anchor="lm")
        L.line([(14, 47), (88, 47)], (40, 44, 56, 255), 0.5)
        P.barcode(L, 12, 58, 86, 72, 20 + tier, (230, 232, 238, 255))
        G.text((100, 44), "SECURE", 5.0, _col(ca, 255), anchor="mm")
        G.text((100, 52), "NFC / RFID", 3.6, _col(cb, 255), anchor="mm")
        G.circle(100, 66, 7, outline=_col(ca, 255), width=1.0)
        G.circle(100, 66, 3.5, outline=_col(cb, 255), width=1.0)
        L.text((W / 2, 4.8), "PROPERTY OF THE DARK WEB - IF FOUND, RUN", 3.0, (180, 186, 200, 220), anchor="mm")
    arr = _finish(base, L, G, glow=[(1, 2.2, 0.8)])
    # outer neon border
    bl = Layer(W, H)
    bl.rrect(1, 1, W - 1, H - 1, 7, outline=_col(ca, 255), width=1.3)
    arr = P.composite(np.asarray(arr, np.float32)[..., :3], bl.done())
    arr = P.add_glow(arr, bl.done(), 1.6, 0.55)
    img = P.to_image(arr)
    # rounded-card alpha mask
    m = Image.new("L", (W * 4, H * 4), 0)
    from PIL import ImageDraw
    ImageDraw.Draw(m).rounded_rectangle([0, 0, W * 4 - 1, H * 4 - 1], 28, fill=255)
    m = m.resize((W, H), Image.LANCZOS)
    img.putalpha(m)
    return img


# ---------------------------------------------------------------------------------------------------- keycard door  (painted as one 128x256 door then cut in two)
def door_full():
    """128x256 painted as one door and cut into two halves (left 24px = hinge strip, bottom/top 24px = edge faces)"""
    W, H = 128, 256
    a = P.metal(W, H, 701, (50, 54, 64), contrast=0.09, brush=0.65, grain=0.035)
    a = P.scratches(a, 3, 22, 0.12)
    a = P.bevel(a, 4, 0.30, 0.45)
    L = Layer(W, H)
    G = Layer(W, H)
    # hinge strip (left 3 px of the sprite is what the thin faces of the door sample)
    L.rect(0, 0, 24, H, fill=(34, 37, 46, 255))
    L.rect(0, 0, 1.5, H, fill=(255, 255, 255, 40))
    L.rect(22.5, 0, 24, H, fill=(0, 0, 0, 150))
    for yy in (22, 98, 158, 234):
        L.rrect(3, yy - 11, 21, yy + 11, 4, fill=(22, 24, 30, 255), outline=(150, 156, 170, 255), width=1.2)
        L.rrect(6, yy - 7, 18, yy + 7, 3, fill=(96, 102, 116, 255))
        L.rect(6, yy - 1, 18, yy + 1, fill=(30, 32, 40, 255))
    G.line([(12, 36), (12, 84)], CYAN[:3] + (200,), 1.2)
    G.line([(12, 172), (12, 220)], MAG[:3] + (200,), 1.2)
    # left column plates, right column plates
    plates = [(30, 10, 64, 56), (30, 62, 64, 122), (30, 134, 64, 194), (30, 200, 64, 246),
              (70, 10, 100, 56), (70, 62, 100, 122), (70, 134, 100, 164)]
    for (x0, y0, x1, y1) in plates:
        L.rrect(x0, y0, x1, y1, 3.5, fill=(34, 37, 46, 255), outline=(8, 9, 12, 255), width=1.6)
        L.rrect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 2.5, outline=(255, 255, 255, 28), width=0.8)
        for (cx, cy) in ((x0 + 5, y0 + 5), (x1 - 5, y0 + 5), (x0 + 5, y1 - 5), (x1 - 5, y1 - 5)):
            P.screw(L, cx, cy, 1.8)
    # chevrons on the upper-right plate
    for i in range(4):
        y = 18 + i * 9
        G.line([(76, y), (85, y + 6), (94, y)], GOLD[:3] + (200,), 1.3)
    # seam at the join of the two halves (y = 128)
    L.rect(0, 124, W, 126.6, fill=(6, 7, 10, 255))
    L.rect(0, 129.4, W, 132, fill=(6, 7, 10, 255))
    G.rect(0, 126.6, W, 129.4, fill=CYAN[:3] + (255,))
    # bolts along the free edge (right)
    for yy in (40, 100, 156, 216):
        L.rrect(106, yy - 6, 124, yy + 6, 3, fill=(20, 22, 28, 255), outline=(150, 156, 170, 255), width=1.0)
        L.rrect(109, yy - 3, 121, yy + 3, 2, fill=(110, 116, 130, 255))
    # keycard reader (lower half)
    rx0, ry0, rx1, ry1 = 68, 170, 100, 226
    L.rrect(rx0, ry0, rx1, ry1, 4, fill=(8, 9, 13, 255), outline=(170, 176, 190, 255), width=1.6)
    L.rrect(rx0 + 4, ry0 + 5, rx1 - 4, ry0 + 21, 2, fill=(2, 3, 5, 255), outline=CYAN[:3] + (200,), width=0.8)
    G.rect(rx0 + 7, ry0 + 11.5, rx1 - 7, ry0 + 14.5, fill=CYAN[:3] + (235,))
    L.rrect(rx0 + 7, ry0 + 28, rx1 - 7, ry0 + 44, 2, outline=(200, 205, 215, 255), width=1.0)
    L.rect(rx0 + 7, ry0 + 32, rx1 - 7, ry0 + 35, fill=(200, 205, 215, 255))
    G.circle(rx0 + 8.5, ry1 - 5, 2.2, fill=RED[:3] + (255,))
    G.circle(rx1 - 8.5, ry1 - 5, 2.2, fill=(60, 255, 120, 110))
    # emblem in the upper-left plate
    ex, ey = 47, 33
    G.circle(ex, ey, 14, outline=CYAN[:3] + (240,), width=1.5)
    G.circle(ex, ey, 10, outline=MAG[:3] + (200,), width=1.0)
    pts = [(ex + 7 * math.cos(math.radians(60 * k + 30)), ey + 7 * math.sin(math.radians(60 * k + 30))) for k in range(6)]
    G.poly(pts, outline=CYAN[:3] + (230,))
    G.circle(ex, ey, 2.4, fill=MAG[:3] + (255,))
    # warning stripes at the very bottom
    for i in range(-2, 12):
        x = i * 12
        L.poly([(x, 238), (x + 6, 238), (x + 0, 252), (x - 6, 252)], fill=(255, 196, 58, 120))
    L.rect(0, 236.5, W, 238, fill=(0, 0, 0, 160))
    return _finish(a, L, G, glow=[(1, 3.0, 0.8)])


def door_bottom():
    return door_full().crop((0, 128, 128, 256))


def door_top():
    return door_full().crop((0, 0, 128, 128))


def keycard_door_item():
    W = 64
    L = Layer(W, W)
    G = Layer(W, W)
    L.rrect(14, 3, 50, 61, 3, fill=(52, 56, 68, 255), outline=(10, 11, 15, 255), width=1.6)
    L.rrect(17, 6, 47, 31, 2, fill=(34, 37, 46, 255), outline=(8, 9, 12, 255), width=1.0)
    L.rrect(17, 34, 47, 58, 2, fill=(34, 37, 46, 255), outline=(8, 9, 12, 255), width=1.0)
    G.rect(14, 31.6, 50, 33.4, fill=CYAN)
    G.circle(32, 18, 6, outline=CYAN, width=1.2)
    G.circle(32, 18, 2, fill=MAG)
    L.rrect(36, 40, 45, 53, 1.5, fill=(8, 9, 13, 255), outline=(170, 176, 190, 255), width=0.8)
    G.rect(38, 43, 43, 45, fill=CYAN)
    G.circle(40.5, 50, 1.2, fill=RED)
    from PIL import ImageFilter
    out = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    out.alpha_composite(L.done())
    gi = G.done()
    out.alpha_composite(gi.filter(ImageFilter.GaussianBlur(1.6)))
    out.alpha_composite(gi)
    return out


# ---------------------------------------------------------------------------------------------------- omega terminal
def _term(kind, active):
    W = 64
    ca, cb = GOLD, RED
    pw = 255 if active else 110
    if kind == "side":
        a = P.metal(W, W, 801, (36, 28, 26), contrast=0.09, brush=0.6)
        a = P.scratches(a, 5, 9, 0.12)
        a = P.bevel(a, 2, 0.22, 0.42)
        L = Layer(W, W)
        G = Layer(W, W)
        L.rrect(6, 6, W - 6, W - 6, 3, fill=(20, 15, 14, 255), outline=(190, 140, 52, 255), width=1.2)
        # hex lattice
        for r in range(3):
            for c in range(3):
                cx = 16 + c * 16 + (8 if r % 2 else 0) - 4
                cy = 15 + r * 14
                pts = [(cx + 6 * math.cos(math.radians(60 * k + 30)), cy + 6 * math.sin(math.radians(60 * k + 30))) for k in range(6)]
                G.poly(pts, outline=_col(ca if (r + c) % 2 else cb, pw))
        _omega_glyph(G, 32, 52, 5.5, _col(ca, pw), 1.4)
        for cx, cy in ((9, 9), (W - 9, 9), (9, W - 9), (W - 9, W - 9)):
            P.screw(L, cx, cy, 1.6, (200, 150, 60, 255))
        return _finish(a, L, G, glow=[(1, 2.0, 0.9 if active else 0.4)])
    if kind == "top":
        a = P.metal(W, W, 802, (30, 24, 22), contrast=0.08)
        a = P.bevel(a, 3, 0.25, 0.45)
        L = Layer(W, W)
        G = Layer(W, W)
        L.rrect(10, 10, W - 10, W - 10, 4, fill=(6, 5, 6, 255), outline=(190, 140, 52, 255), width=1.4)
        L.rrect(14, 25, W - 14, 39, 2, fill=(1, 1, 2, 255), outline=_col(ca, 255), width=0.9)  # card slot
        G.rect(18, 31, W - 18, 33, fill=_col(ca if active else (150, 90, 20, 255), 255))
        for i in range(5):
            G.rect(16 + i * 8.6, 42, 20 + i * 8.6, 44, fill=_col(cb, pw))
        G.circle(32, 17, 2.4, fill=_col(cb, 255 if active else 120))
        _omega_glyph(G, 32, 52, 4.0, _col(ca, pw), 1.1)
        return _finish(a, L, G, glow=[(1, 2.2, 1.0 if active else 0.45)])
    # base
    a = P.metal(W, W, 803, (24, 20, 20), contrast=0.08)
    a = P.bevel(a, 2, 0.2, 0.45)
    L = Layer(W, W)
    G = Layer(W, W)
    for i in range(-2, 12):
        x = i * 10
        L.poly([(x, 48), (x + 5, 48), (x, 60), (x - 5, 60)], fill=(255, 196, 58, 150))
    L.rect(0, 46.6, W, 48, fill=(0, 0, 0, 160))
    L.rect(0, 60, W, 61.4, fill=(0, 0, 0, 160))
    G.rect(0, 8, W, 10, fill=_col(cb, pw))
    G.rect(0, 14, W, 15, fill=_col(ca, pw))
    return _finish(a, L, G, glow=[(1, 1.8, 0.9 if active else 0.4)])


# ---------------------------------------------------------------------------------------------------- phone stand
def stand_side():
    W = 64
    a = P.metal(W, W, 901, (150, 154, 164), contrast=0.10, brush=0.7)  # polished titanium
    a = P.bevel(a, 2, 0.35, 0.45)
    L = Layer(W, W)
    G = Layer(W, W)
    L.rect(0, 28, W, 36, fill=(12, 13, 17, 255))
    G.rect(0, 30.5, W, 33.5, fill=CYAN)
    G.rect(0, 34, W, 35, fill=MAG[:3] + (200,))
    return _finish(a, L, G, glow=[(1, 2.0, 0.9)])


def stand_top():
    W = 64
    a = P.vgrad(W, W, (24, 26, 34), (10, 11, 16))
    a = a * (0.9 + 0.2 * P.fbm(W, W, 905, 3, 3))[..., None]
    L = Layer(W, W)
    G = Layer(W, W)
    for r, c in ((29, CYAN), (24, MAG), (19, CYAN)):
        G.circle(32, 32, r, outline=_col(c, 190), width=0.9)
    for k in range(24):
        a0 = math.radians(k * 15)
        G.line([(32 + 19 * math.cos(a0), 32 + 19 * math.sin(a0)), (32 + 29 * math.cos(a0), 32 + 29 * math.sin(a0))], _col(CYAN, 70), 0.5)
    # phone footprint cradle
    G.rrect(21, 12, 43, 52, 4, outline=(235, 238, 245, 230), width=1.2)
    G.rect(30, 8, 34, 9.6, fill=(235, 238, 245, 230))
    return _finish(a, L, G, glow=[(1, 2.2, 0.9)])


TEXTURES = []
for _t, _pre in ((1, "delivery_box"), (2, "omega_delivery_box")):
    TEXTURES += [
        ("block/%s_front" % _pre, (lambda t=_t: crate_wall(t, "front"))),
        ("block/%s_side" % _pre, (lambda t=_t: crate_wall(t, "side"))),
        ("block/%s_top" % _pre, (lambda t=_t: crate_top(t))),
        ("block/%s_bottom" % _pre, (lambda t=_t: crate_bottom(t))),
        ("block/%s_inner" % _pre, (lambda t=_t: crate_inner(t))),
        ("block/delivery_box_lid_top_%d" % _t, (lambda t=_t: lid_top(t))),
        ("block/delivery_box_lid_side_%d" % _t, (lambda t=_t: lid_side(t))),
    ]
TEXTURES += [
    ("item/dark_keycard_front", lambda: keycard(1, False)),
    ("item/dark_keycard_back", lambda: keycard(1, True)),
    ("item/omega_keycard_front", lambda: keycard(2, False)),
    ("item/omega_keycard_back", lambda: keycard(2, True)),
    ("block/keycard_door_bottom", door_bottom),
    ("block/keycard_door_top", door_top),
    ("item/keycard_door", keycard_door_item),
    ("block/omega_terminal_side", lambda: _term("side", False)),
    ("block/omega_terminal_top", lambda: _term("top", False)),
    ("block/omega_terminal_base", lambda: _term("base", False)),
    ("block/omega_terminal_side_active", lambda: _term("side", True)),
    ("block/omega_terminal_top_active", lambda: _term("top", True)),
    ("block/omega_terminal_base_active", lambda: _term("base", True)),
    ("block/iphone_stand_side", stand_side),
    ("block/iphone_stand_top", stand_top),
]
