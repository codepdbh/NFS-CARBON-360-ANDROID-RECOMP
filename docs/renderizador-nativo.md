# Port del renderizador nativo de Most Wanted a Carbon

Objetivo: dejar de emular la GPU de Xbox 360 (backend Xenos) y dibujar con el renderizador nativo de
NFSMW Android Evolved (`nfsmw-android/app/src/nfsmw_nativo_*`), que lee el anillo PM4 y graba Vulkan directamente.

## Por qué

Medido en un Galaxy S25 Ultra (Adreno 830) el 5 de octubre de 2026, en carrera:

- Xenos deja la GPU al 94–98 % para unos 20–30 FPS incluso a 640×360. Cada render-to-texture pasa por la EDRAM
  emulada (volcado, *resolve* a memoria con tiling y destilado al cargar la textura) y, en una GPU por mosaicos,
  cada pase carga y guarda las imágenes completas.
- En CPU ya se corrigieron dos cosas: las consultas de protección de página leían `/proc/self/maps` en cada fallo de
  escritura vigilada (SDK, tabla de protecciones) y `vkCmdBeginRendering` hacía que el driver Adreno reservara
  memoria en cada pase (`vulkan_dynamic_rendering=false`).

## Qué necesita el renderizador nativo del juego

El renderizador (unas 28.000 líneas) es casi todo genérico de Xbox 360. Del juego necesita:

1. Los constructores de shaders de Direct3D, para saber de qué contenedor sale cada objeto de shader
   (los vertex shaders se parchean al dibujar y no se pueden reconocer por su microcódigo).
2. El parcheador de fetch de vértices de Direct3D (versiones del microcódigo, "IM_LOAD sin memcmp").
3. Las funciones `Draw*`, para anotar el VS y el PS de cada dibujo.
4. Los desplazamientos del dispositivo D3D donde están el VS y el PS activos.
5. La biblioteca de shaders de Carbon (`tools/biblioteca_shaders.mjs` de nfsmw-android, con los contenedores de su
   disco).

## Equivalencias encontradas (Carbon PAL inglés, SHA-256 b1e42391…)

Método: huella de instrucciones sin registros ni saltos (similitud de multiconjuntos) y grafo de llamadas, con el
código generado de los dos juegos. Herramientas en `tools/` (emparejar_funciones.py, grafo.py, buscar_patrones.py).

| Función | MW (PAL España) | Carbon | Cómo se confirmó |
|---|---|---|---|
| Parcheador de fetch de VS | `825A2FB8` | `826FAF30` | única función con las máscaras `0xBFC6CFFF`, `0xBFC0CFFF` y `subfic …,95`; similitud 0,75 |
| Copia del VS al anillo | `825A37D8` | `826FB800` | 0,75; llama al parcheador |
| Desactiva salidas del VS | `825A36A8` | `826FB6C0` | 0,85 |
| Establece shaders al dibujar | `825A3AF0` | `826FBB68` | llama a `826FB800`; único llamador |
| CreateDevice | `825A1658` | `826DF0A8` | 0,87 |
| CreateVertexShader | `8259C038` | `826E4680` | 0,80; mismos llamadores que en MW (envoltorio VS, cargador de efectos, vídeo) |
| CreatePixelShader | `8259BC90` | `826E4218` | 0,68; mismos llamadores (envoltorio PS, cargador de efectos, vídeo) |
| Envoltorio VS / PS de efectos | `82692710` / `82692760` | `8276E5F8` / `8276E670` | |
| Cargador de efectos | `82694E00` | `82770F08` | llama a los dos constructores |
| Vídeo | `826DB8E0` | `827B8910` | llama a los dos constructores |

Diferencia estructural: **Carbon no tiene `FlushState` como función**. Su cuerpo está copiado dentro de cada una
de las diez funciones de dibujo (`826DCB30`, `826DCD68`, `826DCFA0`, `826DD490`, `826DDA78`, `826DDE68`,
`826DE280`, `826DE700`, `826F6A88`, `82706068`), que llaman a `826FBB68` y al volcado de registros `826FA918`
(equivalente de `825A2AA0`). Falta asignar cuál es cada `Draw*`.

