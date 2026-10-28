import { type Badge, type Txn, DAY_MS, startOfDay, yuan } from "../domain/types";
import { addDays, hourOf, isWeekend } from "./time";

export type BadgeScope = "day" | "week";

export interface BadgeContext {
  /** every known transaction (used for "previous period" and "first ever" checks) */
  all: readonly Txn[];
  /** [start, end) in epoch millis */
  start: number;
  end: number;
  now: number;
  scope: BadgeScope;
}

interface Rule {
  code: string;
  title: string;
  emoji: string;
  scopes: readonly BadgeScope[];
  test: (c: BadgeContext, out: readonly Txn[], perDay: Map<number, number>) => string | null;
}

const TAKEOUT = /外卖|美团|饿了么/;

function days(c: BadgeContext): number[] {
  const list: number[] = [];
  for (let d = c.start; d < c.end && d <= startOfDay(c.now); d = addDays(d, 1)) list.push(d);
  return list;
}

/** Ordered by "bragging priority": the first matching rule is the headline badge. */
export const BADGE_RULES: readonly Rule[] = [
  {
    code: "first-txn", title: "首单达成", emoji: "🎉", scopes: ["day", "week"],
    test: (c, out) => {
      if (out.length === 0) return null;
      const first = Math.min(...c.all.map((t) => t.occurredAt));
      return first >= c.start && first < c.end ? "你的第一笔小票诞生了，账本正式开张" : null;
    },
  },
  {
    code: "sleeping-wallet", title: "钱包休眠者", emoji: "😴", scopes: ["day", "week"],
    test: (c, out, perDay) => {
      const zero = days(c).filter((d) => (perDay.get(d) ?? 0) === 0);
      if (c.scope === "day") return zero.length > 0 && c.all.some((t) => t.occurredAt < c.start) ? "今天钱包在睡觉，一分钱没动" : null;
      return zero.length > 0 && c.all.some((t) => t.occurredAt < c.start + 7 * DAY_MS) ? `有 ${zero.length} 天钱包完全休眠` : null;
    },
  },
  {
    code: "milk-tea-champion", title: "奶茶战神", emoji: "🧋", scopes: ["day", "week"],
    test: (c, out) => {
      const n = out.filter((t) => t.category === "DRINK").length;
      const need = c.scope === "day" ? 2 : 4;
      return n >= need ? `${c.scope === "day" ? "今天" : "这周"}喝了 ${n} 杯奶茶咖啡` : null;
    },
  },
  {
    code: "takeout-free", title: "外卖绝缘体", emoji: "🥡", scopes: ["week"],
    test: (c, out, perDay) => {
      const active = [...perDay.values()].filter((v) => v > 0).length;
      return active >= 3 && !out.some((t) => TAKEOUT.test(t.merchant)) ? "这周一单外卖都没点" : null;
    },
  },
  {
    code: "breakfast-guard", title: "早餐守护者", emoji: "🥐", scopes: ["day", "week"],
    test: (c, out) => {
      const ds = new Set(out.filter((t) => t.category === "MEAL" && hourOf(t.occurredAt) >= 5 && hourOf(t.occurredAt) < 10).map((t) => startOfDay(t.occurredAt)));
      const need = c.scope === "day" ? 1 : 3;
      return ds.size >= need ? (c.scope === "day" ? "早餐按时打卡" : `${ds.size} 天都吃了早餐`) : null;
    },
  },
  {
    code: "late-night", title: "深夜觅食者", emoji: "🌙", scopes: ["day", "week"],
    test: (c, out) => {
      const n = out.filter((t) => (t.category === "MEAL" || t.category === "SNACK") && (hourOf(t.occurredAt) >= 22 || hourOf(t.occurredAt) < 4)).length;
      return n >= (c.scope === "day" ? 1 : 2) ? `深夜下单 ${n} 次` : null;
    },
  },
  {
    code: "metro-commuter", title: "地铁通勤侠", emoji: "🚇", scopes: ["day", "week"],
    test: (c, out) => {
      const n = out.filter((t) => t.category === "TRANSPORT" && /地铁|公交/.test(t.merchant)).length;
      return n >= (c.scope === "day" ? 2 : 5) ? `刷了 ${n} 次地铁/公交` : null;
    },
  },
  {
    code: "sub-sweeper", title: "订阅清道夫", emoji: "🧹", scopes: ["week"],
    test: (c, out) => {
      const before = c.all.filter((t) => t.category === "SUBSCRIPTION" && t.direction === "OUT" && t.occurredAt < c.start && t.occurredAt >= c.start - 45 * DAY_MS);
      const during = out.filter((t) => t.category === "SUBSCRIPTION");
      const complete = startOfDay(c.now) >= c.end - DAY_MS;
      return complete && before.length > 0 && during.length === 0 && out.length > 0 ? "上个月还在扣的订阅，这周安静了" : null;
    },
  },
  {
    code: "frugal-star", title: "精打细算星", emoji: "⭐", scopes: ["week"],
    test: (c, out) => {
      const total = out.reduce((s, t) => s + t.amountCents, 0);
      // for a week still in progress, compare against the same number of elapsed days last week
      const elapsed = Math.min(7, Math.floor((startOfDay(c.now) - c.start) / DAY_MS) + 1);
      const prevStart = c.start - 7 * DAY_MS;
      const prev = c.all.filter((t) => t.direction === "OUT" && t.occurredAt >= prevStart && t.occurredAt < prevStart + elapsed * DAY_MS).reduce((s, t) => s + t.amountCents, 0);
      return prev > 0 && total > 0 && total <= prev * 0.85 ? `比上周少花 ¥${yuan(prev - total)}` : null;
    },
  },
  {
    code: "social-ambassador", title: "人情大使", emoji: "🧧", scopes: ["day", "week"],
    test: (c, out) => {
      const soc = out.filter((t) => t.category === "SOCIAL");
      const sum = soc.reduce((s, t) => s + t.amountCents, 0);
      return soc.length >= (c.scope === "day" ? 1 : 2) || sum >= 20000 ? `人情往来 ¥${yuan(sum)}，情义无价` : null;
    },
  },
  {
    code: "weekend-wild", title: "周末放飞者", emoji: "🎈", scopes: ["week"],
    test: (c, out) => {
      const total = out.reduce((s, t) => s + t.amountCents, 0);
      const wk = out.filter((t) => isWeekend(t.occurredAt)).reduce((s, t) => s + t.amountCents, 0);
      return total > 0 && wk >= 10000 && wk / total >= 0.5 ? `周末花掉了本周 ${Math.round((wk / total) * 100)}%` : null;
    },
  },
  {
    code: "steady-eddie", title: "稳如老狗", emoji: "🐕", scopes: ["week"],
    test: (c, out, perDay) => {
      const vals = [...perDay.values()].filter((v) => v > 0);
      if (vals.length < 5) return null;
      const mean = vals.reduce((a, b) => a + b, 0) / vals.length;
      const sd = Math.sqrt(vals.reduce((a, b) => a + (b - mean) ** 2, 0) / vals.length);
      return sd / mean < 0.45 ? "每天花得都差不多，稳" : null;
    },
  },
  {
    code: "tiny-slayer", title: "小额刺客克星", emoji: "🛡️", scopes: ["week"],
    test: (c, out) => {
      const small = out.filter((t) => t.amountCents < 2000).length;
      return out.length >= 5 && small <= 1 ? "这周几乎没被小额支出偷袭" : null;
    },
  },
  {
    code: "learner", title: "学习投资人", emoji: "📚", scopes: ["day", "week"],
    test: (c, out) => {
      const sum = out.filter((t) => t.category === "LEARNING").reduce((s, t) => s + t.amountCents, 0);
      return sum > 0 ? `给自己投资了 ¥${yuan(sum)}` : null;
    },
  },
];

/** Every title that applies in the window (deterministic, with reason). */
export function computeBadges(ctx: BadgeContext): Badge[] {
  const out = ctx.all.filter((t) => t.direction === "OUT" && t.occurredAt >= ctx.start && t.occurredAt < ctx.end);
  const perDay = new Map<number, number>();
  for (const d of days(ctx)) perDay.set(d, 0);
  for (const t of out) perDay.set(startOfDay(t.occurredAt), (perDay.get(startOfDay(t.occurredAt)) ?? 0) + t.amountCents);
  const badges: Badge[] = [];
  for (const rule of BADGE_RULES) {
    if (!rule.scopes.includes(ctx.scope)) continue;
    const reason = rule.test(ctx, out, perDay);
    if (reason) badges.push({ code: rule.code, title: rule.title, emoji: rule.emoji, reason });
  }
  return badges;
}

export function dailyBadge(all: readonly Txn[], dayStart: number, now: number): Badge | null {
  return computeBadges({ all, start: dayStart, end: addDays(dayStart, 1), now, scope: "day" })[0] ?? null;
}
