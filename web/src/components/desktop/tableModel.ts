import { getLocale } from "@/lib/strings";
import { addDays } from "@/logic/time";
import { CATEGORIES, type Category, type Source, type Txn, startOfDay } from "@/domain/types";
import { categoryLabel, sourceLabel } from "./labels";

/**
 * Pure logic behind the desktop DataTable (docs/WEB-SPEC.md §A.2): filtering, sorting, selection,
 * keyboard movement and the virtual window. Kept free of React so the rules are testable.
 */

export type SortKey = "occurredAt" | "amountCents" | "merchant" | "category" | "source";
export type SortDir = "asc" | "desc";
export interface Sort {
  key: SortKey;
  dir: SortDir;
}

export type DirectionFilter = "ALL" | "OUT" | "IN";

export interface Filters {
  query: string;
  direction: DirectionFilter;
  /** inclusive */
  from: number | null;
  /** exclusive */
  to: number | null;
  /** empty = every category */
  categories: Category[];
  minCents: number | null;
  maxCents: number | null;
  /** empty = every source */
  sources: Source[];
}

export const EMPTY_FILTERS: Filters = {
  query: "",
  direction: "ALL",
  from: null,
  to: null,
  categories: [],
  minCents: null,
  maxCents: null,
  sources: [],
};

/** Free text hits the merchant, the note and the category label; `12.5` also finds a ¥12.50 row. */
function matchesQuery(t: Txn, q: string): boolean {
  if (!q) return true;
  const label = `${CATEGORIES[t.category].label} ${categoryLabel(t.category)}`;
  if (t.merchant.trim().toLowerCase().includes(q)) return true;
  if (t.note && t.note.trim().toLowerCase().includes(q)) return true;
  if (label.includes(q)) return true;
  const yuan = (t.amountCents / 100).toFixed(2).replace(/\.00$/, "");
  return yuan.includes(q) || String(t.amountCents).includes(q);
}

export function filterTxns(rows: readonly Txn[], f: Filters): Txn[] {
  const q = f.query.trim().toLowerCase();
  return rows.filter((t) => {
    if (f.direction !== "ALL" && t.direction !== f.direction) return false;
    if (f.from !== null && t.occurredAt < f.from) return false;
    if (f.to !== null && t.occurredAt >= f.to) return false;
    if (f.categories.length > 0 && !f.categories.includes(t.category)) return false;
    if (f.sources.length > 0 && !f.sources.includes(t.source)) return false;
    if (f.minCents !== null && t.amountCents < f.minCents) return false;
    if (f.maxCents !== null && t.amountCents > f.maxCents) return false;
    return matchesQuery(t, q);
  });
}

/* -------------------------------------------------------------- inputs */

/**
 * Amount filter input → cents. Empty, `1.2.3`, `12a`, `-5` and anything past two decimals read as
 * "no bound" instead of a NaN that would empty the table.
 */
export function parseCentsInput(raw: string): number | null {
  const text = raw.trim().replace(/[,¥￥\s]/g, "");
  if (!/^\d+(?:\.\d{0,2})?$/.test(text)) return null;
  const cents = Math.round(Number(text) * 100);
  return Number.isFinite(cents) ? cents : null;
}

/** A range typed the wrong way round reads as the same range, just swapped. */
export function orderCentsRange(min: number | null, max: number | null): { min: number | null; max: number | null } {
  return min !== null && max !== null && min > max ? { min: max, max: min } : { min, max };
}

/** `to` is exclusive (next-day midnight); typed backwards it swaps, keeping both days. */
export function orderDayRange(from: number | null, to: number | null): { from: number | null; to: number | null } {
  if (from === null || to === null || from < to) return { from, to };
  return { from: startOfDay(to - 1), to: addDays(startOfDay(from), 1) };
}

function sortValue(t: Txn, key: SortKey): number | string {
  switch (key) {
    case "merchant":
      return t.merchant;
    case "category":
      return categoryLabel(t.category);
    case "source":
      return sourceLabel(t.source);
    case "amountCents":
      return t.amountCents;
    default:
      return t.occurredAt;
  }
}

