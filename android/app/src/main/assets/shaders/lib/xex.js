// Reads an Xbox 360 XEX2 executable and returns its decompressed PE image.
// Retail discs use AES-128 encryption and "normal" LZX compression. Fan translations often rebuild the executable
// without encryption and without compression, or with "basic" compression (plain data blocks followed by zeros), so
// those layouts are read too.

const RETAIL_KEY = new Uint8Array([
  0x20, 0xb1, 0x85, 0xa5, 0x9d, 0x28, 0xfd, 0xc3, 0x40, 0x58, 0x3f, 0xbb, 0x08, 0x96, 0xbf, 0x91,
]);

function u32(data, offset) {
  if (offset < 0 || offset + 4 > data.length) {
    throw new Error('XEX header is truncated');
  }
  return ((data[offset] << 24) | (data[offset + 1] << 16) | (data[offset + 2] << 8) | data[offset + 3]) >>> 0;
}

// AES-128-CBC decryption without padding (IV = 0). WebCrypto only decrypts PKCS#7 padded input, so a
// valid padding block is encrypted with the last ciphertext block as IV and appended before decrypting.
async function decryptNoPadding(data, keyBytes) {
  const key = await crypto.subtle.importKey('raw', keyBytes, 'AES-CBC', false, ['encrypt', 'decrypt']);
  const last = data.slice(data.length - 16);
  const padding = new Uint8Array(16).fill(16);
  const extra = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-CBC', iv: last }, key, padding)).slice(0, 16);
  const input = new Uint8Array(data.length + 16);
  input.set(data, 0);
  input.set(extra, data.length);
  return new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-CBC', iv: new Uint8Array(16) }, key, input));
}

async function sha1(data) {
  return new Uint8Array(await crypto.subtle.digest('SHA-1', data));
}

function sameBytes(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}

// Returns { image, windowBits, imageSize, blocks }. `decompressLzx(compressed, windowBits, size)` performs the
// LZX step (libmspack compiled to WebAssembly) and must return exactly `size` bytes.
export async function readXexImage(xex, decompressLzx) {
  if (u32(xex, 0) !== 0x58455832) {
    throw new Error('not an XEX2 file');
  }
  const headerSize = u32(xex, 8);
  const securityOffset = u32(xex, 16);
  const optionalCount = u32(xex, 20);
  if (headerSize > xex.length || securityOffset + 0x160 > headerSize || 24 + 8 * optionalCount > headerSize) {
    throw new Error('invalid XEX header bounds');
  }
  let formatOffset = 0;
  for (let i = 0; i < optionalCount; i++) {
    if (u32(xex, 24 + 8 * i) === 0x000003ff) {
      formatOffset = u32(xex, 28 + 8 * i);
    }
  }
  if (!formatOffset || formatOffset + 8 > headerSize) {
    throw new Error('missing file format info');
  }
  const infoSize = u32(xex, formatOffset);
  if (infoSize < 8 || formatOffset + infoSize > headerSize) {
    throw new Error('invalid file format info');
  }
  const types = u32(xex, formatOffset + 4);
  const compression = types & 0xffff;
  const imageSize = u32(xex, securityOffset + 4);
  if (imageSize > 512 * 1024 * 1024) {
    throw new Error('image too large');
  }
  let body = xex.slice(headerSize);
  const encryption = types >>> 16;
  if (encryption === 1) {
    const sessionKey = await decryptNoPadding(xex.slice(securityOffset + 0x150, securityOffset + 0x160), RETAIL_KEY);
    body = await decryptNoPadding(body.slice(0, body.length - (body.length % 16)), sessionKey);
  } else if (encryption !== 0) {
    throw new Error('unsupported encryption');
  }

  // No compression: the image comes as it is. Basic compression: pairs of (bytes of data, bytes of zeros).
  if (compression === 0 || compression === 1) {
    const image = new Uint8Array(imageSize);
    let blocks = 0;
    if (compression === 0) {
      image.set(body.subarray(0, Math.min(body.length, imageSize)));
    } else {
      let source = 0;
      let target = 0;
      for (let at = formatOffset + 8; at + 8 <= formatOffset + infoSize; at += 8) {
        const dataSize = u32(xex, at);
        const zeroSize = u32(xex, at + 4);
        if (source + dataSize > body.length || target + dataSize + zeroSize > imageSize) {
          throw new Error('block outside the XEX');
        }
        image.set(body.subarray(source, source + dataSize), target);
        source += dataSize;
        target += dataSize + zeroSize;
        blocks++;
      }
    }
    if (image[0] !== 0x4d || image[1] !== 0x5a) {
      throw new Error('image has no MZ signature');
    }
    return { image, windowBits: 0, imageSize, blocks };
  }
  if (compression !== 2) {
    throw new Error('unsupported compression');
  }
  if (infoSize < 36) {
    throw new Error('missing LZX format info');
  }
  const window = u32(xex, formatOffset + 8);
  if (!window || (window & (window - 1))) {
    throw new Error('invalid LZX window');
  }
  const windowBits = Math.log2(window);

  let blockSize = u32(xex, formatOffset + 12);
  let expectedHash = xex.slice(formatOffset + 16, formatOffset + 36);
  let position = 0;
  let blocks = 0;
  const pieces = [];
  let total = 0;
  while (blockSize) {
    const end = position + blockSize;
    if (blockSize < 26 || end > body.length) {
      throw new Error('block outside the XEX');
    }
    if (!sameBytes(await sha1(body.subarray(position, end)), expectedHash)) {
      throw new Error(`wrong SHA-1 in block ${blocks}`);
    }
    const next = u32(body, position);
    expectedHash = body.slice(position + 4, position + 24);
    let p = position + 24;
    for (;;) {
      if (p + 2 > end) {
        throw new Error('missing fragment terminator');
      }
      const n = (body[p] << 8) | body[p + 1];
      p += 2;
      if (!n) {
        break;
      }
      if (p + n > end) {
        throw new Error('fragment outside the block');
      }
      pieces.push(body.subarray(p, p + n));
      total += n;
      p += n;
    }
    position = end;
    blockSize = next;
    blocks++;
  }
  if (!blocks) {
    throw new Error('empty block chain');
  }
  const compressed = new Uint8Array(total);
  let offset = 0;
  for (const piece of pieces) {
    compressed.set(piece, offset);
    offset += piece.length;
  }
  const image = await decompressLzx(compressed, windowBits, imageSize);
  if (image.length !== imageSize || image[0] !== 0x4d || image[1] !== 0x5a) {
    throw new Error('decompressed image has no MZ signature');
  }
  return { image, windowBits, imageSize, blocks };
}
