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
