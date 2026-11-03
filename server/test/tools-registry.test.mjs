import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DRAFT_KINDS, DRAFT_SPECS, TOOLS, VIEWS, VIEW_KINDS, promptDraftLine, renderToolDoc, toolDefinitions } from '../src/tools.mjs';
import { buildSystemPrompt } from '../src/prompt.mjs';
import { block, currentBlock, readmePath } from '../scripts/tools.mjs';

test('the README tool table is generated from the registry and cannot drift', () => {
  const readme = readFileSync(readmePath(), 'utf8');
  assert.equal(currentBlock(readme), block(renderToolDoc()), 'run `node scripts/tools.mjs --write`');
  for (const tool of TOOLS) assert.ok(readme.includes(`\`${tool.name}\``), `${tool.name} is documented in the README`);
  for (const kind of VIEW_KINDS) assert.ok(readme.includes(`\`${kind}\``), `view ${kind} is documented`);
  // The retired name must not linger anywhere the reader would copy from.
  assert.equal(readme.includes('read_life_logs'), false);
});

test('the prompt is written from the registry, so it cannot name anything that does not exist', () => {
  const prompt = buildSystemPrompt({ timezone: 'Asia/Shanghai', today: '2026-11-03' });
  // Exact fragments, derived from the registry rather than a list typed in this test.
  assert.ok(prompt.includes(`kind 只能是 ${promptDraftLine()}。`), 'the draft rule enumerates exactly DRAFT_KINDS');
  for (const view of VIEWS) {
    assert.ok(prompt.includes(`- ${view.kind}：${view.use}；${view.fields}`), `view ${view.kind} is described`);
  }
  for (const tool of TOOLS) {
    if (tool.kind === 'data') assert.ok(prompt.includes(`- ${tool.name}：${tool.summary}`), `${tool.name} is offered`);
  }
  for (const kind of DRAFT_KINDS) assert.ok(DRAFT_SPECS[kind].hint.length > 0);
  for (const stale of ['read_life_logs', 'read_notes', 'read_card']) assert.equal(prompt.includes(stale), false);
});

test('show_view accepts exactly the registered view kinds', () => {
  const definition = toolDefinitions.find(tool => tool.function.name === 'show_view');
  assert.deepEqual(definition.function.parameters.properties.kind.enum, [...VIEW_KINDS]);
  const draft = toolDefinitions.find(tool => tool.function.name === 'draft');
  assert.deepEqual(draft.function.parameters.properties.kind.enum, [...DRAFT_KINDS]);
});

test('the generated doc stays small enough to read and names the units', () => {
  const doc = renderToolDoc();
  const rows = doc.split('\n').filter(line => line.startsWith('| `'));
  assert.equal(rows.length, TOOLS.length);
  assert.match(doc, /整数分/);
  assert.match(doc, /kcal/);
  assert.match(doc, /起草 kind：/);
  assert.match(doc, /视图 kind：/);
});
