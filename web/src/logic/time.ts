import { DAY_MS, startOfDay } from "../domain/types";

/** Local calendar helpers. Days are local-time midnights (epoch millis). */

export function addDays(dayStart: number, days: number): number {
  const d = new Date(dayStart);
  d.setDate(d.getDate() + days);
  return startOfDay(d.getTime());
}

/** Monday 00:00 of the week containing `ts`. */
export function weekStartOf(ts: number): number {
  const d = new Date(startOfDay(ts));
  const dow = (d.getDay() + 6) % 7; // Mon=0 … Sun=6
  d.setDate(d.getDate() - dow);
  return d.getTime();
}

export function dayDiff(fromDayStart: number, toDayStart: number): number {
  return Math.round((toDayStart - fromDayStart) / DAY_MS);
}

export function at(dayStart: number, hour: number, minute = 0): number {
  const d = new Date(dayStart);
  d.setHours(hour, minute, 0, 0);
  return d.getTime();
}

export function hourOf(ts: number): number {
  return new Date(ts).getHours();
}

export function isWeekend(ts: number): boolean {
  const g = new Date(ts).getDay();
  return g === 0 || g === 6;
}

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

export function weekdayLabel(ts: number): string {
  return WEEKDAYS[new Date(ts).getDay()];
}

export function dateKey(ts: number): string {
  const d = new Date(ts);
  const m = `${d.getMonth() + 1}`.padStart(2, "0");
  const day = `${d.getDate()}`.padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

export function parseDateKey(key: string): number {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1).getTime();
}

/** "10月27日 周二" */
export function dayLabel(ts: number): string {
  const d = new Date(ts);
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weekdayLabel(ts)}`;
}

/** "今天" / "昨天" / "前天" / "10月25日 周日" relative to `now`. */
export function relativeDayLabel(dayStart: number, now: number): string {
  const diff = dayDiff(dayStart, startOfDay(now));
  if (diff === 0) return "今天";
  if (diff === 1) return "昨天";
  if (diff === 2) return "前天";
  return dayLabel(dayStart);
}

export function hhmm(ts: number): string {
  const d = new Date(ts);
  return `${`${d.getHours()}`.padStart(2, "0")}:${`${d.getMinutes()}`.padStart(2, "0")}`;
}

/** Small deterministic string hash → uint32. */
export function hashString(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Deterministic PRNG (mulberry32). */
export function seeded(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function pickBySeed<T>(items: readonly T[], seed: number): T {
  return items[seed % items.length];
}
