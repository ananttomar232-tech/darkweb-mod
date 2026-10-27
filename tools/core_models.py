#!/usr/bin/env python3
"""
core_models - hand-built 1.20.1 JSON models / blockstates / loot / tags / recipes for the core blocks and items.

build() returns {relative_path: json_object}; paths are relative to src/main/resources (assets/... or data/...).
No vanilla template models are required except the 1.20.1 door templates (block/door_*), minecraft:block/block and builtin/entity.
"""
import math

MOD = "darkweb_duo"
DIRS = ("down", "up", "north", "south", "west", "east")
Y_ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def r(v):
    return round(float(v), 4)


def tx(n):
    return "%s:block/%s" % (MOD, n)


def ix(n):
    return "%s:item/%s" % (MOD, n)


GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def face(tex, uv=None, cull=None, glow=False, rot=None):
    f = {"texture": tex}
    if uv is not None:
        f["uv"] = [r(v) for v in uv]
    if cull:
        f["cullface"] = cull
    if rot:
        f["rotation"] = rot
    if glow:
        f["forge_data"] = dict(GLOW)
    return f


def box(frm, to, tex, omit=(), per=None, uv=None, glow=False, shade=True, glow_dirs=None):
    per = per or {}
    uv = uv or {}
    faces = {}
    for d in DIRS:
        if d in omit:
            continue
        g = glow if glow_dirs is None else (d in glow_dirs)
        faces[d] = face(per.get(d, tex), uv.get(d), glow=g)
    e = {"from": [r(v) for v in frm], "to": [r(v) for v in to], "faces": faces}
    if not shade:
        e["shade"] = False
    return e


class Model:
    def __init__(self, parent="minecraft:block/block", textures=None, ao=True, gui_light=None, display=None):
        self.parent = parent
        self.textures = textures or {}
        self.ao = ao
        self.elements = []
        self.gui_light = gui_light
        self.display = display

    def add(self, e):
        self.elements.append(e)
        return self

    def json(self):
        d = {}
        if self.parent:
            d["parent"] = self.parent
        if not self.ao:
            d["ambientocclusion"] = False
        if self.gui_light:
            d["gui_light"] = self.gui_light
        if self.textures:
            d["textures"] = self.textures
        if self.elements:
            d["elements"] = self.elements
        if self.display:
            d["display"] = self.display
        return d


def display(gui=None, ground=None, fixed=None, tp=None, fp=None, head=None):
    """gui/ground/... are (rotation, translation, scale) tuples; unspecified contexts get sensible block-like defaults"""
    def t(v):
        rot, tr, sc = v
        sc = sc if isinstance(sc, (list, tuple)) else (sc, sc, sc)
        return {"rotation": list(rot), "translation": list(tr), "scale": list(sc)}
    d = {}
    d["gui"] = t(gui or ((30, 225, 0), (0, 0, 0), 0.625))
    d["ground"] = t(ground or ((0, 0, 0), (0, 3, 0), 0.25))
    d["fixed"] = t(fixed or ((0, 0, 0), (0, 0, 0), 0.5))
    d["thirdperson_righthand"] = t(tp or ((75, 45, 0), (0, 2.5, 0), 0.375))
    d["thirdperson_lefthand"] = t(tp or ((75, 45, 0), (0, 2.5, 0), 0.375))
    d["firstperson_righthand"] = t(fp or ((0, 45, 0), (0, 0, 0), 0.4))
    d["firstperson_lefthand"] = t(fp or ((0, 225, 0), (0, 0, 0), 0.4))
    if head:
        d["head"] = t(head)
    return d


# ------------------------------------------------------------------------------------------------------ giant monitor
MONITOR_TEX = {
    "a": tx("monitor_casing_a"), "b": tx("monitor_casing_b"), "edge": tx("monitor_casing_edge"),
    "vent": tx("monitor_casing_vent"), "logo": tx("monitor_back_logo"), "ports": tx("monitor_back_ports"),
    "particle": tx("monitor_casing_a"),
}


