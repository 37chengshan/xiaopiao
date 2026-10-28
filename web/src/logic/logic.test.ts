import { describe, expect, it } from "vitest";
import { startOfDay, type Txn } from "../domain/types";
import { buildDemoTxns } from "../services/demoData";
import { createMemoryLedger } from "../services/demoLedger";
import { BADGE_RULES, computeBadges } from "./badges";
import { dailyComment, weeklyComment } from "./comments";
import { parseBillCsv, tokenizeCsv } from "./csv";
import { buildDailyReceipt } from "./dailyReceipt";
import { detectIntent } from "./intents";
import { parseQuickAdd } from "./quickAdd";
import { simulateRegret } from "./regret";
import { annualCents, detectSubscriptions } from "./subscriptions";
import { addDays, at, weekStartOf } from "./time";
import { buildWeeklyReport } from "./weeklyReport";

// Tuesday 2026-10-27 20:00 local
const NOW = new Date(2026, 9, 27, 20, 0).getTime();
const TODAY = startOfDay(NOW);

function txn(p: Partial<Txn> & Pick<Txn, "amountCents" | "merchant" | "occurredAt">): Txn {
  return { id: `t-${Math.random()}`, direction: "OUT", category: "OTHER", source: "MANUAL", ...p };
}

describe("QuickAddParser", () => {
  it("parses the canonical multi-entry sentence", () => {
    const d = parseQuickAdd("午饭 32 奶茶 15 昨天打车 18", NOW);
    expect(d.map((x) => [x.merchant, x.amountCents])).toEqual([
      ["午饭", 3200],
      ["奶茶", 1500],
      ["打车", 1800],
    ]);
    expect(d[1].category).toBe("DRINK");
    expect(d[2].category).toBe("TRANSPORT");
    expect(startOfDay(d[2].occurredAt)).toBe(addDays(TODAY, -1));
    expect(startOfDay(d[0].occurredAt)).toBe(TODAY);
  });
  it("supports amount-first and yuan suffix", () => {
    const d = parseQuickAdd("32块 午饭，15元奶茶", NOW);
    expect(d.map((x) => x.amountCents)).toEqual([3200, 1500]);
  });
  it("handles decimals and income", () => {
    const d = parseQuickAdd("工资 8000.5", NOW);
    expect(d[0].amountCents).toBe(800050);
    expect(d[0].direction).toBe("IN");
  });
  it("returns nothing without numbers", () => {
    expect(parseQuickAdd("你好呀", NOW)).toEqual([]);
  });
  it("never produces future timestamps for today", () => {
    for (const x of parseQuickAdd("晚饭 40 夜宵 20", at(TODAY, 9))) expect(x.occurredAt).toBeLessThanOrEqual(at(TODAY, 9));
  });
});

describe("intents", () => {
  it("routes the chat intents", () => {
    expect(detectIntent("今天花了多少", NOW).kind).toBe("TODAY_SPEND");
    expect(detectIntent("打印小票", NOW).kind).toBe("RECEIPT");
    expect(detectIntent("这周周报", NOW).kind).toBe("WEEKLY");
    expect(detectIntent("有哪些自动续费", NOW).kind).toBe("SUBSCRIPTIONS");
    expect(detectIntent("午饭 32 奶茶 15", NOW).kind).toBe("ADD");
    expect(detectIntent("blah", NOW).kind).toBe("UNKNOWN");
  });
  it("parses the regret question", () => {
    const i = detectIntent("如果少喝一半奶茶", NOW);
    expect(i).toMatchObject({ kind: "REGRET", reductionPct: 50, target: { kind: "category", category: "DRINK" } });
  });
});

