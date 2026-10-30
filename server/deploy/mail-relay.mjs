// Optional deployment: requires explicit approval for the Worker and exact route.
// No provider credentials are persisted here; request logs must remain disabled.
const reply = status => new Response(null, { status, headers: { 'Cache-Control': 'no-store' } });
async function sameSecret(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length < 32 || b.length < 32) return false;
  const [left, right] = await Promise.all([a, b].map(s => crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))));
  const x = new Uint8Array(left), y = new Uint8Array(right);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}
export function createMailRelay(fetcher = fetch) {
  return async (request, env) => {
    const url = new URL(request.url);
    if (url.hostname !== 'xiaopiao.citygenius.top' || url.pathname !== '/__mail/send' || url.search) return reply(404);
    if (request.method !== 'POST') return reply(405);
    if (!env.RELAY_KEY || !/^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(env.INBOX_ID ?? '') || !env.ORIGIN_IP) return reply(503);
    if (request.headers.get('CF-Connecting-IP') !== env.ORIGIN_IP || !await sameSecret(request.headers.get('X-Xiaopiao-Relay-Key'), env.RELAY_KEY)) return reply(403);
    const authorization = request.headers.get('Authorization');
    const idempotency = request.headers.get('Idempotency-Key');
    if (!authorization?.startsWith('Bearer ') || !/^[a-f0-9]{64}$/.test(idempotency ?? '')) return reply(400);
    try {
      // Bound the stream, including chunked requests with no Content-Length.
      const reader = request.body?.getReader();
      if (!reader) return reply(400);
      const parts = []; let bytes = 0;
      try {
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          bytes += value.byteLength;
          if (bytes > 8192) { await reader.cancel(); return reply(413); }
          parts.push(value);
        }
      } finally { reader.releaseLock(); }
      const data = new Uint8Array(bytes); let offset = 0;
      for (const part of parts) { data.set(part, offset); offset += part.length; }
      const body = JSON.parse(new TextDecoder().decode(data));
      if (Object.keys(body).some(k => !['to', 'subject', 'text'].includes(k)) ||
          !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.to ?? '') ||
          !['验证你的今日小票邮箱 / Verify your email', '重设今日小票密码 / Reset your password'].includes(body.subject) ||
          typeof body.text !== 'string' || body.text.length > 2000) return reply(400);
      const upstream = await fetcher(`https://api.agentmail.to/v0/inboxes/${encodeURIComponent(env.INBOX_ID)}/messages/send`, {
        // Workers supports manual/follow only. 3xx remains a failure and must
        // never forward the provider credential to a redirected destination.
        method: 'POST', redirect: 'manual', signal: AbortSignal.timeout(9000),
        headers: { Authorization: authorization, 'Content-Type': 'application/json', 'Idempotency-Key': idempotency, 'User-Agent': 'Xiaopiao-Agent/1.0' },
        body: JSON.stringify(body),
      });
      await upstream.body?.cancel();
      return reply(upstream.ok ? 200 : 503);
    } catch { return reply(503); }
  };
}
export default { fetch: createMailRelay() };
