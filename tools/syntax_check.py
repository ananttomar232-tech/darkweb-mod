#!/usr/bin/env python3
"""Syntax-checks Java files with javalang (no classpath needed).

usage: syntax_check.py [files or dirs...]   (default: src/main/java)
Exit code 1 if any file fails to parse. Also flags a few mistakes javalang cannot see.
"""
import re
import sys
from pathlib import Path

try:
    import javalang
except ImportError:
    print("pip install javalang --break-system-packages")
    sys.exit(2)

ROOT = Path(__file__).resolve().parent.parent
args = sys.argv[1:] or [str(ROOT / "src/main/java")]
files = []
for a in args:
    p = Path(a)
    files += sorted(p.rglob("*.java")) if p.is_dir() else [p]

bad = 0
for f in files:
    src = f.read_text(encoding="utf-8")
    try:
        javalang.parse.parse(src)
    except Exception as e:  # javalang raises several exception types
        bad += 1
        pos = getattr(e, "at", None)
        print(f"SYNTAX  {f.relative_to(ROOT)}: {e} {pos if pos else ''}")
        continue
    # cheap semantic lints
    pkg = re.search(r"^package\s+([\w.]+);", src, re.M)
    expected = ".".join(f.relative_to(ROOT / "src/main/java").parent.parts) if (ROOT / "src/main/java") in f.parents else None
    if pkg and expected and pkg.group(1) != expected:
        bad += 1
        print(f"PACKAGE {f.relative_to(ROOT)}: declares {pkg.group(1)} but path implies {expected}")
    if re.search(r"\.level\b(?!\s*\()(?!\w)", src) and "level()" not in src.split(".level")[0][-0:]:
        # `.level` used as a field (1.20.1 requires level())
        for m in re.finditer(r"\b(this|entity|mob|boss|player|target|e|living)\.level\b(?!\s*\()", src):
            bad += 1
            print(f"LEVEL   {f.relative_to(ROOT)}: field access '{m.group(0)}' (use level())")
            break

print(f"checked {len(files)} files, {bad} problem(s)")
sys.exit(1 if bad else 0)
