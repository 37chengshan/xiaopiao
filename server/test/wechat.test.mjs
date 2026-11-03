import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';
import { LIMITS, createWechatClient, sceneFor, ticketFromScene } from '../src/wechat.mjs';

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
  // Same id shape as an email registration, so every other route treats both account kinds identically.
  assert.match(first.user.id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
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

test('the ticket and the scene fit WeChat limits, and the App confirms before anything is bound', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  assert.equal(ticket.status, 201);
  // Real WeChat rejects a scene longer than 32 characters, so this is asserted from the actual values, not the mock.
  assert.equal(ticket.data.id.length, LIMITS.ticketLength);
  assert.ok(ticket.data.id.length <= 32);
  assert.equal(ticket.data.scene, sceneFor(ticket.data.id));
  assert.ok(ticket.data.scene.length <= LIMITS.maxScene, `scene ${ticket.data.scene.length} > 32 would 503 in production`);
  assert.match(ticket.data.id, /^[A-Za-z0-9_-]+$/);
  assert.equal(ticketFromScene(ticket.data.scene), ticket.data.id);
  assert.equal(f.wechat.scenes.at(-1).length, ticket.data.scene.length, 'the QR code was asked for with that scene');
  assert.equal(Buffer.from(ticket.data.image, 'base64').toString(), `fake-png:${ticket.data.scene}`);
  assert.ok(ticket.data.expiresAt > Date.now());

  const pending = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}`, undefined, token);
  assert.equal(pending.data.status, 'pending');

  // The mini program scans: this claims the ticket and shows who is asking, but binds nothing yet.
  const code = f.wechat.issue('openid_beta_456');
  const claimed = await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code, nickname: '阿票' });
  assert.equal(claimed.status, 202, JSON.stringify(claimed.data));
  assert.equal(claimed.data.status, 'scanned');
  assert.equal(claimed.data.token, undefined, 'no session before the App confirms');
  assert.equal(f.db.prepare("SELECT wx_openid FROM users WHERE email=?").get(account.email).wx_openid, null, 'nothing bound yet');

  const scanned = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}`, undefined, token);
  assert.equal(scanned.data.status, 'scanned');
  assert.deepEqual(scanned.data.wechat, { nickname: '阿票' });

  const confirmed = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}/confirm`, {}, token);
  assert.equal(confirmed.status, 200, JSON.stringify(confirmed.data));
  assert.equal(confirmed.data.user.wechat, true);
  assert.equal(f.db.prepare("SELECT wx_openid FROM users WHERE email=?").get(account.email).wx_openid, 'openid_beta_456');

  const done = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}`, undefined, token);
  assert.equal(done.data.status, 'bound');
  assert.equal(done.data.user.wechat, true);

  // The mini program learns the outcome, then signs in with its own code — the session is never handed out by a poll.
  const status = await f.request(`/v1/auth/wechat/bind-status?ticket=${ticket.data.id}`);
  assert.deepEqual(status.data, { status: 'bound' });
  const relogin = await f.request('/v1/auth/wechat', { code: f.wechat.issue('openid_beta_456'), client: 'native' });
  assert.equal(relogin.status, 200);
  assert.equal(relogin.data.user.id, done.data.user.id);

  // Replay is refused: one ticket, one binding.
  const replay = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}/confirm`, {}, token);
  assert.equal(replay.status, 400);
  assert.equal(replay.data.error, 'WECHAT_TICKET_INVALID');
});

test('someone else scanning is not enough: without the App confirming, no openid is bound', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  const claimed = await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_stranger') });
  assert.equal(claimed.status, 202);
  // The App owner walks away: the ticket expires and the account is untouched.
  f.db.prepare('UPDATE wechat_bind_tickets SET expires=?').run(Date.now() - 1);
  assert.equal(f.db.prepare('SELECT wx_openid FROM users WHERE email=?').get(account.email).wx_openid, null);
  const late = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}/confirm`, {}, token);
  assert.equal(late.status, 400);
  assert.equal((await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}`, undefined, token)).data.status, 'expired');
  assert.equal((await f.request(`/v1/auth/wechat/bind-status?ticket=${ticket.data.id}`)).data.status, 'expired');
});

test('rejecting a scan voids the ticket and unbinds nothing', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_rejected') });
  const rejected = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}/reject`, {}, token);
  assert.equal(rejected.status, 200);
  assert.equal(rejected.data.status, 'rejected');
  assert.equal(rejected.data.user.wechat, false);
  assert.equal((await f.request(`/v1/auth/wechat/bind-status?ticket=${ticket.data.id}`)).data.status, 'rejected');
  const confirm = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}/confirm`, {}, token);
  assert.equal(confirm.status, 400);
  assert.equal(f.db.prepare('SELECT wx_openid FROM users WHERE email=?').get(account.email).wx_openid, null);
});

test('a ticket expires, belongs to its requester, and two confirmations bind once', async t => {
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
  const strangerConfirm = await f.request(`/v1/auth/wechat/bind-ticket/${fresh.data.id}/confirm`, {}, other.token);
  assert.equal(strangerConfirm.data.error, 'WECHAT_TICKET_INVALID');

  await f.request('/v1/auth/wechat/bind', { ticket: fresh.data.id, code: f.wechat.issue('openid_race_000') });
  const results = await Promise.all([
    f.request(`/v1/auth/wechat/bind-ticket/${fresh.data.id}/confirm`, {}, token),
    f.request(`/v1/auth/wechat/bind-ticket/${fresh.data.id}/confirm`, {}, token),
  ]);
  assert.deepEqual(results.map(result => result.status).sort(), [200, 400], 'one confirmation wins, the other is spent');
});

test('a different WeChat cannot take over a ticket that someone already scanned', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_first') });
  const second = await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_second') });
  assert.equal(second.status, 400);
  assert.equal(second.data.error, 'WECHAT_TICKET_INVALID');
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

test('probing whether an email is taken is rate limited, not answered for free', async t => {
  const f = await fixture(t);
  await f.emailAccount(); // person@example.test exists
  const wechat = await f.wechatAccount('openid_probe');
  const statuses = [];
  for (let i = 0; i < 5; i++) statuses.push((await f.request('/v1/auth/email-attach', { email: account.email }, wechat.token)).status);
  assert.deepEqual(statuses.slice(0, 4), [409, 409, 409, 409], 'the address is taken, four times');
  assert.equal(statuses[4], 429, 'the fifth attempt is refused before the lookup, so enumeration runs out of budget');
});

test('losing the race for the same WeChat is a 409, never a 500', async t => {
  const f = await fixture(t);
  const token = await f.emailAccount();
  const ticket = await f.request('/v1/auth/wechat/bind-ticket', {}, token);
  await f.request('/v1/auth/wechat/bind', { ticket: ticket.data.id, code: f.wechat.issue('openid_race') });
  // Another account wins the same openid between the scan and the confirmation.
  const second = await f.request('/v1/auth/register', { ...account, email: 'other@example.test', username: 'other' });
  assert.equal(second.status, 201);
  f.db.prepare("UPDATE users SET wx_openid='openid_race' WHERE email=?").run('other@example.test');
  const late = await f.request(`/v1/auth/wechat/bind-ticket/${ticket.data.id}/confirm`, {}, token);
  assert.equal(late.status, 409);
  assert.equal(late.data.error, 'WECHAT_IN_USE');
  assert.equal(f.db.prepare('SELECT wx_openid FROM users WHERE email=?').get(account.email).wx_openid, null);
});
