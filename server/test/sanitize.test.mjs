import { test } from 'node:test';
import assert from 'node:assert/strict';
import { stripToolMarkup, createStreamSanitizer } from '../src/sanitize.mjs';

test('complete tool markup is removed, comparisons survive', () => {
  assert.equal(stripToolMarkup('总计<tool_call>{"a":1}</tool_call> ¥12'), '总计 ¥12');
  assert.equal(stripToolMarkup('<function=summarize_period>'), '');
  assert.equal(stripToolMarkup('如果 a<b 就说明 <3'), '如果 a<b 就说明 <3');
});
test('a tag split across chunks is held instead of leaking half of it', () => {
  const s = createStreamSanitizer();
  assert.equal(s.push('合计 ¥12 <tool_ca'), '合计 ¥12 ');
  assert.equal(s.push('ll>{"x":1}</tool_call> 完毕'), ' 完毕');
  assert.equal(s.finish(), '');
});
test('normal streaming text passes through untouched and a dangling tag is dropped', () => {
  const s = createStreamSanitizer();
  assert.equal(s.push('今天花了 **¥12**。'), '今天花了 **¥12**。');
  assert.equal(s.push('<tool_call>'), '');
  assert.equal(s.finish(), '');
  const half = createStreamSanitizer();
  assert.equal(half.push('一共 ¥12 <tool_ca'), '一共 ¥12 ');
  assert.equal(half.finish(), '', 'a half tag at end of stream never surfaces');
});
