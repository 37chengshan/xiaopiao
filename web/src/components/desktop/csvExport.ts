import { CATEGORIES, type Txn } from "@/domain/types";
import { APP_CSV_HEADER, csvSourceLabel } from "@/logic/csv";
import { dateKey, hhmm } from "@/logic/time";

function cell(value: string): string {
  return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

export function txnsToCsv(rows: readonly Txn[]): string {
  const lines = [APP_CSV_HEADER.join(",")];
  for (const t of [...rows].sort((a, b) => a.occurredAt - b.occurredAt)) {
    const amount = (Math.abs(t.amountCents) / 100).toFixed(2);
    lines.push(
      [dateKey(t.occurredAt), hhmm(t.occurredAt), t.merchant, CATEGORIES[t.category].label, t.direction === "OUT" ? "支出" : "收入", amount, t.note ?? "", csvSourceLabel(t.source)]
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
