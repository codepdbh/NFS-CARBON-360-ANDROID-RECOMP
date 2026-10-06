// Builds nfscarbon_shaders.nfsp on the phone from the player's own game files, with the same steps as
// tools/biblioteca_shaders_carbon.mjs (and the same modules: the translator and the packer built for Carbon, DXC and
// LZX from NFSMW Android Evolved). ShaderBuilder.java serves this folder and the game files under
// https://appassets.androidplatform.net/ and receives progress and the result through the CarbonShaders interface.
import createHlslModule from './wasm/hlsl.mjs';
import createDxcModule from './wasm/dxc_web.mjs';
import createPackModule from './wasm/pack.mjs';
import createLzxModule from './wasm/lzx.mjs';
import { readXexImage } from './lib/xex.js';

const host = window.CarbonShaders;
const GAME = 'https://appassets.androidplatform.net/game/';
const CHUNK = 16 << 20;
const MAX_CONTAINER = 1 << 18;
// SHA-256 of the library (this folder's shader_common.h and modules) by SHA-256 of default.xex. Rebuild the value
// with tools/biblioteca_shaders_carbon.mjs whenever any of them changes (and ShaderBuilder.LIBRARY_VERSION).
const LIBRARIES = {
  // PAL (English, multi-language text)
  b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221:
    '9d6c59e985a53837f9f1d2c00f7250dbda45066e18517e4ad23c30180ad64965',
};

const progress = (fraction, text) => host.progress(Math.max(0, Math.min(1, fraction)), text);
const hex = (buffer) => [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, '0')).join('');
const sha256 = async (bytes) => hex(await crypto.subtle.digest('SHA-256', bytes));
const be32 = (d, o) => ((d[o] << 24) | (d[o + 1] << 16) | (d[o + 2] << 8) | d[o + 3]) >>> 0;

async function fetchBytes(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`no se pudo leer ${url} (${response.status})`);
  return new Uint8Array(await response.arrayBuffer());
}

// The 2008 XDK shader containers (10 2A 11 00 pixel / 10 2A 11 01 vertex) of a buffer whose sizes and table
// offsets are consistent. `limit`: no container starts at or past it (the next chunk scans from there).
function scan(data, limit, found) {
  let i = 0;
  for (; i < limit && i + 36 <= data.length; i++) {
    if (data[i] !== 0x10 || data[i + 1] !== 0x2a || data[i + 2] !== 0x11 || data[i + 3] > 1) continue;
    const virt = be32(data, i + 4), phys = be32(data, i + 8);
    const constants = be32(data, i + 16), definitions = be32(data, i + 20), shader = be32(data, i + 24);
    if (virt < 36 || !phys || virt + phys > MAX_CONTAINER || i + virt + phys > data.length) continue;
    if (constants && (constants < 36 || constants >= virt)) continue;
    if (definitions && (definitions < 36 || definitions >= virt)) continue;
    if (shader < 36 || shader >= virt) continue;
    const kind = data[i + 3] ? 'v' : 'p';
    found.push({ name: `${kind}_${String(found.length).padStart(6, '0')}.bin`, bytes: data.slice(i, i + virt + phys) });
    i += virt + phys - 1;
  }
  return i;
}

// One game file in 16 MB pieces, keeping the bytes a container could still need between pieces.
async function scanFile(path, size, found, onBytes) {
  const response = await fetch(GAME + path.split('/').map(encodeURIComponent).join('/'));
  if (!response.ok || !response.body) throw new Error(`no se pudo leer ${path}`);
  const reader = response.body.getReader();
  let carry = new Uint8Array(0);
  let pending = [];
  let pendingBytes = 0;
  let seen = 0;
  const flush = (final) => {
    const data = new Uint8Array(carry.length + pendingBytes);
    data.set(carry, 0);
    let at = carry.length;
    for (const p of pending) {
      data.set(p, at);
      at += p.length;
    }
    pending = [];
    pendingBytes = 0;
    const limit = final ? data.length : Math.max(0, data.length - (MAX_CONTAINER + 36));
    const next = scan(data, limit, found);
    carry = final ? new Uint8Array(0) : data.slice(Math.min(next, data.length));
  };
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    pending.push(value);
    pendingBytes += value.length;
    seen += value.length;
    onBytes(value.length);
    if (pendingBytes >= CHUNK && seen < size) flush(false);
  }
  if (seen !== size) throw new Error(`${path}: se leyeron ${seen} de ${size} bytes`);
  flush(true);
}

