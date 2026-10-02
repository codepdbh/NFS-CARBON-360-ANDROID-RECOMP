// SPDX-License-Identifier: GPL-3.0-only
// Carbon's own render-mode table, verified against the supported Xbox 360 XEX.
#include "generated/default/nfscarbon_pch.h"
#include <rex/cvar.h>
#include <rex/hook.h>
#include <rex/system/flags.h>
#include <rex/filesystem/vfs.h>
#include <rex/filesystem/entry.h>
#include <filesystem>
#include <algorithm>
#include <cctype>
#include <array>
#include <atomic>
#include <string>
#include <vector>

REXCVAR_DEFINE_STRING(carbon_resolution, "1024x576", "Carbon/Android", "Scene and output resolution")
    .allowed({"640x360", "1024x576", "1280x720"}).lifecycle(rex::cvar::Lifecycle::kInitOnly);
REXCVAR_DEFINE_BOOL(carbon_single_pass, true, "Carbon/Android", "Use one scene tile without MSAA")
    .lifecycle(rex::cvar::Lifecycle::kInitOnly);

namespace {
constexpr uint32_t kRenderer = 0x82C65304;
constexpr uint32_t kLanguage = 0x82B78454;
bool verified = false;
bool chose_language = false;
std::pair<uint32_t, uint32_t> Size() {
  const auto& size = REXCVAR_GET(carbon_resolution);
  if (size == "640x360") return {640, 360};
  if (size == "1024x576") return {1024, 576};
  return {1280, 720};
}
void OutputSize(uint8_t* base) {
  if (!verified || !REXCVAR_GET(carbon_single_pass)) return;
  auto [width, height] = Size();
  REX_STORE_U32(0x82C651E8, width);
  REX_STORE_U32(0x82C651F0, width);
  REX_STORE_U32(0x82C651EC, height);
  REX_STORE_U32(0x82C651F4, height);
}
void SelectMode(uint8_t* base) {
  if (!verified || !REXCVAR_GET(carbon_single_pass)) return;
  const uint32_t renderer = REX_LOAD_U32(kRenderer);
  if (renderer) REX_STORE_U32(renderer, 2); // Retail's single tile / no MSAA mode.
}
}

void CarbonVerifyGraphicsHooks(uint8_t* base) {
  const std::array<std::pair<uint32_t, uint32_t>, 15> words = {{
      {0x824FFD30, 0x7D8802A6}, {0x824FFD38, 0x38E301E4},
      {0x824FFD3C, 0x39430064}, {0x824FFD40, 0x38C300A4},
      {0x8250B350, 0x7D8802A6}, {0x8250B360, 0x4859FFF5},
      {0x825003E8, 0x54AB063E}, {0x82500410, 0x2F040000},
      {0x8231F5A8, 0x7D8802A6}, {0x8231F5BC, 0x3FC082B8},
      {0x82B7835C, 0x8207B0FC}, // Spanish row in the language resource table.
      {0x826DEFD0, 0x7D8802A6}, {0x826DEFD8, 0x9421FF80},
      {0x826DF034, 0x817D2A10}, {0x826DF040, 0x814B0000},
  }};
  for (auto [address, value] : words)
    if (REX_LOAD_U32(address) != value) REX_FATAL("Carbon graphics/language hook fingerprint mismatch");
  verified = true;
  chose_language = false;
}

// The profile identifies this D3D ring wait as the hottest guest function.
// Keep its checks and timeout logic, but sleep until progress instead of spinning.
// Read the generation before the pointer, so progress between checking and waiting
// cannot lose a notification. The bounded wait also covers other GPU updates.
extern "C" uint32_t CarbonGpuGeneration();
extern "C" void CarbonWaitGpu(uint32_t);
REX_EXTERN(__imp__sub_826DEFD0);
REX_HOOK_RAW(sub_826DEFD0) {
  if (verified) {
    const uint32_t seen = CarbonGpuGeneration();
    const uint32_t state = ctx.r3.u32;
    const uint32_t device = state ? REX_LOAD_U32(state) : 0;
    if (device && !(REX_LOAD_U8(device + 10813) & 4)) {
      const uint32_t pointer = REX_LOAD_U32(device + 10768);
      if (pointer && REX_LOAD_U32(pointer) == REX_LOAD_U32(state + 8))
        CarbonWaitGpu(seen);
    }
  }
  __imp__sub_826DEFD0(ctx, base);
}

