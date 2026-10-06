# NFS CARBON 360 DECOMP

**English** · [Español](README.es.md)

An experimental static recompilation of **Need for Speed Carbon for Xbox 360**, based on ReXGlue. It translates the
PowerPC code of a copy of the game to C++ and builds it for Android ARM64 (Vulkan) and Windows x64 (Xenos / Direct3D
12). The project's name does not mean that the original source code was recovered.

**Status on 6 October 2026:** on Android the game renders completely (cars, scenery, videos and menus) with the native
renderer of [NFSMW Android Evolved](https://github.com/codepdbh/nfsmw-android), at about 60 FPS on a Samsung S25
Ultra (Adreno 830) at 1280×720 and also at 1920×1080. There are still occasional hitches, and full races, saved
progress and other phones still need testing.

The repository holds the project, the analyser settings and the tools. The game data, the original executable, the
dumps, the generated C++ and the shader library come from your own copy and are not distributed here.

## The game copy

You need an extracted copy of **the Xbox 360 edition** of Carbon (a PC copy does not work), with `default.xex` and its
original `NFS` and `Movies` folders. The tested copy is:

| Field | Value |
| --- | --- |
| Title ID | `454107EC` |
| Media ID | `5E74E60D` |
| Version | `0.0.0.11` |
| Entry point | `0x82943460` |
| `default.xex` SHA-256 | `b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221` |

It is the English PAL edition: its **text** is in English, Spanish, French, German and Italian, but its
**voices, race intros and videos are only in English**. Other editions and regions have not been tested; the launcher
refuses another `default.xex`, because the recompiled code and the hooks depend on it.

## Android

An app separate from Most Wanted (package `com.nfscarbon.android`): full touch controls with a layout editor,
Bluetooth controllers, manual log reports and a launcher with settings.

**Requirements:** Android 8.0 or later, ARM64, Vulkan 1.1 or later and about 6 GB free for the game. Tested on an S25
Ultra (Adreno 830). The native renderer is the one of Most Wanted, which already runs on Adreno, Mali (MediaTek /
Dimensity, with its Mali occlusion query protection) and other Vulkan 1.1 GPUs; the Vulkan 1.1 path was also checked
with Carbon. Carbon itself has not been tried on those phones yet: if you do, please send the log.

1. Install the APK.
2. Copy your Xbox 360 copy to **Internal storage/NFSCARBON** (with `default.xex`, `NFS` and `Movies` directly inside),
   or use **Importar mi copia**. Importing makes a new copy and keeps the previous one as a backup, so it needs extra
   space.
3. Open the launcher, tap **Permitir acceso** and grant access to files.
4. Choose your settings and tap **Jugar**. The first time, the app builds the native renderer's shaders from your
   files (a few seconds; it only happens again if their version changes).

The APK contains neither the game nor anything derived from it.

### Launcher settings

- **Renderizador:** *Nativo* (default) draws with Vulkan directly, with Most Wanted's renderer adapted to Carbon;
  *Xenos* emulates the Xbox 360 GPU (more compatible in principle, much slower).
- **Atajos del renderizador (MW):** optimisations measured in Most Wanted, off by default until they are validated in
  Carbon.
- **Idioma del juego:** language of the text.
- **Idioma de las voces:** *Inglés* (default, what the English PAL copy has) or *Igual que los textos*, for dubbed
  copies. With a copy that has no voices in your language, *Igual que los textos* leaves the race intro audio
  incomplete.
- **Resolución interna:** from 640×360 to 1920×1080. Above 720p only with the native renderer (Xenos stays at
  1280×720, because the scene has to fit in the emulated EDRAM).
- **Ritmo objetivo:** 30, 60, 90, 120 or unlimited (the high ones are experimental).
- **Scene antialiasing**, **vertical sync**, **shader compilation and workers** (Xenos), **game CPU** (prefer fast
  cores), **FPS counter** and **logs**.

With the native renderer the session log is in `Android/data/com.nfscarbon.android/files/logs`. For testing, every line
starting with `--` in `Android/data/com.nfscarbon.android/files/args.txt` is added to the game's arguments (for
example `--nfsmw_nativo_simular_vulkan11=true`).

### Native renderer

