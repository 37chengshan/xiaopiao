import { type Txn } from "../domain/types";
import { categoryFromBill } from "./categorize";
import { hashString } from "./time";

/**
 * Alipay / WeChat bill CSV import.
 * Column layout and preamble handling follow the conventions of deb-sig/double-entry-generator (Apache-2.0):
 * https://github.com/deb-sig/double-entry-generator
 */

export type BillProvider = "alipay" | "wechat";

export interface CsvParseResult {
  provider: BillProvider | null;
  txns: Txn[];
  /** rows that were unparsable or deliberately skipped (closed / 不计收支) */
  failed: number;
  failures: string[];
}

/** RFC4180-ish tokenizer: quoted fields, escaped quotes, CRLF. */
export function tokenizeCsv(input: string): string[][] {
  const text = input.replace(/^﻿/, "");
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else quoted = false;
      } else field += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && text[i + 1] === "\n") i++;
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else field += ch;
  }
  if (field !== "" || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows.filter((r) => r.some((c) => c.trim() !== ""));
}

function findHeader(rows: string[][]): { index: number; provider: BillProvider } | null {
  for (let i = 0; i < rows.length; i++) {
    const cells = rows[i].map((c) => c.trim());
    if (!cells.includes("交易时间") || !cells.includes("交易对方")) continue;
    if (cells.includes("收/支") && (cells.includes("金额") || cells.includes("金额(元)") || cells.includes("金额（元）"))) {
      return { index: i, provider: cells.includes("交易分类") ? "alipay" : "wechat" };
    }
  }
  return null;
}

function parseTime(raw: string): number | null {
  const m = raw.trim().match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/);
  if (!m) return null;
  const [, y, mo, d, h, mi, s] = m;
  const ts = new Date(Number(y), Number(mo) - 1, Number(d), Number(h ?? 0), Number(mi ?? 0), Number(s ?? 0)).getTime();
  return Number.isNaN(ts) ? null : ts;
}

function parseAmountCents(raw: string): number | null {
  const n = Number(raw.replace(/[¥￥,\s]/g, ""));
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.round(n * 100);
}

export function parseBillCsv(content: string): CsvParseResult {
  const rows = tokenizeCsv(content);
  const header = findHeader(rows);
  if (!header) return { provider: null, txns: [], failed: 0, failures: ["没找到账单表头（需要支付宝或微信的账单导出）"] };
  const cols = rows[header.index].map((c) => c.trim().replace("（", "(").replace("）", ")"));
  const col = (name: string) => cols.indexOf(name);
  const iTime = col("交易时间");
  const iCounter = col("交易对方");
  const iDir = col("收/支");
  const iAmt = cols.findIndex((c) => c === "金额" || c === "金额(元)");
  const iCat = header.provider === "alipay" ? col("交易分类") : col("交易类型");
  const iItem = header.provider === "alipay" ? col("商品说明") : col("商品");
  const iStatus = header.provider === "alipay" ? col("交易状态") : col("当前状态");
  const iOrder = header.provider === "alipay" ? col("交易订单号") : col("交易单号");
  const iNote = col("备注");

  const txns: Txn[] = [];
  const failures: string[] = [];
  let failed = 0;
  const fail = (line: number, why: string) => {
    failed++;
    if (failures.length < 5) failures.push(`第 ${line} 行：${why}`);
  };

  for (let r = header.index + 1; r < rows.length; r++) {
    const cells = rows[r].map((c) => c.trim());
    const line = r + 1;
    if (cells.length < Math.max(iTime, iCounter, iDir, iAmt) + 1) {
      fail(line, "列数不够");
      continue;
    }
    const dir = cells[iDir];
    if (dir !== "支出" && dir !== "收入") {
      fail(line, `不计收支（${dir || "空"}）`);
      continue;
    }
    const status = iStatus >= 0 ? cells[iStatus] : "";
    if (/关闭|失败|已全额退款|已退款/.test(status)) {
      fail(line, `交易状态「${status}」`);
      continue;
    }
    const ts = parseTime(cells[iTime]);
    const cents = parseAmountCents(cells[iAmt]);
    if (ts === null || cents === null) {
      fail(line, "时间或金额无法识别");
      continue;
    }
    const merchant = cells[iCounter] || "未知商家";
    const item = iItem >= 0 ? cells[iItem] : "";
    const order = iOrder >= 0 ? cells[iOrder] : "";
    const dedupeKey = order ? `${header.provider}:${order}` : `${header.provider}:${ts}:${cents}:${hashString(merchant + item)}`;
    txns.push({
      id: `csv-${hashString(dedupeKey).toString(36)}-${r}`,
      amountCents: cents,
      direction: dir === "支出" ? "OUT" : "IN",
      merchant,
      category: categoryFromBill(iCat >= 0 ? cells[iCat] : "", `${merchant} ${item}`),
      occurredAt: ts,
      source: "CSV",
      note: [item, iNote >= 0 ? cells[iNote] : ""].filter((s) => s && s !== "/").join(" · ") || undefined,
      dedupeKey,
    });
  }
  return { provider: header.provider, txns, failed, failures };
}

/** Decode raw bytes from a bill export: Alipay exports GBK, WeChat exports UTF-8 (BOM). */
export function decodeBillBytes(bytes: ArrayBuffer): string {
  const utf8 = new TextDecoder("utf-8").decode(bytes);
  if (!utf8.includes("�")) return utf8;
  try {
    return new TextDecoder("gbk").decode(bytes);
  } catch {
    return utf8;
  }
}
