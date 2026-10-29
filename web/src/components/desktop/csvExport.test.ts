import { describe, expect, it } from "vitest";
import type { Txn } from "@/domain/types";
import { parseBillCsv } from "@/logic/csv";
import { txnsToCsv } from "./csvExport";

const D = (y: number, m: number, d: number, h: number, min: number) => new Date(y, m - 1, d, h, min).getTime();

const row = (over: Partial<Txn> & { id: string }): Txn => ({
  amountCents: 3250,
  direction: "OUT",
  merchant: "美团外卖",
  category: "MEAL",
  occurredAt: D(2026, 10, 20, 12, 30),
  source: "MANUAL",
  ...over,
});

const rows: Txn[] = [
  row({ id: "a" }),
  row({ id: "b", amountCents: 2800, merchant: "喜茶(万象城店)", category: "DRINK", source: "QUICK_ADD", note: "少冰, 少糖" }),
  row({ id: "c", amountCents: 100000, direction: "IN", merchant: '工资 "十月"', category: "OTHER", source: "DEMO", note: '含"奖金"\n第二行' }),
  row({ id: "d", amountCents: 300, merchant: "地铁", category: "TRANSPORT", source: "CSV", occurredAt: D(2026, 10, 21, 8, 5) }),
];

describe("csv export re-import (round trip)", () => {
  it("keeps every field of every row", () => {
    const parsed = parseBillCsv(txnsToCsv(rows));
    expect(parsed.failed).toBe(0);
    expect(parsed.failures).toEqual([]);
    expect(parsed.txns).toHaveLength(rows.length);

    const byMerchant = new Map(parsed.txns.map((t) => [t.merchant, t]));
    for (const original of rows) {
      const back = byMerchant.get(original.merchant);
      expect(back, original.merchant).toBeDefined();
      expect(back).toMatchObject({
        amountCents: original.amountCents,
        direction: original.direction,
        category: original.category,
        source: original.source,
        occurredAt: original.occurredAt,
        note: original.note,
      });
    }
  });

  it("reads the BOM Excel needs, and quotes that survived a spreadsheet", () => {
    const parsed = parseBillCsv(`\uFEFF${txnsToCsv(rows)}`);
    expect(parsed.txns).toHaveLength(rows.length);
    expect(parsed.txns.find((t) => t.merchant === '工资 "十月"')?.amountCents).toBe(100000);
  });

  it("gives the same dedupe keys on a second import, so the ledger counts duplicates", () => {
    const once = parseBillCsv(txnsToCsv(rows)).txns.map((t) => t.dedupeKey);
    const twice = parseBillCsv(txnsToCsv(rows)).txns.map((t) => t.dedupeKey);
    expect(twice).toEqual(once);
    expect(new Set(once).size).toBe(rows.length);
  });

  it("still prefers a real bill layout", () => {
    const bill = "交易时间,交易分类,交易对方,商品说明,收/支,金额,交易状态\n2026-10-26 12:30:11,餐饮美食,美团外卖,黄焖鸡,支出,32.50,交易成功\n";
    const parsed = parseBillCsv(bill);
    expect(parsed.provider).toBe("alipay");
    expect(parsed.txns[0]).toMatchObject({ amountCents: 3250, category: "MEAL", source: "CSV" });
  });

  it("flags an export row whose amount or direction was edited by hand", () => {
    const broken = ["日期,时间,商家,分类,方向,金额(元),备注,来源", "2026-10-20,12:30,美团外卖,吃饭,支出,abc,,手动", "2026-10-20,13:00,美团外卖,吃饭,转账,10.00,,手动", "2026-10-20,14:00,瑞幸,奶茶咖啡,支出,15.00,第二杯,一句话"].join("\n");
    const parsed = parseBillCsv(broken);
    expect(parsed.txns).toHaveLength(1);
    expect(parsed.failed).toBe(2);
    expect(parsed.failures).toHaveLength(2);
    expect(parsed.txns[0]).toMatchObject({ merchant: "瑞幸", amountCents: 1500, note: "第二杯", source: "QUICK_ADD" });
  });
});
