import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';
import { LIMITS, createVision, dimensions, parseItems, sniff } from '../src/life.mjs';

const account = { email: 'cook@example.test', username: 'cook', password: 'A long test passphrase 123', client: 'native' };

/** A JPEG header with a real SOF0 frame, padded to `size` bytes so the byte limit can be exercised. */
function jpeg(width, height, size = 2048) {
  const head = Buffer.from([
    0xff, 0xd8, // SOI
    0xff, 0xc0, 0x00, 0x11, 0x08, // SOF0, length 17, precision 8
    (height >> 8) & 0xff, height & 0xff, (width >> 8) & 0xff, width & 0xff,
    0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
  ]);
  const body = Buffer.alloc(Math.max(0, size - head.length - 2), 0x20);
  return Buffer.concat([head, body, Buffer.from([0xff, 0xd9])]);
}
/** A WebP with an extended (VP8X) canvas, which is what modern encoders emit. */
function webp(width, height) {
  const bytes = Buffer.alloc(40, 0);
  bytes.write('RIFF', 0, 'latin1'); bytes.writeUInt32LE(32, 4); bytes.write('WEBP', 8, 'latin1');
  bytes.write('VP8X', 12, 'latin1'); bytes.writeUInt32LE(10, 16); bytes[20] = 0x10;
  bytes.writeUIntLE(width - 1, 24, 3); bytes.writeUIntLE(height - 1, 27, 3);
  return bytes;
}
const b64 = buffer => buffer.toString('base64');

async function fixture(t, options = {}) {
  const db = store(':memory:'), mail = [], calls = [];
  const vision = 'vision' in options ? options.vision : {
    usable: true,
    async describe(bytes, mime) { calls.push({ size: bytes.length, mime }); return [{ name: '牛肉面', portion_text: '一碗', grams_estimate: 550, confidence: 0.72 }]; },
  };
  const app = createApp({ db, sendMail: async item => mail.push(item), agent: async state => state, vision });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function request(path, body, token, method) {
    const response = await fetch(url + path, {
      method: method ?? (body === undefined ? 'GET' : 'POST'),
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, data: await response.json().catch(() => null) };
  }
  const created = await request('/v1/auth/register', account);
  await request('/v1/auth/verify', { code: mail[0].code }, created.data.token);
  return { db, mail, calls, request, token: created.data.token };
}

test('an unauthenticated photo is refused before any model call', async t => {
  const f = await fixture(t);
  const anonymous = await f.request('/v1/life/recognize', { image: b64(jpeg(400, 300)) });
  assert.equal(anonymous.status, 401);
  assert.equal(f.calls.length, 0);
  const read = await f.request('/v1/life/recognize', undefined, f.token);
  assert.equal(read.status, 404, 'there is no read side: nothing is stored');
});

test('a recognised dish comes back without calories, and the image is forwarded as-is', async t => {
  const f = await fixture(t);
  const result = await f.request('/v1/life/recognize', { image: b64(jpeg(512, 384)) }, f.token);
  assert.equal(result.status, 200, JSON.stringify(result.data));
  assert.deepEqual(result.data, { items: [{ name: '牛肉面', portion_text: '一碗', grams_estimate: 550, confidence: 0.72 }] });
  assert.deepEqual(f.calls, [{ size: jpeg(512, 384).length, mime: 'image/jpeg' }]);
  assert.equal(JSON.stringify(result.data).includes('kcal'), false, 'the model never supplies calories');
  assert.equal(JSON.stringify(result.data).includes('base64'), false, 'the image is not echoed back');
});

test('webp is accepted and the byte sniff, not the client, decides the type', async t => {
  const f = await fixture(t);
  const seen = [];
  const vision = { usable: true, describe: async (bytes, mime) => { seen.push(mime); return []; } };
  const g = await fixture(t, { vision });
  assert.equal((await g.request('/v1/life/recognize', { image: b64(webp(320, 240)) }, g.token)).status, 200);
  assert.deepEqual(seen, ['image/webp']);
  // A PNG renamed as a photo is not a photo here: only JPEG and WebP are accepted.
  const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(64)]);
  const rejected = await f.request('/v1/life/recognize', { image: b64(png) }, f.token);
  assert.equal(rejected.status, 400);
  assert.equal(rejected.data.error, 'IMAGE_INVALID');
  assert.equal(f.calls.length, 0);
});

test('oversized bytes and an oversized long side are both 413, before the model is called', async t => {
  const f = await fixture(t);
  const heavy = await f.request('/v1/life/recognize', { image: b64(jpeg(400, 300, LIMITS.bytes + 1)) }, f.token);
  assert.equal(heavy.status, 413);
  assert.equal(heavy.data.error, 'IMAGE_TOO_LARGE');
  const wide = await f.request('/v1/life/recognize', { image: b64(jpeg(800, 400)) }, f.token);
  assert.equal(wide.status, 413, 'long side over 512 px');
  const huge = await f.request('/v1/life/recognize', { image: b64(Buffer.alloc(300_000)) }, f.token);
  assert.equal(huge.status, 413, 'a body larger than any valid photo is refused at the door');
  const malformed = await f.request('/v1/life/recognize', { image: b64(Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(40)])) }, f.token);
  assert.equal(malformed.status, 400, 'a JPEG with no frame header is not an image');
  assert.equal(f.calls.length, 0);
});

