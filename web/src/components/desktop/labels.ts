import { getLocale } from "@/lib/strings";
import { CATEGORIES, type Category, type Direction, type Source } from "@/domain/types";

/**
 * Category / source / direction names are interface copy, not data: the stored value stays a code
 * (`MEAL`), only the label follows the reading language.
 */
const CATEGORY_EN: Record<Category, string> = {
  MEAL: "Meals",
  DRINK: "Coffee & tea",
  SNACK: "Snacks",
  GROCERY: "Groceries",
  TRANSPORT: "Transport",
  SHOPPING: "Shopping",
  FUN: "Fun",
  SUBSCRIPTION: "Subscriptions",
  HOUSING: "Home & bills",
  HEALTH: "Health",
  SOCIAL: "Gifts",
  LEARNING: "Learning",
  OTHER: "Other",
};

const SOURCE_EN: Record<Source, string> = {
  VOICE: "Voice",
  QUICK_ADD: "One line",
  MANUAL: "Manual",
  CSV: "Import",
  DEMO: "Opening balance",
};

export function categoryLabel(category: Category): string {
  return getLocale() === "en" ? CATEGORY_EN[category] : CATEGORIES[category].label;
}

export function sourceLabel(source: Source): string {
  return getLocale() === "en" ? SOURCE_EN[source] : SOURCE_LABEL_ZH[source];
}

export function directionLabel(direction: Direction): string {
  if (direction === "OUT") return getLocale() === "en" ? "Spend" : "支出";
  return getLocale() === "en" ? "Income" : "收入";
}

export const SOURCE_LABEL_ZH: Record<Source, string> = {
  VOICE: "语音",
  QUICK_ADD: "一句话",
  MANUAL: "手动",
  CSV: "导入",
  DEMO: "初始",
};
