import { test } from 'node:test';
import assert from 'node:assert/strict';
import { store } from '../src/store.mjs';
import { GUARD, accountKey, sourceKey, createGuard } from '../src/guard.mjs';

/** A hand-driven clock: locks are the whole point, so tests must not wait for them. */
function fixture(t) {
  const db = store(':memory:');
  let at = 1_700_000_000_000;
  const guard = createGuard({ db, now: () => at });
  t.after(() => db.close());
  return { db, guard, advance: ms => { at += ms; }, keys: [accountKey('u1'), sourceKey('203.0.113.9')] };
}

test('five misses lock the account for a minute, then it opens again', async t => {
  const f = fixture(t);
  for (let i = 0; i < GUARD.account.after - 1; i++) f.guard.failed(f.keys, GUARD.account);
  f.guard.blocked(f.keys); // still allowed
  f.guard.failed(f.keys, GUARD.account);
  assert.throws(() => f.guard.blocked(f.keys), error => error.code === 'RATE_LIMITED' && error.retryAfter === 60);
  f.advance(59_000);
  assert.throws(() => f.guard.blocked(f.keys), /RATE_LIMITED/);
  f.advance(1_001);
  f.guard.blocked(f.keys);
});

test('each further lock doubles, capped at fifteen minutes', async t => {
  const f = fixture(t);
  const round = () => {
    for (let i = 0; i < GUARD.account.after; i++) f.guard.failed(f.keys, GUARD.account);
    try { f.guard.blocked(f.keys); assert.fail('expected a lock'); } catch (error) { return error.retryAfter; }
  };
  assert.equal(round(), 60);
  for (let i = 0; i < 6; i++) { f.advance(901_000); round(); }
  f.advance(901_000);
  assert.equal(round(), GUARD.account.maxLockMs / 1000);
  f.advance(GUARD.account.maxLockMs + 1000);
  assert.equal(round(), GUARD.account.maxLockMs / 1000);
});

test('a successful attempt clears both keys', async t => {
  const f = fixture(t);
  for (let i = 0; i < GUARD.account.after - 1; i++) f.guard.failed(f.keys, GUARD.account);
  f.guard.passed(f.keys);
  assert.equal(f.guard.snapshot().failures, 0);
  assert.equal(f.guard.snapshot().tracked, 0);
  for (let i = 0; i < GUARD.account.after - 1; i++) f.guard.failed(f.keys, GUARD.account);
  f.guard.blocked(f.keys);
  // A lock earned before the success is gone too, so one round of misses never compounds across sessions.
  for (let i = 0; i < GUARD.account.after; i++) f.guard.failed(f.keys, GUARD.account);
  assert.throws(() => f.guard.blocked(f.keys), /RATE_LIMITED/);
  f.guard.passed(f.keys);
  f.guard.blocked(f.keys);
});

test('one source is capped separately from any one account', async t => {
  const f = fixture(t);
  const other = [accountKey('u2'), sourceKey('203.0.113.9')];
  for (let i = 0; i < GUARD.ip.after; i++) {
    f.guard.failed([sourceKey('203.0.113.9')], GUARD.ip);
    assert.ok(failures(f.db, sourceKey('203.0.113.9')) <= GUARD.ip.after);
  }
  // Credential stuffing: a fresh account from the same address is refused, a different address is not.
  assert.throws(() => f.guard.blocked(other), error => error.retryAfter === GUARD.ip.lockMs / 1000);
  f.guard.blocked([accountKey('u2'), sourceKey('198.51.100.4')]);
  f.advance(GUARD.ip.lockMs + 1);
  f.guard.blocked(other);
  // Only the source key was touched; the account keys were never even created.
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM auth_guard WHERE key LIKE ?').get('a:%').n, 0);
});

test('the window resets, so old misses do not accumulate forever', async t => {
  const f = fixture(t);
  const ip = [sourceKey('203.0.113.9')];
  for (let i = 0; i < GUARD.ip.after - 1; i++) f.guard.failed(ip, GUARD.ip);
  f.advance(GUARD.ip.windowMs + 1);
  f.guard.failed(ip, GUARD.ip);
  f.guard.blocked(ip);
  assert.equal(failures(f.db, sourceKey('203.0.113.9')), 1);
});

test('snapshot reports aggregates only, with no key ever leaving the module', async t => {
  const f = fixture(t);
  assert.deepEqual(f.guard.snapshot(), { tracked: 0, locked: 0, failures: 0 });
  f.guard.failed(f.keys, GUARD.account);
  f.guard.failed([sourceKey('203.0.113.9')], GUARD.ip);
  const snapshot = f.guard.snapshot();
  assert.deepEqual(Object.keys(snapshot).sort(), ['failures', 'locked', 'tracked']);
  assert.equal(snapshot.tracked, 2);
  // The sum counts misses, not rows: the source key was missed twice (once inside the pair, once alone).
  assert.equal(snapshot.failures, 3);
  assert.equal(snapshot.locked, 0);
  assert.doesNotMatch(JSON.stringify(snapshot), /u1|203\.0\.113\.9|a:|i:/);
  for (let i = 0; i < GUARD.account.after - 1; i++) f.guard.failed(f.keys, GUARD.account);
  // Both keys of the pair crossed the account threshold, and the count is a number, not the keys themselves.
  assert.equal(f.guard.snapshot().locked, 2);
});

function failures(db, key) { return db.prepare('SELECT failures FROM auth_guard WHERE key=?').get(key)?.failures ?? 0; }
