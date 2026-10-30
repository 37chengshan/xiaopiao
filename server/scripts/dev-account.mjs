#!/usr/bin/env node
/**
 * One dev account on a running `npm run dev` server: register (or log in), read the newest code from the
 * dev mail log, verify the address, and keep the session token in server/data/dev-token.txt.
 *
 *   node scripts/dev-account.mjs [base] [--email=a@b.test] [--username=dev] [--password=…] [--log=path]
 *
 * Only the first six characters of the token are printed; the full token stays in the ignored data dir.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const DEFAULTS = { base: 'http://127.0.0.1:8931', email: 'dev@example.test', username: 'dev', password: 'A long dev passphrase 123' };

const positional = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : DEFAULTS.base;
const flag = (name, fallback) => process.argv.slice(2).find(value => value.startsWith(`--${name}=`))?.slice(name.length + 3) ?? fallback;
const base = positional.replace(/\/$/, '');
const email = flag('email', DEFAULTS.email);
const username = flag('username', DEFAULTS.username);
const password = flag('password', DEFAULTS.password);
const mailLog = flag('log', resolve(here, '../data/dev-mail.log'));
const tokenFile = flag('token-file', resolve(here, '../data/dev-token.txt'));

async function call(path, body, token) {
  const response = await fetch(base + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: response.status, data: await response.json().catch(() => null) };
}

/** The dev mail log is append-only, so the newest matching line is the code that is still valid. */
function newestCode(address) {
  let lines;
  try { lines = readFileSync(mailLog, 'utf8').split('\n'); }
  catch { throw new Error(`找不到 ${mailLog}：dev server 是否用 XP_DEV_MAIL=1 启动？`); }
  for (let index = lines.length - 1; index >= 0; index--) {
    const [at, purpose, to, code] = lines[index].split('\t');
    if (purpose === 'verify' && to === address && /^\d{6}$/.test(code ?? '') && !Number.isNaN(Date.parse(at))) return code;
  }
  throw new Error(`dev mail 日志里没有 ${address} 的验证码`);
}

function die(message, detail) {
  console.error(`dev-account: ${message}`);
  if (detail) console.error(JSON.stringify(detail));
  process.exit(1);
}

async function main() {
  let registered = await call('/v1/auth/register', { email, username, password, client: 'native' });
  let token;
  if (registered.status === 201) token = registered.data.token;
  else if (registered.status === 409) {
    const login = await call('/v1/auth/login', { identifier: email, password, client: 'native' });
    if (login.status !== 200) die('账号已存在但登录失败，换 --email/--password 再试', login.data);
    token = login.data.token;
    console.log(`已有账号，已登录 ${email}`);
  } else die(`注册失败（HTTP ${registered.status}）`, registered.data);

  const me = await call('/v1/auth/me', undefined, token);
  if (me.status !== 200) die(`会话不可用（HTTP ${me.status}）`, me.data);

  if (!me.data.user.verified) {
    // The newest line in the dev log is normally the code register just issued; resend once if it is stale.
    let verified = await call('/v1/auth/verify', { code: newestCode(email) }, token);
    if (verified.status === 400 && verified.data?.error === 'CODE_INVALID') {
      const resent = await call('/v1/auth/resend', {}, token);
      if (resent.status !== 200) die(`重发验证码失败（HTTP ${resent.status}）`, resent.data);
      verified = await call('/v1/auth/verify', { code: newestCode(email) }, token);
    }
    if (verified.status !== 200) die(`验证失败（HTTP ${verified.status}）`, verified.data);
  }

  mkdirSync(dirname(tokenFile), { recursive: true, mode: 0o700 });
  writeFileSync(tokenFile, `${token}\n`, { mode: 0o600 });
  console.log(`base ${base}`);
  console.log(`email ${email}`);
  console.log(`token ${token.slice(0, 6)}… （完整 token 在 ${tokenFile}）`);
  console.log('verified: true');
}

main().catch(error => die(error.message.includes('fetch failed') ? `连不上 ${base}，先 npm run dev` : error.message));
