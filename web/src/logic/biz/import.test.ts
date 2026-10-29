import { describe, expect, it } from "vitest";
import {
  applyPreview,
  buildPreview,
  classifyRows,
  exportRevenueCsv,
  parseRevenueCsv,
  previewImport,
  splitCsvLine,
  type ParsedRow,
} from "./import";
import { entryFor, normalize, type RevenueEntry } from "./book";

const TODAY = new Date(2026, 9, 28).getTime();

function book(rows: Array<[string, number, number]>): RevenueEntry[] {
  return normalize(
    rows.map(([iso, revenueCents, orders]) => ({
      day: new Date(`${iso}T00:00:00`).getTime(),
      revenueCents,
      orders,
      note: "",
      source: "MANUAL" as const,
    })),
  );
}

describe("CSV reading", () => {
  it("skips a header, comments and blank lines", () => {
    const rows = parseRevenueCsv(
      "\uFEFF日期,营收,订单,备注\n# 十月\n2026-10-27,1280.5,86,\n\n2026-10-26,\"2,650\",0,\n",
      TODAY,
    );
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({ line: 3, revenueCents: 128_050, orders: 86, error: null });
    expect(rows[1]).toMatchObject({ revenueCents: 265_000, orders: 0, error: null });
  });

  it("explains every line it cannot import", () => {
    const rows = parseRevenueCsv("2026-10-27,1280\n下周,900\n2026-11-02,900\n2026-10-25,-5\n2026-10-24,900,很多", TODAY);
    expect(rows.map((r) => r.error)).toEqual([null, "date", "future", "negative", "orders"]);
    expect(rows[1].day).toBeNull();
  });

  it("reads a short date, tab-separated paste, and a bare two-column row", () => {
    expect(parseRevenueCsv("10/26\t2650\t40", TODAY)[0]).toMatchObject({ orders: 40, note: "" });
    expect(parseRevenueCsv("10/26,2650", TODAY)[0]).toMatchObject({ revenueCents: 265_000, orders: 0 });
  });

  it("splits quoted fields with escapes", () => {
    expect(splitCsvLine('a,"b,c","d""e"')).toEqual(["a", "b,c", 'd"e']);
  });
});

describe("dedupe against the book", () => {
  const existing = book([["2026-10-27", 128_050, 86]]);

  const rows = parseRevenueCsv(
    [
      "2026-10-27,1280.5,86", // same numbers → nothing to do
      "2026-10-26,2650,40", // unknown day → add
      "2026-10-25,900,20", // unknown day → add
      "2026-10-25,950,21", // second row for the same day → duplicate
      "2026-10-24,oops", // unreadable
    ].join("\n"),
    TODAY,
  );

  it("marks new, same, duplicate and error rows", () => {
    const preview = buildPreview(rows, existing, "replace");
    expect(preview.rows.map((r) => r.status)).toEqual(["same", "new", "new", "duplicate", "error"]);
    expect(preview.counts).toEqual({ add: 2, overwrite: 0, skip: 2, error: 1 });
  });

  it("lets the owner choose what happens to a day that already has numbers", () => {
    const changed = parseRevenueCsv("2026-10-27,1300,90", TODAY);
    const keep = buildPreview(changed, existing, "keep");
    const replace = buildPreview(changed, existing, "replace");
    expect(keep.rows[0]).toMatchObject({ status: "differ", outcome: "skip", existing: { revenueCents: 128_050 } });
    expect(keep.changes).toHaveLength(0);
    expect(replace.rows[0]).toMatchObject({ status: "differ", outcome: "overwrite" });
    expect(replace.changes[0]).toMatchObject({ revenueCents: 130_000, source: "CSV" });
    expect(replace.counts).toMatchObject({ add: 0, overwrite: 1, skip: 0 });
  });

  it("applies only the rows the policy agreed to, and keeps other days", () => {
    const preview = previewImport("2026-10-26,2650,40\n2026-10-20,1000,10", existing, TODAY, "keep", "oct.csv");
    const next = applyPreview(existing, preview);
    expect(next.map((e) => e.revenueCents)).toEqual([100_000, 265_000, 128_050]);
    expect(entryFor(next, new Date(2026, 9, 26).getTime())?.source).toBe("CSV");
    expect(next.find((e) => e.revenueCents === 128_050)?.source).toBe("MANUAL");
  });

  it("adds nothing when every row is a duplicate", () => {
    const preview = previewImport("2026-10-27,1280.5,86", existing, TODAY, "replace");
    expect(preview.counts).toEqual({ add: 0, overwrite: 0, skip: 1, error: 0 });
    expect(applyPreview(existing, preview)).toHaveLength(1);
  });

  it("keeps an error row out of the changes but in the preview", () => {
    const parsed: ParsedRow[] = [{ line: 1, raw: "x", day: null, revenueCents: null, orders: 0, note: "", error: "date" }];
    expect(classifyRows(parsed, existing, "replace")[0].outcome).toBe("skip");
  });
});

describe("export", () => {
  it("writes a spreadsheet-friendly file with a BOM", () => {
    const text = exportRevenueCsv(book([["2026-10-27", 128_050, 86]]));
    expect(text.startsWith("\uFEFF日期,营收,订单,备注,来源")).toBe(true);
    expect(text).toContain("2026-10-27,1280.50,86,,手动");
  });
});
