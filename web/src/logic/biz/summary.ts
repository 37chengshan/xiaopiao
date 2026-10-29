import {
  averageTicketChange,
  bestDay,
  change,
  declineRange,
  declineStreak,
  entryFor,
  sameWeekdayChange,
  type Change,
  type RevenueEntry,
  type RevenuePoint,
  type WeekdayAverage,
  weekdayAverages,
  entriesBetween,
  worstDay,
} from "./book";
import { addDays, weekStart } from "./format";

/**
 * Facts for the "本周小结" cards. Every fact carries numbers only — the sentences are written in
 * `features/biz/summaryText.ts` so the same math can read in either language, and a model can never
 * move a figure: it only ever rephrases what these rows already say.
 */
export type OverviewFact =
  | { kind: "decline"; streak: number; fromCents: number; toCents: number }
  | { kind: "week"; days: number; change: Change }
  | { kind: "weekday"; best: WeekdayAverage; worst: WeekdayAverage; ratio: number | null }
  | { kind: "ticket"; change: Change }
  | { kind: "bestDay"; best: RevenuePoint; worst: RevenuePoint | null };

export interface ReportFact {
  hasEntry: boolean;
  revenueCents: number;
  orders: number;
  /** this day versus the same weekday last week */
  change: Change;
}

const WINDOW_DAYS = 14;

export function overviewFacts(book: readonly RevenueEntry[], today: number): OverviewFact[] {
  if (book.length === 0) return [];
  const out: OverviewFact[] = [];

  const streak = declineStreak(book, today);
  const range = declineRange(book, today, streak);
  if (streak >= 2 && range) out.push({ kind: "decline", streak, fromCents: range[0], toCents: range[1] });

  const week = entriesBetween(book, weekStart(today), today);
  const thisWeek = weekStart(today);
  const beforeWeek = entriesBetween(book, addDays(thisWeek, -7), addDays(today, -7));
  if (week.length > 0 || beforeWeek.length > 0) {
    out.push({
      kind: "week",
      days: week.length,
      change: change(
        week.reduce((n, e) => n + e.revenueCents, 0),
        beforeWeek.reduce((n, e) => n + e.revenueCents, 0),
      ),
    });
  }

  const weekdays = weekdayAverages(book, today).filter((w) => w.days > 0);
  if (weekdays.length >= 2) {
    const best = weekdays.reduce((a, b) => (b.averageCents > a.averageCents ? b : a));
    const worst = weekdays.reduce((a, b) => (b.averageCents < a.averageCents ? b : a));
    out.push({
      kind: "weekday",
      best,
      worst,
      ratio: worst.averageCents > 0 ? best.averageCents / worst.averageCents : null,
    });
  }

  const ticket = averageTicketChange(book, today);
  if (ticket.nowCents > 0) out.push({ kind: "ticket", change: ticket });

  const best = bestDay(book, today, WINDOW_DAYS);
  if (best && best.revenueCents > 0) {
    const worst = worstDay(book, today, WINDOW_DAYS);
    out.push({
      kind: "bestDay",
      best,
      worst: worst && worst.day !== best.day && worst.revenueCents < best.revenueCents ? worst : null,
    });
  }

  return out.slice(0, 5);
}

/** The one line printed at the bottom of the daily receipt. */
export function reportFact(book: readonly RevenueEntry[], day: number): ReportFact {
  const entry = entryFor(book, day);
  return {
    hasEntry: !!entry,
    revenueCents: entry?.revenueCents ?? 0,
    orders: entry?.orders ?? 0,
    change: sameWeekdayChange(book, day),
  };
}
