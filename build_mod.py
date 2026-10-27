#!/usr/bin/env python3
"""
build_mod.py  -  ONE command that generates every asset, verifies the project and builds the mod jar.

    python build_mod.py                # generate (only what is missing) -> verify -> build -> dist/darkweb_duo-1.0.0.jar
    python build_mod.py --regen        # regenerate ALL procedural assets (textures, models, 56 sounds, 2 boss themes)
    python build_mod.py --check        # generate + verify only, no Gradle build
    python build_mod.py --client       # build, then start a dev client (gradle runClient) to try it
    python build_mod.py --java /path/to/jdk17     # use this JDK 17

Mod:   "Dark Web & iPhone Duo"  (mod id darkweb_duo)  -  Minecraft 1.20.1, Forge 47.2.0, Java 17, official Mojang mappings.
Needs: Python 3.9+, JDK 17 and internet access (Gradle + Forge are downloaded from services.gradle.org / maven.minecraftforge.net).
Asset generation needs numpy, Pillow, scipy and ffmpeg - but the generated assets are already shipped in src/main/resources,
so a plain `python build_mod.py` normally skips generation entirely.
"""
import argparse
import glob
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / "tools"
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "darkweb_duo"
JAVA_SRC = ROOT / "src" / "main" / "java"
GRADLE_VERSION = "8.1.1"
GRADLE_URL = "https://services.gradle.org/distributions/gradle-%s-bin.zip" % GRADLE_VERSION
WIN = platform.system() == "Windows"


def say(msg):
    print("[build_mod] " + msg, flush=True)


def die(msg, code=1):
    print("\n[build_mod] ERROR: " + msg, file=sys.stderr)
    sys.exit(code)


# ------------------------------------------------------------------------------------------------ 1) asset generation
GENERATORS = [
    # (script, sentinel file that proves its output is already present)
    ("gen_cipher.py", ASSETS / "textures/entity/cipher_warden.png"),
    ("gen_omega.py", ASSETS / "textures/entity/omega_root.png"),
    ("gen_iphone.py", ASSETS / "textures/entity/iphone"),
    ("gen_audio.py", ASSETS / "sounds/boss2_theme.ogg"),
]


def py_has(mod):
    try:
        __import__(mod)
        return True
    except Exception:
        return False


def ensure_python_deps():
    need = [("numpy", "numpy"), ("PIL", "pillow"), ("scipy", "scipy")]
    missing = [pkg for mod, pkg in need if not py_has(mod)]
    if not missing:
        return True
    say("installing Python packages for asset generation: " + ", ".join(missing))
    for extra in ([], ["--break-system-packages"], ["--user"]):
        r = subprocess.run([sys.executable, "-m", "pip", "install", "-q"] + extra + missing)
        if r.returncode == 0:
            return all(py_has(mod) for mod, _ in need)
    return False


def run_py(script, *args):
    t0 = time.time()
    r = subprocess.run([sys.executable, str(TOOLS / script)] + list(args), cwd=str(ROOT))
    if r.returncode != 0:
        die("%s failed (exit %d)" % (script, r.returncode))
    say("%-18s done in %.1fs" % (script, time.time() - t0))


def generate_assets(regen):
    todo = [(s, f) for s, f in GENERATORS if regen or not f.exists()]
    if todo or regen:
        if not ensure_python_deps():
            if regen:
                die("numpy / Pillow / scipy are required to regenerate assets: pip install numpy pillow scipy")
            say("WARNING: numpy/Pillow/scipy missing - skipping regeneration, using the shipped assets")
            todo = []
        if any(s == "gen_audio.py" for s, _ in todo) and shutil.which("ffmpeg") is None:
            if (ASSETS / "sounds/boss2_theme.ogg").exists():
                say("ffmpeg not found - keeping the shipped audio")
                todo = [(s, f) for s, f in todo if s != "gen_audio.py"]
            else:
                die("ffmpeg is required to render the audio (https://ffmpeg.org) - or restore src/main/resources/assets/darkweb_duo/sounds")
    if not todo:
        say("procedural assets already present (use --regen to rebuild them)")
    for s, _ in todo:
        run_py(s)
    # core assets (textures, models, blockstates, loot, recipes, tags + language merge) are cheap: always refresh them
    if py_has("numpy") and py_has("PIL"):
        run_py("gen_core_assets.py")
    else:
        say("WARNING: skipping gen_core_assets.py (numpy/Pillow missing) - using the shipped core assets")


