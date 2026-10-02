// SPDX-License-Identifier: GPL-3.0-only
#include "../android/app/src/main/cpp/carbon_gpu_wait.h"
#include <cassert>
#include <cstdio>
#include <thread>
#include <vector>

int main() {
  CarbonGpuWait progress;
  auto seen = progress.generation();
  progress.notify();
  // Progress arriving between the pointer check and registering the waiter
  // must not be lost or require another notification.
  assert(progress.wait(seen));
  assert(!progress.wait(progress.generation())); // bounded timeout, no progress
  for (int repeat = 0; repeat < 200; ++repeat) {
    seen = progress.generation();
    std::atomic<int> ready{0};
    std::atomic<int> finished{0};
    std::vector<std::thread> waiters;
    for (int i = 0; i < 3; ++i) waiters.emplace_back([&] {
      ready.fetch_add(1);
      const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(2);
      while (!progress.wait(seen)) assert(std::chrono::steady_clock::now() < deadline);
      finished.fetch_add(1);
    });
    while (ready.load() != 3) std::this_thread::yield();
    progress.notify();
    for (auto& waiter : waiters) waiter.join();
    assert(finished.load() == 3);
  }
  std::puts("GPU waits: missed-wake race, timeout and multiple waiters passed");
}