def monitor_part(px, py):
    m = Model(textures=dict(MONITOR_TEX), ao=False)
    ow = px == 4   # west outer edge (north-facing model)
    oe = px == 0   # east outer edge
    ot = py == 2
    if (px, py) == (2, 0):
        surf = "#ports"
    elif (px, py) == (2, 1):
        surf = "#logo"
    elif py == 2 and px in (1, 2, 3):
        surf = "#vent"
    else:
        surf = "#a" if (px + py) % 2 == 0 else "#b"
    base = "#a" if (px + py) % 2 == 0 else "#b"

    def build(z0, z1, inset, tex_south):
        x0 = inset if ow else 0
        x1 = 16 - inset if oe else 16
        y0 = 0
        y1 = 16 - inset if ot else 16
        omit = ["north"]
        if py == 0:
            omit.append("down")
        else:
            omit.append("down")
        if not ot:
            omit.append("up")
        if not ow:
            omit.append("west")
        if not oe:
            omit.append("east")
        per = {"up": "#edge", "west": "#edge", "east": "#edge", "south": tex_south}
        if z1 < 16:
            per["south"] = base if tex_south != base else tex_south
        return box((x0, y0, z0), (x1, y1, z1), "#edge", omit=omit, per=per)

    # slab behind the glass, then two stepped humps
    m.add(build(10, 13.5, 0, base))
    m.add(build(13.5, 15, 1.5, base))
    m.add(build(15, 16, 3, surf))
    return m


def monitor_item():
    m = Model(textures={"bezel": tx("monitor_bezel"), "screen": tx("monitor_screen_item"), "casing": tx("monitor_casing_a"),
                        "vent": tx("monitor_casing_vent"), "edge": tx("monitor_casing_edge"), "particle": tx("monitor_bezel")},
              ao=False, display=display(gui=((14, -28, 0), (0, -0.4, 0), 0.72), fixed=((0, 0, 0), (0, 0, 0), 0.55)))
    m.add(box((3, 0, 5), (13, 1, 10.5), "#edge", omit=("down",)))
    m.add(box((7, 1, 6.5), (9, 4.2, 8.5), "#edge"))
    m.add(box((0, 4, 6.4), (16, 13.8, 9.2), "#bezel", per={"north": "#casing"}))
    m.add(box((2, 5.2, 4.6), (14, 12.6, 6.4), "#edge", per={"north": "#vent"}, omit=("south",)))
    m.add(box((0.8, 4.8, 9.2), (15.2, 13.0, 9.35), "#screen", omit=("down", "up", "north", "west", "east"), glow=True,
              uv={"south": (0, 0, 16, 16)}))
    return m


# ------------------------------------------------------------------------------------------------------ giant keyboard
KEY_ROWS = [
    # (width, kind) ; kind: d=dark l=light a=accent g=gap
    [(1, "a"), (0.25, "g")] + [(1, "d")] * 4 + [(0.25, "g")] + [(1, "d")] * 4 + [(0.25, "g")] + [(1, "d")] * 4 + [(0.25, "g"), (1, "l")],
    [(1, "d")] * 13 + [(2, "l")],
    [(1.5, "l")] + [(1, "d")] * 12 + [(1.5, "l")],
    [(1.75, "l")] + [(1, "d")] * 11 + [(2.25, "a")],
    [(2.25, "l")] + [(1, "d")] * 10 + [(2.75, "l")],
    [(1.25, "l")] * 3 + [(6.25, "d")] + [(1.25, "l")] * 4,
]


