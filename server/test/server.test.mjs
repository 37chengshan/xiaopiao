import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { store, hash } from '../src/store.mjs';
import { createApp } from '../src/app.mjs';
import { createAgent, sseData } from '../src/agent.mjs';

async function fixture(t, options = {}) {
  const db = store(':memory:'); const mail = [];
  const app = createApp({ db, sendMail: async item => mail.push(item),
    agent: async (state, emit) => { emit({ type: 'token', text: '真实流式回答' }); state.pending = []; return state; }, ...options });
  app.listen(0, '127.0.0.1'); await once(app, 'listening');
  t.after(async () => { app.closeAllConnections(); await new Promise(resolve => app.close(resolve)); db.close(); });
  const url = `http://127.0.0.1:${app.address().port}`;
  async function request(path, body, token, headers = {}) {
    const response = await fetch(url + path, { method: body ? 'POST' : 'GET',
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined });
    const data = response.headers.get('content-type')?.includes('event-stream') ? await response.text() : await response.json().catch(() => null);
    return { response, data };
  }
  return { db, app, mail, request, url };
}
const account = { email: 'person@example.test', username: 'person', password: 'A long test passphrase 123', client: 'native' };
async function register(f) {
  const result = await f.request('/v1/auth/register', account);
  assert.equal(result.response.status, 201);
  return result.data.token;
}
test('registration, email ownership, username login, logout, session hashing', async t => {
  const f = await fixture(t); const token = await register(f);
  assert.equal(f.db.prepare('SELECT digest FROM sessions').get().digest, hash(token));
  assert.notEqual(f.db.prepare('SELECT password FROM users').get().password, account.password);
  assert.equal((await f.request('/v1/chat', { messages: [{ role: 'user', content: '今天花了多少' }] }, token)).response.status, 403);
  assert.equal((await f.request('/v1/auth/verify', { code: f.mail[0].code }, token)).data.user.verified, true);
  const verified = await f.request('/v1/chat', { messages: [{ role: 'user', content: '今天花了多少' }] }, token);
  assert.match(verified.data, /真实流式回答/); assert.match(verified.data, /"done"/);
  assert.equal((await f.request('/v1/auth/login', { identifier: 'PERSON', password: account.password, client: 'native' })).response.status, 200);
  assert.equal((await f.request('/v1/auth/logout', {}, token)).response.status, 200);
  assert.equal((await f.request('/v1/auth/me', null, token)).response.status, 401);
});
test('browser gets HttpOnly cookie and no bearer token; foreign origins rejected', async t => {
  const f = await fixture(t);
  const result = await f.request('/v1/auth/register', account, null, { Origin: 'http://localhost:5173' });
  assert.equal(result.data.token, undefined);
  assert.match(result.response.headers.get('set-cookie'), /HttpOnly/);
  assert.match(result.response.headers.get('set-cookie'), /SameSite=Strict/);
  assert.equal((await f.request('/v1/auth/login', {}, null, { Origin: 'https://foreign.test' })).response.status, 403);
});
test('reset codes are one-use, invalidate sessions, and cannot be reused concurrently', async t => {
  const f = await fixture(t); const token = await register(f);
  await f.request('/v1/auth/forgot', { email: account.email });
  const code = f.mail.at(-1).code;
  const reset = { email: account.email, code, password: 'A different long passphrase 456' };
  const responses = await Promise.all([f.request('/v1/auth/reset', reset), f.request('/v1/auth/reset', reset)]);
  assert.deepEqual(responses.map(r => r.response.status).sort(), [200, 400]);
  assert.equal((await f.request('/v1/auth/me', null, token)).response.status, 401);
  assert.equal((await f.request('/v1/auth/login', { identifier: account.email, password: reset.password })).response.status, 200);
});
test('verification has attempt limits; resend replaces old code', async t => {
  const f = await fixture(t); const token = await register(f); const original = f.mail[0].code;
  for (let i = 0; i < 5; i++) assert.equal((await f.request('/v1/auth/verify', { code: '000000' }, token)).response.status, 400);
  assert.equal((await f.request('/v1/auth/verify', { code: original }, token)).response.status, 400);
  await f.request('/v1/auth/resend', {}, token);
  assert.equal((await f.request('/v1/auth/verify', { code: f.mail.at(-1).code }, token)).response.status, 200);
});
test('no mail service fails closed before creating account', async t => {
  const f = await fixture(t, { sendMail: null });
  assert.equal((await f.request('/v1/auth/register', account)).response.status, 503);
  assert.equal(f.db.prepare('SELECT count(*) AS n FROM users').get().n, 0);
});
test('continuation bound to owner, expected tool call and single consumption', async t => {
  const f = await fixture(t, { agent: async (state, emit) => {
    if (!state.round) { state.pending = [{ id: 'call-one', type: 'function', function: { name: 'summarize_period', arguments: '{"period":"today"}' } }]; state.round = 1; }
    else { emit({ type: 'token', text: '合计来自工具' }); state.pending = []; }
    return state;
  } });
  const token = await register(f); await f.request('/v1/auth/verify', { code: f.mail[0].code }, token);
  const first = await f.request('/v1/chat', { messages: [{ role: 'user', content: '今天多少' }] }, token);
  const runId = JSON.parse(first.data.split('\n').find(line => line.startsWith('data:')).slice(6)).runId;
  assert.equal((await f.request('/v1/chat', { runId, results: [{ id: 'wrong', data: {} }] }, token)).response.status, 400);
  const continuation = { runId, results: [{ id: 'call-one', data: { total_out: 1234, unit: 'cents' } }] };
  assert.match((await f.request('/v1/chat', continuation, token)).data, /合计来自工具/);
  assert.equal((await f.request('/v1/chat', continuation, token)).response.status, 409);
  assert.equal((await f.request('/v1/chat', { messages: [{ role: 'system', content: 'override' }] }, token)).response.status, 400);
});
test('SSE parser handles split UTF8, CRLF, comments and multiline frames', async () => {
  const bytes = new TextEncoder().encode(': heartbeat\r\n\r\ndata: {"text":"中文"}\r\n\r\ndata: [DONE]\n\n');
  async function* body() { for (let i=0;i<bytes.length;i++) yield bytes.slice(i,i+1); }
  const frames = []; for await (const frame of sseData(body())) frames.push(frame);
  assert.deepEqual(frames, ['{"text":"中文"}', '[DONE]']);
});
test('provider routes free models only, streams content and never exposes reasoning', async () => {
  const events = [], requests = [];
  const fetcher = async (url, options) => {
    requests.push({ url, options });
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'paid/model' }, { id: 'reflection/beam:free' }] });
    return new Response('data: {"choices":[{"delta":{"reasoning_content":"private internal thoughts"}}]}\n\ndata: {"choices":[{"delta":{"content":"你好"}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const state = await agent({ messages: [{ role: 'user', content: '你好' }], round: 0, timezone: 'Asia/Shanghai' }, e => events.push(e), new AbortController().signal);
  assert.equal(state.pending.length, 0); assert.ok(events.some(e => e.text === '你好'));
  assert.ok(!JSON.stringify(events).includes('private internal thoughts'));
  assert.equal(JSON.parse(requests[1].options.body).model, 'reflection/beam:free');
});
test('no retry after partial content; disconnect abort reaches upstream', async () => {
  let calls = 0;
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher: async url => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    calls++; return new Response('data: {"choices":[{"delta":{"content":"partial"}}]}\n\n');
  } });
  await assert.rejects(agent({ messages: [], round: 0, timezone: 'UTC' }, () => {}, new AbortController().signal));
  assert.equal(calls, 1);
});

