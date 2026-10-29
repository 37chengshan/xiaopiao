import { CATEGORIES, type Category, type Source, type Txn } from "../domain/types";
import { categoryFromBill } from "./categorize";
import { hashString } from "./time";

/**
 * Alipay / WeChat bill CSV import.
 * Column layout and preamble handling follow the conventions of deb-sig/double-entry-generator (Apache-2.0):
 * https://github.com/deb-sig/double-entry-generator
 */

export type BillProvider = "alipay" | "wechat";

export interface CsvParseResult {
  /** bill vendor the file matched; `null` for a file this app exported, and for files matching no header */
  provider: BillProvider | null;
  txns: Txn[];
  /** rows that were unparsable or deliberately skipped (closed / 不计收支) */
  failed: number;
  failures: string[];
}

/**
 * The app's own export layout (`components/desktop/csvExport.ts` shares this constant). The importer
 * accepts it back, so "export, edit in a spreadsheet, import" keeps working.
 */
export const APP_CSV_HEADER = ["日期", "时间", "商家", "分类", "方向", "金额(元)", "备注", "来源"] as const;

/** Columns required to recognise an app export; the rest are optional when a sheet drops a column. */
const APP_CSV_REQUIRED = ["日期", "时间", "商家", "方向", "金额(元)"] as const;

/** zh labels of the `Source` codes on the wire — one map for the exporter and the importer. */
const SOURCE_ZH: Record<Source, string> = { VOICE: "语音", QUICK_ADD: "一句话", MANUAL: "手动", CSV: "导入", DEMO: "初始" };

export function csvSourceLabel(source: Source): string {
  return SOURCE_ZH[source];
}

const SOURCE_BY_LABEL = new Map<string, Source>(Object.entries(SOURCE_ZH).map(([code, label]) => [label, code as Source]));
const CATEGORY_BY_LABEL = new Map<string, Category>(Object.entries(CATEGORIES).map(([code, meta]) => [meta.label, code as Category]));

/** Full-width parentheses survive a spreadsheet round trip; the header compares on one shape. */
const normCell = (cell: string) => cell.trim().replace("（", "(").replace("）", ")");

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

/** Which layout the header row is: a vendor bill, or this app's own export. */
type CsvFormat = BillProvider | "app";

