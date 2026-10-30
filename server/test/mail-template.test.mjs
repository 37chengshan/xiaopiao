import { test } from 'node:test';
import assert from 'node:assert/strict';
import { verificationHtml } from '../src/mail-template.mjs';
import { createMailRelay } from '../deploy/mail-relay.mjs';
test('HTML verification mail has fixed branded markup and rejects HTML injection', () => {
  for (const purpose of ['verify', 'reset']) {
    const html = verificationHtml(purpose, '123456');
    assert.match(html, /<!doctype html>/); assert.match(html, /123456/); assert.match(html, /10 分钟/);
    assert.ok(!/<script|<img|https?:\/\//i.test(html));
    assert.ok(Buffer.byteLength(JSON.stringify({ html, text: 'fallback' })) < 8192);
  }
  assert.throws(() => verificationHtml('verify', '<script>'));
});
test('relay forwards the optional HTML body while preserving text fallback and fixed destination', async () => {
  let sent;
  const relay = createMailRelay(async (_, options) => { sent = JSON.parse(options.body); return new Response(); });
  const env = { RELAY_KEY: 'test-placeholder-secret-longer-than-32', INBOX_ID: 'qa@example.test', ORIGIN_IP: '192.0.2.1' };
  const body = { to: env.INBOX_ID, subject: '验证你的今日小票邮箱 / Verify your email', text: 'fallback', html: verificationHtml('verify', '123456') };
  const response = await relay(new Request('https://xiaopiao.citygenius.top/__mail/send', { method: 'POST',
    headers: { 'CF-Connecting-IP': env.ORIGIN_IP, 'X-Xiaopiao-Relay-Key': env.RELAY_KEY, Authorization: 'Bearer test-placeholder', 'Idempotency-Key': 'a'.repeat(64) }, body: JSON.stringify(body) }), env);
  assert.equal(response.status, 200); assert.equal(sent.html, body.html); assert.equal(sent.text, 'fallback');
});
