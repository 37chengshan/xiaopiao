import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';
import { createWechatClient, sceneFor, ticketFromScene } from '../src/wechat.mjs';

const secret = 'test-only-appsecret';
const account = { email: 'person@example.test', username: 'person', password: 'A long test passphrase 123', client: 'native' };

/** A WeChat mini program stand-in: `wx.login` codes map to openids, and every code works exactly once. */
function fakeWechat() {
  const codes = new Map(), scenes = [], live = new Set();
  return {
    configured: true,
    issue: (openid, code = `code-${openid}-${codes.size}`) => { codes.set(code, openid); return code; },
    scenes,
    live,
    async openidFor(code) {
      const openid = codes.get(code);
      if (!openid) throw Object.assign(new Error('WECHAT_CODE_INVALID'), { code: 'WECHAT_CODE_INVALID' });
      codes.delete(code);
      return openid;
    },
    async bindCode({ scene }) { scenes.push(scene); live.add(scene); return Buffer.from(`fake-png:${scene}`); },
  };
}

async function fixture(t, options = {}) {
  const db = store(':memory:'), mail = [], wechat = fakeWechat();
  const app = createApp({ db, sendMail: async item => mail.push(item), wechatClient: wechat,
    agent: async state => state, ...options });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function request(path, body, token, method) {
    const response = await fetch(url + path, {
      method: method ?? (body === undefined ? 'GET' : 'POST'),
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, headers: response.headers, data: await response.json().catch(() => null) };
  }
  async function emailAccount() {
    const created = await request('/v1/auth/register', account);
    await request('/v1/auth/verify', { code: mail[0].code }, created.data.token);
    return created.data.token;
  }
  async function wechatAccount(openid = 'openid_alpha_123') {
    const code = wechat.issue(openid);
    const login = await request('/v1/auth/wechat', { code, client: 'native' });
    assert.equal(login.status, 200, JSON.stringify(login.data));
    return { token: login.data.token, user: login.data.user };
  }
  return { db, mail, wechat, request, emailAccount, wechatAccount };
}

test('a first WeChat login creates an account with no email, and the next one reuses it', async t => {
  const f = await fixture(t);
  const first = await f.wechatAccount('openid_alpha_123');
  assert.equal(first.user.email, null);
  assert.equal(first.user.wechat, true);
  assert.equal(first.user.verified, true, 'holding an openid is a real login method, so chat and ledger work');
  assert.match(first.token, /^[A-Za-z0-9_-]{43}$/);
  assert.match(first.user.username, /^wx_[0-9a-f]+$/);
  const second = await f.wechatAccount('openid_alpha_123');
  assert.equal(second.user.id, first.user.id);
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM users').get().n, 1);
  assert.equal((await f.request('/v1/auth/me', undefined, first.token)).data.user.id, first.user.id);
});

test('an unknown or reused wx.login code is refused without creating an account', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/v1/auth/wechat', { code: 'never-issued' })).status, 400);
  assert.equal((await f.request('/v1/auth/wechat', { code: 'never-issued' })).data.error, 'WECHAT_CODE_INVALID');
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM users').get().n, 0);
  assert.equal((await f.request('/v1/auth/wechat', {})).status, 400);
});

test('the real client drops session_key and never returns it to a client', async t => {
  const seen = [];
  const fetcher = async (url, options) => {
    seen.push(String(url));
    if (String(url).includes('jscode2session')) {
      assert.match(String(url), /js_code=the-code/);
      return Response.json({ openid: 'openid_from_wechat', session_key: 'super-secret-session-key' });
    }
    if (String(url).includes('cgi-bin/token')) return Response.json({ access_token: 'token-1', expires_in: 7200 });
    return new Response(Buffer.from('png'), { headers: { 'Content-Type': 'image/png' } });
  };
  const client = createWechatClient({ appId: 'wxtest', secret, fetcher });
  assert.equal(await client.openidFor('the-code'), 'openid_from_wechat');
  const db = store(':memory:');
  const app = createApp({ db, sendMail: async () => {}, wechatClient: client, agent: async state => state });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const base = `http://127.0.0.1:${app.address().port}`;
  const response = await fetch(`${base}/v1/auth/wechat`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: 'the-code', client: 'native' }),
  });
  const text = await response.text();
  assert.equal(response.status, 200);
  assert.equal(text.includes('session_key'), false, 'session_key never leaves the server');
  assert.equal(text.includes('super-secret'), false);
  assert.match(text, /"wechat":true/);
});

