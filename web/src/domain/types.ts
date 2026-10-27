/** Shared domain contracts for the web app. Mirrors the Android `domain/Models.kt`. Additive changes only. */

/** Money is always integer cents (分). */
export type Cents = number;

export type Direction = "OUT" | "IN";
export type Source = "VOICE" | "QUICK_ADD" | "MANUAL" | "CSV" | "DEMO";

export const CATEGORIES = {
  MEAL: { label: "吃饭", emoji: "🍚" },
  DRINK: { label: "奶茶咖啡", emoji: "🧋" },
  SNACK: { label: "零食", emoji: "🍪" },
  GROCERY: { label: "买菜日用", emoji: "🛒" },
  TRANSPORT: { label: "出行", emoji: "🚇" },
  SHOPPING: { label: "购物", emoji: "🛍️" },
  FUN: { label: "娱乐", emoji: "🎮" },
  SUBSCRIPTION: { label: "会员订阅", emoji: "🔁" },
  HOUSING: { label: "住房水电", emoji: "🏠" },
  HEALTH: { label: "医疗健康", emoji: "💊" },
  SOCIAL: { label: "人情红包", emoji: "🧧" },
  LEARNING: { label: "学习", emoji: "📚" },
  OTHER: { label: "其他", emoji: "🧾" },
} as const;
export type Category = keyof typeof CATEGORIES;

export interface Txn {
  id: string;
  amountCents: Cents;
  direction: Direction;
  merchant: string;
  category: Category;
  /** epoch millis */
  occurredAt: number;
  source: Source;
  note?: string;
  dedupeKey?: string;
}

export interface Badge {
  code: string;
  title: string;
  emoji: string;
  reason: string;
}

export interface Subscription {
  merchant: string;
  amountCents: Cents;
  cycleDays: number;
  lastChargedAt: number;
  nextChargeAt: number;
  occurrences: number;
}

export interface DailyReceipt {
  dayStart: number;
  items: Txn[];
  totalOutCents: Cents;
  aiComment: string;
  badge: Badge | null;
}

export const DAY_MS = 24 * 60 * 60 * 1000;

export function yuan(cents: Cents): string {
  const abs = Math.abs(cents);
  const text = abs % 100 === 0 ? `${abs / 100}` : (abs / 100).toFixed(2);
  return cents < 0 ? `-${text}` : text;
}

export function startOfDay(epochMillis: number): number {
  const d = new Date(epochMillis);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}
