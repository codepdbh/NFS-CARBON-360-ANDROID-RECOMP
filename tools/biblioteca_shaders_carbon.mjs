// Builds nfscarbon_shaders.nfsp (the native renderer's shader library) from an extracted Carbon folder, with the
// shader pipeline of NFSMW Android Evolved (nfsmw-android/android/app/src/main/assets/shaders: XenosRecomp, DXC and
// the packer compiled to WebAssembly).
//
// Carbon stores its shaders in the 2008 XDK container (signature 10 2A 11 00 pixel / 10 2A 11 01 vertex), the
// layout XenosRecomp reads natively; Most Wanted's are the 2005 one (10 2A 0E 0x), which the scanner of the
// installer looks for. This scanner accepts a 2008 header when its sizes and table offsets are consistent.
//
// Usage: node biblioteca_shaders_carbon.mjs <nfsmw-android assets/shaders folder> <Carbon folder> <output .nfsp>
//          [--traductor <nfsc_hlsl executable>] [--empaquetador <nfsmw_empaquetar executable>]
// Carbon's vertex shaders write the fog output, which the WebAssembly translator (built for Most Wanted) does not
// declare: --traductor uses XenosRecomp built with -DNFSMW_RECOMP -DNFSC_RECOMP (see nfsmw-android/shaders).
// The WebAssembly packer only accepts 2005 containers: --empaquetador uses nfsmw_empaquetar.cpp built natively.
import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';
import os from 'node:os';

const [shaders, game, output] = process.argv.slice(2);
const opcion = (n) => (process.argv.includes(n) ? process.argv[process.argv.indexOf(n) + 1] : null);
const traductor = opcion('--traductor');
const empaquetador = opcion('--empaquetador');
if (!output) {
  console.error('uso: node biblioteca_shaders_carbon.mjs <assets/shaders de nfsmw-android> <carpeta de Carbon> <salida>');
  process.exit(1);
}
const base = pathToFileURL(path.resolve(shaders) + '/');
const load = async (rel) => import(new URL(rel, base));
const { default: createHlslModule } = await load('wasm/hlsl.mjs');
const { default: createDxcModule } = await load('wasm/dxc_web.mjs');
const { default: createPackModule } = await load('wasm/pack.mjs');
const { default: createLzxModule } = await load('wasm/lzx.mjs');
const { readXexImage } = await load('lib/xex.js');


const MAX_CONTAINER = 1 << 18;
const be32 = (d, o) => ((d[o] << 24) | (d[o + 1] << 16) | (d[o + 2] << 8) | d[o + 3]) >>> 0;

// Every 2008 container of one buffer, in file order, numbered with one counter for the whole scan.
function scan(data, origin, found) {
  for (let i = 0; i + 36 <= data.length; i++) {
    if (data[i] !== 0x10 || data[i + 1] !== 0x2a || data[i + 2] !== 0x11 || data[i + 3] > 1) continue;
    const virt = be32(data, i + 4);
    const phys = be32(data, i + 8);
    const constants = be32(data, i + 16);
    const definitions = be32(data, i + 20);
    const shader = be32(data, i + 24);
    if (virt < 36 || !phys || virt + phys > MAX_CONTAINER || i + virt + phys > data.length) continue;
    if (constants && (constants < 36 || constants >= virt)) continue;
    if (definitions && (definitions < 36 || definitions >= virt)) continue;
    if (shader < 36 || shader >= virt) continue;
    const kind = data[i + 3] ? 'v' : 'p';
    const name = `${kind}_${String(found.length).padStart(6, '0')}.bin`;
    found.push({ name, file: origin, offset: i, bytes: data.slice(i, i + virt + phys) });
    i += virt + phys - 1;
  }
}

const quiet = () => ({ print: () => {}, printErr: () => {} });
const errors = [];
const modules = {
  hlsl: await createHlslModule({ print: () => {}, printErr: (t) => errors.push(t) }),
  dxc: await createDxcModule({ print: () => {}, printErr: (t) => { if (/error/i.test(t)) errors.push(t); } }),
  pack: await createPackModule(quiet()),
  lzx: await createLzxModule(quiet()),
};
const sha = (b) => createHash('sha256').update(b).digest('hex');

const found = [];
const files = fs.readdirSync(path.join(game, 'NFS')).filter((n) => /\.bin$/i.test(n))
  .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
