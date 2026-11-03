/**
 * Photo recognition for life logging (`docs/LIFE-SPEC.md` §8).
 *
 * The model is asked one narrow question — what is this dish, roughly how much — and nothing about calories: the
 * app's own food knowledge base owns every number the person sees. The image is decoded, checked and forwarded in
 * memory; it is never written to disk, never logged, and never echoed back in the response.
 *
 * Two hard rules the code enforces rather than trusting:
 * - The declared mime is ignored. JPEG and WebP are decided from the bytes, and the pixel size from the header, so a
 *   small file cannot turn into a huge decode on someone else's phone and a text file cannot be "a photo".
 * - A model that answers with prose, a broken JSON fragment or a dish name full of markdown yields `items: []` with
 *   a 200: "not recognised" is an answer the person can act on, not a server error.
 */
import { commandcode, endpoints, headersFor } from './providers.mjs';
import { readBounded } from './bounded.mjs';

export const LIMITS = Object.freeze({
  /** Compressed bytes, as the app promises: long side ≤ 512 px, ≤ 200 KB. */
  bytes: 200 * 1024,
  /** Base64 of 200 KiB plus the JSON envelope. */
  body: 280_000,
  maxSide: 512,
  items: 6,
  timeoutMs: 20_000,
  perMinute: 6,
  perDay: 60,
  perSourceMinute: 30,
  responseBytes: 64_000,
});

const fail = (code, field) => { throw Object.assign(new Error(code), { code, ...(field ? { field } : {}) }); };
/** Standard base64 only, like the avatar route: a `data:` wrapper is a client bug. */
const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;

/** JPEG (`FF D8 FF`) or WebP (`RIFF…WEBP`) from the bytes, never from the request. */
export function sniff(bytes) {
  if (!bytes || bytes.length < 16) return null;
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if (bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
      bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) return 'image/webp';
  return null;
}

/** Pixel size straight from the header. `null` means the file is malformed, not "probably fine". */
export function dimensions(bytes, mime = sniff(bytes)) {
  if (mime === 'image/jpeg') {
    let index = 2;
    while (index + 9 < bytes.length) {
      if (bytes[index] !== 0xff) { index++; continue; }
      const marker = bytes[index + 1];
      if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd8)) { index += 2; continue; }
      const length = (bytes[index + 2] << 8) | bytes[index + 3];
      if (length < 2 || index + 2 + length > bytes.length) return null;
      // SOF0..SOF15, except the three markers that are not frame headers.
      if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) {
        const height = (bytes[index + 5] << 8) | bytes[index + 6];
        const width = (bytes[index + 7] << 8) | bytes[index + 8];
        return width && height ? { width, height } : null;
      }
      index += 2 + length;
    }
    return null;
  }
  if (mime === 'image/webp') {
    if (bytes.length < 30) return null;
    const chunk = bytes.toString('latin1', 12, 16);
    if (chunk === 'VP8 ') {
      if (bytes[23] !== 0x9d || bytes[24] !== 0x01 || bytes[25] !== 0x2a) return null;
      return { width: ((bytes[27] << 8) | bytes[26]) & 0x3fff, height: ((bytes[29] << 8) | bytes[28]) & 0x3fff };
    }
    if (chunk === 'VP8L') {
      if (bytes[20] !== 0x2f) return null;
      const bits = bytes[21] | (bytes[22] << 8) | (bytes[23] << 16) | (bytes[24] << 24);
      return { width: (bits & 0x3fff) + 1, height: ((bits >>> 14) & 0x3fff) + 1 };
    }
    if (chunk === 'VP8X') {
      return {
        width: 1 + (bytes[24] | (bytes[25] << 8) | (bytes[26] << 16)),
        height: 1 + (bytes[27] | (bytes[28] << 8) | (bytes[29] << 16)),
      };
    }
    return null;
  }
  return null;
}

const INSTRUCTION = [
  '你只做一件事：认出这张照片里的食物是什么，大概多少份。',
  '不要估算热量，不要给建议，不要评价吃得好不好。',
  '只输出 JSON，不要任何解释：{"items":[{"name":"牛肉面","portion_text":"一碗","grams_estimate":550,"confidence":0.7}]}',
  'name 用简体中文常见菜名；portion_text 如「一碗」「两份」「约 200g」；grams_estimate 只在能推断时给整数克数；confidence 是 0 到 1 之间的小数。',
  '最多 6 项。认不出就输出 {"items":[]}。',
].join('\n');

const asText = value => {
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) return value.map(part => (typeof part === 'string' ? part : part?.text ?? '')).join('');
  return '';
};

