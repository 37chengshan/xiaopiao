import { type Txn } from "../domain/types";

export type LedgerMode = "demo" | "empty";

export interface AddResult {
  added: number;
  duplicates: number;
}

/** Persistence boundary: QMuse 云服务 (`cloudLedger.ts`) plus the in-memory starter adapter (`demoLedger.ts`). */
export interface LedgerService {
  readonly mode: LedgerMode;
  list(): Promise<Txn[]>;
  /** Adds transactions, skipping any whose dedupeKey (or id) already exists. */
  add(txns: readonly Txn[]): Promise<AddResult>;
  update(txn: Txn): Promise<void>;
  remove(id: string): Promise<void>;
  /** Wipes everything held by this service. */
  reset(): Promise<void>;
}
