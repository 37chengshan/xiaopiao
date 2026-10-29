import { describe, expect, it } from "vitest";
import type { Txn } from "@/domain/types";
import {
  EMPTY_FILTERS,
  activeFilterCount,
  filterTxns,
  isAllSelected,
  nextSort,
  orderCentsRange,
  orderDayRange,
  parseCentsInput,
  retainVisible,
  segments,
  sortTxns,
  stepIndex,
  toggleAll,
  toggleSelected,
  virtualWindow,
  type Filters,
  type Sort,
} from "./tableModel";

const DAY = 86_400_000;
const T0 = new Date(2026, 9, 20, 12, 0, 0).getTime();

function row(over: Partial<Txn> & { id: string }): Txn {
  return {
    amountCents: 1000,
    direction: "OUT",
    merchant: "便利店",
    category: "GROCERY",
    occurredAt: T0,
    source: "MANUAL",
    ...over,
  };
}

const rows: Txn[] = [
  row({ id: "a", merchant: "瑞幸咖啡", category: "DRINK", amountCents: 1500, occurredAt: T0, source: "QUICK_ADD", note: "早上一杯" }),
  row({ id: "b", merchant: "美团外卖", category: "MEAL", amountCents: 3200, occurredAt: T0 + DAY, source: "CSV" }),
  row({ id: "c", merchant: "地铁", category: "TRANSPORT", amountCents: 300, occurredAt: T0 + 2 * DAY, direction: "OUT" }),
  row({ id: "d", merchant: "工资", category: "OTHER", amountCents: 900000, occurredAt: T0 + 2 * DAY, direction: "IN", source: "MANUAL" }),
  row({ id: "e", merchant: "盒马", category: "GROCERY", amountCents: 8650, occurredAt: T0 + 3 * DAY, source: "CSV", note: "周末囤货" }),
];

const f = (over: Partial<Filters> = {}): Filters => ({ ...EMPTY_FILTERS, ...over });

describe("filterTxns", () => {
  it("returns everything for empty filters", () => {
    expect(filterTxns(rows, EMPTY_FILTERS).map((r) => r.id)).toEqual(["a", "b", "c", "d", "e"]);
  });

  it("matches merchant, note and category label, case-insensitively", () => {
    expect(filterTxns(rows, f({ query: "瑞幸" })).map((r) => r.id)).toEqual(["a"]);
    expect(filterTxns(rows, f({ query: "囤货" })).map((r) => r.id)).toEqual(["e"]);
    expect(filterTxns(rows, f({ query: "交通" })).map((r) => r.id)).toEqual([]);
    expect(filterTxns(rows, f({ query: "出行" })).map((r) => r.id)).toEqual(["c"]);
    expect(filterTxns(rows, f({ query: "  盒马  " })).map((r) => r.id)).toEqual(["e"]);
  });

  it("finds an amount typed as yuan or cents", () => {
    expect(filterTxns(rows, f({ query: "32" })).map((r) => r.id)).toEqual(["b"]);
    expect(filterTxns(rows, f({ query: "86.5" })).map((r) => r.id)).toEqual(["e"]);
  });

  it("filters by direction", () => {
    expect(filterTxns(rows, f({ direction: "IN" })).map((r) => r.id)).toEqual(["d"]);
    expect(filterTxns(rows, f({ direction: "OUT" })).map((r) => r.id)).toEqual(["a", "b", "c", "e"]);
  });

  it("treats `from` as inclusive and `to` as exclusive", () => {
    expect(filterTxns(rows, f({ from: T0 + DAY })).map((r) => r.id)).toEqual(["b", "c", "d", "e"]);
    expect(filterTxns(rows, f({ to: T0 + DAY })).map((r) => r.id)).toEqual(["a"]);
    expect(filterTxns(rows, f({ from: T0, to: T0 + DAY })).map((r) => r.id)).toEqual(["a"]);
  });

  it("filters by category set, amount bounds and source set", () => {
    expect(filterTxns(rows, f({ categories: ["DRINK", "GROCERY"] })).map((r) => r.id)).toEqual(["a", "e"]);
    expect(filterTxns(rows, f({ minCents: 1500, maxCents: 3200 })).map((r) => r.id)).toEqual(["a", "b"]);
    expect(filterTxns(rows, f({ sources: ["CSV"] })).map((r) => r.id)).toEqual(["b", "e"]);
    expect(filterTxns(rows, f({ sources: ["VOICE"] }))).toEqual([]);
  });

  it("combines every constraint with AND", () => {
    const got = filterTxns(rows, f({ direction: "OUT", categories: ["GROCERY"], sources: ["CSV"], minCents: 5000 }));
    expect(got.map((r) => r.id)).toEqual(["e"]);
  });

  it("never mutates the input", () => {
    const before = rows.map((r) => r.id);
    filterTxns(rows, f({ query: "盒马", direction: "OUT" }));
    expect(rows.map((r) => r.id)).toEqual(before);
  });
});

