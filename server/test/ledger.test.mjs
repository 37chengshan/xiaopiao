import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';

/** Same fixture shape as server.test.mjs: real HTTP, in-memory SQLite, a mail box instead of SMTP. */
async function fixture(t) {
  const db = store(':memory:'); const mail = [];
  const app = createApp({ db, sendMail: async item => mail.push(item), agent: async (state) => state });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function request(path, body, token) {
    const response = await fetch(url + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { response, data: await response.json().catch(() => null) };
  }
  return { db, mail, request };
}

let seqAccount = 0;
const newAccount = () => ({ email: `person${++seqAccount}@example.test`, username: `person${seqAccount}`, password: 'A long test passphrase 123', client: 'native' });

async function accountWith(f) {
  const account = newAccount();
  const registered = await f.request('/v1/auth/register', account);
  assert.equal(registered.response.status, 201);
  return { token: registered.data.token, account };
}

async function verified(f) {
  const { token, account } = await accountWith(f);
  await f.request('/v1/auth/verify', { code: f.mail.at(-1).code }, token);
  return token;
}

const txn = (id, over = {}) => ({
  id,
  amountCents: 3500,
  direction: 'OUT',
  merchant: '老乡鸡',
  category: '吃饭',
  occurredAt: 1_760_000_000_000,
  source: 'MANUAL',
  note: '',
  cardLast4: null,
  updatedAt: 1_760_000_000_500,
  deleted: false,
  ...over,
});
const category = (id, over = {}) => ({
  id, label: '宠物', iconKey: 'paw', hue: 'blue', direction: 'OUT',
  hidden: false, sortOrder: 3, updatedAt: 1_760_000_000_500, deleted: false, ...over,
});

test('ledger requires a session (401) and a verified email (403)', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/v1/ledger/push', { deviceId: 'd1', txns: [], categories: [] })).response.status, 401);
  assert.equal((await f.request('/v1/ledger/changes', undefined)).response.status, 401);

  const { token } = await accountWith(f);
  assert.equal((await f.request('/v1/ledger/push', { deviceId: 'd1', txns: [txn('a')], categories: [] }, token)).response.status, 403);
  assert.equal((await f.request('/v1/ledger/changes?since=0', undefined, token)).response.status, 403);
});

test('push then changes round-trips a structured transaction', async t => {
  const f = await fixture(t); const token = await verified(f);
  const pushed = await f.request('/v1/ledger/push', { deviceId: 'phone-1', txns: [txn('a')], categories: [category('c1')] }, token);
  assert.equal(pushed.response.status, 200);
  assert.deepEqual(pushed.data.applied, 2);
  assert.deepEqual(pushed.data.stale, []);
  assert.deepEqual(pushed.data.rejected, []);
  assert.equal(pushed.data.seq, 2);

  const pulled = await f.request('/v1/ledger/changes?since=0', undefined, token);
  assert.equal(pulled.data.txns.length, 1);
  assert.equal(pulled.data.categories.length, 1);
  assert.equal(pulled.data.nextSince, 2);
  assert.equal(pulled.data.hasMore, false);
  assert.deepEqual(pulled.data.txns[0], {
    id: 'a', amountCents: 3500, direction: 'OUT', merchant: '老乡鸡', category: '吃饭',
    occurredAt: 1_760_000_000_000, source: 'MANUAL', note: '', cardLast4: null,
    updatedAt: 1_760_000_000_500, deleted: false, deviceId: 'phone-1', seq: 1,
  });
  assert.equal(pulled.data.categories[0].seq, 2);
});

