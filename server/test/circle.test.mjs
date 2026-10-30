import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomBytes, randomUUID } from 'node:crypto';
import { store, hash } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';

/** Real HTTP, in-memory SQLite, a mail box instead of SMTP — same fixture shape as ledger.test.mjs. */
async function fixture(t) {
  const db = store(':memory:'); const mail = [];
  const app = createApp({ db, sendMail: async item => mail.push(item), agent: async state => state });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function request(path, { method = 'GET', body, token } = {}) {
    const response = await fetch(url + path, {
      method,
      headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { response, data: await response.json().catch(() => null) };
  }
  return { db, mail, request };
}

let seq = 0;
const newAccount = () => ({
  email: `person${++seq}@example.test`, username: `person${seq}`,
  password: 'A long test passphrase 123', client: 'native',
});

/** Registered but unverified: the 403 path. */
async function unverified(f) {
  const account = newAccount();
  const result = await f.request('/v1/auth/register', { method: 'POST', body: account });
  assert.equal(result.response.status, 201);
  return { token: result.data.token, id: result.data.user.id, username: account.username };
}

async function verified(f) {
  const person = await unverified(f);
  assert.equal((await f.request('/v1/auth/verify', { method: 'POST', body: { code: f.mail.at(-1).code }, token: person.token })).response.status, 200);
  return person;
}

/** Bulk users skip scrypt: only the session lookup matters when 50 accounts have to exist. */
function seeded(db, name) {
  const id = randomUUID(); const token = randomBytes(32).toString('base64url');
  db.prepare('INSERT INTO users VALUES (?,?,?,?,1,?)').run(id, `${name}@example.test`, name, 'x:y', Date.now());
  db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(hash(token), id, Date.now() + 86400_000);
  return { id, token, username: name };
}

const auth = token => ({ token });

async function circleFor(f, token, name = '家人') {
  const created = await f.request('/v1/circles', { method: 'POST', body: { name }, token });
  assert.equal(created.response.status, 201);
  return created.data.circle;
}

async function inviteFor(f, token, circleId) {
  const result = await f.request(`/v1/circles/${circleId}/invites`, { method: 'POST', token });
  assert.equal(result.response.status, 201);
  return result.data;
}

async function join(f, token, code) {
  return f.request('/v1/circles/join', { method: 'POST', body: { code }, token });
}

const receipt = (over = {}) => ({
  date: '2026-10-29', totalCents: 8350, title: '午饭自由人', count: 3, comment: '今天三笔，都是饭。', ...over,
});

test('circles need a session (401) and a verified email (403)', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/v1/circles')).response.status, 401);
  const { token } = await unverified(f);
  assert.equal((await f.request('/v1/circles', auth(token))).response.status, 403);
  assert.equal((await f.request('/v1/circles', { method: 'POST', body: { name: '家人' }, token })).response.status, 403);
});

test('a non-member gets 404 for every circle resource, never 403', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  const posted = await f.request(`/v1/circles/${circle.id}/posts`, { method: 'POST', body: { text: '今天降温' }, token: a.token });
  assert.equal(posted.response.status, 201);
  const postId = posted.data.post.id;

  const probes = [
    ['GET', `/v1/circles/${circle.id}/feed`],
    ['GET', `/v1/circles/${circle.id}/members`],
    ['POST', `/v1/circles/${circle.id}/posts`, { text: '偷看' }],
    ['POST', `/v1/circles/${circle.id}/invites`],
    ['POST', `/v1/circles/${circle.id}/leave`],
    ['PUT', `/v1/posts/${postId}/like`],
    ['GET', `/v1/posts/${postId}/comments`],
    ['POST', `/v1/posts/${postId}/comments`, { text: '路过' }],
    ['DELETE', `/v1/posts/${postId}`],
  ];
  for (const [method, path, body] of probes) {
    const result = await f.request(path, { method, body, token: b.token });
    assert.equal(result.response.status, 404, `${method} ${path}`);
    assert.ok(['CIRCLE_NOT_FOUND', 'POST_NOT_FOUND'].includes(result.data.error), `${method} ${path} → ${result.data.error}`);
  }
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed`, auth(a.token))).response.status, 200);
});

test('invite codes: wrong, expired and used-up codes are refused; joining twice is harmless', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  const invite = await inviteFor(f, a.token, circle.id);
  assert.match(invite.code, /^[A-HJ-NP-Z2-9]{8}$/);
  assert.equal(invite.maxUses, 20);
  assert.equal(invite.expiresAt > Date.now() + 6 * 86400_000, true);

  assert.equal((await join(f, b.token, 'ZZZZZZZZ')).response.status, 400);
  assert.equal((await join(f, b.token, 'ZZZZZZZZ')).data.error, 'INVITE_INVALID');

  const joined = await join(f, b.token, invite.code.toLowerCase());
  assert.equal(joined.response.status, 200);
  assert.equal(joined.data.circle.memberCount, 2);
  assert.equal(joined.data.circle.role, 'member');
  const again = await join(f, b.token, invite.code);
  assert.equal(again.response.status, 200);
  assert.equal(f.db.prepare('SELECT uses FROM circle_invites WHERE code=?').get(invite.code).uses, 1);

  f.db.prepare('UPDATE circle_invites SET expires_at=? WHERE code=?').run(Date.now() - 1, invite.code);
  const c = await verified(f);
  assert.equal((await join(f, c.token, invite.code)).response.status, 400);

  const fresh = await inviteFor(f, a.token, circle.id);
  f.db.prepare('UPDATE circle_invites SET uses=max_uses WHERE code=?').run(fresh.code);
  assert.equal((await join(f, c.token, fresh.code)).data.error, 'INVITE_INVALID');
  assert.equal((await f.request(`/v1/circles/${circle.id}/members`, auth(a.token))).data.count, 2);
});

test('a circle stops at 50 members and a person stops at 10 circles', async t => {
  const f = await fixture(t);
  const owner = seeded(f.db, 'bulkowner');
  const circle = await circleFor(f, owner.token, '同事');
  await inviteFor(f, owner.token, circle.id);
  f.db.prepare('UPDATE circle_invites SET max_uses=100').run();

  for (let index = 0; index < 49; index++) {
    const member = seeded(f.db, `bulkmember${index}`);
    const code = f.db.prepare('SELECT code FROM circle_invites').get().code;
    const result = await join(f, member.token, code);
    assert.equal(result.response.status, 200, `member ${index}`);
  }
  assert.equal((await f.request(`/v1/circles/${circle.id}/members`, auth(owner.token))).data.count, 50);
  const late = seeded(f.db, 'bulklate');
  assert.equal((await join(f, late.token, f.db.prepare('SELECT code FROM circle_invites').get().code)).data.error, 'CIRCLE_FULL');

  const busy = seeded(f.db, 'busy');
  for (let index = 0; index < 10; index++) await circleFor(f, busy.token, `圈${index}`);
  const eleventh = await f.request('/v1/circles', { method: 'POST', body: { name: '第十一个' }, token: busy.token });
  assert.equal(eleventh.response.status, 409);
  assert.equal(eleventh.data.error, 'CIRCLE_LIMIT');
  assert.equal((await join(f, busy.token, f.db.prepare('SELECT code FROM circle_invites').get().code)).data.error, 'CIRCLE_LIMIT');
});

test('the feed is newest first, pages by cursor and drops soft-deleted posts', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  await join(f, b.token, (await inviteFor(f, a.token, circle.id)).code);

  const ids = [];
  for (const text of ['第一', '第二', '第三', '第四', '第五']) {
    const result = await f.request(`/v1/circles/${circle.id}/posts`, { method: 'POST', body: { text }, token: a.token });
    assert.equal(result.response.status, 201);
    ids.push(result.data.post.id);
  }
  const first = await f.request(`/v1/circles/${circle.id}/feed?limit=2`, auth(b.token));
  assert.deepEqual(first.data.posts.map(post => post.text), ['第五', '第四']);
  assert.equal(typeof first.data.nextCursor, 'string');
  const second = await f.request(`/v1/circles/${circle.id}/feed?limit=2&before=${first.data.nextCursor}`, auth(b.token));
  assert.deepEqual(second.data.posts.map(post => post.text), ['第三', '第二']);
  const third = await f.request(`/v1/circles/${circle.id}/feed?limit=2&before=${second.data.nextCursor}`, auth(b.token));
  assert.deepEqual(third.data.posts.map(post => post.text), ['第一']);
  assert.equal(third.data.nextCursor, null);
  const seen = [...first.data.posts, ...second.data.posts, ...third.data.posts].map(post => post.id);
  assert.equal(new Set(seen).size, 5);
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed?limit=51`, auth(b.token))).response.status, 400);
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed?before=not-a-cursor`, auth(b.token))).response.status, 400);
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed?limit=0`, auth(b.token))).response.status, 400);

  // Only the author may delete; a member gets 403, and the post disappears from the feed for everyone.
  assert.equal((await f.request(`/v1/posts/${ids[2]}`, { method: 'DELETE', token: b.token })).response.status, 403);
  assert.equal((await f.request(`/v1/posts/${ids[2]}`, { method: 'DELETE', token: a.token })).response.status, 200);
  assert.equal((await f.request(`/v1/posts/${ids[2]}`, { method: 'DELETE', token: a.token })).response.status, 404);
  const after = await f.request(`/v1/circles/${circle.id}/feed?limit=50`, auth(b.token));
  assert.deepEqual(after.data.posts.map(post => post.id), [ids[4], ids[3], ids[1], ids[0]]);
  assert.equal((await f.request(`/v1/posts/${ids[2]}/comments`, auth(b.token))).response.status, 404);
});