test('the App gets a one-time ticket, the mini program binds it, and the App sees the result', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  assert.equal(ticket.status, 201);
  assert.match(ticket.data.id, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(ticket.data.scene, sceneFor(ticket.data.id));
  assert.equal(ticketFromScene(ticket.data.scene), ticket.data.id);
  assert.equal(Buffer.from(ticket.data.image, 'base64').toString(), `fake-png:${ticket.data.scene}`);
  assert.equal(ticket.data.mime, 'image/png');
  assert.ok(ticket.data.expiresAt > Date.now());

  const poll = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}`, undefined, token);
  assert.equal(poll.data.status, 'pending');

  const code = f.wechat.issue('openid_beta_456');
  const bound = await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code, client: 'native' });
  assert.equal(bound.status, 200, JSON.stringify(bound.data));
  assert.equal(bound.data.user.wechat, true);
  assert.equal(bound.data.user.id, poll.status === 200 ? bound.data.user.id : null);

  const after = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}`, undefined, token);
  assert.equal(after.data.status, 'bound');
  assert.equal(after.data.user.wechat, true);

  // Replay is refused: one ticket, one binding.
  const replay = await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_beta_456') });
  assert.equal(replay.status, 400);
  assert.equal(replay.data.error, 'WECHAT_TICKET_INVALID');
});

test('a ticket expires, belongs to its requester, and cannot bind twice under concurrency', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const expired = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  f.db.prepare('UPDATE wechat_bind_tickets SET expires=?').run(Date.now() - 1);
  const late = await f.request('/v1/auth/wechat/bind', { ticket: expired.data.id, code: f.wechat.issue('openid_late_789') });
  assert.equal(late.status, 400);
  assert.equal((await f.request(`/v1/auth/wechat/bind-ticket/${expired.data.id}`, undefined, token)).data.status, 'expired');

  const fresh = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  const other = await f.wechatAccount('openid_other_user');
  const stranger = await f.request(`/v1/auth/wechat/bind-ticket/${fresh.data.id}`, undefined, other.token);
  assert.equal(stranger.data.error, 'WECHAT_TICKET_INVALID', 'a ticket reveals nothing to the account that did not ask for it');

  const codes = [f.wechat.issue('openid_race_000'), f.wechat.issue('openid_race_000')];
  const results = await Promise.all(codes.map(code => f.request('/v1/auth/wechat/bind', { ticket: fresh.data.id, code })));
  assert.deepEqual(results.map(result => result.status).sort(), [200, 400]);
});

test('ticket requests are rate limited per account', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const results = [];
  for (let i = 0; i < 11; i++) results.push((await f.request('/v1/auth/wechat/bind-ticket', {}, token)).status);
  assert.deepEqual(results.slice(0, 10), Array(10).fill(201));
  assert.equal(results[10], 429, 'eleven tickets in an hour is not a person');
});

test('an email that already belongs to another account is refused instead of merged', async t => {
  const f = await fixture(t);
  await f.emailAccount(); // person@example.test
  const wechat = await f.wechatAccount('openid_needs_email');
  const attach = await f.request('/v1/auth/email-attach', { email: 'person@example.test' }, wechat.token);
  assert.equal(attach.status, 409);
  assert.equal(attach.data.error, 'EMAIL_BOUND_ELSEWHERE');
  assert.match(attach.data.message, /扫小程序码/);
  assert.equal(f.mail.at(-1).to, account.email === 'person@example.test' ? f.mail.at(-1).to : undefined);
});