test('last write wins by updatedAt; equal timestamps let the later arrival win', async t => {
  const f = await fixture(t); const token = await verified(f);
  await f.request('/v1/ledger/push', { deviceId: 'a', txns: [txn('x', { amountCents: 100, updatedAt: 2000 })], categories: [] }, token);

  const older = await f.request('/v1/ledger/push', { deviceId: 'b', txns: [txn('x', { amountCents: 999, updatedAt: 1999 })], categories: [] }, token);
  assert.deepEqual(older.data.stale, [{ id: 'x', kind: 'txn' }]);
  assert.equal(older.data.applied, 0);
  assert.equal(f.db.prepare('SELECT amount_cents AS n FROM ledger_txns WHERE id=?').get('x').n, 100);

  const newer = await f.request('/v1/ledger/push', { deviceId: 'b', txns: [txn('x', { amountCents: 999, updatedAt: 2001 })], categories: [] }, token);
  assert.equal(newer.data.applied, 1);
  assert.equal(f.db.prepare('SELECT amount_cents AS n FROM ledger_txns WHERE id=?').get('x').n, 999);

  const same = await f.request('/v1/ledger/push', { deviceId: 'c', txns: [txn('x', { amountCents: 1234, updatedAt: 2001 })], categories: [] }, token);
  assert.equal(same.data.applied, 1);
  assert.equal(f.db.prepare('SELECT amount_cents AS n FROM ledger_txns WHERE id=?').get('x').n, 1234);
});

test('deleting is a tombstone that other devices pull, and it can be resurrected later', async t => {
  const f = await fixture(t); const token = await verified(f);
  await f.request('/v1/ledger/push', { deviceId: 'a', txns: [txn('gone')], categories: [] }, token);
  const removed = await f.request('/v1/ledger/push', { deviceId: 'b', txns: [txn('gone', { deleted: true, updatedAt: 1_760_000_000_900 })], categories: [] }, token);
  assert.equal(removed.data.applied, 1);

  const pulled = await f.request('/v1/ledger/changes?since=1', undefined, token);
  assert.equal(pulled.data.txns.length, 1);
  assert.equal(pulled.data.txns[0].deleted, true);
  assert.equal(pulled.data.txns[0].id, 'gone');

  const back = await f.request('/v1/ledger/push', { deviceId: 'a', txns: [txn('gone', { deleted: false, updatedAt: 1_760_000_001_000 })], categories: [] }, token);
  assert.equal(back.data.applied, 1);
  assert.equal((await f.request('/v1/ledger/changes?since=2', undefined, token)).data.txns[0].deleted, false);
});

test('changes pages by seq with nextSince and hasMore', async t => {
  const f = await fixture(t); const token = await verified(f);
  const txns = Array.from({ length: 5 }, (_, i) => txn(`t${i}`, { updatedAt: 2000 + i }));
  await f.request('/v1/ledger/push', { deviceId: 'a', txns, categories: [] }, token);

  const first = await f.request('/v1/ledger/changes?since=0&limit=2', undefined, token);
  assert.deepEqual(first.data.txns.map(row => row.id), ['t0', 't1']);
  assert.equal(first.data.nextSince, 2);
  assert.equal(first.data.hasMore, true);

  const second = await f.request(`/v1/ledger/changes?since=${first.data.nextSince}&limit=2`, undefined, token);
  assert.deepEqual(second.data.txns.map(row => row.id), ['t2', 't3']);
  const third = await f.request(`/v1/ledger/changes?since=${second.data.nextSince}&limit=2`, undefined, token);
  assert.deepEqual(third.data.txns.map(row => row.id), ['t4']);
  assert.equal(third.data.hasMore, false);
  assert.equal(third.data.nextSince, 5);

  const empty = await f.request('/v1/ledger/changes?since=5', undefined, token);
  assert.deepEqual(empty.data, { txns: [], categories: [], nextSince: 5, hasMore: false });

  assert.equal((await f.request('/v1/ledger/changes?since=abc', undefined, token)).response.status, 400);
  assert.equal((await f.request('/v1/ledger/changes?since=-1', undefined, token)).response.status, 400);
  assert.equal((await f.request('/v1/ledger/changes?limit=501', undefined, token)).response.status, 400);
});

