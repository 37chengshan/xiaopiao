import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSources, numbersIn, stripDates, unsourcedNumbers } from '../src/sources.mjs';

const tool = data => ({ role: 'tool', tool_call_id: 'c1', content: typeof data === 'string' ? data : JSON.stringify(data) });
const user = content => ({ role: 'user', content });
/** Sources for one question: the user's message first, then its tool results. */
const sourcesFor = (...messages) => buildSources(messages, messages.findLastIndex(message => message.role === 'user') - 1);
const grounded = (text, sources) => unsourcedNumbers(text, sources).length === 0;

test('a sum of two numbers from different results is not a source', () => {
  const sources = sourcesFor(user('这个月和上个月比呢'), tool({ total_out: 11 }), tool({ total_out: 34 }));
  assert.equal(grounded('合计 ¥45.00', sources), false);
  assert.equal(grounded('¥11.00', sources), true);
});

test('the sum of the same field across rows is a source, in cents and in yuan', () => {
  const sources = sourcesFor(user('这三笔一共多少'), tool({ rows: [{ cents: 1500 }, { cents: 1500 }, { cents: 1500 }] }));
  assert.equal(grounded('合计 ¥45.00', sources), true);
  assert.equal(grounded('一共 4500 分', sources), true);
  assert.equal(grounded('合计 ¥46.00', sources), false);
});

test('a mean, a share and a difference inside one result are sources', () => {
  const sources = sourcesFor(user('这个月预算用得怎么样'), tool({ limit_cents: 10000, spent_cents: 4000 }));
  assert.equal(grounded('用掉 40%', sources), true);
  assert.equal(grounded('还剩 ¥60.00', sources), true); // 10000 - 4000 = 6000 cents
  assert.equal(grounded('还剩 ¥70.00', sources), false);
  const mean = sourcesFor(user('平均一餐多少'), tool({ rows: [{ cents: 1000 }, { cents: 3000 }] }));
  assert.equal(grounded('平均 ¥20.00', mean), true); // (1000+3000)/2 = 2000 cents
});

test('rows of one field give totals only: a difference between two rows is not a source', () => {
  // 50 rows, deterministic pseudo-random amounts: pairwise differences would be ~1,225 values, and with them almost
  // any invented figure would find a match.
  const rows = [];
  let seed = 7;
  for (let i = 0; i < 50; i++) {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    rows.push({ cents: 100 + (seed % 90_000) });
  }
  const sources = sourcesFor(user('这 50 笔里差得最多的是哪两笔'), tool({ rows }));
  const values = rows.map(row => row.cents);
  const sum = values.reduce((total, value) => total + value, 0);
  const allowed = new Set([...values, sum, sum / values.length, Math.max(...values), Math.min(...values), rows.length]);
  // A difference between two rows that is not itself a row, a total or a statistic.
  const gap = Math.abs(values[3] - values[27]);
  assert.equal(allowed.has(gap), false, 'the test needs a difference that is not also a row');
  assert.equal(grounded(`两笔相差 ¥${(gap / 100).toFixed(2)}`, sources), false);
  assert.equal(grounded(`合计 ¥${(sum / 100).toFixed(2)}`, sources), true);
  assert.equal(grounded('50 笔', sources), true);
});

test('a share of two sibling fields and the difference between field totals are sources', () => {
  const budget = sourcesFor(user('预算用得怎么样'), tool({ limit: 300000, spent: 120000 }));
  assert.equal(grounded('用掉 40%', budget), true);
  assert.equal(grounded('还剩 ¥1800.00', budget), true);
  const weeks = sourcesFor(user('和上周比呢'), tool({ this_week_cents: 4500, last_week_cents: 5700 }));
  assert.equal(grounded('比上周少 ¥12.00', weeks), true); // field totals 4500 与 5700 的差
  assert.equal(grounded('比上周少 40%', weeks), false); // 12/57 ≈ 21%，不是 40%
});

test('a count is a source only when an array really has that length', () => {
  const three = sourcesFor(user('几笔'), tool({ rows: [{ cents: 100 }, { cents: 200 }, { cents: 300 }] }));
  assert.equal(grounded('3 笔', three), true);
  assert.equal(grounded('4 笔', three), false);
  // Two rows whose sum in yuan is not a count by coincidence (1500 + 2000 = ¥35.00).
  const two = sourcesFor(user('几笔'), tool({ rows: [{ cents: 1500 }, { cents: 2000 }] }));
  assert.equal(grounded('3 笔', two), false);
});

test('dates are stripped before numbers are read, and date digits are never a source', () => {
  assert.equal(stripDates('11月3日').trim(), '');
  assert.equal(stripDates('2026-11-03').trim(), '');
  assert.equal(stripDates('周三 14:30').trim(), '');
  assert.deepEqual(numbersIn('11月3日花了 ¥45.00'), [45]);
  assert.deepEqual(numbersIn('2026-11-03 共 3 笔'), [3]);
  assert.deepEqual(numbersIn('3-5 笔'), [3, 5], 'a range is not a date');
  const sources = sourcesFor(user('这周花了多少'), tool({ day: '2026-11-03', rows: [{ cents: 4500 }] }));
  assert.equal(grounded('11月3日合计 ¥45.00', sources), true);
  assert.equal(grounded('2026', sources), false, 'a date label must not become a number');
  assert.deepEqual(numbersIn('11-03'), [], 'a whole date is stripped, so it is not read as 11 and 3');
});

test("the person's own numbers may be combined with each other", () => {
  const sources = sourcesFor(user('每天一杯 15 元的奶茶，30 天要花多少'), { role: 'user', content: '想省点钱' });
  assert.equal(grounded('一个月 ¥450.00', sources), true);
  assert.equal(grounded('一个月 ¥480.00', sources), false);
  assert.equal(grounded('平均每天 ¥15.00', sources), true);
});

test('a tool result that failed, or that carries no numbers, licenses nothing', () => {
  for (const content of [{ error: 'TOOL_NOT_ALLOWED', cents: 12000 }, { configured: false, unit: 'cents', categories: [] }]) {
    const sources = sourcesFor(user('预算还剩多少'), tool(content));
    assert.equal(grounded('还剩 ¥120.00', sources), false, JSON.stringify(content));
  }
});

test('tool results from an earlier question are not sources', () => {
  const messages = [
    user('昨天花了多少'),
    tool({ total_out: 5500 }),
    user('今天呢'),
    tool({ total_out: 1200 }),
  ];
  const sources = buildSources(messages, messages.findLastIndex(message => message.role === 'user'));
  assert.equal(grounded('昨天 ¥55.00', sources), false);
  assert.equal(grounded('今天 ¥12.00', sources), true);
});
