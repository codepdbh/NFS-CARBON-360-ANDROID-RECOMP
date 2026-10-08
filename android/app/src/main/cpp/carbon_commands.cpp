// SPDX-License-Identifier: GPL-3.0-only
// Carbon's queue: producer takes r3=list; executor embeds 0x82B8C764.
// Verify against this project's supported XEX, never use Most Wanted's guest addresses.
#include "generated/default/nfscarbon_pch.h"
#include <rex/hook.h>
#include "carbon_commands.h"

namespace {
constexpr uint32_t kList = 0x82B8C764;
}
REX_EXTERN(__imp__sub_824695D8);
REX_HOOK_RAW(sub_824695D8) {
  const uint32_t list = ctx.r3.u32;
  if (list != kList || carbon::commands::Read(base + list + 12) == 0) {
    __imp__sub_824695D8(ctx, base);  // Preserve immediate dispatch and other lists.
    return;
  }
  ctx.r3.u64 = carbon::commands::Append(base, list, ctx.r4.u32, ctx.r5.u32, ctx.r6.u32);
}
REX_EXTERN(__imp__sub_82469650);
REX_HOOK_RAW(sub_82469650) {
  // Acquire the producer's entry before the original reads its function and payload.
  ctx.r4.u64 = carbon::commands::Available(base, kList, ctx.r4.u32);
  __imp__sub_82469650(ctx, base);
}