describe("CSV import", () => {
  const alipay = `------------------------------------------------------------------------------------
导出信息：
姓名：测试
支付宝支付科技有限公司  电子客户回单
------------------------------------------------------------------------------------
交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,
2026-10-26 12:30:11,餐饮美食,美团外卖,abc***,黄焖鸡,支出,32.50,花呗,交易成功,2026102600001\t,M1,,
2026-10-26 12:30:11,餐饮美食,美团外卖,abc***,黄焖鸡,支出,32.50,花呗,交易成功,2026102600001\t,M1,,
2026-10-25 09:00:00,其他,某某,xx,转账,不计收支,10.00,余额,交易成功,2026102500002,M2,,
2026-10-24 08:00:00,交通出行,地铁,xx,乘车,支出,abc,余额,交易成功,2026102400003,M3,,
`;
  it("detects Alipay, skips preamble, flags failures", () => {
    const r = parseBillCsv(alipay);
    expect(r.provider).toBe("alipay");
    expect(r.txns).toHaveLength(2);
    expect(r.txns[0]).toMatchObject({ amountCents: 3250, merchant: "美团外卖", category: "MEAL", source: "CSV" });
    expect(r.failed).toBe(2);
  });
  it("dedupes via the ledger service", async () => {
    const r = parseBillCsv(alipay);
    const ledger = createMemoryLedger("empty", NOW);
    expect(await ledger.add(r.txns)).toEqual({ added: 1, duplicates: 1 });
    expect(await ledger.add(r.txns)).toEqual({ added: 0, duplicates: 2 });
  });
  const wechat = `﻿微信支付账单明细
微信昵称：[x]
起始时间：[2026-10-01 00:00:00] 终止时间：[2026-10-27 00:00:00]
----------------------微信支付账单明细列表--------------------
交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
2026-10-20 18:00:00,商户消费,"喜茶(万象城店)",奶茶,支出,¥28.00,零钱,支付成功,4200001,9001,"/"
2026-10-21 10:00:00,转账,小王,转账备注,收入,¥100.00,零钱,已收钱,4200002,9002,/
`;
  it("detects WeChat with BOM, quotes and ¥", () => {
    const r = parseBillCsv(wechat);
    expect(r.provider).toBe("wechat");
    expect(r.txns).toHaveLength(2);
    expect(r.txns[0]).toMatchObject({ amountCents: 2800, merchant: "喜茶(万象城店)", category: "DRINK" });
    expect(r.txns[1].direction).toBe("IN");
  });
  it("tokenizes quoted commas and CRLF", () => {
    expect(tokenizeCsv('a,"b,c"\r\n"d""e",f')).toEqual([["a", "b,c"], ['d"e', "f"]]);
  });
  it("reports unknown files", () => {
    expect(parseBillCsv("hello,world").provider).toBeNull();
  });
});

describe("demo data + badges", () => {
  const demo = buildDemoTxns(NOW);
  it("has three weeks, zero-spend days, and no future entries", () => {
    expect(demo.length).toBeGreaterThan(80);
    expect(Math.max(...demo.map((t) => t.occurredAt))).toBeLessThanOrEqual(NOW);
    expect(demo.some((t) => startOfDay(t.occurredAt) === addDays(TODAY, -2))).toBe(false);
  });
  it("is deterministic", () => {
    expect(buildDemoTxns(NOW)).toEqual(demo);
  });
  it("defines at least 14 titles", () => {
    expect(new Set(BADGE_RULES.map((r) => r.title)).size).toBeGreaterThanOrEqual(14);
  });
  it("awards 奶茶战神 with a reason, deterministically", () => {
    const start = weekStartOf(NOW);
    const drinks = Array.from({ length: 4 }, (_, i) => txn({ amountCents: 1500, merchant: "喜茶", category: "DRINK", occurredAt: at(addDays(start, i), 15) }));
    const ctx = { all: drinks, start, end: addDays(start, 7), now: NOW, scope: "week" as const };
    const a = computeBadges(ctx);
    expect(a.find((b) => b.title === "奶茶战神")?.reason).toContain("4");
    expect(computeBadges(ctx)).toEqual(a);
  });
  it("awards 钱包休眠者 on a zero day with history and 首单达成 on the first", () => {
    const r = buildDailyReceipt(demo, addDays(TODAY, -2), NOW);
    expect(r.totalOutCents).toBe(0);
    expect(r.badge?.title).toBe("钱包休眠者");
    const first = [txn({ amountCents: 1000, merchant: "午饭", category: "MEAL", occurredAt: at(TODAY, 12) })];
    expect(buildDailyReceipt(first, TODAY, NOW).badge?.title).toBe("首单达成");
  });
});

