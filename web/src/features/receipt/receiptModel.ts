import { CATEGORIES, DAY_MS, type DailyReceipt, type Txn } from "../../domain/types";
import { dateKey, dayDiff, hhmm, weekdayLabel, weekStartOf } from "../../logic/time";

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
  folded: { count: number; cents: number } | null;
  income: DocGroup | null;
  stats: { count: string; delta: string; weekDay: string };
  remark: string;
  badgeReason: string | null;
  stampText: string;
  demo: boolean;
}

export interface DocContext {
  now: number;
  /** yesterday's spending; null when unknown */
  prevTotalCents: number | null;
  demo: boolean;
  variant?: Variant;
}

const MAX_GROUPS = 4;
const MAX_LINES = 3;

export const TAGLINE = "认真生活，留下凭证。";
export const DISCLAIMER = "仅作记录 · 非正式票据";
export const STUB = "— 今日小票 · 存根联 —";

export function totalOutOn(txns: readonly Txn[], dayStart: number): number {
  const end = dayStart + DAY_MS;
  return txns.filter((t) => t.direction === "OUT" && t.occurredAt >= dayStart && t.occurredAt < end).reduce((s, t) => s + t.amountCents, 0);
}

/** "较昨日 −12%" and its edge cases (nothing to compare, flat, new spending). */
export function deltaLabel(todayCents: number, prevCents: number | null): string {
  if (prevCents === null) return "较昨日 —";
  if (prevCents === 0) return todayCents === 0 ? "较昨日 持平" : "昨日无支出";
  const pct = Math.round(((todayCents - prevCents) / prevCents) * 100);
  if (pct === 0) return "较昨日 持平";
  return `较昨日 ${pct > 0 ? "+" : "−"}${Math.abs(pct)}%`;
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
      return {
        key,
        emoji: CATEGORIES[key as keyof typeof CATEGORIES].emoji,
        label: CATEGORIES[key as keyof typeof CATEGORIES].label,
        subtotalCents: list.reduce((s, t) => s + t.amountCents, 0),
        lines: shown.map((t) => ({ merchant: t.merchant, cents: t.amountCents })),
        hidden: maxLines > 0 ? sorted.length - shown.length : 0,
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
  return {
    variant,
    dayStart: receipt.dayStart,
    dateLabel: `${dateKey(receipt.dayStart)} ${weekdayLabel(receipt.dayStart)}`,
    printedAt: hhmm(ctx.now),
    isToday: dateKey(receipt.dayStart) === dateKey(ctx.now),
    totalCents: receipt.totalOutCents,
    zero,
    count: receipt.items.length,
    groups,
    folded: foldedGroups.length ? { count: foldedGroups.length, cents: foldedGroups.reduce((s, g) => s + g.subtotalCents, 0) } : null,
    income: inc.length && !brief
      ? {
          key: "IN",
          emoji: "💰",
          label: "收入",
          subtotalCents: inc.reduce((s, t) => s + t.amountCents, 0),
          lines: inc.slice(0, MAX_LINES).map((t) => ({ merchant: t.merchant, cents: t.amountCents })),
          hidden: Math.max(0, inc.length - MAX_LINES),
        }
      : null,
    stats: { count: `笔数 ${receipt.items.length}`, delta: deltaLabel(receipt.totalOutCents, ctx.prevTotalCents), weekDay: `本周第 ${week} 天` },
    remark: receipt.aiComment,
    badgeReason: receipt.badge ? `${receipt.badge.emoji} ${receipt.badge.reason}` : null,
    stampText: receipt.badge?.title ?? (zero ? "钱包休眠者" : "今日小票"),
    demo: ctx.demo,
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
  const L: string[] = [center("今 日 小 票"), center(TAGLINE), rule, pair("日期", doc.dateLabel)];
  if (doc.variant === "full") L.push(pair("出票", doc.printedAt));
  L.push(rule, center(doc.isToday ? "今日支出" : "当日支出"), center(`¥ ${fmtMoney(doc.totalCents)}`), center(DISCLAIMER), rule);
  if (doc.zero) L.push(center("今日无消费"));
  else if (doc.variant === "full") {
    L.push(pair("分类 / 明细", "金额"));
    for (const g of doc.groups) {
      L.push(pair(`${g.emoji} ${g.label}`, fmtMoney(g.subtotalCents)));
      for (const l of g.lines) L.push(pair(`   ${l.merchant}`, fmtMoney(l.cents)));
      if (g.hidden) L.push(`   … 另 ${g.hidden} 笔`);
    }
    if (doc.folded) L.push(pair(`… 其他 ${doc.folded.count} 类`, fmtMoney(doc.folded.cents)));
    if (doc.income) {
      L.push(pair(`${doc.income.emoji} ${doc.income.label}`, `+${fmtMoney(doc.income.subtotalCents)}`));
      for (const l of doc.income.lines) L.push(pair(`   ${l.merchant}`, `+${fmtMoney(l.cents)}`));
    }
  }
  if (doc.variant === "full") L.push(rule, `${doc.stats.count}  ${doc.stats.delta}  ${doc.stats.weekDay}`);
  L.push(rule, `「${doc.remark}」`);
  L.push(center(`[ ${doc.stampText} ]`), center(STUB));
  if (doc.demo) L.push(center("演示数据"));
  return L.join("\n");
}