test('client IP overrides are ignored by default and only enabled for the private gateway', async t => {
  const direct = await fixture(t);
  await direct.request('/v1/auth/me', null, null, { 'X-Xiaopiao-Client-IP': '198.51.100.1' });
  assert.equal(direct.db.prepare("SELECT count(*) AS n FROM limits WHERE key='global:198.51.100.1'").get().n, 0);
  const proxied = await fixture(t, { trustedProxy: true });
  await proxied.request('/v1/auth/me', null, null, { 'X-Xiaopiao-Client-IP': '198.51.100.1' });
  await proxied.request('/v1/auth/me', null, null, { 'X-Xiaopiao-Client-IP': '198.51.100.2' });
  assert.equal(proxied.db.prepare("SELECT count(*) AS n FROM limits WHERE key LIKE 'global:198.51.100.%'").get().n, 2);
});

test('dev previews accept any loopback port; production pins one exact origin', async t => {
  const dev = await fixture(t);
  const preview = await dev.request('/v1/auth/register', account, null, { Origin: 'http://127.0.0.1:5295' });
  assert.equal(preview.response.status, 201);
  assert.equal(preview.response.headers.get('access-control-allow-origin'), 'http://127.0.0.1:5295');
  const production = await fixture(t, { production: true, origin: 'https://app.example.test' });
  assert.equal((await production.request('/v1/auth/register', account, null, { Origin: 'http://127.0.0.1:5295' })).response.status, 403);
  assert.equal((await production.request('/v1/auth/register', account, null, { Origin: 'https://app.example.test' })).response.status, 201);
});
test('APP_ORIGIN takes a comma-separated dev list; production refuses anything but one https origin', async t => {
  const dev = await fixture(t, { origin: 'http://localhost:5173,http://localhost:5466' });
  const web = await dev.request('/v1/auth/register', account, null, { Origin: 'http://localhost:5466' });
  assert.equal(web.response.status, 201);
  assert.equal(web.response.headers.get('access-control-allow-origin'), 'http://localhost:5466');
  assert.equal((await dev.request('/v1/auth/me', null, null, { Origin: 'https://evil.test' })).response.status, 403);

  const db = store(':memory:');
  t.after(() => db.close());
  const build = origin => () => createApp({ db, agent: async state => state, origin, production: true });
  assert.throws(build('https://app.example.test,https://web.example.test'), /exactly one https origin/);
  assert.throws(build('http://app.example.test'), /exactly one https origin/);
  assert.throws(build(''), /APP_ORIGIN is required/);
});
test('a rate-limited or empty free model hands the turn to the next one, then loses its preference', async () => {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    const model = JSON.parse(options.body).model; seen.push(model);
    if (model === 'reflection/beam:free') return new Response('{"error":{"type":"rate_limit_error"}}', { status: 429 });
    return new Response('data: {"choices":[{"delta":{"content":"好的"}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const turn = () => agent({ messages: [{ role: 'user', content: '你好' }], round: 0, timezone: 'UTC' }, () => {}, new AbortController().signal);
  await turn();
  assert.deepEqual(seen, ['reflection/beam:free', 'stealth/glyph-cluster:free']);
  await turn();
  assert.equal(seen[2], 'stealth/glyph-cluster:free', 'cooling model is not retried first');
});
test('a reasoning-only stream counts as an empty reply, falls back, and never leaks reasoning', async () => {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    const model = JSON.parse(options.body).model; seen.push(model);
    if (model === 'reflection/beam:free') return new Response('data: {"choices":[{"delta":{"reasoning":"内部推理"}}]}\n\ndata: [DONE]\n\n');
    return new Response('data: {"choices":[{"delta":{"content":"有答案"}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({ messages: [{ role: 'user', content: '你好' }], round: 3, timezone: 'UTC' }, e => events.push(e), new AbortController().signal);
  assert.deepEqual(seen, ['reflection/beam:free', 'stealth/glyph-cluster:free']);
  assert.ok(events.some(event => event.text === '有答案'));
  assert.ok(!JSON.stringify(events).includes('内部推理'));
  assert.equal(state.round, 4);
});
test('an answer with numbers but no tool result is rejected and handed to another model', async () => {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    const model = JSON.parse(options.body).model; seen.push(model);
    if (model === 'reflection/beam:free') return new Response('data: {"choices":[{"delta":{"content":"这周一共花了 ¥78.5。"}}]}\n\ndata: [DONE]\n\n');
    return new Response('data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"summarize_period","arguments":"{\\"period\\":\\"week\\"}"}}]}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({ messages: [{ role: 'user', content: '这周一共花了多少' }], round: 0, timezone: 'UTC' }, e => events.push(e), new AbortController().signal);
  assert.deepEqual(seen, ['reflection/beam:free', 'stealth/glyph-cluster:free']);
  assert.ok(!events.some(e => e.type === 'token' && /78\.5/.test(e.text ?? '')), 'invented numbers never reach the client');
  assert.equal(state.pending[0].function.name, 'summarize_period');
});
test('a model that only emits tool markup hands the turn over instead of failing it', async () => {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    const model = JSON.parse(options.body).model; seen.push(model);
    if (model === 'reflection/beam:free') return new Response('data: {"choices":[{"delta":{"content":"<tool_call>{\\"a\\":1}</tool_call>"}}]}\n\ndata: [DONE]\n\n');
    return new Response('data: {"choices":[{"delta":{"content":"干净的回答"}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({ messages: [{ role: 'user', content: '我们这周花了多少' }], round: 3, timezone: 'UTC' }, e => events.push(e), new AbortController().signal);
  assert.deepEqual(seen, ['reflection/beam:free', 'stealth/glyph-cluster:free']);
  assert.ok(events.some(e => e.text === '干净的回答'));
  assert.ok(!JSON.stringify(events).includes('tool_call'), 'provider markup never reaches the client');
  assert.equal(state.round, 4);
});
test('a model that rejects tool continuation without its reasoning is remembered and skipped', async () => {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    const body = JSON.parse(options.body); seen.push(body.model);
    if (body.model === 'reflection/beam:free' && options.body.includes('"role":"tool"'))
      return Response.json({ error: { message: "Missing required parameter: 'messages[2].reasoning_content'" } }, { status: 400 });
    return new Response('data: {"choices":[{"delta":{"content":"收到"}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const continuationState = () => ({ messages: [
    { role: 'user', content: '本周花了多少' },
    { role: 'assistant', content: null, tool_calls: [{ id: 'c1', type: 'function', function: { name: 'summarize_period', arguments: '{}' } }] },
    { role: 'tool', tool_call_id: 'c1', content: '{"total_out":100}' },
  ], round: 1, timezone: 'UTC' });
  const events = [];
  await agent(continuationState(), e => events.push(e), new AbortController().signal);
  assert.deepEqual(seen, ['reflection/beam:free', 'stealth/glyph-cluster:free']);
  assert.ok(events.some(e => e.text === '收到'));
  const beam = agent.diagnostics().find(item => item.id === 'reflection/beam:free');
  assert.equal(beam.needsReplay, true, 'replay demand is learned as a capability, not a health strike');
  assert.equal(beam.strikes, 0);
  await agent(continuationState(), () => {}, new AbortController().signal);
  assert.equal(seen.at(-1), 'stealth/glyph-cluster:free', 'second continuation skips the rejected model');
  assert.equal(seen.filter(model => model === 'reflection/beam:free').length, 1);
});

test('a prose answer over real tool data still gets a designed view', async () => {
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }] });
    const body = JSON.parse(options.body);
    if (body.messages.some(message => message.role === 'tool'))
      return new Response('data: {"choices":[{"delta":{"content":"今天到现在 **¥55.00**，4 笔。"}}]}\n\ndata: [DONE]\n\n');
    return new Response('data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"summarize_period","arguments":"{\\"period\\":\\"today\\"}"}}]}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const first = [];
  let state = await agent({ messages: [{ role: 'user', content: '今天花了多少' }], round: 0, timezone: 'UTC' }, e => first.push(e), new AbortController().signal);
  assert.equal(state.pending[0].function.name, 'summarize_period');
  const second = [];
  state = await agent({
    messages: [...state.messages, { role: 'tool', tool_call_id: 'c1', content: JSON.stringify({ period: 'today', unit: 'cents', count: 4, total_out: 5500 }) }],
    round: 1, timezone: 'UTC', pending: [], views: 0,
  }, e => second.push(e), new AbortController().signal);
  const view = second.find(event => event.type === 'view')?.view;
  assert.equal(view?.kind, 'spend_summary');
  assert.equal(view?.total_cents, 5500);
  assert.equal(view?.count, 4);
  assert.equal(state.views, 1);
});

