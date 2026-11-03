import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { store, publicUser } from '../src/store.mjs';

/** The 2026-10 shape of `users`: email and password NOT NULL, no WeChat column. */
function oldDatabase(path) {
  const db = new DatabaseSync(path);
  db.exec(`PRAGMA foreign_keys=ON;
    CREATE TABLE users (id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, username TEXT UNIQUE NOT NULL, password TEXT NOT NULL, verified INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL);
    CREATE TABLE sessions (digest TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, expires INTEGER NOT NULL);
    CREATE TABLE ledger_txns (
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, id TEXT NOT NULL, amount_cents INTEGER NOT NULL,
      direction TEXT NOT NULL, merchant TEXT NOT NULL, category TEXT NOT NULL, occurred_at INTEGER NOT NULL,
      source TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', card_last4 TEXT, updated_at INTEGER NOT NULL,
      deleted INTEGER NOT NULL DEFAULT 0, device_id TEXT NOT NULL, seq INTEGER NOT NULL, PRIMARY KEY (user_id, id));`);
  db.prepare('INSERT INTO users (id,email,username,password,verified,created) VALUES (?,?,?,?,1,?)')
    .run('u1', 'old@example.test', 'old', 'salt:hash', 1);
  db.prepare('INSERT INTO sessions VALUES (?,?,?)').run('digest', 'u1', Date.now() + 60_000);
  db.prepare('INSERT INTO ledger_txns (user_id,id,amount_cents,direction,merchant,category,occurred_at,source,updated_at,device_id,seq) VALUES (?,?,?,?,?,?,?,?,?,?,?)')
    .run('u1', 't1', 1200, 'OUT', '面馆', '吃饭', 1, 'MANUAL', 1, 'device', 1);
  db.close();
}

test('an older database gains a nullable email/password and a WeChat column without losing anything', t => {
  const dir = mkdtempSync(join(tmpdir(), 'xiaopiao-migration-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const path = join(dir, 'old.sqlite');
  oldDatabase(path);

  const db = store(path);
  t.after(() => db.close());
  const columns = db.prepare('PRAGMA table_info(users)').all();
  const column = name => columns.find(item => item.name === name);
  assert.equal(column('email').notnull, 0, 'email becomes optional for a WeChat-only account');
  assert.equal(column('password').notnull, 0);
  assert.ok(column('wx_openid'), 'the WeChat identity column exists');
  assert.equal(db.prepare('SELECT count(*) AS n FROM users WHERE id=?').get('u1').n, 1);
  assert.equal(db.prepare('SELECT email FROM users WHERE id=?').get('u1').email, 'old@example.test');
  assert.equal(db.prepare('SELECT count(*) AS n FROM sessions').get().n, 1, 'sessions survive the swap');
  assert.equal(db.prepare('SELECT amount_cents FROM ledger_txns WHERE id=?').get('t1').amount_cents, 1200, 'ledger rows survive');
  assert.deepEqual(db.prepare('PRAGMA foreign_key_check').all(), [], 'child tables still point at the rebuilt users table');
  assert.equal(db.prepare('PRAGMA foreign_keys').get().foreign_keys, 1, 'foreign keys are switched back on');

  // Uniqueness still holds after the rebuild, and a WeChat-only row is accepted.
  assert.throws(() => db.prepare('INSERT INTO users (id,email,username,password,verified,created) VALUES (?,?,?,?,1,?)')
    .run('u2', 'old@example.test', 'other', 'x', 1));
  db.prepare('INSERT INTO users (id,email,username,password,verified,created,wx_openid) VALUES (?,?,?,?,1,?,?)')
    .run('u3', null, 'wx_abc', null, 1, 'openid_migration');
  db.prepare('INSERT INTO users (id,email,username,password,verified,created,wx_openid) VALUES (?,?,?,?,1,?,?)')
    .run('u4', null, 'wx_def', null, 1, 'openid_migration_2');
  assert.equal(db.prepare('SELECT count(*) AS n FROM users WHERE email IS NULL').get().n, 2, 'several accounts may have no email');
  assert.equal(publicUser(db.prepare('SELECT * FROM users WHERE id=?').get('u3')).email, null);
  assert.equal(publicUser(db.prepare('SELECT * FROM users WHERE id=?').get('u3')).wechat, true);
  assert.equal(publicUser(db.prepare('SELECT * FROM users WHERE id=?').get('u1')).wechat, false);

  // Running the migration twice is a no-op.
  const again = store(path);
  again.close();
});
