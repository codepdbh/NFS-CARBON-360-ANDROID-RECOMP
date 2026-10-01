"""Disassemble a local image produced by run_pc.ps1 -DumpImage.

This inspects the user's executable; it never patches the game or generates
function hints automatically. Requires the Python capstone package.
"""

import argparse
import bisect
import json
from pathlib import Path

from capstone import CS_ARCH_PPC, CS_MODE_32, CS_MODE_BIG_ENDIAN, Cs


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("addresses", nargs="+", type=lambda value: int(value, 0))
    parser.add_argument("--bytes", type=int, default=96)
    parser.add_argument("--image", type=Path,
                        default=root / "out/runtime/cache/carbon-82000000.bin")
    args = parser.parse_args()
    data = args.image.read_bytes()
    assignments = json.loads((root / "generated/default/codegen.partition.json").read_text())["assignments"]
    starts = sorted(int(address, 16) for address in assignments)
    disassembler = Cs(CS_ARCH_PPC, CS_MODE_32 | CS_MODE_BIG_ENDIAN)
    for address in args.addresses:
        offset = address - 0x82000000
        if offset < 0 or offset >= len(data) or address % 4:
            parser.error(f"Address outside the aligned loaded image: {address:#010x}")
        index = bisect.bisect_left(starts, address)
        print(f"\n{address:#010x}: registered entry = {address in starts}")
        print("Nearby entries: " + ", ".join(f"{value:#010x}" for value in starts[max(0, index - 2):index + 3]))
        # Proximity is diagnostic context, not proof of function ownership.
        for instruction in disassembler.disasm(data[offset:offset + args.bytes], address):
            print(f"  {instruction.address:08X}  {instruction.bytes.hex()}  "
                  f"{instruction.mnemonic:8} {instruction.op_str}")


if __name__ == "__main__":
    main()
