/**
 * Free-model routing: per-model health, observed latency and quota spread.
 * Pure state machine so tests can drive it without a clock, network or provider.
 */

const COOLDOWN = {
  rate_limit: [120_000, 30 * 60_000],
  unavailable: [20_000, 5 * 60_000],
  timeout: [45_000, 5 * 60_000],
  empty: [120_000, 10 * 60_000],
  invalid: [600_000, 30 * 60_000],
};
const DEFAULT_LATENCY = 3_000;
const UNKNOWN_BUDGET = 9_000;
const MAX_BUDGET = 20_000;
/** Successful calls inside this window count against a model, spreading free quota. */
const USE_WINDOW = 10 * 60_000;

export function createModelRouter({ models, now = Date.now }) {
  const entries = new Map(models.map(id => [id, {
    id, latency: 0, samples: 0, floor: 0, strikes: 0, uses: [],
    cooldownUntil: 0, lastFailure: null, textWithoutTools: false, needsReplay: false, toolTurns: 0,
  }]));
  // Ordering uses successful samples only; a timeout is a lower bound on the budget, not a latency sample.
  const estimate = entry => entry.samples ? entry.latency : DEFAULT_LATENCY;
  const recentUses = (entry, at) => {
    entry.uses = entry.uses.filter(time => at - time < USE_WINDOW);
    return entry.uses.length;
  };
  function score(entry, at, needsTools) {
    return estimate(entry)
      + entry.strikes * 1_500
      + recentUses(entry, at) * 1_200
      - (needsTools && entry.toolTurns ? 700 : 0);
  }
  function order({ needsTools = true, continuation = false } = {}) {
    const at = now();
    const candidates = [...entries.values()]
      // A model that answered nothing when tools were not offered is only retried with tools available.
      .filter(entry => needsTools || !entry.textWithoutTools)
      // Some free models demand their own reasoning back on tool results; that reasoning is never kept.
      .filter(entry => !(continuation && entry.needsReplay))
      .map(entry => ({ entry, ready: entry.cooldownUntil <= at }))
      .sort((a, b) => (Number(b.ready) - Number(a.ready)) || (score(a.entry, at, needsTools) - score(b.entry, at, needsTools)));
    if (!candidates.length) return [];
    const ready = candidates.filter(item => item.ready).map(item => item.entry.id);
    // Everything cooling down: still offer the earliest expiry so a request is not blocked outright.
    return ready.length ? ready : [candidates[0].entry.id];
  }
  function report(id, outcome) {
    const entry = entries.get(id);
    if (!entry) return;
    const at = now();
    if (outcome.ok) {
      entry.strikes = 0;
      entry.cooldownUntil = 0;
      entry.lastFailure = null;
      entry.uses.push(at);
      if (outcome.needsTools) entry.toolTurns++;
      if (Number.isFinite(outcome.latencyMs) && outcome.latencyMs > 0) {
        entry.latency = entry.samples ? entry.latency * 0.6 + outcome.latencyMs * 0.4 : outcome.latencyMs;
        entry.samples++;
      }
      return;
    }
    if (outcome.kind === 'aborted') return;
    if (outcome.kind === 'replay_required') {
      // Capability gap, not ill health: usable for the first round, skipped once tool results exist.
      entry.needsReplay = true;
      entry.lastFailure = 'replay_required';
      return;
    }
    entry.strikes++;
    entry.lastFailure = outcome.kind ?? 'unavailable';
    // The first timeout only calibrates the budget: retry at once with the larger budget instead of resting.
    if (entry.lastFailure === 'timeout' && entry.strikes === 1 && Number.isFinite(outcome.latencyMs)) {
      entry.floor = Math.max(entry.floor, outcome.latencyMs);
      entry.cooldownUntil = 0;
      return;
    }
    if (entry.lastFailure === 'empty') entry.textWithoutTools = entry.textWithoutTools || !outcome.needsTools;
    const [base, cap] = COOLDOWN[entry.lastFailure] ?? COOLDOWN.unavailable;
    entry.cooldownUntil = at + Math.min(cap, base * 2 ** Math.min(entry.strikes - 1, 4));
    // A timeout only proves the answer takes at least that long: raise the budget floor, keep ordering clean.
    if (entry.lastFailure === 'timeout' && Number.isFinite(outcome.latencyMs)) {
      entry.floor = Math.max(entry.floor, outcome.latencyMs);
    }
  }
  function firstTokenBudget(id) {
    const entry = entries.get(id);
    const observed = Math.max(entry?.samples ? entry.latency : 0, entry?.floor ?? 0);
    if (!observed) return UNKNOWN_BUDGET;
    return Math.min(MAX_BUDGET, Math.max(UNKNOWN_BUDGET, Math.round(observed * 2.5)));
  }
  /** Diagnostics for operators; never contains prompts, keys or user data. */
  function snapshot() {
    const at = now();
    return [...entries.values()].map(entry => ({
      id: entry.id, ready: entry.cooldownUntil <= at, strikes: entry.strikes,
      latency: entry.samples ? Math.round(entry.latency) : null, floor: entry.floor, recentUses: recentUses(entry, at),
      lastFailure: entry.lastFailure, textWithoutTools: entry.textWithoutTools, needsReplay: entry.needsReplay,
    }));
  }
  return { order, report, firstTokenBudget, snapshot };
}
