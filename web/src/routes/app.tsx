import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";
import { Shell } from "@/components/desktop/Shell";
import { initLocalePref } from "@/components/desktop/localePref";
import { LedgerProvider } from "@/services/LedgerProvider";

/**
 * Desktop app shell (docs/WEB-SPEC.md §A). Every `/app/*` page — personal and shop alike — renders
 * inside this layout, so the sidebar, toolbar, command palette and print dialog exist once.
 * The ledger provider lives here rather than in __root to keep the phone routes independent.
 */
export const Route = createFileRoute("/app")({ component: AppLayout });

function AppLayout() {
  useEffect(() => {
    initLocalePref();
  }, []);
  return (
    <LedgerProvider>
      <Shell />
    </LedgerProvider>
  );
}
