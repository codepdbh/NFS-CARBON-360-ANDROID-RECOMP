# NFS CARBON 360 DECOMP

Recompilación estática experimental de **Need for Speed Carbon para Xbox 360**,
basada en ReXGlue. Traduce el código PowerPC de una copia del juego a C++ y lo
compila para Windows x64, con el backend gráfico Xenos / Direct3D 12.
El nombre del proyecto no implica que se haya recuperado el código fuente original.

**Estado al 1 de octubre de 2026:** el usuario confirmó las intros con imagen y
sonido, el menú principal y el acceso a la historia después de la carga.
Todavía falta validar carreras completas, progreso guardado y otros equipos.
Es una prueba para PC; actualmente no hay APK Android.

El repositorio contiene el proyecto, los ajustes del analizador y las herramientas
de diagnóstico. Los datos del juego, el ejecutable original, los volcados y el
C++ generado se obtienen localmente de tu propia copia y no se distribuyen aquí.

## Requisitos

- Windows x64 y GPU compatible con Direct3D 12. Equipo probado: Radeon 780M.
- Una copia extraída de **la edición Xbox 360** de Carbon, con `default.xex` y
  sus carpetas originales, incluida `NFS`. Una copia de PC no sirve como entrada.
- Visual Studio / Build Tools con las herramientas de C++ x64 y Windows SDK.
- LLVM / Clang 20; versión probada: 20.1.8.
- CMake y Ninja; CMake probado: 3.30.5.
- Python 3 y `capstone` 5.0.7.
- El SDK de la revisión indicada de [NFSMW Android Evolved](https://github.com/codepdbh/nfsmw-android),
  utilizado como dependencia de compilación. Carbon mantiene sus correcciones
  del analizador dentro de este proyecto.

La copia comprobada tiene estos identificadores:

| Campo | Valor |
| --- | --- |
| Title ID | `454107EC` |
| Media ID | `5E74E60D` |
| Versión | `0.0.0.11` |
| Entry point | `0x82943460` |
| SHA-256 de `default.xex` | `b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221` |

Los nombres de los videos indican PAL / inglés. Otras ediciones y regiones aún
no se han probado; los complementos verifican los bytes originales antes de
registrar sus funciones.

## Preparación y compilación

Clona ambos proyectos en carpetas hermanas. Fija la dependencia a la revisión
utilizada en esta prueba:

```powershell
git clone https://github.com/codepdbh/NFS-CARBON-360-DECOMP.git
git clone https://github.com/codepdbh/nfsmw-android.git
git -C nfsmw-android checkout 5f581c684af5e357a640d3344dc829ba56c8bc79
python -m pip install -r NFS-CARBON-360-DECOMP/requirements.txt
```

Coloca tu copia extraída en una carpeta hermana llamada `Need_for_Speed_Carbon`:

```text
carpeta-de-trabajo/
  NFS-CARBON-360-DECOMP/
  nfsmw-android/
    sdk/
  Need_for_Speed_Carbon/
    default.xex
    NFS/
    ...resto de los archivos originales...
```

Desde el repositorio de Carbon, indica dónde están LLVM, CMake y Ninja. CMake y
Ninja deben estar en la misma carpeta para este script. Sustituye los ejemplos
por tus rutas reales:

```powershell
cd NFS-CARBON-360-DECOMP
$carbonBuildOptions = @{
    SdkRoot = '..\nfsmw-android\sdk'
    LlvmBin = 'C:\tools\llvm20\bin'
    CMakeBin = 'C:\tools\cmake\bin'
}
.\build_pc.ps1 @carbonBuildOptions
```

El script genera el código, repara los saltos locales comprobables y construye
`out/pc/nfscarbon.exe`. La primera compilación descarga las dependencias del SDK
y puede tardar varios minutos. Los valores por defecto reutilizan las herramientas
de la instalación local de NFSMW y del Android SDK del desarrollador; las opciones
anteriores permiten usar otras ubicaciones.

Para completar la recuperación de las funciones pequeñas usada en esta prueba,
inicia una primera sesión con el volcado local habilitado:

```powershell
.\run_pc.ps1 -DumpImage
```

Cuando aparezcan los logos, cierra el juego y vuelve a compilar. El script detecta
el volcado, genera las entradas pequeñas, las verifica con Capstone y las incluye
en el ejecutable:

```powershell
.\build_pc.ps1 @carbonBuildOptions
.\build_pc.ps1 -EntryChecks @carbonBuildOptions
.\run_pc.ps1
```

El volcado queda en `out/runtime/cache/carbon-82000000.bin` y está excluido de Git.
También están excluidos los perfiles y registros de `out/runtime/`.

## Controles

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

Los mandos usan el backend SDL. La compatibilidad de cada mando sigue pendiente.

## Correcciones y validación

- `src/codegen/phase_gapfill.cpp`: separa las funciones que terminan en un salto
  indirecto incondicional y acelera la limpieza de rangos. La última generación
  identificó 72.822 entradas.
- `tools/fix_local_branches.py`: conserva la condición del salto y reemplaza el
  marcador de error únicamente cuando el destino ya tiene una etiqueta en la
  misma función. Corrigió 216 saltos; quedan 8 marcadores sin resolver.
- `src/carbon_missing_entries.cpp`: traducciones adicionales que comprueban la
  secuencia original antes de registrarse y se omiten si el generador ya las cubre.
- `tools/recover_small_entries.py` y `tools/verify_small_entries.py`: recuperan
  secuencias acotadas de dos instrucciones y verifican sus registros, inmediatos
  y destinos contra Capstone. Pasaron las 35.030 entradas recuperadas de esta copia.
- `tests/story_entry_checks.cpp`: pasaron 40 casos de recursos, estados y entrega
  de argumentos, incluyendo las escrituras de memoria y registros conservados.

La entrada a historia está confirmada por la prueba del usuario. En esa misma
sesión también se registraron advertencias de texturas y un cierre posterior del
controlador D3D12 (`DEVICE_HUNG`, `0x887A0006`). La estabilidad gráfica sigue
pendiente de corrección. Estas comprobaciones no validan todas las rutas del juego.

## Diagnóstico y reportes

Abre un [issue](https://github.com/codepdbh/NFS-CARBON-360-DECOMP/issues) indicando
CPU, GPU, controlador, versión de Windows, edición del juego y pasos para reproducir
el fallo. El registro está en `out/runtime/carbon.log`; revisa su contenido antes
de adjuntarlo. No adjuntes datos del juego ni volcados del ejecutable.

Para investigar una dirección del registro usando el volcado local:

```powershell
python tools/inspect_image.py 0x824DAAA0
```

## Créditos

Basado en ReXGlue SDK, con código derivado de Xenia, y en la dependencia local de
NFSMW Android Evolved. La copia modificada de `phase_gapfill.cpp` conserva la
atribución de Tom Clay y la licencia BSD de tres cláusulas en `LICENSE`.
Need for Speed Carbon pertenece a sus titulares originales. Proyecto comunitario
independiente.
