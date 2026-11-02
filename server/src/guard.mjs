/**
 * Authentication defence: who is failing, how often, and when they get to try again.
 *
 * Two dimensions, because they stop different attacks:
 * - `a:<userId>` — one account being guessed. Locked for 60s after 5 misses, doubling per lock, capped at 15
 *   minutes, so a real person who forgot their password loses seconds and an attacker loses months.
 * - `i:<hash(ip)>` — one source cycling through many accounts (credential stuffing). 20 misses in an hour locks
 *   the source for an hour regardless of which identifier it used.
 *
 * A correct password clears both keys for that attempt, so a legitimately mistyped-then-remembered login leaves
 * nothing behind. Counters are aggregate-only in `snapshot()`: no identifiers ever leave this module.
 *
 * The account lock is the classic trade-off — someone who knows a username can lock it for up to 15 minutes. That
 * is why the source budget is the primary defence and the account lock is short and capped.
 */
import { hash } from './store.mjs';
import { securityError } from './defense.mjs';

export const GUARD = Object.freeze({
  account: Object.freeze({ after: 5, windowMs: 900_000, lockMs: 60_000, maxLockMs: 900_000 }),
  ip: Object.freeze({ after: 20, windowMs: 3_600_000, lockMs: 3_600_000, maxLockMs: 3_600_000 }),
});

/** The two key builders every caller shares: an account key is only built for a user that actually exists. */
export const accountKey = userId => `a:${userId}`;
export const sourceKey = ip => `i:${hash(String(ip))}`;

export function createGuard({ db, now = Date.now }) {
  const read = db.prepare('SELECT * FROM auth_guard WHERE key=?');
  const write = db.prepare(`INSERT INTO auth_guard(key,failures,strikes,window_start,locked_until,updated_at)
    VALUES (?,?,?,?,?,?)
    ON CONFLICT(key) DO UPDATE SET failures=excluded.failures, strikes=excluded.strikes,
      window_start=excluded.window_start, locked_until=excluded.locked_until, updated_at=excluded.updated_at`);
  const drop = db.prepare('DELETE FROM auth_guard WHERE key=?');
  const locked = db.prepare('SELECT key,locked_until FROM auth_guard WHERE locked_until > ?');
  const totals = db.prepare('SELECT count(*) AS tracked, coalesce(sum(failures),0) AS failures FROM auth_guard');

  /** Refuse before any password hashing happens: a locked sender costs one row read, not 128 MiB. */
  function blocked(keys) {
    const at = now();
    for (const key of keys) {
      const row = read.get(key);
      if (row && row.locked_until > at) throw securityError('RATE_LIMITED', Math.max(1, Math.ceil((row.locked_until - at) / 1000)));
    }
  }

  function failed(keys, limits) {
    const at = now();
    for (const key of keys) {
      const row = read.get(key);
      const fresh = !row || at - row.window_start >= limits.windowMs;
      const failures = fresh ? 1 : row.failures + 1;
      const strikes = row?.strikes ?? 0;
      if (failures >= limits.after) {
        // Each completed round of failures doubles the wait; the cap keeps a lockout from becoming a lockout attack.
        const next = strikes + 1;
        write.run(key, 0, next, at, at + Math.min(limits.lockMs * 2 ** (next - 1), limits.maxLockMs), at);
      } else {
        write.run(key, failures, strikes, fresh ? at : row.window_start, row?.locked_until ?? 0, at);
      }
    }
  }

  function passed(keys) { for (const key of keys) drop.run(key); }

  function snapshot() {
    const row = totals.get();
    return { tracked: row.tracked, locked: locked.all(now()).length, failures: row.failures };
  }

  return { blocked, failed, passed, snapshot };
}
