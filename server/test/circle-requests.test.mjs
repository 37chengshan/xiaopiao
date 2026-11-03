import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { store, hash } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';

/** Real HTTP, in-memory SQLite, a mail box instead of SMTP — same fixture as circle.test.mjs. */
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
const account = () => ({
  email: `find${++seq}@example.test`, username: `find${seq}`,
  password: 'A long test passphrase 123', client: 'native',
});

async function verified(f) {
  const person = account();
  const registered = await f.request('/v1/auth/register', { method: 'POST', body: person });
  assert.equal(registered.response.status, 201);
  const token = registered.data.token;
  assert.equal((await f.request('/v1/auth/verify', { method: 'POST', body: { code: f.mail.at(-1).code }, token })).response.status, 200);
  return { token, id: registered.data.user.id, username: person.username };
}

/** Bulk members skip scrypt: only the circle tables matter once 50 people have to exist. */
function seeded(db, name) {
  const id = randomUUID(); const token = randomBytes(32).toString('base64url');
  db.prepare('INSERT INTO users (id,email,username,password,verified,created) VALUES (?,?,?,?,1,?)').run(id, `${name}@example.test`, name, 'x:y', Date.now());
  db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(hash(token), id, Date.now() + 86400_000);
  return { id, token, username: name };
}

const created = async (f, token, name = '家人') => {
  const result = await f.request('/v1/circles', { method: 'POST', body: { name }, token });
  assert.equal(result.response.status, 201);
  return result.data.circle;
};
const invite = async (f, token, circleId) =>
  (await f.request(`/v1/circles/${circleId}/invites`, { method: 'POST', token })).data;
const joinByCode = (f, token, code) => f.request('/v1/circles/join', { method: 'POST', body: { code }, token });
const search = (f, token, no) => f.request(`/v1/circles/search?no=${no}`, { token });
const ask = (f, token, no) => f.request('/v1/circles/requests', { method: 'POST', body: { no }, token });
const pending = (f, token, circleId) => f.request(`/v1/circles/${circleId}/requests`, { token });
const decide = (f, token, circleId, userId, verb) =>
  f.request(`/v1/circles/${circleId}/requests/${userId}/${verb}`, { method: 'POST', token });

test('every circle gets one stable 7-digit number that never starts with zero', async t => {
  const f = await fixture(t);
  const owner = await verified(f);

  const numbers = new Set();
  for (const name of ['家人', '合租', '两个人']) {
    const circle = await created(f, owner.token, name);
    assert.match(circle.no, /^[1-9]\d{6}$/, `number ${circle.no} is 7 digits without a leading zero`);
    assert.equal(typeof circle.no, 'string');
    assert.equal(numbers.has(circle.no), false, 'numbers are unique');
    numbers.add(circle.no);
  }

  // The same number is handed back by list(), and it does not change on later reads.
  const listed = (await f.request('/v1/circles', { token: owner.token })).data.circles;
  assert.deepEqual(new Set(listed.map(circle => circle.no)), numbers);
  assert.deepEqual((await f.request('/v1/circles', { token: owner.token })).data.circles.map(circle => circle.no).sort(),
    listed.map(circle => circle.no).sort());
});

test('searching a number answers member / pending / none and leaks nothing else', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const mate = await verified(f);
  const stranger = await verified(f);
  const circle = await created(f, owner.token, '家人');

  assert.equal((await joinByCode(f, mate.token, (await invite(f, owner.token, circle.id)).code)).response.status, 200);

  const mine = await search(f, owner.token, circle.no);
  assert.equal(mine.response.status, 200);
  assert.deepEqual(mine.data, { circle: { no: circle.no, name: '家人', memberCount: 2, status: 'member' } });
  assert.equal((await search(f, mate.token, circle.no)).data.circle.status, 'member');

  const unknownToMe = await search(f, stranger.token, circle.no);
  assert.deepEqual(unknownToMe.data, { circle: { no: circle.no, name: '家人', memberCount: 2, status: 'none' } });
  assert.equal('id' in unknownToMe.data.circle, false, 'no circle id outside the circle');
  assert.equal('members' in unknownToMe.data.circle, false);
  assert.equal('posts' in unknownToMe.data.circle, false);

  await ask(f, stranger.token, circle.no);
  assert.equal((await search(f, stranger.token, circle.no)).data.circle.status, 'pending');

  const missing = await search(f, stranger.token, '9999999');
  assert.equal(missing.response.status, 404);
  assert.equal(missing.data.error, 'CIRCLE_NOT_FOUND');

  for (const bad of ['123456', '0123456', 'abcdefg', '']) {
    const malformed = await search(f, stranger.token, bad);
    assert.equal(malformed.response.status, 400, `${JSON.stringify(bad)} is not a circle number`);
    assert.equal(malformed.data.error, 'INVALID_INPUT');
  }
  assert.equal((await f.request('/v1/circles/search?no=' + circle.no)).response.status, 401);
});

