import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AGENT_LIMITS, createAgent, toolDefinitions } from '../src/agent.mjs';
import { DATA_TOOLS, DRAFT_KINDS, normalizeDraft, normalizePlan } from '../src/tools.mjs';
import { viewFromToolResults } from '../src/views.mjs';
import { buildSystemPrompt } from '../src/prompt.mjs';

const MODEL = 'reflection/beam:free';
const FALLBACK = 'stealth/glyph-cluster:free';
const signal = () => new AbortController().signal;
const tool = name => toolDefinitions.find(item => item.function.name === name);

/** A provider whose chat answers come from a script, one entry per request. */
function scripted(answers, models = [MODEL]) {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: models.map(id => ({ id })) });
    seen.push(JSON.parse(options.body));
    const next = answers.shift();
    if (!next) throw new Error('SCRIPT_EXHAUSTED');
    return typeof next === 'function' ? next(JSON.parse(options.body)) : new Response(next);
  };
  return { fetcher, seen };
}
const calls = parts => `data: ${JSON.stringify({ choices: [{ delta: { tool_calls: parts } }] })}\n\ndata: [DONE]\n\n`;
const call = (index, name, args) => ({ index, id: `c${index}`, function: { name, arguments: JSON.stringify(args) } });
const text = content => `data: ${JSON.stringify({ choices: [{ delta: { content } }] })}\n\ndata: [DONE]\n\n`;

test('every data tool is declared with a strict schema and the cents unit', () => {
  for (const name of DATA_TOOLS) {
    const definition = tool(name);
    assert.ok(definition, `${name} is declared`);
    assert.equal(definition.type, 'function');
    assert.equal(definition.function.parameters.type, 'object');
    assert.equal(definition.function.parameters.additionalProperties, false, `${name} refuses unknown fields`);
    assert.ok(definition.function.description.length >= 10);
  }
  // Money tools must say the unit in the text the model reads, not only in the schema.
  for (const name of ['read_budget', 'summarize_range', 'read_subscriptions', 'read_weekly_report', 'query_transactions']) {
    assert.match(tool(name).function.description, /分/, `${name} states the unit`);
  }
  for (const name of ['get_life_days', 'get_life_top_foods']) assert.match(tool(name).function.description, /kcal|次数/);
  assert.match(tool('get_meal_spend').function.description, /分/);
  assert.deepEqual(tool('read_budget').function.parameters.properties.period.enum, ['day', 'week', 'month', 'all']);
  assert.deepEqual(tool('summarize_range').function.parameters.properties.group_by.enum, ['day', 'week', 'month', 'category']);
  assert.deepEqual(Object.keys(tool('get_life_days').function.parameters.properties).sort(), ['from', 'to']);
  assert.deepEqual(Object.keys(tool('get_life_top_foods').function.parameters.properties).sort(), ['from', 'limit', 'to']);
  assert.equal(tool('get_life_top_foods').function.parameters.properties.limit.maximum, 20);
  assert.deepEqual(Object.keys(tool('get_meal_spend').function.parameters.properties).sort(), ['from', 'to']);
  assert.deepEqual(tool('list_categories').function.parameters.properties, {});
  // Card wallet and note bodies are not reachable: no tool takes an account, card or raw-text field.
  const names = toolDefinitions.map(item => item.function.name);
  for (const forbidden of ['read_card', 'read_wallet', 'read_notes', 'read_raw_text']) assert.equal(names.includes(forbidden), false);
  const draft = tool('draft');
  assert.deepEqual(draft.function.parameters.properties.kind.enum, [...DRAFT_KINDS]);
  assert.equal(tool('plan_steps').function.parameters.properties.steps.maxItems, 6);
});

