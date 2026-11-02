import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';

/** Real HTTP, in-memory SQLite — same fixture as circle.test.mjs. */
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
async function author(f) {
  const person = { email: `paper${++seq}@example.test`, username: `paper${seq}`, password: 'A long test passphrase 123', client: 'native' };
  const registered = await f.request('/v1/auth/register', { method: 'POST', body: person });
  assert.equal(registered.response.status, 201);
  const token = registered.data.token;
  assert.equal((await f.request('/v1/auth/verify', { method: 'POST', body: { code: f.mail.at(-1).code }, token })).response.status, 200);
  const circle = (await f.request('/v1/circles', { method: 'POST', body: { name: '家人' }, token })).data.circle;
  return { token, circle };
}

const post = (f, token, circleId, receipt, text) =>
  f.request(`/v1/circles/${circleId}/posts`, { method: 'POST', body: { text, receipt }, token });

/** The card as the author's own receipt page prints it: paper, ink, font, which sections, the stamp, the remark. */
const snapshot = () => ({
  date: '2026-11-01',
  totalCents: 8350,
  title: '午饭自由人',
  count: 3,
  comment: '今天比上周同一天少花了 24 块，主要因为没点外卖；奶茶那杯要是也省下，这周就能压到 500 以内了。'
    + '继续这样，月底就不用动备用金，够撑到下个月发工资，而且还能留出一顿饭的余量给自己。',
  style: { paper: 'kraft', ink: 0xff2b2b2b, font: 'serif', content: 0b1011011 },
  stamp: ' 午饭自由人 ',
  printedAt: 1793589000000,
  prevCents: 10750,
  lines: [{ label: '早饭', amountCents: 1200, count: 2 }, { label: '午饭', amountCents: 3500 }],
});

test('a full print snapshot round-trips through post and feed, normalised', async t => {
  const f = await fixture(t);
  const me = await author(f);
  const card = snapshot();
  assert.ok(card.comment.length > 60 && card.comment.length <= 120, 'the fixture needs the longer remark the app now sends');

  const created = await post(f, me.token, me.circle.id, card, '');
  assert.equal(created.response.status, 201);
  const echoed = created.data.post.receipt;
  assert.deepEqual(echoed, {
    date: '2026-11-01',
    totalCents: 8350,
    title: '午饭自由人',
    count: 3,
    comment: card.comment,
    style: { paper: 'kraft', ink: 0xff2b2b2b, font: 'serif', content: 91 },
    stamp: '午饭自由人',
    printedAt: 1793589000000,
    prevCents: 10750,
    lines: [{ label: '早饭', amountCents: 1200, count: 2 }, { label: '午饭', amountCents: 3500 }],
  });

  const feed = (await f.request(`/v1/circles/${me.circle.id}/feed`, { token: me.token })).data.posts;
  assert.deepEqual(feed[0].receipt, echoed, 'the feed renders the same paper the author printed');
});

test('every snapshot field is optional and only what was sent is echoed', async t => {
  const f = await fixture(t);
  const me = await author(f);

  const minimal = { date: '2026-11-01', totalCents: 300, title: '首单', count: 1 };
  const created = await post(f, me.token, me.circle.id, minimal, '');
  assert.equal(created.response.status, 201);
  assert.deepEqual(created.data.post.receipt, { ...minimal, comment: '' });

  // Nulls are values, not omissions: an "unset ink" stays unset instead of picking paper's default.
  const nulls = { date: '2026-11-01', totalCents: 300, title: '首单', count: 1, style: { ink: null }, prevCents: null };
  const withNulls = await post(f, me.token, me.circle.id, nulls, '');
  assert.equal(withNulls.response.status, 201);
  assert.deepEqual(withNulls.data.post.receipt.style, { ink: null });
  assert.equal(withNulls.data.post.receipt.prevCents, null);

  // An empty style is a valid style: the author prints everything with their defaults.
  assert.equal((await post(f, me.token, me.circle.id, { ...minimal, style: {} }, '')).data.post.receipt.style !== undefined, true);
});

