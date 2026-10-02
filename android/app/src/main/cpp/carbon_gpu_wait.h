// SPDX-License-Identifier: GPL-3.0-only
// Bounded GPU progress waits, adapted from nfsmw_espera_anillo.cpp.
#pragma once
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <mutex>

class CarbonGpuWait {
 public:
  uint32_t generation() const { return generation_.load(std::memory_order_acquire); }
  void notify() {
    bool waiting;
    {
      std::lock_guard<std::mutex> lock(mutex_);
      generation_.fetch_add(1, std::memory_order_release);
      waiting = waiters_ != 0;
    }
    if (waiting) cv_.notify_all();
  }
  bool wait(uint32_t seen) {
    std::unique_lock<std::mutex> lock(mutex_);
    ++waiters_;
    bool changed = cv_.wait_for(lock, std::chrono::microseconds(500), [&] {
      return generation() != seen;
    });
    --waiters_;
    return changed;
  }
 private:
  std::atomic<uint32_t> generation_{0};
  std::mutex mutex_;
  std::condition_variable cv_;
  int waiters_ = 0;
};