// Direct3D's own vertex shaders (raw microcode in the xex, no container) in a 2008 container: a 256-register
// float4 array (g_C), the vertex elements of their fetches and COLOR0 as the only interpolator. Same as
// tools/biblioteca_shaders_carbon.mjs.
const IMAGE_BASE = 0x82000000;
const D3D_INTERNAL = [
  { address: 0x8204e878, words: 24, first: 0x00001003, elements: [] },
  { address: 0x8204e7e8, words: 27, first: 0x30052003, elements: [[3, 0, 0], [4, 10, 0]] },
  { address: 0x8204ce28, words: 15, first: 0x10011002, elements: [[2, 0, 0]] },
];
function internalContainer(image, { address, words, elements }) {
  const o = address - IMAGE_BASE;
  const micro = image.slice(o, o + words * 4);
  const w = [];
  const put = (i, v) => { w[i] = v >>> 0; };
  const table = [];
  table.push(80, 68, 0xfffe0300, 1, 28, 0, 72);
  table.push(64, (2 << 16) | 0, (256 << 16) | 0, 48, 0);
  table.push((1 << 16) | 3, (1 << 16) | 4, (256 << 16) | 0, 0);
  table.push(0x675f4300, 0, 0x76735f33, 0x5f300000);
  put(0, 0x102a1101);
  put(3, 0); put(5, 0); put(7, 0); put(8, 0);
  put(4, 36);
  put(9, table.length * 4 + 4);
  table.forEach((v, i) => put(10 + i, v));
  const sh = (10 + table.length) * 4;
  put(6, sh);
  let k = sh / 4;
  put(k++, 0); put(k++, words * 4); put(k++, 0); put(k++, 0); put(k++, 0);
  put(k++, 1 << 5);
  put(k++, 0); put(k++, elements.length); put(k++, 0);
  for (const [instruction, usage, index] of elements) put(k++, instruction | (usage << 12) | (index << 16));
  put(k++, (0 << 8) | (10 << 4) | 0);
  const virt = k * 4;
  put(1, virt);
  put(2, words * 4);
  const bytes = new Uint8Array(virt + words * 4);
  for (let i = 0; i < k; i++) {
    const v = w[i] ?? 0;
    bytes[i * 4] = v >>> 24; bytes[i * 4 + 1] = (v >>> 16) & 255; bytes[i * 4 + 2] = (v >>> 8) & 255; bytes[i * 4 + 3] = v & 255;
  }
  bytes.set(micro, virt);
  return bytes;
}