test('out-of-range snapshot values are refused as invalid', async t => {
  const f = await fixture(t);
  const me = await author(f);
  const base = { date: '2026-11-01', totalCents: 100, title: '首单', count: 1 };
  const bad = [
    ['paper not a known paper', { ...base, style: { paper: 'white' } }],
    ['paper not a string', { ...base, style: { paper: 1 } }],
    ['font not a known font', { ...base, style: { font: 'mono' } }],
    ['ink below zero', { ...base, style: { ink: -1 } }],
    ['ink above 32 bits', { ...base, style: { ink: 0x1_0000_0000 } }],
    ['ink fractional', { ...base, style: { ink: 1.5 } }],
    ['ink not a number', { ...base, style: { ink: 'black' } }],
    ['content above the mask', { ...base, style: { content: 128 } }],
    ['content below zero', { ...base, style: { content: -1 } }],
    ['content fractional', { ...base, style: { content: 0.5 } }],
    ['style is an array', { ...base, style: [] }],
    ['style is a string', { ...base, style: 'kraft' }],
    ['stamp empty', { ...base, stamp: '   ' }],
    ['stamp too long', { ...base, stamp: '一'.repeat(17) }],
    ['stamp not a string', { ...base, stamp: 7 }],
    ['printedAt negative', { ...base, printedAt: -1 }],
    ['printedAt fractional', { ...base, printedAt: 1.5 }],
    ['printedAt not a number', { ...base, printedAt: 'now' }],
    ['prevCents negative', { ...base, prevCents: -5 }],
    ['prevCents fractional', { ...base, prevCents: 12.5 }],
    ['line count above the cap', { ...base, lines: [{ label: '早饭', amountCents: 100, count: 1_000_001 }] }],
    ['line count negative', { ...base, lines: [{ label: '早饭', amountCents: 100, count: -1 }] }],
    ['line count fractional', { ...base, lines: [{ label: '早饭', amountCents: 100, count: 1.2 }] }],
    ['remark over 120', { ...base, comment: '字'.repeat(121) }],
  ];
  for (const [label, receipt] of bad) {
    const result = await post(f, me.token, me.circle.id, receipt, '');
    assert.equal(result.response.status, 400, label);
    assert.equal(result.data.error, 'RECEIPT_INVALID', label);
  }
});

test('unknown snapshot keys are a field error, bill text is still a raw-text error', async t => {
  const f = await fixture(t);
  const me = await author(f);
  const base = { date: '2026-11-01', totalCents: 100, title: '首单', count: 1 };

  const unknownStyleKey = await post(f, me.token, me.circle.id, { ...base, style: { colour: 'red' } }, '');
  assert.equal(unknownStyleKey.response.status, 400);
  assert.equal(unknownStyleKey.data.error, 'RECEIPT_FIELD');

  const unknownTopKey = await post(f, me.token, me.circle.id, { ...base, theme: 'kraft' }, '');
  assert.equal(unknownTopKey.data.error, 'RECEIPT_FIELD');

  const rawInStyle = await post(f, me.token, me.circle.id, { ...base, style: { merchant: '瑞幸' } }, '');
  assert.equal(rawInStyle.response.status, 400);
  assert.equal(rawInStyle.data.error, 'RECEIPT_RAW_TEXT');

  const rawTop = await post(f, me.token, me.circle.id, { ...base, rawText: '支付宝：瑞幸 -18.00' }, '');
  assert.equal(rawTop.data.error, 'RECEIPT_RAW_TEXT');

  const rawInLine = await post(f, me.token, me.circle.id, { ...base, lines: [{ label: '早饭', amountCents: 100, merchant: '瑞幸' }] }, '');
  assert.equal(rawInLine.data.error, 'RECEIPT_RAW_TEXT');

  const textInLine = await post(f, me.token, me.circle.id, { ...base, lines: [{ label: '早饭', amountCents: 100, rawText: '瑞幸 -18' }] }, '');
  assert.equal(textInLine.data.error, 'RECEIPT_RAW_TEXT');

  const unknownLineKey = await post(f, me.token, me.circle.id, { ...base, lines: [{ label: '早饭', amountCents: 100, hue: 'red' }] }, '');
  assert.equal(unknownLineKey.data.error, 'RECEIPT_FIELD');
});

test('the remark may run to 120 characters but not past it', async t => {
  const f = await fixture(t);
  const me = await author(f);
  const base = { date: '2026-11-01', totalCents: 100, title: '首单', count: 1 };
  assert.equal((await post(f, me.token, me.circle.id, { ...base, comment: '字'.repeat(120) }, '')).response.status, 201);
  assert.equal((await post(f, me.token, me.circle.id, { ...base, comment: '字'.repeat(121) }, '')).response.status, 400);
});
