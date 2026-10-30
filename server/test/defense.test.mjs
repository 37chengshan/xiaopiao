import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { request as httpRequest } from 'node:http';
import { connect } from 'node:net';
import { Readable } from 'node:stream';
import { POLICY, createDefense, readJson } from '../src/defense.mjs';
import { createApp } from '../src/app.mjs';
import { store, hash, secret, passwordHash } from '../src/store.mjs';
import { createAgent, sseData } from '../src/agent.mjs';

async function fixture(t, options = {}) {
  const db = store(':memory:');
  const app = createApp({ db, agent: async state => ({ ...state, pending: [] }), sendMail: async () => {}, ...options });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(r => app.close(r)); db.close(); });
  const base = `http://127.0.0.1:${app.address().port}`;
  const user = (id = 'owner') => {
    const token = secret();
    db.prepare('INSERT INTO users VALUES (?,?,?,?,1,?)').run(id, `${id}@example.test`, id, 'unused', Date.now());
    db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(hash(token), id, Date.now() + 60_000);
    return token;
  };
  return { db, app, base, user };
}
function jsonStream(bytes, headers = {}) {
  const req = Readable.from(bytes, { objectMode: false });
  req.headers = { 'content-type': 'application/json', ...headers }; return req;
}
test('JSON decoder preserves split UTF-8 and bounds chunked bytes, media type and nesting', async () => {
  const bytes = Buffer.from(JSON.stringify({ text: '中文小票' }));
  assert.deepEqual(await readJson(jsonStream([...bytes].map(b => Buffer.from([b]))), 100), { text: '中文小票' });
  await assert.rejects(readJson(jsonStream([Buffer.alloc(200)]), 100, 'LEDGER_PAYLOAD_TOO_LARGE'), { code: 'LEDGER_PAYLOAD_TOO_LARGE' });
  await assert.rejects(readJson(jsonStream([Buffer.from('{}')], { 'content-type': 'application/jsonp' }), 100), { code: 'INVALID_INPUT' });
  await assert.rejects(readJson(jsonStream([Buffer.from([123, 34, 120, 34, 58, 34, 255, 34, 125])]), 100), { code: 'INVALID_INPUT' });
  await assert.rejects(readJson(jsonStream([Buffer.from('{"x":'.repeat(30) + '0' + '}'.repeat(30))]), 1000), { code: 'INVALID_INPUT' });
});
test('admission bounds IP cardinality, releases once, and exposes aggregate-only counters', () => {
  const d = createDefense({ ...POLICY, requests: 2, perIp: 1 });
  const a = d.admit('private-address');
  assert.throws(() => d.admit('private-address'), { code: 'SERVER_BUSY' });
  const b = d.admit('another-address');
  assert.throws(() => d.admit('third-address'), { code: 'SERVER_BUSY' });
  a(); a(); b();
  assert.equal(d.snapshot().activeIps, 0); assert.equal(d.snapshot().activeRequests, 0);
  assert.ok(!JSON.stringify(d.snapshot()).includes('private-address'));
});
test('malformed request target returns 400 and leaves server healthy', async t => {
  const f = await fixture(t);
  const socket = connect(f.app.address().port, '127.0.0.1');
  let text = ''; socket.on('data', chunk => { text += chunk; });
  socket.write('GET /v1/%zz HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n');
  await once(socket, 'close'); assert.match(text, /^HTTP\/1.1 400/);
  assert.equal((await fetch(f.base + '/healthz')).status, 200);
});
test('unauthenticated ledger/chat uploads fail before reading the body', async t => {
  const f = await fixture(t);
  for (const path of ['/v1/chat', '/v1/ledger/push']) {
    const status = await new Promise((resolve, reject) => {
      const req = httpRequest(f.base + path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': '500000' } }, res => { res.resume(); resolve(res.statusCode); });
      req.on('error', reject); req.flushHeaders();
    });
    assert.equal(status, 401);
  }
});
test('slow auth upload times out, excess auth is shed, then capacity recovers', async t => {
  const f = await fixture(t, { defensePolicy: { ...POLICY, auth: 1, bodyMs: 150 } });
  const pending = new Promise((resolve, reject) => {
    const req = httpRequest(f.base + '/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Transfer-Encoding': 'chunked' } }, res => { res.resume(); resolve(res.statusCode); });
    req.on('error', reject); req.write('{');
  });
  // Wait for the real admission state, not an assumed network delay.
  for (let i = 0; i < 100 && !f.app.defenseSnapshot().pools.auth; i++) await new Promise(r => setTimeout(r, 1));
  const response = await fetch(f.base + '/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
  assert.equal(response.status, 503); assert.equal(response.headers.get('retry-after'), '5'); await response.arrayBuffer();
  assert.equal(await pending, 408);
  const recovered = await fetch(f.base + '/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
  assert.equal(recovered.status, 400); await recovered.arrayBuffer();
});
test('global mail quota blocks provider calls and rolls back a new registration', async t => {
  let sends = 0; const f = await fixture(t, { sendMail: async () => { sends++; } });
  f.db.prepare('INSERT INTO limits VALUES (?,?,?)').run('mail:all', 100, Date.now() + 60_000);
  const r = await fetch(f.base + '/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'qa@example.test', username: 'qa_test', password: 'A sufficiently long password', client: 'native' }) });
  assert.equal(r.status, 429); await r.arrayBuffer(); assert.equal(sends, 0);
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM users').get().n, 0);
});
test('rate table saturation fails closed without preventing health checks', async t => {
  const f = await fixture(t, { defensePolicy: { ...POLICY, rateKeys: 1 } });
  f.db.prepare('INSERT INTO limits VALUES (?,?,?)').run('occupied', 1, Date.now() + 60_000);
  const r = await fetch(f.base + '/v1/auth/me'); assert.equal(r.status, 503); await r.arrayBuffer();
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM limits').get().n, 1);
  assert.equal((await fetch(f.base + '/healthz')).status, 200);
});
test('global chat pool rejects another account and deadline abort releases capacity', async t => {
  let calls = 0, aborted = false;
  const f = await fixture(t, { defensePolicy: { ...POLICY, chats: 1, chatMs: 100 }, agent: async (_state, _emit, signal) => {
    calls++; await new Promise(resolve => signal.addEventListener('abort', () => { aborted = true; resolve(); }, { once: true })); throw new Error('aborted');
  } });
  const a = f.user('owner_a'), b = f.user('owner_b');
  const chat = token => fetch(f.base + '/v1/chat', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ messages: [{ role: 'user', content: 'hello' }] }) });
  const first = await chat(a);
  const second = await chat(b); assert.equal(second.status, 503); await second.arrayBuffer();
  assert.match(await first.text(), /MODEL_UNAVAILABLE/); assert.equal(aborted, true); assert.equal(calls, 1);
  assert.equal(f.app.defenseSnapshot().pools.chats, 0);
});
test('complete oversized upstream frames and endless small frames are bounded', async () => {
  async function* oversized() { yield Buffer.from('data: ' + 'x'.repeat(260_000) + '\n\n'); }
  await assert.rejects(async () => { for await (const _ of sseData(oversized())) {} }, /FRAME_TOO_LARGE/);
  async function* endless() { for (let i = 0; i < 2100; i++) yield Buffer.from('data: ' + 'x'.repeat(1000) + '\n\n'); }
  await assert.rejects(async () => { for await (const _ of sseData(endless())) {} }, /BODY_TOO_LARGE/);
});
test('oversized provider error is cancelled without disclosing it to the client', async () => {
  let cancelled = false;
  const agent = createAgent({ apiKey: 'test-provider', models: ['reflection/beam:free'], fetcher: async (url, options) => {
    assert.equal(options.redirect, 'error');
    if (url.endsWith('/models')) return Response.json({ data: [] });
    return new Response(new ReadableStream({ pull(c) { c.enqueue(new Uint8Array(8192)); }, cancel() { cancelled = true; } }), { status: 503 });
  } });
  await assert.rejects(agent({ round: 0, messages: [], timezone: 'UTC' }, () => {}, new AbortController().signal), /MODEL_UNAVAILABLE/);
  assert.equal(cancelled, true);
});

