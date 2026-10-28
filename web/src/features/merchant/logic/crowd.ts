import type { Category, Cents } from "../../../domain/types";
import { mulberry32, pickWeighted, type Rng } from "./rng";

/** All merchant-side data is synthetic (mock) until real aggregates exist; audit before submission (docs/APP-DESIGN.md §9). */

export const WEEKDAY_LABELS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"] as const;
export const SLOT_LABELS = ["早晨", "上午", "午间", "下午", "傍晚", "夜间"] as const;
export const SLOT_HOURS = ["6–9 点", "9–11 点", "11–14 点", "14–17 点", "17–20 点", "20–24 点"] as const;

export const AGE_BANDS = ["18–24", "25–34", "35–44", "45+"] as const;
export type AgeBand = (typeof AGE_BANDS)[number];

export const PRICE_BANDS = [
  { id: "lt10", label: "10 元以下", maxCents: 1000 },
  { id: "10to15", label: "10–15 元", maxCents: 1500 },
  { id: "15to20", label: "15–20 元", maxCents: 2000 },
  { id: "gt20", label: "20 元以上", maxCents: Infinity },
] as const;
export type PriceBandId = (typeof PRICE_BANDS)[number]["id"];

export const TOPPINGS = [
  { id: "taro", label: "芋泥", emoji: "🟣" },
  { id: "pearl", label: "珍珠", emoji: "⚫" },
  { id: "coconut", label: "椰果", emoji: "🥥" },
  { id: "pudding", label: "布丁", emoji: "🍮" },
  { id: "herbal", label: "烧仙草", emoji: "🌿" },
] as const;
export type ToppingId = (typeof TOPPINGS)[number]["id"];

export interface DemoPurchase {
  category: Category;
  /** 0 = Monday … 6 = Sunday */
  weekday: number;
  /** index into SLOT_LABELS */
  slot: number;
  amountCents: Cents;
}

export interface DemoMember {
  id: string;
  ageBand: AgeBand;
  toppingPref: ToppingId;
  purchases: DemoPurchase[];
}

export const DEMO_CROWD_SEED = 20261027;
export const DEMO_CROWD_SIZE = 480;

const CATEGORY_WEIGHTS: ReadonlyArray<readonly [Category, number]> = [
  ["DRINK", 42],
  ["MEAL", 24],
  ["SNACK", 14],
  ["GROCERY", 12],
  ["SHOPPING", 5],
  ["OTHER", 3],
];
const WEEKDAY_WEIGHTS = [1, 1, 1, 1.1, 1.5, 1.9, 1.6] as const;
const SLOT_WEIGHTS = [0.2, 0.7, 1.3, 1.8, 1.4, 0.45] as const;
const AGE_WEIGHTS: ReadonlyArray<readonly [AgeBand, number]> = [
  ["18–24", 34],
  ["25–34", 40],
  ["35–44", 18],
  ["45+", 8],
];
const TOPPING_WEIGHTS: ReadonlyArray<readonly [ToppingId, number]> = [
  ["taro", 31],
  ["pearl", 27],
  ["coconut", 17],
  ["pudding", 19],
  ["herbal", 6],
];
const PRICE_BASE: Record<Category, number> = {
  DRINK: 1300,
  MEAL: 2400,
  SNACK: 900,
  GROCERY: 3200,
  SHOPPING: 8800,
  OTHER: 2000,
  TRANSPORT: 400,
  FUN: 3000,
  SUBSCRIPTION: 2500,
  HOUSING: 20000,
  HEALTH: 5000,
  SOCIAL: 6600,
  LEARNING: 9900,
};

function indexed(weights: readonly number[]): ReadonlyArray<readonly [number, number]> {
  return weights.map((w, i) => [i, w] as const);
}

function purchaseAmount(rng: Rng, category: Category, drinkAnchor: number): Cents {
  const base = category === "DRINK" ? drinkAnchor : PRICE_BASE[category];
  const jitter = 0.75 + rng() * 0.55;
  return Math.max(300, Math.round((base * jitter) / 100) * 100);
}

const NON_DRINK_WEIGHTS = CATEGORY_WEIGHTS.filter(([c]) => c !== "DRINK");

/**
 * Realistic mix for a neighbourhood tea shop's opt-in crowd: roughly 6 in 10 buy drinks at all,
 * and about 4 in 10 of those are regulars (3+ drinks in the window).
 */
const DRINK_BUYER_RATE = 0.62;
const REGULAR_RATE = 0.45;

function makeMember(rng: Rng, index: number): DemoMember {
  const buysDrinks = rng() < DRINK_BUYER_RATE;
  const regular = buysDrinks && rng() < REGULAR_RATE;
  const drinkCount = !buysDrinks ? 0 : regular ? 3 + Math.floor(rng() * 5) : 1 + Math.floor(rng() * 2);
  const otherCount = 3 + Math.floor(rng() * 7);
  const drinkAnchor = 900 + Math.floor(rng() * 1300);
  const purchases: DemoPurchase[] = [];
  const add = (category: Category) =>
    purchases.push({
      category,
      weekday: pickWeighted(rng, indexed(WEEKDAY_WEIGHTS)),
      slot: pickWeighted(rng, indexed(SLOT_WEIGHTS)),
      amountCents: purchaseAmount(rng, category, drinkAnchor),
    });
  for (let i = 0; i < drinkCount; i++) add("DRINK");
  for (let i = 0; i < otherCount; i++) add(pickWeighted(rng, NON_DRINK_WEIGHTS));
  return {
    id: `m${String(index + 1).padStart(3, "0")}`,
    ageBand: pickWeighted(rng, AGE_WEIGHTS),
    toppingPref: pickWeighted(rng, TOPPING_WEIGHTS),
    purchases,
  };
}

/** Deterministic synthetic opt-in crowd. Never real users. */
export function generateDemoCrowd(
  seed: number = DEMO_CROWD_SEED,
  size: number = DEMO_CROWD_SIZE,
): DemoMember[] {
  const rng = mulberry32(seed);
  return Array.from({ length: size }, (_, i) => makeMember(rng, i));
}

export function priceBandOf(amountCents: Cents): PriceBandId {
  return PRICE_BANDS.find((b) => amountCents < b.maxCents)?.id ?? "gt20";
}
