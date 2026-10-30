import { randomUUID } from 'node:crypto';
import { createModelRouter } from './router.mjs';
import { buildSystemPrompt } from './prompt.mjs';
import { commandcode, endpoints, headersFor } from './providers.mjs';
import { createStreamSanitizer } from './sanitize.mjs';
import { viewFromToolResults } from './views.mjs';
import { VIEWS } from './prompt.mjs';

const enumString = values => ({ type: 'string', enum: values });
const period = enumString(['today', 'week', 'month']);
const schema = properties => ({ type: 'object', properties, additionalProperties: false });
export const toolDefinitions = [
  ['summarize_period', '汇总本机账本，只返回统计结果；不上传账单原文。', schema({ period, group_by: enumString(['day', 'category']) })],
  ['find_peak', '查本机账本中某周期最大的一项。', schema({ period, dimension: enumString(['day', 'category', 'merchant']) })],
  ['query_transactions', '按时间查询本机结构化记录，最多20条，不读取原文或备注。', schema({ from: { type: 'integer' }, to: { type: 'integer' }, limit: { type: 'integer', minimum: 1, maximum: 20 } })],
  ['read_circle', '仅查询明确授权共享的圈子数据；没有授权返回未共享。', schema({ period, scope: enumString(['me', 'family']) })],
].map(([name, description, parameters]) => ({ type: 'function', function: { name, description, parameters } }));