test('likes are idempotent and the count matches the number of people', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  await join(f, b.token, (await inviteFor(f, a.token, circle.id)).code);
  const post = (await f.request(`/v1/circles/${circle.id}/posts`, { method: 'POST', body: { text: '看这个' }, token: a.token })).data.post;

  const like = () => f.request(`/v1/posts/${post.id}/like`, { method: 'PUT', token: b.token });
  assert.deepEqual((await like()).data, { liked: true, likeCount: 1 });
  assert.deepEqual((await like()).data, { liked: true, likeCount: 1 });
  const mine = await f.request(`/v1/circles/${circle.id}/feed`, auth(b.token));
  assert.equal(mine.data.posts[0].likedByMe, true);
  const theirs = await f.request(`/v1/circles/${circle.id}/feed`, auth(a.token));
  assert.equal(theirs.data.posts[0].likedByMe, false);
  assert.equal(theirs.data.posts[0].likeCount, 1);

  const unlike = () => f.request(`/v1/posts/${post.id}/like`, { method: 'DELETE', token: b.token });
  assert.deepEqual((await unlike()).data, { liked: false, likeCount: 0 });
  assert.deepEqual((await unlike()).data, { liked: false, likeCount: 0 });
});

test('comments: paginated, counted, previewed and deletable only by their author', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  await join(f, b.token, (await inviteFor(f, a.token, circle.id)).code);
  const post = (await f.request(`/v1/circles/${circle.id}/posts`, { method: 'POST', body: { text: '周末去哪' }, token: a.token })).data.post;

  const ids = [];
  for (const text of ['去爬山', '去海边', '去睡觉', '去吃饭']) {
    const result = await f.request(`/v1/posts/${post.id}/comments`, { method: 'POST', body: { text }, token: b.token });
    assert.equal(result.response.status, 201);
    assert.equal(result.data.comment.author.username, b.username);
    ids.push(result.data.comment.id);
  }
  assert.equal((await f.request(`/v1/posts/${post.id}/comments`, { method: 'POST', body: { text: '   ' }, token: b.token })).response.status, 400);
  assert.equal((await f.request(`/v1/posts/${post.id}/comments`, { method: 'POST', body: { text: '长'.repeat(201) }, token: b.token })).response.status, 400);

  const page = await f.request(`/v1/posts/${post.id}/comments?limit=2`, auth(a.token));
  assert.deepEqual(page.data.comments.map(comment => comment.text), ['去吃饭', '去睡觉']);
  assert.equal(typeof page.data.nextCursor, 'string');
  const rest = await f.request(`/v1/posts/${post.id}/comments?limit=2&before=${page.data.nextCursor}`, auth(a.token));
  assert.deepEqual(rest.data.comments.map(comment => comment.text), ['去海边', '去爬山']);
  assert.equal(rest.data.nextCursor, null);

  const feed = await f.request(`/v1/circles/${circle.id}/feed`, auth(a.token));
  assert.equal(feed.data.posts[0].commentCount, 4);
  assert.deepEqual(feed.data.posts[0].comments.map(comment => comment.text), ['去海边', '去睡觉', '去吃饭']);

  assert.equal((await f.request(`/v1/comments/${ids[0]}`, { method: 'DELETE', token: a.token })).response.status, 403);
  assert.equal((await f.request(`/v1/comments/${ids[0]}`, { method: 'DELETE', token: b.token })).response.status, 200);
  assert.equal((await f.request(`/v1/comments/${ids[0]}`, { method: 'DELETE', token: b.token })).response.status, 404);
  const shrunk = await f.request(`/v1/posts/${post.id}/comments`, auth(a.token));
  assert.deepEqual(shrunk.data.comments.map(comment => comment.text), ['去吃饭', '去睡觉', '去海边']);
});

