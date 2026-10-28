import { type Txn } from "../domain/types";

export type LedgerMode = "demo" | "empty";

export interface AddResult {
  added: number;
  duplicates: number;
}

/** Persistence boundary. Today only an in-memory demo adapter exists; QMuse 云服务 will implement this later. */
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
