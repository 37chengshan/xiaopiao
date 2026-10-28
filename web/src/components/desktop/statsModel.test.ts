import { describe, expect, it } from "vitest";
import type { Txn } from "@/domain/types";
import { bucketsOf, previousWindow, seriesOf, shiftAnchor, summarize, sumOut, windowLabel, windowOf } from "./statsModel";

const D = (y: number, m: number, d: number, h = 12) => new Date(y, m - 1, d, h).getTime();
const txn = (over: Partial<Txn> & { id: string }): Txn => ({
  amountCents: 1000,
  direction: "OUT",
  merchant: "便利店",
  category: "GROCERY",
  occurredAt: D(2026, 10, 20),
  source: "MANUAL",
  ...over,
});

describe("windowOf", () => {
  it("spans Monday to Sunday for a week", () => {
    const win = windowOf("week", D(2026, 10, 28));
    expect(win.start).toBe(D(2026, 10, 26, 0));
    expect(win.end).toBe(D(2026, 11, 2, 0));
  });

  it("spans the calendar month, including the December rollover", () => {
    expect(windowOf("month", D(2026, 10, 28))).toEqual({ start: D(2026, 10, 1, 0), end: D(2026, 11, 1, 0) });
    expect(windowOf("month", D(2026, 12, 5))).toEqual({ start: D(2026, 12, 1, 0), end: D(2027, 1, 1, 0) });
  });

  it("spans the calendar year", () => {
    expect(windowOf("year", D(2026, 7, 4))).toEqual({ start: D(2026, 1, 1, 0), end: D(2027, 1, 1, 0) });
  });

  it("normalises custom ranges to whole days and swaps an inverted range", () => {
    expect(windowOf("custom", D(2026, 10, 20), { from: D(2026, 10, 22, 18), to: D(2026, 10, 20, 8) })).toEqual({
      start: D(2026, 10, 20, 0),
      end: D(2026, 10, 23, 0),
    });
  });
});

describe("shiftAnchor", () => {
  it("moves a week, a month and a year by one step", () => {
    expect(shiftAnchor("week", D(2026, 10, 28), -1)).toBe(D(2026, 10, 19, 0));
    expect(shiftAnchor("month", D(2026, 10, 28), 1)).toBe(D(2026, 11, 1, 0));
    expect(shiftAnchor("year", D(2026, 10, 28), -1)).toBe(D(2025, 1, 1, 0));
  });

  it("slides a custom range by its own length", () => {
    const custom = { from: D(2026, 10, 10), to: D(2026, 10, 14) };
    expect(shiftAnchor("custom", custom.from, -1, custom)).toBe(D(2026, 10, 5));
  });
});

describe("previousWindow", () => {
  it("uses the real previous month", () => {
    expect(previousWindow("month", D(2026, 10, 28))).toEqual({ start: D(2026, 9, 1, 0), end: D(2026, 10, 1, 0) });
  });

  it("steps back by one span for a custom range", () => {
    const custom = { from: D(2026, 10, 10), to: D(2026, 10, 12) };
    expect(previousWindow("custom", custom.from, custom)).toEqual({ start: D(2026, 10, 7, 0), end: D(2026, 10, 10, 0) });
  });
});

describe("bucketsOf", () => {
  it("labels seven weekdays for a week", () => {
    const labels = bucketsOf("week", windowOf("week", D(2026, 10, 28))).map((b) => b.label);
    expect(labels).toEqual(["周一", "周二", "周三", "周四", "周五", "周六", "周日"]);
  });

  it("makes one bucket per day for a month and per month for a year", () => {
    expect(bucketsOf("month", windowOf("month", D(2026, 10, 28)))).toHaveLength(31);
    const year = bucketsOf("year", windowOf("year", D(2026, 10, 28)));
    expect(year).toHaveLength(12);
    expect(year[0].label).toBe("1月");
    expect(year[11].label).toBe("12月");
  });

  it("switches a long custom range to month buckets", () => {
    const win = windowOf("custom", D(2026, 1, 1), { from: D(2026, 1, 1), to: D(2026, 5, 31) });
    expect(bucketsOf("custom", win)).toHaveLength(5);
  });
});