void CarbonConfigureMovieFallback(rex::filesystem::VirtualFileSystem* vfs,
                                  const std::filesystem::path& game_root) {
  // Reuse the existing English video when this copy lacks a localized one.
  // VFS aliases consume no extra storage and never replace a supplied dubbing.
  std::error_code error;
  const auto movies = game_root / "Movies";
  std::vector<std::string> filenames;
  for (const auto& file : std::filesystem::directory_iterator(movies, error)) {
    if (file.is_regular_file(error)) {
      auto name = file.path().filename().string();
      std::transform(name.begin(), name.end(), name.begin(), [](unsigned char c) { return std::toupper(c); });
      filenames.push_back(std::move(name));
    }
  }
  if (error) return;
  size_t aliases = 0;
  for (const auto& file : std::filesystem::directory_iterator(movies, error)) {
    std::string source = file.path().filename().string();
    std::string uppercase = source;
    std::transform(uppercase.begin(), uppercase.end(), uppercase.begin(), [](unsigned char c) { return std::toupper(c); });
    const size_t english = uppercase.find("ENGLISH");
    if (english == std::string::npos || !uppercase.ends_with(".WMV")) continue;
    auto* entry = vfs->ResolvePath("D:\\Movies\\" + source);
    if (!entry) continue;
    const std::string target = entry->absolute_path();
    const std::string parent = target.substr(0, target.find_last_of('\\') + 1);
    for (const char* language : {"SPANISH", "FRENCH", "GERMAN", "ITALIAN"}) {
      std::string alias = uppercase;
      alias.replace(english, 7, language);
      if (std::find(filenames.begin(), filenames.end(), alias) == filenames.end()) {
        vfs->RegisterSymbolicLink(parent + alias, target);
        ++aliases;
      }
    }
  }
  REXLOG_WARN("[carbon] Missing localized movies: {} aliases to existing English videos", aliases);
}

REX_EXTERN(__imp__sub_824FFD30);
REX_HOOK_RAW(sub_824FFD30) {
  const uint32_t renderer = ctx.r3.u32;
  __imp__sub_824FFD30(ctx, base);
  if (!verified || !REXCVAR_GET(carbon_single_pass)) return;
  auto [width, height] = Size();
  constexpr uint32_t mode = 2;
  REX_STORE_U32(renderer + 4 + mode * 4, 1);
  REX_STORE_U32(renderer + 28 + mode * 4, width);
  REX_STORE_U32(renderer + 52 + mode * 4, (height + 31) & ~31u);
  REX_STORE_U32(renderer + 76 + mode * 4, 0);
  // Both the rendering tile and its resolve rectangle use the chosen extent.
  for (uint32_t table : {100u, 484u}) {
    const uint32_t rect = renderer + table + mode * 64;
    REX_STORE_U32(rect, 0); REX_STORE_U32(rect + 4, 0);
    REX_STORE_U32(rect + 8, width); REX_STORE_U32(rect + 12, height);
  }
  REXLOG_WARN("[carbon] Scene mode 2: {}x{}, 1 tile, MSAA off", width, height);
}

REX_EXTERN(__imp__sub_8250B350);
REX_HOOK_RAW(sub_8250B350) {
  __imp__sub_8250B350(ctx, base);
  OutputSize(base);
  SelectMode(base);
}

REX_EXTERN(__imp__sub_825003E8);
REX_HOOK_RAW(sub_825003E8) {
  if (verified && REXCVAR_GET(carbon_single_pass)) { ctx.r4.u64 = 2; ctx.r5.u64 = 1; }
  __imp__sub_825003E8(ctx, base);
  if (REXCVAR_GET(carbon_single_pass)) OutputSize(base);
}

REX_EXTERN(__imp__sub_8231F5A8);
REX_HOOK_RAW(sub_8231F5A8) {
  // Retail explicitly requests ENGLISH (0) at startup instead of the console language.
  // Override that first request only; preserve unloading (-1) and in-game language changes.
  if (verified && !chose_language && ctx.r3.s32 == 0) {
    chose_language = true;
    uint32_t language = 0;
    switch (REXCVAR_GET(user_language)) {
      case 5: language = 4; break; // Spanish
      case 4: language = 1; break; // French
      case 3: language = 2; break; // German
      case 6: language = 3; break; // Italian
      default: break; // Unsupported resource sets keep retail English.
    }
    ctx.r3.u64 = language;
    REXLOG_WARN("[carbon] Initial text language {} (Xbox language {})", language, REXCVAR_GET(user_language));
  }
  __imp__sub_8231F5A8(ctx, base);
}
