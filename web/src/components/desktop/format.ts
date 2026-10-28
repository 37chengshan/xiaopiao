import { startOfDay, type Cents } from "@/domain/types";
import { getLocale } from "@/lib/strings";
import { dayDiff, weekdayLabel } from "@/logic/time";

/** ¥ + grouping, built from integer cents (no float maths on money). */
export function money(cents: Cents): string {
  const abs = Math.abs(Math.round(cents));
  const yuan = Math.floor(abs / 100).toLocaleString("en-US");
  const fen = String(abs % 100).padStart(2, "0");
  return `${cents < 0 ? "−" : ""}¥${yuan}.${fen}`;
}

/** Signed amount for a table/total cell: expenses read as a plain number, income carries a `+`. */
export function signedMoney(cents: Cents, direction: "OUT" | "IN"): string {
  return direction === "IN" ? `+${money(cents)}` : money(cents);
}

export type DeltaDir = "up" | "down" | "flat";

export interface Delta {
  dir: DeltaDir;
  pct: number | null;
}

/** Change against a previous period. `prev` of 0 has no meaningful percentage, so it reads as flat. */
export function delta(cur: Cents, prev: Cents | null): Delta {
  if (prev === null || prev === 0) return { dir: cur === 0 ? "flat" : "up", pct: null };
  const p = Math.round(((cur - prev) / prev) * 100);
  if (p === 0) return { dir: "flat", pct: 0 };
  return { dir: p > 0 ? "up" : "down", pct: p };
}

const MONTH_EN = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** "10月18日 周日" / "Oct 18, Sun" — date labels follow the UI language, whatever the stored data language. */
export function dayLabel(ts: number): string {
  const d = new Date(ts);
  return getLocale() === "en" ? `${MONTH_EN[d.getMonth()]} ${d.getDate()}, ${weekdayLabel(ts).replace("周", "")}` : `${d.getMonth() + 1}月${d.getDate()}日 ${weekdayLabel(ts)}`;
}

/** Today / yesterday / the day before, then a full date. */
export function relativeDay(dayStart: number, now: number): string {
  const diff = dayDiff(dayStart, startOfDay(now));
  if (getLocale() === "en") {
    if (diff === 0) return "Today";
    if (diff === 1) return "Yesterday";
    return dayLabel(dayStart);
  }
  if (diff === 0) return "今天";
  if (diff === 1) return "昨天";
  if (diff === 2) return "前天";
  return dayLabel(dayStart);
}

/** ¥ amount without the fen part, for axis ticks and tight labels. */
export function moneyShort(cents: Cents): string {
  const yuan = Math.round(cents / 100);
  if (yuan >= 10000) return `¥${(yuan / 10000).toFixed(yuan >= 100000 ? 0 : 1)}万`;
  return `¥${yuan.toLocaleString("en-US")}`;
}
