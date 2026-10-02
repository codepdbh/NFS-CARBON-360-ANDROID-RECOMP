# Android source attribution

The following files are adapted from codepdbh/nfsmw-android, revision
5f581c684af5e357a640d3344dc829ba56c8bc79, distributed under GNU GPL version 3:

- GameActivity.java
- TouchControlsView.java
- Diagnostics.java
- DiagnosticsApplication.java
- ReportFileProvider.java
- android_touch.cpp
- report_paths.xml
- build_android.ps1
- Initial Android build configuration
- MainActivity.java launcher styling
- carbon_performance.cpp CPU scheduling, adapted to preserve Android's allowed
  CPU mask and use capacity information when available
- carbon_gpu_wait.h and the Carbon D3D wait hook: bounded progress waits adapted
  from app/src/nfsmw_espera_anillo.cpp, with Carbon's verified device layout
- tools/android_direct_calls.py: build-local direct calls adapted from
  tools/llamadas_directas.py, preserving hooked functions and dispatch tables

Source: https://github.com/codepdbh/nfsmw-android
License: LICENSE in this directory.

The Gradle wrapper retains its own Apache License 2.0 notices.

ReXGlue SDK / Xenia notices are retained in the dependency and in the project's
root LICENSE. SDL Java sources are compiled directly from that dependency's SDL
checkout, with its original license. Game files and locally generated translations
are not part of this repository.

`app/src/main/cpp/carbon_entry.cpp` and `../src/ui/window_sdl.cpp` adapt ReXGlue's
BSD-licensed SDL bootstrap and window implementation for Carbon. Their original
Tom Clay / Xenia copyright notices are retained. The launcher icon was supplied
by the project owner.

The Android build generates an altered SDL_events.c with its full original SDL
license notice: a polling sentinel alone does not wake the Android event loop.
The generated ReXGlue command processor retains its BSD notices and adds GPU
progress notifications. Both changes are local to this build; the shared SDK
sources are unchanged.

`carbon_proc_maps.h` is an original GPL-3.0-only implementation of the live Linux
mapping query used by Carbon's local memory_posix.cpp build variant. It preserves
the SDK's memory protection and resource invalidation behavior.