/**
 * The vision call. A provider that returns prose, a truncated body, a rate limit or no configured model at all is
 * not a crash: the first three are "nothing recognised" (`items: []`), the last is a clear 503 — see `recognize`
 * below. Only a timeout is reported as a timeout, which the client shows as 「没认出来，手动搜一下」.
 */
export function createVision({ apiKey, model, provider = commandcode, baseUrl = provider.baseUrl,
  fetcher = fetch, timeoutMs = LIMITS.timeoutMs }) {
  const usable = Boolean(apiKey && model);
  const url = endpoints({ baseUrl }).chat;

  async function describe(bytes, mime) {
    if (!usable) fail('RECOGNIZE_UNAVAILABLE');
    const signal = AbortSignal.timeout(timeoutMs);
    let response;
    try {
      response = await fetcher(url, {
        method: 'POST', redirect: 'error', signal,
        // Same descriptor as the text model, so the user-agent / auth header rules live in exactly one place.
        headers: { ...headersFor(provider, apiKey), 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model, stream: false, max_tokens: 400, temperature: 0,
          messages: [{
            role: 'user',
            content: [
              { type: 'text', text: INSTRUCTION },
              { type: 'image_url', image_url: { url: `data:${mime};base64,${bytes.toString('base64')}` } },
            ],
          }],
        }),
      });
    } catch (error) {
      fail(signal.aborted || error?.name === 'TimeoutError' ? 'RECOGNIZE_TIMEOUT' : 'RECOGNIZE_UNAVAILABLE');
    }
    if (!response.ok) { await response.body?.cancel().catch(() => {}); fail('RECOGNIZE_UNAVAILABLE'); }
    let raw;
    try {
      raw = (await readBounded(response, LIMITS.responseBytes, { code: 'RECOGNIZE_UNAVAILABLE' })).toString('utf8');
    } catch {
      fail(signal.aborted ? 'RECOGNIZE_TIMEOUT' : 'RECOGNIZE_UNAVAILABLE');
    }
    return parseItems(raw);
  }

  return { describe, usable };
}

/** Model output → at most six items, or an empty list. Never throws on a bad answer. */
export function parseItems(body) {
  let payload;
  try { payload = JSON.parse(body); } catch { return []; }
  const content = asText(payload?.choices?.[0]?.message?.content);
  if (!content) return [];
  // Models wrap JSON in fences or prose; the object between the first `{` and the last `}` is the answer.
  const start = content.indexOf('{');
  const end = content.lastIndexOf('}');
  if (start < 0 || end <= start) return [];
  let parsed;
  try { parsed = JSON.parse(content.slice(start, end + 1)); } catch { return []; }
  const list = Array.isArray(parsed?.items) ? parsed.items : [];
  const items = [];
  for (const item of list.slice(0, LIMITS.items)) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) continue;
    // A strict whitelist: a model that volunteers calories or a price has misunderstood the task.
    const name = typeof item.name === 'string' ? item.name.trim().slice(0, 40) : '';
    if (!name || !/[\u4e00-\u9fa5A-Za-z0-9]/.test(name)) continue;
    const portion = typeof item.portion_text === 'string' ? item.portion_text.trim().slice(0, 24) : '';
    const grams = Number(item.grams_estimate);
    const confidence = Number(item.confidence);
    items.push({
      name,
      ...(portion ? { portion_text: portion } : {}),
      ...(Number.isFinite(grams) && grams > 0 && grams <= 5000 ? { grams_estimate: Math.round(grams) } : {}),
      ...(Number.isFinite(confidence) && confidence >= 0 && confidence <= 1 ? { confidence: Math.round(confidence * 100) / 100 } : {}),
    });
  }
  return items;
}

export function createLife({ vision = null }) {
  /** Body is `{ image: "<base64>" }`; anything else is a client bug, and the image never leaves memory. */
  async function recognize(body) {
    if (!vision?.usable) fail('RECOGNIZE_UNAVAILABLE');
    const raw = body?.image;
    if (typeof raw !== 'string' || !raw || !BASE64.test(raw)) fail('IMAGE_INVALID');
    const bytes = Buffer.from(raw, 'base64');
    if (!bytes.length) fail('IMAGE_INVALID');
    if (bytes.length > LIMITS.bytes) fail('IMAGE_TOO_LARGE');
    const mime = sniff(bytes);
    if (!mime) fail('IMAGE_INVALID');
    const size = dimensions(bytes, mime);
    if (!size || !size.width || !size.height) fail('IMAGE_INVALID');
    if (Math.max(size.width, size.height) > LIMITS.maxSide) fail('IMAGE_TOO_LARGE');
    return { items: await vision.describe(bytes, mime) };
  }
  return { recognize };
}