test('an invalid draft shape is refused before it can reach a client', () => {
  assert.deepEqual(normalizeDraft({ kind: 'record', amount_cents: 2500, direction: 'OUT', category: '吃饭', note: '牛肉面' }),
    { kind: 'record', amount_cents: 2500, direction: 'OUT', category: '吃饭', note: '牛肉面' });
  for (const bad of [
    { kind: 'record', amount_cents: 2500, direction: 'OUT', card_last4: '1234' }, // unknown field
    { kind: 'record', amount_cents: 25.5, direction: 'OUT' }, // not integer cents
    { kind: 'record', amount_cents: -100, direction: 'OUT' }, // negative
    { kind: 'record', amount_cents: 2500, direction: 'SIDEWAYS' },
    { kind: 'record', direction: 'OUT' }, // missing amount
    { kind: 'budget', period: 'year', limit_cents: 100000 },
    { kind: 'budget', period: 'month' },
    { kind: 'reminder', at: 1, text: '提醒我' }, // in the past
    { kind: 'reminder', at: Date.now() + 60_000, text: '' },
    { kind: 'transfer', amount_cents: 100 },
    { kind: 'record', amount_cents: 2500, direction: 'OUT', note: 'x'.repeat(121) },
    { kind: 'meal', meal: 'brunch', at: 1, items: [{ name: '牛肉面', qty: 1, unit: '碗' }] }, // unknown meal
    { kind: 'meal', meal: 'lunch', at: 1, items: [] }, // no items
    { kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 1, unit: '碗', kcal: 600 }] }, // model-supplied kcal
    { kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 0, unit: '碗' }] }, // qty must be positive
    { kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 1 }] }, // unit is required
    { kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 1, unit: '碗' }], kcal: 600 }, // kcal on the draft
    { kind: 'activity', name: '跑步', at: 1 }, // neither minutes nor sets
    { kind: 'activity', name: '深蹲', at: 1, sets: [{ reps: 0 }] },
    { kind: 'activity', name: '深蹲', at: 1, sets: [{ reps: 10, kcal: 40 }] },
    { kind: 'activity', name: '跑步', at: 1, minutes: 0 },
  ]) assert.equal(normalizeDraft(bad), null, JSON.stringify(bad));
  assert.deepEqual(normalizeDraft({ kind: 'budget', period: 'month', limit_cents: 300000 }),
    { kind: 'budget', period: 'month', limit_cents: 300000 });
  // A meal and an activity carry quantities only; every calorie comes from the app's knowledge base.
  assert.deepEqual(normalizeDraft({ kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 1, unit: '碗' }], price_cents: 2500 }),
    { kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 1, unit: '碗' }], price_cents: 2500 });
  assert.deepEqual(normalizeDraft({ kind: 'activity', name: '深蹲', at: 1, sets: [{ reps: 10, kg: 60 }, { reps: 8 }] }),
    { kind: 'activity', name: '深蹲', at: 1, sets: [{ reps: 10, kg: 60 }, { reps: 8 }] });
  assert.deepEqual(normalizeDraft({ kind: 'activity', name: '跑步', at: 1, minutes: 30 }),
    { kind: 'activity', name: '跑步', at: 1, minutes: 30 });
  assert.deepEqual(normalizePlan({ steps: [{ tool: 'read_budget', label: '正在查预算' }] }),
    [{ tool: 'read_budget', label: '正在查预算' }]);
  assert.equal(normalizePlan({ steps: [{ tool: 'draft', label: 'x' }] }), null);
  assert.equal(normalizePlan({ steps: [] }), null);
});

