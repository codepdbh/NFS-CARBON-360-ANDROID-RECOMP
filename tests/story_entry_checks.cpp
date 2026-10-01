// Exercise the original resource-update contract without launching the game.
// Include the manual translations to call the otherwise private entry directly.
#include "src/carbon_missing_entries.cpp"
#include <windows.h>
#include <iostream>

const rex::PPCImageInfo PPCImageConfig = {
  .code_base = REX_CODE_BASE, .code_size = REX_CODE_SIZE,
  .image_base = REX_IMAGE_BASE, .image_size = REX_IMAGE_SIZE,
  .func_mappings = nullptr,
};

namespace {
uint32_t resolved_callback = 0;
uint64_t callback_this = 0, callback_argument = 0;
void TestCallback(PPCContext& ctx, [[maybe_unused]] uint8_t* base) {
  callback_this = ctx.r3.u64;
  callback_argument = ctx.r4.u64;
  ctx.r5.u64 = 0xC0DEC0DE;
}
}

// Resolve the synthetic receiver locally, without a live game runtime.
namespace rex::runtime {
PPCFunc* ResolveIndirectFunction(uint32_t address) {
  resolved_callback = address;
  return &TestCallback;
}
}

int main() {
  // Reserve a guest-sized address space; commit only the three pages touched.
  auto* base = static_cast<uint8_t*>(VirtualAlloc(nullptr, 0x100000000ull,
                    MEM_RESERVE, PAGE_READWRITE));
  if (!base) return 2;
  constexpr uint32_t callback_table_page = static_cast<uint32_t>(
      REX_IMAGE_BASE + REX_IMAGE_SIZE + (0x823ABC00ull - REX_CODE_BASE) * 2) & ~4095u;
  for (uint32_t address : {0x10000u, 0x20000u, 0x82BA2000u, callback_table_page}) {
    if (!VirtualAlloc(base + address, 4096, MEM_COMMIT, PAGE_READWRITE)) {
      VirtualFree(base, 0, MEM_RELEASE);
      return 2;
    }
  }
  int cases = 0;
  for (int32_t type : {-1, 0, 1, 2, 3, 4, 5, 6, 12, 13}) {
    for (uint32_t flag : {0u, 1u, 0xFFFFFFFFu}) {
      PPCContext ctx{};
      ctx.r3.u64 = 0x10000;
      ctx.r4.u64 = 0x20000;
      ctx.r10.u64 = 0xDEADBEEF;
      ctx.r11.u64 = 0xDEADBEEF;
      ctx.lr = 0x12345678;
      ctx.r31.u64 = 0xABCDEF0123456789ull;
      REX_STORE_U32(0x20014, static_cast<uint32_t>(type));
      REX_STORE_U32(0x20010, flag);
      REX_STORE_U32(0x10010, 0xDEADBEEF);
      REX_STORE_U32(0x10014, 0xDEADBEEF);
      REX_STORE_U32(0x82BA200C, 0x11223344);
      REX_STORE_U32(0x82BA2010, 0x55667788);
      Carbon_822A9490(ctx, base);
      bool retain = type == 2 || type == 3 || type == 4 || type == 5 || type == 12;
      bool valid = REX_LOAD_U32(0x10010) == 0x20000 &&
          REX_LOAD_U32(0x10014) == (retain ? 0x20000u : 0u) &&
          REX_LOAD_U32(0x82BA200C) == (flag ? 0x11223344u : 0u) &&
          REX_LOAD_U32(0x82BA2010) == (flag ? 0x55667788u : 0u) &&
          ctx.r3.u64 == 0x10000 && ctx.r4.u64 == 0x20000 &&
          ctx.lr == 0x12345678 && ctx.r31.u64 == 0xABCDEF0123456789ull &&
          ctx.r10.u64 == 0 && ctx.cr6.eq == (flag == 0) &&
          ctx.r11.u64 == (flag ? static_cast<uint64_t>(flag) : 0xFFFFFFFF82BA200Cull);
      if (!valid) {
        std::cerr << "Resource entry mismatch: type=" << type << " flag=" << flag << '\n';
        VirtualFree(base, 0, MEM_RELEASE);
        return 1;
      }
      ++cases;
    }
  }
  for (uint64_t state : {0ull, 1ull, 2ull, 3ull, 4ull, 0xFFFFFFFFull, 0x100000002ull}) {
    PPCContext ctx{};
    ctx.r4.u64 = state;
    ctx.r11.u64 = 0x12345678;
    ctx.lr = 0xABCDEF01;
    Carbon_82296468(ctx, base);
    uint32_t low = static_cast<uint32_t>(state);
    uint64_t expected = low == 0 ? 1 : low == 1 ? 12 : low == 2 ? 13 : UINT64_MAX;
    bool expected_lt = low == 0 || low == 2;
    bool expected_eq = low == 1 || low == 3;
    if (ctx.r3.u64 != expected || ctx.r4.u64 != state ||
        ctx.r11.u64 != 0x12345678 || ctx.lr != 0xABCDEF01 ||
        ctx.cr6.lt != expected_lt || ctx.cr6.eq != expected_eq) {
      std::cerr << "State entry mismatch: " << state << '\n';
      VirtualFree(base, 0, MEM_RELEASE);
      return 1;
    }
    ++cases;
  }
  for (uint32_t adjustment : {0u, 4u, 0xFFFFFFFFu}) {
    PPCContext ctx{};
    ctx.r3.u64 = 0xABCDEF0112340000ull;
    ctx.r4.u64 = 0x20000;
    ctx.lr = 0x12345678;
    ctx.r31.u64 = 0x9988776655443322ull;
    REX_STORE_U32(0x20000, 0x823ABC00);
    REX_STORE_U32(0x20004, adjustment);
    REX_STORE_U32(0x20008, 0x10000);
    resolved_callback = 0;
    Carbon_MemberAdapter(ctx, base);
    uint64_t expected_this = 0x10000ull + adjustment;
    if (resolved_callback != 0x823ABC00 || callback_this != expected_this ||
        callback_argument != 0xABCDEF0112340000ull || ctx.r3.u64 != expected_this ||
        ctx.r4.u64 != callback_argument || ctx.r5.u64 != 0xC0DEC0DE ||
        ctx.r10.u64 != 0x10000 || ctx.r9.u64 != adjustment ||
        ctx.r11.u64 != 0x823ABC00 || ctx.ctr.u64 != 0x823ABC00 ||
        ctx.lr != 0x12345678 || ctx.r31.u64 != 0x9988776655443322ull) {
      std::cerr << "Member adapter mismatch: adjustment=" << adjustment << '\n';
      VirtualFree(base, 0, MEM_RELEASE);
      return 1;
    }
    ++cases;
  }
  VirtualFree(base, 0, MEM_RELEASE);
  std::cout << "Story entries: " << cases << " cases passed\n";
  return 0;
}