# ------------------------------------------------------------------------------------------------ 2) verification
def verify():
    problems = []
    # JSON
    n_json = 0
    for f in RES.rglob("*.json"):
        n_json += 1
        try:
            json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:
            problems.append("invalid JSON %s: %s" % (f.relative_to(ROOT), e))
    # every ModSounds event has a file + entry
    sounds_json = {}
    try:
        sounds_json = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    except Exception:
        problems.append("sounds.json missing")
    ms = JAVA_SRC / "com/darkwebduo/registry/ModSounds.java"
    if ms.exists():
        for name in re.findall(r'reg\("([a-z0-9_]+)"\)', ms.read_text(encoding="utf-8")):
            if name not in sounds_json:
                problems.append("sounds.json has no entry for '%s'" % name)
            if not (ASSETS / "sounds" / (name + ".ogg")).exists():
                problems.append("missing sounds/%s.ogg" % name)
    # translation keys used by the Java code exist
    lang_file = ASSETS / "lang" / "en_us.json"
    lang = {}
    try:
        lang = json.loads(lang_file.read_text(encoding="utf-8"))
    except Exception:
        problems.append("lang/en_us.json missing")
    key_re = re.compile(r'"((?:message|command|tooltip|itemGroup|phone|entity|block|item)\.darkweb_duo\.[A-Za-z0-9_.]*)"')
    for f in JAVA_SRC.rglob("*.java"):
        for key in key_re.findall(f.read_text(encoding="utf-8")):
            ok = (key in lang) if not key.endswith(".") else any(k.startswith(key) for k in lang)
            if not ok:
                problems.append("missing lang key '%s' (%s)" % (key, f.name))
    # textures referenced by the Java renderers
    tex_re = re.compile(r'"(textures/[a-z0-9_/]+\.png)"')
    for f in JAVA_SRC.rglob("*.java"):
        for t in tex_re.findall(f.read_text(encoding="utf-8")):
            if not (ASSETS / t).exists():
                problems.append("missing %s (%s)" % (t, f.name))
    # blockstates / item models for every registered block + item
    for b in ("giant_monitor", "giant_keyboard", "giant_mouse", "delivery_box", "omega_delivery_box", "keycard_door", "omega_terminal", "iphone_duo"):
        for rel in ("blockstates/%s.json" % b, "models/item/%s.json" % b, "../../data/darkweb_duo/loot_tables/blocks/%s.json" % b):
            if not (ASSETS / rel).exists():
                problems.append("missing " + rel)
    for i in ("dark_keycard", "omega_keycard"):
        if not (ASSETS / ("models/item/%s.json" % i)).exists():
            problems.append("missing models/item/%s.json" % i)
    # Java syntax (javalang) when available
    if py_has("javalang"):
        r = subprocess.run([sys.executable, str(TOOLS / "syntax_check.py")], cwd=str(ROOT), capture_output=True, text=True)
        tail = (r.stdout.strip().splitlines() or [""])[-1]
        if r.returncode != 0:
            problems.append("Java syntax check failed:\n" + r.stdout)
        else:
            say("java syntax check: " + tail)
    else:
        say("(pip install javalang to also run the Java syntax check)")
    say("checked %d JSON files, sounds, language keys, textures" % n_json)
    if problems:
        print("\n".join("  - " + p for p in problems[:60]))
        die("%d verification problem(s)" % len(problems))
    say("verification OK")


# ------------------------------------------------------------------------------------------------ 3) JDK 17 + Gradle
def java_major(java_exe):
    try:
        out = subprocess.run([str(java_exe), "-version"], capture_output=True, text=True, timeout=30)
    except Exception:
        return None
    m = re.search(r'version "(\d+)(?:\.(\d+))?', out.stderr + out.stdout)
    if not m:
        return None
    major = int(m.group(1))
    return int(m.group(2)) if major == 1 and m.group(2) else major


def find_jdk17(explicit=None):
    cands = []
    if explicit:
        cands.append(Path(explicit))
    if os.environ.get("JAVA_HOME"):
        cands.append(Path(os.environ["JAVA_HOME"]))
    which = shutil.which("java")
    if which:
        cands.append(Path(which).resolve().parent.parent)
    pats = ["/usr/lib/jvm/*17*", "/usr/lib/jvm/*/", "/Library/Java/JavaVirtualMachines/*17*/Contents/Home", "/opt/homebrew/opt/openjdk@17",
            "/usr/local/opt/openjdk@17", os.path.expanduser("~/.sdkman/candidates/java/17*"), os.path.expanduser("~/.jdks/*17*"),
            "C:/Program Files/Java/jdk-17*", "C:/Program Files/Eclipse Adoptium/jdk-17*", "C:/Program Files/Microsoft/jdk-17*",
            "C:/Program Files/Amazon Corretto/jdk17*", "C:/Program Files/Zulu/zulu-17*"]
    for p in pats:
        cands += [Path(x) for x in sorted(glob.glob(p))]
    seen = set()
    for home in cands:
        home = home.resolve() if home.exists() else home
        if str(home) in seen:
            continue
        seen.add(str(home))
        exe = home / "bin" / ("java.exe" if WIN else "java")
        if exe.exists() and java_major(exe) == 17:
            return home
    return None


