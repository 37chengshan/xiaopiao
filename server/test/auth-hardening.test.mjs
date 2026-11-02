import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { scryptSync } from 'node:crypto';
import { store } from '../src/store.mjs';
import { KDF } from '../src/password.mjs';
import { createApp } from '../src/app.mjs';

/** One derivation at test cost: the rules are under test, not 128 MiB per login. */
const KDF_TEST = { ...KDF, logN: 12, concurrency: 1, queue: 4 };
const PHRASE = 'A long test phrase 123';

async function fixture(t, options = {}) {
  const db = store(':memory:'), mail = [];
  const app = createApp({ db, sendMail: async item => mail.push(item), agent: async state => state, kdf: KDF_TEST, ...options });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function request(path, { body, token, headers = {} } = {}) {
    const response = await fetch(url + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { response, data: await response.json().catch(() => null) };
  }
  const login = (identifier, password, headers) => request('/v1/auth/login', { body: { identifier, password, client: 'native' }, headers });
  /** Registers and verifies; returns the live session token. */
  async function account({ email = 'owner@example.test', username = 'owner', password = PHRASE } = {}) {
    const created = await request('/v1/auth/register', { body: { email, username, password, client: 'native' } });
    assert.equal(created.response.status, 201, JSON.stringify(created.data));
    assert.equal((await request('/v1/auth/verify', { body: { code: mail.at(-1).code }, token: created.data.token })).response.status, 200);
    return { token: created.data.token, id: created.data.user.id, email, username, password };
  }
  return { db, mail, request, login, account };
}

test('weak new passwords are refused with a field, and nothing is created', async t => {
  const f = await fixture(t);
  for (const [password, username] of [['password123', 'someone'], ['01234567', 'someone'], ['longperson', 'longperson'], ['short', 'someone']]) {
    const result = await f.request('/v1/auth/register', { body: { email: `${username}@example.test`, username, password, client: 'native' } });
    assert.equal(result.response.status, 400, password);
    assert.equal(result.data.error, password === 'short' ? 'INVALID_INPUT' : 'PASSWORD_WEAK');
    assert.equal(result.data.field, 'password');
  }
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM users').get().n, 0);
});

test('a strong password is stored versioned, and weak ones are refused on reset too', async t => {
  const f = await fixture(t);
  const person = await f.account();
  assert.match(f.db.prepare('SELECT password FROM users WHERE id=?').get(person.id).password, /^scrypt1\$12\$8\$1\$/);
  await f.request('/v1/auth/forgot', { body: { email: person.email } });
  const reset = await f.request('/v1/auth/reset', { body: { email: person.email, code: f.mail.at(-1).code, password: 'password123' } });
  assert.equal(reset.response.status, 400);
  assert.equal(reset.data.error, 'PASSWORD_WEAK');
  // An address that does not exist answers the same way it always did, so strength never reveals accounts.
  const unknown = await f.request('/v1/auth/reset', { body: { email: 'nobody@example.test', code: '123456', password: 'password123' } });
  assert.equal(unknown.data.error, 'CODE_INVALID');
});

test('five wrong passwords lock the account for a minute instead of allowing ten', async t => {
  const f = await fixture(t, { trustedProxy: true });
  const person = await f.account();
  for (let i = 0; i < 5; i++) {
    const miss = await f.login(person.username, `${person.password}${i}`);
    assert.equal(miss.response.status, 401);
    assert.equal(miss.data.error, 'INVALID_CREDENTIALS');
  }
  const blocked = await f.login(person.username, person.password);
  assert.equal(blocked.response.status, 429);
  assert.equal(blocked.data.error, 'RATE_LIMITED');
  assert.ok(Number(blocked.response.headers.get('retry-after')) <= 60);
  // The block is checked before any derivation, and it follows the account: another address gets no further tries.
  const elsewhere = await f.request('/v1/auth/login', {
    body: { identifier: person.email, password: person.password, client: 'native' }, headers: { 'x-xiaopiao-client-ip': '198.51.100.7' },
  });
  assert.equal(elsewhere.response.status, 429);
});

test('one source guessing across accounts is capped at twenty misses an hour', async t => {
  const f = await fixture(t, { trustedProxy: true });
  const person = await f.account();
  const address = { 'x-xiaopiao-client-ip': '203.0.113.9' };
  for (let i = 0; i < 20; i++) {
    const miss = await f.request('/v1/auth/login', { body: { identifier: `nobody${i}`, password: 'whatever-it-is', client: 'native' }, headers: address });
    assert.equal(miss.response.status, 401, `miss ${i}`);
  }
  // Right password, right account, same source: still refused.
  const stuffed = await f.request('/v1/auth/login', { body: { identifier: person.username, password: person.password, client: 'native' }, headers: address });
  assert.equal(stuffed.response.status, 429);
  assert.equal(stuffed.data.error, 'RATE_LIMITED');
  assert.ok(Number(stuffed.response.headers.get('retry-after')) > 60);
  // Another source is untouched.
  const healthy = await f.request('/v1/auth/login', { body: { identifier: person.username, password: person.password, client: 'native' }, headers: { 'x-xiaopiao-client-ip': '198.51.100.7' } });
  assert.equal(healthy.response.status, 200);
});

test('a pre-versioning hash logs in and is upgraded in place', async t => {
  const f = await fixture(t);
  const person = await f.account();
  const salt = 'LegacySaltValueForTest';
  const legacy = `${salt}:${scryptSync(person.password, salt, 64, { N: 2 ** 17, r: 8, p: 1, maxmem: 256 * 1024 * 1024 }).toString('hex')}`;
  f.db.prepare('UPDATE users SET password=? WHERE id=?').run(legacy, person.id);
  const result = await f.login(person.username, person.password);
  assert.equal(result.response.status, 200);
  assert.match(f.db.prepare('SELECT password FROM users WHERE id=?').get(person.id).password, /^scrypt1\$12\$8\$1\$/);
  // Logging in again uses the new row: the old one was replaced, not appended.
  assert.equal((await f.login(person.username, person.password)).response.status, 200);
});

test('changing the password revokes other sessions and any pending reset code', async t => {
  const f = await fixture(t);
  const person = await f.account();
  const other = await f.login(person.username, person.password);
  assert.equal(other.response.status, 200);
  assert.equal((await f.request('/v1/auth/forgot', { body: { email: person.email } })).response.status, 200);
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM codes WHERE user_id=? AND purpose='reset'").get(person.id).n, 1);

  const changed = await f.request('/v1/auth/password', { body: { currentPassword: person.password, newPassword: 'A newer phrase 456' }, token: person.token });
  assert.equal(changed.response.status, 200);
  assert.deepEqual(changed.data, { ok: true });
  // The session that asked stays; every other one and the unused reset code are gone.
  assert.equal((await f.request('/v1/auth/me', { token: person.token })).response.status, 200);
  assert.equal((await f.request('/v1/auth/me', { token: other.data.token })).response.status, 401);
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM codes WHERE user_id=? AND purpose='reset'").get(person.id).n, 0);
  assert.equal((await f.login(person.username, person.password)).response.status, 401);
  assert.equal((await f.login(person.username, 'A newer phrase 456')).response.status, 200);
});

test('changing the password needs the current one and a new, different, strong value', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/v1/auth/password', { body: { currentPassword: PHRASE, newPassword: 'A newer phrase 456' } })).response.status, 401);
  const person = await f.account();
  const wrong = await f.request('/v1/auth/password', { body: { currentPassword: 'not-the-password', newPassword: 'A newer phrase 456' }, token: person.token });
  assert.equal(wrong.response.status, 400);
  assert.equal(wrong.data.error, 'INVALID_CURRENT_PASSWORD');
  assert.equal(wrong.data.field, 'currentPassword');
  const same = await f.request('/v1/auth/password', { body: { currentPassword: person.password, newPassword: person.password }, token: person.token });
  assert.equal(same.response.status, 400);
  assert.equal(same.data.error, 'PASSWORD_UNCHANGED');
  const weak = await f.request('/v1/auth/password', { body: { currentPassword: person.password, newPassword: 'password123' }, token: person.token });
  assert.equal(weak.response.status, 400);
  assert.equal(weak.data.error, 'PASSWORD_WEAK');
  assert.equal(weak.data.field, 'newPassword');
  // Nothing changed along the way.
  assert.equal((await f.login(person.username, person.password)).response.status, 200);
  // Repeated wrong current passwords are capped (5 per hour per account) rather than free to guess.
  for (let i = 0; i < 5; i++) await f.request('/v1/auth/password', { body: { currentPassword: `wrong${i}`, newPassword: 'A newer phrase 456' }, token: person.token });
  const capped = await f.request('/v1/auth/password', { body: { currentPassword: person.password, newPassword: 'A newer phrase 456' }, token: person.token });
  assert.equal(capped.response.status, 429);
});

test('a peppered deployment tags new hashes and still logs them in', async t => {
  const f = await fixture(t, { pepper: 'test-pepper' });
  const person = await f.account({ email: 'pepper@example.test', username: 'pepper' });
  assert.match(f.db.prepare('SELECT password FROM users WHERE id=?').get(person.id).password, /^scrypt1p\$/);
  assert.equal((await f.login('pepper', PHRASE)).response.status, 200);
  assert.equal((await f.login('pepper', `${PHRASE}!`)).response.status, 401);
});
