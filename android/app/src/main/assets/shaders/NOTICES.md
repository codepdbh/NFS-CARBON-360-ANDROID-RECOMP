# Third-party notices

| Component | Where | License |
|---|---|---|
| [DirectXShaderCompiler](https://github.com/microsoft/DirectXShaderCompiler) v2025.1, unmodified, with its bundled SPIRV-Tools and SPIRV-Headers | `wasm/dxc_web.*` | University of Illinois/NCSA Open Source License (LLVM); SPIRV-Tools: Apache-2.0; SPIRV-Headers: MIT |
| [XenosRecomp](https://github.com/hedge-dev/XenosRecomp) by hedge-dev and contributors, with the changes of NFSMW-NX | `wasm/hlsl.*`, `shader_common.h` | MIT |
| [libmspack](https://www.cabextract.org.uk/libmspack/) by Stuart Caie (LZX decoder) | `wasm/lzx.*` | LGPL-2.1 |
| [fmt](https://github.com/fmtlib/fmt) | `wasm/hlsl.*` | MIT |
| [xxHash](https://github.com/Cyan4973/xxHash) by Yann Collet | `wasm/hlsl.*`, `wasm/pack.*` | BSD-2-Clause |
| Shader library code of [NFSMW-NX](https://github.com/StevensND/nfsmw-nx) | `wasm/pack.*` | GPL-3.0 |

libmspack is covered by the GNU Lesser General Public License 2.1. `wasm/lzx.wasm` is built from the unmodified
libmspack sources (`lzxd.c` and `system.c`, at the commit pinned by ReXGlue SDK v0.10.0) and `shaders/nfsmw_lzx.cpp` of
the NFSMW-NX repository, with `shaders/wasm/build_wasm_tools.bat`; with those sources it can be rebuilt and replaced
by a modified version.

`wasm/hlsl.*` and `wasm/pack.*` are built for NFS Carbon (XenosRecomp with `-DNFSMW_RECOMP -DNFSC_RECOMP`, and the
packer that accepts the 2008 shader containers) from https://github.com/codepdbh/nfsmw-android (`shaders/`), with
Emscripten as in `shaders/wasm/build_wasm_tools.bat`. The other modules are those of NFSMW Android Evolved, unchanged.
