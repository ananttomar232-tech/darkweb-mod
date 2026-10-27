#!/usr/bin/env python3
"""
gen_core_assets - textures + models + blockstates + loot tables + recipes + tags + language file for the core blocks/items
(giant monitor/keyboard/mouse, delivery boxes, keycards, keycard door, omega terminal, iPhone Duo stand).

Also merges every tools/lang/*.json fragment into assets/darkweb_duo/lang/en_us.json and validates all generated JSON
(texture references, element bounds, uv ranges). Deterministic and idempotent; needs numpy + Pillow.
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
ROOT = HERE.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "darkweb_duo"
MOD = "darkweb_duo"

CORE_LANG = {
    "itemGroup.darkweb_duo.main": "Dark Web & iPhone Duo",
    "block.darkweb_duo.giant_monitor": "Giant Monitor",
    "block.darkweb_duo.giant_keyboard": "Giant Keyboard",
    "block.darkweb_duo.giant_mouse": "Giant Mouse",
    "block.darkweb_duo.delivery_box": "Delivery Box",
    "block.darkweb_duo.omega_delivery_box": "Omega Delivery Box",
    "block.darkweb_duo.keycard_door": "Keycard Door",
    "block.darkweb_duo.omega_terminal": "Omega Terminal",
    "item.darkweb_duo.giant_monitor": "Giant Monitor",
    "item.darkweb_duo.giant_keyboard": "Giant Keyboard",
    "item.darkweb_duo.giant_mouse": "Giant Mouse",
    "item.darkweb_duo.delivery_box": "Delivery Box",
    "item.darkweb_duo.omega_delivery_box": "Omega Delivery Box",
    "item.darkweb_duo.keycard_door": "Keycard Door",
    "item.darkweb_duo.omega_terminal": "Omega Terminal",
    "item.darkweb_duo.dark_keycard": "Dark Web Keycard",
    "item.darkweb_duo.omega_keycard": "Omega Keycard",
    "entity.darkweb_duo.cipher_warden": "The Cipher Warden",
    "entity.darkweb_duo.omega_root": "OMEGA ROOT",
    "entity.darkweb_duo.cutscene_camera": "Cutscene Camera",
    "tooltip.darkweb_duo.dark_keycard.1": "Opens Keycard Doors",
    "tooltip.darkweb_duo.dark_keycard.2": "Issued by the Darknet. Do not lose it.",
    "tooltip.darkweb_duo.omega_keycard.1": "Awakens the Omega Terminal",
    "tooltip.darkweb_duo.omega_keycard.2": "Root-level clearance. Consumed on use.",
    "message.darkweb_duo.door_denied": "ACCESS DENIED - a Dark Web Keycard is required",
    "message.darkweb_duo.door_granted": "ACCESS GRANTED",
    "message.darkweb_duo.got_dark_keycard": "A Dark Web Keycard flies into your inventory",
    "message.darkweb_duo.got_omega_keycard": "An Omega Keycard flies into your inventory",
    "message.darkweb_duo.monitor_need_devices": "The Giant Monitor needs a Giant Keyboard and a Giant Mouse within %s blocks",
    "message.darkweb_duo.monitor_need_emeralds": "The order costs %s emeralds",
    "message.darkweb_duo.monitor_ordered": "Order placed - a Delivery Box is falling from orbit",
    "message.darkweb_duo.no_monitor": "No powered Giant Monitor nearby",
    "message.darkweb_duo.terminal_blocked": "The terminal has no clearance - clear the ceiling above it",
    "message.darkweb_duo.terminal_busy": "The terminal is already running",
    "message.darkweb_duo.terminal_need_card": "An Omega Keycard is required",
    "message.darkweb_duo.terminal_reset": "The terminal reboots",
    "message.darkweb_duo.terminal_spent": "The terminal has already been used",
    "command.darkweb_duo.arena_building": "Building the Cipher arena...",
    "command.darkweb_duo.arena_ready": "The Cipher Warden sleeps. Step in front of it in survival mode...",
    "command.darkweb_duo.omega_building": "Building the Omega arena...",
    "command.darkweb_duo.omega_ready": "The Omega Terminal awaits an Omega Keycard",
}


def write_json(path, obj, indent=1):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=indent, ensure_ascii=False) + "\n", encoding="utf-8")


def gen_textures():
    import core_tex_a
    import core_tex_b
    paths = []
    for name, fn in core_tex_a.TEXTURES + core_tex_b.TEXTURES:
        p = ASSETS / "textures" / (name + ".png")
        p.parent.mkdir(parents=True, exist_ok=True)
        fn().save(p, optimize=True)
        paths.append(p)
    return paths


def gen_models():
    import core_models
    files = core_models.build()
    for rel, obj in files.items():
        write_json(RES / rel, obj)
    return files


def gen_lang():
    write_json(HERE / "lang" / "core.json", CORE_LANG, 2)
    merged = {}
    for f in sorted((HERE / "lang").glob("*.json")):
        merged.update(json.loads(f.read_text(encoding="utf-8")))
    write_json(ASSETS / "lang" / "en_us.json", dict(sorted(merged.items())), 2)
    return merged


def validate(files):
    """consistency checks over the generated JSON: texture files exist, '#refs' are defined, bounds/uvs are legal"""
    errors = []
    tex_root = ASSETS / "textures"
    for rel, obj in files.items():
        if "/models/" not in rel:
            continue
        textures = obj.get("textures", {})
        parent = obj.get("parent", "")
        for k, v in textures.items():
            if v.startswith("#"):
                continue
            ns, _, path = v.partition(":")
            if ns == MOD and not (tex_root / (path + ".png")).exists():
                errors.append("%s: missing texture %s" % (rel, v))
        for i, e in enumerate(obj.get("elements", [])):
            for a in e["from"] + e["to"]:
                if a < -16 or a > 32:
                    errors.append("%s: element %d out of bounds" % (rel, i))
            for d in range(3):
                if e["from"][d] > e["to"][d]:
                    errors.append("%s: element %d inverted on axis %d" % (rel, i, d))
            for dname, f in e["faces"].items():
                t = f["texture"]
                if t.startswith("#") and t[1:] not in textures:
                    errors.append("%s: element %d face %s uses undefined %s" % (rel, i, dname, t))
                for u in f.get("uv", []):
                    if u < -0.001 or u > 16.001:
                        errors.append("%s: element %d face %s uv %s" % (rel, i, dname, f["uv"]))
    # blockstate model references
    for rel, obj in files.items():
        if "/blockstates/" not in rel:
            continue
        for key, v in obj["variants"].items():
            name = v["model"].split(":", 1)[1]
            if "%s/assets/%s/models/%s.json" % (".", MOD, name) and ("assets/%s/models/%s.json" % (MOD, name)) not in files:
                errors.append("%s: variant %s -> missing model %s" % (rel, key, name))
    return errors


def main():
    try:
        paths = gen_textures()
    except ImportError as e:
        print("core assets: skipped textures (%s)" % e)
        paths = []
    files = gen_models()
    lang = gen_lang()
    errs = validate(files)
    if errs:
        print("\n".join(errs[:40]))
        raise SystemExit("core assets: %d validation errors" % len(errs))
    if paths:
        try:
            import core_sheet
            core_sheet.sheet(paths, HERE / "preview" / "core_sheet.png", cell=128, cols=8)
        except Exception as e:  # preview is optional
            print("preview skipped:", e)
    print("core assets: %d textures, %d json files, %d lang keys" % (len(paths), len(files), len(lang)))


if __name__ == "__main__":
    main()