test('a WeChat account attaches an email with a code, then sets its first password', async t => {
  const f = await fixture(t);
  const wechat = await f.wechatAccount('openid_wants_email');
  const attach = await f.request('/v1/auth/email-attach', { email: 'wx@example.test' }, wechat.token);
  assert.equal(attach.status, 200);
  assert.equal(f.mail.at(-1).to, 'wx@example.test');
  const confirm = await f.request('/v1/auth/email-attach/confirm', { code: f.mail.at(-1).code }, wechat.token);
  assert.equal(confirm.status, 200, JSON.stringify(confirm.data));
  assert.equal(confirm.data.user.email, 'wx@example.test');
  assert.equal(confirm.data.user.verified, true);

  // A password needs an email to be recoverable, and now there is one.
  const set = await f.request('/v1/auth/password', { newPassword: 'Another long passphrase 9' }, wechat.token);
  assert.equal(set.status, 200, JSON.stringify(set.data));
  const login = await f.request('/v1/auth/login', { identifier: 'wx@example.test', password: 'Another long passphrase 9', client: 'native' });
  assert.equal(login.status, 200);
  assert.equal(login.data.user.id, wechat.user.id);
});

test('a password cannot be set before an email exists', async t => {
  const f = await fixture(t);
  const wechat = await f.wechatAccount('openid_no_email');
  const set = await f.request('/v1/auth/password', { newPassword: 'Another long passphrase 9' }, wechat.token);
  assert.equal(set.status, 400);
  assert.equal(set.data.error, 'EMAIL_REQUIRED');
});

test('unbinding refuses to remove the only way in, and works once an email and password exist', async t => {
  const f = await fixture(t);
  const wechat = await f.wechatAccount('openid_unbind_me');
  const blocked = await f.request('/v1/auth/wechat', undefined, wechat.token, 'DELETE');
  assert.equal(blocked.status, 409);
  assert.equal(blocked.data.error, 'WECHAT_LAST_LOGIN');

  await f.request('/v1/auth/email-attach', { email: 'unbind@example.test' }, wechat.token);
  await f.request('/v1/auth/email-attach/confirm', { code: f.mail.at(-1).code }, wechat.token);
  await f.request('/v1/auth/password', { newPassword: 'Another long passphrase 9' }, wechat.token);
  const removed = await f.request('/v1/auth/wechat', undefined, wechat.token, 'DELETE');
  assert.equal(removed.status, 200, JSON.stringify(removed.data));
  assert.equal(removed.data.user.wechat, false);
  assert.equal((await f.request('/v1/auth/me', undefined, wechat.token)).data.user.wechat, false);
  const again = await f.request('/v1/auth/wechat', undefined, wechat.token, 'DELETE');
  assert.equal(again.data.error, 'WECHAT_NOT_BOUND');
});

test('a WeChat identity already used by another account is refused', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  await f.wechatAccount('openid_taken');
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  const taken = await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_taken') });
  assert.equal(taken.status, 409);
  assert.equal(taken.data.error, 'WECHAT_IN_USE');
  assert.equal(f.db.prepare("SELECT count(*) AS n FROM users WHERE wx_openid='openid_taken'").get().n, 1);
});

test('an account that already has a different WeChat cannot be rebound', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  f.db.prepare("UPDATE users SET wx_openid='openid_existing' WHERE email=?").run(account.email);
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  assert.equal(ticket.status, 409);
  assert.equal(ticket.data.error, 'WECHAT_ALREADY_BOUND');
  // Even with a ticket from elsewhere, binding a second identity is refused.
  f.db.prepare('INSERT INTO wechat_bind_tickets (digest,user_id,status,openid,created_at,expires) VALUES (?,?,?,?,?,?)')
    .run('deadbeef', f.db.prepare('SELECT id FROM users WHERE email=?').get(account.email).id, 'pending', null, Date.now(), Date.now() + 60_000);
  const bind = await f.request('/v1/auth/wechat/bind', { ticket: 'x'.repeat(43), code: f.wechat.issue('openid_new') });
  assert.equal(bind.status, 400, 'a ticket that is not ours is simply invalid');
});

test('WeChat routes fail closed with a clear code when the mini program is not configured', async t => {
  const f = await fixture(t, { wechatClient: createWechatClient({}) });
  const login = await f.request('/v1/auth/wechat', { code: 'anything', client: 'native' });
  assert.equal(login.status, 503);
  assert.equal(login.data.error, 'WECHAT_UNAVAILABLE');
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  assert.equal(ticket.status, 503);
});