def keyboard_model(pressed):
    sfx = "_lit" if pressed else ""
    m = Model(textures={
        "base": tx("keyboard_base_lit" if pressed else "keyboard_base"), "side": tx("keyboard_side"),
        "kd": tx("keycap_dark" + sfx), "kl": tx("keycap_light" + sfx), "ka": tx("keycap_accent" + sfx),
        "particle": tx("keyboard_side")}, ao=False,
        display=display(gui=((30, 225, 0), (0, -2.2, 0), 0.72), ground=((0, 0, 0), (0, 3, 0), 0.3), fixed=((90, 0, 0), (0, 0, 0), 0.62)))
    m.add(box((0, 0, 3), (16, 1.6, 13), "#side", omit=("down",)))
    m.add(box((0.4, 1.6, 3.4), (15.6, 2.1, 12.6), "#side", per={"up": "#base"}, omit=("down",), glow=pressed, glow_dirs=("up",)))
    sink = 0.32 if pressed else 0.0
    nrows = len(KEY_ROWS)
    for ri, row in enumerate(KEY_ROWS):
        zc = 11.9 - ri * 1.18
        h = 0.62 + 0.09 * (nrows - 1 - ri) - sink
        x = 0.5
        for (w, kind) in row:
            if kind != "g":
                tex = {"d": "#kd", "l": "#kl", "a": "#ka"}[kind]
                gap = 0.14
                x0, x1 = x + gap / 2.0, x + w - gap / 2.0
                depth = 0.46 if ri == 0 else 0.52
                kuv = {"north": (0, 13, 16, 16), "south": (0, 13, 16, 16), "west": (0, 13, 16, 16), "east": (0, 13, 16, 16),
                       "up": (0, 0, 16, 16)}
                if w >= 1.9:
                    kuv["up"] = (1, 9.5, 15, 14.5)   # blank part of the cap so the legend is not smeared across wide keys
                m.add(box((x0, 2.1, zc - depth), (x1, 2.1 + h, zc + depth), tex,
                          per={}, uv=kuv, omit=("down",), glow=pressed, glow_dirs=("up",) if pressed else ()))
            x += w
    return m


# ------------------------------------------------------------------------------------------------------ giant mouse
def mouse_model(pressed):
    top = "#shell"
    m = Model(textures={
        "shell": tx("mouse_shell_lit" if pressed else "mouse_shell"), "side": tx("mouse_side"), "glow": tx("mouse_glow"),
        "belly": tx("mouse_belly"), "wheel": tx("mouse_wheel"), "cable": tx("mouse_cable"), "particle": tx("mouse_side")},
        ao=False, display=display(gui=((30, 225, 0), (0, -1.5, 0), 0.9), ground=((0, 0, 0), (0, 3, 0), 0.4), fixed=((90, 0, 0), (0, 0, 0), 0.7)))
    cx, cz = 8.0, 8.0
    ax, az, ay = 5.0, 7.0, 4.6
    # belly plate + glowing skirt ring
    m.add(box((3.6, 0, 1.8), (12.4, 0.5, 14.2), "#side", per={"down": "#belly"}, uv={"down": (3.6, 1.8, 12.4, 14.2)}))
    ring_y0, ring_y1 = 0.5, 1.0
    s = 1.0 - (0.75 / ay) ** 2
    hx = ax * math.sqrt(s) + 0.12
    hz = az * math.sqrt(s) + 0.12
    m.add(box((cx - hx, ring_y0, cz - hz), (cx + hx, ring_y1, cz + hz), "#glow", omit=("down",), glow=True, shade=False))
    # elliptical dome built from strips; layer heights follow the profile angle so the top is as smooth as the base
    nl = 13
    th0 = math.asin(1.0 / ay)
    ys = [ay * math.sin(th0 + (math.pi / 2 - th0) * k / float(nl)) for k in range(nl + 1)]
    sw = 1.0
    for li in range(nl):
        y0, y1 = ys[li], ys[li + 1]
        if y1 - y0 < 0.02:
            continue
        ym = (y0 + y1) * 0.5
        s = 1.0 - (ym / ay) ** 2
        if s <= 0.02:
            continue
        zr = az * math.sqrt(s)
        n = max(1, int(math.ceil(2 * zr / sw)))
        w = 2 * zr / n
        for k in range(n):
            za = cz - zr + k * w
            zb = za + w
            zm = (za + zb) / 2.0
            zf = min(abs(za - cz), abs(zb - cz))
            rem = s - (zf / az) ** 2
            if rem <= 0.01:
                continue
            hx = ax * math.sqrt(rem) * (1.0 - 0.14 * ((zm - cz) / az))   # nose (+z) is narrower than the palm end
            if hx < 0.3:
                continue
            m.add(box((cx - hx, y0, za), (cx + hx, y1, zb), "#side", per={"up": top}, omit=["down"],
                      glow=pressed, glow_dirs=("up",) if pressed else ()))
    # scroll wheel in the slot and the cable
    m.add(box((7.35, 3.75, 9.5), (8.65, 4.75, 12.5), "#wheel", glow=True, shade=False, glow_dirs=("up", "west", "east")))
    m.add(box((7.55, 1.0, 14.3), (8.45, 1.7, 16.0), "#cable"))
    return m


