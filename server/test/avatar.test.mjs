import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomBytes } from 'node:crypto';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';

/** Real HTTP, in-memory SQLite, a mail box instead of SMTP — same fixture as circle.test.mjs. */
async function fixture(t) {
  const db = store(':memory:'); const mail = [];
  const app = createApp({ db, sendMail: async item => mail.push(item), agent: async state => state });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function raw(path, { method = 'GET', body, token } = {}) {
    const response = await fetch(url + path, {
      method,
      headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { response, bytes: Buffer.from(await response.arrayBuffer()) };
  }
  async function request(path, options) {
    const { response, bytes } = await raw(path, options);
    let data = null;
    try { data = JSON.parse(bytes.toString('utf8')); } catch { /* image bytes */ }
    return { response, data };
  }
  return { db, mail, request, raw };
}

let seq = 0;
const account = () => ({
  email: `face${++seq}@example.test`, username: `face${seq}`,
  password: 'A long test passphrase 123', client: 'native',
});

async function verified(f) {
  const person = account();
  const registered = await f.request('/v1/auth/register', { method: 'POST', body: person });
  assert.equal(registered.response.status, 201);
  const token = registered.data.token;
  const confirmed = await f.request('/v1/auth/verify', { method: 'POST', body: { code: f.mail.at(-1).code }, token });
  assert.equal(confirmed.response.status, 200);
  return { token, id: registered.data.user.id, username: person.username, password: person.password };
}

/** Real 1×1 PNG, plus hand-built headers for the other accepted types: the server trusts magic bytes only. */
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
const png = (extra = 0) => Buffer.concat([PNG, Buffer.alloc(extra, 0x00)]);
const jpeg = (extra = 0) => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01]), Buffer.alloc(extra, 0x11)]);
const webp = (extra = 0) => Buffer.concat([
  Buffer.from('RIFF'), Buffer.from([0x1a, 0x00, 0x00, 0x00]), Buffer.from('WEBPVP8 '), Buffer.alloc(4 + extra, 0x00),
]);
const gif = () => Buffer.concat([Buffer.from('GIF89a'), Buffer.alloc(8, 0x00)]);

const putAvatar = (f, token, payload) => f.request('/v1/auth/avatar', { method: 'PUT', body: payload, token });

test('a preset is the avatar every client reads back', async t => {
  const f = await fixture(t);
  const me = await verified(f);

  assert.equal((await f.request('/v1/auth/me', { token: me.token })).data.user.avatar, null);

  const set = await putAvatar(f, me.token, { preset: 'p3' });
  assert.equal(set.response.status, 200);
  assert.deepEqual(set.data.user.avatar, { preset: 'p3' });
  assert.deepEqual((await f.request('/v1/auth/me', { token: me.token })).data.user.avatar, { preset: 'p3' });

  const login = await f.request('/v1/auth/login', {
    method: 'POST', body: { identifier: me.username, password: me.password, client: 'native' },
  });
  assert.equal(login.response.status, 200);
  assert.deepEqual(login.data.user.avatar, { preset: 'p3' }, 'login carries the avatar too');

  assert.equal((await f.request('/v1/avatars/' + me.id, { token: me.token })).response.status, 404,
    'a preset has no bytes to serve');
});

test('preset ids are p1…p12 and nothing else', async t => {
  const f = await fixture(t);
  const me = await verified(f);

  for (const preset of ['p0', 'p13', 'p01', 'P3', 'p1x', '', 3, null]) {
    const result = await putAvatar(f, me.token, { preset });
    assert.equal(result.response.status, 400, `preset ${JSON.stringify(preset)} must be refused`);
    assert.equal(result.data.error, 'AVATAR_INVALID');
  }
  assert.equal((await putAvatar(f, me.token, { preset: 'p12' })).response.status, 200);
  assert.equal((await putAvatar(f, me.token, { preset: 'p1' })).response.status, 200);
});

test('JPEG, PNG and WebP photos are stored under a cache-busting url', async t => {
  const f = await fixture(t);
  const me = await verified(f);

  for (const [bytes, mime] of [[jpeg(), 'image/jpeg'], [png(), 'image/png'], [webp(), 'image/webp']]) {
    const saved = await putAvatar(f, me.token, { image: bytes.toString('base64') });
    assert.equal(saved.response.status, 200);
    const url = saved.data.user.avatar.url;
    assert.match(url, new RegExp(`^/v1/avatars/${me.id}\\?v=\\d+$`));

    const fetched = await f.raw(url, { token: me.token });
    assert.equal(fetched.response.status, 200);
    assert.equal(fetched.response.headers.get('content-type'), mime);
    assert.equal(fetched.response.headers.get('cache-control'), 'private, max-age=86400');
    assert.equal(fetched.response.headers.get('x-content-type-options'), 'nosniff');
    assert.deepEqual(fetched.bytes, bytes);
  }
});

