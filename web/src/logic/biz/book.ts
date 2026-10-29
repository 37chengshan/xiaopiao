import { addDays, monthKey, shortDate, weekdayIndex, weekStart } from "./format";

/**
 * The shop's revenue book: one row per day, integer cents, no clock inside.
 * Every function takes the book plus the day it should treat as "today", so the same numbers
 * can be shown, exported and tested. Ported from the Android `merchant/Revenue.kt`.
 */

/** Where a day's number came from. `SEED` rows are mock data: recognisable here in code, not labelled in the UI. */
export type EntrySource = "SEED" | "MANUAL" | "CSV";

export interface RevenueEntry {
  /** local midnight, epoch millis */
  day: number;
  revenueCents: number;
  /** 0 when the owner only knows the total */
  orders: number;
  note: string;
  source: EntrySource;
}

export interface RevenuePoint {
  day: number;
  revenueCents: number;
  orders: number;
  hasEntry: boolean;
  source: EntrySource | null;
}

/** A number now versus a baseline. `pct` is null when the baseline is zero (nothing meaningful to divide by). */
export interface Change {
  nowCents: number;
  beforeCents: number;
  diffCents: number;
  pct: number | null;
  hasBase: boolean;
}

export interface WeekdayAverage {
  /** 0 = Monday … 6 = Sunday */
  index: number;
  averageCents: number;
  /** days with a recorded entry */
  days: number;
}

export interface TrendPoint {
  key: string;
  label: string;
  revenueCents: number;
  orders: number;
  /** days with an entry inside the bucket */
  days: number;
  /** same bucket one period earlier, null when the compare period is off */
  compareCents: number | null;
}

export interface TicketBand {
  id: string;
  /** band lower bound, cents */
  fromCents: number;
  /** band upper bound, cents (exclusive); Infinity for the last band */
  toCents: number;
  days: number;
}

export function change(nowCents: number, beforeCents: number): Change {
  const diffCents = nowCents - beforeCents;
  return {
    nowCents,
    beforeCents,
    diffCents,
    pct: beforeCents > 0 ? diffCents / beforeCents : null,
    hasBase: beforeCents > 0,
  };
}

/** Drops negative rows, keeps one row per day (last wins) and sorts oldest → newest. */
export function normalize(entries: readonly RevenueEntry[]): RevenueEntry[] {
  const byDay = new Map<number, RevenueEntry>();
  for (const e of entries) {
    if (e.revenueCents < 0 || e.orders < 0) continue;
    byDay.set(e.day, { ...e, orders: Math.round(e.orders) });
  }
  return [...byDay.values()].sort((a, b) => a.day - b.day);
}

export function entryFor(entries: readonly RevenueEntry[], day: number): RevenueEntry | null {
  return entries.find((e) => e.day === day) ?? null;
}

export function totalCents(entries: readonly RevenueEntry[]): number {
  return entries.reduce((n, e) => n + e.revenueCents, 0);
}

export function totalOrders(entries: readonly RevenueEntry[]): number {
  return entries.reduce((n, e) => n + e.orders, 0);
}

/** Rounded yuan-per-order; 0 when the order count is unknown. */
export function averageTicket(revenueCents: number, orders: number): number {
  return orders <= 0 ? 0 : Math.round(revenueCents / orders);
}

/** Weighted by orders, so a busy day counts more than a slow one; days without an order count are left out. */
export function averageTicketOf(entries: readonly RevenueEntry[]): number {
  const known = entries.filter((e) => e.orders > 0);
  return averageTicket(totalCents(known), totalOrders(known));
}

/** Existing rows between `from` and `to`, inclusive. */
export function entriesBetween(entries: readonly RevenueEntry[], from: number, to: number): RevenueEntry[] {
  return entries.filter((e) => e.day >= from && e.day <= to).sort((a, b) => a.day - b.day);
}

/** `days` points ending at `endDay`, oldest first, zero-filled on days without an entry. */
export function series(entries: readonly RevenueEntry[], endDay: number, days: number): RevenuePoint[] {
  const byDay = new Map(entries.map((e) => [e.day, e]));
  return Array.from({ length: days }, (_, i) => {
    const day = addDays(endDay, i - (days - 1));
    const e = byDay.get(day);
    return {
      day,
      revenueCents: e?.revenueCents ?? 0,
      orders: e?.orders ?? 0,
      hasEntry: !!e,
      source: e?.source ?? null,
    };
  });
}