# ------------------------------------------------------------------------------------------------------ delivery boxes
def box_model(tier, state):
    pre = "delivery_box" if tier == 1 else "omega_delivery_box"
    m = Model(textures={
        "front": tx(pre + "_front"), "side": tx(pre + "_side"), "top": tx(pre + "_top"), "bottom": tx(pre + "_bottom"),
        "inner": tx(pre + "_inner"), "lidt": tx("delivery_box_lid_top_%d" % tier), "lids": tx("delivery_box_lid_side_%d" % tier),
        "particle": tx(pre + "_side")}, ao=True,
        display=display(gui=((30, 225, 0), (0, -1, 0), 0.7), fixed=((0, 0, 0), (0, -2, 0), 0.6)))
    per = {"north": "#front", "south": "#side", "west": "#side", "east": "#side", "up": "#top", "down": "#bottom"}
    if state == "closed":
        m.add(box((1.5, 0, 1.5), (14.5, 9, 14.5), "#side", per=per))
        m.add(box((1.5, 9, 1.5), (14.5, 11, 14.5), "#lids", per={"up": "#lidt"}, omit=("down",),
                  uv={"up": (0, 0, 16, 16), "north": (0, 0, 16, 2), "south": (0, 0, 16, 2), "west": (0, 0, 16, 2), "east": (0, 0, 16, 2)}))
    else:
        # hollow body: floor + four walls with an inner lining, no lid (the block-entity renderer animates it)
        m.add(box((1.5, 0, 1.5), (14.5, 1.5, 14.5), "#inner", per={"down": "#bottom"}, omit=("north", "south", "west", "east")))
        m.add(box((1.5, 0, 1.5), (14.5, 1.5, 14.5), "#side", per=per, omit=("up", "down")))
        m.add(box((1.5, 1.5, 1.5), (14.5, 9, 2.5), "#side", per={"north": "#front", "south": "#inner", "up": "#top"}, omit=("down", "west", "east")))
        m.add(box((1.5, 1.5, 13.5), (14.5, 9, 14.5), "#side", per={"north": "#inner", "south": "#side", "up": "#top"}, omit=("down", "west", "east")))
        m.add(box((1.5, 1.5, 2.5), (2.5, 9, 13.5), "#side", per={"west": "#side", "east": "#inner", "up": "#top"}, omit=("down", "north", "south")))
        m.add(box((13.5, 1.5, 2.5), (14.5, 9, 13.5), "#side", per={"east": "#side", "west": "#inner", "up": "#top"}, omit=("down", "north", "south")))
        # little cushion that the card rests on
        m.add(box((5.5, 1.5, 5.5), (10.5, 2.3, 10.5), "#inner", omit=("down",)))
    # corner posts
    for (x, z) in ((1.1, 1.1), (13.9, 1.1), (1.1, 13.9), (13.9, 13.9)):
        m.add(box((x, 0, z), (x + 1.0, 9.4 if state == "closed" else 9.4, z + 1.0), "#top", omit=("down",)))
    return m


# ------------------------------------------------------------------------------------------------------ keycard items
def keycard_item(name):
    f, b = ix(name + "_front"), ix(name + "_back")
    m = Model(parent=None, textures={"front": f, "back": b, "particle": f}, ao=False, gui_light="front",
              display=display(gui=((8, -24, -5), (0, 0, 0), 1.0), ground=((90, 0, 0), (0, 1, 0), 0.5),
                              fixed=((0, 0, 0), (0, 0, 0), 0.5), tp=((0, 0, 0), (0, 2, 1), 0.55), fp=((0, -90, 25), (1.13, 3.2, 1.13), 0.68)))
    m.add({"from": [0, 3, 7.6], "to": [16, 13, 8.4], "faces": {
        "south": face("#front", (0, 0, 16, 16)),
        "north": face("#back", (16, 0, 0, 16)),
        "up": face("#front", (0, 0, 16, 0.8)),
        "down": face("#front", (0, 15.2, 16, 16)),
        "west": face("#front", (0, 0, 0.8, 16)),
        "east": face("#front", (15.2, 0, 16, 16))}})
    return m


