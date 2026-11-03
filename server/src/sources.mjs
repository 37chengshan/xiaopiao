/**
 * Number grounding. A reply may only contain numbers that are traceable to something the person can check:
 * the current question's tool results, or the person's own words.
 *
 * Three allowances, each deliberate:
 * - cents → yuan: tools speak integer cents, the model writes 「¥12.30」.
 * - rounding: a stored 1820.4 kcal may be spoken as 1820; a percentage may be rounded.
 * - simple arithmetic over what is already there: 「15 元 × 30 天」 → 450, and 15×30 is in the source set.
 *
 * Nothing else. Tool results that failed (`{error: …}`, `TOOL_NOT_ALLOWED`) and results with no numbers contribute
 * nothing, so "the model called some tool" is never by itself permission to invent a figure.
 */
const NUMBER = /[-+]?\d[\d,]*(?:\.\d+)?/g;
const round2 = value => Math.round(value * 100) / 100;

/** Every number in a piece of text, with thousands separators dropped. */
export function numbersIn(text) {
  if (typeof text !== 'string' || !text) return [];
  return (text.match(NUMBER) ?? [])
    .map(token => Number(token.replace(/,/g, '')))
    .filter(value => Number.isFinite(value));
}

/** Numbers inside a tool result: JSON numbers, numeric strings, and digits embedded in labels like `2026-11-01`. */
function collect(value, into) {
  if (typeof value === 'number') { if (Number.isFinite(value)) into.push(value); return; }
  if (typeof value === 'string') { into.push(...numbersIn(value)); return; }
  if (Array.isArray(value)) { for (const item of value) collect(item, into); return; }
  if (value && typeof value === 'object') for (const item of Object.values(value)) collect(item, into);
}

/**
 * The set of numbers a reply may use: everything in this question's tool results (`index > turnStart`), plus every
 * user message in the conversation, plus the derived forms above. A tool result from an earlier question is not a
 * source: the reply would be quoting figures for a question nobody asked.
 */
export function buildSources(messages, turnStart = -1) {
  const raw = [];
  for (const [index, message] of messages.entries()) {
    if (message.role === 'user') { raw.push(...numbersIn(message.content)); continue; }
    if (message.role !== 'tool' || index <= turnStart) continue;
    let parsed;
    try { parsed = JSON.parse(message.content); } catch { raw.push(...numbersIn(message.content)); continue; }
    // A failed tool call carries no data, so it licenses nothing — even if the error text happens to hold digits.
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && parsed.error) continue;
    collect(parsed, raw);
  }
  const values = new Set();
  const add = value => { if (Number.isFinite(value)) values.add(round2(value)); };
  for (const value of raw) {
    add(value);
    // Integer cents are the most common unit; the person reads yuan.
    if (Number.isInteger(value)) add(value / 100);
    add(Math.round(value));
  }
  // 模拟后果: sums and products of two real numbers ("15 元 × 30 天 = 450").
  const base = raw.filter(Number.isFinite).slice(0, 60);
  for (let i = 0; i < base.length; i++) {
    for (let j = i; j < base.length; j++) {
      add(base[i] + base[j]);
      add(base[i] * base[j]);
      add(Math.abs(base[i] - base[j]));
    }
  }
  return values;
}

/** The numbers in `text` that no source explains. Empty means the reply is fully grounded. */
export function unsourcedNumbers(text, sources) {
  return numbersIn(text).filter(value => !sources.has(round2(value)));
}