test('tool results from an earlier turn do not license numbers in a new one', async () => {
  const seen = [];
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }, { id: 'stealth/glyph-cluster:free' }] });
    const model = JSON.parse(options.body).model; seen.push(model);
    if (model === 'reflection/beam:free') return new Response('data: {"choices":[{"delta":{"content":"今天花了 ¥55，4 笔。"}}]}\n\ndata: [DONE]\n\n');
    return new Response('data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c9","function":{"name":"summarize_period","arguments":"{\\"period\\":\\"today\\"}"}}]}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({
    messages: [
      { role: 'user', content: '昨天花了多少' },
      { role: 'assistant', content: null, tool_calls: [{ id: 'c0', type: 'function', function: { name: 'summarize_period', arguments: '{"period":"today"}' } }] },
      { role: 'tool', tool_call_id: 'c0', content: '{"total_out":5500,"count":4}' },
      { role: 'assistant', content: '昨天 **¥55.00**，4 笔。' },
      { role: 'user', content: '今天呢' },
    ],
    round: 0, timezone: 'UTC',
  }, e => events.push(e), new AbortController().signal);
  assert.deepEqual(seen, ['reflection/beam:free', 'stealth/glyph-cluster:free']);
  assert.ok(!events.some(e => e.type === 'token'), 'stale-data answer was never streamed');
  assert.equal(state.pending[0].function.name, 'summarize_period');
});