It is built from `nfsmw-android/app/src` with `NFSC_RECOMP` and Carbon's hooks (`src/carbon_nativo_ganchos.cpp`).
[docs/renderizador-nativo.md](docs/renderizador-nativo.md) (Spanish) lists the Direct3D functions and offsets found in
Carbon, what had to be added to the renderer (7e3 render targets, rectangle lists, identifying the vertex shaders that
Carbon's D3D reorders) and what is still pending.

The shader library is built on the phone (`assets/shaders/build.js`, in a WebView): it finds the game's 2008 shader
containers, adds the three vertex shaders of D3D itself (loose microcode in the xex), translates them with XenosRecomp
and DXC compiled to WebAssembly, and checks the SHA-256 expected for your executable. On a PC,
`tools/biblioteca_shaders_carbon.mjs` does the same and gives the same library, byte for byte.

## Building

Clone both projects side by side and pin the dependency to the tested revision:

```powershell
git clone https://github.com/codepdbh/NFS-CARBON-360-DECOMP.git
git clone https://github.com/codepdbh/nfsmw-android.git
git -C nfsmw-android checkout c2cd03742c24a76d0c349512a09d1244fe6e456d
python nfsmw-android/tools/fetch_thirdparty.py
python -m pip install -r NFS-CARBON-360-DECOMP/requirements.txt
```

Put your extracted copy in a sibling folder called `Need_for_Speed_Carbon`:

```text
work-folder/
  NFS-CARBON-360-DECOMP/
  nfsmw-android/
  Need_for_Speed_Carbon/
    default.xex
    NFS/
    Movies/
```

Tools: Visual Studio / Build Tools with C++ x64 and the Windows SDK, LLVM / Clang 20 (tested 20.1.8), CMake 3.30.5 and
Ninja, Python 3 with `capstone` 5.0.7. For Android also JDK 17+, the Android SDK with API 35 and NDK `28.2.13676358`.

### Code generation (PC)

From the Carbon repository, say where LLVM, CMake and Ninja are (CMake and Ninja in the same folder):

```powershell
cd NFS-CARBON-360-DECOMP
$carbonBuildOptions = @{
    SdkRoot = '..\nfsmw-android\sdk'
    LlvmBin = 'C:\tools\llvm20\bin'
    CMakeBin = 'C:\tools\cmake\bin'
}
.\build_pc.ps1 @carbonBuildOptions
```

It generates the code, repairs the local branches that can be checked and builds `out/pc/nfscarbon.exe`. To finish
recovering the small functions, start a session with the local dump, close it when the logos appear and build again:

```powershell
.\run_pc.ps1 -DumpImage
.\build_pc.ps1 @carbonBuildOptions
.\build_pc.ps1 -EntryChecks @carbonBuildOptions
.\run_pc.ps1
```

The dump is written to `out/runtime/cache/carbon-82000000.bin` and is ignored by Git, like the logs in `out/runtime/`.

### APK

With the code generation and the local recovery done:

```powershell
.\build_android.ps1
```

The APK is `android/app/build/outputs/apk/release/app-release.apk`, signed with the local debug key (not a stable
signature for public releases). The `assets/shaders/wasm/hlsl.*` and `pack.*` modules are built with Emscripten from
`nfsmw-android/shaders` (see `assets/shaders/NOTICES.md`).

### PC

The PC build uses Xenos on Direct3D 12 and is meant for testing. Controls:

| Key | Controller |
| --- | --- |
| Enter | Start |
| Space | A / accept |
| Backspace | B / back |
| WASD | Left stick |
| E / O | Right trigger |
| Q / I | Left trigger |
| L | X |
| P | Y |

## Analysis fixes

- `src/codegen/phase_gapfill.cpp`: splits the functions that end in an unconditional indirect branch; the last
  generation found 72,822 entries.
- `tools/fix_local_branches.py`: fixes the local branches whose target already has a label in the same function.
- `src/carbon_missing_entries.cpp` and `tools/recover_small_entries.py` / `verify_small_entries.py`: missing entries,
  checked against the original bytes and Capstone.
- `tests/`: checks of story entries, the GPU wait, the memory map reader and the Android direct calls.

## Diagnostics and reports

On Android, **Enviar crash o log** prepares a ZIP with the app's logs and the device diagnostics; nothing is sent on
its own. You can also open an [issue](https://github.com/codepdbh/NFS-CARBON-360-DECOMP/issues) with your phone, GPU
and the steps. Do not attach game data or dumps of the executable.

On PC the log is `out/runtime/carbon.log`; `.\run_pc.ps1 -GpuDiagnostics` turns on the D3D12 debug layer and DRED, and
`python tools/inspect_image.py 0x824DAAA0` inspects an address with the local dump.

## Credits

Based on the ReXGlue SDK, with code derived from Xenia, and on NFSMW Android Evolved (its SDK, its native renderer, the
controls, the SDL lifecycle and the report helpers, under GPL-3.0, kept in `android/LICENSE`; see also
`android/NOTICE.md`). The modified copy of `phase_gapfill.cpp` keeps Tom Clay's attribution and the three-clause BSD
license in `LICENSE`. Need for Speed Carbon belongs to its owners. An independent community project.
