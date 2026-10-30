/**
 * Circles: a private feed for family and close friends — text posts plus an optional daily-receipt card.
 *
 * Two rules shape this module:
 * - The receipt card is a strict whitelist of structured numbers and short copy. Anything that could be
 *   bill text (rawText, notification, screenshot, merchant …) is refused by name, like the ledger does.
 * - A non-member gets 404, never 403: whether a circle or a post exists is itself private inside a circle.
 */
import { randomInt, randomUUID } from 'node:crypto';

export const LIMITS = {
  name: 20,
  text: 500,
  comment: 200,
  circlesPerUser: 10,
  membersPerCircle: 50,
  postsPage: 50,
  defaultPostsPage: 20,
  commentsPage: 50,
  defaultCommentsPage: 20,
  previewComments: 3,
  receiptTitle: 16,
  receiptComment: 60,
  receiptLineLabel: 16,
  receiptLines: 30,
  receiptCount: 1_000_000,
  maxCents: 10_000_000_000,
  inviteCode: 8,
  inviteDays: 7,
  inviteUses: 20,
  username: 30,
};

/** Receipt cards carry structured numbers, never bill text. These names are refused with a clear reason. */
const RECEIPT_RAW_FIELDS = new Set([
  'rawtext', 'raw_text', 'raw', 'text', 'body', 'content', 'note', 'merchant', 'ocr',
  'screenshot', 'screenshots', 'image', 'images', 'photo', 'photos', 'html', 'payload',
  'attachment', 'notification', 'notifications', 'rawmessage', 'raw_message',
]);
const RECEIPT_FIELDS = ['date', 'totalCents', 'title', 'count', 'comment', 'lines'];
const LINE_FIELDS = ['label', 'amountCents'];
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
/** No 0/O/1/I: an invite code is read aloud and typed by hand. */
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const fail = code => { throw Object.assign(new Error(code), { code }); };
const isObject = value => Boolean(value) && typeof value === 'object' && !Array.isArray(value);
const isCents = value => Number.isSafeInteger(value) && value >= 0 && value <= LIMITS.maxCents;

function trimmed(value, max, { allowEmpty = false } = {}) {
  if (typeof value !== 'string') fail('INVALID_INPUT');
  const result = value.trim();
  if (!result && !allowEmpty) fail('INVALID_INPUT');
  if (result.length > max) fail('INVALID_INPUT');
  return result;
}

function isDate(value) {
  const match = typeof value === 'string' ? DATE.exec(value) : null;
  if (!match) return false;
  const [year, month, day] = match.slice(1).map(Number);
  const at = new Date(Date.UTC(year, month - 1, day));
  return at.getUTCFullYear() === year && at.getUTCMonth() === month - 1 && at.getUTCDate() === day;
}

/** Validates the card the client would print, returning the normalised copy that is safe to store. */
function validReceipt(raw) {
  if (!isObject(raw)) return { reason: 'RECEIPT_INVALID' };
  const card = {};
  for (const [key, value] of Object.entries(raw)) {
    if (RECEIPT_RAW_FIELDS.has(key.toLowerCase())) return { reason: 'RECEIPT_RAW_TEXT' };
    if (!RECEIPT_FIELDS.includes(key)) return { reason: 'RECEIPT_FIELD' };
    card[key] = value;
  }
  if (!isDate(card.date)) return { reason: 'RECEIPT_INVALID' };
  if (!isCents(card.totalCents)) return { reason: 'RECEIPT_INVALID' };
  if (typeof card.title !== 'string' || !card.title.trim() || card.title.trim().length > LIMITS.receiptTitle) return { reason: 'RECEIPT_INVALID' };
  if (!Number.isSafeInteger(card.count) || card.count < 0 || card.count > LIMITS.receiptCount) return { reason: 'RECEIPT_INVALID' };
  const comment = card.comment ?? '';
  if (typeof comment !== 'string' || comment.length > LIMITS.receiptComment) return { reason: 'RECEIPT_INVALID' };
  let lines;
  if (card.lines !== undefined) {
    if (!Array.isArray(card.lines) || card.lines.length > LIMITS.receiptLines) return { reason: 'RECEIPT_INVALID' };
    lines = [];
    for (const line of card.lines) {
      if (!isObject(line)) return { reason: 'RECEIPT_INVALID' };
      for (const key of Object.keys(line)) {
        if (RECEIPT_RAW_FIELDS.has(key.toLowerCase())) return { reason: 'RECEIPT_RAW_TEXT' };
        if (!LINE_FIELDS.includes(key)) return { reason: 'RECEIPT_FIELD' };
      }
      if (typeof line.label !== 'string' || !line.label.trim() || line.label.trim().length > LIMITS.receiptLineLabel) return { reason: 'RECEIPT_INVALID' };
      if (!isCents(line.amountCents)) return { reason: 'RECEIPT_INVALID' };
      lines.push({ label: line.label.trim(), amountCents: line.amountCents });
    }
  }
  return {
    value: {
      date: card.date,
      totalCents: card.totalCents,
      title: card.title.trim(),
      count: card.count,
      comment,
      ...(lines ? { lines } : {}),
    },
  };
}