describe("sortTxns", () => {
  it("sorts by time newest first and oldest first", () => {
    expect(sortTxns(rows, { key: "occurredAt", dir: "desc" }).map((r) => r.id)).toEqual(["e", "c", "d", "b", "a"]);
    expect(sortTxns(rows, { key: "occurredAt", dir: "asc" }).map((r) => r.id)).toEqual(["a", "b", "c", "d", "e"]);
  });

  it("keeps ties in their incoming order (stable)", () => {
    const tied = [
      row({ id: "x", amountCents: 500 }),
      row({ id: "y", amountCents: 500 }),
      row({ id: "z", amountCents: 100 }),
    ];
    expect(sortTxns(tied, { key: "amountCents", dir: "asc" }).map((r) => r.id)).toEqual(["z", "x", "y"]);
    expect(sortTxns(tied, { key: "amountCents", dir: "desc" }).map((r) => r.id)).toEqual(["x", "y", "z"]);
  });

  it("sorts text columns with the Chinese collation", () => {
    const asc = sortTxns(rows, { key: "merchant", dir: "asc" }).map((r) => r.merchant);
    expect(asc[0]).toBe("地铁");
    expect(asc).toHaveLength(rows.length);
  });

  it("cycles asc → desc on the same column and starts new columns by type", () => {
    expect(nextSort({ key: "occurredAt", dir: "desc" }, "occurredAt")).toEqual({ key: "occurredAt", dir: "asc" });
    expect(nextSort({ key: "occurredAt", dir: "asc" }, "merchant")).toEqual({ key: "merchant", dir: "asc" });
    expect(nextSort({ key: "merchant", dir: "asc" }, "amountCents")).toEqual({ key: "amountCents", dir: "desc" });
  });

  it("accepts the EMPTY sort defaults through a typed Sort", () => {
    const sort: Sort = { key: "amountCents", dir: "desc" };
    expect(sortTxns(rows, sort)[0].id).toBe("d");
  });
});

describe("selection", () => {
  it("toggles one row without mutating the source set", () => {
    const base = new Set(["a"]);
    const next = toggleSelected(base, "b");
    expect([...next]).toEqual(["a", "b"]);
    expect([...base]).toEqual(["a"]);
    expect([...toggleSelected(next, "a")]).toEqual(["b"]);
  });

  it("selects every visible row, then clears them", () => {
    const ids = rows.map((r) => r.id);
    const all = toggleAll(new Set<string>(), ids);
    expect(isAllSelected(all, ids)).toBe(true);
    expect(toggleAll(all, ids).size).toBe(0);
  });

  it("adds the missing rows when only some are selected", () => {
    const ids = rows.map((r) => r.id);
    const partial = toggleAll(new Set(["a", "c"]), ids);
    expect(partial.size).toBe(ids.length);
    expect(isAllSelected(partial, [])).toBe(false);
  });

  it("drops hidden rows after a filter change", () => {
    const kept = retainVisible(new Set(["a", "b", "deleted"]), ["a", "b"]);
    expect([...kept]).toEqual(["a", "b"]);
  });
});

