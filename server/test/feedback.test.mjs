import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomBytes, randomUUID } from 'node:crypto';
import { store, hash } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';
import { createFeedback, LIMITS } from '../src/feedback.mjs';

/** Real HTTP, in-memory SQLite — same fixture shape as circle.test.mjs, without the mail box. */
async function fixture(t) {
  const db = store(':memory:');
  const app = createApp({ db, sendMail: async () => {}, agent: async state => state });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  /** `raw` sends the string untouched, so the byte limit can be hit without a legal payload. */
  async function request(path, { method = 'GET', body, token, raw, type = 'application/json' } = {}) {
    const payload = raw ?? (body === undefined ? undefined : JSON.stringify(body));
    const response = await fetch(url + path, {
      method,
      headers: { ...(payload === undefined ? {} : { 'Content-Type': type }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: payload,
    });
    return { response, data: await response.json().catch(() => null) };
  }
  return { db, request };
}

/** A ready account skips scrypt: for feedback only the session lookup is under test. */
function seeded(db, name) {
  const id = randomUUID(); const token = randomBytes(32).toString('base64url');
  db.prepare('INSERT INTO users (id,email,username,password,verified,created) VALUES (?,?,?,?,1,?)').run(id, `${name}@example.test`, name, 'x:y', Date.now());
  db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(hash(token), id, Date.now() + 86400_000);
  return { id, token, username: name };
}

const note = (over = {}) => ({ kind: 'general', text: '小票识别错了，帮我看看', appVersion: '0.9.3', device: 'Xiaomi 14 · Android 15', ...over });
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const rows = db => db.prepare('SELECT * FROM feedback ORDER BY created_at, rowid').all();

test('anonymous feedback is stored without a user and answered with an id', async t => {
  const f = await fixture(t);
  const result = await f.request('/v1/feedback', { method: 'POST', body: note({ kind: 'capture_miss', text: '  支付宝 | 收钱码到账 | 金额 18.00  ' }) });
  assert.equal(result.response.status, 201);
  assert.equal(result.data.ok, true);
  assert.match(result.data.id, UUID);
  const [row] = rows(f.db);
  assert.equal(row.user_id, null);
  assert.equal(row.kind, 'capture_miss');
  assert.equal(row.text, '支付宝 | 收钱码到账 | 金额 18.00');
  assert.equal(row.app_version, '0.9.3');
  assert.equal(row.device, 'Xiaomi 14 · Android 15');
  assert.ok(Number.isSafeInteger(row.created_at) && row.created_at > 0);
});

test('a signed-in sender is stored against the account; a dead token is anonymous, never 401', async t => {
  const f = await fixture(t);
  const person = seeded(f.db, 'writer');
  const signed = await f.request('/v1/feedback', { method: 'POST', body: note(), token: person.token });
  assert.equal(signed.response.status, 201);
  // A stale token must not turn into 401: the App clears its session on 401 and the note would be lost.
  const stale = await f.request('/v1/feedback', { method: 'POST', body: note({ text: '没有登录也要能发' }), token: randomBytes(32).toString('base64url') });
  assert.equal(stale.response.status, 201);
  const [first, second] = rows(f.db);
  assert.equal(first.user_id, person.id);
  assert.equal(second.user_id, null);
});

test('an unknown kind or an unexpected key is refused', async t => {
  const f = await fixture(t);
  const post = body => f.request('/v1/feedback', { method: 'POST', body });
  // Every attempt counts against the 10/hour cap, rejected or not, so the refusals are split across tests.
  for (const bad of [
    note({ kind: 'bug' }), note({ kind: undefined }), note({ kind: 'Capture_Miss' }),
    { ...note(), note: 'extra' }, { ...note(), userId: 'someone-else' }, { ...note(), client: 'native' },
  ]) {
    const result = await post(bad);
    assert.equal(result.response.status, 400, JSON.stringify(bad).slice(0, 80));
    assert.equal(result.data.error, 'INVALID_INPUT');
  }
  assert.equal(rows(f.db).length, 0);
  // Both optional labels may be left out entirely.
  assert.equal((await post({ kind: 'general', text: '只有两个字段', device: 'Pixel 8' })).response.status, 201);
  assert.equal((await post({ kind: 'capture_miss', text: '也只要两个字段' })).response.status, 201);
  assert.deepEqual(rows(f.db).map(row => [row.kind, row.app_version, row.device]).at(-1), ['capture_miss', '', '']);
});

test('empty or over-long text and labels are refused; the limits themselves are inclusive', async t => {
  const f = await fixture(t);
  const post = body => f.request('/v1/feedback', { method: 'POST', body });
  for (const bad of [
    note({ text: '' }), note({ text: '   \n\t ' }), note({ text: undefined }), note({ text: `${'a'.repeat(LIMITS.text)}b` }),
    note({ appVersion: 'v'.repeat(LIMITS.appVersion + 1) }), note({ device: 'd'.repeat(LIMITS.device + 1) }),
    note({ appVersion: 93 }), note({ device: ['Xiaomi'] }),
  ]) {
    const result = await post(bad);
    assert.equal(result.response.status, 400, JSON.stringify(bad).slice(0, 80));
    assert.equal(result.data.error, 'INVALID_INPUT');
  }
  assert.equal(rows(f.db).length, 0);
  const edge = await post(note({ text: 'a'.repeat(LIMITS.text), appVersion: 'v'.repeat(LIMITS.appVersion), device: 'd'.repeat(LIMITS.device) }));
  assert.equal(edge.response.status, 201);
  assert.equal(rows(f.db)[0].text.length, LIMITS.text);
});

test('an error response never echoes the text the user sent', async t => {
  const f = await fixture(t);
  const secret = '收款 99.90 元 张三';
  const result = await f.request('/v1/feedback', { method: 'POST', body: { kind: 'nope', text: secret, appVersion: '0.9.3', device: 'Pixel' } });
  assert.equal(result.response.status, 400);
  assert.doesNotMatch(JSON.stringify(result.data), /收款|99\.90|张三/);
});

test('the body limit refuses more than 16 KB before parsing', async t => {
  const f = await fixture(t);
  const oversized = `{"kind":"general","text":"${'a'.repeat(17_000)}"}`;
  const result = await f.request('/v1/feedback', { method: 'POST', raw: oversized });
  assert.ok(Buffer.byteLength(oversized) > LIMITS.body);
  assert.equal(result.response.status, 400);
  assert.equal(rows(f.db).length, 0);
});

test('ten notes an hour per sender, then 429 with Retry-After', async t => {
  const f = await fixture(t);
  for (let i = 0; i < LIMITS.perHour; i++) {
    assert.equal((await f.request('/v1/feedback', { method: 'POST', body: note({ text: `第 ${i} 条` }) })).response.status, 201);
  }
  const blocked = await f.request('/v1/feedback', { method: 'POST', body: note({ text: '第十一条' }) });
  assert.equal(blocked.response.status, 429);
  assert.equal(blocked.data.error, 'RATE_LIMITED');
  assert.ok(Number(blocked.response.headers.get('retry-after')) > 0);
  assert.equal(rows(f.db).length, LIMITS.perHour);
});

test('the per-hour cap is per account, not per address', async t => {
  const f = await fixture(t);
  const one = seeded(f.db, 'one'), two = seeded(f.db, 'two');
  for (let i = 0; i < LIMITS.perHour; i++) {
    assert.equal((await f.request('/v1/feedback', { method: 'POST', body: note(), token: one.token })).response.status, 201);
  }
  assert.equal((await f.request('/v1/feedback', { method: 'POST', body: note(), token: one.token })).response.status, 429);
  // Same address, another account: still allowed, and an anonymous note from that address is not what was capped.
  assert.equal((await f.request('/v1/feedback', { method: 'POST', body: note(), token: two.token })).response.status, 201);
});

test('the client IP is stored only as the existing hash, never in clear', async t => {
  const f = await fixture(t);
  const person = seeded(f.db, 'hashed');
  await f.request('/v1/feedback', { method: 'POST', body: note() });
  await f.request('/v1/feedback', { method: 'POST', body: note(), token: person.token });
  const stored = rows(f.db);
  assert.equal(stored.length, 2);
  for (const row of stored) assert.equal(row.ip_hash, hash('127.0.0.1'));
  // The address survives nowhere that belongs to feedback: the per-sender limit key hashes it too.
  const dump = JSON.stringify([...stored, ...f.db.prepare("SELECT * FROM limits WHERE key LIKE 'feedback:%'").all()]);
  assert.doesNotMatch(dump, /127\.0\.0\.1/);
  assert.deepEqual(f.db.prepare('SELECT key FROM limits WHERE key LIKE ?').all('feedback:%').map(row => row.key).sort(),
    [`feedback:${hash('127.0.0.1')}`, `feedback:${person.id}`, 'feedback:all'].sort());
});

test('feedback has no HTTP read path', async t => {
  const f = await fixture(t);
  await f.request('/v1/feedback', { method: 'POST', body: note() });
  assert.equal((await f.request('/v1/feedback')).response.status, 404);
  assert.equal((await f.request('/v1/feedback', { method: 'POST', raw: '' })).response.status, 400);
});

test('the operator read returns newest first and honours since and limit', async t => {
  const f = await fixture(t);
  for (const text of ['最早', '中间', '最新']) {
    assert.equal((await f.request('/v1/feedback', { method: 'POST', body: note({ text }) })).response.status, 201);
  }
  const reader = createFeedback({ db: f.db });
  const all = reader.read();
  assert.deepEqual(all.map(row => row.text), ['最新', '中间', '最早']);
  assert.deepEqual(Object.keys(all[0]).sort(), ['appVersion', 'createdAt', 'device', 'id', 'ipHash', 'kind', 'text', 'userId']);
  assert.equal(reader.read({ limit: 2 }).length, 2);
  const middle = reader.read({ since: all[1].createdAt });
  assert.ok(middle.length >= 2);
  assert.ok(middle.every(row => row.createdAt >= all[1].createdAt));
});
