import { categoryLabel } from "@/components/desktop/labels";
import { getLocale } from "@/lib/strings";
import { CATEGORIES, DAY_MS, type DailyReceipt, type Txn } from "../../domain/types";
import { dateKey, dayDiff, hhmm, weekdayLabel, weekStartOf } from "../../logic/time";
import { fill, s } from "./strings";

export type Variant = "full" | "brief";

export interface DocLine {
  merchant: string;
  cents: number;
}

export interface DocGroup {
  key: string;
  emoji: string;
  label: string;
  subtotalCents: number;
  lines: DocLine[];
  /** lines not shown (kept off the paper to bound its height) */
  hidden: number;
  /** "… 2 more" for `hidden`, already in the reading language */
  hiddenLabel: string | null;
}

/** Labels on the paper, in the reading language. Fixed at build time so the paper, the PNG and the copied text agree. */
export interface ReceiptCopy {
  title: string;
  /** the letterspaced form the plain-text columns use ("今 日 小 票"); equal to `title` where spacing is not a thing */
  titleSpaced: string;
  tagline: string;
  date: string;
  printed: string;
  totalToday: string;
  totalThatDay: string;
  disclaimer: string;
  /** column header over the category / merchant lines */
  colCategory: string;
  colAmount: string;
  emptyTitle: string;
  emptyQuote: string;
  stub: string;
  /** footer under the exported image */
  footer: string;
  income: string;
}

/** Everything the paper, the PNG and the copied text are drawn from. */
export interface ReceiptDoc {
  variant: Variant;
  dayStart: number;
  dateLabel: string;
  printedAt: string;
  isToday: boolean;
  totalCents: number;
  zero: boolean;
  count: number;
  groups: DocGroup[];
  /** categories folded into one "其他 N 类" line */
  folded: { count: number; cents: number; label: string } | null;
  income: DocGroup | null;
  /** `stacked` when the three stats are too wide to share one line in this language */
  stats: { count: string; delta: string; weekDay: string; stacked: boolean };
  remark: string;
  /** the remark wrapped in the language's quote marks */
  remarkText: string;
  badgeReason: string | null;
  stampText: string;
  copy: ReceiptCopy;
  /** accessible name for the paper */
  ariaLabel: string;
}

export interface DocContext {
  now: number;
  /** yesterday's spending; null when unknown */
  prevTotalCents: number | null;
  variant?: Variant;
}

const MAX_GROUPS = 4;
const MAX_LINES = 3;
/** columns the stats row can span on one line (paper is 304px wide, minus 20px padding each side) */
const STATS_COLS = 42;

function copyOf(): ReceiptCopy {
  return {
    title: s("paper.title"),
    titleSpaced: s("paper.titleSpaced"),
    tagline: s("paper.tagline"),
    date: s("paper.date"),
    printed: s("paper.printed"),
    totalToday: s("paper.totalToday"),
    totalThatDay: s("paper.totalThatDay"),
    disclaimer: s("paper.disclaimer"),
    colCategory: s("paper.columns"),
    colAmount: s("paper.amount"),
    emptyTitle: s("paper.emptyTitle"),
    emptyQuote: s("paper.emptyQuote"),
    stub: s("paper.stub"),
    footer: s("paper.footer"),
    income: s("paper.income"),
  };
}

export function totalOutOn(txns: readonly Txn[], dayStart: number): number {
  const end = dayStart + DAY_MS;
  return txns.filter((t) => t.direction === "OUT" && t.occurredAt >= dayStart && t.occurredAt < end).reduce((s, t) => s + t.amountCents, 0);
}

/** "较昨日 −12%" and its edge cases (nothing to compare, flat, new spending). */
export function deltaLabel(todayCents: number, prevCents: number | null): string {
  if (prevCents === null) return s("paper.delta.unknown");
  if (prevCents === 0) return todayCents === 0 ? s("paper.delta.flat") : s("paper.delta.noYesterday");
  const pct = Math.round(((todayCents - prevCents) / prevCents) * 100);
  if (pct === 0) return s("paper.delta.flat");
  return fill(s(pct > 0 ? "paper.delta.up" : "paper.delta.down"), `${Math.abs(pct)}`);
}