describe("seriesOf", () => {
  it("totals each bucket and aligns the compare period by index", () => {
    const win = windowOf("month", D(2026, 10, 28));
    const prev = previousWindow("month", D(2026, 10, 28));
    const rows = [
      txn({ id: "a", amountCents: 1000, occurredAt: D(2026, 10, 20) }),
      txn({ id: "b", amountCents: 2500, occurredAt: D(2026, 10, 20, 18) }),
      txn({ id: "c", amountCents: 900, occurredAt: D(2026, 9, 20) }),
      txn({ id: "d", amountCents: 700, direction: "IN", occurredAt: D(2026, 10, 20) }),
    ];
    const points = seriesOf("month", win, rows, prev);
    expect(points).toHaveLength(31);
    expect(points[19]).toEqual({ label: "10/20", value: 3500, compare: 900 });
    // September has 30 days, so the 31st October bucket has nothing to align with.
    expect(points.slice(0, 30).every((p) => typeof p.compare === "number")).toBe(true);
    expect(points[30].compare).toBeUndefined();
    expect(points.reduce((s, p) => s + p.value, 0)).toBe(3500);
  });

  it("omits the compare series when no previous window is given", () => {
    const points = seriesOf("week", windowOf("week", D(2026, 10, 28)), [], null);
    expect(points.every((p) => p.compare === undefined)).toBe(true);
  });
});

describe("summarize", () => {
  const win = windowOf("month", D(2026, 10, 28));
  const rows = [
    txn({ id: "a", amountCents: 1000, occurredAt: D(2026, 10, 20), category: "MEAL" }),
    txn({ id: "b", amountCents: 2000, occurredAt: D(2026, 10, 20), category: "MEAL" }),
    txn({ id: "c", amountCents: 500, occurredAt: D(2026, 10, 22), category: "DRINK" }),
    txn({ id: "d", amountCents: 9999, direction: "IN", occurredAt: D(2026, 10, 22) }),
    txn({ id: "e", amountCents: 4000, occurredAt: D(2026, 9, 30) }),
  ];

  it("counts only outflow inside the window", () => {
    const s = summarize(rows, win);
    expect(s.totalCents).toBe(3500);
    expect(s.count).toBe(3);
    expect(s.activeDays).toBe(2);
    expect(s.avgPerDayCents).toBe(1750);
  });

  it("finds the largest single entry and ranks categories", () => {
    const s = summarize(rows, win);
    expect(s.peak?.id).toBe("b");
    expect(s.byCategory).toEqual([
      { category: "MEAL", cents: 3000 },
      { category: "DRINK", cents: 500 },
    ]);
  });

  it("reports an empty window without dividing by zero", () => {
    const s = summarize([], win);
    expect(s).toMatchObject({ totalCents: 0, count: 0, activeDays: 0, avgPerDayCents: 0, peak: null });
    expect(s.byCategory).toEqual([]);
  });
});

describe("sumOut and windowLabel", () => {
  it("sums outflow in a half-open range", () => {
    const rows = [
      txn({ id: "a", amountCents: 100, occurredAt: D(2026, 10, 10, 0) }),
      txn({ id: "b", amountCents: 200, occurredAt: D(2026, 10, 10, 23) }),
      txn({ id: "c", amountCents: 400, occurredAt: D(2026, 10, 11, 0) }),
    ];
    expect(sumOut(rows, D(2026, 10, 10, 0), D(2026, 10, 11, 0))).toBe(300);
  });

  it("labels the period in the active language", () => {
    expect(windowLabel("month", D(2026, 10, 28))).toBe("2026 年 10 月");
    expect(windowLabel("year", D(2026, 10, 28))).toBe("2026 年");
    expect(windowLabel("week", D(2026, 10, 28))).toBe("10月26日 – 11月1日");
  });
});
