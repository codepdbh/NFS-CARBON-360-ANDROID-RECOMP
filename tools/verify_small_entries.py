"""Independently check recovered C++ entries with Capstone's PPC decoder."""

import json
import re
import struct
from pathlib import Path

from capstone import CS_ARCH_PPC, CS_MODE_32, CS_MODE_BIG_ENDIAN, Cs


def expected_statement(instruction):
    mnemonic = instruction.mnemonic
    operands = [part.strip() for part in instruction.op_str.split(",")]
    if mnemonic in ("li", "lis"):
        value = int(operands[1], 0) * (65536 if mnemonic == "lis" else 1)
        return f"ctx.{operands[0]}.s64 = {value};"
    if mnemonic in ("addi", "addis"):
        value = int(operands[2], 0) * (65536 if mnemonic == "addis" else 1)
        return f"ctx.{operands[0]}.s64 = ctx.{operands[1]}.s64 + ({value});"
    if mnemonic == "mr":
        return f"ctx.{operands[0]}.u64 = ctx.{operands[1]}.u64;"
    if mnemonic in ("lwz", "stw"):
        match = re.fullmatch(r"(-?(?:0x[0-9a-f]+|[0-9]+))\((r[0-9]+|0)\)", operands[1])
        if not match:
            raise AssertionError(instruction.op_str)
        offset, register = int(match[1], 0), match[2]
        expression = (f"ctx.{register}.u32 + ({offset})" if register != "0"
                      else f"static_cast<uint32_t>({offset})")
        if mnemonic == "lwz":
            return f"ctx.{operands[0]}.u64 = REX_LOAD_U32({expression});"
        return f"REX_STORE_U32({expression}, ctx.{operands[0]}.u32);"
    raise AssertionError(f"Unexpected original opcode: {mnemonic}")


def main():
    root = Path(__file__).resolve().parents[1]
    source = (root / "generated/carbon_recovered_thunks.cpp").read_text()
    image = (root / "out/runtime/cache/carbon-82000000.bin").read_bytes()
    registered = {int(value, 16) for value in
                  json.loads((root / "generated/default/codegen.partition.json").read_text())["assignments"]}
    functions = dict(re.findall(
        r"void CarbonSmall_([0-9A-F]{8})\(PPCContext& ctx, uint8_t\* base\) \{\n(.*?)\n\}",
        source, re.S))
    entries = re.findall(
        r"\{0x([0-9A-F]{8}), 0x([0-9A-F]{8}), 0x([0-9A-F]{8}), 0x([0-9A-F]{8}), &CarbonSmall_([0-9A-F]{8})\}",
        source)
    decoder = Cs(CS_ARCH_PPC, CS_MODE_32 | CS_MODE_BIG_ENDIAN)
    for address_text, first, second, third, function in entries:
        address = int(address_text, 16)
        words = [int(first, 16), int(second, 16)]
        if int(third, 16):
            words.append(int(third, 16))
        original = image[address - 0x82000000:address - 0x82000000 + len(words) * 4]
        assert original == struct.pack(">" + "I" * len(words), *words)
        assert address_text == function
        decoded = list(decoder.disasm(original, address))
        assert len(decoded) == len(words)
        if len(words) == 3:
            assert decoded[0].mnemonic in ("li", "lis") and decoded[1].mnemonic == "stw"
            assert decoded[0].op_str.split(",")[0] == decoded[1].op_str.split(",")[0]
        expected = "\n".join("  " + expected_statement(instruction) for instruction in decoded[:-1])
        if decoded[-1].mnemonic == "b":
            target = int(decoded[-1].op_str, 0) & 0xFFFFFFFF
            assert target in registered
            expected += f"\n  REX_CALL_INDIRECT_FUNC(0x{target:08X});"
        else:
            assert decoded[-1].mnemonic == "blr" and len(words) == 2
        assert functions[function] == expected, f"Decoder disagreement at {address:#010x}"
    assert entries and len(entries) == len(functions)
    print(f"Capstone verified {len(entries)} recovered entries against original instructions and C++")


if __name__ == "__main__":
    main()