function findHeader(rows: string[][]): { index: number; format: CsvFormat } | null {
  for (let i = 0; i < rows.length; i++) {
    const cells = rows[i].map(normCell);
    if (cells.includes("交易时间") && cells.includes("交易对方")) {
      if (!cells.includes("收/支") || !cells.some((c) => c === "金额" || c === "金额(元)")) continue;
      return { index: i, format: cells.includes("交易分类") ? "alipay" : "wechat" };
    }
    if (APP_CSV_REQUIRED.every((name) => cells.includes(name))) return { index: i, format: "app" };
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

interface FailureLog {
  failed: number;
  failures: string[];
  fail: (line: number, why: string) => void;
}

/** Counts every skipped row, but keeps only the first five reasons for the import preview. */
function failureLog(): FailureLog {
  const log: FailureLog = {
    failed: 0,
    failures: [],
    fail(line, why) {
      log.failed++;
      if (log.failures.length < 5) log.failures.push(`第 ${line} 行：${why}`);
    },
  };
  return log;
}

export function parseBillCsv(content: string): CsvParseResult {
  const rows = tokenizeCsv(content);
  const header = findHeader(rows);
  if (!header) {
    return {
      provider: null,
      txns: [],
      failed: 0,
      failures: ["没找到账单表头（需要支付宝 / 微信的账单，或从小票导出的 CSV）"],
    };
  }
  if (header.format === "app") return parseAppExport(rows, header.index);
  return parseBillRows(rows, header.index, header.format);
}

function parseBillRows(rows: string[][], headerIndex: number, provider: BillProvider): CsvParseResult {
  const cols = rows[headerIndex].map(normCell);
  const col = (name: string) => cols.indexOf(name);
  const iTime = col("交易时间");
  const iCounter = col("交易对方");
  const iDir = col("收/支");
  const iAmt = cols.findIndex((c) => c === "金额" || c === "金额(元)");
  const iCat = provider === "alipay" ? col("交易分类") : col("交易类型");
  const iItem = provider === "alipay" ? col("商品说明") : col("商品");
  const iStatus = provider === "alipay" ? col("交易状态") : col("当前状态");
  const iOrder = provider === "alipay" ? col("交易订单号") : col("交易单号");
  const iNote = col("备注");

  const txns: Txn[] = [];
  const log = failureLog();

  for (let r = headerIndex + 1; r < rows.length; r++) {
    const cells = rows[r].map((c) => c.trim());
    const line = r + 1;
    if (cells.length < Math.max(iTime, iCounter, iDir, iAmt) + 1) {
      log.fail(line, "列数不够");
      continue;
    }
    const dir = cells[iDir];
    if (dir !== "支出" && dir !== "收入") {
      log.fail(line, `不是收入或支出，已跳过（${dir || "空"}）`);
      continue;
    }
    const status = iStatus >= 0 ? cells[iStatus] : "";
    if (/关闭|失败|已全额退款|已退款/.test(status)) {
      log.fail(line, `交易状态「${status}」`);
      continue;
    }
    const ts = parseTime(cells[iTime]);
    const cents = parseAmountCents(cells[iAmt]);
    if (ts === null || cents === null) {
      log.fail(line, "时间或金额无法识别");
      continue;
    }
    const merchant = cells[iCounter] || "未知商家";
    const item = iItem >= 0 ? cells[iItem] : "";
    const order = iOrder >= 0 ? cells[iOrder] : "";
    const dedupeKey = order ? `${provider}:${order}` : `${provider}:${ts}:${cents}:${hashString(merchant + item)}`;
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
  return { provider, txns, failed: log.failed, failures: log.failures };
}

/** A file this app wrote: every field already has its final value, so the row maps one to one. */
function parseAppExport(rows: string[][], headerIndex: number): CsvParseResult {
  const cols = rows[headerIndex].map(normCell);
  const col = (name: string) => cols.indexOf(name);
  const iDate = col("日期");
  const iTime = col("时间");
  const iMerchant = col("商家");
  const iCat = col("分类");
  const iDir = col("方向");
  const iAmt = col("金额(元)");
  const iNote = col("备注");
  const iSource = col("来源");

  const txns: Txn[] = [];
  const log = failureLog();

  for (let r = headerIndex + 1; r < rows.length; r++) {
    const cells = rows[r].map((c) => c.trim());
    const line = r + 1;
    if (cells.length <= Math.max(iDate, iMerchant, iDir, iAmt)) {
      log.fail(line, "列数不够");
      continue;
    }
    const dir = cells[iDir];
    if (dir !== "支出" && dir !== "收入") {
      log.fail(line, `不是收入或支出，已跳过（${dir || "空"}）`);
      continue;
    }
    const ts = parseTime(`${cells[iDate]} ${cells[iTime] ?? ""}`);
    const cents = parseAmountCents(cells[iAmt]);
    if (ts === null || cents === null) {
      log.fail(line, "时间或金额无法识别");
      continue;
    }
    const merchant = cells[iMerchant] || "未知商家";
    const note = iNote >= 0 ? cells[iNote] : "";
    const dedupeKey = `app:${ts}:${cents}:${dir}:${hashString(merchant + note)}`;
    txns.push({
      id: `app-${hashString(dedupeKey).toString(36)}-${r}`,
      amountCents: cents,
      direction: dir === "支出" ? "OUT" : "IN",
      merchant,
      category: CATEGORY_BY_LABEL.get(iCat >= 0 ? cells[iCat] : "") ?? categoryFromBill(iCat >= 0 ? cells[iCat] : "", `${merchant} ${note}`),
      occurredAt: ts,
      source: (iSource >= 0 ? SOURCE_BY_LABEL.get(cells[iSource]) : undefined) ?? "CSV",
      note: note || undefined,
      dedupeKey,
    });
  }
  return { provider: null, txns, failed: log.failed, failures: log.failures };
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
