import { createServer } from 'node:http';
import { isIP } from 'node:net';
import { randomInt, randomUUID } from 'node:crypto';
import { hash, secret, passwordHash, passwordMatches, publicUser } from './store.mjs';
import { createLedger, LIMITS as LEDGER } from './ledger.mjs';
const errors = {
  INVALID_INPUT: [400, '请检查输入内容'], INVALID_CREDENTIALS: [401, '账号或密码不正确'],
  UNAUTHORIZED: [401, '请重新登录'], EMAIL_UNVERIFIED: [403, '请先验证邮箱'],
  ACCOUNT_EXISTS: [409, '该邮箱或用户名暂不可用'], RATE_LIMITED: [429, '操作太频繁，请稍后再试'],
  MAIL_UNAVAILABLE: [503, '邮件服务暂不可用，请稍后再试'], CODE_INVALID: [400, '验证码错误或已过期'],
  RUN_INVALID: [409, '这次对话已过期，请重新发送'], MODEL_UNAVAILABLE: [503, 'AI 暂时不可用，可继续使用本机记账'],
  LEDGER_TOO_MANY: [400, '一次最多同步 500 条'], LEDGER_PAYLOAD_TOO_LARGE: [413, '这次提交的数据太大，请分批同步'],
};
function fail(code) { throw Object.assign(new Error(code), { code }); }
function text(value, max = 256) { if (typeof value !== 'string' || !value.trim() || value.length > max) fail('INVALID_INPUT'); return value.trim(); }
function emailOf(value) { const email = text(value, 254).toLowerCase(); if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fail('INVALID_INPUT'); return email; }
function passwordOf(value) { if (typeof value !== 'string' || value.length < 15 || value.length > 128) fail('INVALID_INPUT'); return value; }
function numberParam(url, name, fallback) {
  const raw = url.searchParams.get(name);
  if (raw === null || raw === '') return fallback;
  if (!/^\d{1,16}$/.test(raw)) fail('INVALID_INPUT');
  return Number(raw);
}
function cookieToken(req) { return /(?:^|;\s*)xp_session=([A-Za-z0-9_-]+)/.exec(req.headers.cookie ?? '')?.[1]; }
const PAYLOAD = { default: 96_000, ledger: 512_000 };
async function bodyOf(req, limit = PAYLOAD.default, tooLarge = 'INVALID_INPUT') {
  if (!req.headers['content-type']?.startsWith('application/json')) fail('INVALID_INPUT');
  let raw = ''; for await (const chunk of req) { raw += chunk; if (Buffer.byteLength(raw) > limit) fail(tooLarge); }
  try { const value = JSON.parse(raw); if (!value || typeof value !== 'object' || Array.isArray(value)) fail('INVALID_INPUT'); return value; } catch { fail('INVALID_INPUT'); }
}
/** Dev previews move between loopback ports; production stays pinned to one exact HTTPS origin. */
const loopbackOrigin = /^http:\/\/(?:127\.0\.0\.1|localhost|\[::1\])(?::\d{1,5})?$/;
export function createApp({ db, agent, sendMail, origin = 'http://localhost:5173', production = false, trustedProxy = false }) {
  const dummyPassword = passwordHash(secret());
  const ledger = createLedger({ db });
  const ledgerOf = req => {
    const user = userOf(req);
    if (!user.verified) fail('EMAIL_UNVERIFIED');
    return user;
  };
  const count = (value, max) => {
    if (value === undefined) return undefined;
    if (!Number.isSafeInteger(value) || value < 0 || value > max) fail('INVALID_INPUT');
    return value;
  };
  const active = new Set();
  let authWork = 0;
  const locks = new Set();
  const cookie = (token, expires = 30 * 86400) => `xp_session=${token}; HttpOnly; Path=/v1; SameSite=Strict; Max-Age=${expires}${production ? '; Secure' : ''}`;
  const cleanup = setInterval(() => {
    for (const table of ['sessions', 'codes', 'runs', 'limits']) db.prepare(`DELETE FROM ${table} WHERE expires < ?`).run(Date.now());
  }, 60_000).unref();
  function rate(key, max, window = 900_000) {
    const now = Date.now();
    db.prepare('INSERT INTO limits(key,count,expires) VALUES (?,1,?) ON CONFLICT(key) DO UPDATE SET count=CASE WHEN expires < ? THEN 1 ELSE count+1 END, expires=CASE WHEN expires < ? THEN excluded.expires ELSE expires END').run(key, now + window, now, now);
    if (db.prepare('SELECT count FROM limits WHERE key=?').get(key).count > max) fail('RATE_LIMITED');
  }
  function userOf(req) {
    const token = req.headers.authorization?.match(/^Bearer ([A-Za-z0-9_-]{43})$/)?.[1] ?? cookieToken(req);
    if (!token) fail('UNAUTHORIZED');
    const user = db.prepare('SELECT u.*, s.digest AS session_digest FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.digest=? AND s.expires>?').get(hash(token), Date.now());
    if (!user) fail('UNAUTHORIZED');
    return user;
  }
  function session(res, user, native) {
    const token = secret();
    db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(hash(token), user.id, Date.now() + 30 * 86400_000);
    res.setHeader('Set-Cookie', cookie(token));
    return { user: publicUser(user), ...(native ? { token } : {}) };
  }
  async function issueCode(user, purpose) {
    if (!sendMail) fail('MAIL_UNAVAILABLE');
    rate(`mail:${user.id}`, 4, 3600_000);
    const code = String(randomInt(100000, 1000000));
    db.prepare('INSERT INTO codes VALUES (?,?,?,?,0) ON CONFLICT(user_id,purpose) DO UPDATE SET digest=excluded.digest, expires=excluded.expires, attempts=0').run(user.id, purpose, hash(`${user.id}:${purpose}:${code}`), Date.now() + 600_000);
    try { await sendMail({ to: user.email, purpose, code }); }
    catch { db.prepare('DELETE FROM codes WHERE user_id=? AND purpose=?').run(user.id, purpose); fail('MAIL_UNAVAILABLE'); }
  }
  function consumeCode(user, purpose, value) {
    const code = text(value, 6);
    const row = db.prepare('SELECT * FROM codes WHERE user_id=? AND purpose=?').get(user.id, purpose);
    if (!row || row.expires <= Date.now() || row.attempts >= 5) fail('CODE_INVALID');
    db.prepare('UPDATE codes SET attempts=attempts+1 WHERE user_id=? AND purpose=?').run(user.id, purpose);
    if (hash(`${user.id}:${purpose}:${code}`) !== row.digest) fail('CODE_INVALID');
    db.prepare('DELETE FROM codes WHERE user_id=? AND purpose=?').run(user.id, purpose);
  }
  const server = createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    const requestOrigin = req.headers.origin;
    // A loopback preview port is accepted in dev only; production keeps one exact origin.
    const originAllowed = requestOrigin !== undefined && requestOrigin !== '' && (requestOrigin === origin || (!production && loopbackOrigin.test(requestOrigin)));
    if (requestOrigin && !originAllowed) { res.writeHead(403); return res.end(); }
    if (originAllowed) {
      res.setHeader('Access-Control-Allow-Origin', requestOrigin); res.setHeader('Vary', 'Origin');
      res.setHeader('Access-Control-Allow-Credentials', 'true');
      res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
      res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    }
    if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
    const url = new URL(req.url, 'http://localhost');
    const json = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(value)); };
    try {
      if (url.pathname === '/healthz' && req.method === 'GET') return json({ ok: true });
      if (!url.pathname.startsWith('/v1/')) return json({ error: 'NOT_FOUND' }, 404);
      // Native clients have no Origin. Browser requests use a Strict HttpOnly cookie and exact origin.
      if (!requestOrigin && req.headers['sec-fetch-site'] && req.headers['sec-fetch-site'] !== 'same-origin') fail('UNAUTHORIZED');
      // Only the private deployment gateway may set this; never trust arbitrary X-Forwarded-For.
      const forwarded = req.headers['x-xiaopiao-client-ip'];
      const ip = trustedProxy && typeof forwarded === 'string' && isIP(forwarded) ? forwarded : req.socket.remoteAddress;
      rate(`global:${ip}`, 300, 60_000);
      if (url.pathname === '/v1/auth/me' && req.method === 'GET') return json({ user: publicUser(userOf(req)) });
      if (url.pathname === '/v1/ledger/changes' && req.method === 'GET') {
        const user = ledgerOf(req);
        rate(`ledger:${user.id}`, 900, 3600_000);
        const since = count(numberParam(url, 'since', 0), Number.MAX_SAFE_INTEGER);
        const limit = count(numberParam(url, 'limit', LEDGER.defaultPage), LEDGER.page) ?? LEDGER.defaultPage;
        if (limit < 1) fail('INVALID_INPUT');
        return json(ledger.changes(user.id, since, limit));
      }
      if (req.method !== 'POST') return json({ error: 'NOT_FOUND' }, 404);
      const ledgerPush = url.pathname === '/v1/ledger/push';
      const body = await bodyOf(req, ledgerPush ? PAYLOAD.ledger : PAYLOAD.default, ledgerPush ? 'LEDGER_PAYLOAD_TOO_LARGE' : 'INVALID_INPUT');
      const native = body.client === 'native' && !requestOrigin && !req.headers['sec-fetch-site'];
      if (ledgerPush) {
        const user = ledgerOf(req);
        rate(`ledger-push:${user.id}`, 300, 3600_000);
        const deviceId = typeof body.deviceId === 'string' ? body.deviceId.trim() : '';
        if (!deviceId || deviceId.length > LEDGER.deviceId) fail('INVALID_INPUT');
        const txns = body.txns ?? [];
        const categories = body.categories ?? [];
        if (!Array.isArray(txns) || !Array.isArray(categories)) fail('INVALID_INPUT');
        if (txns.length + categories.length > LEDGER.items) fail('LEDGER_TOO_MANY');
        return json(ledger.push(user.id, deviceId, txns, categories));
      }
      if (url.pathname.startsWith('/v1/auth/')) {
        rate(`auth:${ip}`, 30);
        if (authWork >= 3) fail('RATE_LIMITED');
        authWork++;
        try { switch (url.pathname) {
          case '/v1/auth/register': {
            const email = emailOf(body.email), username = text(body.username, 30).toLowerCase(), password = passwordOf(body.password);
            if (!/^[a-z][a-z0-9_]{2,29}$/.test(username)) fail('INVALID_INPUT');
            if (!sendMail) fail('MAIL_UNAVAILABLE');
            if (db.prepare('SELECT id FROM users WHERE email=? OR username=?').get(email, username)) fail('ACCOUNT_EXISTS');
            const encoded = await passwordHash(password);
            const user = { id: randomUUID(), email, username, verified: 0 };
            try { db.prepare('INSERT INTO users VALUES (?,?,?,?,0,?)').run(user.id, email, username, encoded, Date.now()); }
            catch { fail('ACCOUNT_EXISTS'); }
            try { await issueCode(user, 'verify'); }
            catch (error) { db.prepare('DELETE FROM users WHERE id=?').run(user.id); throw error; }
            return json(session(res, user, native), 201);
          }
          case '/v1/auth/login': {
            const identifier = text(body.identifier, 254).toLowerCase();
            rate(`login:${hash(identifier)}`, 10);
            const password = passwordOf(body.password);
            const user = db.prepare('SELECT * FROM users WHERE email=? OR username=?').get(identifier, identifier);
            const matches = await passwordMatches(password, user?.password ?? await dummyPassword);
            if (!user || !matches) fail('INVALID_CREDENTIALS');
            return json(session(res, user, native));
          }
          case '/v1/auth/logout': {
            const user = userOf(req); db.prepare('DELETE FROM sessions WHERE digest=?').run(user.session_digest);
            res.setHeader('Set-Cookie', cookie('', 0)); return json({ ok: true });
          }
          case '/v1/auth/verify': {
            const user = userOf(req); consumeCode(user, 'verify', body.code);
            db.prepare('UPDATE users SET verified=1 WHERE id=?').run(user.id);
            return json({ user: publicUser({ ...user, verified: 1 }) });
          }
          case '/v1/auth/resend': { const user = userOf(req); if (!user.verified) await issueCode(user, 'verify'); return json({ ok: true }); }
          case '/v1/auth/forgot': {
            const email = emailOf(body.email); rate(`forgot:${hash(email)}`, 4, 3600_000);
            if (!sendMail) fail('MAIL_UNAVAILABLE');
            const user = db.prepare('SELECT * FROM users WHERE email=?').get(email);
            if (user) await issueCode(user, 'reset');
            return json({ ok: true, message: '如果邮箱已注册，我们会发送验证码' });
          }
          case '/v1/auth/reset': {
            const email = emailOf(body.email), password = passwordOf(body.password);
            const user = db.prepare('SELECT * FROM users WHERE email=?').get(email);
            if (!user) fail('CODE_INVALID');
            const encoded = await passwordHash(password);
            // No await between consume and update: one-use even under concurrent reset requests.
            consumeCode(user, 'reset', body.code);
            db.prepare('UPDATE users SET password=? WHERE id=?').run(encoded, user.id);
            db.prepare('DELETE FROM sessions WHERE user_id=?').run(user.id);
            return json({ ok: true });
          }
          default: return json({ error: 'NOT_FOUND' }, 404);
        }
        } finally { authWork--; }
      }
      if (url.pathname !== '/v1/chat') return json({ error: 'NOT_FOUND' }, 404);
      const user = userOf(req);
      if (!user.verified) fail('EMAIL_UNVERIFIED');
      rate(`chat:${user.id}`, 40, 3600_000);
      if (active.has(user.id)) fail('RATE_LIMITED');
      let state;
      const runId = body.runId ? text(body.runId, 64) : randomUUID();
      if (body.runId) {
        if (locks.has(runId)) fail('RUN_INVALID');
        const row = db.prepare('SELECT * FROM runs WHERE id=? AND user_id=? AND expires>?').get(runId, user.id, Date.now());
        if (!row) fail('RUN_INVALID');
        state = JSON.parse(row.state);
        if (!state.pending?.length || !Array.isArray(body.results) || body.results.length !== state.pending.length) fail('RUN_INVALID');
        for (const call of state.pending) {
          const result = body.results.find(result => result.id === call.id);
          if (!result || result.data === undefined || JSON.stringify(result.data).length > 16_000) fail('INVALID_INPUT');
          state.messages.push({ role: 'tool', tool_call_id: call.id, content: JSON.stringify(result.data) });
        }
        db.prepare('DELETE FROM runs WHERE id=?').run(runId);
      } else {
        if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 24) fail('INVALID_INPUT');
        const messages = body.messages.map(message => {
          if (!['user', 'assistant'].includes(message.role)) fail('INVALID_INPUT');
          return { role: message.role, content: text(message.content, 6000) };
        });
        if (messages.at(-1).role !== 'user') fail('INVALID_INPUT');
        let timezone = text(body.timezone ?? 'Asia/Shanghai', 64);
        try { new Intl.DateTimeFormat('en', { timeZone: timezone }); } catch { fail('INVALID_INPUT'); }
        state = { messages, timezone, pending: [], round: 0 };
      }
      active.add(user.id); locks.add(runId);
      const controller = new AbortController();
      const abort = () => controller.abort();
      res.once('close', abort);
      res.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
      const emit = event => { if (!res.destroyed && !controller.signal.aborted) res.write(`data: ${JSON.stringify(event)}\n\n`); };
      const heartbeat = setInterval(() => { if (!res.destroyed) res.write(': heartbeat\n\n'); }, 10_000);
      try {
        state = await agent(state, emit, controller.signal);
        if (!controller.signal.aborted && state.pending.length) {
          db.prepare('INSERT INTO runs VALUES (?,?,?,?)').run(runId, user.id, JSON.stringify(state), Date.now() + 120_000);
          emit({ type: 'tool_calls', runId, calls: state.pending });
        } else emit({ type: 'done' });
      } catch {
        emit({ type: 'error', code: 'MODEL_UNAVAILABLE', message: errors.MODEL_UNAVAILABLE[1] });
      } finally {
        clearInterval(heartbeat); active.delete(user.id); locks.delete(runId);
        res.off('close', abort); res.end();
      }
    } catch (error) {
      const code = errors[error.code] ? error.code : 'INTERNAL_ERROR';
      const [status, message] = errors[code] ?? [500, '服务暂时不可用，请稍后重试'];
      if (!res.headersSent) json({ error: code, message }, status); else res.end();
    }
  });
  server.headersTimeout = 15_000; server.requestTimeout = 30_000;
  server.on('close', () => clearInterval(cleanup));
  return server;
}
