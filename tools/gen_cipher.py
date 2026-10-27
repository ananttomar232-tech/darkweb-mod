#!/usr/bin/env python3
"""
Generates everything visual for boss 1 (the Cipher Warden):
  * src/main/java/com/darkwebduo/client/render/CipherWardenParts.java   (LayerDefinition, density 2)
  * assets/darkweb_duo/textures/entity/cipher_warden.png                (diffuse)
  * assets/darkweb_duo/textures/entity/cipher_warden_glow_{1,2,3}.png   (emissive, one palette per boss phase)
  * tools/preview/cipher_warden_*.png                                   (software preview, not shipped)
Model space: y grows downward, ground is y=24 for the root part, front is -z, +x is the boss's left.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import modelkit as mk  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
JAVA_OUT = ROOT / "src/main/java/com/darkwebduo/client/render/CipherWardenParts.java"
TEX_DIR = ROOT / "src/main/resources/assets/darkweb_duo/textures/entity"
PREVIEW_DIR = ROOT / "tools/preview"

PHASE_PALETTES = [
    # base, light, dark, glow1, glow2, hot
    mk.Pal(0x2A3140, 0x8FA3C4, 0x10131C, 0x19E6FF, 0xFF2BD6, 0xE8FFFF),   # phase 1: cyan / magenta
    mk.Pal(0x2E2640, 0xA48FC4, 0x120F1C, 0xFF2BD6, 0xA040FF, 0xFFE8FA),   # phase 2: magenta / violet
    mk.Pal(0x40262A, 0xC49A8F, 0x1C1012, 0xFF4030, 0xFFB020, 0xFFF3D0),   # phase 3: crimson / gold
]


def build_model():
    M = mk.Model("cipher_warden", density=2, tex=1024, tex_h=512)

    body = M.part("body", None, (0, 12, 0))   # x D=2 -> 24 = vanilla ground line (the renderer scales by 1/D inside the vanilla 1.501 offset)
    hip = body.child("hip", (0, -44, 0))
    hip.box(-10, -7, -6, 20, 12, 12, "armor")
    hip.box(-6, -3, -8, 12, 10, 3, "armor")           # belt buckle plate
    hip.box(-3, 0, -8.5, 6, 5, 1, "core")             # belt gem
    hip.box(-5, 5, -7, 10, 13, 2, "armor")            # tasset front
    hip.box(-5, 5, 5, 10, 13, 2, "armor")             # tasset back
    hip.box(-12, 2, -4, 2, 12, 8, "armor")            # tasset right
    hip.box(10, 2, -4, 2, 12, 8, "armor")             # tasset left

    # ------------------------------------------------ legs
    for side, sx in (("l", 1), ("r", -1)):
        thigh = hip.child("thigh_" + side, (8 * sx, 2, 0))
        thigh.box(-5, 0, -5, 10, 20, 10, "armor")
        thigh.box(-6, 1, -7, 12, 12, 3, "armor")
        thigh.box(-1 + 5.5 * sx - 1, 4, -4, 2, 12, 8, "bright")      # glowing side strip
        shin = thigh.child("shin_" + side, (0, 20, 0))
        shin.box(-4, 0, -4, 8, 18, 8, "armor")
        shin.box(-5, -3, -8, 10, 8, 5, "armor")                    # knee guard
        shin.box(-1.5, -1, -9, 3, 3, 1, "core")
        shin.box(-5, 5, -6, 10, 12, 3, "armor")                    # greave
        shin.box(-3, 4, 4, 6, 12, 3, "vent")                       # calf vent
        foot = shin.child("foot_" + side, (0, 18, 0))
        foot.box(-5, 0, -8, 10, 4, 14, "armor")
        foot.box(-2, 1, 6, 4, 3, 3, "armor")
        for i, tx in enumerate((-4.0, -1.0, 2.0)):
            foot.box(tx, 1, -11, 2, 2, 3, "bone")

    # ------------------------------------------------ cable tail (6 segments)
    prev = hip.child("tail_0", (0, 2, 6), (-0.35, 0, 0))
    prev.box(-3, -3, 0, 6, 6, 8, "armor")
    for i in range(1, 6):
        seg = prev.child("tail_%d" % i, (0, 0, 8), (0.16, 0, 0))
        s = round((5 - i * 0.6) * 2) / 2.0
        seg.box(-s / 2.0, -s / 2.0, 0, s, s, 8, "cable" if i < 5 else "energy")
        prev = seg

    # ------------------------------------------------ torso
    torso = hip.child("torso", (0, -5, 0))
    torso.box(-8, -8, -5, 16, 13, 10, "armor")                   # abdomen
    torso.box(-15, -34, -8, 30, 27, 16, "armor")                 # chest
    torso.box(-6, -28, -9.5, 12, 12, 2, "core")                  # chest core window
    torso.box(-14, -33, -10, 13, 11, 3, "armor")                 # pectoral R
    torso.box(1, -33, -10, 13, 11, 3, "armor")                   # pectoral L
    torso.box(-13, -21, -9, 4, 12, 2, "armor")
    torso.box(9, -21, -9, 4, 12, 2, "armor")
    for i in range(3):
        torso.box(-8, -14 + i * 4, -8, 16, 2, 2, "bright" if i == 1 else "armor")   # rib slats
    torso.box(-16, -22, -4, 2, 14, 8, "vent")
    torso.box(14, -22, -4, 2, 14, 8, "vent")
    torso.box(-8, -30, 8, 16, 22, 8, "armor")                    # back reactor
    torso.box(-4, -26, 15.5, 8, 8, 1, "core")
    torso.box(-6, -12, 9, 12, 3, 2, "vent")
    torso.box(-1, -37, 2, 2, 4, 8, "armor")                      # spine ridge
    torso.box(-1, -34, 8, 2, 6, 6, "armor")
    torso.box(-11, -36, -6, 22, 3, 12, "armor")                  # collar
    torso.box(-1, -38, -9, 2, 4, 1, "energy")

    # ------------------------------------------------ neck + head
    neck = torso.child("neck", (0, -34, 0))
    neck.box(-4, -5, -4, 8, 6, 8, "cable")
    head = neck.child("head", (0, -4, 0))
    head.box(-7, -12, -8, 14, 12, 14, "armor")
    head.box(-8, -5, -6, 1, 5, 9, "armor")
    head.box(7, -5, -6, 1, 5, 9, "armor")
    head.box(-6, -13, -7, 12, 2, 12, "armor")                    # brow plate
    head.box(-5.5, -8, -9, 4, 2, 1, "energy_h")                  # eye R
    head.box(1.5, -8, -9, 4, 2, 1, "energy_h")                   # eye L
    head.box(-1.5, -11, -9, 3, 3, 1, "core")                     # forehead gem
    head.box(-4, -4, -9, 8, 1, 1, "vent")                        # mouth grill (upper)
    head.box(-1, -14, 1, 2, 8, 8, "armor")                       # head crest
    jaw = head.child("jaw", (0, -3, 2))
    jaw.box(-5, 0, -10, 10, 4, 11, "armor")
    jaw.box(-4, -1, -10, 1, 2, 1, "bone")
    jaw.box(3, -1, -10, 1, 2, 1, "bone")
    jaw.box(-1, -1, -10, 2, 2, 1, "bone")
    for side, sx in (("l", 1), ("r", -1)):
        horn = head.child("horn_" + side, (6 * sx, -11, 1), (0, 0, 0.55 * sx))
        horn.box(-2, -12, -2, 4, 12, 4, "bone")
        tip = horn.child("horn_tip_" + side, (0, -12, 0), (0, 0, -0.5 * sx))
        tip.box(-1.5, -10, -1.5, 3, 10, 3, "bone")

    # ------------------------------------------------ halo (two counter-rotating rings behind the head)
    halo = neck.child("halo", (0, -22, 7))
    n_out = 16
    for i in range(n_out):
        a = 2 * math.pi * i / n_out
        seg = halo.child("halo_a%d" % i, (math.cos(a) * 20, math.sin(a) * 20, 0), (0, 0, a + math.pi / 2))
        seg.box(-4.5, -1.5, -1.5, 9, 3, 3, "energy" if i % 2 == 0 else "bright")
    halo2 = neck.child("halo2", (0, -22, 10))
    n_in = 10
    for i in range(n_in):
        a = 2 * math.pi * i / n_in
        seg = halo2.child("halo_b%d" % i, (math.cos(a) * 12, math.sin(a) * 12, 0), (0, 0, a + math.pi / 2))
        seg.box(-4, -1, -1, 8, 2, 2, "energy")

    # ------------------------------------------------ wings of data shards
    for side, sx in (("l", 1), ("r", -1)):
        wing = torso.child("wing_" + side, (9 * sx, -26, 10), (0.25, 0.5 * sx, 0))
        wing.box(-2, -3, -2, 4, 6, 4, "armor")
        for i in range(5):
            ang = sx * (0.28 + i * 0.30)
            feather = wing.child("feather_%s%d" % (side, i), (0, 0, 0), (0, 0, ang))
            length = 26 + i * 4 if i < 3 else 26 + (4 - i) * 5 + 8
            feather.box(-1, -length, -4, 2, length, 8, "shard")

    # ------------------------------------------------ arms
    for side, sx in (("l", 1), ("r", -1)):
        arm = torso.child("arm_" + side, (21 * sx, -29, 0))
        arm.box(-5, 0, -5, 10, 20, 10, "armor")
        arm.box(-5, -7, -10, 11 if sx > 0 else 11, 10, 20, "armor")           # pauldron
        arm.box(-4 + 2 * sx - 1, -10, -6, 3, 6, 3, "bone")                       # spikes
        arm.box(-4 + 2 * sx - 1, -10, 0, 3, 6, 3, "bone")
        arm.box(-4 + 2 * sx - 1, -12, -3, 3, 8, 3, "bone")
        arm.box(-6, 4, -6, 12, 3, 12, "armor")                                   # elbow ring
        fore = arm.child("forearm_" + side, (0, 20, 0))
        fore.box(-6, 0, -6, 12, 20, 12, "armor")
        fore.box(-7, 2, -7, 14, 6, 14, "armor")
        fore.box(-1.5, 4, -7.5, 3, 12, 1, "bright")                              # glowing vambrace strip
        fore.box(-5, 8, 6, 10, 10, 3, "vent")
        hand = fore.child("hand_" + side, (0, 20, 0))
        hand.box(-7, 0, -7, 14, 11, 14, "armor")
        hand.box(-6, 1, -9, 12, 4, 3, "armor")                                    # knuckle plate
        for i, fx in enumerate((-5.0, -1.0, 3.0)):
            hand.box(fx, 5, -11, 3, 12, 4, "bone")                                # fingers
            hand.box(fx + 0.5, 15, -12, 2, 5, 3, "bone")                          # claws
        hand.box(-2, 6, 7, 4, 5, 2, "core")                                       # palm core (back of hand)
    return M


def main():
    M = build_model()
    used = M.pack()
    print("cipher model: %d parts, %d boxes, texture rows used %d / %d" % (len(M.parts), len(M.all_boxes()), used, M.tex))
    JAVA_OUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_OUT.write_text(M.emit_java("com.darkwebduo.client.render", "CipherWardenParts"))

    TEX_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    diffuse, glow1 = mk.paint_model(M, PHASE_PALETTES[0])
    mk.save_png(diffuse, str(TEX_DIR / "cipher_warden.png"))
    mk.save_png(glow1, str(TEX_DIR / "cipher_warden_glow_1.png"))
    for i in (1, 2):
        _, g = mk.paint_model(M, PHASE_PALETTES[i])
        mk.save_png(g, str(TEX_DIR / ("cipher_warden_glow_%d.png" % (i + 1))))
    if "--preview" in sys.argv:
        mk.render_preview(M, diffuse, glow1, str(PREVIEW_DIR / "cipher_warden_front.png"), yaw=20, pitch=-8)
        mk.render_preview(M, diffuse, glow1, str(PREVIEW_DIR / "cipher_warden_back.png"), yaw=200, pitch=-8)
        mk.render_preview(M, diffuse, glow1, str(PREVIEW_DIR / "cipher_warden_side.png"), yaw=100, pitch=-5)
        import numpy as np
        from PIL import Image
        Image.fromarray(diffuse).resize((512, 512), Image.NEAREST).save(str(PREVIEW_DIR / "cipher_warden_atlas.png"))


if __name__ == "__main__":
    main()