# ------------------------------------------------------------------------------------------------------ terminal
def terminal_model(active):
    sfx = "_active" if active else ""
    m = Model(textures={"side": tx("omega_terminal_side" + sfx), "top": tx("omega_terminal_top" + sfx),
                        "base": tx("omega_terminal_base" + sfx), "particle": tx("omega_terminal_base" + sfx)}, ao=False,
              display=display(gui=((30, 225, 0), (0, -1.5, 0), 0.62)))
    m.add(box((0.5, 0, 0.5), (15.5, 3, 15.5), "#base", omit=()))
    m.add(box((2.5, 3, 2.5), (13.5, 12, 13.5), "#side", omit=("down", "up"),
              uv={"north": (2.5, 3.5, 13.5, 12.5), "south": (2.5, 3.5, 13.5, 12.5), "west": (2.5, 3.5, 13.5, 12.5), "east": (2.5, 3.5, 13.5, 12.5)},
              glow=active, glow_dirs=("north", "south", "west", "east") if active else ()))
    m.add(box((1, 12, 1), (15, 15, 15), "#base", per={"up": "#top"},
              uv={"up": (1, 1, 15, 15), "north": (1, 2, 15, 5), "south": (1, 2, 15, 5), "west": (1, 2, 15, 5), "east": (1, 2, 15, 5)},
              glow=active, glow_dirs=("up",) if active else ()))
    # corner fins
    for (x, z) in ((1.5, 1.5), (12.5, 1.5), (1.5, 12.5), (12.5, 12.5)):
        m.add(box((x, 3, z), (x + 2, 12, z + 2), "#base", omit=("down", "up"),
                  uv={"north": (0, 0, 2, 9), "south": (0, 0, 2, 9), "west": (0, 0, 2, 9), "east": (0, 0, 2, 9)}))
    return m


# ------------------------------------------------------------------------------------------------------ phone stand
def phone_stand():
    m = Model(textures={"side": tx("iphone_stand_side"), "top": tx("iphone_stand_top"), "particle": tx("iphone_stand_side")}, ao=False)
    m.add(box((1.5, 0, 1.5), (14.5, 1.2, 14.5), "#side", per={"up": "#top"}, omit=("down",), uv={"up": (0, 0, 16, 16)}, glow_dirs=("up",), glow=True))
    # polished titanium frame (four bars) around the levitation field
    m.add(box((1.5, 1.2, 1.5), (14.5, 2.0, 2.5), "#side", omit=("down",)))
    m.add(box((1.5, 1.2, 13.5), (14.5, 2.0, 14.5), "#side", omit=("down",)))
    m.add(box((1.5, 1.2, 2.5), (2.5, 2.0, 13.5), "#side", omit=("down",)))
    m.add(box((13.5, 1.2, 2.5), (14.5, 2.0, 13.5), "#side", omit=("down",)))
    # four emitter pins
    for (x, z) in ((3.4, 3.4), (11.6, 3.4), (3.4, 11.6), (11.6, 11.6)):
        m.add(box((x, 1.2, z), (x + 1.0, 2.6, z + 1.0), "#side", omit=("down",)))
    return m


# ------------------------------------------------------------------------------------------------------ blockstates
def monitor_state():
    v = {}
    for f, y in Y_ROT.items():
        for px in range(5):
            for py in range(3):
                e = {"model": "%s:block/giant_monitor_part_%d_%d" % (MOD, px, py)}
                if y:
                    e["y"] = y
                v["facing=%s,px=%d,py=%d" % (f, px, py)] = e
    return {"variants": v}


def facing_state(model_fmt, props):
    """props: list of (name, [values]) besides facing; model_fmt(**kw) -> model name"""
    v = {}
    combos = [{}]
    for name, vals in props:
        combos = [dict(c, **{name: val}) for c in combos for val in vals]
    for f, y in Y_ROT.items():
        for c in combos:
            key = ",".join(["facing=" + f] + ["%s=%s" % (k, str(c[k]).lower()) for k in c])
            e = {"model": "%s:block/%s" % (MOD, model_fmt(c))}
            if y:
                e["y"] = y
            v[key] = e
    return {"variants": v}