test('anything that is not a JPEG, PNG or WebP is refused', async t => {
  const f = await fixture(t);
  const me = await verified(f);

  for (const bytes of [gif(), randomBytes(512), Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>')]) {
    const result = await putAvatar(f, me.token, { image: bytes.toString('base64') });
    assert.equal(result.response.status, 400);
    assert.equal(result.data.error, 'AVATAR_INVALID');
  }
  assert.equal((await putAvatar(f, me.token, { image: 'data:image/png;base64,' + PNG.toString('base64') })).response.status, 400);
  assert.equal((await putAvatar(f, me.token, {})).response.status, 400, 'empty body');
  assert.equal((await putAvatar(f, me.token, { preset: 'p2', image: png().toString('base64') })).response.status, 400,
    'preset and image are mutually exclusive');
});

test('a photo over 256 KB is refused, and so is a body too large to be one', async t => {
  const f = await fixture(t);
  const me = await verified(f);

  const overLimit = await putAvatar(f, me.token, { image: png(256 * 1024).toString('base64') });
  assert.equal(overLimit.response.status, 413);
  assert.equal(overLimit.data.error, 'AVATAR_TOO_LARGE');

  const huge = await putAvatar(f, me.token, { image: png(400 * 1024).toString('base64') });
  assert.equal(huge.response.status, 413);
  assert.equal(huge.data.error, 'AVATAR_TOO_LARGE');

  const exactly = await putAvatar(f, me.token, { image: png(256 * 1024 - PNG.length).toString('base64') });
  assert.equal(exactly.response.status, 200, 'exactly 256 KiB is inside the limit');
  assert.equal((await f.request('/v1/auth/me', { token: me.token })).data.user.avatar.url.length > 0, true);
});

test('a preset replaces the photo, DELETE clears the avatar', async t => {
  const f = await fixture(t);
  const me = await verified(f);

  await putAvatar(f, me.token, { image: png().toString('base64') });
  const preset = await putAvatar(f, me.token, { preset: 'p5' });
  assert.deepEqual(preset.data.user.avatar, { preset: 'p5' });
  assert.equal((await f.request('/v1/avatars/' + me.id, { token: me.token })).response.status, 404,
    'the stored photo is gone, not shadowed');

  const cleared = await f.request('/v1/auth/avatar', { method: 'DELETE', token: me.token });
  assert.equal(cleared.response.status, 200);
  assert.equal(cleared.data.user.avatar, null);
  assert.equal((await f.request('/v1/auth/me', { token: me.token })).data.user.avatar, null);
});

test('a photo is visible to a circle mate and 404 to everyone else', async t => {
  const f = await fixture(t);
  const alice = await verified(f);
  const bob = await verified(f);
  const stranger = await verified(f);

  await putAvatar(f, alice.token, { preset: 'p7' });
  const circle = (await f.request('/v1/circles', { method: 'POST', body: { name: '家人' }, token: alice.token })).data.circle;
  const invite = (await f.request(`/v1/circles/${circle.id}/invites`, { method: 'POST', token: alice.token })).data;
  assert.equal((await f.request('/v1/circles/join', { method: 'POST', body: { code: invite.code }, token: bob.token })).response.status, 200);

  assert.equal((await f.request('/v1/avatars/' + alice.id, { token: bob.token })).response.status, 404,
    'a preset has no image, and leaks nothing about the account');
  await putAvatar(f, alice.token, { image: jpeg().toString('base64') });

  const mine = await f.raw('/v1/avatars/' + alice.id, { token: alice.token });
  assert.equal(mine.response.status, 200);

  const mate = await f.raw('/v1/avatars/' + alice.id, { token: bob.token });
  assert.equal(mate.response.status, 200, 'members of a shared circle can draw the face');
  assert.deepEqual(mate.bytes, jpeg());

  const foreign = await f.request('/v1/avatars/' + alice.id, { token: stranger.token });
  assert.equal(foreign.response.status, 404, 'never 403: whether an avatar exists is private');
  assert.equal(foreign.data.error, 'AVATAR_NOT_FOUND');

  assert.equal((await f.request('/v1/avatars/' + alice.id)).response.status, 401);

  // Leaving the circle takes the face away again.
  assert.equal((await f.request(`/v1/circles/${circle.id}/leave`, { method: 'POST', token: bob.token })).response.status, 200);
  assert.equal((await f.request('/v1/avatars/' + alice.id, { token: bob.token })).response.status, 404);
});

test('circle members carry the avatar, and unverified accounts cannot set one', async t => {
  const f = await fixture(t);
  const alice = await verified(f);
  const bob = await verified(f);
  await putAvatar(f, bob.token, { preset: 'p9' });

  const circle = (await f.request('/v1/circles', { method: 'POST', body: { name: '合租' }, token: alice.token })).data.circle;
  const invite = (await f.request(`/v1/circles/${circle.id}/invites`, { method: 'POST', token: alice.token })).data;
  await f.request('/v1/circles/join', { method: 'POST', body: { code: invite.code }, token: bob.token });

  const members = (await f.request(`/v1/circles/${circle.id}/members`, { token: alice.token })).data.members;
  const byId = Object.fromEntries(members.map(member => [member.id, member.avatar]));
  assert.deepEqual(byId[alice.id], null);
  assert.deepEqual(byId[bob.id], { preset: 'p9' });

  const pending = account();
  const registered = await f.request('/v1/auth/register', { method: 'POST', body: pending });
  assert.equal((await putAvatar(f, registered.data.token, { preset: 'p1' })).response.status, 403);
  assert.equal(registered.data.user.avatar, null, 'registration answers with the field as well');
});
