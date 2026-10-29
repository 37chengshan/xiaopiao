import { CATEGORIES, type Txn } from "@/domain/types";
import { dateKey, hhmm } from "@/logic/time";
import { SOURCE_LABEL } from "./tableModel";

/** CSV out (docs/WEB-SPEC.md §A.3): the same columns the import accepts, so an export re-imports cleanly. */
const HEADER = ["日期", "时间", "商家", "分类", "方向", "金额(元)", "备注", "来源"];

function cell(value: string): string {
  return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

export function txnsToCsv(rows: readonly Txn[]): string {
  const lines = [HEADER.join(",")];
  for (const t of [...rows].sort((a, b) => a.occurredAt - b.occurredAt)) {
    const amount = (Math.abs(t.amountCents) / 100).toFixed(2);
    lines.push(
      [dateKey(t.occurredAt), hhmm(t.occurredAt), t.merchant, CATEGORIES[t.category].label, t.direction === "OUT" ? "支出" : "收入", amount, t.note ?? "", SOURCE_LABEL[t.source]]
        .map(cell)
        .join(","),
    );
  }
  return lines.join("\n");
}

export function downloadBlob(name: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Excel opens a BOM-prefixed UTF-8 CSV without mangling Chinese merchants. */
export function downloadCsv(name: string, rows: readonly Txn[]) {
  downloadBlob(name, new Blob([`\uFEFF${txnsToCsv(rows)}`], { type: "text/csv;charset=utf-8" }));
}