def door_state():
    v = {}
    table = {
        # (facing, hinge, open) -> y
        ("east", "left", False): 0, ("south", "left", False): 90, ("west", "left", False): 180, ("north", "left", False): 270,
        ("east", "left", True): 90, ("south", "left", True): 180, ("west", "left", True): 270, ("north", "left", True): 0,
        ("east", "right", False): 0, ("south", "right", False): 90, ("west", "right", False): 180, ("north", "right", False): 270,
        ("east", "right", True): 270, ("south", "right", True): 0, ("west", "right", True): 90, ("north", "right", True): 180,
    }
    for half, hname in (("lower", "bottom"), ("upper", "top")):
        for (facing, hinge, opn), y in table.items():
            name = "keycard_door_%s_%s%s" % (hname, hinge, "_open" if opn else "")
            e = {"model": "%s:block/%s" % (MOD, name)}
            if y:
                e["y"] = y
            v["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, str(opn).lower())] = e
    return {"variants": v}


def door_models():
    out = {}
    for hname in ("bottom", "top"):
        for hinge in ("left", "right"):
            for opn in (False, True):
                name = "keycard_door_%s_%s%s" % (hname, hinge, "_open" if opn else "")
                out["assets/%s/models/block/%s.json" % (MOD, name)] = {
                    "parent": "minecraft:block/door_%s_%s%s" % (hname, hinge, "_open" if opn else ""),
                    "textures": {"bottom": tx("keycard_door_bottom"), "top": tx("keycard_door_top")}}
    return out


# ------------------------------------------------------------------------------------------------------ data
ALL_BLOCKS = ["giant_monitor", "giant_keyboard", "giant_mouse", "delivery_box", "omega_delivery_box", "keycard_door", "omega_terminal", "iphone_duo"]


def loot_self(block, extra=None, conditions=None):
    pool = {"rolls": 1.0, "bonus_rolls": 0.0,
            "entries": [dict({"type": "minecraft:item", "name": "%s:%s" % (MOD, block)}, **(extra or {}))],
            "conditions": (conditions or []) + [{"condition": "minecraft:survives_explosion"}]}
    return {"type": "minecraft:block", "pools": [pool]}


def recipes():
    out = {}

    def shaped(name, pattern, key, result, count=1):
        out["data/%s/recipes/%s.json" % (MOD, name)] = {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
            "result": {"item": "%s:%s" % (MOD, result), "count": count}}

    shaped("giant_monitor", ["GGG", "GRG", "III"],
           {"G": {"item": "minecraft:glass_pane"}, "R": {"item": "minecraft:redstone_block"}, "I": {"item": "minecraft:iron_block"}}, "giant_monitor")
    shaped("giant_keyboard", ["BBB", "SSS", "III"],
           {"B": {"item": "minecraft:stone_button"}, "S": {"item": "minecraft:smooth_stone_slab"}, "I": {"item": "minecraft:iron_ingot"}}, "giant_keyboard")
    shaped("giant_mouse", [" B ", "ISI", " I "],
           {"B": {"item": "minecraft:stone_button"}, "S": {"item": "minecraft:redstone"}, "I": {"item": "minecraft:iron_ingot"}}, "giant_mouse")
    shaped("delivery_box", ["PPP", "PEP", "PPP"],
           {"P": {"item": "minecraft:paper"}, "E": {"item": "minecraft:ender_pearl"}}, "delivery_box")
    return out


def tags():
    mod = lambda n: "%s:%s" % (MOD, n)
    return {
        "data/minecraft/tags/blocks/mineable/pickaxe.json": {"replace": False, "values": [mod(b) for b in ALL_BLOCKS]},
        "data/minecraft/tags/blocks/needs_iron_tool.json": {"replace": False, "values": [mod("keycard_door")]},
    }


# ------------------------------------------------------------------------------------------------------ build
def build():
    o = {}
    A = "assets/%s/" % MOD

    def put(path, obj):
        o[path] = obj.json() if isinstance(obj, Model) else obj

    # monitor
    for px in range(5):
        for py in range(3):
            put(A + "models/block/giant_monitor_part_%d_%d.json" % (px, py), monitor_part(px, py))
    put(A + "blockstates/giant_monitor.json", monitor_state())
    put(A + "models/item/giant_monitor.json", monitor_item())
    # keyboard
    put(A + "models/block/giant_keyboard.json", keyboard_model(False))
    put(A + "models/block/giant_keyboard_pressed.json", keyboard_model(True))
    put(A + "blockstates/giant_keyboard.json",
        facing_state(lambda c: "giant_keyboard_pressed" if c["pressed"] == "true" or c["pressed"] is True else "giant_keyboard",
                     [("pressed", [False, True])]))
    put(A + "models/item/giant_keyboard.json", {"parent": "%s:block/giant_keyboard" % MOD})
    # mouse
    put(A + "models/block/giant_mouse.json", mouse_model(False))
    put(A + "models/block/giant_mouse_pressed.json", mouse_model(True))
    put(A + "blockstates/giant_mouse.json",
        facing_state(lambda c: "giant_mouse_pressed" if c["pressed"] in ("true", True) else "giant_mouse", [("pressed", [False, True])]))
    put(A + "models/item/giant_mouse.json", {"parent": "%s:block/giant_mouse" % MOD})
    # delivery boxes
    for tier, name in ((1, "delivery_box"), (2, "omega_delivery_box")):
        put(A + "models/block/%s.json" % name, box_model(tier, "closed"))
        put(A + "models/block/%s_open.json" % name, box_model(tier, "open"))
        put(A + "blockstates/%s.json" % name,
            facing_state(lambda c, n=name: n + "_open" if c["open"] in ("true", True) else n, [("open", [False, True])]))
        put(A + "models/item/%s.json" % name, {"parent": "%s:block/%s" % (MOD, name)})
    # keycards
    put(A + "models/item/dark_keycard.json", keycard_item("dark_keycard"))
    put(A + "models/item/omega_keycard.json", keycard_item("omega_keycard"))
    # door
    for p, obj in door_models().items():
        o[p] = obj
    put(A + "blockstates/keycard_door.json", door_state())
    put(A + "models/item/keycard_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": ix("keycard_door")}})
    # terminal
    put(A + "models/block/omega_terminal.json", terminal_model(False))
    put(A + "models/block/omega_terminal_active.json", terminal_model(True))
    put(A + "blockstates/omega_terminal.json", {"variants": {
        "active=false": {"model": "%s:block/omega_terminal" % MOD}, "active=true": {"model": "%s:block/omega_terminal_active" % MOD}}})
    put(A + "models/item/omega_terminal.json", {"parent": "%s:block/omega_terminal" % MOD})
    # phone stand + builtin entity item
    put(A + "models/block/iphone_duo.json", phone_stand())
    put(A + "blockstates/iphone_duo.json", facing_state(lambda c: "iphone_duo", [("open", [False, True])]))
    put(A + "models/item/iphone_duo.json", {
        "parent": "minecraft:builtin/entity",
        "display": display(gui=((18, -34, 0), (0, 0, 0), 0.8), ground=((0, 0, 0), (0, 2, 0), 0.4), fixed=((0, 180, 0), (0, 0, 0), 0.8),
                           tp=((0, 0, 0), (0, 3, 0), 0.55), fp=((0, -20, 0), (0, 1, 0), 0.7))})
    # loot tables
    for b in ALL_BLOCKS:
        if b == "giant_monitor":
            o["data/%s/loot_tables/blocks/%s.json" % (MOD, b)] = {"type": "minecraft:block", "pools": []}
        elif b == "keycard_door":
            o["data/%s/loot_tables/blocks/%s.json" % (MOD, b)] = loot_self(
                b, conditions=[{"condition": "minecraft:block_state_property", "block": "%s:%s" % (MOD, b), "properties": {"half": "lower"}}])
        elif b == "iphone_duo":
            o["data/%s/loot_tables/blocks/%s.json" % (MOD, b)] = loot_self(
                b, extra={"functions": [{"function": "minecraft:copy_nbt", "source": "block_entity",
                                         "ops": [{"source": "Color", "target": "Color", "op": "replace"}]}]})
        else:
            o["data/%s/loot_tables/blocks/%s.json" % (MOD, b)] = loot_self(b)
    o.update(recipes())
    o.update(tags())
    return o


if __name__ == "__main__":
    import json
    res = build()
    print(len(res), "files,", sum(len(json.dumps(v)) for v in res.values()), "bytes")
