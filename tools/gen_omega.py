#!/usr/bin/env python3
"""
Generates everything visual for boss 2 (Omega Root - a floating, four-armed celestial war-machine):
  * src/main/java/com/darkwebduo/client/render/OmegaRootParts.java   (LayerDefinition, density 2)
  * assets/darkweb_duo/textures/entity/omega_root.png                (diffuse)
  * assets/darkweb_duo/textures/entity/omega_root_glow_{1..5}.png    (emissive, one colourway per boss phase)
  * tools/preview/omega_root_*.png                                    (software preview, not shipped)
Height units: 16 per block. Ground = the root part's pivot. Hit-box 3.4 x 8.6 blocks (138 units); crown tips reach ~142.
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import modelkit as mk  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
JAVA_OUT = ROOT / "src/main/java/com/darkwebduo/client/render/OmegaRootParts.java"
TEX_DIR = ROOT / "src/main/resources/assets/darkweb_duo/textures/entity"
PREVIEW_DIR = ROOT / "tools/preview"

# glow colourways per phase (must match OmegaRootEntity.C1 / C2 and the renderer)
GLOWS = [
    (0xFFD87A, 0x8A5CFF),   # 1 gold / violet
    (0x6FE8FF, 0xFFFFFF),   # 2 ice / white
    (0xFF4FD8, 0xFFD87A),   # 3 magenta / gold
    (0xFF5030, 0xFFB020),   # 4 crimson / amber
    (0xFFFFFF, 0x8AF0FF),   # 5 white-hot / cyan
]


def palette(i):
    g1, g2 = GLOWS[i]
    # pearl armour: base, light, dark, glow1, glow2, hot
    return mk.Pal(0xD6D2EA, 0xFFFFFF, 0x241E3C, g1, g2, 0xFFFFFF)


# ------------------------------------------------------------------------------------------------ extra materials
def _gold_pal(pal):
    g = mk.Pal(0xB8913C, 0xFFE9A8, 0x2E2208, 0x000000, 0x000000)
    g.glow1, g.glow2, g.hot = pal.glow1, pal.glow2, pal.hot
    return g


def paint_gold(W, H, face, rs, pal, seed):
    return mk.paint_armor(W, H, face, rs, _gold_pal(pal), seed, glow_amt=0.18, panel=12, wear=0.8)


def paint_goldglow(W, H, face, rs, pal, seed):
    return mk.paint_armor(W, H, face, rs, _gold_pal(pal), seed, glow_amt=0.95, panel=8, wear=0.5)


def paint_cloth(W, H, face, rs, pal, seed):
    xx, yy = mk._grid(W, H)
    n = mk.fbm(W, H, seed, 4.0, 3)
    folds = 0.5 + 0.5 * np.sin(xx * 0.85 + n * 5.0)
    col = pal.dark[None, None, :] * (0.5 + 1.1 * folds[:, :, None])
    col = col + np.array([0.10, 0.06, 0.18])[None, None, :] * n[:, :, None]
    glow = np.zeros((H, W, 3))
    ex = np.minimum(xx, W - 1 - xx)
    edge = np.clip(1.0 - ex / 1.3, 0, 1)
    hem = np.clip(1.0 - (H - 1 - yy) / 2.0, 0, 1)
    thread = np.maximum(edge, hem)
    gold = np.array([0.80, 0.66, 0.30])
    col = col * (1 - thread[:, :, None] * 0.8) + thread[:, :, None] * gold[None, None, :] * 0.8
    glow += thread[:, :, None] * pal.glow1[None, None, :] * 0.75
    flow = np.clip(np.sin(yy * 0.5 - (seed % 97) * 0.3 + xx * 0.1) * 2 - 1.0, 0, 1) * mk._line_mask(xx, (W - 1) / 2.0, max(1.0, W * 0.18))
    glow += flow[:, :, None] * pal.glow2[None, None, :] * 0.9
    return np.clip(col, 0, 1), np.clip(glow, 0, 1)


def paint_crystal(W, H, face, rs, pal, seed):
    xx, yy = mk._grid(W, H)
    n = mk.fbm(W, H, seed, 5.0, 3)
    facets = np.floor(n * 5.0) / 5.0
    streak = 0.5 + 0.5 * np.sin((xx * 0.6 + yy * 0.35) + facets * 11.0)
    lo = np.array([0.20, 0.12, 0.42])
    hi = np.array([0.72, 0.62, 1.0])
    t = np.clip(0.25 + 0.55 * facets + 0.25 * streak, 0, 1)
    col = lo[None, None, :] * (1 - t[:, :, None]) + hi[None, None, :] * t[:, :, None]
    vein = (np.abs(mk.fbm(W, H, seed + 9, 4.0, 3) - 0.5) < 0.035).astype(np.float64)
    ex = np.minimum(xx, W - 1 - xx)
    ey = np.minimum(yy, H - 1 - yy)
    rim = np.clip(1.0 - np.minimum(ex, ey) / 1.6, 0, 1)
    glow = vein[:, :, None] * pal.glow2[None, None, :] * 0.85 + rim[:, :, None] * pal.glow1[None, None, :] * 0.8
    col = col + rim[:, :, None] * 0.25
    return np.clip(col, 0, 1), np.clip(glow, 0, 1)


def paint_mask(W, H, face, rs, pal, seed):
    xx, yy = mk._grid(W, H)
    n = mk.fbm(W, H, seed, 6.0, 3)
    shade = {"top": 1.1, "bottom": 0.7, "front": 1.0, "back": 0.85, "left": 0.92, "right": 0.92}[face]
    col = np.array([0.88, 0.86, 0.94])[None, None, :] * (0.86 + 0.2 * n[:, :, None]) * shade
    cx, cy = (W - 1) / 2.0, (H - 1) / 2.0
    rr = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    fil = (np.abs(np.sin(rr * 0.7)) < 0.07).astype(np.float64) * (rr > 2)
    gold = np.array([0.82, 0.66, 0.28])
    col = col * (1 - fil[:, :, None] * 0.7) + fil[:, :, None] * gold[None, None, :] * 0.7
    glow = fil[:, :, None] * pal.glow1[None, None, :] * 0.35
    ex = np.minimum(xx, W - 1 - xx)
    ey = np.minimum(yy, H - 1 - yy)
    col = col * (1.0 - np.clip(1.0 - np.minimum(ex, ey) / 1.2, 0, 1)[:, :, None] * 0.45)
    return np.clip(col, 0, 1), np.clip(glow, 0, 1)


EXTRA = {"gold": paint_gold, "goldglow": paint_goldglow, "cloth": paint_cloth, "crystal": paint_crystal, "mask": paint_mask}


# ------------------------------------------------------------------------------------------------ model
def build_model():
    M = mk.Model("omega_root", density=2, tex=1024, tex_h=4096)
    body = M.part("body", None, (0, 12, 0))

    # ------------------------------------------------ pelvis + skirt of tendrils
    pelvis = body.child("pelvis", (0, -50, 0))
    pelvis.box(-9, -6, -7, 18, 12, 14, "armor")
    pelvis.box(-11, -2, -9, 22, 4, 18, "gold")
    pelvis.box(-3, -5, -10.5, 6, 8, 1.5, "core")
    pelvis.box(-14, -4, -5, 3, 14, 10, "armor")
    pelvis.box(11, -4, -5, 3, 14, 10, "armor")
    pelvis.box(-8, 6, -6, 16, 5, 12, "armor")
    pelvis.box(-6, 11, -5, 12, 4, 10, "gold")
    pelvis.box(-4, 15, -4, 8, 5, 8, "armor")
    pelvis.box(-2, 20, -2, 4, 6, 4, "energy")
    NT = 12
    for i in range(NT):
        a = 2 * math.pi * i / NT
        holder = pelvis.child("tn_%d" % i, (math.cos(a) * 10, 6, math.sin(a) * 10), (0, math.pi / 2 - a, 0))
        sa = holder.child("tn_%d_a" % i, (0, 0, 0), (0.4, 0, 0))
        sa.box(-2.5, 0, -2, 5, 16, 4, "cloth")
        sb = sa.child("tn_%d_b" % i, (0, 16, 0), (0.25, 0, 0))
        sb.box(-2, 0, -1.5, 4, 16, 3, "cloth")
        sb.box(-1, 14, -1, 2, 5, 2, "energy" if i % 2 == 0 else "gold")

    # ------------------------------------------------ torso
    torso = pelvis.child("torso", (0, -2, 0))
    torso.box(-9, -14, -6, 18, 14, 12, "armor")
    torso.box(-12, -16, -7, 24, 3, 14, "gold")
    torso.box(-16, -54, -8, 32, 38, 16, "armor")
    torso.box(-7, -42, -9.5, 14, 14, 2, "core")                 # hex reactor window (front)
    torso.box(-10, -45, -10, 20, 3, 2, "gold")
    torso.box(-10, -28, -10, 20, 3, 2, "gold")
    torso.box(-10, -42, -10, 3, 14, 2, "gold")
    torso.box(7, -42, -10, 3, 14, 2, "gold")
    torso.box(-15, -53, -9.5, 8, 10, 2, "armor")
    torso.box(7, -53, -9.5, 8, 10, 2, "armor")
    torso.box(-17, -46, -4, 2, 16, 8, "vent")
    torso.box(15, -46, -4, 2, 16, 8, "vent")
    torso.box(-9, -50, 8, 18, 26, 6, "armor")                   # back reactor housing
    torso.box(-4, -42, 13.5, 8, 8, 1, "core")
    torso.box(-6, -26, 9, 12, 3, 2, "vent")
    torso.box(-13, -58, -7, 26, 4, 14, "gold")                  # collar
    torso.box(-1, -64, -1, 2, 6, 2, "energy")

    # ------------------------------------------------ neck, floating mask, crown
    neck = torso.child("neck", (0, -58, 0))
    neck.box(-3, -4, -3, 6, 8, 6, "cable")
    head = neck.child("head", (0, -8, 0))
    head.box(-7, -6, -6, 14, 12, 12, "mask")
    head.box(-6, -8, -5, 12, 2, 10, "mask")
    head.box(-8, -3, -4, 1, 8, 8, "gold")
    head.box(7, -3, -4, 1, 8, 8, "gold")
    head.box(-1, -4, -7, 2, 9, 1, "energy")                     # vertical light-slit "face"
    head.box(-5.5, -1, -7, 3.5, 1, 1, "energy_h")
    head.box(2, -1, -7, 3.5, 1, 1, "energy_h")
    head.box(-7, -6, 5, 14, 26, 2, "cloth")                     # veil
    jaw = head.child("jaw", (0, 5, -1))
    jaw.box(-5.5, 0, -5, 11, 5, 10, "mask")
    jaw.box(-1, 1, -6, 2, 3, 1, "energy")
    crown = head.child("crown", (0, -8, 2))
    for k in range(7):
        i = k - 3
        L = 12 - abs(i) * 1.5
        blade = crown.child("cr_%d" % k, (0, 0, 0), (0, 0, i * 0.30))
        blade.box(-1.5, -L, -1, 3, L, 2, "gold")
        blade.box(-1, -L - 4, -0.5, 2, 4, 1, "energy")

    # ------------------------------------------------ four arms
    for side, sx in (("l", 1), ("r", -1)):
        arm = torso.child("arm_" + side, (24 * sx, -46, 0))
        arm.box(-5, -2, -5, 10, 26, 10, "armor")
        arm.box(-8, -9, -11, 16, 5, 22, "armor")                # pauldron layers
        arm.box(-6, -14, -9, 12, 5, 18, "gold")
        arm.box(-4, -18, -6, 8, 4, 12, "armor")
        arm.box(-1, -27, -1, 2, 9, 2, "energy")
        arm.box(-6, 10, -6, 12, 3, 12, "gold")
        fore = arm.child("forearm_" + side, (0, 24, 0))
        fore.box(-6, 0, -6, 12, 22, 12, "armor")
        fore.box(-7, 2, -7, 14, 5, 14, "gold")
        fore.box(-1.5, 8, -6.5, 3, 10, 1, "bright")
        fore.box(-5, 10, 5, 10, 8, 3, "vent")
        hand = fore.child("hand_" + side, (0, 22, 0))
        hand.box(-7, 0, -7, 14, 9, 14, "armor")
        hand.box(-6, 1, -9, 12, 3, 3, "gold")
        for fx in (-5, -1.5, 2):
            hand.box(fx, 5, -9, 3, 12, 3, "gold")
            hand.box(fx + 0.5, 16, -10, 2, 5, 2, "energy")
        hand.box(-2, 5, 7, 4, 5, 2, "core")

        larm = torso.child("larm_" + side, (15 * sx, -22, -2))
        larm.box(-3, -2, -3, 6, 18, 6, "armor")
        larm.box(-4, 6, -4, 8, 3, 8, "gold")
        lfore = larm.child("lfore_" + side, (0, 16, 0))
        lfore.box(-2.5, 0, -2.5, 5, 16, 5, "armor")
        lfore.box(-3.5, 2, -3.5, 7, 3, 7, "gold")
        lhand = lfore.child("lhand_" + side, (0, 16, 0))
        lhand.box(-4, 0, -4, 8, 4, 8, "gold")
        lhand.box(-1, 4, -3, 2, 22, 6, "energy")
        lhand.box(-0.5, 26, -1.5, 1, 4, 3, "bright")

    # ------------------------------------------------ six crystal wings
    for side, sx in (("l", 1), ("r", -1)):
        mount = torso.child("wing_" + side, (10 * sx, -42, 9), (0.35, 0.5 * sx, 0))
        mount.box(-3, -4, -3, 6, 8, 6, "armor")
        for i, (L, ang) in enumerate(((56, 0.15), (48, 0.55), (40, 0.95))):
            f = mount.child("wing_%s%d" % (side, i), (0, 0, 0), (0, 0, sx * ang))
            f.box(-1.5, -L, -5, 3, L, 10, "crystal")
            f.box(-0.5, -L + 4, -5.5, 1, L - 8, 1, "energy")

    # ------------------------------------------------ armillary gyroscope rings
    gyro = body.child("gyro", (0, -80, 0))
    rings = [("a", 44, 26, (math.pi / 2, 0, 0)), ("b", 66, 36, (0, math.pi / 2, 0)), ("c", 58, 32, (0.8, 0.6, 0))]
    for key, R, N, tilt in rings:
        t = gyro.child("g%s_t" % key, (0, 0, 0), tilt)
        ring = t.child("g%s" % key, (0, 0, 0))
        for i in range(N):
            a = 2 * math.pi * i / N
            seg = ring.child("g%s_%d" % (key, i), (math.cos(a) * R, math.sin(a) * R, 0), (0, 0, a + math.pi / 2))
            L = round(2 * R * math.sin(math.pi / N) * 1.08 * 2) / 2.0
            if i % 8 == 0:
                seg.box(-3, -2.5, -2.5, 6, 5, 5, "core")
            else:
                seg.box(-L / 2.0, -1, -1.5, L, 2, 3, "gold" if i % 2 == 0 else "goldglow")

    # ------------------------------------------------ orbiting crystals
    orbit = body.child("orbit", (0, -80, 0))
    NS = 10
    for i in range(NS):
        a = 2 * math.pi * i / NS
        r = 52 + (i % 3) * 5
        sh = orbit.child("sh_%d" % i, (math.cos(a) * r, ((i % 3) - 1) * 18, math.sin(a) * r), (0.5 if i % 2 else -0.5, a, 0.3))
        sh.box(-2, -7, -2, 4, 14, 4, "crystal")
        sh.box(-1, -10, -1, 2, 3, 2, "energy")
    return M


def fit_height(M, used):
    h = 128
    while h < used:
        h *= 2
    M.tex_h = h


def main():
    M = build_model()
    used = M.pack()
    fit_height(M, used)
    print("omega model: %d parts, %d boxes, texture rows used %d -> %dx%d" % (len(M.parts), len(M.all_boxes()), used, M.tex, M.tex_h))
    JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUT.write_text(M.emit_java("com.darkwebduo.client.render", "OmegaRootParts"))

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    diffuse, glow1 = mk.paint_model(M, palette(0), EXTRA)
    mk.save_png(diffuse, str(TEX_DIR / "omega_root.png"))
    mk.save_png(glow1, str(TEX_DIR / "omega_root_glow_1.png"))
    for i in range(1, 5):
        _, g = mk.paint_model(M, palette(i), EXTRA)
        mk.save_png(g, str(TEX_DIR / ("omega_root_glow_%d.png" % (i + 1))))
    if "--preview" in sys.argv:
        mk.render_preview(M, diffuse, glow1, str(PREVIEW_DIR / "omega_root_front.png"), yaw=20, pitch=-8)
        mk.render_preview(M, diffuse, glow1, str(PREVIEW_DIR / "omega_root_back.png"), yaw=200, pitch=-8)
        mk.render_preview(M, diffuse, glow1, str(PREVIEW_DIR / "omega_root_side.png"), yaw=100, pitch=-5)
        from PIL import Image
        Image.fromarray(diffuse).resize((512, 512 * M.tex_h // M.tex), Image.NEAREST).save(str(PREVIEW_DIR / "omega_root_atlas.png"))


if __name__ == "__main__":
    main()