test('disconnect cannot release an authentication work slot while mail is still running', async t => {
  let releaseMail, entered;
  const inMail = new Promise(resolve => { entered = resolve; });
  const f = await fixture(t, { defensePolicy: { ...POLICY, auth: 1 }, sendMail: async () => {
    entered(); await new Promise(resolve => { releaseMail = resolve; });
  } });
  f.user();
  const req = httpRequest(f.base + '/v1/auth/forgot', { method: 'POST', headers: { 'Content-Type': 'application/json' } });
  req.on('error', () => {}); req.end(JSON.stringify({ email: 'owner@example.test' }));
  await inMail; req.destroy();
  const r = await fetch(f.base + '/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
  assert.equal(r.status, 503); await r.arrayBuffer();
  releaseMail();
  for (let i = 0; i < 100 && f.app.defenseSnapshot().pools.auth; i++) await new Promise(resolve => setTimeout(resolve, 1));
  assert.equal(f.app.defenseSnapshot().pools.auth, 0);
});

test('authenticated circle writes retain the default JSON body limit', async t => {
  const f = await fixture(t); const token = f.user();
  const r = await fetch(f.base + '/v1/circles', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ name: 'x'.repeat(100_000) }) });
  assert.equal(r.status, 400); await r.arrayBuffer();
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM circles').get().n, 0);
});
test('a successful login keeps at most ten sessions for that account', async t => {
  const f = await fixture(t); f.user();
  const password = 'A long session test passphrase';
  f.db.prepare('UPDATE users SET password=? WHERE id=?').run(await passwordHash(password), 'owner');
  for (let i = 0; i < 12; i++) f.db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(hash(secret()), 'owner', Date.now() + 60_000 + i);
  const r = await fetch(f.base + '/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ identifier: 'owner', password, client: 'native' }) });
  assert.equal(r.status, 200); const data = await r.json();
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM sessions WHERE user_id=?').get('owner').n, 10);
  assert.ok(f.db.prepare('SELECT 1 FROM sessions WHERE digest=?').get(hash(data.token)));
});
