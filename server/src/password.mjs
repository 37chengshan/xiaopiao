/**
 * The password service: one place that decides how a password is stored, how it is checked, and what makes a new
 * one acceptable.
 *
 * Three rules:
 * - Stored hashes are versioned. `scrypt1$<logN>$<r>$<p>$<salt>$<key>` carries its own cost, so raising the cost
 *   later never invalidates an existing row: `verify` derives with the row's parameters and `needsUpgrade` tells the
 *   login path to re-hash once it knows the plaintext. The pre-2026 format `<salt>:<hex>` still verifies.
 * - A pepper (server-side key, never in the database) is optional and off by default. When set, new hashes are
 *   tagged `scrypt1p$`, so adding or removing the variable never breaks rows written under the other variant.
 * - Every derivation goes through a slot gate. scrypt at N=2^17 costs 128 MiB while it runs; the systemd unit caps
 *   this service at 768M, so unbounded parallelism would OOM the process long before an attacker gave up.
 */
import { createHmac, scrypt as derive, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { secret } from './store.mjs';
import { securityError } from './defense.mjs';

const scrypt = promisify(derive);

export const KDF = Object.freeze({ logN: 17, r: 8, p: 1, keylen: 64, maxmem: 256 * 1024 * 1024, concurrency: 2, queue: 8 });
export const PASSWORD = Object.freeze({ min: 8, max: 128 });

/** The cost every hash written before versioning used; frozen so a future KDF change cannot orphan old rows. */
const LEGACY = Object.freeze({ logN: 17, r: 8, p: 1 });

/** Tried first by every password-guessing tool; a list this short still removes the worst offenders. */
const COMMON = new Set([
  'password', 'password1', 'password12', 'password123', 'password1234', 'passw0rd', 'p@ssw0rd', 'pa55word',
  '12345678', '123456789', '1234567890', '12345678910', '0123456789', '87654321', '11111111', '00000000',
  '88888888', '66668888', 'qq123456', 'qq12345678', 'abc12345', 'abcd1234', 'a1234567', 'a12345678',
  'qwertyui', 'qwerty123', 'qwertyuiop', 'qwertyuiop123', 'asdfghjk', 'asdfghjkl', 'zxcvbnm1', 'zxcvbnmlk',
  'iloveyou', 'iloveyou1', 'letmein1', 'letmein123', 'welcome1', 'welcome123', 'admin123', 'admin1234',
  'administrator', 'root1234', 'test1234', 'guest123', 'changeme', 'changeme1', 'secret123', 'default1',
  'sunshine', 'princess', 'football', 'baseball', 'superman', 'trustno1', 'shadow12', 'master12',
  'monkey12', 'dragon12', 'michael1', 'jennifer', 'computer', 'internet', 'whatever', 'starwars',
  'woaini1314', 'woaini520', 'woaini123', '5201314520', '5201314a', '1314520a', '123456abc', 'abc123456',
  'a123456789', 'aa123456', 'aa12345678', 'abc123456789', 'qwe123456', 'qweasdzxc', 'q1w2e3r4', 'zaq12wsx',
  'xiaopiao', 'xiaopiao123', 'jinrixiaopiao', 'xiaopiao2026', 'receipt123', 'alipay123', 'wechat123',
  'mima123456', 'mima1234', 'wodemima', 'buzhidao', 'nihao123', 'nihao1234', 'zhang123', 'wang123',
  'liu12345', 'chen1234', 'yang1234', 'zhao1234', 'huang123', 'zhou1234', 'wu123456', 'xu123456',
  'phone123', 'android1', 'android123', 'samsung1', 'xiaomi123', 'huawei123', 'oppo1234', 'vivo1234',
]);

const LETTERS = /^[a-z]+$/;
const DIGITS = /^\d+$/;

/** Strictly consecutive characters in one direction: `12345678`, `abcdefgh`, `87654321`, `hgfedcba`. */
function sequential(value) {
  if (!LETTERS.test(value) && !DIGITS.test(value)) return false;
  const step = value.charCodeAt(1) - value.charCodeAt(0);
  if (step !== 1 && step !== -1) return false;
  for (let i = 2; i < value.length; i++) if (value.charCodeAt(i) - value.charCodeAt(i - 1) !== step) return false;
  return true;
}

/**
 * What a *new* password must satisfy. Existing credentials are never re-checked: a person who registered years ago
 * with a short password keeps logging in, they just cannot choose another weak one.
 */
export function checkStrength(password, { username = '', email = '' } = {}) {
  if (typeof password !== 'string') return { ok: false, reason: 'short' };
  if (password.length < PASSWORD.min) return { ok: false, reason: 'short' };
  if (password.length > PASSWORD.max) return { ok: false, reason: 'long' };
  const folded = password.toLowerCase();
  if (COMMON.has(folded)) return { ok: false, reason: 'common' };
  if (/^(.)\1*$/u.test(password)) return { ok: false, reason: 'repeat' };
  if (sequential(folded)) return { ok: false, reason: 'sequence' };
  const identities = [username.toLowerCase(), String(email).toLowerCase().split('@')[0]].filter(Boolean);
  if (identities.includes(folded)) return { ok: false, reason: 'identity' };
  return { ok: true };
}

function parse(stored) {
  if (typeof stored !== 'string' || !stored) return null;
  if (!stored.includes('$')) {
    // Pre-versioning rows: `<salt b64url>:<key hex>`, cost fixed by LEGACY.
    const at = stored.indexOf(':');
    if (at < 1) return null;
    const hex = stored.slice(at + 1);
    if (!/^[0-9a-f]+$/i.test(hex) || hex.length % 2) return null;
    return { legacy: true, peppered: false, ...LEGACY, salt: stored.slice(0, at), key: Buffer.from(hex, 'hex') };
  }
  const parts = stored.split('$');
  if (parts.length !== 6) return null;
  const [tag, logN, r, p, salt, key] = parts;
  if (tag !== 'scrypt1' && tag !== 'scrypt1p') return null;
  if (!/^\d{1,2}$/.test(logN) || !/^\d{1,3}$/.test(r) || !/^\d{1,3}$/.test(p)) return null;
  if (!salt || !/^[A-Za-z0-9_-]+$/.test(salt) || !/^[A-Za-z0-9_-]+$/.test(key)) return null;
  const cost = { logN: Number(logN), r: Number(r), p: Number(p) };
  if (cost.logN < 10 || cost.logN > 20 || cost.r < 1 || cost.r > 32 || cost.p < 1 || cost.p > 16) return null;
  return { legacy: false, peppered: tag === 'scrypt1p', ...cost, salt, key: Buffer.from(key, 'base64url') };
}

/** Bounded parallelism: a slot is taken synchronously, so an overloaded queue is refused instead of piling up. */
function createGate({ concurrency, queue }) {
  const waiters = [];
  let active = 0;
  async function acquire() {
    if (active < concurrency) { active++; return; }
    if (waiters.length >= queue) throw securityError('SERVER_BUSY', 5);
    await new Promise(resolve => waiters.push(resolve));
  }
  function release() {
    const next = waiters.shift();
    if (next) next(); else active--;
  }
  return { acquire, release };
}

export function createPasswords({ pepper = null, kdf = KDF } = {}) {
  const gate = createGate(kdf);
  const keyed = Boolean(pepper);

  /** scrypt on the HMAC of the password when a pepper is configured; parameters always come from the stored row. */
  function deriveKey(password, salt, cost, peppered) {
    const material = peppered ? createHmac('sha256', pepper).update(password).digest() : password;
    return scrypt(material, salt, KDF.keylen, { N: 2 ** cost.logN, r: cost.r, p: cost.p, maxmem: KDF.maxmem });
  }

  async function hash(password) {
    const salt = secret();
    const tag = keyed ? 'scrypt1p' : 'scrypt1';
    await gate.acquire();
    try {
      const key = await deriveKey(password, salt, kdf, keyed);
      return [tag, kdf.logN, kdf.r, kdf.p, salt, key.toString('base64url')].join('$');
    } finally { gate.release(); }
  }

  /**
   * Never throws: an unreadable or truncated row is a failed login, not a 500. A peppered row checked by an instance
   * without the pepper also fails — boot warns about exactly that case.
   */
  async function verify(password, stored) {
    if (typeof password !== 'string') return false;
    const parsed = parse(stored);
    if (!parsed) return false;
    if (parsed.peppered && !keyed) return false;
    let candidate;
    await gate.acquire();
    try { candidate = await deriveKey(password, parsed.salt, parsed, parsed.peppered); }
    catch { return false; }
    finally { gate.release(); }
    return candidate.length === parsed.key.length && timingSafeEqual(candidate, parsed.key);
  }

  /** True when the row predates the current cost, the current format, or the currently configured pepper. */
  function needsUpgrade(stored) {
    const parsed = parse(stored);
    if (!parsed) return false;
    if (parsed.legacy) return true;
    if (parsed.logN !== kdf.logN || parsed.r !== kdf.r || parsed.p !== kdf.p) return true;
    return keyed && !parsed.peppered;
  }

  return { hash, verify, needsUpgrade };
}
