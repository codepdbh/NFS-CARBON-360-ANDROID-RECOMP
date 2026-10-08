// SPDX-License-Identifier: GPL-3.0-only
// Command publication adapted from NFSMW a7e6e4c / victorgbd 3d9358e, with Carbon's verified layout.
#pragma once
#include "nfsmw_ordenes_publicadas.h"
#include <algorithm>
#include <cstdint>
#include <cstring>

namespace carbon::commands {
inline uint32_t Read(const uint8_t* bytes) {
  uint32_t value;
  std::memcpy(&value, bytes, sizeof(value));
  return nfsmw::ordenes::Intercambiar(value);
}
// Caller has checked that the list is open. Preserve the game's 16-byte rounding and copy.
inline uint32_t Append(uint8_t* base, uint32_t list, uint32_t source, uint32_t function, uint32_t size) {
  const uint32_t bytes = (size + 15) & ~15u;
  const uint32_t entry = Read(base + list + 20);
  std::memmove(base + entry, base + source, bytes);
  nfsmw::ordenes::Escribir(base + entry, function);
  nfsmw::ordenes::Escribir(base + entry + 4, bytes);
  nfsmw::ordenes::Escribir(base + list + 20, entry + bytes);
  nfsmw::ordenes::Publicar(base + list, nfsmw::ordenes::Publicadas(base + list) + 1);
  return entry;
}
inline uint32_t Available(uint8_t* base, uint32_t list, uint32_t requested) {
  const uint32_t published = nfsmw::ordenes::Publicadas(base + list);
  return std::min(requested, published - Read(base + list + 4));
}
}  // namespace carbon::commands