test('six a minute per account, then a Chinese 429', async t => {
  const f = await fixture(t);
  const statuses = [];
  for (let i = 0; i < 7; i++) statuses.push((await f.request('/v1/life/recognize', { image: b64(jpeg(200, 200)) }, f.token)).status);
  assert.deepEqual(statuses.slice(0, 6), Array(6).fill(200));
  assert.equal(statuses[6], 429);
  const blocked = await f.request('/v1/life/recognize', { image: b64(jpeg(200, 200)) }, f.token);
  assert.match(blocked.data.message, /频繁/);
  assert.equal(f.calls.length, 6, 'the refused request never reaches the model');
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM limits WHERE key LIKE 'recognize-day:%'").get().n, 1);
});

test('model prose, broken JSON and a timeout all end in a clear answer', async t => {
  const f = await fixture(t, { vision: { usable: true, describe: async () => [] } });
  assert.deepEqual((await f.request('/v1/life/recognize', { image: b64(jpeg(200, 200)) }, f.token)).data, { items: [] });

  const timeout = await fixture(t, { vision: { usable: true, describe: async () => { throw Object.assign(new Error('RECOGNIZE_TIMEOUT'), { code: 'RECOGNIZE_TIMEOUT' }); } } });
  const late = await timeout.request('/v1/life/recognize', { image: b64(jpeg(200, 200)) }, timeout.token);
  assert.equal(late.status, 504);
  assert.match(late.data.message, /手动搜一下/);

  const off = await fixture(t, { vision: null });
  const unavailable = await off.request('/v1/life/recognize', { image: b64(jpeg(200, 200)) }, off.token);
  assert.equal(unavailable.status, 503);
  assert.equal(unavailable.data.error, 'RECOGNIZE_UNAVAILABLE');
});

test('the model answer is parsed strictly: fences survive, invented calories do not', () => {
  const fenced = JSON.stringify({ choices: [{ message: { content: '```json\n{"items":[{"name":"牛肉面","portion_text":"一碗","grams_estimate":550,"confidence":0.7,"kcal":600}]}\n```' } }] });
  assert.deepEqual(parseItems(fenced), [{ name: '牛肉面', portion_text: '一碗', grams_estimate: 550, confidence: 0.7 }]);
  assert.deepEqual(parseItems(JSON.stringify({ choices: [{ message: { content: '这是一碗牛肉面，大约 600 千卡。' } }] })), []);
  assert.deepEqual(parseItems('not json'), []);
  assert.deepEqual(parseItems(JSON.stringify({ choices: [{ message: { content: '{"items":[{"name":""},{"name":"米饭","grams_estimate":-5},{"name":"汤","confidence":9}]}' } }] })),
    [{ name: '米饭' }, { name: '汤' }]);
  const seven = { items: Array.from({ length: 8 }, (_, i) => ({ name: `菜${i}` })) };
  assert.equal(parseItems(JSON.stringify({ choices: [{ message: { content: JSON.stringify(seven) } }] })).length, LIMITS.items);
});

test('the vision call is bounded, uses the provider shape, and never streams a partial answer', async () => {
  const requests = [];
  const fetcher = async (url, options) => {
    requests.push({ url, body: JSON.parse(options.body) });
    return Response.json({ choices: [{ message: { content: '{"items":[{"name":"番茄炒蛋","portion_text":"一份","confidence":0.6}]}' } }] });
  };
  const vision = createVision({ apiKey: 'test-placeholder', model: 'vision-test', baseUrl: 'https://vision.example/v1', fetcher });
  const items = await vision.describe(Buffer.from([0xff, 0xd8, 0xff, 0x00]), 'image/jpeg');
  assert.deepEqual(items, [{ name: '番茄炒蛋', portion_text: '一份', confidence: 0.6 }]);
  assert.equal(requests[0].url, 'https://vision.example/v1/chat/completions');
  assert.equal(requests[0].body.stream, false);
  assert.match(requests[0].body.messages[0].content[1].image_url.url, /^data:image\/jpeg;base64,/);
  await assert.rejects(createVision({ apiKey: null, model: null }).describe(Buffer.from([1]), 'image/jpeg'),
    error => error.code === 'RECOGNIZE_UNAVAILABLE');
});

test('the vision call uses the same provider descriptor as the text model', async () => {
  const seen = [];
  const provider = { id: 'test', baseUrl: 'https://vision.example/v1', headers: { 'User-Agent': 'Xiaopiao-Agent/1.0' }, auth: 'bearer' };
  const vision = createVision({
    apiKey: 'test-placeholder', model: 'vision-test', provider,
    fetcher: async (url, options) => { seen.push({ url, headers: options.headers }); return Response.json({ choices: [{ message: { content: '{"items":[]}' } }] }); },
  });
  await vision.describe(Buffer.from([0xff, 0xd8, 0xff, 0x00]), 'image/jpeg');
  assert.equal(seen[0].url, 'https://vision.example/v1/chat/completions');
  assert.equal(seen[0].headers['User-Agent'], 'Xiaopiao-Agent/1.0', 'the descriptor header is not bypassed');
  assert.match(seen[0].headers.Authorization, /^Bearer /);
});

test('header parsing reads the real pixel size instead of trusting the client', () => {
  assert.deepEqual(dimensions(jpeg(600, 400)), { width: 600, height: 400 });
  assert.deepEqual(dimensions(webp(512, 288)), { width: 512, height: 288 });
  assert.equal(sniff(Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(16)])), 'image/jpeg');
  assert.equal(sniff(Buffer.from('GIF89a................', 'latin1')), null);
  assert.equal(dimensions(Buffer.from([0xff, 0xd8, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])), null);
});