test('the receipt card is a strict whitelist: lines are opt-in and bill text is refused', async t => {
  const f = await fixture(t);
  const a = await verified(f);
  const circle = await circleFor(f, a.token);
  const create = body => f.request(`/v1/circles/${circle.id}/posts`, { method: 'POST', body, token: a.token });

  const small = await create({ receipt: receipt() });
  assert.equal(small.response.status, 201);
  assert.deepEqual(small.data.post.receipt, receipt());
  assert.equal(small.data.post.text, '');

  const lines = Array.from({ length: 30 }, (_, index) => ({ label: `第${index}笔`, amountCents: 1000 + index }));
  const detailed = await create({ text: '今天的大头都在这里', receipt: receipt({ lines }) });
  assert.equal(detailed.response.status, 201);
  assert.equal(detailed.data.post.receipt.lines.length, 30);

  const refused = [
    [receipt({ rawText: '支付宝-老乡鸡-35.00' }), 'RECEIPT_RAW_TEXT'],
    [receipt({ notification: '微信支付收款' }), 'RECEIPT_RAW_TEXT'],
    [receipt({ screenshot: 'data:image/png;base64,xx' }), 'RECEIPT_RAW_TEXT'],
    [receipt({ lines: [{ label: '午饭', amountCents: 3500, merchant: '老乡鸡' }] }), 'RECEIPT_RAW_TEXT'],
    [receipt({ merchant: '老乡鸡' }), 'RECEIPT_RAW_TEXT'],
    [receipt({ totalCentsX: 1 }), 'RECEIPT_FIELD'],
    [receipt({ title: '这个头衔实在是太长了根本放不进小票' }), 'RECEIPT_INVALID'],
    [receipt({ totalCents: 83.5 }), 'RECEIPT_INVALID'],
    [receipt({ totalCents: -1 }), 'RECEIPT_INVALID'],
    [receipt({ count: 1.5 }), 'RECEIPT_INVALID'],
    [receipt({ date: '2026-02-31' }), 'RECEIPT_INVALID'],
    [receipt({ date: '10/29' }), 'RECEIPT_INVALID'],
    [receipt({ comment: '啰'.repeat(61) }), 'RECEIPT_INVALID'],
    [receipt({ lines: [...lines, { label: '第31笔', amountCents: 1 }] }), 'RECEIPT_INVALID'],
    [receipt({ lines: [{ label: '超长的一行标签在这里真的放不下呀哈', amountCents: 1 }] }), 'RECEIPT_INVALID'],
    [[], 'RECEIPT_INVALID'],
  ];
  for (const [card, reason] of refused) {
    const result = await create({ receipt: card });
    assert.equal(result.response.status, 400, JSON.stringify(card));
    assert.equal(result.data.error, reason, JSON.stringify(card));
  }
  assert.equal((await create({ receipt: '一张小票图片' })).response.status, 400);
  assert.equal((await create({})).data.error, 'POST_EMPTY');
  assert.equal((await create({ text: '  ' })).data.error, 'POST_EMPTY');
  assert.equal((await create({ text: '长'.repeat(501) })).response.status, 400);
});

