// SPDX-License-Identifier: GPL-3.0-only
// Read current Linux mappings without per-line allocations or scanf.
// No protection cache: every query sees the current mapping/protection state.
#pragma once
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <unistd.h>

namespace carbon {
struct MapEntry { uintptr_t start = 0, end = 0; char perms[5] = {}; };
inline bool ParseMapPrefix(const char* text, size_t length, MapEntry& out) {
  size_t i = 0;
  auto hex = [&](uintptr_t& value) {
    value = 0;
    const size_t first = i;
    while (i < length) {
      const unsigned char c = text[i];
      unsigned digit;
      if (c >= '0' && c <= '9') digit = c - '0';
      else if (c >= 'a' && c <= 'f') digit = c - 'a' + 10;
      else if (c >= 'A' && c <= 'F') digit = c - 'A' + 10;
      else break;
      if (value > (std::numeric_limits<uintptr_t>::max() - digit) / 16) return false;
      value = value * 16 + digit;
      ++i;
    }
    return i != first;
  };
  MapEntry entry;
  if (!hex(entry.start) || i >= length || text[i++] != '-' || !hex(entry.end) ||
      entry.start >= entry.end || i >= length || text[i++] != ' ') return false;
  while (i < length && text[i] == ' ') ++i;
  if (length - i < 4) return false;
  if ((text[i] != 'r' && text[i] != '-') || (text[i+1] != 'w' && text[i+1] != '-') ||
      (text[i+2] != 'x' && text[i+2] != '-') || (text[i+3] != 'p' && text[i+3] != 's')) return false;
  std::memcpy(entry.perms, text + i, 4);
  out = entry;
  return true;
}

inline bool FindMapInFd(int fd, uintptr_t address, MapEntry& out, size_t chunk = 8192) {
  char buffer[8192], prefix[80];
  size_t length = 0;
  if (!chunk || chunk > sizeof(buffer)) return false;
  for (;;) {
    ssize_t count = read(fd, buffer, chunk);
    if (count < 0 && errno == EINTR) continue;
    if (count <= 0) break;
    for (ssize_t i = 0; i < count; ++i) {
      if (buffer[i] == '\n') {
        MapEntry entry;
        if (ParseMapPrefix(prefix, length, entry) && address >= entry.start && address < entry.end) {
          out = entry;
          return true;
        }
        length = 0;
      } else if (length < sizeof(prefix)) prefix[length++] = buffer[i];
    }
  }
  MapEntry entry;
  if (length && ParseMapPrefix(prefix, length, entry) && address >= entry.start && address < entry.end) {
    out = entry;
    return true;
  }
  return false;
}
inline bool FindProcMapEntry(void* address, MapEntry& out) {
  const int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
  if (fd < 0) return false;
  const bool found = FindMapInFd(fd, reinterpret_cast<uintptr_t>(address), out);
  close(fd);
  return found;
}
} // namespace carbon