describe("virtualWindow", () => {
  const base = { rowHeight: 40, viewportHeight: 400, count: 1000, overscan: 5 };

  it("renders nothing for an empty list", () => {
    expect(virtualWindow({ ...base, scrollTop: 0, count: 0 })).toEqual({ start: 0, end: 0, padTop: 0, padBottom: 0 });
  });

  it("mounts a viewport plus overscan and pads the rest", () => {
    const w = virtualWindow({ ...base, scrollTop: 0 });
    expect(w.start).toBe(0);
    expect(w.end).toBe(16); // 11 visible + 5 overscan
    expect(w.padTop).toBe(0);
    expect(w.padBottom).toBe((1000 - 16) * 40);
  });

  it("follows the scroll position", () => {
    const w = virtualWindow({ ...base, scrollTop: 4000 });
    expect(w.start).toBe(95);
    expect(w.padTop).toBe(95 * 40);
    expect(w.end - w.start).toBe(21); // 11 visible + 5 overscan either side
  });

  it("clamps at the bottom so the window never runs past the last row", () => {
    const w = virtualWindow({ ...base, scrollTop: 39_900, count: 1000 });
    expect(w.end).toBe(1000);
    expect(w.padBottom).toBe(0);
  });

  it("adapts to row count above the 500-row virtualization threshold", () => {
    const w = virtualWindow({ ...base, scrollTop: 0, count: 501 });
    expect(w.end).toBe(16);
    expect(w.padBottom).toBe((501 - 16) * 40);
  });
});

describe("stepIndex", () => {
  it("starts at the first or last row and clamps at both ends", () => {
    expect(stepIndex(-1, 5, 1)).toBe(0);
    expect(stepIndex(-1, 5, -1)).toBe(4);
    expect(stepIndex(0, 5, -1)).toBe(0);
    expect(stepIndex(4, 5, 1)).toBe(4);
    expect(stepIndex(2, 5, 1)).toBe(3);
    expect(stepIndex(0, 0, 1)).toBe(-1);
  });
});

describe("segments", () => {
  it("splits text around every case-insensitive hit", () => {
    expect(segments("瑞幸咖啡 咖啡", "咖啡")).toEqual([
      { text: "瑞幸", hit: false },
      { text: "咖啡", hit: true },
      { text: " ", hit: false },
      { text: "咖啡", hit: true },
    ]);
  });

  it("returns the whole string when the query is blank or absent", () => {
    expect(segments("盒马", "")).toEqual([{ text: "盒马", hit: false }]);
    expect(segments("盒马", "地铁")).toEqual([{ text: "盒马", hit: false }]);
  });
});

describe("activeFilterCount", () => {
  it("counts filter groups, not individual values", () => {
    expect(activeFilterCount(EMPTY_FILTERS)).toBe(0);
    expect(activeFilterCount(f({ direction: "OUT", categories: ["MEAL", "DRINK"], minCents: 100, maxCents: 200 }))).toBe(3);
    expect(activeFilterCount(f({ from: T0, sources: ["CSV"] }))).toBe(2);
  });
});

describe("parseCentsInput", () => {
  it("reads yuan as integer cents", () => {
    expect(parseCentsInput("12")).toBe(1200);
    expect(parseCentsInput("12.5")).toBe(1250);
    expect(parseCentsInput("12.50")).toBe(1250);
    expect(parseCentsInput(" 1,234.05 ")).toBe(123405);
    expect(parseCentsInput("0.07")).toBe(7);
  });

  it("reads junk as no bound instead of NaN", () => {
    for (const junk of ["", "  ", "1.2.3", "12a", "-5", "12.345", "abc"]) expect(parseCentsInput(junk)).toBeNull();
  });
});

describe("orderCentsRange", () => {
  it("swaps a range typed the wrong way round", () => {
    expect(orderCentsRange(500, 100)).toEqual({ min: 100, max: 500 });
    expect(orderCentsRange(null, 100)).toEqual({ min: null, max: 100 });
    expect(orderCentsRange(100, null)).toEqual({ min: 100, max: null });
    expect(orderCentsRange(100, 500)).toEqual({ min: 100, max: 500 });
    expect(orderCentsRange(null, null)).toEqual({ min: null, max: null });
  });
});

describe("orderDayRange", () => {
  it("swaps two days, keeping both ends of an exclusive range", () => {
    const from = new Date(2026, 9, 24).getTime();
    const to = new Date(2026, 9, 21).getTime();
    expect(orderDayRange(from, to)).toEqual({ from: new Date(2026, 9, 20).getTime(), to: new Date(2026, 9, 25).getTime() });
  });

  it("leaves an already ordered or half-open range alone", () => {
    const a = new Date(2026, 9, 20).getTime();
    const b = new Date(2026, 9, 24).getTime();
    expect(orderDayRange(a, b)).toEqual({ from: a, to: b });
    expect(orderDayRange(a, null)).toEqual({ from: a, to: null });
    expect(orderDayRange(null, b)).toEqual({ from: null, to: b });
  });
});
