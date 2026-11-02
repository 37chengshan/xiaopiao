/**
 * "一键反馈给开发者": one short note the user wrote and chose to send, plus the build it came from.
 *
 * Rules that shape this module:
 * - Sign-in is optional. The App must be able to send feedback before (or without) an account, so the row keeps
 *   `user_id` only when a live session asked; otherwise the row is anonymous.
 * - The text is user-authored copy that may quote one notification. It is never logged, never echoed back in an
 *   error, and only read by the operator script (`scripts/feedback.mjs`) on the server itself — there is no HTTP
 *   read endpoint.
 * - The client IP never reaches the table: only `hash(ip)`, the same helper sessions use, so a row can be counted
 *   per network but not traced to an address.
 *
 * The body is a strict whitelist; an unexpected key is a client bug, not a new field.
 */
import { randomUUID } from 'node:crypto';

export const LIMITS = Object.freeze({
  text: 3000,
  appVersion: 32,
  device: 120,
  /** Body limit for POST /v1/feedback: 3000 characters of CJK fit well inside this. */
  body: 16 * 1024,
  perHour: 10,
  globalPerHour: 300,
  /** Operator read path (`scripts/feedback.mjs`). */
  readDefault: 50,
  readMax: 500,
});

export const KINDS = Object.freeze(['capture_miss', 'general']);
const FIELDS = new Set(['kind', 'text', 'appVersion', 'device']);

const fail = code => { throw Object.assign(new Error(code), { code }); };
const isObject = value => Boolean(value) && typeof value === 'object' && !Array.isArray(value);

/** Optional free-form label: absent and empty are the same row (`''`), anything longer than the limit is refused. */
function label(value, max) {
  if (value === undefined || value === null) return '';
  if (typeof value !== 'string') fail('INVALID_INPUT');
  const result = value.trim();
  if (result.length > max) fail('INVALID_INPUT');
  return result;
}

/** Validates one body and returns the normalised row. Never returns the caller's object. */
export function validFeedback(raw) {
  if (!isObject(raw)) fail('INVALID_INPUT');
  for (const key of Object.keys(raw)) if (!FIELDS.has(key)) fail('INVALID_INPUT');
  if (!KINDS.includes(raw.kind)) fail('INVALID_INPUT');
  if (typeof raw.text !== 'string') fail('INVALID_INPUT');
  const text = raw.text.trim();
  if (!text || text.length > LIMITS.text) fail('INVALID_INPUT');
  return { kind: raw.kind, text, appVersion: label(raw.appVersion, LIMITS.appVersion), device: label(raw.device, LIMITS.device) };
}

export function createFeedback({ db, now = Date.now }) {
  const write = db.prepare('INSERT INTO feedback VALUES (?,?,?,?,?,?,?,?)');
  const newest = db.prepare(`SELECT id, user_id, kind, text, app_version, device, created_at, ip_hash FROM feedback
    WHERE created_at >= ? ORDER BY created_at DESC, rowid DESC LIMIT ?`);

  /** Storing is the whole request: an accepted note has an id, and nothing is echoed back to the sender. */
  function add({ userId = null, ipHash, body }) {
    const entry = validFeedback(body);
    const id = randomUUID();
    write.run(id, userId, entry.kind, entry.text, entry.appVersion, entry.device, now(), ipHash);
    return { ok: true, id };
  }

  /**
   * Operator read path only. Newest first, and `user_id` comes back as-is so a report can be matched to the account
   * that wrote it — the script runs on the server, next to the database.
   */
  function read({ since = 0, limit = LIMITS.readDefault } = {}) {
    return newest.all(since, limit).map(row => ({
      id: row.id, userId: row.user_id, kind: row.kind, text: row.text,
      appVersion: row.app_version, device: row.device, createdAt: row.created_at, ipHash: row.ip_hash,
    }));
  }

  return { add, read };
}
