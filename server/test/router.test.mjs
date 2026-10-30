import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createModelRouter } from '../src/router.mjs';

function clock(start = 1_000_000) {
  let time = start;
  return { now: () => time, advance: ms => { time += ms; } };
}
const models = ['fast:free', 'slow:free', 'broken:free'];

test('without history every model is offered; observed latency then ranks the fastest first', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  assert.deepEqual(router.order().sort(), [...models].sort());
  router.report('slow:free', { ok: true, latencyMs: 9_000 });
  router.report('fast:free', { ok: true, latencyMs: 700 });
  assert.equal(router.order()[0], 'fast:free');
});
test('rate limits rest far longer than upstream errors, and repeats escalate', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  router.report('broken:free', { ok: false, kind: 'unavailable' });
  router.report('slow:free', { ok: false, kind: 'rate_limit' });
  assert.deepEqual(router.order({ needsTools: false }), ['fast:free']);
  c.advance(60_000);
  assert.ok(router.order({ needsTools: false }).includes('broken:free'), 'short cooldown expired');
  assert.ok(!router.order({ needsTools: false }).includes('slow:free'), 'rate limit still cooling');
  router.report('slow:free', { ok: false, kind: 'rate_limit' });
  c.advance(180_000);
  assert.ok(!router.order({ needsTools: false }).includes('slow:free'), 'second rate limit doubles the rest');
  c.advance(300_000);
  assert.ok(router.order({ needsTools: false }).includes('slow:free'), 'eventually retried');
});
test('a success clears strikes and cooldown', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  router.report('fast:free', { ok: false, kind: 'timeout', latencyMs: 6_000 });
  assert.equal(router.firstTokenBudget('fast:free'), 15_000, 'first timeout only raises the budget');
  router.report('fast:free', { ok: false, kind: 'timeout', latencyMs: 15_000 });
  assert.ok(!router.order().includes('fast:free'), 'repeated timeouts rest');
  c.advance(46_000);
  router.report('fast:free', { ok: true, latencyMs: 900 });
  assert.equal(router.order()[0], 'fast:free');
  assert.equal(router.snapshot().find(item => item.id === 'fast:free').strikes, 0);
});
test('an empty answer without tools only removes that model from tool-free rounds', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  router.report('fast:free', { ok: false, kind: 'empty', needsTools: false });
  assert.ok(!router.order({ needsTools: false }).includes('fast:free'));
  c.advance(121_000);
  assert.ok(router.order().includes('fast:free'), 'still usable when tools are offered');
  assert.ok(!router.order({ needsTools: false }).includes('fast:free'), 'capability memory survives the cooldown');
});
test('everything cooling still yields the earliest expiry instead of blocking', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  router.report('fast:free', { ok: false, kind: 'rate_limit' });
  router.report('slow:free', { ok: false, kind: 'rate_limit' });
  router.report('broken:free', { ok: false, kind: 'rate_limit' });
  c.advance(10_000);
  router.report('broken:free', { ok: true, latencyMs: 500 });
  assert.deepEqual(router.order(), ['broken:free']);
});
test('first-token budget starts at the floor, follows measured latency, and is clamped', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  assert.equal(router.firstTokenBudget('slow:free'), 9_000);
  router.report('slow:free', { ok: true, latencyMs: 9_000 });
  assert.equal(router.firstTokenBudget('slow:free'), 20_000);
  router.report('fast:free', { ok: false, kind: 'timeout', latencyMs: 4_000 });
  assert.equal(router.firstTokenBudget('fast:free'), 10_000);
});
test('quota spread prefers the least recently used model, and user aborts cost nothing', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  for (let i = 0; i < 4; i++) router.report('fast:free', { ok: true, latencyMs: 100 });
  assert.notEqual(router.order()[0], 'fast:free');
  router.report('slow:free', { ok: false, kind: 'aborted' });
  assert.ok(router.order({ needsTools: false }).includes('slow:free'));
});
test('tool rounds prefer a model that already produced tool calls', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  router.report('fast:free', { ok: true, latencyMs: 1_000, needsTools: true });
  router.report('slow:free', { ok: true, latencyMs: 1_500 });
  assert.equal(router.order({ needsTools: true })[0], 'fast:free');
});
test('a model that demands its reasoning back is skipped only once tool results exist', () => {
  const c = clock();
  const router = createModelRouter({ models, now: c.now });
  router.report('fast:free', { ok: false, kind: 'replay_required' });
  assert.ok(!router.order({ continuation: true }).includes('fast:free'));
  assert.ok(router.order({ continuation: false }).includes('fast:free'), 'still fine for a first round');
  assert.equal(router.snapshot().find(item => item.id === 'fast:free').strikes, 0, 'not counted as unhealthy');
});
