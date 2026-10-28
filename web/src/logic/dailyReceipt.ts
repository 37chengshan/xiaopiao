import { type DailyReceipt, type Txn, startOfDay } from "../domain/types";
import { dailyBadge } from "./badges";
import { dailyComment } from "./comments";
import { addDays, dateKey } from "./time";

export function buildDailyReceipt(all: readonly Txn[], dayStart: number, now: number = Date.now()): DailyReceipt {
  const start = startOfDay(dayStart);
  const end = addDays(start, 1);
  const items = all.filter((t) => t.occurredAt >= start && t.occurredAt < end).sort((a, b) => a.occurredAt - b.occurredAt);
  const totalOutCents = items.filter((t) => t.direction === "OUT").reduce((s, t) => s + t.amountCents, 0);
  return {
    dayStart: start,
    items,
    totalOutCents,
    aiComment: dailyComment(items, dateKey(start)),
    badge: dailyBadge(all, start, now),
  };
}
