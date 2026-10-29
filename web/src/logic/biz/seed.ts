import { seeded } from "../time";
import { weekdayIndex, addDays } from "./format";
import type { RevenueEntry } from "./book";

/**
 * SYNTHETIC revenue book used until the owner records their own weeks. Every row keeps
 * `source: "SEED"` so it is recognisable in code and a real row for the same day simply replaces it.
 * No clock inside: the caller passes today's day-start.
 */
const SEED = 20261028;
const DAYS = 42;

/** Revenue of a plain weekday, cents. */
const BASE_CENTS = 265_000;

export function seededBook(todayStart: number, days = DAYS): RevenueEntry[] {
  const rnd = seeded(SEED);
  // Two closed days make the gaps visible in the chart and the totals.
  const closed = new Set([days - 9, days - 23]);
  const out: RevenueEntry[] = [];
  for (let offset = 0; offset < days; offset++) {
    if (closed.has(offset)) continue;
    const day = addDays(todayStart, offset - (days - 1));
    const weekday = weekdayIndex(day);
    const factor = [0.92, 0.84, 0.86, 0.97, 1.12, 1.38, 1.24][weekday] ?? 1;
    const noise = 0.9 + rnd() * 0.2;
    const revenueCents = Math.round((BASE_CENTS * factor * noise) / 100) * 100;
    const ticketCents = 2_800 + Math.floor(rnd() * 900);
    out.push({
      day,
      revenueCents,
      orders: Math.max(1, Math.round(revenueCents / ticketCents)),
      note: "",
      source: "SEED",
    });
  }
  return out;
}
