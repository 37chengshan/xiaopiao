/**
 * Number grounding. A reply may only contain numbers that are traceable to something the person can check:
 * the current question's tool results, or the person's own words.
 *
 * What a tool result licenses:
 * - each number it contains, and that number in yuan when it is integer cents;
 * - the length of each array it contains (「3 笔」 is a count, not an estimate);
 * - within one result: the sum, mean, max and min of the same numeric field, and the difference or ratio between
 *   two *sibling* numbers (a share of a budget) or between two field totals (this week vs last week) — also in yuan.
 *   Pairwise arithmetic across rows of one field is deliberately absent: with 200 rows it would make almost any
 *   amount "traceable".
 *
 * What it does not: digits inside strings (a `day: "2026-11-03"` label is a date, not three quantities), and any
 * arithmetic that mixes numbers from *different* results — that is how 「3 笔」 and 「22%」 used to appear out of
 * nothing.
 *
 * The person's own numbers are treated more generously: they may be combined with each other freely (「15 元 ×
 * 30 天」 → 450, 「30 天 ÷ 30」 → 1), because that is what "模拟后果" means: arithmetic over what they said.
 */
// A minus sign only counts as a sign at the start of a number, so a range such as 「3-5 笔」 is two numbers.
const NUMBER = /(?<![\d.])[-+]?\d[\d,]*(?:\.\d+)?/g;
const round2 = value => Math.round(value * 100) / 100;
const round1 = value => Math.round(value * 10) / 10;

/**
 * Date and time shapes are stripped before a reply is read for numbers: 「11月3日」「11-03」「2026-11-03」「周三」
 * 「14:30」「3点20分」 are labels the person needs, not figures to verify.
 */
const DATE_TIME = [
  /\d{4}\s*[-/年]\s*\d{1,2}\s*[-/月]\s*\d{1,2}\s*日?/g,
  /\d{4}\s*[-/年]\s*\d{1,2}\s*月?/g,
  /\b\d{1,2}-(?:0[1-9]|[12]\d|3[01])\b/g,
  /\d{1,2}\s*月\s*\d{1,2}\s*日/g,
  /(?:周|星期)\s*[一二三四五六日天]/g,
  /\b\d{1,2}:\d{2}(?::\d{2})?\b/g,
  /\d{1,2}\s*点(?:\s*\d{1,2}\s*分)?/g,
];

export function stripDates(text) {
  let out = String(text);
  for (const pattern of DATE_TIME) out = out.replace(pattern, ' ');
  return out;
}

/** Every number in a piece of text, dates removed, thousands separators dropped. */
export function numbersIn(text) {
  if (typeof text !== 'string' || !text) return [];
  return (stripDates(text).match(NUMBER) ?? [])
    .map(token => Number(token.replace(/,/g, '')))
    .filter(value => Number.isFinite(value));
}

const addTo = (set, value) => {
  if (!Number.isFinite(value)) return;
  set.add(round2(value));
  set.add(round1(value));
  // Integer cents are how tools speak; the person reads yuan.
  if (Number.isInteger(value)) set.add(round2(value / 100));
};

/**
 * Totals for one *field* («all the `cents` in these rows»): sum, mean, max and min. Mixing unrelated fields would
 * let the mean of a limit and a spend pass as a real figure, so this is only used for one field at a time.
 */
function addFieldStats(set, values) {
  const numbers = values.filter(value => Number.isFinite(value));
  if (!numbers.length) return;
  const sum = numbers.reduce((total, value) => total + value, 0);
  addTo(set, sum);
  addTo(set, sum / numbers.length);
  addTo(set, Math.max(...numbers));
  addTo(set, Math.min(...numbers));
}

/** Difference and ratio (as a percentage, 0 or 1 decimal) between two numbers of the same group. */
function addPairs(set, values) {
  const numbers = values.filter(value => Number.isFinite(value));
  for (let i = 0; i < numbers.length; i++) {
    for (let j = i + 1; j < numbers.length; j++) {
      addTo(set, Math.abs(numbers[i] - numbers[j]));
      if (numbers[j] !== 0) addTo(set, (numbers[i] / numbers[j]) * 100);
      if (numbers[i] !== 0) addTo(set, (numbers[j] / numbers[i]) * 100);
    }
  }
}