async function main() {
  progress(0, 'Cargando el compilador de shaders…');
  const quiet = () => ({ print: () => {}, printErr: () => {} });
  const [hlsl, dxc, pack, lzx] = await Promise.all([
    createHlslModule(quiet()), createDxcModule(quiet()), createPackModule(quiet()), createLzxModule(quiet()),
  ]);
  const shaderCommon = await fetchBytes('./shader_common.h');

  progress(0.02, 'Leyendo default.xex…');
  const xex = await fetchBytes(GAME + 'default.xex');
  const xexHash = await sha256(xex);
  const { image } = await readXexImage(xex, async (compressed, bits, size) => {
    lzx.FS.writeFile('/i.lzx', compressed);
    if (lzx.callMain(['/i.lzx', '/i.bin', String(bits), String(size)])) throw new Error('fallo al descomprimir default.xex');
    return lzx.FS.readFile('/i.bin');
  });

  const found = [];
  const files = JSON.parse(host.listDiscFiles());
  const total = files.reduce((sum, f) => sum + f.size, 0);
  let read = 0;
  for (const f of files) {
    await scanFile(f.path, f.size, found, (n) => {
      read += n;
      progress(0.03 + 0.37 * (read / total), `Buscando shaders en ${f.path}…`);
    });
  }
  scan(image, image.length, found);
  for (const internal of D3D_INTERNAL) {
    if (be32(image, internal.address - IMAGE_BASE) === internal.first) {
      found.push({ name: `v_d3d_${internal.address.toString(16)}.bin`, bytes: internalContainer(image, internal) });
    }
  }
  // The same microcode can be stored more than once: the first copy of each container.
  const containers = [];
  const seenHashes = new Set();
  for (const c of found) {
    const h = await sha256(c.bytes);
    if (!seenHashes.has(h)) {
      seenHashes.add(h);
      containers.push(c);
    }
  }
  if (containers.length < 60) throw new Error(`solo ${containers.length} shaders: ¿es una copia completa del juego?`);
  progress(0.4, `Encontrados ${containers.length} shaders. Traduciendo…`);

  hlsl.FS.mkdirTree('/in');
  for (const c of containers) hlsl.FS.writeFile(`/in/${c.name}`, c.bytes);
  hlsl.FS.writeFile('/shader_common.h', shaderCommon);
  const translated = hlsl.callMain(['/in', '/out', '/shader_common.h']);
  if (translated !== 0 && translated !== undefined) throw new Error(`traducción fallida (${translated})`);

  dxc.FS.mkdirTree('/work');
  pack.FS.mkdirTree('/in');
  pack.FS.mkdirTree('/spirv');
  let done = 0;
  for (const c of containers) {
    const stem = c.name.slice(0, -4);
    dxc.FS.writeFile(`/work/${stem}.hlsl`, hlsl.FS.readFile(`/out/${stem}.hlsl`));
    const rc = dxc.ccall('compile', 'number', ['string', 'string', 'number'],
      [`/work/${stem}.hlsl`, `/work/${stem}.spv`, stem.startsWith('v_') ? 1 : 0]);
    if (rc !== 0) throw new Error(`DXC rechazó ${stem} (${rc})`);
    pack.FS.writeFile(`/spirv/${stem}.spv`, dxc.FS.readFile(`/work/${stem}.spv`));
    pack.FS.writeFile(`/in/${c.name}`, c.bytes);
    dxc.FS.unlink(`/work/${stem}.hlsl`);
    dxc.FS.unlink(`/work/${stem}.spv`);
    if (++done % 10 === 0) progress(0.45 + 0.53 * (done / containers.length), `Compilados ${done} de ${containers.length}…`);
  }
  const packed = pack.callMain(['/in', '/spirv', '/nfscarbon_shaders.nfsp']);
  if (packed !== 0 && packed !== undefined) throw new Error(`empaquetado fallido (${packed})`);
  const library = pack.FS.readFile('/nfscarbon_shaders.nfsp');

  progress(0.99, 'Comprobando la biblioteca…');
  const libraryHash = await sha256(library);
  const expected = LIBRARIES[xexHash];
  if (expected && libraryHash !== expected) {
    throw new Error(`la biblioteca no coincide con la esperada (${libraryHash.slice(0, 12)}…)`);
  }
  let text = '';
  for (let i = 0; i < library.length; i += 0x8000) {
    text += String.fromCharCode.apply(null, library.subarray(i, i + 0x8000));
  }
  host.done(btoa(text), libraryHash, xexHash in LIBRARIES ? 'PAL inglés' : '');
}

main().catch((error) => host.fail(String(error && error.message ? error.message : error)));
