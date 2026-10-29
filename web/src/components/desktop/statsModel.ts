import { DAY_MS, type Category, type Txn, startOfDay } from "@/domain/types";
import { addDays, weekStartOf } from "@/logic/time";
import { getLocale } from "@/lib/strings";
import type { Point } from "./charts";

/**
 * Period maths for the stats page (docs/WEB-SPEC.md §A.3): week / month / year / custom windows,
 * day or month buckets, per-category totals and the "compare with the previous period" series.
 */

export type Period = "week" | "month" | "year" | "custom";

export interface Window {
  /** inclusive */
  start: number;
  /** exclusive */
  end: number;
}

export interface CustomRange {
  from: number;
  to: number;
}

const WEEKDAY_SHORT_ZH = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
const WEEKDAY_SHORT_EN = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const MONTH_SHORT_EN = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function monthStartOf(ts: number): number {
  const d = new Date(ts);
  return new Date(d.getFullYear(), d.getMonth(), 1).getTime();
}

function nextMonthStart(ts: number): number {
  const d = new Date(monthStartOf(ts));
  return new Date(d.getFullYear(), d.getMonth() + 1, 1).getTime();
}

function yearStartOf(ts: number): number {
  return new Date(new Date(ts).getFullYear(), 0, 1).getTime();
}

export function windowOf(period: Period, anchor: number, custom?: CustomRange): Window {
  if (period === "week") {
    const start = weekStartOf(anchor);
    return { start, end: addDays(start, 7) };
  }
  if (period === "month") {
    return { start: monthStartOf(anchor), end: nextMonthStart(anchor) };
  }
  if (period === "year") {
    const start = yearStartOf(anchor);
    return { start, end: new Date(new Date(start).getFullYear() + 1, 0, 1).getTime() };
  }
  const a = startOfDay(custom?.from ?? anchor);
  const b = startOfDay(custom?.to ?? anchor);
  return { start: Math.min(a, b), end: addDays(Math.max(a, b), 1) };
}

/** Move the period cursor one step back (-1) or forward (+1); custom ranges slide by their own length. */
export function shiftAnchor(period: Period, anchor: number, dir: number, custom?: CustomRange): number {
  if (period === "week") return addDays(weekStartOf(anchor), 7 * dir);
  if (period === "month") {
    const d = new Date(monthStartOf(anchor));
    return new Date(d.getFullYear(), d.getMonth() + dir, 1).getTime();
  }
  if (period === "year") {
    return new Date(new Date(yearStartOf(anchor)).getFullYear() + dir, 0, 1).getTime();
  }
  const span = windowOf("custom", anchor, custom);
  const shift = span.end - span.start;
  return (custom?.from ?? anchor) + shift * dir;
}

export function previousWindow(period: Period, anchor: number, custom?: CustomRange): Window {
  if (period === "custom") {
    const win = windowOf("custom", anchor, custom);
    const span = win.end - win.start;
    return { start: win.start - span, end: win.start };
  }
  return windowOf(period, shiftAnchor(period, anchor, -1));
}

export interface Bucket {
  label: string;
  start: number;
  end: number;
}

/** Day buckets for a week or a short custom range; month buckets for a year or a long range. */
export function bucketsOf(period: Period, win: Window): Bucket[] {
  const spanDays = Math.round((win.end - win.start) / DAY_MS);
  if (period === "year" || (period === "custom" && spanDays > 92)) {
    const out: Bucket[] = [];
    const first = new Date(win.start);
    let cursor = new Date(first.getFullYear(), first.getMonth(), 1);
    while (cursor.getTime() < win.end) {
      const next = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1);
      const label = getLocale() === "en" ? MONTH_SHORT_EN[cursor.getMonth()] : `${cursor.getMonth() + 1}月`;
      out.push({ label, start: Math.max(cursor.getTime(), win.start), end: Math.min(next.getTime(), win.end) });
      cursor = next;
    }
    return out;
  }
  const out: Bucket[] = [];
  for (let at = win.start; at < win.end; at = addDays(at, 1)) {
    const d = new Date(at);
    const weekday = getLocale() === "en" ? WEEKDAY_SHORT_EN[(d.getDay() + 6) % 7] : WEEKDAY_SHORT_ZH[(d.getDay() + 6) % 7];
    out.push({ label: period === "week" ? weekday : `${d.getMonth() + 1}/${d.getDate()}`, start: at, end: addDays(at, 1) });
  }
  return out;
}

