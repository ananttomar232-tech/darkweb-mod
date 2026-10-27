#!/usr/bin/env python3
"""
Tiny software renderer for 1.20.1 block-model JSON (elements/faces/uv, no rotation) so generated models can be eyeballed
without Minecraft. Painter's algorithm, back-face culling, per-direction shading, nearest texture sampling.

usage as a module:  render(models, yaw, pitch, size, out)   models = [(json_obj, (ox,oy,oz) offset in px, y_rot_deg)]
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/darkweb_duo/textures"
_cache = {}

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}

# (corner picker, uv corner) per direction following FaceInfo
def corners(d, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if d == "down":
        return [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]
    if d == "up":
        return [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)]
    if d == "north":
        return [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)]
    if d == "south":
        return [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)]
    if d == "west":
        return [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)]
    return [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)]


NORMAL = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def auto_uv(d, f, t):
    if d == "down":
        return [f[0], 16 - t[2], t[0], 16 - f[2]]
    if d == "up":
        return [f[0], f[2], t[0], t[2]]
    if d == "north":
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if d == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if d == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]


def load_tex(ref):
    ns, _, path = ref.partition(":")
    if ref in _cache:
        return _cache[ref]
    p = TEX / (path + ".png")
    im = np.asarray(Image.open(p).convert("RGBA"), np.float32) if p.exists() else None
    _cache[ref] = im
    return im


def quads_of(model, offset=(0, 0, 0), yrot=0):
    tex = model.get("textures", {})
    out = []
    for e in model.get("elements", []):
        f, t = e["from"], e["to"]
        for d, face in e["faces"].items():
            ref = face["texture"]
            if ref.startswith("#"):
                ref = tex.get(ref[1:])
            if ref is None or ref.startswith("#"):
                continue
            img = load_tex(ref)
            if img is None:
                continue
            uv = face.get("uv") or auto_uv(d, f, t)
            cs = corners(d, f, t)
            ang = math.radians(yrot)
            pts = []
            for (x, y, z) in cs:
                x -= 8
                z -= 8
                rx = x * math.cos(ang) + z * math.sin(ang)
                rz = -x * math.sin(ang) + z * math.cos(ang)
                pts.append((rx + 8 + offset[0], y + offset[1], rz + 8 + offset[2]))
            n = NORMAL[d]
            nrx = n[0] * math.cos(ang) + n[2] * math.sin(ang)
            nrz = -n[0] * math.sin(ang) + n[2] * math.cos(ang)
            out.append((pts, uv, img, (nrx, n[1], nrz), SHADE[d] if e.get("shade", True) else 1.0, "forge_data" in face))
    return out


def render(items, yaw=35.0, pitch=28.0, size=512, out=None, bg=(70, 72, 80), center=None, span=None):
    quads = []
    for (m, off, yr) in items:
        quads += quads_of(m, off, yr)
    ya, pa = math.radians(yaw), math.radians(pitch)
    # camera basis (looking at the centre from yaw/pitch; yaw 0 = looking toward -z from +z... i.e. we stand at +z? we choose: yaw rotates about Y)
    fwd = np.array([-math.sin(ya) * math.cos(pa), -math.sin(pa), -math.cos(ya) * math.cos(pa)])
    right = np.array([math.cos(ya), 0, -math.sin(ya)])
    up = np.cross(right, fwd)
    allp = np.array([p for q in quads for p in q[0]])
    if center is None:
        center = (allp.min(0) + allp.max(0)) / 2
    center = np.array(center, float)
    if span is None:
        proj = np.stack([(allp - center) @ right, (allp - center) @ up], 1)
        span = float(np.abs(proj).max()) * 2.15
    S = 2
    W = H = size * S
    zbuf = np.full((H, W), 1e9, np.float32)
    img = np.zeros((H, W, 3), np.float32)
    img[:] = np.array(bg, np.float32)
    scale = W / span
    # sort back to front only needed for translucency; z-buffer handles the rest
    for (pts, uv, tex, nrm, shade, glow) in quads:
        n = np.array(nrm, float)
        if n @ fwd > 0.0:   # facing away from camera
            continue
        P3 = [np.array(p, float) for p in pts]
        sc = [((((p - center) @ right) * scale) + W / 2, (-((p - center) @ up) * scale) + H / 2, float((p - center) @ fwd)) for p in P3]
        # affine uv: corner0->(u0,v0) corner1->(u0,v1) corner2->(u1,v1) corner3->(u1,v0)
        uvs = [(uv[0], uv[1]), (uv[0], uv[3]), (uv[2], uv[3]), (uv[2], uv[1])]
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = [sc[i] for i in tri]
            ua, ub, uc = [uvs[i] for i in tri]
            minx = int(max(0, math.floor(min(a[0], b[0], c[0]))))
            maxx = int(min(W - 1, math.ceil(max(a[0], b[0], c[0]))))
            miny = int(max(0, math.floor(min(a[1], b[1], c[1]))))
            maxy = int(min(H - 1, math.ceil(max(a[1], b[1], c[1]))))
            if minx > maxx or miny > maxy:
                continue
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            l1 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
            l2 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
            l3 = 1 - l1 - l2
            m = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
            if not m.any():
                continue
            z = l1 * a[2] + l2 * b[2] + l3 * c[2]
            zb = zbuf[miny:maxy + 1, minx:maxx + 1]
            m &= z < zb + 1e-6
            if not m.any():
                continue
            u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0]
            v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1]
            px = np.clip((u / 16.0 * tw).astype(int), 0, tw - 1)
            py = np.clip((v / 16.0 * th).astype(int), 0, th - 1)
            col = tex[py, px]
            al = col[..., 3:4] / 255.0
            k = 1.0 if glow else shade
            rgb = col[..., :3] * k
            region = img[miny:maxy + 1, minx:maxx + 1]
            mm = m & (col[..., 3] > 8)
            region[mm] = rgb[mm] * al[mm] + region[mm] * (1 - al[mm])
            zb[mm] = z[mm]
    res = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)
    if out:
        res.save(out)
    return res
