// Scheduling adapted from codepdbh/nfsmw-android (5f581c6).
// SPDX-License-Identifier: GPL-3.0-only
// Android scheduling and lightweight frame statistics for Carbon.
#include <jni.h>
#include <sched.h>
#include <unistd.h>
#include <algorithm>
#include <filesystem>
#include <fstream>
#include <vector>
#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/perf/counter.h>
#include <chrono>
#include "nfsmw_nativo_sistema.h"

REXCVAR_DEFINE_BOOL(carbon_fast_cores, true, "Carbon/Android",
                    "Prefer fast CPU cores for newly created game and GPU threads")
    .lifecycle(rex::cvar::Lifecycle::kInitOnly);
REXCVAR_DEFINE_BOOL(carbon_perf_csv, false, "Carbon/Android", "Record guest frame timings for diagnosis")
    .lifecycle(rex::cvar::Lifecycle::kInitOnly);

void CarbonConfigureAndroidCpu() {
  if (!REXCVAR_GET(carbon_fast_cores)) return;
  cpu_set_t allowed;
  if (sched_getaffinity(0, sizeof(allowed), &allowed) != 0) return;
  const long count = sysconf(_SC_NPROCESSORS_CONF);
  if (count < 2 || count > CPU_SETSIZE) return;
  std::vector<long> scores(count, 0);
  int available = 0;
  for (int cpu = 0; cpu < count; ++cpu) if (CPU_ISSET(cpu, &allowed)) ++available;
  auto read_scores = [&](const char* attribute) {
    std::fill(scores.begin(), scores.end(), 0);
    for (int cpu = 0; cpu < count; ++cpu) {
      if (!CPU_ISSET(cpu, &allowed)) continue;
      std::ifstream input("/sys/devices/system/cpu/cpu" + std::to_string(cpu) + attribute);
      if (!(input >> scores[cpu]) || scores[cpu] <= 0) return false;
    }
    return true;
  };
  // Capacity includes microarchitecture differences, even at similar clocks.
  // Some vendor kernels hide it; frequency is a conservative fallback.
  const bool capacity = read_scores("/cpu_capacity");
  if (!capacity && !read_scores("/cpufreq/cpuinfo_max_freq")) return;
  const long fastest = *std::max_element(scores.begin(), scores.end());
  cpu_set_t fast;
  CPU_ZERO(&fast);
  int selected = 0;
  for (int cpu = 0; cpu < count; ++cpu) {
    if (CPU_ISSET(cpu, &allowed) && scores[cpu] >= fastest * 0.7) {
      CPU_SET(cpu, &fast);
      ++selected;
    }
  }
  if (selected < 2 || selected == available) return;
  // Only this startup thread and its future children; never Android's UI,
  // Binder threads, or threads whose affinity belongs to the OS.
  if (sched_setaffinity(0, sizeof(fast), &fast) == 0)
    REXLOG_INFO("[carbon] Game startup on {} fast cores of {} available, selected by {}",
                selected, available, capacity ? "capacity" : "frequency");
}

void CarbonStartAndroidFrameLog(const std::filesystem::path& logs) {
  if (!REXCVAR_GET(carbon_perf_csv)) return;
  std::error_code error;
  std::filesystem::create_directories(logs, error);
  if (!error) rex::perf::SetCsvLogPath((logs / "carbon-frames.csv").string());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nfscarbon_android_GameActivity_nativeFrameTimeUs(JNIEnv*, jclass) {
  // The native renderer does not feed the SDK's frame counters: the average time between its Swaps since
  // the previous call (the launcher asks a few times per second).
  // Not cached: the launcher starts asking before the game has parsed its arguments.
  if (!nfsmw::nativo::Activo()) return rex::perf::GetSnapshotCounter(rex::perf::CounterId::kFrameTimeUs);
  static uint64_t last_swaps = 0;
  static auto last_time = std::chrono::steady_clock::now();
  static jlong last_result = 0;
  const uint64_t swaps = nfsmw::nativo::SwapsNativos();
  const auto now = std::chrono::steady_clock::now();
  const auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(now - last_time).count();
  if (swaps > last_swaps && elapsed >= 250000) {
    last_result = jlong(elapsed / int64_t(swaps - last_swaps));
    last_swaps = swaps;
    last_time = now;
  } else if (elapsed >= 2000000) {
    last_result = 0;  // no frames for two seconds (loading)
    last_swaps = swaps;
    last_time = now;
  }
  return last_result;
}
