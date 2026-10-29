import { money, parseDayText, parseMoneyText, shortDate } from "./format";
import { merge, type RevenueEntry } from "./book";

/**
 * Importing a shop's own daily totals: `日期,营收[,订单][,备注]`.
 * Tolerant of quoted money (`"1,280.50"`), `2026/10/27`, `10/27`, a BOM, header lines, blank lines and `#` notes.
 * Nothing is guessed: an unreadable line is reported and left out, never imported as a zero.
 */

/** Why a line cannot be imported. The wording lives in the UI dictionary, not here. */
export type ImportErrorCode = "columns" | "date" | "future" | "revenue" | "negative" | "orders";

export interface ParsedRow {
  /** 1-based line in the source text */
  line: number;
  raw: string;
  day: number | null;
  revenueCents: number | null;
  orders: number;
  note: string;
  /** why the line cannot be imported; null when it parsed */
  error: ImportErrorCode | null;
}

/** How a day that already has numbers is treated on import. */
export type ConflictPolicy = "replace" | "keep";

/** new = day unknown to the book; same = identical numbers; differ = the book has other numbers. */
export type RowStatus = "new" | "same" | "differ" | "duplicate" | "error";

export interface PreviewRow extends ParsedRow {
  status: RowStatus;
  /** what the book holds for that day today */
  existing: RevenueEntry | null;
  /** what this row would write, when it writes at all */
  outcome: "add" | "overwrite" | "skip";
}

export interface ImportPreview {
  fileName: string;
  rows: PreviewRow[];
  counts: { add: number; overwrite: number; skip: number; error: number };
  /** days the import would add or overwrite */
  changes: RevenueEntry[];
  totalCents: number;
}

const MAX_ORDERS = 100_000;
const MAX_NOTE = 40;

/** RFC4180-ish split for a single line: quoted fields, `""` escapes, ragged rows allowed. */
export function splitCsvLine(line: string): string[] {
  const out: string[] = [];
  let field = "";
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (quoted) {
      if (ch === '"') {
        if (line[i + 1] === '"') {
          field += '"';
          i++;
        } else quoted = false;
      } else field += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === "," || ch === "\t") {
      out.push(field);
      field = "";
    } else field += ch;
  }
  out.push(field);
  return out;
}

const HEADER_WORDS = ["日期", "date", "营收", "营业额", "revenue", "orders", "订单", "金额"];

function looksLikeHeader(fields: readonly string[], todayStart: number): boolean {
  const head = fields.slice(0, 3).join("").toLowerCase();
  if (HEADER_WORDS.some((w) => head.includes(w.toLowerCase()))) return true;
  // "Mon,1/2" style: a first cell that has letters and is not a date.
  return fields.length >= 2 && /[a-z\u4e00-\u9fa5]/i.test(fields[0]) && parseDayText(fields[0], todayStart) === null;
}

function parseRow(line: number, raw: string, fields: readonly string[], todayStart: number): ParsedRow {
  const fail = (error: ImportErrorCode): ParsedRow => ({ line, raw, day: null, revenueCents: null, orders: 0, note: "", error });
  if (fields.length < 2) return fail("columns");
  const day = parseDayText(fields[0], todayStart);
  if (day === null) return fail("date");
  if (day > todayStart) return fail("future");
  const revenueCents = parseMoneyText(fields[1]);
  if (revenueCents === null) return fail("revenue");
  if (revenueCents < 0) return fail("negative");
  const ordersText = (fields[2] ?? "").trim();
  const orders = ordersText === "" ? 0 : Number(ordersText.replace(/[,，\s]/g, ""));
  if (!Number.isInteger(orders) || orders < 0 || orders > MAX_ORDERS) return fail("orders");
  return {
    line,
    raw,
    day,
    revenueCents,
    orders,
    note: (fields[3] ?? "").trim().replace(/^"|"$/g, "").slice(0, MAX_NOTE),
    error: null,
  };
}

/** Reads a CSV (or TSV, by tab detection per line) text into rows. Never throws on bad input. */
export function parseRevenueCsv(text: string, todayStart: number): ParsedRow[] {
  const rows: ParsedRow[] = [];
  const lines = text.replace(/^\uFEFF/, "").split(/\r\n|\n|\r/);
  let headerChecked = false;
  lines.forEach((rawLine, index) => {
    const line = rawLine.trim();
    if (line === "" || line.startsWith("#")) return;
    const fields = splitCsvLine(line);
    if (!headerChecked) {
      headerChecked = true;
      if (looksLikeHeader(fields, todayStart)) return;
    }
    rows.push(parseRow(index + 1, line, fields, todayStart));
  });
  return rows;
}

