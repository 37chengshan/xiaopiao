import { CATEGORIES, type Category } from "../../../domain/types";
import {
  PRICE_BANDS,
  SLOT_LABELS,
  TOPPINGS,
  WEEKDAY_LABELS,
  priceBandOf,
  type DemoMember,
  type DemoPurchase,
} from "./crowd";

/** k-anonymity threshold: any group with fewer than K distinct people is suppressed. */
export const K_ANON = 20;

export interface ShareCell {
  key: string;
  label: string;
  emoji?: string;
  /** distinct people in this group; null when suppressed */
  people: number | null;
  /** 0–1 share of the whole; null when suppressed */
  share: number | null;
  suppressed: boolean;
}

export interface HeatCell {
  weekday: number;
  slot: number;
  people: number | null;
  /** 0–1 relative intensity among visible cells; null when suppressed */
  level: number | null;
  suppressed: boolean;
}

export interface PeakSlot {
  weekday: number;
  slot: number;
  label: string;
}

export interface Insights {
  crowdSize: number;
  k: number;
  drinkBuyers: number;
  categoryShare: ShareCell[];
  /** rows = 7 weekdays, columns = 6 time slots (drink purchases) */
  heatmap: HeatCell[][];
  priceBands: ShareCell[];
  toppings: ShareCell[];
  /** share of drink buyers with ≥3 drink purchases in the window; null when too few buyers */
  repeatRate: number | null;
  peak: PeakSlot | null;
  suppressedCells: number;
}

function distinctPeople(
  crowd: readonly DemoMember[],
  match: (p: DemoPurchase) => boolean,
): number {
  return crowd.reduce((n, m) => (m.purchases.some(match) ? n + 1 : n), 0);
}

function cell(
  key: string,
  label: string,
  people: number,
  count: number,
  total: number,
  k: number,
  emoji?: string,
): ShareCell {
  const suppressed = people < k;
  return {
    key,
    label,
    emoji,
    people: suppressed ? null : people,
    share: suppressed || total === 0 ? null : count / total,
    suppressed,
  };
}

function categoryShare(crowd: readonly DemoMember[], k: number): ShareCell[] {
  const all = crowd.flatMap((m) => m.purchases);
  return (Object.keys(CATEGORIES) as Category[])
    .map((c) => ({ c, count: all.filter((p) => p.category === c).length }))
    .filter(({ count }) => count > 0)
    .sort((a, b) => b.count - a.count)
    .map(({ c, count }) =>
      cell(c, CATEGORIES[c].label, distinctPeople(crowd, (p) => p.category === c), count, all.length, k, CATEGORIES[c].emoji),
    );
}

function heatmap(crowd: readonly DemoMember[], k: number): HeatCell[][] {
  const rows = WEEKDAY_LABELS.map((_, weekday) =>
    SLOT_LABELS.map((_, slot) => {
      const people = distinctPeople(
        crowd,
        (p) => p.category === "DRINK" && p.weekday === weekday && p.slot === slot,
      );
      return { weekday, slot, people, suppressed: people < k };
    }),
  );
  const max = Math.max(1, ...rows.flat().filter((c) => !c.suppressed).map((c) => c.people));
  return rows.map((row) =>
    row.map((c) => ({
      weekday: c.weekday,
      slot: c.slot,
      people: c.suppressed ? null : c.people,
      level: c.suppressed ? null : c.people / max,
      suppressed: c.suppressed,
    })),
  );
}

function peakOf(grid: readonly (readonly HeatCell[])[]): PeakSlot | null {
  let best: HeatCell | null = null;
  for (const c of grid.flat()) {
    if (!c.suppressed && (best === null || (c.people ?? 0) > (best.people ?? 0))) best = c;
  }
  return best
    ? {
        weekday: best.weekday,
        slot: best.slot,
        label: `${WEEKDAY_LABELS[best.weekday]}${SLOT_LABELS[best.slot]}`,
      }
    : null;
}

function priceBands(crowd: readonly DemoMember[], k: number): ShareCell[] {
  const drinks = crowd.flatMap((m) => m.purchases).filter((p) => p.category === "DRINK");
  return PRICE_BANDS.map((band) =>
    cell(
      band.id,
      band.label,
      distinctPeople(crowd, (p) => p.category === "DRINK" && priceBandOf(p.amountCents) === band.id),
      drinks.filter((p) => priceBandOf(p.amountCents) === band.id).length,
      drinks.length,
      k,
    ),
  );
}

function toppings(crowd: readonly DemoMember[], k: number): ShareCell[] {
  const drinkers = crowd.filter((m) => m.purchases.some((p) => p.category === "DRINK"));
  return TOPPINGS.map((t) => {
    const n = drinkers.filter((m) => m.toppingPref === t.id).length;
    return cell(t.id, t.label, n, n, drinkers.length, k, t.emoji);
  });
}

function repeatRate(crowd: readonly DemoMember[], k: number): number | null {
  const counts = crowd
    .map((m) => m.purchases.filter((p) => p.category === "DRINK").length)
    .filter((n) => n > 0);
  if (counts.length < k) return null;
  return counts.filter((n) => n >= 3).length / counts.length;
}

/** Aggregate a crowd into merchant-safe insights. Only groups of ≥k people are ever exposed. */
export function aggregate(crowd: readonly DemoMember[], k: number = K_ANON): Insights {
  const grid = heatmap(crowd, k);
  return {
    crowdSize: crowd.length,
    k,
    drinkBuyers: crowd.filter((m) => m.purchases.some((p) => p.category === "DRINK")).length,
    categoryShare: categoryShare(crowd, k),
    heatmap: grid,
    priceBands: priceBands(crowd, k),
    toppings: toppings(crowd, k),
    repeatRate: repeatRate(crowd, k),
    peak: peakOf(grid),
    suppressedCells: grid.flat().filter((c) => c.suppressed).length,
  };
}
