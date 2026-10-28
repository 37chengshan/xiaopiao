import { type Category, type Cents, type Txn, DAY_MS, startOfDay } from "../domain/types";

export type RegretTarget =
  | { kind: "category"; category: Category }
  | { kind: "merchant"; merchant: string };

export type WindowMonths = 1 | 3 | 6;

export interface RegretInput {
  txns: readonly Txn[];
  now: number;
  target: RegretTarget;
  /** 0–100 */
  reductionPct: number;
  windowMonths: WindowMonths;
}

export interface Equivalent {
  emoji: string;
  label: string;
  /** how many of them, rounded to 1 decimal */
  count: number;
}

export interface RegretResult {
  /** days of real data used for the monthly rate */
  observedDays: number;
  /** spend on the target over the observed window */
  observedCents: Cents;
  /** estimated spend on target per 30 days at the observed pace */
  monthlyCents: Cents;
  projectedCents: Cents;
  savedCents: Cents;
  equivalents: Equivalent[];
  enoughData: boolean;
}

/** Prices are rough, everyday references — purely illustrative. */
const REFERENCES: ReadonlyArray<{ emoji: string; unit: string; cents: Cents }> = [
  { emoji: "🧋", unit: "杯奶茶", cents: 1500 },
  { emoji: "🎬", unit: "张电影票", cents: 4500 },
  { emoji: "🍲", unit: "顿火锅", cents: 12000 },
  { emoji: "📚", unit: "本新书", cents: 5000 },
  { emoji: "🧳", unit: "次周末小旅行", cents: 150000 },
];

export function matchesTarget(t: Txn, target: RegretTarget): boolean {
  if (t.direction !== "OUT") return false;
  return target.kind === "category"
    ? t.category === target.category
    : t.merchant.includes(target.merchant);
}

export function listTargets(txns: readonly Txn[]): RegretTarget[] {
  const byMerchant = new Map<string, number>();
  const byCat = new Map<Category, number>();
  for (const t of txns) {
    if (t.direction !== "OUT") continue;
    byMerchant.set(t.merchant, (byMerchant.get(t.merchant) ?? 0) + t.amountCents);
    byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
  }
  const cats = [...byCat.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5);
  const merchants = [...byMerchant.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3);
  return [
    ...cats.map(([category]): RegretTarget => ({ kind: "category", category })),
    ...merchants.map(([merchant]): RegretTarget => ({ kind: "merchant", merchant })),
  ];
}

function pickEquivalents(savedCents: Cents): Equivalent[] {
  if (savedCents <= 0) return [];
  const picks = REFERENCES.filter((r) => savedCents / r.cents >= 1).slice(-3);
  const chosen = picks.length > 0 ? picks : [REFERENCES[0]];
  return chosen.map((r) => ({
    emoji: r.emoji,
    label: r.unit,
    count: Math.round((savedCents / r.cents) * 10) / 10,
  }));
}

/**
 * Pure arithmetic: take the real spend on a target, project it to the chosen window at the same pace,
 * and show what a reduction would leave. No returns, no investment assumptions.
 */
export function simulateRegret(input: RegretInput): RegretResult {
  const { txns, now, target, reductionPct, windowMonths } = input;
  const outs = txns.filter((t) => t.direction === "OUT" && t.occurredAt <= now);
  if (outs.length === 0) {
    return { observedDays: 0, observedCents: 0, monthlyCents: 0, projectedCents: 0, savedCents: 0, equivalents: [], enoughData: false };
  }
  const first = Math.min(...outs.map((t) => t.occurredAt));
  const observedDays = Math.max(7, Math.round((startOfDay(now) - startOfDay(first)) / DAY_MS) + 1);
  const observedCents = outs.filter((t) => matchesTarget(t, target)).reduce((s, t) => s + t.amountCents, 0);
  const monthlyCents = Math.round((observedCents / observedDays) * 30);
  const projectedCents = monthlyCents * windowMonths;
  const pct = Math.min(100, Math.max(0, reductionPct));
  const savedCents = Math.round((projectedCents * pct) / 100);
  return {
    observedDays,
    observedCents,
    monthlyCents,
    projectedCents,
    savedCents,
    equivalents: pickEquivalents(savedCents),
    enoughData: observedCents > 0,
  };
}