test('asking to join waits for the owner, who then lets the person in', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const applicant = await verified(f);
  const circle = await created(f, owner.token, '家人');

  assert.equal((await f.request(`/v1/circles/${circle.id}/feed`, { token: applicant.token })).response.status, 404,
    'a number alone opens nothing');

  const asked = await ask(f, applicant.token, circle.no);
  assert.equal(asked.response.status, 201);
  assert.deepEqual(asked.data, { ok: true, status: 'pending' });

  // A second tap is idempotent and keeps the original asking time.
  const again = await ask(f, applicant.token, circle.no);
  assert.equal(again.response.status, 201);
  assert.deepEqual(again.data, { ok: true, status: 'pending' });
  const queue = (await pending(f, owner.token, circle.id)).data.requests;
  assert.equal(queue.length, 1);
  assert.deepEqual(queue[0], { userId: applicant.id, username: applicant.username, createdAt: queue[0].createdAt, avatar: null });

  // Waiting is still not a member.
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed`, { token: applicant.token })).response.status, 404);
  assert.equal((await search(f, applicant.token, circle.no)).data.circle.status, 'pending');

  const approved = await decide(f, owner.token, circle.id, applicant.id, 'approve');
  assert.equal(approved.response.status, 200);
  assert.deepEqual(approved.data, { ok: true });

  assert.deepEqual((await pending(f, owner.token, circle.id)).data.requests, []);
  assert.equal((await search(f, applicant.token, circle.no)).data.circle.status, 'member');
  const members = (await f.request(`/v1/circles/${circle.id}/members`, { token: owner.token })).data.members;
  assert.deepEqual(members.map(member => [member.id, member.role]), [[owner.id, 'owner'], [applicant.id, 'member']]);
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed`, { token: applicant.token })).response.status, 200,
    'the approved member reads the feed');
  assert.deepEqual((await ask(f, applicant.token, circle.no)).data, { ok: true, status: 'member' });

  assert.equal((await decide(f, owner.token, circle.id, applicant.id, 'approve')).response.status, 404,
    'the same request cannot be approved twice');
});

test('rejecting removes the ask and leaves the person outside', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const applicant = await verified(f);
  const circle = await created(f, owner.token, '大学室友');
  await ask(f, applicant.token, circle.no);

  const rejected = await decide(f, owner.token, circle.id, applicant.id, 'reject');
  assert.equal(rejected.response.status, 200);
  assert.deepEqual(rejected.data, { ok: true });
  assert.deepEqual((await pending(f, owner.token, circle.id)).data.requests, []);
  assert.equal((await search(f, applicant.token, circle.no)).data.circle.status, 'none');
  assert.equal((await f.request(`/v1/circles/${circle.id}/feed`, { token: applicant.token })).response.status, 404);
  assert.equal((await decide(f, owner.token, circle.id, applicant.id, 'reject')).response.status, 404);
});

test('only the owner sees and answers requests; a stranger cannot even ask about the circle', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const mate = await verified(f);
  const stranger = await verified(f);
  const circle = await created(f, owner.token, '家人');
  await joinByCode(f, mate.token, (await invite(f, owner.token, circle.id)).code);
  await ask(f, stranger.token, circle.no);

  const asMate = await pending(f, mate.token, circle.id);
  assert.equal(asMate.response.status, 403);
  assert.equal(asMate.data.error, 'FORBIDDEN');
  assert.equal((await decide(f, mate.token, circle.id, stranger.id, 'approve')).response.status, 403);
  assert.equal((await decide(f, mate.token, circle.id, stranger.id, 'reject')).response.status, 403);

  const asStranger = await pending(f, stranger.token, circle.id);
  assert.equal(asStranger.response.status, 404, 'a non-member is told the circle does not exist');
  assert.equal(asStranger.data.error, 'CIRCLE_NOT_FOUND');
  assert.equal((await decide(f, stranger.token, circle.id, stranger.id, 'approve')).response.status, 404);

  assert.equal((await pending(f, owner.token, circle.id)).data.requests.length, 1, 'the ask is still waiting');
});

