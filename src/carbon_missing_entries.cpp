// Exact translation of executable entries missed by automatic discovery.
// Validated against the loaded original PPC instructions before registration.
#include "generated/default/nfscarbon_pch.h"
#include "carbon_missing_entries.h"
#include <rex/system/function_dispatcher.h>
#include <array>
#include <bit>

namespace {

// Identical eight-instruction member-callback adapters occur consecutively.
// GapFill doesn't split at bctr, so only the first adapter may be discovered.
constexpr std::array<uint32_t, 8> kMemberAdapter = {
  0x7C8B2378, 0x7C641B78, 0x814B0008, 0x812B0004,
  0x816B0000, 0x7C6A4A14, 0x7D6903A6, 0x4E800420,
};

void Carbon_MemberAdapter(PPCContext& ctx, uint8_t* base) {
  ctx.r11.u64 = ctx.r4.u64;
  ctx.r4.u64 = ctx.r3.u64;
  ctx.r10.u64 = REX_LOAD_U32(ctx.r11.u32 + 8);
  ctx.r9.u64 = REX_LOAD_U32(ctx.r11.u32 + 4);
  ctx.r11.u64 = REX_LOAD_U32(ctx.r11.u32);
  ctx.r3.u64 = ctx.r10.u64 + ctx.r9.u64;
  ctx.ctr.u64 = ctx.r11.u64;
  REX_CALL_INDIRECT_FUNC(ctx.ctr.u32);
}

void Carbon_Blr([[maybe_unused]] PPCContext& ctx,
                [[maybe_unused]] uint8_t* base) {}

void Carbon_82641480(PPCContext& ctx, uint8_t* base) {
  ctx.r3.s64 = ctx.r3.s64 - 4;
  REX_CALL_INDIRECT_FUNC(0x8262DB10);
}

void Carbon_82296468(PPCContext& ctx, [[maybe_unused]] uint8_t* base) {
  ctx.cr6.compare<uint32_t>(ctx.r4.u32, 1, ctx.xer);
  if (ctx.cr6.lt) { ctx.r3.s64 = 1; return; }
  if (ctx.cr6.eq) { ctx.r3.s64 = 12; return; }
  ctx.cr6.compare<uint32_t>(ctx.r4.u32, 3, ctx.xer);
  if (ctx.cr6.lt) { ctx.r3.s64 = 13; return; }
  ctx.r3.s64 = -1;
}

constexpr std::array<uint32_t, 13> kOriginal_82296468 = {
  0x2B040001, 0x41980028, 0x419A001C, 0x2B040003,
  0x4198000C, 0x3860FFFF, 0x4E800020, 0x3860000D,
  0x4E800020, 0x3860000C, 0x4E800020, 0x38600001,
  0x4E800020,
};

// 0x822A9490..0x822A94F0: update an object's two resource references and
// clear the original global pair when the resource's field_0x10 is zero.
// Keep the original comparison sequence and all volatile register effects.
void Carbon_822A9490(PPCContext& ctx, uint8_t* base) {
  ctx.r11.u64 = REX_LOAD_U32(ctx.r4.u32 + 0x14);
  ctx.r10.s64 = 0;
  ctx.cr6.compare<int32_t>(ctx.r11.s32, 2, ctx.xer);
  if (ctx.cr6.eq) goto loc_822A94C8;
  ctx.cr6.compare<int32_t>(ctx.r11.s32, 3, ctx.xer);
  if (ctx.cr6.eq) goto loc_822A94C8;
  ctx.cr6.compare<int32_t>(ctx.r11.s32, 4, ctx.xer);
  if (ctx.cr6.eq) goto loc_822A94C8;
  ctx.cr6.compare<int32_t>(ctx.r11.s32, 12, ctx.xer);
  if (ctx.cr6.eq) goto loc_822A94C8;
  ctx.cr6.compare<int32_t>(ctx.r11.s32, 5, ctx.xer);
  if (ctx.cr6.eq) goto loc_822A94C8;
  REX_STORE_U32(ctx.r3.u32 + 0x14, ctx.r10.u32);
  goto loc_822A94CC;
loc_822A94C8:
  REX_STORE_U32(ctx.r3.u32 + 0x14, ctx.r4.u32);
loc_822A94CC:
  REX_STORE_U32(ctx.r3.u32 + 0x10, ctx.r4.u32);
  ctx.r11.u64 = REX_LOAD_U32(ctx.r4.u32 + 0x10);
  ctx.cr6.compare<int32_t>(ctx.r11.s32, 0, ctx.xer);
  if (!ctx.cr6.eq) return;
  ctx.r11.s64 = static_cast<int32_t>(0x82BA0000u);
  ctx.r11.s64 = ctx.r11.s64 + 0x200C;
  REX_STORE_U32(ctx.r11.u32, ctx.r10.u32);
  REX_STORE_U32(ctx.r11.u32 + 4, ctx.r10.u32);
}

constexpr std::array<uint32_t, 24> kOriginal_822A9490 = {
  0x81640014, 0x39400000, 0x2F0B0002, 0x419A002C,
  0x2F0B0003, 0x419A0024, 0x2F0B0004, 0x419A001C,
  0x2F0B000C, 0x419A0014, 0x2F0B0005, 0x419A000C,
  0x91430014, 0x48000008, 0x90830014, 0x90830010,
  0x81640010, 0x2F0B0000, 0x4C9A0020, 0x3D6082BA,
  0x396B200C, 0x914B0000, 0x914B0004, 0x4E800020,
};

// 0x82322048..0x82322094: virtual dispatch through object->field_0xb0,
// falling back to the original object's vtable when it is unavailable.
void Carbon_82322048(PPCContext& ctx, uint8_t* base) {
  ctx.r11.u64 = REX_LOAD_U32(ctx.r3.u32 + 0xB0);
  ctx.cr6.compare<uint32_t>(ctx.r11.u32, 0, ctx.xer);
  if (!ctx.cr6.eq) {
    ctx.r11.u64 = REX_LOAD_U32(ctx.r11.u32 + 0x18);
    ctx.r11.s64 = ctx.r11.s64 + 1;
    ctx.r11.u64 = std::countl_zero(ctx.r11.u32);
    ctx.r11.u64 = (ctx.r11.u32 >> 5) & 1;
    ctx.r11.u64 ^= 1;
    ctx.cr6.compare<uint32_t>(ctx.r11.u32, 0, ctx.xer);
    if (!ctx.cr6.eq) {
      ctx.r3.u64 = REX_LOAD_U32(ctx.r3.u32 + 0xB0);
      ctx.r11.u64 = REX_LOAD_U32(ctx.r3.u32);
      ctx.r11.u64 = REX_LOAD_U32(ctx.r11.u32 + 4);
      ctx.ctr.u64 = ctx.r11.u64;
      REX_CALL_INDIRECT_FUNC(ctx.ctr.u32);
      return;
    }
  }
  ctx.r11.u64 = REX_LOAD_U32(ctx.r3.u32);
  ctx.r11.u64 = REX_LOAD_U32(ctx.r11.u32 + 0x68);
  ctx.ctr.u64 = ctx.r11.u64;
  REX_CALL_INDIRECT_FUNC(ctx.ctr.u32);
}

constexpr std::array<uint32_t, 19> kOriginal_82322048 = {
  0x816300B0, 0x2B0B0000, 0x419A0034, 0x816B0018, 0x396B0001,
  0x7D6B0034, 0x556BDFFE, 0x696B0001, 0x2B0B0000, 0x419A0018,
  0x806300B0, 0x81630000, 0x816B0004, 0x7D6903A6, 0x4E800420,
  0x81630000, 0x816B0068, 0x7D6903A6, 0x4E800420,
};

} // namespace

