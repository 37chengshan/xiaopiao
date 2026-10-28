import {
  BookOpen,
  CupSoda,
  Cookie,
  Gamepad2,
  Gift,
  House,
  Pill,
  Receipt,
  Repeat,
  ShoppingBag,
  ShoppingBasket,
  TrainFront,
  Utensils,
  type LucideIcon,
} from "lucide-react";
import type { Category } from "../../domain/types";
import type { Tone } from "./kit";

/** Line icon per category (docs/DESIGN.md §1.3: emoji stay on receipt paper and badges). */
export const CATEGORY_ICONS: Record<Category, LucideIcon> = {
  MEAL: Utensils,
  DRINK: CupSoda,
  SNACK: Cookie,
  GROCERY: ShoppingBasket,
  TRANSPORT: TrainFront,
  SHOPPING: ShoppingBag,
  FUN: Gamepad2,
  SUBSCRIPTION: Repeat,
  HOUSING: House,
  HEALTH: Pill,
  SOCIAL: Gift,
  LEARNING: BookOpen,
  OTHER: Receipt,
};

/** Semantic soft tint per category for icon tiles (round 2: colour carries meaning, clay stays scarce). */
export const CATEGORY_TONES: Record<Category, Tone> = {
  MEAL: "amber",
  DRINK: "violet",
  SNACK: "amber",
  GROCERY: "green",
  TRANSPORT: "blue",
  SHOPPING: "violet",
  FUN: "violet",
  SUBSCRIPTION: "blue",
  HOUSING: "blue",
  HEALTH: "green",
  SOCIAL: "amber",
  LEARNING: "blue",
  OTHER: "neutral",
};
