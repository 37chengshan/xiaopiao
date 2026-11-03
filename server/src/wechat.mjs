/**
 * WeChat, personal-subject edition.
 *
 * A personal WeChat account can run a mini program but cannot use 开放平台 login, so the only identity a server can
 * obtain is the mini program `openid` from `wx.login` + `jscode2session`. Everything here is shaped by that:
 *
 * - `session_key` is a secret the server has no use for (we never decrypt user data), so it is read and dropped.
 *   It is never returned, logged or stored.
 * - An account may exist with only a WeChat identity: no email, no password. `verified` then means "proven owner of
 *   a real login method", and attaching an email proves it with a code, so it stays 1.
 * - Binding from App / Web cannot ask for openid, so it uses a one-time ticket: the App shows a mini program code
 *   (`getUnlimited` with `scene=<ticket>`), the mini program sends the ticket plus its own `jscode2session` code
 *   back, and the App polls the ticket until it turns `bound`.
 * - Two accounts never merge silently. If the email or the WeChat identity already belongs to someone else, the
 *   request is refused and the person is told which side to start from.
 */
import { randomBytes } from 'node:crypto';
import { hash, secret } from './store.mjs';

export const LIMITS = Object.freeze({
  ticketMs: 5 * 60_000,
  /** Ten codes an hour is far more than one person needs and far less than a bot wants. */
  ticketsPerHour: 10,
  /** Access tokens live 7200s upstream; refresh early so a slow request never uses an expired one. */
  tokenMs: 100 * 60_000,
  qrWidth: 280,
  /** A mini program `scene` value: up to 32 url-safe characters, so a 21-char ticket plus a prefix fits. */
  scenePrefix: 'b:',
  timeoutMs: 8_000,
  usernameTries: 5,
});

const fail = (code, field) => { throw Object.assign(new Error(code), { code, ...(field ? { field } : {}) }); };

async function readJson(response, limit = 64_000) {
  const reader = response.body?.getReader();
  if (!reader) throw Object.assign(new Error('WECHAT_UNAVAILABLE'), { code: 'WECHAT_UNAVAILABLE' });
  const chunks = []; let bytes = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > limit) throw Object.assign(new Error('WECHAT_UNAVAILABLE'), { code: 'WECHAT_UNAVAILABLE' });
      chunks.push(value);
    }
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
  try { return JSON.parse(new TextDecoder().decode(Buffer.concat(chunks))); }
  catch { throw Object.assign(new Error('WECHAT_UNAVAILABLE'), { code: 'WECHAT_UNAVAILABLE' }); }
}

/**
 * The three WeChat calls this product makes. Separate from the account logic so tests can drive every failure
 * (invalid code, rate limit, dead token) without a network and without a real AppSecret.
 */
