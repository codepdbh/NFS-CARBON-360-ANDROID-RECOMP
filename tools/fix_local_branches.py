"""Repair only fatal branch placeholders whose label exists in the SAME function.

The original branch condition and PPC destination are preserved. Missing labels
remain fatal and are reported, so this cannot silently skip unknown game code.
"""

import re
from pathlib import Path


FUNCTION = re.compile(r"(?m)^DEFINE_REX_FUNC\(")
FATAL = re.compile(
    r'(?m)^(\tif \([^\n]+?\)) REX_FATAL\("Unresolved branch from '
    r'0x([0-9A-F]{8}) to 0x([0-9A-F]{8})"\);$')


def main():
    root = Path(__file__).resolve().parents[1]
    fixed, missing, changed = 0, 0, 0
    for path in (root / "generated/default").glob("nfscarbon_recomp.*.cpp"):
        original = path.read_text(encoding="utf-8")
        starts = [match.start() for match in FUNCTION.finditer(original)]
        starts.append(len(original))
        output = [original[:starts[0]]] if len(starts) > 1 else [original]
        for first, end in zip(starts, starts[1:]):
            body = original[first:end]
            labels = set(re.findall(r"(?m)^loc_([0-9A-F]{8}):$", body))

            def repair(match):
                nonlocal fixed, missing
                condition, source, target = match.groups()
                if target not in labels:
                    missing += 1
                    return match[0]
                fixed += 1
                return f"{condition} goto loc_{target};"

            output.append(FATAL.sub(repair, body))
        result = "".join(output)
        if result != original:
            path.write_text(result, encoding="utf-8")
            changed += 1
    print(f"Repaired {fixed} original local branches in {changed} files; "
          f"{missing} unresolved branches kept for further analysis")


if __name__ == "__main__":
    main()
