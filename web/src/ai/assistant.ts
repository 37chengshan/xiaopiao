import { type Txn, startOfDay, yuan } from "../domain/types";
import { dailyComment, greeting, todaySpendLine } from "../logic/comments";
import { type Intent, detectIntent } from "../logic/intents";
import { addDays, dateKey } from "../logic/time";

/** Everything the chat needs from "AI". Template/rule based now; a cloud-function LLM can implement the same shape. */
export interface Assistant {
  opening(txns: readonly Txn[], now: number): { text: string; chips: string[] };
  understand(text: string, now: number): Intent;
  comment(items: readonly Txn[], dayStart: number): string;
}

export const templateAssistant: Assistant = {
  opening(txns, now) {
    const today = startOfDay(now);
    const yesterday = addDays(today, -1);
    const hello = greeting(now);
    const loggedRecently = txns.some((t) => t.occurredAt >= yesterday);
    if (txns.length === 0) {
      return { text: `${hello} 我是小票，你的记账小搭子～ 试试直接跟我说「午饭 32 奶茶 15」，我来记。`, chips: ["午饭 32 奶茶 15", "导入账单", "今天花了多少"] };
    }
    if (!loggedRecently) {
      return { text: `${hello} 昨天没见你～要不要补记一下？`, chips: ["昨天午饭 30", "昨天没花钱", "今天花了多少"] };
    }
    const todays = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today);
    const total = todays.reduce((s, t) => s + t.amountCents, 0);
    return { text: `${hello} ${todaySpendLine(total, todays.length)}`, chips: ["打印小票", "这周周报", "有哪些自动续费", "如果少喝一半奶茶"] };
  },
  understand: (text, now) => detectIntent(text, now),
  comment: (items, dayStart) => dailyComment(items, dateKey(dayStart)),
};

export function spendSummary(txns: readonly Txn[], now: number): { totalCents: number; count: number; text: string } {
  const today = startOfDay(now);
  const todays = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today);
  const totalCents = todays.reduce((s, t) => s + t.amountCents, 0);
  return { totalCents, count: todays.length, text: todays.length ? `今天花了 ¥${yuan(totalCents)}（${todays.length} 笔）` : "今天还没有花钱" };
}