export function createWechatClient({ appId, secret: appSecret, fetcher = fetch, now = Date.now, baseUrl = 'https://api.weixin.qq.com' }) {
  const configured = Boolean(appId && appSecret);
  let token = null;
  const request = (url, init = {}) => fetcher(url, {
    redirect: 'error', signal: AbortSignal.timeout(LIMITS.timeoutMs), ...init,
  });

  /** `code` is single-use and short-lived; the response's `session_key` never leaves this function. */
  async function openidFor(code) {
    if (!configured) fail('WECHAT_UNAVAILABLE');
    const url = new URL(`${baseUrl}/sns/jscode2session`);
    url.searchParams.set('appid', appId);
    url.searchParams.set('secret', appSecret);
    url.searchParams.set('js_code', code);
    url.searchParams.set('grant_type', 'authorization_code');
    let body;
    try { body = await readJson(await request(url)); }
    catch { fail('WECHAT_UNAVAILABLE'); }
    if (body.errcode) {
      // 40029 / 40163: bad or already used code. 45011: too many attempts from that code's owner.
      if ([40029, 40163].includes(body.errcode)) fail('WECHAT_CODE_INVALID');
      fail('WECHAT_UNAVAILABLE');
    }
    if (typeof body.openid !== 'string' || !/^[A-Za-z0-9_-]{10,64}$/.test(body.openid)) fail('WECHAT_UNAVAILABLE');
    return body.openid;
  }

  async function accessToken() {
    if (!configured) fail('WECHAT_UNAVAILABLE');
    if (token && token.until > now()) return token.value;
    const url = new URL(`${baseUrl}/cgi-bin/token`);
    url.searchParams.set('grant_type', 'client_credential');
    url.searchParams.set('appid', appId);
    url.searchParams.set('secret', appSecret);
    let body;
    try { body = await readJson(await request(url)); }
    catch { fail('WECHAT_UNAVAILABLE'); }
    if (typeof body.access_token !== 'string' || !body.access_token) fail('WECHAT_UNAVAILABLE');
    token = { value: body.access_token, until: now() + Math.min(LIMITS.tokenMs, Math.max(60_000, Number(body.expires_in ?? 7200) * 1000 - 60_000)) };
    return token.value;
  }

  /** The mini program code the App shows; `scene` carries the ticket, `page` is the mini program route. */
  async function bindCode({ scene, page }) {
    const uri = new URL(`${baseUrl}/wxa/getwxacodeunlimit`);
    uri.searchParams.set('access_token', await accessToken());
    let response;
    try {
      response = await request(uri, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scene, page, check_path: false, env_version: 'release', width: LIMITS.qrWidth }),
      });
    } catch { fail('WECHAT_UNAVAILABLE'); }
    if (!response.ok) fail('WECHAT_UNAVAILABLE');
    const type = response.headers.get('content-type') ?? '';
    if (type.includes('application/json')) {
      // An error body is JSON; a successful body is the PNG itself. Never forward the error text.
      await readJson(response).catch(() => null);
      token = null;
      fail('WECHAT_UNAVAILABLE');
    }
    const bytes = Buffer.from(await response.arrayBuffer());
    if (!bytes.length || bytes.length > 512_000) fail('WECHAT_UNAVAILABLE');
    return bytes;
  }

  return { openidFor, bindCode, accessToken, configured };
}

/** `b:<ticket>` — a scene value the mini program can split back into its ticket. */
export const sceneFor = ticket => `${LIMITS.scenePrefix}${ticket}`;
export const ticketFromScene = scene => (typeof scene === 'string' && scene.startsWith(LIMITS.scenePrefix)
  ? scene.slice(LIMITS.scenePrefix.length) : '');

