"""Recover exact, bounded PPC leaf entries missing from the generated table.

Recognizes two-instruction return leaves and argument-adapter tail calls.
No branch target is invented: tail calls must reach a registered entry.
Generated implementations and original instruction fingerprints stay local.
"""

import json
import re
import struct
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[1]
    image = (root / "out/runtime/cache/carbon-82000000.bin").read_bytes()
    pch = (root / "generated/default/nfscarbon_pch.h").read_text()
    bounds = {name: int(re.search(rf"#define REX_{name} (0x[0-9A-Fa-f]+)", pch)[1], 16)
              for name in ("IMAGE_BASE", "CODE_BASE", "CODE_SIZE")}
    registered = {int(value, 16) for value in
                  json.loads((root / "generated/default/codegen.partition.json").read_text())["assignments"]}
    entries = []
    for address in range(bounds["CODE_BASE"], bounds["CODE_BASE"] + bounds["CODE_SIZE"] - 4, 4):
        if address in registered:
            continue
        first, second = struct.unpack_from(">II", image, address - bounds["IMAGE_BASE"])
        opcode = first >> 26
        dst, src = (first >> 21) & 31, (first >> 16) & 31
        immediate = first & 0xFFFF
        if immediate & 0x8000:
            immediate -= 0x10000
        instruction = None
        if opcode in (14, 15):  # addi / addis, including li / lis (RA = 0)
            addend = immediate * (65536 if opcode == 15 else 1)
            expression = f"ctx.r{src}.s64 + ({addend})" if src else str(addend)
            instruction = f"ctx.r{dst}.s64 = {expression};"
        elif first & 0xFC0007FF == 0x7C000378 and ((first >> 11) & 31) == dst:
            # mr is or RA,RS,RS without recording CR0.
            instruction = f"ctx.r{src}.u64 = ctx.r{dst}.u64;"
        elif opcode == 32 and second == 0x4E800020:  # lwz; blr
            expression = f"ctx.r{src}.u32 + ({immediate})" if src else f"static_cast<uint32_t>({immediate})"
            instruction = f"ctx.r{dst}.u64 = REX_LOAD_U32({expression});"
        elif opcode == 36 and second == 0x4E800020:  # stw; blr
            expression = f"ctx.r{src}.u32 + ({immediate})" if src else f"static_cast<uint32_t>({immediate})"
            instruction = f"REX_STORE_U32({expression}, ctx.r{dst}.u32);"
        if instruction is None:
            continue
        if second == 0x4E800020:
            tail = None
        elif second & 0xFC000003 == 0x48000000:  # relative b, no link
            displacement = second & 0x03FFFFFC
            if displacement & 0x02000000:
                displacement -= 0x04000000
            tail = (address + 4 + displacement) & 0xFFFFFFFF
            if tail not in registered:
                continue
        else:
            continue
        entries.append((address, first, second, instruction, tail))
    lines = [
        "// Generated locally from the loaded Carbon executable. Do not distribute.",
        '#include "generated/default/nfscarbon_pch.h"',
        "#include <rex/system/function_dispatcher.h>",
        "namespace {",
    ]
    for address, _, _, instruction, tail in entries:
        lines += [f"void CarbonSmall_{address:08X}(PPCContext& ctx, uint8_t* base) {{",
                  f"  {instruction}"]
        if tail is not None:
            lines.append(f"  REX_CALL_INDIRECT_FUNC(0x{tail:08X});")
        lines.append("}")
    lines += [
        "struct Entry { uint32_t address, first, second; PPCFunc* function; };",
        "const Entry entries[] = {",
    ]
    lines += [f"  {{0x{address:08X}, 0x{first:08X}, 0x{second:08X}, &CarbonSmall_{address:08X}}},"
              for address, first, second, _, _ in entries]
    lines += [
        "};", "}",
        "void CarbonRegisterRecoveredEntries(rex::runtime::FunctionDispatcher* dispatcher, uint8_t* base) {",
        "  size_t count = 0;",
        "  for (const auto& entry : entries) {",
        "    if (dispatcher->GetFunction(entry.address)) continue;",
        "    if (REX_LOAD_U32(entry.address) != entry.first || REX_LOAD_U32(entry.address + 4) != entry.second)",
        '      REX_FATAL("Carbon small-entry fingerprint differs from loaded image");',
        "    if (!dispatcher->SetFunction(entry.address, entry.function))",
        '      REX_FATAL("Could not register recovered Carbon leaf entry");',
        "    ++count;",
        "  }",
        '  REXLOG_INFO("[carbon] Registered {} verified small PPC entries", count);',
        "}",
    ]
    output = root / "generated/carbon_recovered_thunks.cpp"
    result = "\n".join(lines) + "\n"
    if not output.exists() or output.read_text(encoding="utf-8") != result:
        output.write_text(result, encoding="utf-8")
    addresses = {entry[0] for entry in entries}
    print(f"Recovered {len(entries)} exact small entries -> {output}")
    print(f"Latest observed 0x821A3FC0 recovered: {0x821A3FC0 in addresses}")


if __name__ == "__main__":
    main()
