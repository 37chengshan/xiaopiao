import { randomUUID } from 'node:crypto';
import { createModelRouter } from './router.mjs';
import { buildSystemPrompt } from './prompt.mjs';
import { commandcode, endpoints, headersFor } from './providers.mjs';
import { createStreamSanitizer } from './sanitize.mjs';
import { viewFromToolResults } from './views.mjs';
import { textBounded } from './bounded.mjs';
import { buildSources, unsourcedNumbers } from './sources.mjs';
import {
  DATA_TOOLS, SERVER_TOOLS, TOOL_LABELS, VIEW_KINDS, normalizeDraft, normalizePlan, toolDefinitions,
} from './tools.mjs';

export { toolDefinitions } from './tools.mjs';

/**
 * Turn budgets. Eight rounds is enough for "计划 → 预习 → 取数 → 视图" with room for a correction, and it is a
 * hard stop: a free model that keeps asking for tools must not turn one question into an open-ended session.
 * `turnMs` is the wall-clock cap across *all* HTTP requests of one question, because a continuation starts a new
 * request and a per-request timeout cannot see the previous ones.
 */
export const AGENT_LIMITS = Object.freeze({ rounds: 8, turnMs: 150_000, calls: 6, planPasses: 2 });

/**
 * Last-line filter for the boundaries in `docs/SPEC.md` (no advice on what to eat, no weight-loss plans, no medical
 * guidance). The primary control is the prompt; this catches a model that ignores it before the text is shown.
 * Deliberately narrow: only shapes that are always forbidden, never a judgement word on its own, so a refusal
 * sentence such as 「我只帮记录、复盘和算账，吃什么由你决定」 passes untouched.
 */
