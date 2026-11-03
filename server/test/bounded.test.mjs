import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readBounded, textBounded } from '../src/bounded.mjs';
import { createWechatClient } from '../src/wechat.mjs';

/** A body whose chunks can be counted, so "was it cancelled?" is observable. */
function stream(chunks, { declared = null, cancel = () => {} } = {}) {
  const queue = [...chunks];
  let cancelled = false;
  return {
    response: {
      ok: true,
      headers: { get: name => (name === 'content-length' && declared !== null ? String(declared) : null) },
      body: {
        cancel: async () => { cancelled = true; },
        getReader: () => ({
          async read() {
            if (cancelled) return { done: true, value: undefined };
            const next = queue.shift();
            return next ? { done: false, value: Buffer.from(next) } : { done: true, value: undefined };
          },
          async cancel() { cancelled = true; cancel(); },
          releaseLock() {},
        }),
      },
    },
    cancelled: () => cancelled,
  };
}

test('a declared Content-Length over the cap is refused without reading the body', async () => {
  let reads = 0;
  const body = stream([Buffer.alloc(10)], { declared: 5_000 });
  const reader = body.response.body.getReader();
  body.response.body.getReader = () => ({ ...reader, read: async () => { reads++; return { done: true }; } });
  await assert.rejects(readBounded(body.response, 1_000, { code: 'TOO_BIG' }), error => error.code === 'TOO_BIG');
  assert.equal(reads, 0);
  assert.equal(body.cancelled(), true, 'the socket is released even when nothing was read');
});

test('a chunked body without a length stops at the cap and cancels', async () => {
  const body = stream([Buffer.alloc(600), Buffer.alloc(600)]);
  await assert.rejects(readBounded(body.response, 1_000), error => error.code === 'BODY_TOO_LARGE');
  assert.equal(body.cancelled(), true);
});

test('a body inside the cap is returned whole, as bytes and as text', async () => {
  const bytes = stream(['{"a":', '1}']);
  assert.equal((await readBounded(bytes.response, 100)).toString(), '{"a":1}');
  const text = stream(['中文', '{"ok":true}']);
  assert.equal(await textBounded(text.response, 100), '中文{"ok":true}');
  assert.equal(bytes.cancelled(), true, 'released after a successful read too');
});

test('the WeChat client refuses a scene WeChat itself would reject', async () => {
  let calls = 0;
  const client = createWechatClient({ appId: 'wxtest', secret: 's', fetcher: async () => { calls++; return Response.json({ access_token: 't' }); } });
  await assert.rejects(client.bindCode({ scene: `b:${'x'.repeat(43)}`, page: 'pages/bind/bind' }), /SCENE_TOO_LONG/);
  assert.equal(calls, 0, 'the request is never sent');
  // 32 exactly is WeChat's limit: it must be accepted by our guard.
  const ok = createWechatClient({ appId: 'wxtest', secret: 's', fetcher: async url => (String(url).includes('cgi-bin/token')
    ? Response.json({ access_token: 't', expires_in: 7200 })
    : new Response(Buffer.from('png'), { headers: { 'Content-Type': 'image/png' } })) });
  assert.equal((await ok.bindCode({ scene: 'x'.repeat(32), page: 'p' })).length, 3);
});
