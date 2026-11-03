import { DatabaseSync } from 'node:sqlite';
import { mkdirSync, chmodSync } from 'node:fs';
import { dirname } from 'node:path';
import { randomBytes, randomInt, createHash } from 'node:crypto';
export const hash = value => createHash('sha256').update(value).digest('hex');
export const secret = () => randomBytes(32).toString('base64url');
export function store(path) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  const db = new DatabaseSync(path);
  if (path !== ':memory:') chmodSync(path, 0o600);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, email TEXT UNIQUE, username TEXT UNIQUE NOT NULL, password TEXT, verified INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, wx_openid TEXT UNIQUE);
    CREATE TABLE IF NOT EXISTS sessions (digest TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS email_changes (user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, email TEXT NOT NULL, expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS avatars (user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, preset TEXT, mime TEXT, bytes BLOB, updated_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS codes (user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, purpose TEXT NOT NULL, digest TEXT NOT NULL, expires INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(user_id,purpose));
    CREATE TABLE IF NOT EXISTS runs (id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, state TEXT NOT NULL, expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS limits (key TEXT PRIMARY KEY, count INTEGER NOT NULL, expires INTEGER NOT NULL);
    CREATE INDEX IF NOT EXISTS limits_expiry ON limits (expires);
    CREATE TABLE IF NOT EXISTS ledger_seq (user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, value INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS ledger_txns (
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      id TEXT NOT NULL,
      amount_cents INTEGER NOT NULL,
      direction TEXT NOT NULL,
      merchant TEXT NOT NULL,
      category TEXT NOT NULL,
      occurred_at INTEGER NOT NULL,
      source TEXT NOT NULL,
      note TEXT NOT NULL DEFAULT '',
      card_last4 TEXT,
      updated_at INTEGER NOT NULL,
      deleted INTEGER NOT NULL DEFAULT 0,
      device_id TEXT NOT NULL,
      seq INTEGER NOT NULL,
      PRIMARY KEY (user_id, id)
    );
    CREATE INDEX IF NOT EXISTS ledger_txns_pull ON ledger_txns (user_id, seq);
    CREATE TABLE IF NOT EXISTS ledger_categories (
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      id TEXT NOT NULL,
      label TEXT NOT NULL,
      icon_key TEXT NOT NULL,
      hue TEXT NOT NULL,
      direction TEXT,
      hidden INTEGER NOT NULL DEFAULT 0,
      sort_order INTEGER NOT NULL DEFAULT 0,
      updated_at INTEGER NOT NULL,
      deleted INTEGER NOT NULL DEFAULT 0,
      seq INTEGER NOT NULL,
      PRIMARY KEY (user_id, id)
    );
    CREATE INDEX IF NOT EXISTS ledger_categories_pull ON ledger_categories (user_id, seq);
    CREATE TABLE IF NOT EXISTS circles (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      created_at INTEGER NOT NULL,
      no TEXT
    );
    CREATE TABLE IF NOT EXISTS circle_members (
      circle_id TEXT NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      role TEXT NOT NULL,
      joined_at INTEGER NOT NULL,
      PRIMARY KEY (circle_id, user_id)
    );
    CREATE INDEX IF NOT EXISTS circle_members_user ON circle_members (user_id);
    CREATE TABLE IF NOT EXISTS circle_requests (
      circle_id TEXT NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      created_at INTEGER NOT NULL,
      PRIMARY KEY (circle_id, user_id)
    );
    CREATE TABLE IF NOT EXISTS circle_invites (
      code TEXT PRIMARY KEY,
      circle_id TEXT NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
      created_by TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      expires_at INTEGER NOT NULL,
      max_uses INTEGER NOT NULL,
      uses INTEGER NOT NULL DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS circle_invites_circle ON circle_invites (circle_id);
    CREATE TABLE IF NOT EXISTS circle_posts (
      id TEXT PRIMARY KEY,
      circle_id TEXT NOT NULL REFERENCES circles(id) ON DELETE CASCADE,
      author_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      text TEXT NOT NULL DEFAULT '',
      receipt TEXT,
      created_at INTEGER NOT NULL,
      deleted INTEGER NOT NULL DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS circle_posts_feed ON circle_posts (circle_id, created_at);
    CREATE TABLE IF NOT EXISTS post_likes (
      post_id TEXT NOT NULL REFERENCES circle_posts(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      created_at INTEGER NOT NULL,
      PRIMARY KEY (post_id, user_id)
    );
    CREATE TABLE IF NOT EXISTS post_comments (
      id TEXT PRIMARY KEY,
      post_id TEXT NOT NULL REFERENCES circle_posts(id) ON DELETE CASCADE,
      author_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      text TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      deleted INTEGER NOT NULL DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS post_comments_post ON post_comments (post_id, created_at);
    CREATE TABLE IF NOT EXISTS feedback (
      id TEXT PRIMARY KEY,
      user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      text TEXT NOT NULL,
      app_version TEXT NOT NULL,
      device TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      ip_hash TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS feedback_created ON feedback (created_at);
    CREATE TABLE IF NOT EXISTS auth_guard (
      key TEXT PRIMARY KEY,
      failures INTEGER NOT NULL,
      strikes INTEGER NOT NULL DEFAULT 0,
      window_start INTEGER NOT NULL,
      locked_until INTEGER NOT NULL DEFAULT 0,
      updated_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS wechat_bind_tickets (
      digest TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      status TEXT NOT NULL DEFAULT 'pending',
      openid TEXT,
      nickname TEXT,
      avatar_url TEXT,
      created_at INTEGER NOT NULL,
      expires INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS wechat_bind_tickets_user ON wechat_bind_tickets (user_id);`);
  migrateCircleNumbers(db);
  migrateWeChatUsers(db);
  migrateTicketColumns(db);
  return db;
}
/**
 * The binding ticket gained two columns when binding became a two-step decision (the App confirms what the mini
 * program claimed). A database written by the previous shape gets them here; existing rows stay pending.
 */
function migrateTicketColumns(db) {
  const columns = new Set(db.prepare('PRAGMA table_info(wechat_bind_tickets)').all().map(column => column.name));
  for (const name of ['nickname', 'avatar_url']) {
    if (!columns.has(name)) db.exec(`ALTER TABLE wechat_bind_tickets ADD COLUMN ${name} TEXT`);
  }
}
/**
 * WeChat-only accounts have no email and no password, so those two columns must be nullable. SQLite cannot drop a
 * NOT NULL constraint in place, so an older database is rebuilt once at startup: copy, swap, rename. Foreign keys
 * are switched off for the swap (otherwise dropping `users` would cascade every ledger row away) and back on after,
 * and the child tables keep pointing at the name `users`, which the rename restores.
 */
function migrateWeChatUsers(db) {
  const columns = db.prepare('PRAGMA table_info(users)').all();
  const column = name => columns.find(item => item.name === name);
  const hasOpenid = Boolean(column('wx_openid'));
  const nullable = column('email')?.notnull === 0 && column('password')?.notnull === 0;
  if (hasOpenid && nullable) return;
  db.exec('PRAGMA foreign_keys=OFF; BEGIN IMMEDIATE');
  try {
    db.exec('DROP TABLE IF EXISTS users_wechat');
    db.exec(`CREATE TABLE users_wechat (
      id TEXT PRIMARY KEY, email TEXT UNIQUE, username TEXT UNIQUE NOT NULL, password TEXT,
      verified INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, wx_openid TEXT UNIQUE)`);
    db.exec(`INSERT INTO users_wechat (id,email,username,password,verified,created)
      SELECT id,email,username,password,verified,created FROM users`);
    db.exec('DROP TABLE users');
    db.exec('ALTER TABLE users_wechat RENAME TO users');
    db.exec('COMMIT');
  } catch (error) {
    db.exec('ROLLBACK');
    db.exec('PRAGMA foreign_keys=ON');
    throw error;
  }
  db.exec('PRAGMA foreign_keys=ON');
  const broken = db.prepare('PRAGMA foreign_key_check').all();
  if (broken.length) throw new Error('MIGRATION_FOREIGN_KEY_CHECK_FAILED');
}
/**
 * `circles.no`: the 7-digit number a person can be told out loud to find a circle. Databases created before it
 * existed get the column and a number per circle here, so an upgrade needs no manual step. The unique index is
 * created after the column: SQLite allows several NULLs in a unique index, so either order is safe, but this one
 * keeps a half-filled table from ever looking valid.
 */
function migrateCircleNumbers(db) {
  if (!db.prepare('PRAGMA table_info(circles)').all().some(column => column.name === 'no')) {
    db.exec('ALTER TABLE circles ADD COLUMN no TEXT');
  }
  const missing = db.prepare('SELECT id FROM circles WHERE no IS NULL').all();
  if (missing.length) {
    const taken = new Set(db.prepare('SELECT no FROM circles WHERE no IS NOT NULL').all().map(row => row.no));
    const assign = db.prepare('UPDATE circles SET no=? WHERE id=?');
    for (const circle of missing) {
      let number;
      do { number = String(randomInt(1_000_000, 10_000_000)); } while (taken.has(number));
      taken.add(number);
      assign.run(number, circle.id);
    }
  }
  db.exec('CREATE UNIQUE INDEX IF NOT EXISTS circles_no ON circles (no)');
}
/**
 * `avatar` is null, `{ preset }` or `{ url }` (see avatar.mjs); callers resolve it, never the stored bytes.
 * `email` is null for an account created from WeChat that has not attached an email yet, and `wechat` says whether
 * a WeChat identity is bound — a client needs both to decide which sign-in methods to offer.
 */
export function publicUser(row, avatar = null) {
  return {
    id: row.id,
    email: row.email ?? null,
    username: row.username,
    verified: Boolean(row.verified),
    avatar,
    wechat: Boolean(row.wx_openid),
  };
}