export function fmtMoney(cents: number): string {
  return (Math.abs(cents) / 100).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function groupOut(items: readonly Txn[], maxLines: number): DocGroup[] {
  const by = new Map<string, Txn[]>();
  for (const t of items) by.set(t.category, [...(by.get(t.category) ?? []), t]);
  return [...by.entries()]
    .map(([key, list]) => {
      const sorted = [...list].sort((a, b) => b.amountCents - a.amountCents);
      const shown = sorted.slice(0, maxLines);
      const hidden = maxLines > 0 ? sorted.length - shown.length : 0;
      return {
        key,
        emoji: CATEGORIES[key as keyof typeof CATEGORIES].emoji,
        label: categoryLabel(key as keyof typeof CATEGORIES),
        subtotalCents: list.reduce((s, t) => s + t.amountCents, 0),
        lines: shown.map((t) => ({ merchant: t.merchant, cents: t.amountCents })),
        hidden,
        hiddenLabel: hidden > 0 ? fill(s("paper.hidden"), `${hidden}`) : null,
      };
    })
    .sort((a, b) => b.subtotalCents - a.subtotalCents);
}

export function buildReceiptDoc(receipt: DailyReceipt, ctx: DocContext): ReceiptDoc {
  const variant = ctx.variant ?? "full";
  const out = receipt.items.filter((t) => t.direction === "OUT");
  const inc = receipt.items.filter((t) => t.direction === "IN");
  const brief = variant === "brief";
  const all = brief ? [] : groupOut(out, MAX_LINES);
  const keep = MAX_GROUPS;
  const rest = all.slice(keep);
  const groups = rest.length > 1 ? all.slice(0, keep - 1) : all.slice(0, keep);
  const foldedGroups = rest.length > 1 ? all.slice(keep - 1) : [];
  const zero = receipt.items.length === 0;
  const week = dayDiff(weekStartOf(receipt.dayStart), receipt.dayStart) + 1;
  const copy = copyOf();
  const count = fill(s("paper.count"), `${receipt.items.length}`);
  const delta = deltaLabel(receipt.totalOutCents, ctx.prevTotalCents);
  const weekDay = fill(s("paper.weekDay"), `${week}`);
  // the paper keeps its ISO date and appends the weekday in the reading language ("周三" / "Wed")
  const weekday = getLocale() === "en" ? new Intl.DateTimeFormat("en-US", { weekday: "short" }).format(receipt.dayStart) : weekdayLabel(receipt.dayStart);
  return {
    variant,
    dayStart: receipt.dayStart,
    dateLabel: `${dateKey(receipt.dayStart)} ${weekday}`,
    printedAt: hhmm(ctx.now),
    isToday: dateKey(receipt.dayStart) === dateKey(ctx.now),
    totalCents: receipt.totalOutCents,
    zero,
    count: receipt.items.length,
    groups,
    folded: foldedGroups.length
      ? {
          count: foldedGroups.length,
          cents: foldedGroups.reduce((s, g) => s + g.subtotalCents, 0),
          label: fill(s("paper.folded"), `${foldedGroups.length}`),
        }
      : null,
    income: inc.length && !brief
      ? {
          key: "IN",
          emoji: "💰",
          label: copy.income,
          subtotalCents: inc.reduce((s, t) => s + t.amountCents, 0),
          lines: inc.slice(0, MAX_LINES).map((t) => ({ merchant: t.merchant, cents: t.amountCents })),
          hidden: Math.max(0, inc.length - MAX_LINES),
          hiddenLabel: inc.length > MAX_LINES ? fill(s("paper.hidden"), `${inc.length - MAX_LINES}`) : null,
        }
      : null,
    stats: { count, delta, weekDay, stacked: displayWidth(`${count}   ${delta}   ${weekDay}`) > STATS_COLS },
    remark: receipt.aiComment,
    remarkText: fill(s("paper.quote"), receipt.aiComment),
    badgeReason: receipt.badge ? `${receipt.badge.emoji} ${receipt.badge.reason}` : null,
    stampText: receipt.badge?.title ?? (zero ? s("paper.stamp.sleeper") : s("paper.stamp.today")),
    copy,
    ariaLabel: fill(s("paper.aria"), dateKey(receipt.dayStart)),
  };
}

/* ---------- plain text (复制文字) ---------- */

/** Terminal columns: CJK and emoji take two. */
export function displayWidth(s: string): number {
  let w = 0;
  for (const ch of s) {
    const c = ch.codePointAt(0) ?? 0;
    w += c >= 0x1100 && (c <= 0x115f || (c >= 0x2e80 && c <= 0xa4cf) || (c >= 0xac00 && c <= 0xd7a3) || (c >= 0xf900 && c <= 0xfaff) || (c >= 0xfe30 && c <= 0xfe6f) || (c >= 0xff00 && c <= 0xff60) || c >= 0x1f300) ? 2 : 1;
  }
  return w;
}

const COLS = 32;

function center(s: string): string {
  return " ".repeat(Math.max(0, Math.floor((COLS - displayWidth(s)) / 2))) + s;
}

function pair(left: string, right: string): string {
  return left + " ".repeat(Math.max(1, COLS - displayWidth(left) - displayWidth(right))) + right;
}

export function receiptPlainText(doc: ReceiptDoc): string {
  const rule = "-".repeat(COLS);
  const c = doc.copy;
  const L: string[] = [center(c.titleSpaced), center(c.tagline), rule, pair(c.date, doc.dateLabel)];
  if (doc.variant === "full") L.push(pair(c.printed, doc.printedAt));
  L.push(rule, center(doc.isToday ? c.totalToday : c.totalThatDay), center(`¥ ${fmtMoney(doc.totalCents)}`), center(c.disclaimer), rule);
  if (doc.zero) L.push(center(c.emptyTitle));
  else if (doc.variant === "full") {
    L.push(pair(c.colCategory, c.colAmount));
    for (const g of doc.groups) {
      L.push(pair(`${g.emoji} ${g.label}`, fmtMoney(g.subtotalCents)));
      for (const l of g.lines) L.push(pair(`   ${l.merchant}`, fmtMoney(l.cents)));
      if (g.hiddenLabel) L.push(`   ${g.hiddenLabel}`);
    }
    if (doc.folded) L.push(pair(doc.folded.label, fmtMoney(doc.folded.cents)));
    if (doc.income) {
      L.push(pair(`${doc.income.emoji} ${doc.income.label}`, `+${fmtMoney(doc.income.subtotalCents)}`));
      for (const l of doc.income.lines) L.push(pair(`   ${l.merchant}`, `+${fmtMoney(l.cents)}`));
    }
  }
  if (doc.variant === "full") {
    L.push(rule, ...(doc.stats.stacked ? [center(doc.stats.count), center(doc.stats.delta), center(doc.stats.weekDay)] : [`${doc.stats.count}  ${doc.stats.delta}  ${doc.stats.weekDay}`]));
  }
  L.push(rule, doc.remarkText);
  L.push(center(`[ ${doc.stampText} ]`), center(c.stub));
  return L.join("\n");
}