for (const name of files) {
  const before = found.length;
  scan(new Uint8Array(fs.readFileSync(path.join(game, 'NFS', name))), `NFS/${name}`, found);
  console.log(`NFS/${name}: ${found.length - before} contenedores`);
}
const xex = new Uint8Array(fs.readFileSync(path.join(game, 'default.xex')));
const { image } = await readXexImage(xex, async (c, bits, size) => {
  modules.lzx.FS.writeFile('/i.lzx', c);
  if (modules.lzx.callMain(['/i.lzx', '/i.bin', String(bits), String(size)])) throw new Error('LZX failed');
  return modules.lzx.FS.readFile('/i.bin');
});
const before = found.length;
scan(image, 'default.xex', found);
console.log(`default.xex: ${found.length - before} contenedores (imagen ${image.length} bytes)`);

// The same microcode can be stored more than once: keep the first copy of each container.
const unique = [];
const seen = new Set();
for (const c of found) {
  const h = sha(c.bytes);
  if (!seen.has(h)) {
    seen.add(h);
    unique.push(c);
  }
}
console.log(`total ${found.length}, distintos ${unique.length}`);

// buildShaderLibrary (lib/shaders.js) without Most Wanted's source rewrites (cheap PCF and shadow by minimum on its
// shadow-map fetches, the composition blur switch): translate, compile with DXC, pack.
function construir(containers, { hlsl, dxc, pack }, shaderCommon) {
  let leerHlsl;
  if (traductor) {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nfsc-'));
    fs.mkdirSync(path.join(tmp, 'in'));
    for (const c of containers) fs.writeFileSync(path.join(tmp, 'in', c.name), c.bytes);
    fs.writeFileSync(path.join(tmp, 'shader_common.h'), shaderCommon);
    execFileSync(traductor, [path.join(tmp, 'in'), path.join(tmp, 'out'), path.join(tmp, 'shader_common.h')],
      { stdio: ['ignore', 'ignore', 'inherit'] });
    leerHlsl = (stem) => new Uint8Array(fs.readFileSync(path.join(tmp, 'out', stem + '.hlsl')));
  } else {
    hlsl.FS.mkdirTree('/in');
    for (const c of containers) hlsl.FS.writeFile(`/in/${c.name}`, c.bytes);
    hlsl.FS.writeFile('/shader_common.h', shaderCommon);
    const translated = hlsl.callMain(['/in', '/out', '/shader_common.h']);
    if (translated !== 0 && translated !== undefined) throw new Error(`traduccion fallida (${translated})`);
    leerHlsl = (stem) => hlsl.FS.readFile(`/out/${stem}.hlsl`);
  }
  dxc.FS.mkdirTree('/work');
  pack.FS.mkdirTree('/in');
  pack.FS.mkdirTree('/spirv');
  let done = 0;
  for (const c of containers) {
    const stem = c.name.slice(0, -4);
    dxc.FS.writeFile(`/work/${stem}.hlsl`, leerHlsl(stem));
    const rc = dxc.ccall('compile', 'number', ['string', 'string', 'number'],
      [`/work/${stem}.hlsl`, `/work/${stem}.spv`, stem.startsWith('v_') ? 1 : 0]);
    if (rc !== 0) throw new Error(`DXC rechazo ${stem} (${rc})`);
    pack.FS.writeFile(`/spirv/${stem}.spv`, dxc.FS.readFile(`/work/${stem}.spv`));
    pack.FS.writeFile(`/in/${c.name}`, c.bytes);
    if (++done % 20 === 0) console.log(`  compilados ${done} de ${containers.length}`);
  }
  if (empaquetador) {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nfsc-pack-'));
    for (const d of ['in', 'spirv']) {
      fs.mkdirSync(path.join(tmp, d));
      for (const n of pack.FS.readdir(`/${d}`).filter((n) => !n.startsWith('.'))) {
        fs.writeFileSync(path.join(tmp, d, n), pack.FS.readFile(`/${d}/${n}`));
      }
    }
    execFileSync(empaquetador, [path.join(tmp, 'in'), path.join(tmp, 'spirv'), path.join(tmp, 'out.nfsp')],
      { stdio: ['ignore', 'inherit', 'inherit'] });
    return new Uint8Array(fs.readFileSync(path.join(tmp, 'out.nfsp')));
  }
  const packed = pack.callMain(['/in', '/spirv', '/nfsmw_shaders.nfsp']);
  if (packed !== 0 && packed !== undefined) throw new Error(`empaquetado fallido (${packed})`);
  return pack.FS.readFile('/nfsmw_shaders.nfsp');
}

try {
  const library = construir(unique, modules, new Uint8Array(fs.readFileSync(new URL('shader_common.h', base))));
  fs.writeFileSync(output, library);
  console.log(`biblioteca ${library.length} bytes, SHA-256 ${sha(library)}`);
} catch (e) {
  console.log('FALLO:', e.message);
  console.log(errors.slice(0, 15).join('\n'));
  process.exit(1);
}