const circleJson = row => ({ id: row.id, name: row.name, role: row.role, memberCount: row.member_count, createdAt: row.created_at });

/** The cursor is opaque on purpose: `<createdAt>.<rowid>`, base64url, so a client cannot invent a page start. */
const encodeCursor = (at, rid) => Buffer.from(`${at}.${rid}`).toString('base64url');
function decodeCursor(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value !== 'string' || value.length > 64) fail('INVALID_INPUT');
  const text = Buffer.from(value, 'base64url').toString();
  const match = /^(\d{1,16})\.(\d{1,16})$/.exec(text);
  if (!match) fail('INVALID_INPUT');
  return { at: Number(match[1]), rid: Number(match[2]) };
}

export function createCircles({ db, now = Date.now }) {
  const circleRow = db.prepare(`SELECT c.id, c.name, c.created_at, m.role,
      (SELECT COUNT(*) FROM circle_members x WHERE x.circle_id=c.id) AS member_count
    FROM circles c JOIN circle_members m ON m.circle_id=c.id WHERE c.id=? AND m.user_id=?`);
  const memberRow = db.prepare('SELECT role FROM circle_members WHERE circle_id=? AND user_id=?');
  const memberTotal = db.prepare('SELECT COUNT(*) AS n FROM circle_members WHERE circle_id=?');
  const circlesOf = db.prepare(`SELECT c.id, c.name, c.created_at, m.role,
      (SELECT COUNT(*) FROM circle_members x WHERE x.circle_id=c.id) AS member_count
    FROM circles c JOIN circle_members m ON m.circle_id=c.id WHERE m.user_id=? ORDER BY c.created_at DESC`);
  const circleCount = db.prepare('SELECT COUNT(*) AS n FROM circle_members WHERE user_id=?');
  const membersOf = db.prepare(`SELECT m.user_id, m.role, m.joined_at, u.username FROM circle_members m
    JOIN users u ON u.id=m.user_id WHERE m.circle_id=? ORDER BY m.role='owner' DESC, m.joined_at, u.username`);
  const postRow = db.prepare('SELECT rowid AS rid, * FROM circle_posts WHERE id=?');
  const postWithAuthor = db.prepare(`SELECT p.rowid AS rid, p.*, u.username FROM circle_posts p JOIN users u ON u.id=p.author_id WHERE p.id=?`);
  const likedBy = db.prepare('SELECT 1 AS ok FROM post_likes WHERE post_id=? AND user_id=?');
  const likesOf = db.prepare('SELECT COUNT(*) AS n FROM post_likes WHERE post_id=?');
  const commentTotal = db.prepare('SELECT COUNT(*) AS n FROM post_comments WHERE post_id=? AND deleted=0');
  const latestComments = db.prepare(`SELECT c.id, c.text, c.created_at, c.author_id, u.username FROM post_comments c
    JOIN users u ON u.id=c.author_id WHERE c.post_id=? AND c.deleted=0
    ORDER BY c.created_at DESC, c.rowid DESC LIMIT ?`);
  const commentRow = db.prepare('SELECT rowid AS rid, * FROM post_comments WHERE id=?');

  /** Non-members are told "not found", never "forbidden": membership itself is private. */
  function membership(userId, circleId) {
    const row = memberRow.get(circleId, userId);
    if (!row) fail('CIRCLE_NOT_FOUND');
    return row.role;
  }
  function view(userId, circleId) {
    const row = circleRow.get(circleId, userId);
    if (!row) fail('CIRCLE_NOT_FOUND');
    return circleJson(row);
  }
  function livePost(userId, postId) {
    const post = postRow.get(postId);
    if (!post || post.deleted) fail('POST_NOT_FOUND');
    membership(userId, post.circle_id);
    return post;
  }
  function pageLimit(value, fallback, max) {
    if (value === undefined || value === null || value === '') return fallback;
    if (!Number.isSafeInteger(value) || value < 1) fail('INVALID_INPUT');
    return Math.min(value, max);
  }

  function create(userId, name) {
    const label = trimmed(name, LIMITS.name);
    if (circleCount.get(userId).n >= LIMITS.circlesPerUser) fail('CIRCLE_LIMIT');
    const id = randomUUID(); const at = now();
    db.exec('BEGIN IMMEDIATE');
    try {
      db.prepare('INSERT INTO circles(id,name,owner_id,created_at) VALUES (?,?,?,?)').run(id, label, userId, at);
      db.prepare("INSERT INTO circle_members(circle_id,user_id,role,joined_at) VALUES (?,?,'owner',?)").run(id, userId, at);
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
    return view(userId, id);
  }

  function list(userId) {
    return { circles: circlesOf.all(userId).map(circleJson) };
  }

  function members(userId, circleId) {
    membership(userId, circleId);
    const rows = membersOf.all(circleId);
    return {
      count: rows.length,
      members: rows.map(row => ({ id: row.user_id, username: row.username, role: row.role, joinedAt: row.joined_at })),
    };
  }

  function invite(userId, circleId) {
    membership(userId, circleId);
    const expiresAt = now() + LIMITS.inviteDays * 86400_000;
    for (let attempt = 0; attempt < 5; attempt++) {
      let code = '';
      for (let index = 0; index < LIMITS.inviteCode; index++) code += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
      try {
        db.prepare('INSERT INTO circle_invites(code,circle_id,created_by,expires_at,max_uses,uses) VALUES (?,?,?,?,?,0)')
          .run(code, circleId, userId, expiresAt, LIMITS.inviteUses);
        return { code, expiresAt, maxUses: LIMITS.inviteUses };
      } catch { /* code collision, draw again */ }
    }
    throw new Error('invite code space exhausted');
  }

  function join(userId, code) {
    const value = trimmed(code, 16).toUpperCase();
    const found = db.prepare('SELECT * FROM circle_invites WHERE code=?').get(value);
    if (!found || found.expires_at <= now() || found.uses >= found.max_uses) fail('INVITE_INVALID');
    if (memberRow.get(found.circle_id, userId)) return view(userId, found.circle_id);
    if (circleCount.get(userId).n >= LIMITS.circlesPerUser) fail('CIRCLE_LIMIT');
    if (memberTotal.get(found.circle_id).n >= LIMITS.membersPerCircle) fail('CIRCLE_FULL');
    db.exec('BEGIN IMMEDIATE');
    try {
      // Re-reading under the write lock keeps two simultaneous joins from exceeding max_uses or the cap.
      const fresh = db.prepare('SELECT uses, max_uses, expires_at FROM circle_invites WHERE code=?').get(value);
      if (!fresh || fresh.expires_at <= now() || fresh.uses >= fresh.max_uses) fail('INVITE_INVALID');
      if (db.prepare('SELECT 1 FROM circle_members WHERE circle_id=? AND user_id=?').get(found.circle_id, userId)) {
        db.exec('COMMIT');
        return view(userId, found.circle_id);
      }
      if (db.prepare('SELECT COUNT(*) AS n FROM circle_members WHERE circle_id=?').get(found.circle_id).n >= LIMITS.membersPerCircle) fail('CIRCLE_FULL');
      db.prepare("INSERT INTO circle_members(circle_id,user_id,role,joined_at) VALUES (?,?,'member',?)").run(found.circle_id, userId, now());
      db.prepare('UPDATE circle_invites SET uses=uses+1 WHERE code=?').run(value);
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
    return view(userId, found.circle_id);
  }

  function leave(userId, circleId) {
    const role = membership(userId, circleId);
    if (role === 'owner') {
      // The owner can only leave an empty circle; otherwise members would be stranded without an owner.
      if (memberTotal.get(circleId).n > 1) fail('CIRCLE_OWNER_LEAVE');
      db.prepare('DELETE FROM circles WHERE id=?').run(circleId);
      return { ok: true, deleted: true };
    }
    db.prepare('DELETE FROM circle_members WHERE circle_id=? AND user_id=?').run(circleId, userId);
    return { ok: true, deleted: false };
  }

  function removeMember(userId, circleId, targetId) {
    if (membership(userId, circleId) !== 'owner') fail('FORBIDDEN');
    if (targetId === userId) fail('INVALID_INPUT');
    if (!memberRow.get(circleId, targetId)) fail('MEMBER_NOT_FOUND');
    db.prepare('DELETE FROM circle_members WHERE circle_id=? AND user_id=?').run(circleId, targetId);
    return { ok: true };
  }

  function postJson(row, userId) {
    const liked = likedBy.get(row.id, userId);
    const commentCount = commentTotal.get(row.id).n;
    const latest = latestComments.all(row.id, LIMITS.previewComments);
    return {
      id: row.id,
      text: row.text,
      receipt: row.receipt ? JSON.parse(row.receipt) : null,
      createdAt: row.created_at,
      author: { id: row.author_id, username: row.username },
      likeCount: likesOf.get(row.id).n,
      likedByMe: Boolean(liked),
      commentCount,
      // Oldest first inside the preview so a client can append the next page without reversing.
      comments: latest.reverse().map(comment => ({
        id: comment.id, text: comment.text, createdAt: comment.created_at,
        author: { id: comment.author_id, username: comment.username },
      })),
    };
  }

  function feed(userId, circleId, before, limit) {
    membership(userId, circleId);
    const size = pageLimit(limit, LIMITS.defaultPostsPage, LIMITS.postsPage);
    const cursor = decodeCursor(before ?? null);
    const rows = cursor
      ? db.prepare(`SELECT p.rowid AS rid, p.*, u.username FROM circle_posts p JOIN users u ON u.id=p.author_id
          WHERE p.circle_id=? AND p.deleted=0 AND (p.created_at<? OR (p.created_at=? AND p.rowid<?))
          ORDER BY p.created_at DESC, p.rowid DESC LIMIT ?`).all(circleId, cursor.at, cursor.at, cursor.rid, size + 1)
      : db.prepare(`SELECT p.rowid AS rid, p.*, u.username FROM circle_posts p JOIN users u ON u.id=p.author_id
          WHERE p.circle_id=? AND p.deleted=0 ORDER BY p.created_at DESC, p.rowid DESC LIMIT ?`).all(circleId, size + 1);
    const page = rows.slice(0, size);
    return {
      posts: page.map(row => postJson(row, userId)),
      nextCursor: rows.length > size ? encodeCursor(page.at(-1).created_at, page.at(-1).rid) : null,
    };
  }

  function post(userId, circleId, body) {
    membership(userId, circleId);
    const text = body.text === undefined || body.text === null ? '' : trimmed(body.text, LIMITS.text, { allowEmpty: true });
    let receipt = null;
    if (body.receipt !== undefined && body.receipt !== null) {
      const parsed = validReceipt(body.receipt);
      if (parsed.reason) fail(parsed.reason);
      receipt = JSON.stringify(parsed.value);
    }
    if (!text && !receipt) fail('POST_EMPTY');
    const id = randomUUID(); const at = now();
    db.prepare('INSERT INTO circle_posts(id,circle_id,author_id,text,receipt,created_at,deleted) VALUES (?,?,?,?,?,?,0)')
      .run(id, circleId, userId, text, receipt, at);
    return postJson(postWithAuthor.get(id), userId);
  }

  function removePost(userId, postId) {
    const row = livePost(userId, postId);
    if (row.author_id !== userId) fail('FORBIDDEN');
    db.prepare('UPDATE circle_posts SET deleted=1 WHERE id=?').run(postId);
    return { ok: true };
  }

  function like(userId, postId) {
    livePost(userId, postId);
    db.prepare('INSERT OR IGNORE INTO post_likes(post_id,user_id,created_at) VALUES (?,?,?)').run(postId, userId, now());
    return { liked: true, likeCount: likesOf.get(postId).n };
  }

  function unlike(userId, postId) {
    livePost(userId, postId);
    db.prepare('DELETE FROM post_likes WHERE post_id=? AND user_id=?').run(postId, userId);
    return { liked: false, likeCount: likesOf.get(postId).n };
  }

  const commentJson = row => ({
    id: row.id, text: row.text, createdAt: row.created_at, author: { id: row.author_id, username: row.username },
  });

  function comments(userId, postId, before, limit) {
    livePost(userId, postId);
    const size = pageLimit(limit, LIMITS.defaultCommentsPage, LIMITS.commentsPage);
    const cursor = decodeCursor(before ?? null);
    const rows = cursor
      ? db.prepare(`SELECT c.rowid AS rid, c.*, u.username FROM post_comments c JOIN users u ON u.id=c.author_id
          WHERE c.post_id=? AND c.deleted=0 AND (c.created_at<? OR (c.created_at=? AND c.rowid<?))
          ORDER BY c.created_at DESC, c.rowid DESC LIMIT ?`).all(postId, cursor.at, cursor.at, cursor.rid, size + 1)
      : db.prepare(`SELECT c.rowid AS rid, c.*, u.username FROM post_comments c JOIN users u ON u.id=c.author_id
          WHERE c.post_id=? AND c.deleted=0 ORDER BY c.created_at DESC, c.rowid DESC LIMIT ?`).all(postId, size + 1);
    const page = rows.slice(0, size);
    return {
      comments: page.map(commentJson),
      nextCursor: rows.length > size ? encodeCursor(page.at(-1).created_at, page.at(-1).rid) : null,
    };
  }

  function comment(userId, postId, body) {
    livePost(userId, postId);
    const text = trimmed(body.text, LIMITS.comment);
    const id = randomUUID(); const at = now();
    db.prepare('INSERT INTO post_comments(id,post_id,author_id,text,created_at,deleted) VALUES (?,?,?,?,?,0)')
      .run(id, postId, userId, text, at);
    return { comment: commentJson(db.prepare(`SELECT c.id, c.text, c.created_at, c.author_id, u.username FROM post_comments c
      JOIN users u ON u.id=c.author_id WHERE c.id=?`).get(id)) };
  }

  function removeComment(userId, commentId) {
    const row = commentRow.get(commentId);
    if (!row || row.deleted) fail('POST_NOT_FOUND');
    livePost(userId, row.post_id);
    if (row.author_id !== userId) fail('FORBIDDEN');
    db.prepare('UPDATE post_comments SET deleted=1 WHERE id=?').run(commentId);
    return { ok: true };
  }

  return { create, list, members, invite, join, leave, removeMember, feed, post, removePost, like, unlike, comments, comment, removeComment };
}

export { validReceipt };