test('owner can remove members; leaving empties and deletes an owner-only circle', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  const invite = await inviteFor(f, a.token, circle.id);
  await join(f, b.token, invite.code);
  const bId = f.db.prepare('SELECT user_id FROM circle_members WHERE role=?').get('member').user_id;

  assert.equal((await f.request(`/v1/circles/${circle.id}/members/${bId}`, { method: 'DELETE', token: b.token })).response.status, 403);
  assert.equal((await f.request(`/v1/circles/${circle.id}/members/${bId}`, { method: 'DELETE', token: a.token })).response.status, 200);
  assert.equal((await f.request(`/v1/circles/${circle.id}/members`, auth(a.token))).data.count, 1);
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed`, auth(b.token))).response.status, 404);

  assert.equal((await join(f, b.token, invite.code)).response.status, 200);
  assert.equal((await f.request(`/v1/circles/${circle.id}/leave`, { method: 'POST', token: a.token })).data.error, 'CIRCLE_OWNER_LEAVE');
  assert.deepEqual((await f.request(`/v1/circles/${circle.id}/leave`, { method: 'POST', token: b.token })).data, { ok: true, deleted: false });
  assert.equal((await f.request('/v1/circles', auth(b.token))).data.circles.length, 0);
  assert.deepEqual((await f.request(`/v1/circles/${circle.id}/leave`, { method: 'POST', token: a.token })).data, { ok: true, deleted: true });
  assert.equal((await f.request('/v1/circles', auth(a.token))).data.circles.length, 0);
});

test('my circles carry my role and the member count, and no email ever leaves', async t => {
  const f = await fixture(t);
  const a = await verified(f); const b = await verified(f);
  const circle = await circleFor(f, a.token);
  await join(f, b.token, (await inviteFor(f, a.token, circle.id)).code);
  await f.request(`/v1/circles/${circle.id}/posts`, { method: 'POST', body: { text: '吃饭了', receipt: receipt() }, token: a.token });

  const mine = await f.request('/v1/circles', auth(b.token));
  assert.deepEqual(mine.data.circles.map(item => [item.name, item.role, item.memberCount]), [['家人', 'member', 2]]);
  const theirs = await f.request('/v1/circles', auth(a.token));
  assert.equal(theirs.data.circles[0].role, 'owner');

  const feed = await f.request(`/v1/circles/${circle.id}/feed`, auth(b.token));
  assert.deepEqual(Object.keys(feed.data.posts[0].author).sort(), ['id', 'username']);
  assert.equal(JSON.stringify(feed.data).includes('@example.test'), false);
  assert.equal(JSON.stringify((await f.request(`/v1/circles/${circle.id}/members`, auth(b.token))).data).includes('@example.test'), false);
});
