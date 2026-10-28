import { describe, expect, it } from "vitest";
import {
  averageTicketChange,
  bestDay,
  declineStreak,
  entryFor,
  merge,
  monthlyTrend,
  normalize,
  remove,
  sameWeekdayChange,
  series,
  ticketBandOf,
  ticketHistogram,
  upsert,
  weekdayAverages,
  weeklyTrend,
  entriesBetween,
  worstDay,
  type RevenueEntry,
} from "./book";
import { addDays, weekStart } from "./format";
import { seededBook } from "./seed";
import { overviewFacts, reportFact } from "./summary";
import { gate } from "./sample";

const TODAY = new Date(2026, 9, 28).getTime(); // Wednesday

function day(offset: number): number {
  return addDays(TODAY, offset);
}

function entry(offset: number, revenueCents: number, orders = 0): RevenueEntry {
  return { day: day(offset), revenueCents, orders, note: "", source: "MANUAL" };
}

describe("the book", () => {
  it("keeps one row per day, newest value wins, oldest first", () => {
    const book = normalize([entry(-2, 100), entry(0, 300), entry(-2, 200)]);
    expect(book.map((e) => e.revenueCents)).toEqual([200, 300]);
  });

  it("drops rows that cannot be true", () => {
    const book = normalize([{ ...entry(0, -1) }, { ...entry(-1, 100), orders: -3 }, entry(-3, 500)]);
    expect(book).toHaveLength(1);
    expect(book[0].revenueCents).toBe(500);
  });

  it("zero-fills days without an entry so a gap is visible", () => {
    const points = series([entry(0, 10_000, 4), entry(-3, 20_000, 5)], TODAY, 4);
    expect(points.map((p) => p.revenueCents)).toEqual([20_000, 0, 0, 10_000]);
    expect(points.map((p) => p.hasEntry)).toEqual([true, false, false, true]);
  });

  it("upserts, removes and merges per day", () => {
    const book = normalize([entry(-2, 100), entry(-1, 200)]);
    expect(upsert(book, entry(-1, 250)).map((e) => e.revenueCents)).toEqual([100, 250]);
    expect(remove(book, day(-2)).map((e) => e.revenueCents)).toEqual([200]);
    expect(merge(book, [entry(-3, 10), entry(-1, 999)]).map((e) => e.revenueCents)).toEqual([10, 100, 999]);
  });

  it("compares today with yesterday and with the same weekday last week", () => {
    const book = normalize([entry(-1, 100_000, 10), entry(-7, 200_000, 20), entry(0, 150_000, 12)]);
    const yesterday = sameWeekdayChange(book, day(-1));
    expect(yesterday).toMatchObject({ nowCents: 100_000, beforeCents: 0, hasBase: false, pct: null });
    const today = sameWeekdayChange(book, TODAY);
    expect(today.nowCents).toBe(150_000);
    expect(today.beforeCents).toBe(200_000);
    expect(today.pct).toBeCloseTo(-0.25);
  });

  it("averages the weekdays it actually has", () => {
    const book = normalize([entry(-1, 100_000, 10), entry(-8, 200_000, 20)]);
    const averages = weekdayAverages(book, TODAY, 4);
    expect(averages).toHaveLength(7);
    expect(averages[1]).toMatchObject({ days: 2, averageCents: 150_000 });
    expect(averages[2]).toMatchObject({ days: 0, averageCents: 0 });
  });

  it("counts a decline streak and the range it fell over", () => {
    const book = normalize([entry(-3, 300), entry(-2, 250), entry(-1, 200), entry(0, 100)]);
    expect(declineStreak(book, TODAY)).toBe(3);
  });

  it("names the best and quietest recorded day, ignoring unknown days", () => {
    const book = normalize([entry(-5, 400), entry(-4, 100), entry(0, 250)]);
    expect(bestDay(book, TODAY)?.revenueCents).toBe(400);
    expect(worstDay(book, TODAY)?.revenueCents).toBe(100);
    expect(bestDay([], TODAY)).toBeNull();
  });

  it("compares the average ticket only when orders are known", () => {
    const book = normalize([entry(-1, 300_000, 0), entry(-2, 300_000, 10)]);
    const change = averageTicketChange(book, TODAY);
    expect(change.nowCents).toBe(30_000);
    expect(change.hasBase).toBe(false);
  });

  it("windows a week from Monday", () => {
    expect(shortDateOf(weekStart(TODAY))).toBe("10/26");
    expect(entriesBetween(normalize([entry(0, 1), entry(-4, 2)]), weekStart(TODAY), TODAY)).toHaveLength(1);
  });
});

function shortDateOf(ms: number): string {
  const d = new Date(ms);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

describe("trends", () => {
  const book = seededBook(TODAY);

  it("buckets weeks and months without inventing days", () => {
    const weeks = weeklyTrend(book, TODAY, 6);
    expect(weeks).toHaveLength(6);
    expect(weeks[5].days).toBeGreaterThan(0);
    expect(weeks[5].days).toBeLessThanOrEqual(3); // Monday → Wednesday
    const months = monthlyTrend(book, TODAY, 3);
    expect(months.at(-1)?.label).toBe("10月");
    expect(months.at(-1)?.days).toBe(26); // Oct 1 … Oct 28, minus the two days the shop was closed
  });

  it("bands days by average ticket", () => {
    expect(ticketBandOf({ ...entry(0, 300_000, 100) })).toBe("30to40");
    expect(ticketBandOf({ ...entry(0, 300_000, 0) })).toBeNull();
    const bands = ticketHistogram(book);
    expect(bands.reduce((n, b) => n + b.days, 0)).toBe(book.filter((e) => e.orders > 0).length);
  });
});

describe("facts", () => {
  it("says nothing about an empty book", () => {
    expect(overviewFacts([], TODAY)).toEqual([]);
  });

  it("reports a decline, a week total and the best day from real rows", () => {
    const book = normalize([entry(-3, 400_000, 100), entry(-2, 300_000, 90), entry(-1, 200_000, 80), entry(0, 100_000, 70)]);
    const kinds = overviewFacts(book, TODAY).map((f) => f.kind);
    expect(kinds).toContain("decline");
    expect(kinds).toContain("week");
    expect(kinds).toContain("ticket");
    const decline = overviewFacts(book, TODAY).find((f) => f.kind === "decline");
    expect(decline).toMatchObject({ streak: 3, fromCents: 400_000, toCents: 100_000 });
  });

  it("gives the receipt line its own day and the same weekday a week earlier", () => {
    const book = normalize([entry(-7, 200_000, 50), entry(0, 250_000, 60)]);
    const fact = reportFact(book, TODAY);
    expect(fact).toMatchObject({ hasEntry: true, revenueCents: 250_000, orders: 60 });
    expect(fact.change.pct).toBeCloseTo(0.25);
    expect(reportFact(book, day(-1))).toMatchObject({ hasEntry: false, revenueCents: 0 });
  });

  it("keeps the seeded book deterministic", () => {
    expect(seededBook(TODAY)).toEqual(seededBook(TODAY));
    expect(entryFor(seededBook(TODAY), TODAY)?.source).toBe("SEED");
  });
});

describe("small-sample gate", () => {
  it("hides a group under the threshold and hands back no value", () => {
    const hide = gate(19, 12_345);
    expect(hide.visible).toBe(false);
    expect(hide.value).toBeNull();
    expect(hide.sample).toBe(19);
    expect(hide.threshold).toBe(20);
  });

  it("shows a group at exactly the threshold", () => {
    expect(gate(20, "42%").value).toBe("42%");
  });
});
