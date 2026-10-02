"""Generate build-local direct calls, preserving every known hook and dispatch table.

Adapted from nfsmw-android/tools/llamadas_directas.py (GPL-3.0-only).
Original locally generated game sources are never modified.
"""
import argparse
import re
from pathlib import Path

CALL = re.compile(r"(?<![\w])((?:sub_[0-9A-F]{8}|__(?:save|rest)gprlr_\d+))\(ctx, base\);")
DEFINITION = re.compile(r"DEFINE_REX_FUNC\((\w+)\)")
ADDRESS = re.compile(r"(?<![0-9A-Fa-f])(82[0-9A-Fa-f]{6})(?![0-9A-Fa-f])")

def transform(source, defined, blocked):
    return CALL.sub(lambda match: "__imp__" + match.group(0)
                    if match[1] in defined and match[1] not in blocked else match.group(0), source)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    files = sorted((args.root / "generated/default").glob("nfscarbon_recomp.*.cpp"))
    texts = {file: file.read_text(encoding="utf-8") for file in files}
    defined = set().union(*(set(DEFINITION.findall(text)) for text in texts.values()))
    blocked = set()
    roots = [args.root / "src", args.root / "android/app/src/main/cpp",
             args.sdk / "src", args.sdk / "include"]
    for root in roots:
        for file in root.rglob("*"):
            if file.suffix not in {".cpp", ".h", ".in", ".c"}: continue
            text = file.read_text(encoding="utf-8", errors="ignore")
            blocked.update("sub_" + value.upper() for value in ADDRESS.findall(text))
            blocked.update(re.findall(r"REX_HOOK\w*\s*\(\s*(\w+)", text))
    args.output.mkdir(parents=True, exist_ok=True)
    converted = 0
    for file, text in texts.items():
        result = transform(text, defined, blocked)
        converted += result.count("__imp__") - text.count("__imp__")
        target = args.output / file.name
        if not target.exists() or target.read_text(encoding="utf-8") != result:
            target.write_text(result, encoding="utf-8", newline="\n")
    print(f"Carbon Android: {converted} direct calls, {len(blocked)} preserved hook candidates")

if __name__ == "__main__": main()
