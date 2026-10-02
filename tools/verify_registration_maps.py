"""Check that Android's image table covers the omitted registration helper."""
from pathlib import Path
import re


def main():
    generated = Path(__file__).resolve().parents[1] / "generated" / "default"
    image = (generated / "nfscarbon_init.cpp").read_text(encoding="utf-8")
    helper = (generated / "nfscarbon_register.cpp").read_text(encoding="utf-8")
    mappings = re.findall(r"\{\s*(0x[0-9a-fA-F]+)\s*,\s*(\w+)\s*\}", image)
    registrations = re.findall(r"SetFunction\(\s*(0x[0-9a-fA-F]+)\s*,\s*(\w+)\s*\)", helper)
    normalize = lambda entries: [(int(address, 16), symbol) for address, symbol in entries]
    mappings, registrations = normalize(mappings), normalize(registrations)
    if not mappings or mappings != registrations:
        raise SystemExit("FAIL: PPCImageConfig mappings differ from the registration helper")
    if len({address for address, _ in mappings}) != len(mappings):
        raise SystemExit("FAIL: duplicate guest addresses in the image mapping table")
    print(f"PASS: all {len(mappings):,} ordered guest/symbol mappings match")


if __name__ == "__main__":
    main()
