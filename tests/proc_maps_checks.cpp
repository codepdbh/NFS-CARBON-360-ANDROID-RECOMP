// SPDX-License-Identifier: GPL-3.0-only
#include "../android/app/src/main/cpp/carbon_proc_maps.h"
#include <cassert>
#include <cstdio>
#include <string>
#include <sys/mman.h>
int main() {
  carbon::MapEntry entry;
  const std::string maps = "1000-2000 r--p 0000 00:00 0 " + std::string(16000, 'x') +
                          "\n2000-4000 rw-p 0000 00:00 0\n5000-6000 ---s 0 00:00 0";
  FILE* file = tmpfile();
  assert(file);
  assert(fwrite(maps.data(), 1, maps.size(), file) == maps.size());
  fflush(file);
  for (size_t chunk : {1u, 7u, 17u, 8192u}) {
    auto find = [&](uintptr_t address) {
      assert(lseek(fileno(file), 0, SEEK_SET) == 0);
      return carbon::FindMapInFd(fileno(file), address, entry, chunk);
    };
    assert(find(0x1000) && entry.start == 0x1000 && std::strcmp(entry.perms, "r--p") == 0);
    assert(find(0x2000) && entry.end == 0x4000 && std::strcmp(entry.perms, "rw-p") == 0);
    assert(!find(0x4000)); // exclusive end, hole
    assert(find(0x5fff) && std::strcmp(entry.perms, "---s") == 0); // EOF without newline
    assert(!find(0x6000));
  }
  fclose(file);
  for (const char* bad : {"", "f-1 r--p", "1000-2000 rw", "10000000000000000-2000 rw-p", "1-2 zzzp"})
    assert(!carbon::ParseMapPrefix(bad, std::strlen(bad), entry));
  const size_t size = sysconf(_SC_PAGESIZE);
  void* page = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  assert(page != MAP_FAILED);
  assert(carbon::FindProcMapEntry(page, entry) && entry.perms[1] == 'w');
  assert(mprotect(page, size, PROT_READ) == 0);
  assert(carbon::FindProcMapEntry(page, entry) && entry.perms[1] == '-');
  assert(mprotect(page, size, PROT_READ | PROT_WRITE) == 0);
  assert(carbon::FindProcMapEntry(page, entry) && entry.perms[1] == 'w');
  assert(munmap(page, size) == 0);
  std::puts("Memory maps: chunk boundaries, long paths, holes, overflow and live mprotect changes passed");
}