/**
 * Classifies parsed rows against the book. A day already seen earlier in the same file is a
 * `duplicate` (first one wins), a day whose numbers match is `same`, a different day is `differ`
 * and follows the policy.
 */
export function classifyRows(rows: readonly ParsedRow[], book: readonly RevenueEntry[], policy: ConflictPolicy): PreviewRow[] {
  const byDay = new Map(book.map((e) => [e.day, e]));
  const seen = new Set<number>();
  return rows.map((row) => {
    if (row.error !== null || row.day === null || row.revenueCents === null) {
      return { ...row, status: "error" as const, existing: null, outcome: "skip" as const };
    }
    const existing = byDay.get(row.day) ?? null;
    if (seen.has(row.day)) {
      return { ...row, status: "duplicate" as const, existing, outcome: "skip" as const };
    }
    seen.add(row.day);
    if (!existing) return { ...row, status: "new" as const, existing: null, outcome: "add" as const };
    const identical =
      existing.revenueCents === row.revenueCents && existing.orders === row.orders && existing.note === row.note;
    if (identical) return { ...row, status: "same" as const, existing, outcome: "skip" as const };
    return {
      ...row,
      status: "differ" as const,
      existing,
      outcome: policy === "replace" ? ("overwrite" as const) : ("skip" as const),
    };
  });
}

/** Builds the preview: classified rows, counts, and the day rows the import would write. */
export function previewImport(
  text: string,
  book: readonly RevenueEntry[],
  todayStart: number,
  policy: ConflictPolicy,
  fileName = "",
): ImportPreview {
  return buildPreview(parseRevenueCsv(text, todayStart), book, policy, fileName);
}

/** Same, from rows already extracted (an Excel sheet, a pasted table). */
export function buildPreview(
  parsed: readonly ParsedRow[],
  book: readonly RevenueEntry[],
  policy: ConflictPolicy,
  fileName = "",
): ImportPreview {
  const rows = classifyRows(parsed, book, policy);
  const counts = { add: 0, overwrite: 0, skip: 0, error: 0 };
  const changes: RevenueEntry[] = [];
  for (const row of rows) {
    if (row.status === "error") counts.error++;
    else if (row.outcome === "skip") counts.skip++;
    if (row.outcome === "add") counts.add++;
    if (row.outcome === "overwrite") counts.overwrite++;
    if (row.outcome !== "skip" && row.day !== null && row.revenueCents !== null) {
      changes.push({
        day: row.day,
        revenueCents: row.revenueCents,
        orders: row.orders,
        note: row.note,
        source: "CSV",
      });
    }
  }
  return { fileName, rows, counts, changes, totalCents: changes.reduce((n, e) => n + e.revenueCents, 0) };
}

/** Applies a preview to the book. Imported rows become source `CSV`. */
export function applyPreview(book: readonly RevenueEntry[], preview: ImportPreview): RevenueEntry[] {
  return preview.changes.length === 0 ? [...book].sort((a, b) => a.day - b.day) : merge(book, preview.changes);
}

// ---- export -----------------------------------------------------------------------------------

/** Quotes a field only when it needs it (comma, quote, newline, tab). */
export function csvCell(value: string): string {
  return /[",\n\t]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/** The book as `日期,营收,订单,备注,来源` with a BOM so spreadsheet apps read UTF-8. */
export function exportRevenueCsv(entries: readonly RevenueEntry[]): string {
  const head = "日期,营收,订单,备注,来源";
  const lines = [...entries]
    .sort((a, b) => a.day - b.day)
    .map((e) => {
      const d = new Date(e.day);
      const date = `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, "0")}-${`${d.getDate()}`.padStart(2, "0")}`;
      const source = e.source === "CSV" ? "导入" : e.source === "MANUAL" ? "手动" : "记录";
      return [date, (e.revenueCents / 100).toFixed(2), `${e.orders}`, csvCell(e.note), source].join(",");
    });
  return `\uFEFF${head}\n${lines.join("\n")}\n`;
}
