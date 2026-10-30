import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';

async function fixture(t) {
  const db = store(':memory:'), mail = [];
  const app = createApp({ db, sendMail: async x => mail.push(x), agent: async s => s });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(r => app.close(r)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  const call = async (path, body, token) => {
    const r = await fetch(url + '/v1/auth/' + path, { method: body ? 'POST' : 'GET', headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}),
    }, body: body ? JSON.stringify(body) : undefined });
    return { status: r.status, data: await r.json() };
  };
  const password = 'A long test-only passphrase';
  const owner = await call('register', { email: 'qa@example.test', username: 'owner', password, client: 'native' });
  await call('verify', { code: mail[0].code }, owner.data.token);
  return { db, mail, call, password, token: owner.data.token };
}
test('public account policy and validation failures identify the password field', async t => {
  const f = await fixture(t);
  const p = await f.call('policy'); assert.equal(p.status, 200); assert.equal(p.data.passwordMin, 15);
  assert.equal(p.data.profileEditing, true);
  const r = await f.call('register', { email: 'other@example.test', username: 'other', password: 'test-short', client: 'native' });
  assert.equal(r.status, 400); assert.equal(r.data.field, 'password');
});
test('profile updates preserve identity and require a verified account', async t => {
  const f = await fixture(t);
  assert.equal((await f.call('profile', { username: 'renamed' })).status, 401);
  const id = f.db.prepare('SELECT id FROM users').get().id;
  const r = await f.call('profile', { username: 'Renamed' }, f.token);
  assert.equal(r.status, 200); assert.equal(r.data.user.username, 'renamed'); assert.equal(r.data.user.id, id);
  const invalid = await f.call('profile', { username: '?' }, f.token);
  assert.equal(invalid.status, 400); assert.equal(invalid.data.field, 'username');
});
test('email change requires reauthentication, new-address verification, and revokes other sessions', async t => {
  const f = await fixture(t);
  const login = await f.call('login', { identifier: 'owner', password: f.password, client: 'native' });
  const wrong = await f.call('email-request', { email: 'new@example.test', currentPassword: 'wrong' }, f.token);
  assert.equal(wrong.status, 400); assert.equal(wrong.data.field, 'currentPassword');
  const r = await f.call('email-request', { email: 'new@example.test', currentPassword: f.password }, f.token);
  assert.equal(r.status, 200); assert.equal(f.mail.at(-1).to, 'new@example.test');
  assert.equal(f.db.prepare('SELECT email FROM users').get().email, 'qa@example.test');
  const done = await f.call('email-confirm', { code: f.mail.at(-1).code }, f.token);
  assert.equal(done.status, 200); assert.equal(done.data.user.email, 'new@example.test');
  assert.equal((await f.call('me', null, login.data.token)).status, 401);
  assert.equal((await f.call('me', null, f.token)).status, 200);
  assert.equal((await f.call('email-confirm', { code: f.mail.at(-1).code }, f.token)).status, 400);
});
test('failed email-change codes consume attempts and cannot be brute-forced', async t => {
  const f = await fixture(t);
  await f.call('email-request', { email: 'new@example.test', currentPassword: f.password }, f.token);
  for (let i = 0; i < 5; i++) assert.equal((await f.call('email-confirm', { code: '000000' }, f.token)).status, 400);
  assert.equal((await f.call('email-confirm', { code: f.mail.at(-1).code }, f.token)).status, 400);
  assert.equal(f.db.prepare('SELECT email FROM users').get().email, 'qa@example.test');
});