export const ADVICE_PATTERNS = [
  // Direct or softened Chinese suggestions about what to eat or drink.
  /(建议|推荐|应该|最好|不妨|可以|尽量|记得)(你|您)?(多|少|别|不要|不)?(吃|喝|选|改|换|忌口)/,
  // Imperatives that dropped the verb «建议»: 「多喝水」「少吃点炸鸡」. Past/completed forms (「少吃了 3 顿」) are a
  // review, not advice, so they are excluded.
  /(多|少)(吃|喝)(?!了|过)/,
  /忌口|戒掉|控制饮食|管住嘴|别吃了|别喝了/,
  /(减肥|减重|增肌|瘦身)(计划|方案|目标|食谱|餐)/,
  /帮你(减肥|减重|瘦)/,
  /你(吃|喝)得(太|有点|比较|不够)(多|少|油腻|咸|甜)/,
  /(营养|健康)(有|会|存在)?(风险|隐患|问题|不良|负担)/,
  /可能导致|会引起|不利于/,
  // English is a first language for part of the audience; the same imperative shapes must be caught.
  /you (?:should|might|may|had better|need to)\s+(?:eat|drink|try|avoid|cut)/i,
  /(?:try|consider)\s+(?:eating|drinking|cutting|avoiding)/i,
  /cut down on|cut back on/i,
  /I(?:'| a)?d? (?:recommend|suggest)/i,
  /(?:eat|drink) (?:more|less)\b/i,
  /(?:reduce|lower) your (?:intake|calories|sugar|salt|fat)/i,
  /weight[- ]loss plan|lose weight/i,
  /(?:healthy|healthier) (?:choice|option|alternative)/i,
];

/** Parses arbitrary UTF-8/SSE boundaries; ignores comments and multiline data is reassembled. */
export async function* sseData(body) {
  const decoder = new TextDecoder();
  let buffer = '', bytes = 0;
  for await (const chunk of body) {
    bytes += chunk.byteLength;
    if (bytes > 2_000_000) throw new Error('UPSTREAM_BODY_TOO_LARGE');
    buffer += decoder.decode(chunk, { stream: true });
    let match;
    while ((match = /\r?\n\r?\n/.exec(buffer))) {
      if (match.index > 256_000) throw new Error('UPSTREAM_FRAME_TOO_LARGE');
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
const boundedText = (response, limit) => textBounded(response, limit, { code: 'UPSTREAM_BODY_TOO_LARGE' });
async function providerNote(response) {
  try { return (await boundedText(response, 4096)).slice(0, 512).toLowerCase(); } catch { return ''; }
}

/** The messages of the question being answered now; earlier turns never license numbers or views. */
const currentTurn = state => state.messages.slice(state.messages.findLastIndex(message => message.role === 'user') + 1);

/** Every tool call name → the tool it belongs to, for the calls the current question has produced. */
function callNames(messages) {
  const names = new Map();
  for (const message of messages) {
    if (message.role !== 'assistant' || !Array.isArray(message.tool_calls)) continue;
    for (const call of message.tool_calls) names.set(call.id, call.function?.name);
  }
  return names;
}

/** True when this question already got at least one real data result: it decides whether a wrap-up line is honest. */
function hasDataResult(messages) {
  const names = callNames(messages);
  return messages.some(message => message.role === 'tool' && DATA_TOOLS.includes(names.get(message.tool_call_id)));
}

/**
 * Out of rounds or out of time: keep what the tools already returned. A synthesized view is built from those
 * numbers; with numbers but no matching view the person still gets one honest line instead of an empty bubble.
 */
function finish(state, emit) {
  state.pending = [];
  state.truncated = true;
  const turn = currentTurn(state);
  if (!state.views) {
    const view = viewFromToolResults(turn);
    if (view) { state.views = 1; emit({ type: 'view', view }); return state; }
    if (hasDataResult(turn)) emit({ type: 'token', text: '这一轮查得比较多，先把已经查到的结果给你。' });
  }
  return state;
}

export function createAgent({ apiKey, fetcher = fetch, provider = commandcode, models = provider.models, now = Date.now }) {
  const router = createModelRouter({ models, now });
  const urls = endpoints(provider);
  let discovery = null;
  async function discover(signal) {
    if (discovery && discovery.until > now()) return discovery.models;
    try {
      const response = await fetcher(urls.models, { headers: headersFor(provider, apiKey), redirect: 'error', signal });
      if (!response.ok) { await response.body?.cancel(); throw new Error('MODEL_DISCOVERY_FAILED'); }
      const body = JSON.parse(await boundedText(response, 512_000));
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
    // A draft with a wrong shape is a wrong answer, not a transport failure: try the next model.
    if (error?.message === 'INVALID_DRAFT') return 'invalid';
    // Out-of-scope advice is the same kind of failure: nothing was shown, so another model may do better.
    if (error?.message === 'OUT_OF_SCOPE_ADVICE') return 'invalid';
    if (started) return 'aborted';
    if (status === 429) return 'rate_limit';
    if ((status === 400 || status === 422) && note.includes('reasoning_content')) return 'replay_required';
    if (['UNKNOWN_TOOL', 'INVALID_ARGUMENTS', 'OUTPUT_TRUNCATED', 'ARGUMENT_LIMIT', 'TOOL_LIMIT'].includes(error?.message)) return 'invalid';
    if (firstTimedOut || deadline?.aborted || error?.name === 'TimeoutError') return 'timeout';
    return 'unavailable';
  }

  /** One model call. Returns the state, plus `replanned` when only a plan was produced (nothing to send yet). */
  async function attempt(state, emit, signal) {
    const needsTools = state.round < AGENT_LIMITS.rounds - 1;
    const live = new Set(await discover(signal));
    const thisTurn = currentTurn(state);
    const continuation = thisTurn.some(message => message.role === 'tool');
    // A view built from this turn's own tool numbers, ready to show if the model answers in prose.
    const groundedView = viewFromToolResults(thisTurn);
    // Every number the answer may use: this question's tool results plus what the person themselves typed.
    const sources = buildSources(state.messages, state.messages.length - thisTurn.length - 1);
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
      const onDeadline = () => controller.abort();
      firstToken.addEventListener('abort', onFirstTimeout, { once: true });
      deadline.addEventListener('abort', onDeadline, { once: true });
      try {
        if (!announced) { emit({ type: 'status', text: '正在理解问题' }); announced = true; }
        else emit({ type: 'status', text: '正在组织回答' });
        let firstTokenAt = 0;
        const response = await fetcher(urls.chat, {
          method: 'POST', redirect: 'error', signal: AbortSignal.any([signal, controller.signal]),
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
        // The answer is buffered to the end of the model's turn and only then shown, in one piece. Both content
        // checks (numbers must be sourced, no advice) need the *whole* answer: the offending sentence is usually
        // the last one, and a 64-character window would let a long reply stream right past them. Progress events —
        // status, plan, step — are still live, so the UI is not blank while the model writes.
        // A reply is capped at 24k characters, so this buffer is bounded as well.
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
            }
          }
          for (const part of delta.tool_calls ?? []) {
            const call = calls.get(part.index) ?? { id: '', type: 'function', function: { name: '', arguments: '' } };
            call.id += part.id ?? '';
            call.function.name += part.function?.name ?? '';
            call.function.arguments += part.function?.arguments ?? '';
            if (calls.size >= AGENT_LIMITS.calls && !calls.has(part.index)) throw new Error('TOOL_LIMIT');
            if (call.id.length > 256 || call.function.name.length > 128) throw new Error('ARGUMENT_LIMIT');
            if (call.function.arguments.length > 4096) throw new Error('ARGUMENT_LIMIT');
            calls.set(part.index, call);
          }
          if (choice.finish_reason === 'length') throw new Error('OUTPUT_TRUNCATED');
        }
        if (!done) throw new Error('UPSTREAM_TRUNCATED');
        const rest = sanitizer.finish();
        if (rest) {
          content += rest;
          if (content.length > 24_000) throw new Error('OUTPUT_LIMIT');
        }
        const pending = [...calls.values()];
        for (const call of pending) {
          if (!toolDefinitions.some(tool => tool.function.name === call.function.name)) throw new Error('UNKNOWN_TOOL');
          if (!call.id) call.id = randomUUID();
          const args = JSON.parse(call.function.arguments || '{}');
          if (args === null || Array.isArray(args) || typeof args !== 'object') throw new Error('INVALID_ARGUMENTS');
        }
        // Presentation only: an unknown view kind is dropped instead of being drawn by the client.
        const usable = pending.filter(call => {
          if (call.function.name !== 'show_view') return true;
          try {
            const args = JSON.parse(call.function.arguments || '{}');
            return VIEW_KINDS.includes(args.kind);
          } catch { return false; }
        });
        // A draft is an action proposal, so it must be exactly right or not exist at all. A model that invents a
        // field is not trustworthy about the amount either: nothing is forwarded and another model gets the turn.
        const drafts = [];
        for (const call of usable.filter(item => item.function.name === 'draft')) {
          let draft = null;
          try { draft = normalizeDraft(JSON.parse(call.function.arguments || '{}'), { now: now() }); } catch { draft = null; }
          if (!draft) throw new Error('INVALID_DRAFT');
          call.function.arguments = JSON.stringify(draft);
          drafts.push(draft);
        }
        pending.length = 0; pending.push(...usable);
        // The model answered in prose or asked for an unknown view: render the view ourselves.
        const synthesized = !state.views && !pending.some(call => call.function.name === 'show_view') ? groundedView : null;
        // A view (model's or ours) is a complete answer: no text required.
        if (!pending.length && !content.trim() && !synthesized && !state.views) throw new Error('EMPTY_RESPONSE');
        // Two checks over the complete answer, before a single character is shown:
        // - every number must be explained by a source (tool result or the person's own words);
        // - advice the product refuses (what to eat, weight-loss plans, medical guidance) is not shown at all.
        // A failure costs a retry on another model and the person sees nothing of the rejected text.
        if (content.trim()) {
          if (unsourcedNumbers(content, sources).length) throw new Error('UNSOURCED_NUMBER');
          if (ADVICE_PATTERNS.some(pattern => pattern.test(content))) throw new Error('OUT_OF_SCOPE_ADVICE');
        }
        state.messages.push({ role: 'assistant', content: content || null, ...(pending.length ? { tool_calls: pending } : {}) });
        state.round++;
        // Validated: the answer goes out in one piece. `token` keeps its existing shape, so a client needs no change.
        if (content.trim()) emit({ type: 'token', text: content });
        router.report(model, { ok: true, latencyMs: firstTokenAt ? firstTokenAt - startedAt : undefined, needsTools });
        if (synthesized) { state.views = 1; emit({ type: 'view', view: synthesized }); }
        // A plan is a note to self. It is acknowledged here and never forwarded as a tool call, so an old client
        // needs no change. Only a plan on its own costs another model call (`replanned`); a plan written together
        // with its first lookups is acknowledged and those lookups go out in the same round trip.
        const plans = pending.filter(call => call.function.name === 'plan_steps');
        const forwarded = pending.filter(call => call.function.name !== 'plan_steps');
        for (const call of plans) {
          let steps = null;
          try { steps = normalizePlan(JSON.parse(call.function.arguments || '{}')); } catch { steps = null; }
          state.messages.push({ role: 'tool', tool_call_id: call.id, content: steps ? '{"ok":true}' : '{"error":"INVALID_PLAN"}' });
          if (steps && state.planFor !== state.messages.findLastIndex(message => message.role === 'user')) {
            state.planFor = state.messages.findLastIndex(message => message.role === 'user');
            emit({ type: 'plan', steps: steps.map((step, index) => ({ index: index + 1, ...step })) });
          }
        }
        state.pending = forwarded;
        state.views = (state.views ?? 0) + forwarded.filter(call => call.function.name === 'show_view').length;
        if (plans.length && !forwarded.length) return { state, replanned: true };
        return { state, drafts };
      } catch (error) {
        const kind = classify({ status, note, error, started, firstTimedOut: firstToken.aborted, signal, deadline });
        if (kind === 'aborted') throw error;
        router.report(model, { ok: false, kind, latencyMs: (started ? 0 : now() - startedAt), needsTools });
      } finally {
        controller.abort();
        firstToken.removeEventListener('abort', onFirstTimeout);
        deadline.removeEventListener('abort', onDeadline);
      }
    }
    throw new Error('MODEL_UNAVAILABLE');
  }

  /**
   * One turn of the conversation: a model call, the plan acknowledgement, the step narration and the draft
   * announcement. Bounded by rounds and by wall time across the whole question.
   */
  async function turn(state, emit, signal) {
    if (!apiKey) throw new Error('MODEL_UNAVAILABLE');
    state.startedAt ??= now();
    state.steps ??= {};
    // Tool results arrived with this request: their steps are finished, whatever the model does next.
    for (const message of currentTurn(state)) {
      if (message.role !== 'tool') continue;
      const step = state.steps[message.tool_call_id];
      if (!step || step.done) continue;
      step.done = true;
      emit({ type: 'step', id: message.tool_call_id, tool: step.tool, label: TOOL_LABELS[step.tool] ?? '正在查询', status: 'done' });
    }
    for (let pass = 0; pass < AGENT_LIMITS.planPasses; pass++) {
      if (state.round >= AGENT_LIMITS.rounds || now() - state.startedAt > AGENT_LIMITS.turnMs) return finish(state, emit);
      const result = await attempt(state, emit, signal);
      state = result.state;
      if (result.replanned) continue;
      const forwarded = state.pending.filter(call => !SERVER_TOOLS.includes(call.function.name));
      // A plan the model never wrote: two or more lookups in one message are already a plan, shown the same way.
      const dataCalls = forwarded.filter(call => DATA_TOOLS.includes(call.function.name));
      if (dataCalls.length > 1 && state.planFor !== state.messages.findLastIndex(message => message.role === 'user')) {
        state.planFor = state.messages.findLastIndex(message => message.role === 'user');
        emit({ type: 'plan', steps: dataCalls.map((call, index) => ({ index: index + 1, tool: call.function.name, label: TOOL_LABELS[call.function.name] ?? '正在查询' })) });
      }
      for (const call of forwarded) {
        if (call.function.name === 'show_view') continue;
        const step = state.steps[call.id] ??= { tool: call.function.name, done: false };
        emit({ type: 'step', id: call.id, tool: step.tool, label: TOOL_LABELS[step.tool] ?? '正在查询', status: 'running' });
      }
      // The draft card is shown from this event; the same call still travels in `tool_calls` so a client that
      // renders from tool calls keeps working and the run protocol stays one shape.
      for (const draft of result.drafts ?? []) emit({ type: 'draft', draft });
      return state;
    }
    return finish(state, emit);
  }
  // Operator diagnostics only: model health, no prompts, no user data.
  turn.diagnostics = () => router.snapshot();
  return turn;
}