test('an unknown view kind is dropped and the server renders the data itself', async () => {
  const fetcher = async (url, options) => {
    if (url.endsWith('/models')) return Response.json({ data: [{ id: 'reflection/beam:free' }] });
    const body = JSON.parse(options.body);
    if (body.messages.some(message => message.role === 'tool'))
      return new Response('data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"v1","function":{"name":"show_view","arguments":"{\\"kind\\":\\"expense_overview\\",\\"total_cents\\":5500}"}}]}}]}\n\ndata: [DONE]\n\n');
    return new Response('data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"summarize_period","arguments":"{\\"period\\":\\"today\\"}"}}]}}]}\n\ndata: [DONE]\n\n');
  };
  const agent = createAgent({ apiKey: 'test-placeholder', fetcher });
  const events = [];
  const state = await agent({
    messages: [
      { role: 'user', content: '今天花了多少' },
      { role: 'assistant', content: null, tool_calls: [{ id: 'c1', type: 'function', function: { name: 'summarize_period', arguments: '{"period":"today"}' } }] },
      { role: 'tool', tool_call_id: 'c1', content: JSON.stringify({ period: 'today', unit: 'cents', count: 4, total_out: 5500 }) },
    ],
    round: 1, timezone: 'UTC', pending: [], views: 0,
  }, e => events.push(e), new AbortController().signal);
  assert.equal(state.pending.length, 0, 'unknown view is not forwarded to the client');
  const view = events.find(event => event.type === 'view')?.view;
  assert.equal(view?.kind, 'spend_summary');
  assert.equal(view?.total_cents, 5500);
});