Dispositivo D3D: MW guarda el PS y el VS que usa `FlushState` en `+0x3290` y `+0x3294`; Carbon los lee en
`+0x307C` y `+0x3080` (`826FBB68`).

Funciones de dibujo asignadas: `DrawVertices` = `826DDA78`, `DrawIndexedVertices` = `826DDE68`,
`DrawVerticesUP` = `826DD448` (núcleo `826DCFA0`), `DrawIndexedVerticesUP` = `826DD9D8` (núcleo `826DD490`).
Los argumentos van en los mismos registros que en MW.

`SetVertexShader` = `826E4790` guarda el VS en `+0x3080`; `SetPixelShader` = `826E4350` guarda el PS en
`+0x307C`. `CreateVertexShader` recibe el contenedor 2008 completo en `r3` (copia la parte virtual al objeto + 872
y la física a memoria de GPU).

El parcheador de fetch de Carbon no tiene la firma de MW: el objeto VS guarda varias copias parcheadas (paso de
416 bytes) y se llama desde `826FB800` (copia al anillo, retorno `826FB948`), `826FBA20` (retorno `826FBAE8`) y
`826FC1F0` (retorno `826FC2F8`) con `r3` = objeto y `r4` = destino. Por eso el atajo "IM_LOAD sin memcmp" está
apagado en Carbon (`nfsmw_nativo_im_load_sin_memcmp=false`).

## Biblioteca de shaders

`tools/biblioteca_shaders_carbon.mjs`: 84 contenedores distintos (122 en ZZDATA0, 3 en el xex; los bloques JDLZ
de los ZZDATA no contienen ninguno). Necesita XenosRecomp con `-DNFSC_RECOMP` (salidas FOG y NORMAL0, texturas
1D, posiciones 2-6 y TEXCOORD8 en las ubicaciones 16-21) y el empaquetador nativo (acepta contenedores 2008).
El renderizador la busca como `nfscarbon_shaders.nfsp` junto al ejecutable o en la carpeta del juego.

## Integración

El renderizador se compila desde `nfsmw-android/app/src` con `NFSC_RECOMP` (ver `CMakeLists.txt`); los ganchos
de Carbon están en `src/carbon_nativo_ganchos.cpp` y la espera del anillo (`826DEFD0`) usa el progreso del hilo
del anillo nativo. Se elige con la opción "Renderizador" del lanzador (`nfsmw_renderizador`).

## Estado (6 de octubre de 2026)

Arranca con el renderizador nativo, ~60 FPS en menú y carrera (S25), 0 dibujos rechazados. Hecho en el
renderizador compartido (`nfsmw-android/app/src`, bajo `NFSC_RECOMP` donde es propio de Carbon):

- Destinos de color 2_10_10_10_FLOAT (7e3) y su alias 16_16_16_16 en RGBA16F; copias entre formatos con blit;
  borrado 7e3 decodificado.
- Los otros seis `Draw*` de Carbon (`826DE280`, `826DE700`, `826F6A88`, `82706068`, `826DCB30`, `826DCD68`)
  dejan registros `kCualquiera` (solo los shaders).
- Identificación de VS cargados: el D3D de Carbon reescribe el swizzle de destino de cada fetch y reordena
  los fetch; se comparan como conjunto (42 de 45 identificados; los 3 restantes son internos del D3D).

## Pendiente

- Imagen oscura y transparencias rotas en carrera.
- Los vídeos se quedan en negro.
- Contador de FPS del lanzador (lee contadores de Xenos).
- ~25 % de dibujos siguen sin registro de `Draw*`.
- `Swap` (candidato `826E27A8`, similitud 0,59) y `Issue` de las consultas (candidato `826EDF38`).
- Generar la biblioteca en el teléfono (módulos WebAssembly con `NFSC_RECOMP`).
- Depurar la imagen pase a pase y las optimizaciones propias de Carbon.
