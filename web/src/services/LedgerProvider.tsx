import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { type Txn } from "../domain/types";
import { type AddResult, type LedgerMode, type LedgerService } from "./LedgerService";
import { createMemoryLedger } from "./demoLedger";

const PREF_KEY = "xiaopiao.mode";

function readPref(): LedgerMode | null {
  try {
    const v = localStorage.getItem(PREF_KEY);
    return v === "demo" || v === "empty" ? v : null;
  } catch {
    return null;
  }
}

interface LedgerContextValue {
  mode: LedgerMode | null;
  chooseMode: (mode: LedgerMode) => void;
  /** back to the first-run choice */
  forgetMode: () => void;
  service: LedgerService | null;
  txns: Txn[];
  isLoading: boolean;
  isError: boolean;
  refetch: () => void;
  now: number;
  add: (txns: readonly Txn[]) => Promise<AddResult>;
  update: (txn: Txn) => Promise<void>;
  remove: (id: string) => Promise<void>;
  /** re-insert (undo of delete) */
  restore: (txn: Txn) => Promise<void>;
  reset: () => Promise<void>;
}

const LedgerContext = createContext<LedgerContextValue | null>(null);

export function LedgerProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [mode, setMode] = useState<LedgerMode | null>(readPref);
  const [service, setService] = useState<LedgerService | null>(() => {
    const m = readPref();
    return m ? createMemoryLedger(m) : null;
  });

  const query = useQuery({
    queryKey: ["txns", mode],
    queryFn: () => service!.list(),
    enabled: service !== null,
  });
  const invalidate = useCallback(() => qc.invalidateQueries({ queryKey: ["txns"] }), [qc]);

  const value = useMemo<LedgerContextValue>(
    () => ({
      mode,
      service,
      txns: query.data ?? [],
      isLoading: service !== null && query.isLoading,
      isError: query.isError,
      refetch: () => void query.refetch(),
      now: Date.now(),
      chooseMode: (m) => {
        try {
          localStorage.setItem(PREF_KEY, m);
        } catch {
          /* private mode: preference just won't stick */
        }
        setService(createMemoryLedger(m));
        setMode(m);
      },
      forgetMode: () => {
        try {
          localStorage.removeItem(PREF_KEY);
        } catch {
          /* ignore */
        }
        setService(null);
        setMode(null);
      },
      add: async (txns) => {
        const r = await service!.add(txns);
        await invalidate();
        return r;
      },
      update: async (txn) => {
        await service!.update(txn);
        await invalidate();
      },
      remove: async (id) => {
        await service!.remove(id);
        await invalidate();
      },
      restore: async (txn) => {
        await service!.add([txn]);
        await invalidate();
      },
      reset: async () => {
        await service!.reset();
        await invalidate();
      },
    }),
    [mode, service, query.data, query.isLoading, query.isError, query.refetch, invalidate],
  );

  return <LedgerContext.Provider value={value}>{children}</LedgerContext.Provider>;
}

export function useLedger(): LedgerContextValue {
  const ctx = useContext(LedgerContext);
  if (!ctx) throw new Error("useLedger must be used inside LedgerProvider");
  return ctx;
}
