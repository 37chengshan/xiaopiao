import { startOfDay } from "../../domain/types";
import { getLocale } from "../../lib/strings";
import { addDays, dayDiff, dateKey } from "../time";

/**
 * Number and date formatting for the shop console. Weekday names and sentence wording live in
 * `features/biz/strings.ts`; only the compact axis ticks ask the locale whether to shorten a large
 * amount as 万 or as k/M, so the same cents always read the same everywhere else.
 */

/** "¥6,420.5" — grouped thousands, decimals only when the amount has them. */
export function money(cents: number): string {
  return cents < 0 ? `-¥${moneyPlain(-cents)}` : `¥${moneyPlain(cents)}`;
}

/** "6,420.5" (no currency mark, sign kept). */
export function moneyPlain(cents: number): string {
  const abs = Math.abs(Math.round(cents));
  const whole = `${Math.floor(abs / 100)}`.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  const rest = abs % 100;
  const frac = rest === 0 ? "" : rest % 10 === 0 ? `.${rest / 10}` : `.${`${rest}`.padStart(2, "0")}`;
  return `${cents < 0 ? "-" : ""}${whole}${frac}`;
}

/** "6.4k" / "1.2万" (zh) or "¥6.4k" / "¥1.1M" (en) — axis labels only; the exact number stays in the tooltip. */
export function moneyCompact(cents: number): string {
  const yuan = cents / 100;
  if (getLocale() === "en") {
    const abs = Math.abs(yuan);
    if (abs >= 1_000_000) return `¥${Number((yuan / 1_000_000).toFixed(1))}M`;
    if (abs >= 1_000) return `¥${Number((yuan / 1_000).toFixed(1))}k`;
    return `¥${Math.round(yuan)}`;
  }
  if (Math.abs(yuan) >= 10_000) return `${(yuan / 10_000).toFixed(yuan % 10_000 === 0 ? 0 : 1)}万`;
  if (Math.abs(yuan) >= 1_000) return `${(yuan / 1_000).toFixed(1)}k`;
  return `${Math.round(yuan)}`;
}

/** "+12%" / "-4%" / "持平" (empty when there is no baseline). */
export function changeText(pct: number | null): string {
  if (pct === null) return "";
  const points = Math.round(pct * 100);
  if (points === 0) return "0%";
  return `${points > 0 ? "+" : ""}${points}%`;
}

/** Editable value for amount inputs: "1280.5". */
export function centsToInput(cents: number): string {
  return (Math.round(cents) / 100).toFixed(2).replace(/\.00$/, "");
}

/** "1,280.5" / "¥1,280.50" / "1280" → cents, or null. */
export function parseMoneyText(raw: string): number | null {
  const text = raw.trim().replace(/["'¥￥,，\s]/g, "");
  if (text === "") return null;
  const m = text.match(/^(-?)(\d{1,12})(?:\.(\d{1,4}))?$/);
  if (!m) return null;
  const [, sign, whole, frac = ""] = m;
  const cents = Number(whole) * 100 + Math.round(Number(`0.${frac || "0"}`) * 100);
  return sign === "-" ? -cents : cents;
}

/** "2026-10-28", "2026/1/5", "2026.1.5", "10/27" → day-start, or null. Never guesses an impossible date. */
export function parseDayText(raw: string, todayStart: number): number | null {
  const text = raw.trim().replace(/["']/g, "").replace(/\s/g, "");
  const full = text.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/);
  const short = full ? null : text.match(/^(\d{1,2})[-/.](\d{1,2})$/);
  const year = full ? Number(full[1]) : new Date(todayStart).getFullYear();
  const month = full ? Number(full[2]) : short ? Number(short[1]) : NaN;
  const day = full ? Number(full[3]) : short ? Number(short[2]) : NaN;
  if (!Number.isFinite(month) || !Number.isFinite(day)) return null;
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  const d = new Date(year, month - 1, day, 0, 0, 0, 0);
  // Roll-over guard: 2026-02-31 silently becomes March.
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) return null;
  return startOfDay(d.getTime());
}

export { addDays, dayDiff, dateKey, startOfDay };

/** 0 = Monday … 6 = Sunday. */
export function weekdayIndex(dayStart: number): number {
  return (new Date(dayStart).getDay() + 6) % 7;
}

/** Monday 00:00 of the week containing `dayStart`. */
export function weekStart(dayStart: number): number {
  return addDays(dayStart, -weekdayIndex(dayStart));
}

/** "2026-10". */
export function monthKey(dayStart: number): string {
  const d = new Date(dayStart);
  return `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, "0")}`;
}

/** "10/28". */
export function shortDate(dayStart: number): string {
  const d = new Date(dayStart);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}