/**
 * Walks one tool result: numbers, array lengths, and per-field/per-group statistics. Strings are labels and
 * contribute no numbers at all — a date is not three quantities.
 */
function collectResult(value, set) {
  if (typeof value === 'number') { addTo(set, value); return; }
  if (Array.isArray(value)) {
    addTo(set, value.length);
    const perKey = new Map();
    for (const item of value) {
      if (typeof item === 'number') continue;
      if (item && typeof item === 'object') {
        for (const [key, inner] of Object.entries(item)) {
          if (typeof inner !== 'number') continue;
          const list = perKey.get(key) ?? [];
          list.push(inner);
          perKey.set(key, list);
        }
      }
    }
    // One field across many rows: totals only — sum, mean, max, min. Deliberately NOT pairwise differences: 200
    // rows would generate ~20,000 values and almost any invented amount would find a match.
    for (const list of perKey.values()) addFieldStats(set, list);
    for (const item of value) if (item && typeof item === 'object' && !Array.isArray(item)) collectResult(item, set);
    return;
  }
  if (value && typeof value === 'object') {
    const scalars = [];
    for (const inner of Object.values(value)) {
      if (typeof inner === 'number') scalars.push(inner);
      collectResult(inner, set);
    }
    // Two fields of the same object: a share of a budget, a change against last week. A share or a difference is
    // meaningful here; a mean across unrelated fields is not, so no totals for this group.
    if (scalars.length > 1) addPairs(set, scalars);
  }
}

/** The sum of every numeric field of a result, keyed by field name — 「本周合计 vs 上周合计」 lives on this level. */
function fieldTotals(value, totals = new Map()) {
  if (typeof value === 'number') return totals;
  if (Array.isArray(value)) { for (const item of value) fieldTotals(item, totals); return totals; }
  if (!value || typeof value !== 'object') return totals;
  for (const [key, inner] of Object.entries(value)) {
    if (typeof inner === 'number') totals.set(key, (totals.get(key) ?? 0) + inner);
    else fieldTotals(inner, totals);
  }
  return totals;
}

/**
 * The numbers a reply may use. Tool results count only from the current question (`index > turnStart`); the
 * person's own words count from the whole conversation. A result that failed, or that carries no numbers at all,
 * licenses nothing.
 */
export function buildSources(messages, turnStart = -1) {
  const values = new Set();
  const stated = [];
  for (const [index, message] of messages.entries()) {
    if (message.role === 'user') { stated.push(...numbersIn(message.content)); continue; }
    if (message.role !== 'tool' || index <= turnStart) continue;
    let parsed;
    try { parsed = JSON.parse(message.content); } catch { continue; }
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && parsed.error) continue;
    collectResult(parsed, values);
    // Differences and ratios between the totals of different fields of the same result (this week vs last week).
    const totals = [...fieldTotals(parsed).values()];
    if (totals.length > 1) addPairs(values, totals);
  }
  for (const value of stated) addTo(values, value);
  // The person's own numbers may be combined with each other: sums, differences, products, quotients.
  for (let i = 0; i < stated.length; i++) {
    for (let j = i; j < stated.length; j++) {
      addTo(values, stated[i] + stated[j]);
      addTo(values, stated[i] * stated[j]);
      addTo(values, Math.abs(stated[i] - stated[j]));
      if (stated[j] !== 0) addTo(values, stated[i] / stated[j]);
      if (stated[i] !== 0) addTo(values, stated[j] / stated[i]);
    }
  }
  return values;
}

/** The numbers in `text` that no source explains. Empty means the reply is fully grounded. */
export function unsourcedNumbers(text, sources) {
  return numbersIn(text).filter(value => !sources.has(round2(value)) && !sources.has(round1(value)));
}
