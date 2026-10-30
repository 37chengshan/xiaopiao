import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createMailRelay } from '../deploy/mail-relay.mjs';
const key = 'test-relay-secret-placeholder-32-characters';
const env = { RELAY_KEY: key, INBOX_ID: 'self@example.test', ORIGIN_IP: '192.0.2.1' };
function request({ path = '/__mail/send', ip = env.ORIGIN_IP, secret = key, extra = {}, text = 'verification test', host = 'xiaopiao.citygenius.top' } = {}) {
  return new Request(`https://${host}${path}`, { method: 'POST', headers: {
    'CF-Connecting-IP': ip, 'X-Xiaopiao-Relay-Key': secret, Authorization: 'Bearer test-provider-placeholder', 'Idempotency-Key': 'a'.repeat(64),
  }, body: JSON.stringify({ to: env.INBOX_ID, subject: '验证你的今日小票邮箱 / Verify your email', text, ...extra }) });
}
test('mail relay refuses wrong hostname/path, origin and secret before contacting upstream', async () => {
  let calls = 0; const relay = createMailRelay(async () => { calls++; return new Response(); });
  for (const options of [{ path: '/anything' }, { host: 'other.example' }, { path: '/__mail/send?url=https://other.example' }]) assert.equal((await relay(request(options), env)).status, 404);
  for (const options of [{ ip: '192.0.2.2' }, { secret: 'wrong-secret-placeholder-with-32-characters' }]) assert.equal((await relay(request(options), env)).status, 403);
  assert.equal(calls, 0);
});
test('mail relay has a fixed sender destination, strips relay secret and suppresses provider output', async () => {
  let call;
  const relay = createMailRelay(async (url, options) => { call = { url, options }; return new Response('private provider response'); });
  const response = await relay(request(), env);
  assert.equal(response.status, 200); assert.equal(await response.text(), '');
  assert.equal(call.url, 'https://api.agentmail.to/v0/inboxes/self%40example.test/messages/send');
  assert.equal(call.options.headers['X-Xiaopiao-Relay-Key'], undefined);
  assert.equal(call.options.redirect, 'error');
  assert.equal(call.options.headers['Idempotency-Key'], 'a'.repeat(64));
  const failure = createMailRelay(async () => new Response('sensitive failure', { status: 403 }));
  assert.equal((await failure(request(), env)).status, 503);
});
test('mail relay bounds bodies and rejects additional fields without forwarding', async () => {
  let calls = 0; const relay = createMailRelay(async () => { calls++; return new Response(); });
  assert.equal((await relay(request({ text: 'x'.repeat(9000) }), env)).status, 413);
  assert.equal((await relay(request({ extra: { url: 'https://other.example' } }), env)).status, 400);
  assert.equal(calls, 0);
});
