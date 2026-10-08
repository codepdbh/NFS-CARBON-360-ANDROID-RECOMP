# Compatibilidad y drivers en Carbon 0.3.2

Se incorpora la revisión [a7e6e4c de Most Wanted 0.5.4](https://github.com/codepdbh/nfsmw-android/commit/a7e6e4c3ca71064d0eea56d91fcc8aaf63418fc7)
del SDK y del renderizador compartido. Los arreglos de cuatro conjuntos, sincronización ARM y Xclipse proceden del
[commit 3d9358e del fork de victorgbd](https://github.com/victorgbd/NFSMW-Recompiled-Mobile/commit/3d9358e13dd6a10fb4b82f64040cddd07b215362),
adaptados en Most Wanted bajo GPL-3.0. El cargador usa libadrenotools y liblinkernsbypass bajo BSD-2-Clause; las licencias
están en la dependencia fijada y en `assets/DRIVER-NOTICES.txt` del APK. Se conservan el launcher y los controles de Carbon.

## Renderer y órdenes del juego

- En GPU con `maxBoundDescriptorSets == 4`, el renderer compartido junta las texturas 3D y los cubos, adaptando los
  bindings Vulkan y las decoraciones de todos los shaders. Con cinco o más conjuntos se conserva la distribución habitual.
- El driver propietario de Samsung Xclipse usa conversión BC4/BC5 en CPU; los drivers Mesa identificados conservan BC nativo.
- La sincronización se adapta a la **cola de Carbon `0x82B8C764`**: productor `sub_824695D8`, ejecutor `sub_82469650`.
  Se copia la entrada redondeada a 16 bytes y se escribe función, tamaño y cola antes de publicar el contador con release.
  El ejecutor hace acquire y procesa como máximo las entradas publicadas. Las listas cerradas y otras listas conservan la
  llamada original. Los ganchos se verifican contra las instrucciones de la copia soportada; no usan direcciones de MW.
- La transformación de llamadas directas conserva ambos ganchos. Estas medidas corrigen causas de incompatibilidad y
  posibles cierres; no permiten prometer compatibilidad ni FPS para todos los teléfonos.

## Instalar o actualizar

Descarga el APK ARM64 de [Releases](https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP/releases/latest). Instálalo encima
del anterior sin desinstalar ni borrar datos. Necesitas Android 8+, ARM64 y, para el renderer nativo, Vulkan 1.1+ con las
funciones de descriptor indexing requeridas. Tener Vulkan 1.2/1.3 no garantiza las funciones opcionales.

Usa tu copia Xbox 360 **PAL inglesa** con `default.xex` SHA-256
`b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221`. Copia `default.xex`, `NFS/` y `Movies/` directamente
en **Memoria interna/NFSCARBON/** o pulsa **Importar mi copia**. Las voces de esta copia son inglesas; los textos admiten
español, inglés, francés, alemán e italiano. El APK no distribuye datos del juego.

## Driver Vulkan

1. Con Android 9+, entra en **Driver Vulkan → Importar ZIP**.
2. Elige un paquete Android ARM64 para tu GPU, Android y kernel. Debe incluir `meta.json`, por ejemplo:
   `{"name":"Mi driver","libraryName":"libvulkan_freedreno.so","minApi":28}`. Se admiten carpetas contenedoras.
3. Selecciona el driver y pulsa **Probar driver**. La prueba usa el mismo cargador que el juego en un proceso separado,
   con tiempo máximo de 20 segundos, e informa de GPU, Vulkan, conjuntos y funciones faltantes.
4. Si falla, vuelve a **Del sistema**. **Eliminar seleccionado** elimina únicamente ese paquete importado.

La importación comprueba límites de tamaño, rutas y ELF ARM64. No modifica drivers del sistema ni descarga paquetes.
**Turnip es para Adreno; PanVK para Mali.** Importar un paquete no garantiza que soporte el dispositivo. PanVK no se
anuncia como solución confirmada; continúan las restricciones de hardware, kernel, gralloc y versión de Android.

El launcher prueba el driver del sistema antes de iniciar el renderer nativo y cualquier driver externo antes de jugar.
El resultado nativo de funciones faltantes bloquea el inicio nativo y permite cambiar de driver. En Xenos esa lista de
funciones nativas no reemplaza las comprobaciones que realiza su propio runtime.

## Actualizaciones opcionales

Se consulta en segundo plano `codepdbh/NFS-CARBON-360-ANDROID-RECOMP/releases/latest`, sin autenticación ni envío de registros.
Solo se ofrecen versiones estables superiores, con **Actualizar** o **Más tarde**. **Buscar actualizaciones** fuerza la
consulta; las automáticas usan una caché de seis horas y avisan una vez por release. Sin conexión se puede seguir jugando.
El aviso abre el APK oficial en el navegador y nunca lo descarga o instala en segundo plano. Cada juego consulta su propio
repositorio: Carbon no ofrece APKs de Most Wanted.

## Pruebas y límites

- Importación: paquete con carpeta, biblioteca ARM64 y rechazo de rutas externas, metadatos excesivos y arquitectura PC.
- Versiones: actualizaciones, igualdad, versiones antiguas, números de varias cifras, betas y etiquetas inválidas.
- En ARM64: 200.000 entradas de la cola de Carbon, con comprobación de función, payload, redondeo, cola y límite de consumo.
- Los 87 módulos de la biblioteca de Carbon se adaptaron en ARM64 a cuatro conjuntos y SPIR-V 1.3; todos pasaron
  `spirv-val --target-env vulkan1.1`. La biblioteca original coincide con el SHA-256 esperado por el generador del teléfono.
- Las 23 instrucciones usadas en los fingerprints gráficos y de sincronización coinciden con el volcado de la copia soportada.
- El mapa conserva las 73.177 funciones; las 75 llamadas a productor/ejecutor conservan el despacho por los nuevos ganchos.
- APK Release ARM64 compilado y firmado; sus 11 bibliotecas ELF tienen alineación de 16 KB.
- Cargador real probado en Android con el driver del sistema (Adreno 830, Vulkan 1.3.284, dispositivo lógico creado),
  biblioteca ausente y ELF sin implementación Vulkan. Los dos últimos se devuelven como diagnóstico de fallo.
- El usuario confirmó una carrera en la versión 0.3.2 con imagen, sonido y controles correctos en el teléfono conectado.

La comprobación en Adreno permite detectar regresiones, pero falta confirmar cada modelo Mali, Xclipse, PowerVR,
Snapdragon 888/8 Gen 1 y Adreno 720. Los drivers sin descriptor indexing no obtienen esas funciones por bajar la resolución.
Para ayudar a reproducir fallos, usa **Enviar crash o log**: el informe incluye el driver seleccionado, sus funciones y los
argumentos efectivos. Adjunta el ZIP y describe el momento del fallo en el issue de Carbon.
