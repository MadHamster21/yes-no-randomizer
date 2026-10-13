import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {inflateSync} from 'node:zlib';

const filename = process.argv[2];
assert(filename, 'Pass the exported PNG path');
const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const png = readFileSync(filename);
assert(png.subarray(0, 8).equals(signature), 'Expected PNG');

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const result = Buffer.alloc(12 + data.length);
  result.writeUInt32BE(data.length, 0);
  result.write(type, 4, 4, 'ascii');
  data.copy(result, 8);
  result.writeUInt32BE(crc32(result.subarray(4, 8 + data.length)), 8 + data.length);
  return result;
}

function parse(bytes) {
  const chunks = [];
  let offset = 8;
  while (offset < bytes.length) {
    const length = bytes.readUInt32BE(offset);
    assert(offset + length + 12 <= bytes.length, 'Truncated PNG chunk');
    const type = bytes.toString('ascii', offset + 4, offset + 8);
    const data = bytes.subarray(offset + 8, offset + 8 + length);
    assert.equal(crc32(bytes.subarray(offset + 4, offset + 8 + length)),
      bytes.readUInt32BE(offset + 8 + length), `Invalid ${type} checksum`);
    chunks.push({type, data, raw: bytes.subarray(offset, offset + length + 12)});
    offset += length + 12;
  }
  assert.equal(chunks[0].type, 'IHDR');
  assert.equal(chunks.at(-1).type, 'IEND');
  return chunks;
}

// System.Drawing renders in its standard sRGB space. Declare that space explicitly
// and discard any competing optional profile/gamma tags before the image data.
const chunks = parse(png).filter(item => !['iCCP', 'sRGB', 'gAMA', 'cHRM'].includes(item.type));
const output = Buffer.concat([signature, chunks[0].raw,
  chunk('sRGB', Buffer.from([0])), ...chunks.slice(1).map(item => item.raw)]);
const finalChunks = parse(output);
const header = finalChunks[0].data;
const width = header.readUInt32BE(0);
const height = header.readUInt32BE(4);
assert.equal(width, 512);
assert.equal(height, 512);
assert.equal(header[8], 8, 'Expected 8 bits per channel');
assert.equal(header[9], 6, 'Expected RGBA (32-bit PNG)');
assert.equal(header[12], 0, 'Expected non-interlaced PNG');
assert(output.length <= 1_000_000, 'Must be below the requested 1 MB limit');
assert.equal(finalChunks.filter(item => item.type === 'sRGB').length, 1);

// Decode all five PNG scanline filter types independently of the exporter and
// inspect every alpha value, including the edges and square corners.
const packed = inflateSync(Buffer.concat(finalChunks.filter(item => item.type === 'IDAT')
  .map(item => item.data)));
const stride = width * 4;
assert.equal(packed.length, (stride + 1) * height);
let previous = Buffer.alloc(stride);
function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}
for (let y = 0; y < height; y++) {
  const offset = y * (stride + 1);
  const filter = packed[offset];
  assert(filter >= 0 && filter <= 4, 'Unknown PNG filter');
  const row = Buffer.alloc(stride);
  for (let x = 0; x < stride; x++) {
    const a = x >= 4 ? row[x - 4] : 0;
    const b = previous[x];
    const c = x >= 4 ? previous[x - 4] : 0;
    const predictor = [0, a, b, Math.floor((a + b) / 2), paeth(a, b, c)][filter];
    row[x] = (packed[offset + 1 + x] + predictor) & 255;
  }
  for (let x = 3; x < stride; x += 4) assert.equal(row[x], 255, 'Transparency found');
  previous = row;
}
writeFileSync(filename, output);
const report = {
  file: path.basename(filename),
  width, height, bytes: output.length, format: 'PNG', bitsPerPixel: 32,
  colorSpace: 'sRGB', srgbRenderingIntent: 'perceptual', alpha: '255 for every pixel',
  background: 'opaque; original pale-blue background preserved',
  corners: 'full square; no outer mask baked into the asset',
  method: 'Original master resampled with System.Drawing high-quality bicubic interpolation; no AI redraw',
  source: '01-decision-die.png',
  sourceSha256: createHash('sha256').update(readFileSync(path.join(path.dirname(filename), '01-decision-die.png'))).digest('hex'),
  sha256: createHash('sha256').update(output).digest('hex'),
  checks: ['PNG chunk checksums', '512x512', '8-bit RGBA', 'sRGB declaration',
    'under 1000000 bytes', 'all 262144 pixels fully opaque']
};
writeFileSync(path.join(path.dirname(filename), 'play-store-icon-validation.json'),
  JSON.stringify(report, null, 2) + '\n');
console.log(`Validated ${report.file}: 512x512, 32-bit sRGB PNG, ${report.bytes} bytes, fully opaque.`);
