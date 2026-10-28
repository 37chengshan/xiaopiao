import { type Txn, startOfDay } from "../domain/types";
import { categorize } from "../logic/categorize";
import { addDays, at, seeded } from "../logic/time";

/** ~3 weeks of clearly fictional demo spending, anchored to `now` so it always looks fresh. */
export function buildDemoTxns(now: number): Txn[] {
  const rand = seeded(20261027);
  const int = (lo: number, hi: number) => lo + Math.floor(rand() * (hi - lo + 1));
  const pick = <T,>(xs: readonly T[]): T => xs[Math.floor(rand() * xs.length)];
  const today = startOfDay(now);
  const out: Txn[] = [];
  let n = 0;
  const push = (day: number, h: number, m: number, merchant: string, yuanAmt: number, category?: Txn["category"], note?: string) => {
    const ts = at(day, h, m);
    if (ts > now) return;
    n += 1;
    out.push({
      id: `demo-${n}`,
      amountCents: Math.round(yuanAmt * 100),
      direction: "OUT",
      merchant,
      category: category ?? categorize(merchant),
      occurredAt: ts,
      source: "DEMO",
      note,
    });
  };

  // monthly subscriptions with three months of history
  for (const back of [3, 33, 63]) push(addDays(today, -back), 0, 5, "iCloud", 6, "SUBSCRIPTION");
  for (const back of [8, 38, 68]) push(addDays(today, -back), 9, 0, "爱奇艺会员", 25, "SUBSCRIPTION");
  for (const back of [15, 45, 75]) push(addDays(today, -back), 7, 30, "网易云音乐", 15, "SUBSCRIPTION");

  const teas = ["喜茶", "瑞幸咖啡", "古茗", "蜜雪冰城", "霸王茶姬"];
  const takeouts = ["美团外卖·黄焖鸡", "饿了么·麻辣烫", "美团外卖·轻食沙拉", "美团外卖·螺蛳粉"];
  const zeroDays = new Set([2, 9]);

  for (let back = 20; back >= 0; back--) {
    if (zeroDays.has(back)) continue;
    const day = addDays(today, -back);
    const dow = new Date(day).getDay();
    const weekend = dow === 0 || dow === 6;
    if (!weekend) {
      push(day, 8, int(0, 40), "地铁", int(3, 6), "TRANSPORT");
      if (rand() < 0.5) push(day, 8, int(41, 55), "包子豆浆", int(6, 12), "MEAL");
      push(day, 12, int(0, 30), rand() < 0.65 ? pick(takeouts) : "公司食堂", int(18, 36), "MEAL");
      if (rand() < 0.65) push(day, 15, int(0, 40), pick(teas), int(12, 24), "DRINK");
      if (rand() < 0.4) push(day, 19, int(0, 40), pick(takeouts), int(25, 45), "MEAL");
      push(day, 18, int(0, 40), "地铁", int(3, 6), "TRANSPORT");
      if (rand() < 0.1) push(day, 23, int(0, 40), "夜宵烧烤", int(30, 60), "MEAL");
    } else {
      push(day, 11, int(0, 40), pick(["早午餐", "肯德基", "面馆"]), int(25, 55), "MEAL");
      push(day, 15, int(0, 40), pick(teas), int(14, 26), "DRINK");
      if (rand() < 0.7) push(day, 17, 20, pick(["电影票", "密室逃脱", "桌游吧", "逛街"]), int(45, 120), "FUN");
      push(day, 19, int(0, 40), pick(["火锅", "烤肉", "朋友聚餐AA"]), int(60, 130), "MEAL");
      if (rand() < 0.6) push(day, 21, int(0, 40), "滴滴打车", int(14, 32), "TRANSPORT");
    }
  }
  push(addDays(today, -12), 20, 10, "《设计心理学》", 49, "LEARNING");
  push(addDays(today, -6), 14, 0, "同事结婚红包", 200, "SOCIAL");
  push(addDays(today, -4), 21, 30, "淘宝·收纳盒", 39.9, "SHOPPING");
  push(addDays(today, -10), 18, 40, "盒马超市", 86.5, "GROCERY");
  push(addDays(today, -1), 18, 40, "盒马超市", 57.2, "GROCERY");
  return out.sort((a, b) => a.occurredAt - b.occurredAt);
}
