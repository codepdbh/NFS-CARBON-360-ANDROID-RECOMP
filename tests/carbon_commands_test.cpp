// SPDX-License-Identifier: GPL-3.0-only
#include "carbon_commands.h"
#include <array>
#include <cassert>
#include <cstdio>
#include <thread>
#include <vector>

int main() {
  alignas(4) std::array<uint8_t, 512> memory{};
  constexpr uint32_t list = 16, source = 96, entry = 160;
  for (unsigned i = 0; i < 64; ++i) memory[source + i] = uint8_t(i);
  nfsmw::ordenes::Escribir(memory.data() + list + 20, entry);
  assert(carbon::commands::Append(memory.data(), list, source, 0x8251BEE0, 17) == entry);
  assert(carbon::commands::Read(memory.data() + entry) == 0x8251BEE0);
  assert(carbon::commands::Read(memory.data() + entry + 4) == 32);
  assert(carbon::commands::Read(memory.data() + list + 20) == entry + 32);
  assert(carbon::commands::Available(memory.data(), list, 100) == 1);
  assert(carbon::commands::Available(memory.data(), list, 0) == 0);
  for (unsigned i = 8; i < 32; ++i) assert(memory[entry + i] == i);
  nfsmw::ordenes::Escribir(memory.data() + list + 4, 1);
  assert(carbon::commands::Available(memory.data(), list, 100) == 0);

  // No recycled entries: producer publishes complete payloads, consumer sees only that snapshot.
  constexpr uint32_t total = 200000;
  std::vector<uint32_t> aligned((total * 16 + 128) / 4);
  auto* base = reinterpret_cast<uint8_t*>(aligned.data());
  constexpr uint32_t queue = 0, payload = 32, first = 64;
  nfsmw::ordenes::Escribir(base + queue + 20, first);
  std::thread producer([&] {
    for (uint32_t i = 0; i < total; ++i) {
      nfsmw::ordenes::Escribir(base + payload + 8, i);
      nfsmw::ordenes::Escribir(base + payload + 12, i ^ 0xACDC1234);
      carbon::commands::Append(base, queue, payload, 0x82000000 + i * 4, 16);
    }
  });
  uint32_t consumed = 0;
  while (consumed < total) {
    uint32_t ready = carbon::commands::Available(base, queue, total);
    for (uint32_t i = 0; i < ready; ++i, ++consumed) {
      const uint32_t offset = first + consumed * 16;
      assert(carbon::commands::Read(base + offset) == 0x82000000 + consumed * 4);
      assert(carbon::commands::Read(base + offset + 4) == 16);
      assert(carbon::commands::Read(base + offset + 8) == consumed);
      assert(carbon::commands::Read(base + offset + 12) == (consumed ^ 0xACDC1234));
    }
    nfsmw::ordenes::Escribir(base + queue + 4, consumed);
  }
  producer.join();
  std::puts("Carbon queue checks passed: padding, function, tail, bounded/zero counts, 200000 ARM publications.");
}