void CarbonRegisterMissingEntries(rex::runtime::FunctionDispatcher* dispatcher,
                                  uint8_t* base) {
  // Boot reached an unregistered 0x823503D0 consisting solely of `blr`.
  // Any indirect entry whose actual first instruction is unconditional `blr`
  // has exactly these semantics, including entry directly at an epilogue.
  // Preserve existing generated functions; never replace a non-return opcode.
  size_t return_entries = 0;
  size_t member_adapters = 0;
  for (uint32_t entry = PPCImageConfig.code_base;
       entry < PPCImageConfig.code_base + PPCImageConfig.code_size; entry += 4) {
    if (REX_LOAD_U32(entry) == 0x4E800020 && !dispatcher->GetFunction(entry)) {
      if (!dispatcher->SetFunction(entry, &Carbon_Blr))
        REX_FATAL("Could not register original Carbon return instruction");
      ++return_entries;
    }
    if (REX_LOAD_U32(entry) == kMemberAdapter[0] &&
        entry + kMemberAdapter.size() * 4 <= PPCImageConfig.code_base + PPCImageConfig.code_size &&
        !dispatcher->GetFunction(entry)) {
      bool matches = true;
      for (size_t index = 1; index < kMemberAdapter.size(); ++index) {
        if (REX_LOAD_U32(entry + static_cast<uint32_t>(index * 4)) != kMemberAdapter[index]) {
          matches = false;
          break;
        }
      }
      if (matches) {
        if (!dispatcher->SetFunction(entry, &Carbon_MemberAdapter))
          REX_FATAL("Could not register original Carbon member adapter");
        ++member_adapters;
      }
    }
  }
  REXLOG_INFO("[carbon] Registered {} original blr entries", return_entries);
  REXLOG_INFO("[carbon] Registered {} verified member adapters", member_adapters);
  constexpr uint32_t state_entry = 0x82296468;
  if (!dispatcher->GetFunction(state_entry)) {
    for (size_t index = 0; index < kOriginal_82296468.size(); ++index) {
      if (REX_LOAD_U32(state_entry + static_cast<uint32_t>(index * 4)) != kOriginal_82296468[index])
        REX_FATAL("Carbon executable differs at state entry 0x82296468");
    }
    if (!dispatcher->SetFunction(state_entry, &Carbon_82296468))
      REX_FATAL("Could not register Carbon state entry 0x82296468");
    REXLOG_INFO("[carbon] Registered verified state entry 0x82296468");
  }
  constexpr uint32_t story_entry = 0x822A9490;
  if (!dispatcher->GetFunction(story_entry)) {
    for (size_t index = 0; index < kOriginal_822A9490.size(); ++index) {
      if (REX_LOAD_U32(story_entry + static_cast<uint32_t>(index * 4)) != kOriginal_822A9490[index])
        REX_FATAL("Carbon executable differs at story entry 0x822A9490");
    }
    if (!dispatcher->SetFunction(story_entry, &Carbon_822A9490))
      REX_FATAL("Could not register Carbon story entry 0x822A9490");
    REXLOG_INFO("[carbon] Registered verified story entry 0x822A9490");
  }
  constexpr uint32_t adjustor = 0x82641480;
  if (!dispatcher->GetFunction(adjustor)) {
    if (REX_LOAD_U32(adjustor) != 0x3863FFFC ||
        REX_LOAD_U32(adjustor + 4) != 0x4BFEC68C)
      REX_FATAL("Carbon executable differs at adjustor 0x82641480");
    if (!dispatcher->SetFunction(adjustor, &Carbon_82641480))
      REX_FATAL("Could not register Carbon adjustor 0x82641480");
    REXLOG_INFO("[carbon] Registered verified adjustor 0x82641480");
  }
  constexpr uint32_t address = 0x82322048;
  if (dispatcher->GetFunction(address)) return;
  for (size_t index = 0; index < kOriginal_82322048.size(); ++index) {
    if (REX_LOAD_U32(address + static_cast<uint32_t>(index * 4)) != kOriginal_82322048[index]) {
      REX_FATAL("Carbon executable differs at 0x82322048; refusing incompatible translation");
    }
  }
  if (!dispatcher->SetFunction(address, &Carbon_82322048)) {
    REX_FATAL("Could not register Carbon entry 0x82322048");
  }
  REXLOG_INFO("[carbon] Registered verified PPC entry 0x82322048");
}