/** Stable sort (ties keep their incoming order) so re-sorting never shuffles equal rows. */
export function sortTxns(rows: readonly Txn[], sort: Sort): Txn[] {
  const sign = sort.dir === "asc" ? 1 : -1;
  return rows
    .map((row, index) => ({ row, index }))
    .sort((a, b) => {
      const av = sortValue(a.row, sort.key);
      const bv = sortValue(b.row, sort.key);
      const cmp = typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv), getLocale() === "en" ? "en" : "zh-CN");
      return cmp !== 0 ? cmp * sign : a.index - b.index;
    })
    .map((x) => x.row);
}

/** Sorting cycles asc → desc → asc; a new column starts descending (newest / largest first). */
export function nextSort(current: Sort, key: SortKey): Sort {
  if (current.key !== key) return { key, dir: key === "merchant" || key === "category" || key === "source" ? "asc" : "desc" };
  return { key, dir: current.dir === "asc" ? "desc" : "asc" };
}

/* ------------------------------------------------------------- selection */

export function toggleSelected(selected: ReadonlySet<string>, id: string): Set<string> {
  const next = new Set(selected);
  if (!next.delete(id)) next.add(id);
  return next;
}

export function isAllSelected(selected: ReadonlySet<string>, ids: readonly string[]): boolean {
  return ids.length > 0 && ids.every((id) => selected.has(id));
}

/** Header checkbox: select every visible row, or clear them when they are all already on. */
export function toggleAll(selected: ReadonlySet<string>, ids: readonly string[]): Set<string> {
  const next = new Set(selected);
  if (isAllSelected(next, ids)) ids.forEach((id) => next.delete(id));
  else ids.forEach((id) => next.add(id));
  return next;
}

/** Drop ids that are no longer visible (filter changed) so batch actions never touch hidden rows. */
export function retainVisible(selected: ReadonlySet<string>, ids: readonly string[]): Set<string> {
  const visible = new Set(ids);
  return new Set([...selected].filter((id) => visible.has(id)));
}

/* ------------------------------------------------------------- virtual */

export interface VirtualWindow {
  start: number;
  /** exclusive */
  end: number;
  padTop: number;
  padBottom: number;
}

/** Rows to mount for a scrolled viewport; `overscan` keeps a few rows ready above and below. */
export function virtualWindow(opts: { scrollTop: number; viewportHeight: number; rowHeight: number; count: number; overscan?: number }): VirtualWindow {
  const { scrollTop, viewportHeight, rowHeight, count, overscan = 6 } = opts;
  if (count <= 0) return { start: 0, end: 0, padTop: 0, padBottom: 0 };
  const first = Math.floor(Math.max(0, scrollTop) / rowHeight);
  const visible = Math.ceil(Math.max(0, viewportHeight) / rowHeight) + 1;
  const start = Math.max(0, Math.min(first - overscan, Math.max(0, count - 1)));
  const end = Math.max(start, Math.min(count, first + visible + overscan));
  return { start, end, padTop: start * rowHeight, padBottom: (count - end) * rowHeight };
}

/* ------------------------------------------------------------ keyboard */

export function stepIndex(index: number, length: number, delta: number): number {
  if (length <= 0) return -1;
  const base = index < 0 ? (delta > 0 ? -1 : length) : index;
  return Math.max(0, Math.min(length - 1, base + delta));
}

/* ------------------------------------------------------------ highlight */

export interface Segment {
  text: string;
  hit: boolean;
}

/** Split text into plain / hit parts for search highlighting (case-insensitive, first occurrence onwards). */
export function segments(text: string, query: string): Segment[] {
  const q = query.trim().toLowerCase();
  if (!q) return [{ text, hit: false }];
  const lower = text.toLowerCase();
  const out: Segment[] = [];
  let at = 0;
  while (at < text.length) {
    const found = lower.indexOf(q, at);
    if (found < 0) break;
    if (found > at) out.push({ text: text.slice(at, found), hit: false });
    out.push({ text: text.slice(found, found + q.length), hit: true });
    at = found + q.length;
  }
  if (at < text.length) out.push({ text: text.slice(at), hit: false });
  return out.length > 0 ? out : [{ text, hit: false }];
}

/* --------------------------------------------------------------- chips */

export function activeFilterCount(f: Filters): number {
  let n = 0;
  if (f.direction !== "ALL") n++;
  if (f.from !== null || f.to !== null) n++;
  if (f.categories.length > 0) n++;
  if (f.minCents !== null || f.maxCents !== null) n++;
  if (f.sources.length > 0) n++;
  return n;
}
