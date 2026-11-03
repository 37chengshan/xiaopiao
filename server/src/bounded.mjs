/**
 * One bounded body reader for every upstream response: the model provider, WeChat, the vision model.
 *
 * Three rules, identical for all three callers:
 * - `Content-Length` is checked before reading, so a declared-huge body costs nothing.
 * - Bytes are counted while streaming, so a lying or chunked response stops at the cap.
 * - The body is always cancelled, so a keep-alive socket is never left half-read.
 */
const tooLarge = code => Object.assign(new Error(code), { code });

export async function readBounded(response, limit, { code = 'BODY_TOO_LARGE' } = {}) {
  const declared = Number(response?.headers?.get?.('content-length'));
  if (Number.isFinite(declared) && declared > limit) {
    await response.body?.cancel?.().catch(() => {});
    throw tooLarge(code);
  }
  const reader = response.body?.getReader();
  if (!reader) throw tooLarge(code);
  const chunks = []; let bytes = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > limit) throw tooLarge(code);
      chunks.push(value);
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
  return Buffer.concat(chunks, bytes);
}

export async function textBounded(response, limit, options) {
  return (await readBounded(response, limit, options)).toString('utf8');
}
