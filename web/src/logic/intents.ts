import { type Category } from "../domain/types";
import { parseQuickAdd, type DraftTxn } from "./quickAdd";
import { type RegretTarget, type WindowMonths } from "./regret";

export type Intent =
  | { kind: "TODAY_SPEND" }
  | { kind: "MONTH_SPEND" }
  | { kind: "RECEIPT" }
  | { kind: "WEEKLY" }
  | { kind: "SUBSCRIPTIONS" }
  | { kind: "REGRET"; target: RegretTarget | null; reductionPct: number; months: WindowMonths }
  | { kind: "ADD"; drafts: DraftTxn[] }
  | { kind: "IMPORT" }
  | { kind: "UNKNOWN" };

/** What the user wants to cut back on. Both languages share the list, so a mixed sentence still resolves. */
const TARGET_WORDS: ReadonlyArray<readonly [RegExp, RegretTarget]> = [
  [/奶茶|咖啡|饮料|喝|\b(coffee|latte|tea|drinks?)\b/i, { kind: "category", category: "DRINK" satisfies Category }],
  [/外卖/, { kind: "merchant", merchant: "外卖" }],
  [/\b(takeout|take-out|delivery|eating out)\b/i, { kind: "category", category: "MEAL" satisfies Category }],
  [/打车|滴滴/, { kind: "merchant", merchant: "打车" }],
  [/\b(taxi|uber|lyft|didi|rides?)\b/i, { kind: "category", category: "TRANSPORT" satisfies Category }],
  [/零食|\b(snacks?|chips|candy)\b/i, { kind: "category", category: "SNACK" satisfies Category }],
  [/网购|购物|剁手|\b(shopping|clothes)\b/i, { kind: "category", category: "SHOPPING" satisfies Category }],
  [/游戏|电影|娱乐|\b(games?|movies?|entertainment)\b/i, { kind: "category", category: "FUN" satisfies Category }],
  [/会员|订阅|\b(subscriptions?|memberships?|netflix|spotify)\b/i, { kind: "category", category: "SUBSCRIPTION" satisfies Category }],
];

export function parseReduction(text: string): number {
  if (/三分之二|\btwo[-\s]thirds?\b/i.test(text)) return 67;
  if (/三分之一|\ba third\b|\bthird\b/i.test(text)) return 33;
  if (/四分之一|\ba quarter\b|\bquarter\b/i.test(text)) return 25;
  const pct = text.match(/(\d{1,3})\s*%/);
  if (pct) return Math.min(100, Number(pct[1]));
  const cheng = text.match(/([一二三四五六七八九1-9])\s*成/);
  if (cheng) {
    const map: Record<string, number> = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
    return (map[cheng[1]] ?? Number(cheng[1])) * 10;
  }
  if (/一半|半|\bhalf\b/i.test(text)) return 50;
  if (/戒|不再|再也不|不喝|不点|不买|\b(quit|give up|cut out|stop|cancel)\b/i.test(text)) return 100;
  return 50;
}

export function parseMonths(text: string): WindowMonths {
  if (/半年|6\s*个?月|六个?月|\b(six|6)\s*months?\b|\bhalf a year\b/i.test(text)) return 6;
  if (/1\s*个?月|一个月|这个月|\b(one|a)\s+month\b|\bthis month\b/i.test(text)) return 1;
  return 3;
}

/** Decide what the user wants from a free-text chat message. Rule-based; swappable with an LLM later. */
export function detectIntent(text: string, now: number = Date.now()): Intent {
  const t = text.trim();
  if (!t) return { kind: "UNKNOWN" };
  const hasDigit = /\d/.test(t);

  const regretZh = /如果|假如|要是|少(喝|吃|点|买|花|打)|戒|模拟|后悔/.test(t) && /少|戒|不(喝|点|买)|模拟|后悔/.test(t);
  const regretEn = /\bwhat if\b|\bcut(ting)? (back|down|out)\b|\bif i (had )?(cut|skip|drop|quit|stop|give up|spend|bought)\b/i.test(t);
  if (regretZh || regretEn) {
    const target = TARGET_WORDS.find(([re]) => re.test(t))?.[1] ?? null;
    return { kind: "REGRET", target, reductionPct: parseReduction(t), months: parseMonths(t) };
  }
  if (/小票|打印|收据|\breceipts?\b|\bprint\b/i.test(t) && !hasDigit) return { kind: "RECEIPT" };
  if (/周报|这周|本周|上周|颁奖|头衔|\b(this|last) week\b|\bweekly\b/i.test(t) && !hasDigit) return { kind: "WEEKLY" };
  if (/续费|订阅|扣费|扣钱|侦探|\bsubscriptions?\b|\brecurring\b|\bauto-?renew(al|ing|s)?\b|\bmemberships?\b/i.test(t) && (!hasDigit || /哪些|有没有|\b(which|any)\b/i.test(t))) {
    return { kind: "SUBSCRIPTIONS" };
  }
  if (/这个月|本月|这月|\bthis month\b|\bmonth so far\b/i.test(t) && !hasDigit) return { kind: "MONTH_SPEND" };
  if (/(今天|今日|今儿).*(花|多少|消费|支出)|花了多少|花了几|\btoday\b.*\b(spent|spend|spending|cost|paid)\b|\b(spent|paid)\b.*\btoday\b|how much.*\btoday\b/i.test(t) && !hasDigit) {
    return { kind: "TODAY_SPEND" };
  }
  if (/导入|csv|账单文件|支付宝账单|微信账单|\bimport\b|\bstatements?\b|\bbill file\b/i.test(t) && !hasDigit) return { kind: "IMPORT" };
  const drafts = parseQuickAdd(t, now);
  if (drafts.length > 0) return { kind: "ADD", drafts };
  return { kind: "UNKNOWN" };
}
