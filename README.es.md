# NFS CARBON 360 DECOMP

[English](README.md) · **Español**

Recompilación estática experimental de **Need for Speed Carbon para Xbox 360**, basada en ReXGlue. Traduce el código
PowerPC de una copia del juego a C++ y lo compila para Android ARM64 (Vulkan) y Windows x64 (Xenos / Direct3D 12).
El nombre del proyecto no implica que se haya recuperado el código fuente original.

**Estado al 6 de octubre de 2026:** en Android el juego se ve completo (coches, escenario, vídeos y menús) con el
renderizador nativo de [NFSMW Android Evolved](https://github.com/codepdbh/nfsmw-android), a unos 60 FPS en un
Samsung S25 Ultra (Adreno 830) a 1280×720 y también a 1920×1080. Todavía hay tirones ocasionales y falta validar
carreras completas, el progreso guardado y otros teléfonos.

El repositorio contiene el proyecto, los ajustes del analizador y las herramientas. Los datos del juego, el ejecutable
original, los volcados, el C++ generado y la biblioteca de shaders se obtienen de tu propia copia y no se distribuyen
aquí.

## La copia del juego

Hace falta una copia extraída de **la edición Xbox 360** de Carbon (una copia de PC no sirve), con `default.xex` y sus
carpetas originales `NFS` y `Movies`. La copia comprobada es esta:

| Campo | Valor |
| --- | --- |
| Title ID | `454107EC` |
| Media ID | `5E74E60D` |
| Versión | `0.0.0.11` |
| Entry point | `0x82943460` |
| SHA-256 de `default.xex` | `b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221` |

Es la edición PAL en inglés: trae los **textos** en inglés, español, francés, alemán e italiano, pero las
**voces, presentaciones de carrera y vídeos solo en inglés**. Otras ediciones y regiones no se han probado; el launcher
rechaza otro `default.xex` porque el código recompilado y los ganchos dependen de él.

## Android

App independiente de Most Wanted (paquete `com.nfscarbon.android`): mando táctil completo con editor de posiciones,
mandos Bluetooth, envío manual de registros y launcher con ajustes.

**v0.3.2:** incorpora los arreglos de compatibilidad de Most Wanted 0.5.4: GPU con cuatro conjuntos de descriptores,
conversión BC4/BC5 para Xclipse, publicación de órdenes con sincronización ARM adaptada a Carbon, importación y
prueba de drivers Vulkan y avisos opcionales de nuevas releases. Conserva el launcher, idiomas y controles de Carbon.
Descarga el APK en [Releases](https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP/releases/latest) e instálalo encima del
anterior **sin desinstalar ni borrar datos** para conservar partidas y ajustes.

**Requisitos:** Android 8.0 o superior, ARM64, Vulkan 1.1 o superior **con las funciones de descriptor indexing
necesarias para el renderizador nativo**, y unos 6 GB libres para el juego. La versión de Vulkan por sí sola no
garantiza esas funciones; el launcher las comprueba. Los drivers externos requieren Android 9 o posterior. Probado en un
S25 Ultra (Adreno 830). El renderizador nativo es el mismo que el de Most Wanted, que ya funciona en Adreno, Mali
(MediaTek / Dimensity, con la protección de consultas de oclusión de Mali) y otros con Vulkan 1.1; el camino de
Vulkan 1.1 se comprobó también con Carbon. Aun así, Carbon todavía no se ha probado en esos teléfonos: si lo pruebas,
envía el registro.

1. Instala el APK.
2. Copia tu copia Xbox 360 a **Memoria interna/NFSCARBON** (con `default.xex`, `NFS` y `Movies` directamente dentro), o
   usa **Importar mi copia**. La importación crea una copia nueva y conserva la anterior como respaldo: necesita
   espacio adicional.
3. Abre el launcher, pulsa **Permitir acceso** y concede el acceso a los archivos.
4. Elige los ajustes y pulsa **Jugar**. La primera vez, la app genera los shaders del renderizador nativo a partir de
   tus archivos (unos segundos; solo se repite si cambia la versión).

El APK no incluye el juego ni ningún dato derivado de él. Consulta la [guía de compatibilidad y drivers](docs/compatibilidad-android-0.3.2.md).

### Ajustes del launcher

- **Driver Vulkan:** importa ZIPs con `meta.json` y bibliotecas Android ARM64, selecciona el driver o vuelve al del
  sistema. **Probar driver** muestra la GPU, Vulkan y las funciones disponibles en un proceso separado. Turnip es para
  Adreno y PanVK para Mali; cada paquete debe admitir tu GPU, Android y kernel. No se incluyen paquetes de drivers.
- **Buscar actualizaciones:** comprueba la última release estable de este repositorio. El aviso automático permite
  **Actualizar** o **Más tarde**; no bloquea el juego, no instala APKs ni requiere conexión para jugar.
- **Renderizador:** *Nativo* (por defecto) dibuja con Vulkan directamente, con el renderizador de Most Wanted
  adaptado a Carbon; *Xenos* emula la GPU de la Xbox 360 (más compatible en teoría, mucho más lento).
- **Atajos del renderizador (MW):** optimizaciones medidas en Most Wanted, desactivadas por defecto hasta validarlas
  en Carbon.
- **Idioma del juego:** idioma de los textos.
- **Idioma de las voces:** *Inglés* (por defecto, lo que trae la copia PAL inglesa) o *Igual que los textos*, para
  copias dobladas. Con una copia sin voces en tu idioma, *Igual que los textos* deja el audio de las presentaciones de
  carrera incompleto.
- **Resolución interna:** de 640×360 a 1920×1080. Por encima de 720p solo con el renderizador nativo (Xenos se queda
  en 1280×720 porque la escena tiene que caber en la EDRAM emulada).
- **Ritmo objetivo:** 30, 60, 90, 120 o sin límite (los altos son experimentales).
- **Antialiasing de la escena**, **sincronización vertical**, **compilación y trabajadores de shaders** (Xenos), **CPU
  del juego** (preferir núcleos rápidos), **contador de FPS** y **registros**.

Con el renderizador nativo el registro de la sesión queda en `Android/data/com.nfscarbon.android/files/logs`. Para
pruebas, cada línea que empiece por `--` en `Android/data/com.nfscarbon.android/files/args.txt` se añade a los
argumentos del juego (por ejemplo `--nfsmw_nativo_simular_vulkan11=true`).

### Renderizador nativo

Se compila desde `nfsmw-android/app/src` con `NFSC_RECOMP` y los ganchos de Carbon (`src/carbon_nativo_ganchos.cpp`).
En [docs/renderizador-nativo.md](docs/renderizador-nativo.md) están las funciones y desplazamientos de Direct3D
encontrados en Carbon, lo que hubo que añadir al renderizador (destinos 7e3, listas de rectángulos, la identificación
de los vertex shaders que reordena el D3D de Carbon) y lo pendiente.

La biblioteca de shaders se genera en el teléfono (`assets/shaders/build.js`, en una WebView): busca los contenedores
de shaders de 2008 del juego, añade los tres vertex shaders propios del D3D (microcódigo suelto en el xex), los
traduce con XenosRecomp y DXC compilados a WebAssembly y comprueba el SHA-256 esperado para tu ejecutable. En PC,
`tools/biblioteca_shaders_carbon.mjs` hace lo mismo y da la misma biblioteca, byte a byte.

## Compilar

Clona ambos proyectos en carpetas hermanas y fija la dependencia a la revisión probada:

```powershell
git clone https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP.git
git clone https://github.com/codepdbh/nfsmw-android.git
git -C nfsmw-android checkout a7e6e4c3ca71064d0eea56d91fcc8aaf63418fc7
python nfsmw-android/tools/fetch_thirdparty.py
python -m pip install -r NFS-CARBON-360-ANDROID-RECOMP/requirements.txt
```

Coloca tu copia extraída en una carpeta hermana llamada `Need_for_Speed_Carbon`:

```text
carpeta-de-trabajo/
  NFS-CARBON-360-ANDROID-RECOMP/
  nfsmw-android/
  Need_for_Speed_Carbon/
    default.xex
    NFS/
    Movies/
```

Herramientas: Visual Studio / Build Tools con C++ x64 y Windows SDK, LLVM / Clang 20 (probado 20.1.8), CMake 3.30.5 y
Ninja, Python 3 con `capstone` 5.0.7. Para Android además JDK 17+, Android SDK con API 35 y NDK `28.2.13676358`.

### Generación de código (PC)

Desde el repositorio de Carbon, indica dónde están LLVM, CMake y Ninja (CMake y Ninja en la misma carpeta):

```powershell
cd NFS-CARBON-360-ANDROID-RECOMP
$carbonBuildOptions = @{
    SdkRoot = '..\nfsmw-android\sdk'
    LlvmBin = 'C:\tools\llvm20\bin'
    CMakeBin = 'C:\tools\cmake\bin'
}
.\build_pc.ps1 @carbonBuildOptions
```

Genera el código, repara los saltos locales comprobables y construye `out/pc/nfscarbon.exe`. Para completar la
recuperación de las funciones pequeñas, inicia una sesión con el volcado local, ciérrala al ver los logos y vuelve a
compilar:

```powershell
.\run_pc.ps1 -DumpImage
.\build_pc.ps1 @carbonBuildOptions
.\build_pc.ps1 -EntryChecks @carbonBuildOptions
.\run_pc.ps1
```

El volcado queda en `out/runtime/cache/carbon-82000000.bin` y está excluido de Git, igual que los registros de
`out/runtime/`.

### APK

Con la generación de código y la recuperación local hechas:

```powershell
.\build_android.ps1
```

El APK queda en `android/app/build/outputs/apk/release/app-release.apk`, firmado con la clave de depuración local (no
es una firma estable para versiones públicas). Los módulos `assets/shaders/wasm/hlsl.*` y `pack.*` se compilan con
Emscripten desde `nfsmw-android/shaders` (ver `assets/shaders/NOTICES.md`).

### PC

La versión de PC usa Xenos sobre Direct3D 12 y sirve para pruebas. Controles:

| Tecla | Acción del mando |
| --- | --- |
| Enter | Start |
| Espacio | A / aceptar |
| Backspace | B / volver |
| WASD | Joystick izquierdo |
| E / O | Gatillo derecho |
| Q / I | Gatillo izquierdo |
| L | X |
| P | Y |

## Correcciones del análisis

- `src/codegen/phase_gapfill.cpp`: separa las funciones que terminan en un salto indirecto incondicional; la última
  generación identificó 72.822 entradas.
- `tools/fix_local_branches.py`: corrige los saltos locales cuyo destino ya tiene etiqueta en la misma función.
- `src/carbon_missing_entries.cpp` y `tools/recover_small_entries.py` / `verify_small_entries.py`: entradas que
  faltaban, comprobadas contra los bytes originales y Capstone.
- `tests/`: comprobaciones de entradas de historia, de la espera de la GPU, de la lectura del mapa de memoria y de
  las llamadas directas de Android.

## Diagnóstico y reportes

En Android, **Enviar crash o log** prepara un ZIP con los registros de la app y el diagnóstico del dispositivo; no se
envía nada automáticamente. También puedes abrir un
[issue](https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP/issues) indicando el teléfono, la GPU y los pasos. No
adjuntes datos del juego ni volcados del ejecutable.

En PC el registro está en `out/runtime/carbon.log`; `.\run_pc.ps1 -GpuDiagnostics` activa la capa de depuración de
D3D12 y DRED, y `python tools/inspect_image.py 0x824DAAA0` inspecciona una dirección con el volcado local.

## Créditos

Basado en ReXGlue SDK, con código derivado de Xenia, y en NFSMW Android Evolved (su SDK, su renderizador nativo, los
controles, el ciclo de vida SDL y los ayudantes de informes, bajo GPL-3.0, conservada en `android/LICENSE`; consulta
también `android/NOTICE.md`). La copia modificada de `phase_gapfill.cpp` conserva la atribución de Tom Clay y la
licencia BSD de tres cláusulas en `LICENSE`. Need for Speed Carbon pertenece a sus titulares. Proyecto comunitario
independiente.