export function createWechatAccounts({ db, client, now = Date.now }) {
  const findOpenid = db.prepare('SELECT * FROM users WHERE wx_openid=?');
  const assignOpenid = db.prepare('UPDATE users SET wx_openid=? WHERE id=? AND wx_openid IS NULL');
  const clearOpenid = db.prepare('UPDATE users SET wx_openid=NULL WHERE id=?');
  const insertUser = db.prepare('INSERT INTO users (id,email,username,password,verified,created,wx_openid) VALUES (?,?,?,?,?,?,?)');
  const usernameTaken = db.prepare('SELECT 1 FROM users WHERE username=?');
  const ticketInsert = db.prepare('INSERT INTO wechat_bind_tickets (digest,user_id,status,openid,created_at,expires) VALUES (?,?,?,?,?,?)');
  const ticketRead = db.prepare('SELECT * FROM wechat_bind_tickets WHERE digest=?');
  const ticketClaim = db.prepare("UPDATE wechat_bind_tickets SET status='bound', openid=? WHERE digest=? AND status='pending' AND expires>?");
  const ticketsForUser = db.prepare('DELETE FROM wechat_bind_tickets WHERE user_id=?');

  /** An 8-character suffix plus a fixed prefix: short, unique in practice, retried on the rare collision. */
  function freeUsername() {
    for (let attempt = 0; attempt < LIMITS.usernameTries; attempt++) {
      const name = `wx_${randomBytes(5).toString('hex')}`;
      if (!usernameTaken.get(name)) return name;
    }
    return `wx_${randomBytes(8).toString('hex')}`;
  }

  /** Sign in by openid, creating the account on first sight. Nothing but the openid is taken from WeChat. */
  function login(openid) {
    const existing = findOpenid.get(openid);
    if (existing) return { user: existing, created: false };
    const user = { id: randomBytes(16).toString('hex'), username: freeUsername() };
    // `verified` is 1: holding an openid is proof of a real login method, and there is no email to verify yet.
    insertUser.run(user.id, null, user.username, null, 1, now(), openid);
    return { user: findOpenid.get(openid), created: true };
  }

  /** One ticket, one QR code, one App polling loop. The ticket itself is the secret; only its digest is stored. */
  async function issueTicket(userId) {
    const ticket = secret();
    const image = await client.bindCode({ scene: sceneFor(ticket), page: 'pages/bind/bind' });
    const expires = now() + LIMITS.ticketMs;
    ticketInsert.run(hash(ticket), userId, 'pending', null, now(), expires);
    return { ticket, expires, image };
  }

  /** Polling is scoped to the account that asked for the ticket, so a leaked ticket reveals nothing to a stranger. */
  function status(userId, ticket) {
    if (typeof ticket !== 'string' || !/^[A-Za-z0-9_-]{20,64}$/.test(ticket)) fail('WECHAT_TICKET_INVALID');
    const row = ticketRead.get(hash(ticket));
    if (!row || row.user_id !== userId) fail('WECHAT_TICKET_INVALID');
    if (row.status === 'bound') return { status: 'bound' };
    if (row.expires <= now()) return { status: 'expired' };
    return { status: 'pending', expiresAt: row.expires };
  }

  /**
   * The mini program's half: ticket + its own `wx.login` code. Runs in one transaction so a replayed ticket or two
   * simultaneous scans can only ever bind once.
   */
  async function bind({ ticket, code }) {
    if (typeof ticket !== 'string' || !/^[A-Za-z0-9_-]{20,64}$/.test(ticket)) fail('WECHAT_TICKET_INVALID');
    const digest = hash(ticket);
    const row = ticketRead.get(digest);
    if (!row) fail('WECHAT_TICKET_INVALID');
    if (row.status === 'bound') fail('WECHAT_TICKET_INVALID');
    if (row.expires <= now()) fail('WECHAT_TICKET_INVALID');
    const user = db.prepare('SELECT * FROM users WHERE id=?').get(row.user_id);
    if (!user) fail('WECHAT_TICKET_INVALID');
    const openid = await client.openidFor(code);
    if (user.wx_openid && user.wx_openid !== openid) fail('WECHAT_ALREADY_BOUND');
    const owner = findOpenid.get(openid);
    if (owner && owner.id !== user.id) fail('WECHAT_IN_USE');
    db.exec('BEGIN IMMEDIATE');
    try {
      if (!user.wx_openid) {
        const assigned = assignOpenid.run(openid, user.id);
        if (assigned.changes !== 1) throw Object.assign(new Error('WECHAT_ALREADY_BOUND'), { code: 'WECHAT_ALREADY_BOUND' });
      }
      const claimed = ticketClaim.run(openid, digest, now());
      if (claimed.changes !== 1) throw Object.assign(new Error('WECHAT_TICKET_INVALID'), { code: 'WECHAT_TICKET_INVALID' });
      db.exec('COMMIT');
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
    return { user: db.prepare('SELECT * FROM users WHERE id=?').get(user.id) };
  }

  /** Refuses to remove the only way into the account: without WeChat there must still be an email and a password. */
  function unbind(userRow) {
    if (!userRow.wx_openid) fail('WECHAT_NOT_BOUND');
    if (!userRow.email || !userRow.password) fail('WECHAT_LAST_LOGIN');
    clearOpenid.run(userRow.id);
    ticketsForUser.run(userRow.id);
    return { user: db.prepare('SELECT * FROM users WHERE id=?').get(userRow.id) };
  }

  return { login, issueTicket, status, bind, unbind, openidFor: client.openidFor };
}
