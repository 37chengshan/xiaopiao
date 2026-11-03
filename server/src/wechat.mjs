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
 *   (`getUnlimited` with `scene=<prefix><ticket>`), the mini program sends the ticket plus its own `jscode2session`
 *   code back, and the App — which is signed in — confirms or rejects. **Nothing is written to the account until the
 *   App confirms**, because the person holding the phone must be the one who decides to attach a WeChat identity.
 * - Two accounts never merge silently. If the email or the WeChat identity already belongs to someone else, the
 *   request is refused and the person is told which side to start from.
 *
 * The ticket is short on purpose: a mini program `scene` is capped at 32 characters by WeChat, so a 43-character
 * base64 id (the shape of an API secret) would make `getwxacodeunlimit` fail for every real deployment.
 */
import { randomBytes, randomUUID } from 'node:crypto';
import { hash } from './store.mjs';
import { readBounded } from './bounded.mjs';

export const LIMITS = Object.freeze({
  ticketMs: 5 * 60_000,
  /** 15 random bytes → 20 url-safe characters; with the prefix that is 22, comfortably under WeChat's 32. */
  ticketBytes: 15,
  ticketLength: 20,
  /** WeChat's own limit for `scene` in `getwxacodeunlimit`. */
  maxScene: 32,
  scenePrefix: 'b:',
  /** Ten codes an hour is far more than one person needs and far less than a bot wants. */
  ticketsPerHour: 10,
  /** Access tokens live 7200 s upstream; refresh early so a slow request never uses an expired one. */
  tokenMs: 100 * 60_000,
  qrWidth: 280,
  timeoutMs: 8_000,
  usernameTries: 5,
  /** A nickname the scanning side reports about itself: unbounded text would be a way to impersonate someone. */
  nickname: 32,
});

const fail = (code, field) => { throw Object.assign(new Error(code), { code, ...(field ? { field } : {}) }); };
const unavailable = () => fail('WECHAT_UNAVAILABLE');
const TICKET = new RegExp(`^[A-Za-z0-9_-]{${LIMITS.ticketLength}}$`);
const isTicket = value => typeof value === 'string' && TICKET.test(value);

/** The three WeChat calls this product makes, separate so tests can drive every failure without a network. */
export function createWechatClient({ appId, secret: appSecret, fetcher = fetch, now = Date.now, baseUrl = 'https://api.weixin.qq.com' }) {
  const configured = Boolean(appId && appSecret);
  let token = null;
  const request = (url, init = {}) => fetcher(url, {
    redirect: 'error', signal: AbortSignal.timeout(LIMITS.timeoutMs), ...init,
  });
  async function json(response) {
    try { return JSON.parse((await readBounded(response, 64_000, { code: 'WECHAT_UNAVAILABLE' })).toString('utf8')); }
    catch { return unavailable(); }
  }

  /** `code` is single-use and short-lived; the response's `session_key` never leaves this function. */
  async function openidFor(code) {
    if (!configured) unavailable();
    const url = new URL(`${baseUrl}/sns/jscode2session`);
    url.searchParams.set('appid', appId);
    url.searchParams.set('secret', appSecret);
    url.searchParams.set('js_code', code);
    url.searchParams.set('grant_type', 'authorization_code');
    let body;
    try { body = await json(await request(url)); } catch { unavailable(); }
    if (body.errcode) {
      // 40029 / 40163: bad or already used code. 45011: too many attempts from that code's owner.
      if ([40029, 40163].includes(body.errcode)) fail('WECHAT_CODE_INVALID');
      unavailable();
    }
    if (typeof body.openid !== 'string' || !/^[A-Za-z0-9_-]{10,64}$/.test(body.openid)) unavailable();
    return body.openid;
  }

  async function accessToken() {
    if (!configured) unavailable();
    if (token && token.until > now()) return token.value;
    const url = new URL(`${baseUrl}/cgi-bin/token`);
    url.searchParams.set('grant_type', 'client_credential');
    url.searchParams.set('appid', appId);
    url.searchParams.set('secret', appSecret);
    let body;
    try { body = await json(await request(url)); } catch { unavailable(); }
    if (typeof body.access_token !== 'string' || !body.access_token) unavailable();
    token = { value: body.access_token, until: now() + Math.min(LIMITS.tokenMs, Math.max(60_000, Number(body.expires_in ?? 7200) * 1000 - 60_000)) };
    return token.value;
  }

  /** The mini program code the App shows; `scene` carries the ticket, `page` is the mini program route. */
  async function bindCode({ scene, page }) {
    if (typeof scene !== 'string' || !scene || scene.length > LIMITS.maxScene) {
      // WeChat would reject this, and a silent 503 for every user is worse than a loud failure at the source.
      throw new Error(`SCENE_TOO_LONG:${scene?.length ?? 0}`);
    }
    const uri = new URL(`${baseUrl}/wxa/getwxacodeunlimit`);
    uri.searchParams.set('access_token', await accessToken());
    let response;
    try {
      response = await request(uri, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scene, page, check_path: false, env_version: 'release', width: LIMITS.qrWidth }),
      });
    } catch { unavailable(); }
    if (!response.ok) { await response.body?.cancel().catch(() => {}); unavailable(); }
    const type = response.headers.get('content-type') ?? '';
    if (type.includes('application/json')) {
      // An error body is JSON; a successful body is the PNG itself. Never forward the error text.
      await json(response).catch(() => null);
      token = null;
      unavailable();
    }
    let bytes;
    try { bytes = await readBounded(response, 512_000, { code: 'WECHAT_UNAVAILABLE' }); } catch { unavailable(); }
    if (!bytes.length) unavailable();
    return bytes;
  }

  return { openidFor, bindCode, accessToken, configured };
}