test('one batch is capped at 500 items', async t => {
  const f = await fixture(t); const token = await verified(f);
  const over = await f.request('/v1/ledger/push', { deviceId: 'a', txns: Array.from({ length: 501 }, (_, i) => txn(`t${i}`)), categories: [] }, token);
  assert.equal(over.response.status, 400);
  assert.equal(over.data.error, 'LEDGER_TOO_MANY');

  const exactly = await f.request('/v1/ledger/push', { deviceId: 'a', txns: Array.from({ length: 500 }, (_, i) => txn(`t${i}`, { updatedAt: 3000 + i })), categories: [] }, token);
  assert.equal(exactly.response.status, 200);
  assert.equal(exactly.data.applied, 500);
});

test('unknown and raw-text fields reject the item, not the batch', async t => {
  const f = await fixture(t); const token = await verified(f);
  const result = await f.request('/v1/ledger/push', {
    deviceId: 'a',
    txns: [
      txn('ok'),
      txn('raw', { rawText: '支付宝 38.00 老乡鸡' }),
      txn('title', { title: '午饭' }),
      txn('text', { text: '一段小票原文' }),
      txn('weird', { colour: 'red' }),
    ],
    categories: [category('c-ok'), category('c-raw', { screenshot: 'data:image/png;base64,AAA' })],
  }, token);
  assert.equal(result.response.status, 200);
  assert.equal(result.data.applied, 2);
  assert.deepEqual(result.data.rejected, [
    { id: 'raw', kind: 'txn', reason: 'RAW_TEXT_FORBIDDEN' },
    { id: 'title', kind: 'txn', reason: 'RAW_TEXT_FORBIDDEN' },
    { id: 'text', kind: 'txn', reason: 'RAW_TEXT_FORBIDDEN' },
    { id: 'weird', kind: 'txn', reason: 'UNKNOWN_FIELD' },
    { id: 'c-raw', kind: 'category', reason: 'RAW_TEXT_FORBIDDEN' },
  ]);
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM ledger_txns').get().n, 1);
});

test('invalid money, direction, times and ids are rejected with reasons', async t => {
  const f = await fixture(t); const token = await verified(f);
  const result = await f.request('/v1/ledger/push', {
    deviceId: 'a',
    txns: [
      txn('zero', { amountCents: 0 }),
      txn('negative', { amountCents: -100 }),
      txn('float', { amountCents: 12.5 }),
      txn('huge', { amountCents: 10_000_000_001 }),
      txn('direction', { direction: 'SIDEWAYS' }),
      txn('time', { occurredAt: -1 }),
      txn('future', { occurredAt: 9_999_999_999_999 }),
      txn('updated', { updatedAt: 0 }),
      txn('card', { cardLast4: '12' }),
      txn('source', { source: 'manual' }),
      { id: '../escape', amountCents: 100, direction: 'OUT', occurredAt: 1, source: 'MANUAL', updatedAt: 1 },
      { amountCents: 100, direction: 'OUT', occurredAt: 1, source: 'MANUAL', updatedAt: 1 },
    ],
    categories: [category('c', { label: '' })],
  }, token);
  assert.equal(result.data.applied, 0);
  assert.deepEqual(result.data.rejected.map(row => [row.id, row.reason]), [
    ['zero', 'AMOUNT_INVALID'], ['negative', 'AMOUNT_INVALID'], ['float', 'AMOUNT_INVALID'], ['huge', 'AMOUNT_INVALID'],
    ['direction', 'DIRECTION_INVALID'], ['time', 'OCCURRED_AT_INVALID'], ['future', 'OCCURRED_AT_INVALID'],
    ['updated', 'UPDATED_AT_INVALID'], ['card', 'CARD_INVALID'], ['source', 'SOURCE_INVALID'],
    ['../escape', 'ID_INVALID'], [null, 'ID_INVALID'], ['c', 'LABEL_INVALID'],
  ]);
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM ledger_txns').get().n, 0);
});