describe("comments", () => {
  it("is deterministic per date and differs by spend bucket", () => {
    expect(dailyComment([], "2026-10-27")).toBe(dailyComment([], "2026-10-27"));
    expect(weeklyComment(100, 0, "k")).toContain("第一份周报");
    expect(weeklyComment(50, 100, "k")).not.toBe(weeklyComment(200, 100, "k"));
  });
});

describe("daily receipt + weekly report", () => {
  const demo = buildDemoTxns(NOW);
  it("sums outgoing items for a day", () => {
    const r = buildDailyReceipt(demo, addDays(TODAY, -1), NOW);
    expect(r.totalOutCents).toBe(r.items.filter((t) => t.direction === "OUT").reduce((s, t) => s + t.amountCents, 0));
    expect(r.aiComment.length).toBeGreaterThan(4);
  });
  it("builds a weekly report with a previous-week delta", () => {
    const w = buildWeeklyReport(demo, addDays(weekStartOf(NOW), -7), NOW);
    expect(w.byDay).toHaveLength(7);
    expect(w.byDay.reduce((a, b) => a + b, 0)).toBe(w.totalCents);
    expect(w.deltaPct).not.toBeNull();
  });
});

describe("SubscriptionDetector", () => {
  const demo = buildDemoTxns(NOW);
  it("finds the three monthly subscriptions in demo data", () => {
    const subs = detectSubscriptions(demo, NOW);
    const names = subs.map((s) => s.merchant);
    expect(names).toEqual(expect.arrayContaining(["爱奇艺会员", "网易云音乐", "iCloud"]));
    const iqiyi = subs.find((s) => s.merchant === "爱奇艺会员")!;
    expect(iqiyi.cycleDays).toBe(30);
    expect(annualCents(iqiyi)).toBe(Math.round((2500 * 365) / 30));
  });
  it("tolerates ±10% amounts and rejects irregular gaps", () => {
    const mk = (days: number[], amt: number[]) => days.map((d, i) => txn({ amountCents: amt[i], merchant: "X", occurredAt: at(addDays(TODAY, -d), 9) }));
    expect(detectSubscriptions(mk([60, 30, 0], [2500, 2600, 2400]), NOW)).toHaveLength(1);
    expect(detectSubscriptions(mk([60, 30, 0], [2500, 4000, 2500]), NOW)).toHaveLength(0);
    expect(detectSubscriptions(mk([50, 12, 0], [2500, 2500, 2500]), NOW)).toHaveLength(0);
    expect(detectSubscriptions(mk([365, 0], [29900, 29900]), NOW)[0].cycleDays).toBe(365);
  });
});

describe("RegretSimulator", () => {
  const demo = buildDemoTxns(NOW);
  it("is pure arithmetic and scales with reduction and window", () => {
    const base = { txns: demo, now: NOW, target: { kind: "category", category: "DRINK" } as const };
    const half = simulateRegret({ ...base, reductionPct: 50, windowMonths: 3 });
    const all = simulateRegret({ ...base, reductionPct: 100, windowMonths: 3 });
    expect(half.enoughData).toBe(true);
    expect(all.savedCents).toBe(all.projectedCents);
    expect(Math.abs(half.savedCents * 2 - all.savedCents)).toBeLessThanOrEqual(1);
    expect(simulateRegret({ ...base, reductionPct: 50, windowMonths: 6 }).projectedCents).toBe(half.monthlyCents * 6);
    expect(half.equivalents.length).toBeGreaterThan(0);
  });
  it("reports not enough data", () => {
    expect(simulateRegret({ txns: [], now: NOW, target: { kind: "category", category: "DRINK" }, reductionPct: 50, windowMonths: 3 }).enoughData).toBe(false);
  });
});
