import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DRAFT_KINDS, DRAFT_SPECS, TOOLS, renderToolDoc } from '../src/tools.mjs';
import { buildSystemPrompt } from '../src/prompt.mjs';
import { block, currentBlock, readmePath } from '../scripts/tools.mjs';

test('the README tool table is generated from the registry and cannot drift', () => {
  const readme = readFileSync(readmePath(), 'utf8');
  assert.equal(currentBlock(readme), block(renderToolDoc()), 'run `node scripts/tools.mjs --write`');
  for (const tool of TOOLS) assert.ok(readme.includes(`\`${tool.name}\``), `${tool.name} is documented in the README`);
  // The retired name must not linger anywhere the reader would copy from.
  assert.equal(readme.includes('read_life_logs'), false);
});

test('the prompt lists exactly the registered tools and draft kinds', () => {
  const prompt = buildSystemPrompt({ timezone: 'Asia/Shanghai', today: '2026-11-03' });
  for (const kind of DRAFT_KINDS) {
    assert.ok(prompt.includes(`${kind}（`), `${kind} is described in the draft rule`);
    assert.ok(DRAFT_SPECS[kind].hint.length > 0);
  }
  // The rule that enumerates kinds is the only place they may be listed, and it must list exactly the registry.
  const rule = prompt.split('\n').find(line => line.includes('kind 只能是'));
  const listed = [...rule.matchAll(/(record|budget|reminder|meal|activity)（/g)].map(match => match[1]);
  assert.deepEqual([...new Set(listed)].sort(), [...DRAFT_KINDS].sort());
  for (const tool of TOOLS) {
    if (tool.kind === 'data') assert.ok(prompt.includes(tool.name), `${tool.name} is offered to the model`);
  }
  // A tool that does not exist must not be described as if it did.
  for (const stale of ['read_life_logs', 'read_notes', 'read_card']) assert.equal(prompt.includes(stale), false);
});

test('the generated doc stays small enough to read and names the units', () => {
  const doc = renderToolDoc();
  const rows = doc.split('\n').filter(line => line.startsWith('| `'));
  assert.equal(rows.length, TOOLS.length);
  assert.match(doc, /整数分/);
  assert.match(doc, /kcal/);
  assert.match(doc, /起草 kind：/);
});
