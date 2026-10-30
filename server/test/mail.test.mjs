import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createAgentMailSender } from '../src/mail.mjs';

test('AgentMail sender is unavailable until both secret and approved inbox exist', () => {
  assert.equal(createAgentMailSender({ apiKey: 'test-placeholder' }), null);
  assert.equal(createAgentMailSender({ inboxId: 'receipt@example.test' }), null);
  assert.throws(() => createAgentMailSender({ apiKey: 'test-placeholder', inboxId: '../wrong' }));
});
test('verification and recovery use the configured inbox, finite timeout and idempotency', async () => {
  const calls = [];
  const sender = createAgentMailSender({ apiKey: 'test-placeholder', inboxId: 'receipt@example.test', fetcher: async (url, options) => { calls.push({ url, options }); return new Response('{}'); } });
  await sender({ to: 'person@example.test', purpose: 'verify', code: '123456' });
  await sender({ to: 'person@example.test', purpose: 'verify', code: '123456' });
  await sender({ to: 'person@example.test', purpose: 'reset', code: '123456' });
  assert.equal(calls[0].url, 'https://api.agentmail.to/v0/inboxes/receipt%40example.test/messages/send');
  assert.equal(calls[0].options.redirect, 'error');
  assert.equal(calls[0].options.headers['Idempotency-Key'], calls[1].options.headers['Idempotency-Key']);
  assert.notEqual(calls[0].options.headers['Idempotency-Key'], calls[2].options.headers['Idempotency-Key']);
  const body = JSON.parse(calls[0].options.body);
  assert.equal(body.to, 'person@example.test'); assert.ok(body.text.includes('123456'));
  assert.equal(body.html, undefined);
});
test('provider errors cannot expose keys or response content', async () => {
  const sender = createAgentMailSender({ apiKey: 'test-placeholder', inboxId: 'receipt@example.test', fetcher: async () => new Response('sensitive upstream response', { status: 403 }) });
  await assert.rejects(sender({ to: 'person@example.test', purpose: 'verify', code: '123456' }), error => error.message === 'MAIL_UNAVAILABLE');
});
test('an optional private relay requires a paired secret and an exact HTTPS send path', () => {
  const base = { apiKey: 'test-placeholder', inboxId: 'receipt@example.test' };
  assert.throws(() => createAgentMailSender({ ...base, relayUrl: 'https://relay.example/__mail/send' }));
  for (const relayUrl of ['http://relay.example/__mail/send', 'https://relay.example/other', 'https://relay.example/__mail/send?url=other']) {
    assert.throws(() => createAgentMailSender({ ...base, relayUrl, relayKey: 'test-relay-secret-placeholder-32-characters' }));
  }
});
test('the private relay preserves provider authorization, timeout and idempotency', async () => {
  let call;
  const sender = createAgentMailSender({ apiKey: 'test-placeholder', inboxId: 'receipt@example.test', relayUrl: 'https://relay.example/__mail/send', relayKey: 'test-relay-secret-placeholder-32-characters',
    fetcher: async (url, options) => { call = { url, options }; return new Response(); } });
  await sender({ to: 'receipt@example.test', purpose: 'verify', code: '123456' });
  assert.equal(call.url, 'https://relay.example/__mail/send');
  assert.equal(call.options.headers['X-Xiaopiao-Relay-Key'], 'test-relay-secret-placeholder-32-characters');
  assert.equal(call.options.headers.Authorization, 'Bearer test-placeholder');
  assert.equal(call.options.redirect, 'error');
  assert.match(call.options.headers['Idempotency-Key'], /^[a-f0-9]{64}$/);
});