/** The only way the assistant presents anything: pick a view, fill it with numbers from the tools above. */
const viewKinds = ['spend_summary', 'category_split', 'trend', 'entries', 'peak', 'weekly_report', 'subscriptions', 'draft_list'];
const viewRow = { type: 'object', properties: {
  label: { type: 'string' }, merchant: { type: 'string' }, category: { type: 'string' },
  cents: { type: 'integer' }, occurred_at: { type: 'integer' }, cadence: { type: 'string' },
}, additionalProperties: false };
toolDefinitions.push({ type: 'function', function: {
  name: 'show_view',
  description: '用取数工具返回的数字渲染一个界面视图。先取数，再调用一次；数字必须与工具结果一致，单位是分。',
  parameters: schema({
    kind: enumString(viewKinds),
    period: enumString(['today', 'week', 'month']),
    title: { type: 'string' },
    total_cents: { type: 'integer' },
    count: { type: 'integer' },
    rows: { type: 'array', items: viewRow },
    points: { type: 'array', items: viewRow },
    label: { type: 'string' },
    dimension: enumString(['day', 'category', 'merchant']),
    week_label: { type: 'string' },
    top_category: { type: 'string' },
  }),
} });
/** Parses arbitrary UTF-8/SSE boundaries; ignores comments and multiline data is reassembled. */
export async function* sseData(body) {
  const decoder = new TextDecoder();
  let buffer = '';
  for await (const chunk of body) {
    buffer += decoder.decode(chunk, { stream: true });
    let match;
    while ((match = /\r?\n\r?\n/.exec(buffer))) {
      const frame = buffer.slice(0, match.index);
      buffer = buffer.slice(match.index + match[0].length);
      const data = frame.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
      if (data) yield data;
    }
    if (buffer.length > 256_000) throw new Error('UPSTREAM_FRAME_TOO_LARGE');
  }
  if (buffer.trim()) throw new Error('UPSTREAM_TRUNCATED');
}
/** Reads only enough of a provider error to route it; bodies are never logged, stored or forwarded. */
async function providerNote(response) {
  try { return (await response.text()).slice(0, 512).toLowerCase(); } catch { return ''; }
}
export function createAgent({ apiKey, fetcher = fetch, provider = commandcode, models = provider.models, now = Date.now }) {
  const router = createModelRouter({ models, now });
  const urls = endpoints(provider);
  let discovery = null;
  async function discover(signal) {
    if (discovery && discovery.until > now()) return discovery.models;
    try {
      const response = await fetcher(urls.models, { headers: headersFor(provider, apiKey), signal });
      if (!response.ok) throw new Error('MODEL_DISCOVERY_FAILED');
      const body = await response.json();
      const ids = new Set((body.data ?? []).map(item => item.id));
      const live = models.filter(id => ids.has(id));
      // Static allowlist is free-tier only, so a discovery hiccup degrades ordering, not availability.
      discovery = { models: live.length ? live : models, until: now() + 300_000 };
    } catch (error) {
      if (signal?.aborted) throw error;
      discovery = { models, until: now() + 30_000 };
    }
    return discovery.models;
  }
  /** Failure kinds drive how long a free model rests before it is tried again. */
  function classify({ status, note = '', error, started, firstTimedOut, signal, deadline }) {
    if (signal?.aborted) return 'aborted';
    // Nothing usable was produced (only markup, or numbers with no tool source): another model may answer.
    if (error?.message === 'EMPTY_RESPONSE') return 'empty';
    if (error?.message === 'UNSOURCED_NUMBER') return 'invalid';
    if (started) return 'aborted';
    if (status === 429) return 'rate_limit';
    if ((status === 400 || status === 422) && note.includes('reasoning_content')) return 'replay_required';
    if (['UNKNOWN_TOOL', 'INVALID_ARGUMENTS', 'OUTPUT_TRUNCATED', 'ARGUMENT_LIMIT', 'TOOL_LIMIT'].includes(error?.message)) return 'invalid';
    if (firstTimedOut || deadline?.aborted || error?.name === 'TimeoutError') return 'timeout';
    return 'unavailable';
  }
  async function turn(state, emit, signal) {
    if (!apiKey) throw new Error('MODEL_UNAVAILABLE');
    if (state.round >= 4) throw new Error('ROUND_LIMIT');
    const needsTools = state.round < 3;
    const live = new Set(await discover(signal));
    // Grounding and view synthesis only trust results fetched for the question being answered now.
    const turnStart = state.messages.findLastIndex(message => message.role === 'user');
    const thisTurn = state.messages.slice(turnStart + 1);
    const continuation = thisTurn.some(message => message.role === 'tool');
    // A view built from this turn's own tool numbers, ready to show if the model answers in prose.
    const groundedView = viewFromToolResults(thisTurn);
    const candidates = router.order({ needsTools, continuation }).filter(model => live.has(model));
    if (!candidates.length) throw new Error('MODEL_UNAVAILABLE');
    let announced = false;
    for (const model of candidates) {
      if (signal.aborted) throw signal.reason ?? new Error('ABORTED');
      const budget = router.firstTokenBudget(model);
      const startedAt = now();
      let started = false;
      let status = 0;
      let note = '';
      const firstToken = AbortSignal.timeout(budget);
      const controller = new AbortController();
      const deadline = AbortSignal.timeout(budget + 12_000);
      const onFirstTimeout = () => { if (!started) controller.abort(); };
      const onDeadline = () => { if (!started) controller.abort(); };
      firstToken.addEventListener('abort', onFirstTimeout, { once: true });
      deadline.addEventListener('abort', onDeadline, { once: true });
      try {
        if (!announced) { emit({ type: 'status', text: '正在理解问题' }); announced = true; }
        else emit({ type: 'status', text: '正在组织回答' });
        let firstTokenAt = 0;
        const response = await fetcher(urls.chat, {
          method: 'POST', signal: AbortSignal.any([signal, controller.signal]),
          headers: { ...headersFor(provider, apiKey), 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model, stream: true, max_tokens: 1600,
            messages: [{ role: 'system', content: buildSystemPrompt({ timezone: state.timezone }) }, ...state.messages],
            ...(needsTools && provider.supportsTools ? { tools: toolDefinitions } : {}),
          }),
        });
        status = response.status;
        if (!response.ok) { note = await providerNote(response); throw new Error(status === 429 ? 'UPSTREAM_RATE_LIMIT' : 'UPSTREAM_FAILED'); }
        let content = '', done = false;
        const calls = new Map();
        const sanitizer = createStreamSanitizer();
        // Short answers are held for a beat so a fabricated number is caught before the user sees it.
        // Anything longer than the gate streams live; it is already showing real content by then.
        let gate = '';
        let streaming = false;
        const flush = (text) => { if (text) emit({ type: 'token', text }); };
        const pushContent = (text) => {
          if (streaming) { flush(text); return; }
          gate += text;
          if (gate.length >= 64) { streaming = true; flush(gate); gate = ''; }
        };
        for await (const data of sseData(response.body)) {
          if (data === '[DONE]') { done = true; break; }
          const choice = JSON.parse(data).choices?.[0];
          const delta = choice?.delta;
          if (!delta) continue;
          if (delta.content || delta.tool_calls?.length) { started = true; firstTokenAt ||= now(); }
          // Provider reasoning_content is private. UI receives only actual operation summaries.
          if (delta.content) {
            const clean = sanitizer.push(delta.content);
            if (clean) {
              content += clean;
              if (content.length > 24_000) throw new Error('OUTPUT_LIMIT');
              pushContent(clean);
            }
          }
          if (delta.tool_calls?.length) { streaming = true; flush(gate); gate = ''; }
          for (const part of delta.tool_calls ?? []) {
            const call = calls.get(part.index) ?? { id: '', type: 'function', function: { name: '', arguments: '' } };
            call.id += part.id ?? '';
            call.function.name += part.function?.name ?? '';
            call.function.arguments += part.function?.arguments ?? '';
            if (calls.size >= 4 && !calls.has(part.index)) throw new Error('TOOL_LIMIT');
            if (call.function.arguments.length > 4096) throw new Error('ARGUMENT_LIMIT');
            calls.set(part.index, call);
          }
          if (choice.finish_reason === 'length') throw new Error('OUTPUT_TRUNCATED');
        }
        if (!done) throw new Error('UPSTREAM_TRUNCATED');
        const rest = sanitizer.finish();
        if (rest) { content += rest; pushContent(rest); }
        const pending = [...calls.values()];
        for (const call of pending) {
          if (!toolDefinitions.some(tool => tool.function.name === call.function.name)) throw new Error('UNKNOWN_TOOL');
          if (!call.id) call.id = randomUUID();
          const args = JSON.parse(call.function.arguments || '{}');
          if (args === null || Array.isArray(args) || typeof args !== 'object') throw new Error('INVALID_ARGUMENTS');
        }
        const usable = pending.filter(call => {
          if (call.function.name !== 'show_view') return true;
          try {
            const args = JSON.parse(call.function.arguments || '{}');
            return VIEWS.some(([kind]) => kind === args.kind);
          } catch { return false; }
        });
        pending.length = 0; pending.push(...usable);
        // The model answered in prose or asked for an unknown view: render the view ourselves.
        const synthesized = !state.views && !pending.some(call => call.function.name === 'show_view') ? groundedView : null;
        // A view (model's or ours) is a complete answer: no text required.
        if (!pending.length && !content.trim() && !synthesized && !state.views) throw new Error('EMPTY_RESPONSE');
        // A number without tool data is a hallucination the user cannot verify: retry on another model.
        // Only possible while the answer is still inside the gate (nothing has been shown yet).
        if (!streaming && !pending.length && !groundedView && /[0-9]/.test(content)) throw new Error('UNSOURCED_NUMBER');
        // The answer was short enough to stay gated: it is legitimate, so show it now, in one piece.
        if (!streaming && gate) { const held = gate; gate = ''; streaming = true; flush(held); }
        state.messages.push({ role: 'assistant', content: content || null, ...(pending.length ? { tool_calls: pending } : {}) });
        state.views = (state.views ?? 0) + pending.filter(call => call.function.name === 'show_view').length;
        state.pending = pending;
        state.round++;
        router.report(model, { ok: true, latencyMs: firstTokenAt ? firstTokenAt - startedAt : undefined, needsTools });
        if (synthesized) { state.views = 1; emit({ type: 'view', view: synthesized }); }
        return state;
      } catch (error) {
        const kind = classify({ status, note, error, started, firstTimedOut: firstToken.aborted, signal, deadline });
        if (kind === 'aborted') throw error;
        router.report(model, { ok: false, kind, latencyMs: (started ? 0 : now() - startedAt), needsTools });
      } finally {
        firstToken.removeEventListener('abort', onFirstTimeout);
        deadline.removeEventListener('abort', onDeadline);
      }
    }
    throw new Error('MODEL_UNAVAILABLE');
  }
  // Operator diagnostics only: model health, no prompts, no user data.
  turn.diagnostics = () => router.snapshot();
  return turn;
}