export function upsert(entries: readonly RevenueEntry[], entry: RevenueEntry): RevenueEntry[] {
  return normalize([...entries.filter((e) => e.day !== entry.day), entry]);
}

export function remove(entries: readonly RevenueEntry[], day: number): RevenueEntry[] {
  return normalize(entries.filter((e) => e.day !== day));
}

/** Import merge: incoming rows win per day, everything else stays. */
export function merge(entries: readonly RevenueEntry[], incoming: readonly RevenueEntry[]): RevenueEntry[] {
  const days = new Set(incoming.map((e) => e.day));
  return normalize([...entries.filter((e) => !days.has(e.day)), ...incoming]);
}

// ---- comparisons ------------------------------------------------------------------------------

/** Today versus yesterday; both read 0 on a day without an entry. */
export function todayVsYesterday(entries: readonly RevenueEntry[], today: number): Change {
  return change(entryFor(entries, today)?.revenueCents ?? 0, entryFor(entries, addDays(today, -1))?.revenueCents ?? 0);
}

/** Today versus the same weekday last week — the fair comparison for a shop. */
export function sameWeekdayChange(entries: readonly RevenueEntry[], today: number): Change {
  return change(entryFor(entries, today)?.revenueCents ?? 0, entryFor(entries, addDays(today, -7))?.revenueCents ?? 0);
}

/** Monday → today versus the same weekday span last week. */
export function weekToDateChange(entries: readonly RevenueEntry[], today: number): Change {
  const start = weekStart(today);
  return change(
    totalCents(entriesBetween(entries, start, today)),
    totalCents(entriesBetween(entries, addDays(start, -7), addDays(today, -7))),
  );
}

/** Average ticket of the last 7 days versus the 7 days before them. */
export function averageTicketChange(entries: readonly RevenueEntry[], today: number): Change {
  return change(
    averageTicketOf(entriesBetween(entries, addDays(today, -6), today)),
    averageTicketOf(entriesBetween(entries, addDays(today, -13), addDays(today, -7))),
  );
}

/** Best recorded day in the window; ties go to the later day. */
export function bestDay(entries: readonly RevenueEntry[], today: number, days = 14): RevenuePoint | null {
  const rows = entriesBetween(entries, addDays(today, -(days - 1)), today);
  if (rows.length === 0) return null;
  const best = rows.reduce((a, b) => (b.revenueCents >= a.revenueCents ? b : a));
  return { day: best.day, revenueCents: best.revenueCents, orders: best.orders, hasEntry: true, source: best.source };
}

/** Quietest recorded day in the window. Days without an entry are unknown, not quiet. */
export function worstDay(entries: readonly RevenueEntry[], today: number, days = 14): RevenuePoint | null {
  const rows = entriesBetween(entries, addDays(today, -(days - 1)), today);
  if (rows.length === 0) return null;
  const worst = rows.reduce((a, b) => (b.revenueCents < a.revenueCents ? b : a));
  return { day: worst.day, revenueCents: worst.revenueCents, orders: worst.orders, hasEntry: true, source: worst.source };
}

/** Average revenue per weekday over the last `weeks` weeks, ignoring days without an entry. */
export function weekdayAverages(entries: readonly RevenueEntry[], today: number, weeks = 4): WeekdayAverage[] {
  const rows = entriesBetween(entries, addDays(today, -(weeks * 7 - 1)), today);
  return Array.from({ length: 7 }, (_, index) => {
    const ofDay = rows.filter((e) => weekdayIndex(e.day) === index);
    return {
      index,
      averageCents: ofDay.length === 0 ? 0 : Math.round(totalCents(ofDay) / ofDay.length),
      days: ofDay.length,
    };
  });
}

/** Consecutive days, ending at the latest recorded day, that each earned less than the day before. */
export function declineStreak(entries: readonly RevenueEntry[], today: number): number {
  const byDay = new Map(normalize(entries).map((e) => [e.day, e]));
  let day = [...byDay.keys()].filter((d) => d <= today).pop();
  let streak = 0;
  while (day !== undefined) {
    const current = byDay.get(day);
    const previous = byDay.get(addDays(day, -1));
    if (!current || !previous || current.revenueCents >= previous.revenueCents) break;
    streak++;
    day = addDays(day, -1);
  }
  return streak;
}

