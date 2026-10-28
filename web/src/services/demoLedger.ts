import { type Txn } from "../domain/types";
import { buildDemoTxns } from "./demoData";
import { type LedgerMode, type LedgerService } from "./LedgerService";

/** In-memory adapter. Lives only as long as the page; "demo" mode starts with fictional data, "empty" with none. */
export function createMemoryLedger(mode: LedgerMode, now: number = Date.now()): LedgerService {
  let rows: Txn[] = mode === "demo" ? buildDemoTxns(now) : [];
  const keyOf = (t: Txn) => t.dedupeKey ?? t.id;
  return {
    mode,
    async list() {
      return [...rows].sort((a, b) => a.occurredAt - b.occurredAt);
    },
    async add(txns) {
      const seen = new Set(rows.map(keyOf));
      let added = 0;
      let duplicates = 0;
      const next = [...rows];
      for (const t of txns) {
        if (seen.has(keyOf(t))) {
          duplicates++;
          continue;
        }
        seen.add(keyOf(t));
        next.push(t);
        added++;
      }
      rows = next;
      return { added, duplicates };
    },
    async update(txn) {
      rows = rows.map((r) => (r.id === txn.id ? txn : r));
    },
    async remove(id) {
      rows = rows.filter((r) => r.id !== id);
    },
    async reset() {
      rows = [];
    },
  };
}
