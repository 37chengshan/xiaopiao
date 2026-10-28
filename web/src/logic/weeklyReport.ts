import { type Badge, type Category, type Cents, type Txn, DAY_MS } from "../domain/types";
import { computeBadges } from "./badges";
import { weeklyComment } from "./comments";
import { addDays, dateKey, weekStartOf } from "./time";

export interface WeeklyReport {
  weekStart: number;
  weekEnd: number;
  totalCents: Cents;
  prevTotalCents: Cents;
  /** null when there is no previous week to compare against */
  deltaPct: number | null;
  /** Mon..Sun spend, cents */
  byDay: Cents[];
  topCategories: Array<{ category: Category; cents: Cents }>;
  badges: Badge[];
  comment: string;
  txnCount: number;
}

function sumOut(txns: readonly Txn[], start: number, end: number): Cents {
  return txns.filter((t) => t.direction === "OUT" && t.occurredAt >= start && t.occurredAt < end).reduce((s, t) => s + t.amountCents, 0);
}

export function buildWeeklyReport(all: readonly Txn[], weekStart: number, now: number = Date.now()): WeeklyReport {
  const start = weekStartOf(weekStart);
  const end = addDays(start, 7);
  const prevStart = addDays(start, -7);
  const week = all.filter((t) => t.direction === "OUT" && t.occurredAt >= start && t.occurredAt < end);
  const total = sumOut(all, start, end);
  const prev = sumOut(all, prevStart, start);
  const byDay: Cents[] = Array.from({ length: 7 }, () => 0);
  const byCat = new Map<Category, Cents>();
  for (const t of week) {
    const idx = Math.min(6, Math.floor((t.occurredAt - start) / DAY_MS));
    byDay[idx] += t.amountCents;
    byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
  }
  return {
    weekStart: start,
    weekEnd: end,
    totalCents: total,
    prevTotalCents: prev,
    deltaPct: prev > 0 ? Math.round(((total - prev) / prev) * 100) : null,
    byDay,
    topCategories: [...byCat.entries()].map(([category, cents]) => ({ category, cents })).sort((a, b) => b.cents - a.cents).slice(0, 3),
    badges: computeBadges({ all, start, end, now, scope: "week" }),
    comment: weeklyComment(total, prev, dateKey(start)),
    txnCount: week.length,
  };
}