/** First and last number of the decline streak, for the warning sentence ("从 ¥x 到 ¥y"). */
export function declineRange(entries: readonly RevenueEntry[], today: number, streak: number): [number, number] | null {
  if (streak <= 0) return null;
  const byDay = new Map(normalize(entries).map((e) => [e.day, e]));
  const last = [...byDay.keys()].filter((d) => d <= today).pop();
  if (last === undefined) return null;
  const high = byDay.get(addDays(last, -streak))?.revenueCents;
  const low = byDay.get(last)?.revenueCents;
  return high === undefined || low === undefined ? null : [high, low];
}

// ---- buckets ----------------------------------------------------------------------------------

/** Last `weeks` weeks (Monday-start buckets), oldest first, with the optional compare week before each. */
export function weeklyTrend(entries: readonly RevenueEntry[], today: number, weeks = 6, compare = false): TrendPoint[] {
  const thisWeek = weekStart(today);
  return Array.from({ length: weeks }, (_, i) => {
    const start = addDays(thisWeek, -(weeks - 1 - i) * 7);
    const end = i === weeks - 1 ? today : addDays(start, 6);
    const rows = entriesBetween(entries, start, end);
    const before = compare ? entriesBetween(entries, addDays(start, -7), addDays(end, -7)) : [];
    return {
      key: `${start}`,
      label: shortDate(start),
      revenueCents: totalCents(rows),
      orders: totalOrders(rows),
      days: rows.length,
      compareCents: compare ? totalCents(before) : null,
    };
  });
}

/** Last `months` calendar months, oldest first, ending with the month of `today`. */
export function monthlyTrend(entries: readonly RevenueEntry[], today: number, months = 6, compare = false): TrendPoint[] {
  const d = new Date(today);
  return Array.from({ length: months }, (_, i) => {
    const cursor = new Date(d.getFullYear(), d.getMonth() - (months - 1 - i), 1);
    const start = cursor.getTime();
    const next = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1).getTime();
    const end = Math.min(addDays(next, -1), today);
    const rows = entriesBetween(entries, start, end);
    const beforeStart = new Date(cursor.getFullYear(), cursor.getMonth() - 1, 1).getTime();
    const beforeEnd = addDays(start, -1);
    const before = compare ? entriesBetween(entries, beforeStart, beforeEnd) : [];
    return {
      key: monthKey(start),
      label: `${cursor.getMonth() + 1}月`,
      revenueCents: totalCents(rows),
      orders: totalOrders(rows),
      days: rows.length,
      compareCents: compare ? totalCents(before) : null,
    };
  });
}

/** Fixed ¥ bands, so days stay comparable across periods. */
const TICKET_BANDS: ReadonlyArray<Omit<TicketBand, "days">> = [
  { id: "lt20", fromCents: 0, toCents: 2000 },
  { id: "20to30", fromCents: 2000, toCents: 3000 },
  { id: "30to40", fromCents: 3000, toCents: 4000 },
  { id: "40to60", fromCents: 4000, toCents: 6000 },
  { id: "gte60", fromCents: 6000, toCents: Infinity },
];

export function ticketBandOf(entry: RevenueEntry): string | null {
  const ticket = averageTicket(entry.revenueCents, entry.orders);
  if (ticket <= 0) return null;
  return TICKET_BANDS.find((b) => ticket >= b.fromCents && ticket < b.toCents)?.id ?? "gte60";
}

/** How many days of the period landed in each ¥ average-ticket band. */
export function ticketHistogram(entries: readonly RevenueEntry[]): TicketBand[] {
  const counts = new Map<string, number>();
  for (const e of entries) {
    const id = ticketBandOf(e);
    if (id) counts.set(id, (counts.get(id) ?? 0) + 1);
  }
  return TICKET_BANDS.map((b) => ({ ...b, days: counts.get(b.id) ?? 0 }));
}

export function ticketBandLabel(band: TicketBand): string {
  const from = band.fromCents / 100;
  if (band.toCents === Infinity) return `¥${from}+`;
  return `¥${from}–${band.toCents / 100}`;
}