test('one user never sees another user\'s rows or sequence', async t => {
  const f = await fixture(t);
  const alice = await verified(f);
  const bob = await verified(f);
  const aliceId = f.db.prepare('SELECT id FROM users ORDER BY created LIMIT 1').get().id;

  const a = await f.request('/v1/ledger/push', { deviceId: 'alice-phone', txns: [txn('shared-id'), txn('alice-only', { updatedAt: 2001 })], categories: [] }, alice);
  assert.equal(a.data.applied, 2);
  const b = await f.request('/v1/ledger/push', { deviceId: 'bob-phone', txns: [txn('shared-id', { merchant: '鲍伯的店' })], categories: [] }, bob);
  assert.equal(b.data.applied, 1);
  assert.equal(b.data.seq, 1, 'sequences are per user');

  const bobRows = await f.request('/v1/ledger/changes?since=0', undefined, bob);
  assert.deepEqual(bobRows.data.txns.map(row => row.id), ['shared-id']);
  assert.equal(bobRows.data.txns[0].merchant, '鲍伯的店');

  const aliceRows = await f.request('/v1/ledger/changes?since=0', undefined, alice);
  assert.deepEqual(aliceRows.data.txns.map(row => row.id), ['shared-id', 'alice-only']);
  assert.equal(aliceRows.data.txns[0].merchant, '老乡鸡');

  // A stale write from Bob can never win over Alice's row: they are different rows.
  const bobAgain = await f.request('/v1/ledger/push', { deviceId: 'bob-phone', txns: [txn('shared-id', { merchant: '旧名字', updatedAt: 1 })], categories: [] }, bob);
  assert.equal(bobAgain.data.stale.length, 1);
  assert.equal(f.db.prepare('SELECT merchant FROM ledger_txns WHERE user_id=? AND id=?').get(aliceId, 'shared-id').merchant, '老乡鸡');
});

test('snake_case aliases are accepted once, and conflicting duplicates are refused', async t => {
  const f = await fixture(t); const token = await verified(f);
  const alias = {
    id: 'alias', amount_cents: 4200, direction: 'OUT', merchant: '', category: '吃饭',
    occurred_at: 1_760_000_000_000, source: 'VOICE', note: '', updated_at: 1_760_000_000_500, deleted: false, card_last4: '0042',
  };
  const ok = await f.request('/v1/ledger/push', { deviceId: 'a', txns: [alias], categories: [] }, token);
  assert.equal(ok.data.applied, 1);

  const conflict = await f.request('/v1/ledger/push', { deviceId: 'a', txns: [{ ...alias, id: 'dupe', amount_cents: 100, amountCents: 200 }], categories: [] }, token);
  assert.deepEqual(conflict.data.rejected, [{ id: 'dupe', kind: 'txn', reason: 'ALIAS_CONFLICT' }]);
});

test('a push needs a device id and the body is bounded', async t => {
  const f = await fixture(t); const token = await verified(f);
  assert.equal((await f.request('/v1/ledger/push', { txns: [], categories: [] }, token)).response.status, 400);
  assert.equal((await f.request('/v1/ledger/push', { deviceId: 'x'.repeat(65), txns: [], categories: [] }, token)).response.status, 400);
  assert.equal((await f.request('/v1/ledger/push', { deviceId: 'a', txns: {}, categories: [] }, token)).response.status, 400);

  const huge = { deviceId: 'a', categories: [] };
  huge.txns = Array.from({ length: 2_000 }, (_, i) => txn(`big${i}`, { note: 'n'.repeat(200) }));
  const tooBig = await f.request('/v1/ledger/push', huge, token);
  assert.equal(tooBig.response.status, 413);
  assert.equal(tooBig.data.error, 'LEDGER_PAYLOAD_TOO_LARGE');
});

test('ledger routes are not reachable with the wrong method', async t => {
  const f = await fixture(t); const token = await verified(f);
  assert.equal((await f.request('/v1/ledger/push', undefined, token)).response.status, 404);
  assert.equal((await f.request('/v1/ledger/changes', { deviceId: 'a', txns: [], categories: [] }, token)).response.status, 404);
  assert.equal((await f.request('/v1/ledger/other', { deviceId: 'a' }, token)).response.status, 404);
});
