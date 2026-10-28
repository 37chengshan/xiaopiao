import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { type Txn } from "../../domain/types";
import { useLedger } from "@/services/LedgerProvider";
import { EntryDrawer } from "./EntryDrawer";

export interface EntryRequest {
  tab?: "manual" | "import";
  /** prefill (draft from chat, or an existing Txn when editing) */
  initial?: Partial<Txn>;
  /** when set, the saved Txn goes here instead of straight to the ledger (used by chat drafts) */
  onSave?: (txn: Txn) => void;
  /** editing an existing ledger row */
  editing?: boolean;
}

interface EntryContextValue {
  open: (req?: EntryRequest) => void;
}

const EntryContext = createContext<EntryContextValue | null>(null);

export function EntryProvider({ children }: { children: ReactNode }) {
  const { mode } = useLedger();
  const [req, setReq] = useState<EntryRequest | null>(null);
  const [openState, setOpenState] = useState(false);
  const open = useCallback((r?: EntryRequest) => {
    setReq(r ?? {});
    setOpenState(true);
  }, []);
  const value = useMemo(() => ({ open }), [open]);
  return (
    <EntryContext.Provider value={value}>
      {children}
      {mode !== null && req && <EntryDrawer key={JSON.stringify(req.initial ?? {}) + (req.tab ?? "")} request={req} open={openState} onOpenChange={setOpenState} />}
    </EntryContext.Provider>
  );
}

export function useEntry(): EntryContextValue {
  const ctx = useContext(EntryContext);
  if (!ctx) throw new Error("useEntry must be used inside EntryProvider");
  return ctx;
}
