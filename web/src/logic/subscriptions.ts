import { type Cents, type Subscription, type Txn, DAY_MS } from "../domain/types";

const CYCLES: ReadonlyArray<{ days: number; tol: number }> = [
  { days: 7, tol: 1 },
  { days: 30, tol: 4 },
  { days: 91, tol: 8 },
  { days: 365, tol: 20 },
];

function median(xs: number[]): number {
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

function normalizeMerchant(m: string): string {
  return m.replace(/[（(].*?[)）]/g, "").replace(/\s+/g, "").toLowerCase();
}

/** Recurring charges at a stable amount (±10%) and a regular cycle (weekly/monthly/quarterly/yearly). */
export function detectSubscriptions(txns: readonly Txn[], now: number = Date.now()): Subscription[] {
  const groups = new Map<string, Txn[]>();
  for (const t of txns) {
    if (t.direction !== "OUT" || t.occurredAt > now) continue;
    const key = normalizeMerchant(t.merchant);
    groups.set(key, [...(groups.get(key) ?? []), t]);
  }
  const found: Subscription[] = [];
  for (const list of groups.values()) {
    if (list.length < 2) continue;
    const sorted = [...list].sort((a, b) => a.occurredAt - b.occurredAt);
    const med = median(sorted.map((t) => t.amountCents));
    const stable = sorted.filter((t) => Math.abs(t.amountCents - med) <= med * 0.1);
    if (stable.length < 2) continue;
    const gaps = stable.slice(1).map((t, i) => (t.occurredAt - stable[i].occurredAt) / DAY_MS);
    const gap = median(gaps);
    const cycle = CYCLES.find((c) => Math.abs(gap - c.days) <= c.tol);
    if (!cycle) continue;
    if (!gaps.every((g) => Math.abs(g - cycle.days) <= cycle.tol * 1.5)) continue;
    // weekly "subscriptions" need more evidence: daily-habit merchants (e.g. 地铁) would otherwise match.
    if (cycle.days === 7 && stable.length < 3) continue;
    const last = stable[stable.length - 1];
    found.push({
      merchant: last.merchant,
      amountCents: Math.round(med),
      cycleDays: cycle.days,
      lastChargedAt: last.occurredAt,
      nextChargeAt: last.occurredAt + cycle.days * DAY_MS,
      occurrences: stable.length,
    });
  }
  return found.sort((a, b) => annualCents(b) - annualCents(a));
}

export function annualCents(s: Subscription): Cents {
  return Math.round((s.amountCents * 365) / s.cycleDays);
}

export function cycleLabel(days: number): string {
  if (days <= 7) return "每周";
  if (days <= 31) return "每月";
  if (days <= 95) return "每季度";
  return "每年";
}

export type CancelPlatform = "alipay" | "wechat" | "apple";

/** Guide text only — we never cancel anything for the user. */
export const CANCEL_GUIDES: Record<CancelPlatform, { name: string; steps: string[] }> = {
  alipay: {
    name: "支付宝",
    steps: ["打开支付宝，点右下角「我的」", "进入右上角「设置」→「支付设置」", "点「免密支付 / 自动扣款」", "找到这项服务，点进去选「关闭服务」"],
  },
  wechat: {
    name: "微信",
    steps: ["打开微信，点「我」→「服务」", "进入右上角「钱包」→「支付设置」", "点「自动续费」", "找到这项服务，点进去选「关闭扣费服务」"],
  },
  apple: {
    name: "Apple",
    steps: ["打开「设置」，点最上方你的头像", "点「订阅」", "选中要取消的订阅", "点「取消订阅」并确认"],
  },
};