export function sumOut(txns: readonly Txn[], start: number, end: number): number {
  return txns.filter((t) => t.direction === "OUT" && t.occurredAt >= start && t.occurredAt < end).reduce((sum, t) => sum + t.amountCents, 0);
}

/** One point per bucket, plus the aligned previous-period value when asked to compare. */
export function seriesOf(period: Period, win: Window, txns: readonly Txn[], compareWith: Window | null): Point[] {
  const buckets = bucketsOf(period, win);
  const prev = compareWith ? bucketsOf(period, compareWith) : null;
  return buckets.map((b, i) => {
    const point: Point = { label: b.label, value: sumOut(txns, b.start, b.end) };
    const p = prev?.[i];
    if (p) point.compare = sumOut(txns, p.start, p.end);
    return point;
  });
}

export interface Summary {
  totalCents: number;
  count: number;
  /** days with at least one entry, for the per-day average */
  activeDays: number;
  avgPerDayCents: number;
  peak: Txn | null;
  byCategory: { category: Category; cents: number }[];
}

export function summarize(txns: readonly Txn[], win: Window): Summary {
  const inWindow = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= win.start && t.occurredAt < win.end);
  const totalCents = inWindow.reduce((sum, t) => sum + t.amountCents, 0);
  const days = new Set(inWindow.map((t) => startOfDay(t.occurredAt)));
  const byCat = new Map<Category, number>();
  let peak: Txn | null = null;
  for (const t of inWindow) {
    byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
    if (!peak || t.amountCents > peak.amountCents) peak = t;
  }
  return {
    totalCents,
    count: inWindow.length,
    activeDays: days.size,
    avgPerDayCents: days.size > 0 ? Math.round(totalCents / days.size) : 0,
    peak,
    byCategory: [...byCat.entries()].map(([category, cents]) => ({ category, cents })).sort((a, b) => b.cents - a.cents),
  };
}

/** Stable, readable label for the period cursor (used in the stats toolbar). */
export function windowLabel(period: Period, anchor: number, custom?: CustomRange): string {
  const win = windowOf(period, anchor, custom);
  const d = new Date(win.start);
  const endDay = new Date(win.end - DAY_MS);
  if (getLocale() === "en") {
    if (period === "week") return `${MONTH_SHORT_EN[d.getMonth()]} ${d.getDate()} – ${MONTH_SHORT_EN[endDay.getMonth()]} ${endDay.getDate()}`;
    if (period === "month") return `${MONTH_SHORT_EN[d.getMonth()]} ${d.getFullYear()}`;
    if (period === "year") return `${d.getFullYear()}`;
    return `${d.toISOString().slice(0, 10)} – ${endDay.toISOString().slice(0, 10)}`;
  }
  if (period === "week") {
    return getLocale() === "en"
      ? `${MONTH_SHORT_EN[d.getMonth()]} ${d.getDate()} – ${MONTH_SHORT_EN[endDay.getMonth()]} ${endDay.getDate()}`
      : `${d.getMonth() + 1}月${d.getDate()}日 – ${endDay.getMonth() + 1}月${endDay.getDate()}日`;
  }
  if (period === "month") return getLocale() === "en" ? `${MONTH_SHORT_EN[d.getMonth()]} ${d.getFullYear()}` : `${d.getFullYear()} 年 ${d.getMonth() + 1} 月`;
  if (period === "year") return getLocale() === "en" ? `${d.getFullYear()}` : `${d.getFullYear()} 年`;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} – ${endDay.getFullYear()}-${String(endDay.getMonth() + 1).padStart(2, "0")}-${String(endDay.getDate()).padStart(2, "0")}`;
}

/** Peak day helper for the overview summary (a day, not a txn). */
export function peakDayOf(txns: readonly Txn[], win: Window): { dayStart: number; cents: number } | null {
  const byDay = new Map<number, number>();
  for (const t of txns) {
    if (t.direction !== "OUT" || t.occurredAt < win.start || t.occurredAt >= win.end) continue;
    const day = startOfDay(t.occurredAt);
    byDay.set(day, (byDay.get(day) ?? 0) + t.amountCents);
  }
  let best: { dayStart: number; cents: number } | null = null;
  for (const [dayStart, cents] of byDay) if (!best || cents > best.cents) best = { dayStart, cents };
  return best;
}