test('a plan is announced once, acknowledged server-side, and never sent as a tool call', async () => {
  const { fetcher, seen } = scripted([
    calls([call(0, 'plan_steps', { steps: [{ tool: 'read_budget', label: '正在查预算' }, { tool: 'summarize_range', label: '正在对比本月支出' }] })]),
    calls([call(0, 'read_budget', { period: 'month' }), call(1, 'summarize_range', { from: 1, to: 2, group_by: 'category' })]),
  ]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({ messages: [{ role: 'user', content: '这个月还剩多少预算，比上个月花得多吗' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  const plan = events.find(event => event.type === 'plan');
  assert.ok(plan, 'plan event');
  assert.deepEqual(plan.steps, [
    { index: 1, tool: 'read_budget', label: '正在查预算' },
    { index: 2, tool: 'summarize_range', label: '正在对比本月支出' },
  ]);
  assert.equal(events.filter(event => event.type === 'plan').length, 1, 'announced once');
  // The plan travelled back to the model as a tool result, so the model can continue in the same HTTP request.
  assert.ok(seen[1].messages.some(message => message.role === 'tool' && message.tool_call_id === 'c0'));
  assert.deepEqual(state.pending.map(item => item.function.name), ['read_budget', 'summarize_range']);
  const running = events.filter(event => event.type === 'step');
  assert.deepEqual(running.map(event => [event.tool, event.status]), [['read_budget', 'running'], ['summarize_range', 'running']]);
  assert.ok(events.indexOf(plan) < events.indexOf(running[0]), 'plan comes before the steps it describes');
  assert.equal(seen[1].messages.filter(message => message.role === 'tool' && message.tool_call_id === 'c0').length, 1);
});

test('a plan written together with its first lookups costs no extra model call', async () => {
  const { fetcher, seen } = scripted([
    calls([
      call(0, 'plan_steps', { steps: [{ tool: 'read_budget', label: '正在查预算' }] }),
      call(1, 'read_budget', { period: 'month' }),
    ]),
  ]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({ messages: [{ role: 'user', content: '预算呢' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  assert.equal(seen.length, 1, 'one model call answered plan and lookup together');
  assert.deepEqual(state.pending.map(item => item.function.name), ['read_budget']);
  assert.equal(events.filter(event => event.type === 'plan').length, 1);
  assert.equal(events.some(event => event.tool === 'plan_steps'), false, 'the plan itself is not a forwarded step');
  assert.equal(events.filter(event => event.type === 'step' && event.status === 'running').length, 1);
});

test('two lookups without an explicit plan still narrate a plan, and steps close when results arrive', async () => {
  const { fetcher } = scripted([calls([call(0, 'read_budget', {}), call(1, 'read_subscriptions', {})])]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  let state = await agent({ messages: [{ role: 'user', content: '预算和订阅一起看' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  assert.deepEqual(events.filter(event => event.type === 'plan')[0].steps.map(step => step.tool), ['read_budget', 'read_subscriptions']);
  assert.equal(state.pending.length, 2);

  const { fetcher: second } = scripted([text('两件事都查完了')]);
  const continuation = createAgent({ apiKey: 'test-placeholder', fetcher: second });
  const closed = [];
  state = await continuation({
    ...state,
    messages: [...state.messages,
      { role: 'tool', tool_call_id: 'c0', content: '{"configured":true}' },
      { role: 'tool', tool_call_id: 'c1', content: '{"count":0}' }],
    pending: [],
  }, e => closed.push(e), signal());
  const done = closed.filter(event => event.type === 'step');
  assert.deepEqual(done.map(event => [event.tool, event.status]), [['read_budget', 'done'], ['read_subscriptions', 'done']]);
  assert.equal(new Set(done.map(event => event.id)).size, 2, 'each step closes once');
  assert.equal(state.round, 2);
});

test('the eighth round stops asking for tools, keeps the numbers, and never ends empty', async () => {
  const { fetcher, seen } = scripted([]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({
    messages: [
      { role: 'user', content: '今天花了多少' },
      { role: 'assistant', content: null, tool_calls: [{ id: 'c1', type: 'function', function: { name: 'summarize_period', arguments: '{"period":"today"}' } }] },
      { role: 'tool', tool_call_id: 'c1', content: JSON.stringify({ period: 'today', unit: 'cents', count: 4, total_out: 5500 }) },
    ],
    round: AGENT_LIMITS.rounds, pending: [], timezone: 'UTC',
  }, e => events.push(e), signal());
  assert.equal(seen.length, 0, 'no further model call');
  assert.equal(state.truncated, true);
  assert.deepEqual(state.pending, []);
  const view = events.find(event => event.type === 'view')?.view;
  assert.equal(view.kind, 'spend_summary');
  assert.equal(view.total_cents, 5500);
  assert.equal(events.some(event => event.type === 'error'), false);
});

test('a question that outlives its wall clock is closed the same way, from the results it already has', async () => {
  const { fetcher, seen } = scripted([]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({
    messages: [
      { role: 'user', content: '上个月花了多少' },
      { role: 'assistant', content: null, tool_calls: [{ id: 'c1', type: 'function', function: { name: 'summarize_range', arguments: '{"from":1,"to":2,"group_by":"category"}' } }] },
      { role: 'tool', tool_call_id: 'c1', content: JSON.stringify({ unit: 'cents', count: 3, total_out: 8800, buckets: [{ label: '吃饭', cents: 8800 }] }) },
    ],
    round: 2, startedAt: Date.now() - AGENT_LIMITS.turnMs - 1, pending: [], timezone: 'UTC',
  }, e => events.push(e), signal());
  assert.equal(seen.length, 0);
  assert.equal(state.truncated, true);
  assert.equal(events.find(event => event.type === 'view')?.view.kind, 'category_split');
});

test('a valid draft is validated, announced, and still forwarded for the client to confirm', async () => {
  const { fetcher } = scripted([calls([call(0, 'draft', { kind: 'record', amount_cents: 2500, direction: 'OUT', category: '吃饭', note: '牛肉面' })])]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({ messages: [{ role: 'user', content: '午饭牛肉面 25 记一下' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  assert.deepEqual(events.find(event => event.type === 'draft').draft,
    { kind: 'record', amount_cents: 2500, direction: 'OUT', category: '吃饭', note: '牛肉面' });
  assert.deepEqual(state.pending.map(item => item.function.name), ['draft']);
  assert.equal(JSON.parse(state.pending[0].function.arguments).amount_cents, 2500);
  // The draft card is an event, but the call also travels so a client that renders from tool_calls keeps working.
  assert.deepEqual(events.filter(event => event.type === 'step').map(event => event.tool), ['draft']);
});

test('a draft with a made-up field never reaches the client; another model gets the turn', async () => {
  const { fetcher, seen } = scripted([
    calls([call(0, 'draft', { kind: 'record', amount_cents: 2500, direction: 'OUT', card_last4: '1234' })]),
    text('好的，先不记。'),
  ], [MODEL, FALLBACK]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  await agent({ messages: [{ role: 'user', content: '记一笔' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  assert.equal(seen.length, 2, 'the broken draft costs one model, not the answer');
  assert.equal(events.some(event => event.type === 'draft'), false);
  assert.ok(events.some(event => event.type === 'token' && event.text === '好的，先不记。'));
});

test('unsourced numbers stay refused, and numbers from a new tool are allowed', async () => {
  const rejected = scripted([text('这个月预算还剩 ¥120.00。'), text('先说结论，我没有查。')], [MODEL, FALLBACK]);
  const first = createAgent({ apiKey: 'test-placeholder', fetcher: rejected.fetcher });
  const events = [];
  await first({ messages: [{ role: 'user', content: '预算还剩多少' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  assert.equal(rejected.seen.length, 2);
  assert.equal(events.some(event => event.type === 'token' && event.text.includes('120')), false, 'invented number never streams');

  const backed = scripted([text('这个月预算还剩 ¥120.00。')], [MODEL]);
  const second = createAgent({ apiKey: 'test-placeholder', fetcher: backed.fetcher });
  const allowed = [];
  const state = await second({
    messages: [
      { role: 'user', content: '预算还剩多少' },
      { role: 'assistant', content: null, tool_calls: [{ id: 'b1', type: 'function', function: { name: 'read_budget', arguments: '{"period":"month"}' } }] },
      { role: 'tool', tool_call_id: 'b1', content: JSON.stringify({ configured: true, unit: 'cents', categories: [] }) },
    ],
    round: 1, pending: [], timezone: 'UTC',
  }, e => allowed.push(e), signal());
  assert.ok(allowed.some(event => event.type === 'token' && event.text.includes('120')), 'numbers backed by a tool result are fine');
  assert.equal(state.pending.length, 0);
});

test('a newer result without a view kind cannot resurrect an older card', () => {
  const messages = [
    { role: 'user', content: '今天花了多少，预算呢' },
    { role: 'assistant', content: null, tool_calls: [{ id: 'a', type: 'function', function: { name: 'summarize_period', arguments: '{"period":"today"}' } }] },
    { role: 'tool', tool_call_id: 'a', content: JSON.stringify({ period: 'today', unit: 'cents', count: 4, total_out: 5500 }) },
    { role: 'assistant', content: null, tool_calls: [{ id: 'b', type: 'function', function: { name: 'read_budget', arguments: '{"period":"month"}' } }] },
    { role: 'tool', tool_call_id: 'b', content: JSON.stringify({ configured: true, unit: 'cents' }) },
  ];
  assert.equal(viewFromToolResults(messages), null);
  assert.equal(viewFromToolResults(messages.slice(0, 3)).kind, 'spend_summary');
  const subscriptions = messages.slice(0, 3).concat([
    { role: 'assistant', content: null, tool_calls: [{ id: 's', type: 'function', function: { name: 'read_subscriptions', arguments: '{}' } }] },
    { role: 'tool', tool_call_id: 's', content: JSON.stringify({ unit: 'cents', rows: [{ merchant: '网飞', cents: 2500, cadence: '每月' }] }) },
  ]);
  assert.deepEqual(viewFromToolResults(subscriptions).rows, [{ merchant: '网飞', cents: 2500, cadence: '每月' }]);
});

test('a meal or activity draft that carries calories is refused before it can be shown', async () => {
  const { fetcher, seen } = scripted([
    calls([call(0, 'draft', { kind: 'meal', meal: 'lunch', at: 1, items: [{ name: '牛肉面', qty: 1, unit: '碗', kcal: 600 }] })]),
    text('热量得让 App 自己算，我先不记。'),
  ], [MODEL, FALLBACK]);
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  await agent({ messages: [{ role: 'user', content: '午饭牛肉面，600 大卡' }], round: 0, timezone: 'UTC' }, e => events.push(e), signal());
  assert.equal(seen.length, 2, 'the bad draft is handed to another model');
  assert.equal(events.some(event => event.type === 'draft'), false);
  assert.ok(!JSON.stringify(events).includes('600'), 'no invented or echoed calorie reaches the client');
});

test('the prompt allows review and simulation, and refuses advice', () => {
  const prompt = buildSystemPrompt({ timezone: 'Asia/Shanghai', today: '2026-11-03' });
  assert.match(prompt, /复盘/);
  assert.match(prompt, /模拟后果/);
  assert.match(prompt, /只帮记录、复盘和算账/);
  for (const forbidden of ['推荐吃什么', '减重或增肌计划', '医疗或营养治疗建议', '不说「超标」']) {
    assert.ok(prompt.includes(forbidden), `the prompt names the boundary: ${forbidden}`);
  }
  assert.match(prompt, /按每天少一杯算/);
});

test('the prompt names every tool, the plan step and the draft kinds', () => {
  const prompt = buildSystemPrompt({ timezone: 'Asia/Shanghai', today: '2026-11-03' });
  for (const name of DATA_TOOLS) assert.ok(prompt.includes(name), `${name} is documented for the model`);
  assert.ok(prompt.includes('plan_steps'));
  assert.ok(prompt.includes('draft'));
  assert.match(prompt, /不推荐买什么/);
  assert.match(prompt, /整数分/);
});