test('a circle that filled up between asking and approving refuses the approval', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const applicant = await verified(f);
  const circle = await created(f, owner.token, '家人');

  await ask(f, applicant.token, circle.no);
  // Fill the circle to its cap meanwhile: 50 including the owner.
  const insert = f.db.prepare("INSERT INTO circle_members(circle_id,user_id,role,joined_at) VALUES (?,?,'member',?)");
  for (let index = 0; index < 49; index++) insert.run(circle.id, seeded(f.db, `bulk${index}`).id, Date.now());

  const full = await decide(f, owner.token, circle.id, applicant.id, 'approve');
  assert.equal(full.response.status, 409);
  assert.equal(full.data.error, 'CIRCLE_FULL');
  assert.equal((await pending(f, owner.token, circle.id)).data.requests.length, 1, 'the ask stays for later');

  const late = await verified(f);
  const refused = await ask(f, late.token, circle.no);
  assert.equal(refused.response.status, 409);
  assert.equal(refused.data.error, 'CIRCLE_FULL');
  assert.equal((await search(f, late.token, circle.no)).data.circle.status, 'none');
});

test('the circle list badges the owner with the number of waiting asks', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const mate = await verified(f);
  const first = await verified(f);
  const second = await verified(f);
  const mine = await created(f, owner.token, '家人');

  await joinByCode(f, mate.token, (await invite(f, owner.token, mine.id)).code);
  await ask(f, first.token, mine.no);
  await ask(f, second.token, mine.no);

  const owned = (await f.request('/v1/circles', { token: owner.token })).data.circles;
  assert.deepEqual(owned.map(circle => [circle.no, circle.pendingRequests]), [[mine.no, 2]]);

  const asMate = (await f.request('/v1/circles', { token: mate.token })).data.circles;
  assert.deepEqual(asMate.map(circle => [circle.no, circle.pendingRequests, circle.role]), [[mine.no, 0, 'member']]);

  await decide(f, owner.token, mine.id, first.id, 'approve');
  assert.deepEqual((await f.request('/v1/circles', { token: owner.token })).data.circles.map(circle => circle.pendingRequests), [1]);
});

test('deleting a circle takes its requests with it, and a member who leaves drops their ask', async t => {
  const f = await fixture(t);
  const owner = await verified(f);
  const mate = await verified(f);
  const applicant = await verified(f);
  const circle = await created(f, owner.token, '两个人');
  await joinByCode(f, mate.token, (await invite(f, owner.token, circle.id)).code);
  await ask(f, applicant.token, circle.no);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM circle_requests WHERE circle_id=?').get(circle.id).n, 1);

  // A pending ask from someone who already left the circle is dropped with them.
  f.db.prepare("INSERT INTO circle_requests(circle_id,user_id,created_at) VALUES (?,?,?)").run(circle.id, mate.id, Date.now());
  assert.equal((await f.request(`/v1/circles/${circle.id}/leave`, { method: 'POST', token: mate.token })).response.status, 200);
  assert.deepEqual(f.db.prepare('SELECT user_id FROM circle_requests WHERE circle_id=? ORDER BY user_id').all(circle.id).map(row => row.user_id),
    [applicant.id]);

  assert.deepEqual((await f.request(`/v1/circles/${circle.id}/leave`, { method: 'POST', token: owner.token })).data, { ok: true, deleted: true });
  assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM circle_requests').get().n, 0, 'no asks outlive the circle');
});

test('an existing database gets a number for every circle already in it', async t => {
  const directory = mkdtempSync(join(tmpdir(), 'xiaopiao-circles-'));
  const path = join(directory, 'old.sqlite');
  // The schema exactly as it shipped before numbers existed: no `no` column, no requests table.
  const old = new DatabaseSync(path);
  old.exec(`CREATE TABLE users (id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, username TEXT UNIQUE NOT NULL,
      password TEXT NOT NULL, verified INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL);
    CREATE TABLE circles (id TEXT PRIMARY KEY, name TEXT NOT NULL, owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at INTEGER NOT NULL);
    INSERT INTO users (id,email,username,password,verified,created) VALUES ('u1','old@example.test','old','x:y',1,1);
    INSERT INTO circles VALUES ('c1','家人','u1',1);
    INSERT INTO circles VALUES ('c2','室友','u1',2);`);
  old.close();
  t.after(() => rmSync(directory, { recursive: true, force: true }));

  const db = store(path);
  const rows = db.prepare('SELECT id, no FROM circles ORDER BY id').all();
  assert.deepEqual(rows.map(row => row.id), ['c1', 'c2']);
  for (const row of rows) assert.match(row.no, /^[1-9]\d{6}$/);
  assert.notEqual(rows[0].no, rows[1].no);
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM circle_requests').get().n, 0);

  // Reopening must not renumber anything.
  db.close();
  const reopened = store(path);
  assert.deepEqual(reopened.prepare('SELECT id, no FROM circles ORDER BY id').all().map(row => [row.id, row.no]),
    rows.map(row => [row.id, row.no]));
  reopened.close();
});
