import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { type Txn } from "../domain/types";
import { type AddResult, type LedgerService } from "./LedgerService";
import { hasQmuseLoginHint } from "./appwrite";
import { createCloudLedger } from "./cloudLedger";
import { createMemoryLedger } from "./demoLedger";

interface LedgerContextValue {
  service: LedgerService;
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
  // QMuse 云服务 ledger; anonymous visitors also see the in-memory starter ledger (it never leaves the browser).
  const [service] = useState<LedgerService>(() =>
    createCloudLedger(hasQmuseLoginHint() ? null : createMemoryLedger("demo")),
  );

  const query = useQuery({
    queryKey: ["txns"],
    queryFn: () => service.list(),
  });
  const { data, isLoading: loading, isError, refetch } = query;
  const invalidate = useCallback(() => qc.invalidateQueries({ queryKey: ["txns"] }), [qc]);

  const value = useMemo<LedgerContextValue>(
    () => ({
      service,
      txns: data ?? [],
      isLoading: loading,
      isError,
      refetch: () => void refetch(),
      now: Date.now(),
      add: async (txns) => {
        const r = await service.add(txns);
        await invalidate();
        return r;
      },
      update: async (txn) => {
        await service.update(txn);
        await invalidate();
      },
      remove: async (id) => {
        await service.remove(id);
        await invalidate();
      },
      restore: async (txn) => {
        await service.add([txn]);
        await invalidate();
      },
      reset: async () => {
        await service.reset();
        await invalidate();
      },
    }),
    [service, data, loading, isError, refetch, invalidate],
  );

  return <LedgerContext.Provider value={value}>{children}</LedgerContext.Provider>;
}

export function useLedger(): LedgerContextValue {
  const ctx = useContext(LedgerContext);
  if (!ctx) throw new Error("useLedger must be used inside LedgerProvider");
  return ctx;
}
