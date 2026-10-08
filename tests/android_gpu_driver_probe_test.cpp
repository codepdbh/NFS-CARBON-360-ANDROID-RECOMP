// SPDX-License-Identifier: GPL-3.0-only
// Probe the same loader as Carbon on Android, with system, missing and invalid drivers.
#include "../android/app/src/main/cpp/android_diagnostics.cpp"
#include <cstdio>
int main(int argc, char** argv) {
  if (argc != 3 && argc != 5) return 2;
  const auto report = Probe(argv[1], argv[2], argc == 5 ? argv[3] : "", argc == 5 ? argv[4] : "");
  std::puts(report.c_str());
}