/** `b:<ticket>` — a scene value the mini program can split back into its ticket, always inside WeChat's 32. */
export const sceneFor = ticket => `${LIMITS.scenePrefix}${ticket}`;
export const ticketFromScene = scene => (typeof scene === 'string' && scene.startsWith(LIMITS.scenePrefix)
  ? scene.slice(LIMITS.scenePrefix.length) : '');

export function createWechatAccounts({ db, client, now = Date.now }) {
  const findOpenid = db.prepare('SELECT * FROM users WHERE wx_openid=?');
  const assignOpenid = db.prepare('UPDATE users SET wx_openid=? WHERE id=? AND wx_openid IS NULL');
  const clearOpenid = db.prepare('UPDATE users SET wx_openid=NULL WHERE id=?');
  const insertUser = db.prepare('INSERT INTO users (id,email,username,password,verified,created,wx_openid) VALUES (?,?,?,?,?,?,?)');
  const usernameTaken = db.prepare('SELECT 1 FROM users WHERE username=?');
  const byId = db.prepare('SELECT * FROM users WHERE id=?');
  const ticketInsert = db.prepare(`INSERT INTO wechat_bind_tickets (digest,user_id,status,openid,nickname,created_at,expires)
    VALUES (?,?,?,?,?,?,?)`);
  const ticketRead = db.prepare('SELECT * FROM wechat_bind_tickets WHERE digest=?');
  const ticketClaim = db.prepare("UPDATE wechat_bind_tickets SET status='scanned', openid=?, nickname=? WHERE digest=? AND status='pending' AND expires>?");
  const ticketBind = db.prepare("UPDATE wechat_bind_tickets SET status='bound' WHERE digest=? AND status='scanned' AND expires>?");
  const ticketReject = db.prepare("UPDATE wechat_bind_tickets SET status='rejected', openid=NULL WHERE digest=? AND status IN ('pending','scanned')");
  const deleteTicketsForUser = db.prepare('DELETE FROM wechat_bind_tickets WHERE user_id=?');

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
    // `verified` is 1: holding an openid is proof of a real login method, and there is no email to verify yet.
    insertUser.run(randomUUID(), null, freeUsername(), null, 1, now(), openid);
    return { user: findOpenid.get(openid), created: true };
  }

  /** One ticket, one QR code, one App polling loop. The ticket itself is the secret; only its digest is stored. */
  async function issueTicket(userId) {
    const ticket = randomBytes(LIMITS.ticketBytes).toString('base64url');
    if (ticket.length !== LIMITS.ticketLength || sceneFor(ticket).length > LIMITS.maxScene) throw new Error('TICKET_LENGTH');
    const image = await client.bindCode({ scene: sceneFor(ticket), page: 'pages/bind/bind' });
    const expires = now() + LIMITS.ticketMs;
    ticketInsert.run(hash(ticket), userId, 'pending', null, null, now(), expires);
    return { ticket, expires, image };
  }

  /** The ticket row for its owner, with the expiry turned into a state the clients can show. */
  function ticketFor(userId, ticket) {
    if (!isTicket(ticket)) fail('WECHAT_TICKET_INVALID');
    const row = ticketRead.get(hash(ticket));
    // Another account's ticket is indistinguishable from one that never existed.
    if (!row || row.user_id !== userId) fail('WECHAT_TICKET_INVALID');
    if (row.status !== 'bound' && row.status !== 'rejected' && row.expires <= now()) return { row, status: 'expired' };
    return { row, status: row.status };
  }

  /**
   * App side: what the person sees before deciding. The nickname is **reported by the scanning side**, so it is
   * shown as plain text only ("this phone says it is 阿票") and never written to the account — the confirmation is
   * what makes the binding real. No avatar is accepted at all: an arbitrary URL would leak the App user's IP to a
   * third party the moment it is rendered.
   */
  function status(userId, ticket) {
    const { row, status: state } = ticketFor(userId, ticket);
    if (state === 'scanned') {
      return { status: 'scanned', wechat: row.nickname ? { nickname: row.nickname } : {} };
    }
    if (state === 'pending') return { status: 'pending', expiresAt: row.expires };
    return { status: state };
  }

  /** Mini program side: the status only, no account data and no session. */
  function scanStatus(ticket) {
    if (!isTicket(ticket)) fail('WECHAT_TICKET_INVALID');
    const row = ticketRead.get(hash(ticket));
    if (!row) fail('WECHAT_TICKET_INVALID');
    if (row.status !== 'bound' && row.status !== 'rejected' && row.expires <= now()) return { status: 'expired' };
    return { status: row.status };
  }

  /**
   * Mini program half: its own `wx.login` code plus the scanned ticket. This only *claims* the ticket — the openid
   * is remembered with the nickname/avatar so the App can ask "attach this WeChat?" — nothing is bound yet.
   */
  async function claim({ ticket, code, nickname }) {
    if (!isTicket(ticket)) fail('WECHAT_TICKET_INVALID');
    const digest = hash(ticket);
    const row = ticketRead.get(digest);
    if (!row || row.status === 'bound' || row.status === 'rejected') fail('WECHAT_TICKET_INVALID');
    if (row.expires <= now()) fail('WECHAT_TICKET_INVALID');
    const openid = await client.openidFor(code);
    if (row.status === 'scanned') {
      // The same person tapping again is harmless; a different WeChat must not take over a claimed ticket.
      if (row.openid !== openid) fail('WECHAT_TICKET_INVALID');
      return { status: 'scanned' };
    }
    const owner = findOpenid.get(openid);
    if (owner) fail('WECHAT_IN_USE');
    const claimed = ticketClaim.run(openid, sanitizeNickname(nickname), digest, now());
    if (claimed.changes !== 1) fail('WECHAT_TICKET_INVALID');
    return { status: 'scanned' };
  }

  /**
   * App half: the signed-in person confirms. Everything that must hold at the moment of writing is checked inside
   * one transaction, so two taps or two phones can only bind once.
   */
  function confirm(userId, ticket) {
    const { row, status: state } = ticketFor(userId, ticket);
    if (state !== 'scanned' || !row.openid) fail('WECHAT_TICKET_INVALID');
    const user = byId.get(userId);
    if (!user) fail('WECHAT_TICKET_INVALID');
    if (user.wx_openid && user.wx_openid !== row.openid) fail('WECHAT_ALREADY_BOUND');
    const owner = findOpenid.get(row.openid);
    if (owner && owner.id !== userId) fail('WECHAT_IN_USE');
    db.exec('BEGIN IMMEDIATE');
    try {
      if (!user.wx_openid) {
        try {
          const assigned = assignOpenid.run(row.openid, userId);
          if (assigned.changes !== 1) throw Object.assign(new Error('WECHAT_IN_USE'), { code: 'WECHAT_IN_USE' });
        } catch (error) {
          // The unique index is the last word: another request bound this openid between our check and our write.
          if (/UNIQUE|constraint/i.test(String(error?.message))) throw Object.assign(new Error('WECHAT_IN_USE'), { code: 'WECHAT_IN_USE' });
          throw error;
        }
      }
      const bound = ticketBind.run(hash(ticket), now());
      if (bound.changes !== 1) throw Object.assign(new Error('WECHAT_TICKET_INVALID'), { code: 'WECHAT_TICKET_INVALID' });
      db.exec('COMMIT');
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
    return { user: byId.get(userId) };
  }

  /** Refusing voids the ticket: the mini program learns it is over instead of waiting for a scan that will not bind. */
  function reject(userId, ticket) {
    const { status: state } = ticketFor(userId, ticket);
    if (state !== 'pending' && state !== 'scanned') fail('WECHAT_TICKET_INVALID');
    ticketReject.run(hash(ticket));
    return { status: 'rejected' };
  }

  /** Refuses to remove the only way into the account: without WeChat there must still be an email and a password. */
  function unbind(userRow) {
    if (!userRow.wx_openid) fail('WECHAT_NOT_BOUND');
    if (!userRow.email || !userRow.password) fail('WECHAT_LAST_LOGIN');
    clearOpenid.run(userRow.id);
    deleteTicketsForUser.run(userRow.id);
    return { user: byId.get(userRow.id) };
  }

  return { login, issueTicket, status, scanStatus, claim, confirm, reject, unbind };
}

/**
 * The mini program may send a nickname it obtained itself. It is untrusted: control characters and zero-width /
 * bidi marks are removed (they are used to forge a display name that reads as someone else), surrounding space is
 * trimmed, and anything that is not 1–32 visible characters is dropped entirely rather than truncated.
 */
export function sanitizeNickname(value) {
  if (typeof value !== 'string') return null;
  const clean = value
    .replace(/[\u0000-\u001f\u007f-\u009f]/g, '')
    .replace(/[\u200b-\u200f\u202a-\u202e\u2060-\u2064\ufeff]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return clean.length && clean.length <= LIMITS.nickname ? clean : null;
}