def find_gradle(java_home):
    wrapper = ROOT / ("gradlew.bat" if WIN else "gradlew")
    if wrapper.exists() and (ROOT / "gradle/wrapper/gradle-wrapper.jar").exists():
        return [str(wrapper)]
    local = ROOT / ".build-tools" / ("gradle-" + GRADLE_VERSION) / "bin" / ("gradle.bat" if WIN else "gradle")
    if local.exists():
        return [str(local)]
    sysg = shutil.which("gradle")
    if sysg:
        # Forge 1.20.1 needs Gradle 8.1.x - 8.x; take a system gradle only if it is not too new / old
        try:
            out = subprocess.run([sysg, "--version"], capture_output=True, text=True, timeout=120,
                                 env=dict(os.environ, JAVA_HOME=str(java_home))).stdout
            m = re.search(r"Gradle (\d+)\.(\d+)", out)
            if m and (int(m.group(1)), int(m.group(2))) >= (8, 1) and int(m.group(1)) == 8:
                return [sysg]
        except Exception:
            pass
    say("downloading Gradle %s (about 120 MB) ..." % GRADLE_VERSION)
    dest = ROOT / ".build-tools"
    dest.mkdir(exist_ok=True)
    zpath = dest / ("gradle-%s-bin.zip" % GRADLE_VERSION)
    try:
        urllib.request.urlretrieve(GRADLE_URL, str(zpath))
        with zipfile.ZipFile(zpath) as z:
            z.extractall(dest)
    except Exception as e:
        die("could not download Gradle (%s). Install Gradle %s manually or run `gradle wrapper` once, then re-run." % (e, GRADLE_VERSION))
    zpath.unlink(missing_ok=True)
    if not WIN:
        try:
            os.chmod(local, 0o755)
        except OSError:
            pass
    return [str(local)]


def build(args):
    jdk = find_jdk17(args.java)
    if jdk is None:
        die("JDK 17 not found. Install it (e.g. Eclipse Temurin 17: https://adoptium.net/temurin/releases/?version=17) "
            "and re-run, or pass --java <path-to-jdk17>. Forge 1.20.1 requires exactly Java 17.")
    say("using JDK 17 at %s" % jdk)
    gradle = find_gradle(jdk)
    env = dict(os.environ, JAVA_HOME=str(jdk))
    env["PATH"] = str(jdk / "bin") + os.pathsep + env.get("PATH", "")
    env.pop("JAVA_TOOL_OPTIONS", None) if args.clean_env else None
    tasks = ["clean", "build"] if args.clean else ["build"]
    cmd = gradle + tasks + ["--console=plain"] + (["--offline"] if args.offline else [])
    say("running: " + " ".join(cmd) + "   (the first run downloads Minecraft + Forge and takes several minutes)")
    r = subprocess.run(cmd, cwd=str(ROOT), env=env)
    if r.returncode != 0:
        die("Gradle build failed (exit %d). Scroll up for the first error - compile errors name the file and line." % r.returncode)
    jars = [j for j in (ROOT / "build" / "libs").glob("*.jar") if not any(s in j.name for s in ("sources", "javadoc", "slim"))]
    if not jars:
        die("build finished but no jar was found in build/libs")
    jar = max(jars, key=lambda p: p.stat().st_mtime)
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    out = dist / jar.name
    shutil.copy2(jar, out)
    say("BUILD SUCCESSFUL -> %s (%.1f MB)" % (out, out.stat().st_size / 1e6))
    say("install: copy it to <minecraft>/mods together with Forge 1.20.1-47.2.0 (or newer 47.x).")
    if args.client:
        say("starting a dev client ...")
        subprocess.run(gradle + ["runClient", "--console=plain"], cwd=str(ROOT), env=env)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--regen", action="store_true", help="regenerate all procedural assets")
    ap.add_argument("--check", action="store_true", help="generate + verify only (no Gradle build)")
    ap.add_argument("--no-generate", action="store_true", help="skip asset generation")
    ap.add_argument("--clean", action="store_true", help="gradle clean first")
    ap.add_argument("--offline", action="store_true", help="gradle --offline")
    ap.add_argument("--client", action="store_true", help="run a dev client after building")
    ap.add_argument("--java", help="path to a JDK 17 home")
    ap.add_argument("--clean-env", action="store_true", help="drop JAVA_TOOL_OPTIONS (proxy settings) from the Gradle environment")
    args = ap.parse_args()
    t0 = time.time()
    if not args.no_generate:
        generate_assets(args.regen)
    verify()
    if args.check:
        say("check finished in %.1fs" % (time.time() - t0))
        return
    build(args)
    say("total %.0fs" % (time.time() - t0))


if __name__ == "__main__":
    main()
