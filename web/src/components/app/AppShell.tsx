import { useRouterState } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { Toaster } from "@/components/ui/sonner";
import { EntryProvider } from "@/features/entry/EntryProvider";
import { useLedger } from "@/services/LedgerProvider";
import { FirstRun } from "./FirstRun";
import { TabBar } from "./TabBar";

const CONSUMER_PATHS = ["/", "/today", "/ledger", "/insights", "/me"];

/** Phone column: full-bleed on phones, a centered ~430px device on desktop. */
export function AppShell({ children }: { children: ReactNode }) {
  const { mode } = useLedger();
  const path = useRouterState({ select: (s) => s.location.pathname });
  const gated = CONSUMER_PATHS.includes(path) && mode === null;
  return (
    <div className="flex min-h-dvh items-center justify-center bg-canvas-outer md:p-4">
      <div className="relative flex h-dvh w-full max-w-[430px] flex-col overflow-hidden bg-canvas md:h-[min(900px,calc(100dvh-2rem))] md:rounded-[2rem] md:shadow-2xl md:ring-1 md:ring-black/5">
        <EntryProvider>
          <main className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{gated ? <FirstRun /> : children}</main>
          {!gated && <TabBar />}
        </EntryProvider>
        <Toaster position="top-center" offset={12} />
      </div>
    </div>
  );
}
